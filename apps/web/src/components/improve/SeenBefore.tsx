import { useQuery } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { ArrowRight, Layers } from 'lucide-react';
import { coachQuery, kindCategory } from '@/components/coach/data';
import { listStyles } from '@/components/bank/ListRow';
import { Section } from '@/components/result';
import { Badge } from '@/components/ui';
import { plural } from '@/lib/format';
import { useAccount } from '@/lib/query';
import { categoryLabel } from '@/lib/result';
import { recurringQuery } from './data';

const link = 'inline-flex items-center gap-1 font-medium text-accent-text underline underline-offset-4 hover:no-underline';

/**
 * On a result: which of this test's mistakes you have made before (in how many of your last tests of this skill), whether one is in
 * your coach's focus, and how many went into your drills. Accounts only; nothing when there is nothing to say.
 */
export function SeenBefore({ attemptId }: { attemptId: string }) {
  const account = useAccount();
  const { data } = useQuery({ ...recurringQuery(attemptId), enabled: !!account });
  const report = useQuery({ ...coachQuery, enabled: !!account }).data?.report;
  if (!data) return null;
  const focus = new Set(report?.words.focus.map((f) => kindCategory(report.facts.candidates.find((c) => c.id === f.candidateId)?.kind ?? '')) ?? []);
  const repeats = data.items.filter((i) => i.before > 0).slice(0, 4);
  if (!repeats.length && !data.added) return null;
  return (
    <Section title={repeats.length ? 'Mistakes you have made before' : 'Added to your drills'} caption={repeats.length ? `Compared with your last ${plural(repeats[0]!.outOf, 'test')} of this kind.` : undefined}>
      {repeats.length > 0 && (
        <ul className={listStyles}>
          {repeats.map((i) => (
            <li key={i.category} className="flex flex-wrap items-baseline justify-between gap-x-6 gap-y-1 py-3">
              <span className="flex min-w-0 flex-wrap items-baseline gap-x-3 gap-y-1">
                <Link to="/improve/patterns" search={{ pattern: i.category }} className="type-subheading rounded-sm hover:text-accent-text">
                  {categoryLabel(i.category)}
                </Link>
                <span className="type-caption type-num">{plural(i.here, 'time')} this time</span>
                {focus.has(i.category) && <Badge tone="accent">In your focus</Badge>}
              </span>
              <span className="type-caption type-num">
                {i.before === i.outOf ? (i.outOf === 1 ? 'also in your last test' : `also in all of your last ${i.outOf} tests`) : `also in ${i.before} of your last ${plural(i.outOf, 'test')}`}
              </span>
            </li>
          ))}
        </ul>
      )}
      {data.added > 0 && (
        <p className="type-body flex flex-wrap items-center gap-x-2 gap-y-1">
          <Layers className="size-4 text-accent-text" aria-hidden />
          <span>{plural(data.added, 'mistake')} from this test went into your drills.</span>
          <Link to="/improve/drill" search={{}} className={link}>
            Drill now <ArrowRight className="size-4" aria-hidden />
          </Link>
        </p>
      )}
    </Section>
  );
}
