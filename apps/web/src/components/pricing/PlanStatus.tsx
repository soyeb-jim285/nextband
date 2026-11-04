import { Link } from '@tanstack/react-router';
import { Sparkles } from 'lucide-react';
import { buttonStyles } from '@/components/ui';
import { useQuota, type Quota } from '@/lib/community';
import { cn } from '@/lib/utils';
import { inPlayApp } from '@/lib/inApp';

const DAY = 86_400_000;
/** A paid plan this close to its end is "ending": the renew prompts turn amber. */
export const ENDING_DAYS = 7;

export const fmtDate = (d: Date) => d.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });

export type Plan =
  | { state: 'free' }
  | { state: 'active' | 'ending'; until: Date; daysLeft: number }
  | { state: 'unlimited' | 'guest' };

/** Where the person stands. A subscriber whose date has passed is back on the free plan (the server already counts them as community). */
export function planOf(q: Pick<Quota, 'tier' | 'subscribedUntil'>, now = Date.now()): Plan {
  if (q.tier === 'guest' || q.tier === 'unlimited') return { state: q.tier };
  const until = q.subscribedUntil ? new Date(q.subscribedUntil) : null;
  if (q.tier !== 'subscriber' || !until || until.getTime() <= now) return { state: 'free' };
  const daysLeft = Math.ceil((until.getTime() - now) / DAY);
  return { state: daysLeft <= ENDING_DAYS ? 'ending' : 'active', until, daysLeft };
}

export const usePlan = (): Plan | null => {
  const { data } = useQuota();
  return data ? planOf(data) : null;
};

/** "3 days left", "Ends today". */
export const leftText = (days: number) => (days <= 1 ? 'Ends within a day' : `${days} days left`);

/** The one call to action for the plan: Upgrade (free), Renew (ending), Extend (active). Null when there is nothing to buy. */
export const actionLabel = (p: Plan) => (p.state === 'free' ? 'Upgrade' : p.state === 'ending' ? 'Renew plan' : p.state === 'active' ? 'Extend plan' : null);

export function PlanButton({ plan, size = 'sm', className }: { plan: Plan; size?: 'sm' | 'lg'; className?: string }) {
  const label = actionLabel(plan);
  if (!label || inPlayApp()) return null;
  const primary = plan.state === 'free' || plan.state === 'ending';
  return (
    <Link to="/checkout" className={buttonStyles({ variant: primary ? 'primary' : 'secondary', size, className })}>
      <Sparkles aria-hidden />
      {label}
    </Link>
  );
}

/** One line on where the plan stands, for Settings, Pricing and the dashboard. */
export function planLine(p: Plan): string | null {
  if (p.state === 'free') return 'You are on the free plan.';
  if (p.state === 'active' || p.state === 'ending') return `Your paid plan runs until ${fmtDate(p.until)} (${leftText(p.daysLeft).toLowerCase()}). Buying again adds the time to the end.`;
  if (p.state === 'unlimited') return 'You have unlimited access. There is nothing to buy.';
  return null;
}

export const planToneClass = (p: Plan) => cn(p.state === 'ending' && 'font-medium text-warn-text');
