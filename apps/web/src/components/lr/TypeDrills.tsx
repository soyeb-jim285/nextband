import { useQuery } from '@tanstack/react-query';
import { useNavigate } from '@tanstack/react-router';
import { DRILL_TYPES, type DrillType } from '@ielts/core';
import { useState } from 'react';
import { GroupHeading, listStyles, RowChevron, rowStyles } from '@/components/bank/ListRow';
import { Badge, Spinner, toast } from '@/components/ui';
import { ensureSession } from '@/lib/auth';
import { lrDrillsQuery, startDrill } from '@/lib/lr';
import { useMe } from '@/lib/query';
import { cn } from '@/lib/utils';

const DESC: Record<DrillType, string> = {
  tfng: 'True / False / Not Given and Yes / No / Not Given statements.',
  match: 'Choose the heading that sums up each paragraph.',
  minfo: 'Find the paragraph that holds each piece of information.',
  mfeat: 'Match each statement to a person, place or date.',
  mend: 'Join each sentence beginning to the right ending.',
  mcq: 'One answer, or choose two.',
  sent: 'Complete sentences with words from the passage.',
  summ: 'Fill a summary, notes or a table, some with a word box.',
  diag: 'Label a diagram with words from the passage.',
  short: 'Answer questions in a few words from the passage.',
};
const TYPES = Object.keys(DRILL_TYPES) as DrillType[];
const GROUPS: { heading: string; types: DrillType[] }[] = [
  { heading: 'Statements', types: ['tfng'] },
  { heading: 'Matching', types: ['match', 'minfo', 'mfeat', 'mend'] },
  { heading: 'Multiple choice', types: ['mcq'] },
  { heading: 'Completion and short answers', types: ['sent', 'summ', 'diag', 'short'] },
];

/** Reading page, "By question type" tab: a row starts (or resumes) the next drill of that type. */
export function TypeDrills() {
  const me = useMe().data;
  const stats = useQuery({ ...lrDrillsQuery, enabled: !!me });
  const navigate = useNavigate();
  const [busy, setBusy] = useState<DrillType | null>(null);

  const by = new Map((stats.data ?? []).map((s) => [s.type, s]));
  const seen = TYPES.filter((t) => (by.get(t)?.total ?? 0) >= 4);
  const weakest = seen.length >= 2 ? seen.reduce((w, t) => (pct(by.get(t)!) < pct(by.get(w)!) ? t : w)) : null;

  const start = async (type: DrillType) => {
    setBusy(type);
    try {
      await ensureSession(); // a visitor gets a guest session, as for a full test
      await navigate({ to: '/lr/run/$attemptId', params: { attemptId: await startDrill({ type }) } });
    } catch {
      toast('Could not start the drill. Try again.', { tone: 'bad' });
      setBusy(null);
    }
  };

  return (
    <div>
      <p className="type-caption mb-1 max-w-[68ch]">A short passage with questions of one type, about 10 minutes. No timer and no band: see which trap caught you on every wrong answer.</p>
      {GROUPS.map((g) => (
        <section key={g.heading} aria-label={g.heading}>
          <GroupHeading>{g.heading}</GroupHeading>
          <ul className={listStyles}>
            {g.types.map((t) => {
              const a = by.get(t);
              return (
                <li key={t}>
                  <button type="button" onClick={() => void start(t)} disabled={!!busy} aria-busy={busy === t} className={cn(rowStyles, 'disabled:cursor-wait')}>
                    <span className="min-w-0 flex-1">
                      <span className="flex flex-wrap items-center gap-2">
                        <span className="type-subheading font-medium">{DRILL_TYPES[t].label}</span>
                        {t === weakest && <Badge tone="warn">Your weakest</Badge>}
                      </span>
                      <span className="type-caption mt-0.5 block text-pretty">{DESC[t]}</span>
                    </span>
                    <span className="type-caption type-num shrink-0 text-right">
                      {a?.done ? (
                        <>
                          <span className="block font-medium text-ink">{Math.round(pct(a) * 100)}% right</span>
                          {a.done} of {a.count} done
                        </>
                      ) : a ? `${a.count} drills` : null}
                    </span>
                    {busy === t ? <Spinner className="size-4 shrink-0" /> : <RowChevron />}
                  </button>
                </li>
              );
            })}
          </ul>
        </section>
      ))}
    </div>
  );
}

const pct = (a: { right: number; total: number }) => a.right / Math.max(a.total, 1);
