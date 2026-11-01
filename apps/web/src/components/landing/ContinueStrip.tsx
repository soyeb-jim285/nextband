import { Link } from '@tanstack/react-router';
import { rowLink, useGuestRecent, type Row } from '@/lib/guestRecent';

const SKILL = { speaking: 'Speaking', writing: 'Writing', listening: 'Listening', reading: 'Reading' } as const;

// The verb says what the link does: a running Listening or Reading test resumes, anything else opens its result.
export const verb = (r: Row) => (r.state === 'open' && (r.skill === 'listening' || r.skill === 'reading') ? 'Resume' : 'See result');

/**
 * For a guest (anonymous session) who already took tests: one slim row under the header that reopens the latest one.
 * Nothing for a brand-new visitor or while the list loads. No band figure here: the landing page never shows one.
 */
export function ContinueStrip() {
  const latest = useGuestRecent()[0];
  if (!latest) return null;
  return (
    <div className="lp-continue">
      <div className="lp-container lp-continue-row">
        <span className="lp-continue-label">{latest.state === 'open' ? 'Where you left off' : 'Your last test'}</span>
        <Link {...rowLink(latest)} className="lp-continue-link">
          {verb(latest)}: {latest.title}
        </Link>
        <span className="lp-continue-meta">{latest.skill === 'speaking' || latest.skill === 'writing' ? `${SKILL[latest.skill]} ${latest.part}` : latest.part}</span>
      </div>
    </div>
  );
}
