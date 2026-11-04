import { useQuery } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { Compass, LoaderCircle } from 'lucide-react';
import { useState } from 'react';
import { patternsQuery } from '@/components/improve/data';
import { Today } from '@/components/improve/Today';
import { Section } from '@/components/result';
import { Badge, buttonStyles, EmptyState, toast } from '@/components/ui';
import { call, client } from '@/lib/api';
import { formatBand, formatDate, plural } from '@/lib/format';
import { useAccount } from '@/lib/query';
import { CheatSheet } from './CheatSheet';
import { type Coach, type Feedback, itemKey, kindCategory, type Report } from './data';
import { FocusList } from './FocusList';
import { daysUntil, GoalControls } from './GoalControls';
import { SkillCards } from './SkillCards';
import { Win } from './Summary';
import { WeekPlan } from './WeekPlan';

const rtf = new Intl.RelativeTimeFormat('en', { numeric: 'auto' });
/** Report day (the student's local YYYY-MM-DD) as "today", "yesterday", "3 days ago". */
const relativeDay = (day: string) => rtf.format(-Math.round((Date.parse(new Date().toLocaleDateString('en-CA')) - Date.parse(day)) / 86_400_000), 'day');

/** The "Your plan" tab of Improve for a loaded GET /api/coach response. The page header and tabs come from the Improve layout. */
export function CoachView({ data }: { data: Coach }) {
  if (data.state === 'empty' || !data.report)
    return data.updating ? (
      <EmptyState icon={<LoaderCircle className="animate-spin" />} title="Your coach is reading your tests…">
        <span role="status">Your first report takes about a minute. This page updates by itself.</span>
      </EmptyState>
    ) : (
      <EmptyState
        icon={<Compass />}
        title="Your coach starts after your first test"
        action={
          <Link to="/writing" className={buttonStyles()}>
            Write your first essay
          </Link>
        }
      >
        It reads all your tests together and shows where you stand in each skill, the 3 things most worth fixing this week, and turns your own mistakes into short drills.
      </EmptyState>
    );
  return <Ready report={data.report} updating={data.updating} />;
}

/** "This is wrong" on coach items: hidden at once, saved for the report, put back if the save fails. */
export function useFeedback(report: Report): Feedback {
  const [gone, setGone] = useState<string[]>([]);
  return {
    hidden: new Set([...report.hidden, ...gone]),
    wrong: (key) => {
      setGone((g) => [...g, key]);
      call(client.POST('/api/coach/feedback', { body: { reportId: report.id, itemKey: key } })).then(
        () => toast('Thanks. That item is hidden until your next report.'),
        (e: Error) => {
          setGone((g) => g.filter((k) => k !== key));
          toast(`Couldn't save that: ${e.message}`, { tone: 'bad' });
        },
      );
    },
  };
}

function Ready({ report, updating }: { report: Report; updating: boolean }) {
  const { facts, words } = report;
  const account = useAccount();
  const { patterns, deck } = useQuery({ ...patternsQuery, enabled: !!account }).data ?? {};
  const fb = useFeedback(report);
  const examDate = account ? account.settings.examDate : facts.examDate;
  const days = daysUntil(examDate);
  // the first focus item that has drill cards: "Drill anyway" on a day with nothing due
  const top = words.focus.map((f) => kindCategory(facts.candidates.find((c) => c.id === f.candidateId)?.kind ?? '')).find((cat) => deck?.some((d) => d.category === cat && d.cards > 0));

  return (
    <div className="space-y-10">
      <div className="space-y-4">
        <div className="flex flex-wrap items-start justify-between gap-x-6 gap-y-3">
          <div className="type-caption flex flex-wrap items-center gap-2">
            <span>
              Coach updated {relativeDay(report.day)}, from {plural(facts.totalTests, 'test')}.
            </span>
            {facts.target != null && <Badge tone="accent">Target band {formatBand(facts.target)}</Badge>}
            {examDate && days != null && (
              <Badge>
                Exam {formatDate(examDate)} · {days === 0 ? 'today' : plural(days, 'day')}
              </Badge>
            )}
          </div>
          {account && <GoalControls chosen={facts.target} />}
        </div>
        <p className="type-reading max-w-[65ch] text-ink">{words.summary}</p>
        {updating && (
          <p className="type-caption flex items-center gap-2" role="status">
            <LoaderCircle className="size-3.5 animate-spin" aria-hidden />
            Updating your coach…
          </p>
        )}
      </div>

      {account && <Today next={words.plan[0]} top={top} />}

      {words.focus.length > 0 && (
        <Section title="This week's focus" caption="In order of how much they would lift your band.">
          <FocusList focus={words.focus} candidates={facts.candidates} fb={fb} patterns={patterns} deck={deck} />
        </Section>
      )}

      {words.win && <Win text={words.win} />}

      <Section title="Where you stand" caption={`Your band in each skill${facts.target != null ? ', against your target' : ''}.`}>
        <SkillCards skills={facts.skills} target={facts.target} />
      </Section>

      {words.plan.length > 0 && (
        <Section title="Your week" caption={days != null ? `Sized to the ${plural(days, 'day')} left before your exam.` : 'Tap a line to start it.'}>
          <WeekPlan plan={words.plan} />
        </Section>
      )}

      {words.cheat.some((c) => !fb.hidden.has(itemKey('cheat', c.candidateId))) && (
        <Section title="Before your next test" caption="Read this just before you start.">
          <CheatSheet cheat={words.cheat} fb={fb} />
        </Section>
      )}
    </div>
  );
}
