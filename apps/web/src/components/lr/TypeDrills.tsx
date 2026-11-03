import { useQuery } from '@tanstack/react-query';
import { useNavigate } from '@tanstack/react-router';
import { DRILL_FAMILIES, type DrillFamily } from '@ielts/core';
import { ArrowRight } from 'lucide-react';
import { useState } from 'react';
import { rowStyles } from '@/components/bank/ListRow';
import { Section } from '@/components/result';
import { Badge, Spinner, toast } from '@/components/ui';
import { ensureSession } from '@/lib/auth';
import { lrProgressQuery, startDrill } from '@/lib/lr';
import { useMe } from '@/lib/query';
import { cn } from '@/lib/utils';

const DESC: Record<DrillFamily, string> = {
  tfng: 'True / False / Not Given and Yes / No / Not Given statements.',
  match: 'Headings, information, features and sentence endings.',
  gap: 'Sentence, summary, note and table completion.',
  mcq: 'One answer, or choose two.',
};
const FAMILIES = Object.keys(DRILL_FAMILIES) as DrillFamily[];

/** Progress reports accuracy per type label (lrTypeLabel); fold the labels into the four drill families. */
const familyOfLabel = (l: string): DrillFamily => (/^(true|yes)/i.test(l) ? 'tfng' : /^matching/i.test(l) ? 'match' : /^multiple choice/i.test(l) ? 'mcq' : 'gap');

/** Reading page: practise one question type. A card starts (or resumes) the next drill of that type. */
export function TypeDrills() {
  const me = useMe().data;
  const progress = useQuery({ ...lrProgressQuery, enabled: !!me });
  const navigate = useNavigate();
  const [busy, setBusy] = useState<DrillFamily | null>(null);

  const acc = new Map<DrillFamily, { right: number; total: number }>();
  for (const t of progress.data?.byType ?? []) {
    if (t.skill !== 'reading') continue;
    const e = acc.get(familyOfLabel(t.label)) ?? { right: 0, total: 0 };
    acc.set(familyOfLabel(t.label), { right: e.right + t.right, total: e.total + t.total });
  }
  const seen = FAMILIES.filter((f) => (acc.get(f)?.total ?? 0) >= 4);
  const weakest = seen.length >= 2 ? seen.reduce((w, f) => (pct(acc.get(f)!) < pct(acc.get(w)!) ? f : w)) : null;

  const start = async (f: DrillFamily) => {
    setBusy(f);
    try {
      await ensureSession(); // a visitor gets a guest session, as for a full test
      await navigate({ to: '/lr/run/$attemptId', params: { attemptId: await startDrill(f) } });
    } catch {
      toast('Could not start the drill. Try again.', { tone: 'bad' });
      setBusy(null);
    }
  };

  return (
    <Section title="Practise one question type" caption="One short passage with questions of a single type. No timer and no band, just the questions you want to get better at." className="mb-10">
      <ul className="grid gap-3 sm:grid-cols-2">
        {FAMILIES.map((f) => {
          const a = acc.get(f);
          return (
            <li key={f}>
              <button type="button" onClick={() => void start(f)} disabled={!!busy} aria-busy={busy === f} className={cn(rowStyles, 'h-full items-start rounded-lg border border-line px-4 before:inset-x-0 disabled:cursor-wait')}>
                <span className="min-w-0 flex-1">
                  <span className="type-subheading flex flex-wrap items-center gap-2">
                    {DRILL_FAMILIES[f].label}
                    {f === weakest && <Badge tone="warn">Your weakest</Badge>}
                  </span>
                  <span className="type-caption mt-0.5 block text-pretty">{DESC[f]}</span>
                  <span className="type-caption type-num mt-1.5 block font-medium text-ink">{a?.total ? `${Math.round(pct(a) * 100)}% right so far (${a.right} of ${a.total})` : 'Not tried yet'}</span>
                </span>
                {busy === f ? <Spinner className="mt-1 size-4 shrink-0" /> : <ArrowRight className="mt-1 size-4 shrink-0 text-accent-text" aria-hidden />}
              </button>
            </li>
          );
        })}
      </ul>
    </Section>
  );
}

const pct = (a: { right: number; total: number }) => a.right / Math.max(a.total, 1);
