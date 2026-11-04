import { useQuery } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { ArrowRight, Layers } from 'lucide-react';
import { ACTION, type CoachWords } from '@/components/coach/data';
import { buttonStyles, Card, Skeleton } from '@/components/ui';
import { plural } from '@/lib/format';
import { dueQuery } from './data';

export const SESSION = 10; // cards in one drill session
/** Minutes for n cards: about 30 s each, never "0 min". */
export const drillMinutes = (n: number) => Math.max(2, Math.round(Math.min(n, SESSION) / 2));

/**
 * Today, the one primary action of Improve: the drill when cards are due; on a day with none, the top focus pattern ahead of schedule;
 * before any cards exist, what fills them. The next line of the week plan sits underneath.
 */
export function Today({ next, top }: { next?: CoachWords['plan'][number]; top?: string }) {
  const { data } = useQuery(dueQuery());
  const due = data?.total ?? 0;
  const step = next && ACTION[next.action];
  return (
    <Card tone="hero" className="flex flex-col gap-5 sm:flex-row sm:items-center sm:justify-between">
      <div className="min-w-0 space-y-1">
        <h2 className="type-subheading flex items-center gap-2">
          <Layers className="size-4 text-accent-text" aria-hidden />
          Today
        </h2>
        {!data ? (
          <Skeleton className="h-5 w-64" />
        ) : (
          <p className="type-body text-ink">
            {due > 0
              ? `${plural(due, 'drill card')} due from your own mistakes. A session is ${Math.min(due, SESSION)} cards, about ${drillMinutes(due)} minutes.`
              : data.deck > 0
                ? 'Nothing due today: each card comes back just before you would forget it.'
                : 'Your drills fill up after each test, from the mistakes worth practising.'}
          </p>
        )}
        {next && step && (
          <p className="type-caption">
            Next in your week:{' '}
            {step.to ? (
              <Link to={step.to} className="font-medium text-accent-text underline underline-offset-2 hover:no-underline">
                {next.text}
              </Link>
            ) : (
              next.text
            )}{' '}
            · {step.minutes} min
          </p>
        )}
      </div>
      {data && due > 0 ? (
        <Link to="/improve/drill" search={{}} className={buttonStyles({ size: 'lg', className: 'shrink-0 max-sm:w-full' })}>
          Start drill
          <ArrowRight aria-hidden />
        </Link>
      ) : data && data.deck > 0 ? (
        <Link to="/improve/drill" search={{ ahead: true, ...(top && { pattern: top }) }} className={buttonStyles({ variant: 'outline', size: 'lg', className: 'shrink-0 max-sm:w-full' })}>
          {top ? 'Drill your top pattern' : 'Drill anyway'}
        </Link>
      ) : null}
    </Card>
  );
}
