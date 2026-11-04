import { guestUser, testUser } from './test/helpers'; // first: loads the app before the modules that import it back
import { eq, sql } from 'drizzle-orm';
import { expect, it, vi } from 'vitest';
import { auth } from './auth';
import { sendsIdle } from './auth-email';
import { db } from './db/client';
import { emailLog, userLimits } from './db/schema';
import { sendEmail } from './email';
import { checkStart, payerOf, reserve } from './quota';
import { updateSettings } from './settings';
import { sendLimitEmail, sendWelcomeEmail } from './upgrade-emails';

vi.mock('./email', () => ({ sendEmail: vi.fn(async () => ({ ok: true, id: 'e1', attempts: 1 })), sendBatch: vi.fn(async () => ({ ok: true })) }));
const send = vi.mocked(sendEmail);
const DAY = 86_400_000;
const mine = (email: string) => send.mock.calls.map(([m]) => m).filter((m) => m.to === email);
const free = (email: string, verified = true) => testUser(email, { unlimited: false, verified }).then((r) => r.user);
const LIMIT = { what: 'writing tests', period: 'today' };

it('welcome: once per free account, with the Free vs Pro table, website link and unsubscribe', async () => {
  const u = await free('w-a@test.dev');
  send.mockClear();
  expect(await sendWelcomeEmail(u.id)).toBe(true);
  expect(await sendWelcomeEmail(u.id)).toBe(false);
  const [m] = mine('w-a@test.dev');
  expect(mine('w-a@test.dev')).toHaveLength(1);
  expect(m!.subject).toBe('Welcome to NextBand, Test');
  expect(m!.html).toContain('/pricing');
  expect(m!.html).toMatch(/bKash, Nagad or a card/);
  expect(m!.headers?.['List-Unsubscribe']).toContain('/api/email/unsubscribe?t=');
});

it('skips guests, unverified, Pro, no-limit and opted-out accounts', async () => {
  const guest = await guestUser();
  const unverified = await free('w-unv@test.dev', false);
  const pro = await free('w-pro@test.dev');
  await db.insert(userLimits).values({ userId: pro.id, subscribedUntil: new Date(Date.now() + 30 * DAY), updatedBy: 'test' });
  const unlimited = (await testUser('w-unl@test.dev')).user;
  const out = await free('w-out@test.dev');
  await updateSettings(out.id, { productEmails: false });
  send.mockClear();
  for (const u of [guest.user, unverified, pro, unlimited, out]) {
    expect(await sendWelcomeEmail(u.id)).toBe(false);
    expect(await sendLimitEmail(u.id, LIMIT)).toBe(false);
  }
  expect(send).not.toHaveBeenCalled();
});

it('limit email: at most once in 7 days; a failed send frees the slot', async () => {
  const u = await free('l-a@test.dev');
  send.mockClear();
  send.mockResolvedValueOnce({ ok: false, kind: 'network', attempts: 3 });
  expect(await sendLimitEmail(u.id, LIMIT)).toBe(false);
  expect(await sendLimitEmail(u.id, LIMIT)).toBe(true);
  expect(await sendLimitEmail(u.id, LIMIT)).toBe(false);
  expect(mine('l-a@test.dev').at(-1)!.subject).toBe("You've used your free writing tests");
  expect(mine('l-a@test.dev').at(-1)!.html).toContain('for today');
  await db.update(emailLog).set({ createdAt: sql`now() - interval '8 days'` }).where(eq(emailLog.purpose, 'limit-reached'));
  expect(await sendLimitEmail(u.id, LIMIT)).toBe(true);
  expect(mine('l-a@test.dev')).toHaveLength(3); // failed, sent, sent a week later
});

it('a free account refused a test start gets the limit email', async () => {
  const u = await free('l-q@test.dev');
  const p = await payerOf({ ...u, emailVerified: true });
  await reserve(p, 'writing', 'unit-1', null); // tests: one writing test a day
  send.mockClear();
  await expect(checkStart(p, 'writing', null)).rejects.toMatchObject({ status: 429 });
  await vi.waitFor(() => expect(mine('l-q@test.dev')).toHaveLength(1));
  expect(mine('l-q@test.dev')[0]!.html).toContain('writing tests for today');
});

it('confirming the email with the code sends the welcome', async () => {
  const u = await free('w-otp@test.dev', false);
  send.mockClear();
  await auth.api.sendVerificationOTP({ body: { email: u.email, type: 'email-verification' } });
  await sendsIdle();
  const code = mine('w-otp@test.dev')[0]!.subject.match(/\d{6}/)![0];
  await auth.api.verifyEmailOTP({ body: { email: u.email, otp: code } });
  await vi.waitFor(() => expect(mine('w-otp@test.dev').map((m) => m.subject)).toContain('Welcome to NextBand, Test'));
});
