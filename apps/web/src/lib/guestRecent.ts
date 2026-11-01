import { useQuery } from '@tanstack/react-query';
import { call, client } from './api';
import { partsLabel } from './lr';
import { useMe } from './query';

/*
 * A guest's latest tests, with no UI imports: the dashboard's GuestRecent list and the landing page (ContinueStrip, the
 * hero's Continue button) share it, so the landing never pulls the list's row, badge and remove-attempt code.
 */

type Skill = 'speaking' | 'writing' | 'listening' | 'reading';
export type Row = { id: string; skill: Skill; title: string; part: string; mode: string; at: string; band: number | null; state: 'done' | 'open' | 'scoring' | 'failed' };

/** A guest's latest tests (newest first, at most 5); empty for an account, a visitor, or a guest with none yet. */
export function useGuestRecent(skill?: Skill): Row[] {
  const guest = !!useMe().data?.user.isAnonymous;
  const sp = skill === 'speaking' || skill === 'writing' ? skill : undefined;
  const wantSp = !skill || !!sp;
  const wantLr = !skill || skill === 'listening' || skill === 'reading';
  const a = useQuery({ enabled: guest && wantSp, queryKey: ['guest-recent', 'attempts', sp], queryFn: () => call(client.GET('/api/attempts', { params: { query: { skill: sp } } })) });
  const l = useQuery({ enabled: guest && wantLr, queryKey: ['guest-recent', 'lr'], queryFn: () => call(client.GET('/api/lr/attempts')) });

  const rows: Row[] = [
    ...(a.data?.items ?? []).map((x): Row => ({ id: x.id, skill: x.skill, title: x.promptTitle, part: `${x.skill === 'speaking' ? 'Part' : 'Task'} ${x.part}`, mode: x.mode, at: x.createdAt, band: x.overall, state: x.status === 'done' ? 'done' : x.status === 'recording' ? 'open' : x.status === 'failed' ? 'failed' : 'scoring' })),
    ...(l.data?.items ?? []).filter((x) => !skill || x.skill === skill).map((x): Row => ({ id: x.id, skill: x.skill, title: x.title, part: `${x.skill === 'listening' ? 'Listening' : 'Reading'}${x.parts ? `, ${partsLabel(x.skill, x.parts)}` : ''}`, mode: x.mode, at: x.startedAt, band: x.band, state: x.status === 'submitted' ? 'done' : 'open' })),
  ]
    .sort((p, q) => q.at.localeCompare(p.at))
    .slice(0, 5);
  return guest ? rows : [];
}

/** Where a row opens: a Listening/Reading test still running resumes, everything else opens its result. */
export const rowLink = (r: Row) =>
  ({ to: r.skill === 'listening' || r.skill === 'reading' ? (r.state === 'open' ? '/lr/run/$attemptId' : '/lr/result/$attemptId') : r.skill === 'speaking' ? '/speaking/result/$attemptId' : '/writing/result/$attemptId', params: { attemptId: r.id } }) as const;
