import { and, eq, gt, sql } from 'drizzle-orm';
import { db } from './db/client';
import { emailLog } from './db/schema';
import { sendEmail } from './email';
import { docOf, renderDoc, TEMPLATES } from './email-templates';
import { campaignEmail, type Recipient } from './emails/campaigns';

const WEEK = 7 * 86_400_000;
type Key = 'welcome-free' | 'limit-reached';

/** Verified, non-guest, free (not paid, not "no test limits") account with news and tips on; else null. */
async function freeRecipient(userId: string): Promise<Recipient | null> {
  const [u] = (await db.execute(sql`select u.id, u.email, u.name from "user" u
    left join user_settings s on s.user_id = u.id left join user_limits l on l.user_id = u.id
    where u.id = ${userId} and not coalesce(u.is_anonymous, false) and u.email_verified and coalesce((s.data->>'productEmails')::boolean, true)
      and not coalesce(l.unlimited, false) and (l.subscribed_until is null or l.subscribed_until <= now())`)) as unknown as Recipient[];
  return u ?? null;
}

/** Sends `key` unless one was sent since `since` (null: ever). The email_log row is claimed under a lock first, so two requests at once send once;
 *  a failed send deletes the claim so the next trigger tries again. Never throws. */
async function sendOnce(userId: string, key: Key, since: Date | null, vars: Record<string, string> = {}): Promise<boolean> {
  try {
    const u = await freeRecipient(userId);
    if (!u) return false;
    const email = u.email.toLowerCase();
    const id = await db.transaction(async (tx) => {
      await tx.execute(sql`select pg_advisory_xact_lock(hashtextextended(${`${key}:${email}`}, 0))`);
      const [hit] = await tx.select({ id: emailLog.id }).from(emailLog)
        .where(and(eq(emailLog.email, email), eq(emailLog.purpose, key), since ? gt(emailLog.createdAt, since) : undefined)).limit(1);
      if (hit) return null;
      const [row] = await tx.insert(emailLog).values({ userId, email, purpose: key, status: 'sent', attempts: 0 }).returning({ id: emailLog.id });
      return row!.id;
    });
    if (!id) return false;
    const r = await sendEmail(campaignEmail(await renderDoc(await docOf(key), 'promo', TEMPLATES[key].reason), u, vars));
    if (r.ok) await db.update(emailLog).set({ providerId: r.id, attempts: r.attempts }).where(eq(emailLog.id, id));
    else await db.delete(emailLog).where(eq(emailLog.id, id));
    return r.ok;
  } catch (e) {
    console.error('[email] upgrade email failed', key, userId, e instanceof Error ? e.message : e);
    return false;
  }
}

/** Once per account, when a free account confirms its email (Better Auth afterEmailVerification). */
export const sendWelcomeEmail = (userId: string) => sendOnce(userId, 'welcome-free', null);

/** A free account was refused a test start for its quota: at most one every 7 days. `what`/`period`: "writing tests", "this week". */
export const sendLimitEmail = (userId: string, vars: { what: string; period: string }, now = new Date()) =>
  sendOnce(userId, 'limit-reached', new Date(now.getTime() - WEEK), vars);
