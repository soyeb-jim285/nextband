import { Link, useRouterState } from '@tanstack/react-router';
import { BookOpen, Headphones, Mic, PenLine } from 'lucide-react';
import { RemoveAttempt } from '@/components/history/RemoveAttempt';
import { Section } from '@/components/result';
import { listStyles, RowChevron, RowIcon, rowStyles } from '@/components/bank/ListRow';
import { Badge, buttonStyles } from '@/components/ui';
import { formatBand, formatDate } from '@/lib/format';
import { rowLink, useGuestRecent, type Row } from '@/lib/guestRecent';
import { cn } from '@/lib/utils';

type Skill = Row['skill'];
export type { Row };

const ICON = { speaking: Mic, writing: PenLine, listening: Headphones, reading: BookOpen };
const modeLabel = (m: string) => (m === 'practice' ? 'Practice' : m === 'live' ? 'Live' : 'Exam');
const STATE = { open: { tone: 'warn', label: 'In progress, resume' }, scoring: { tone: 'accent', label: 'Scoring' }, failed: { tone: 'bad', label: 'Scoring failed' } } as const;

export { rowLink, useGuestRecent };

/**
 * Guests have no History page, but their own few tests are listed here (same two endpoints History uses; the server caps a guest's list).
 * Renders nothing for an account or when there is nothing yet. `skill` narrows it to one hub.
 */
export function GuestRecent({ skill, className }: { skill?: Skill; className?: string }) {
  const redirect = useRouterState({ select: (s) => s.location.href });
  const rows = useGuestRecent(skill);
  if (!rows.length) return null;

  return (
    <Section title="Your recent tests" id="guest-recent" className={className}>
      <ul className={listStyles}>
        {rows.map((r) => {
          const Icon = ICON[r.skill];
          const st = r.state in STATE ? STATE[r.state as keyof typeof STATE] : null;
          return (
            <li key={r.id} className="flex items-center gap-1">
              <Link {...rowLink(r)} className={cn(rowStyles, 'min-w-0 flex-1')}>
                <RowIcon>
                  <Icon />
                </RowIcon>
                <span className="min-w-0 flex-1">
                  <span className="type-subheading line-clamp-2 block font-medium text-pretty">
                    <span className="sr-only">{r.skill}: </span>
                    {r.title}
                  </span>
                  <span className="type-caption mt-1 flex flex-wrap items-center gap-x-2 gap-y-1">
                    <Badge tone={r.mode === 'practice' ? 'neutral' : 'accent'}>{modeLabel(r.mode)}</Badge>
                    {r.part}, {formatDate(r.at)}
                  </span>
                </span>
                {st ? (
                  <Badge tone={st.tone} className="shrink-0">
                    {st.label}
                  </Badge>
                ) : r.band != null ? (
                  <span className="type-band text-3xl">
                    <span className="sr-only">Band </span>
                    {formatBand(r.band)}
                  </span>
                ) : null}
                <RowChevron />
              </Link>
              <RemoveAttempt kind={r.skill === 'listening' || r.skill === 'reading' ? 'lr' : 'attempt'} id={r.id} title={r.title} />
            </li>
          );
        })}
      </ul>
      <div className="flex flex-wrap items-center justify-between gap-x-6 gap-y-3">
        <p className="type-caption min-w-0 flex-1 text-pretty">These live in this browser only. Create an account to keep them and unlock full History.</p>
        <Link to="/signup" search={{ redirect }} className={buttonStyles({ size: 'sm' })}>
          Create account
        </Link>
      </div>
    </Section>
  );
}
