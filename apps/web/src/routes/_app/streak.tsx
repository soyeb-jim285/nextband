import type { ReactNode } from 'react';
import { queryOptions, useQuery } from '@tanstack/react-query';
import { createFileRoute, Link } from '@tanstack/react-router';
import { ArrowRight, Snowflake } from 'lucide-react';
import { BigFlame, HeatLegend, Heatmap, ShareButton, WeekStrip, flameLabel } from '@/components/streak/Streak';
import { Alert, buttonStyles, PageContainer, PageHeader, ProgressBar } from '@/components/ui';
import { call, client } from '@/lib/api';
import { plural } from '@/lib/format';
import { useAccount } from '@/lib/query';
import { freezesLine, milestoneProgress, streakQuery, type Streak } from '@/lib/streak';

const dueQuery = queryOptions({ queryKey: ['cards', 'due'], queryFn: () => call(client.GET('/api/cards/due')), staleTime: 0 });

export const Route = createFileRoute('/_app/streak')({
  loader: ({ context }) => context.queryClient.ensureQueryData(streakQuery).catch(() => null), // a visitor has no session
  component: StreakPage,
});

function StreakPage() {
  const { data: s } = useQuery(streakQuery);
  if (!s)
    return (
      <PageContainer>
        <PageHeader title="Your streak" description="Take any practice test to start a streak. A 2-minute drill keeps it going." />
      </PageContainer>
    );
  return <StreakView s={s} />;
}

function StreakView({ s }: { s: Streak }) {
  const account = useAccount();
  const due = useQuery({ ...dueQuery, enabled: !!account }).data?.total ?? 0;
  const toExam = account?.settings.examDate ? Math.round((Date.parse(account.settings.examDate) - Date.parse(s.date)) / 864e5) : null;
  const until = s.repair && new Date(s.repair.until).toLocaleString('en', { weekday: 'long', hour: 'numeric', minute: '2-digit' });
  const thisMonth = s.month.filter((d) => d.level > 0).length;
  const cta = due ? (
    <Link to="/improve/drill" search={{}} className={buttonStyles({ className: 'shrink-0' })}>
      Drill now <ArrowRight aria-hidden />
    </Link>
  ) : (
    <Link to="/speaking" className={buttonStyles({ className: 'shrink-0' })}>
      Practise now <ArrowRight aria-hidden />
    </Link>
  );
  return (
    <PageContainer>
      <PageHeader title="Your streak" description="A practice day is one test part, 5 drill cards, or 10 minutes of Listening or Reading." />
      <div className="space-y-10">
        {s.repair && (
          <Alert tone="accent" title={`Bring back your ${s.repair.target}-day streak`}>
            {s.repair.daysDone === 0 ? 'Practise today and tomorrow.' : 'One more practice day does it.'} The offer ends {until}.
          </Alert>
        )}
        {!s.repair && s.current === 0 && s.longest > 0 && <p className="type-body text-muted">New streak, fresh start. Your longest is {plural(s.longest, 'day')}.</p>}

        {/* The count and this week, open on the page: no box around them. */}
        <section className="flex flex-wrap items-end justify-between gap-x-12 gap-y-8">
          <div className="flex flex-wrap items-end gap-x-12 gap-y-6">
            <div role="img" aria-label={flameLabel(s)} className="w-fit">
              <BigFlame count={s.current} size="xl" />
              <p className="type-subheading mt-1 text-center text-muted">day streak</p>
            </div>
            <WeekStrip week={s.week} className="w-full max-w-xs pb-1" />
          </div>
          <div className="flex flex-col items-start gap-3 sm:items-end">
            <p className="type-body text-ink">{due ? `${plural(due, 'drill')} ${due === 1 ? 'is' : 'are'} waiting` : 'Keep it going today'}</p>
            <div className="flex gap-2">
              {s.justReached != null && s.justReached === s.current && <ShareButton n={s.current} week={s.week} />}
              {cta}
            </div>
          </div>
        </section>

        {/* One ruled row of facts instead of a stack of cards. */}
        <dl className="grid grid-cols-2 gap-y-6 border-y border-line py-5 sm:grid-cols-4 sm:divide-x sm:divide-line">
          <Fact label="Longest">{plural(s.longest, 'day')}</Fact>
          <Fact label="Freezes">
            <span className="flex items-center gap-1.5">
              <Snowflake className="size-4 text-sky-text" aria-hidden />
              {s.freezes}
            </span>
            <span className="block text-caption font-normal text-muted">{freezesLine(s).split(' · ')[1] ?? 'max 2 held'}</span>
          </Fact>
          {s.nextMilestone != null ? (
            <Fact label="Next milestone">
              {plural(s.nextMilestone, 'day')}
              <ProgressBar value={milestoneProgress(s) ?? 1} label={`Progress to the ${s.nextMilestone}-day milestone`} className="mt-2 h-1.5 max-w-36" />
            </Fact>
          ) : (
            <Fact label="Milestones">All reached</Fact>
          )}
          <Fact label={toExam != null && toExam >= 0 && toExam <= 30 ? 'To your exam' : 'This month'}>
            {toExam != null && toExam >= 0 && toExam <= 30 ? (toExam ? plural(toExam, 'day') : 'Today') : plural(thisMonth, 'practice day')}
          </Fact>
        </dl>

        <SixMonths s={s} />
      </div>
    </PageContainer>
  );
}

function Fact({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="sm:px-6 sm:first:pl-0">
      <dt className="text-caption text-muted">{label}</dt>
      <dd className="mt-1 text-lg font-semibold text-ink tabular-nums">{children}</dd>
    </div>
  );
}

/** The last 6 months. The weeks share the card's width (no sideways scroll), capped so the cells stay small squares on a wide screen. */
function SixMonths({ s }: { s: Streak }) {
  const days = s.days.filter((d) => d.level > 0).length;
  return (
    <section className="space-y-5">
      <div className="flex max-w-[48rem] flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
        <h2 className="type-heading">
          Last <span className="sm:hidden">three</span>
          <span className="max-sm:hidden">six</span> months
        </h2>
        <p className="text-caption text-muted max-sm:hidden">{plural(days, 'practice day')}</p>
      </div>
      {/* A phone shows the last 3 months so the squares stay tappable-size; wider screens show all 6. */}
      <Heatmap days={s.days} today={s.date} weeks={13} monthLabels fluid className="[--gap:3px] sm:hidden" />
      <Heatmap days={s.days} today={s.date} weeks={26} monthLabels fluid className="max-w-[48rem] [--gap:4px] max-sm:hidden" />
      <HeatLegend />
    </section>
  );
}
