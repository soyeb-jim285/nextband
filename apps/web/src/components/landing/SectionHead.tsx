import type { CSSProperties, ReactNode } from 'react';

/**
 * An act's head. `split` (default): the H2 in the left half, the sub in the right, baseline aligned (stacked under
 * 768px). `stacked`: H2 over sub, left. `centred`: both on the axis. `label` names the H2 when
 * its visible title is decorated (pills, chips) and would read oddly.
 * `end`: H2 over sub in the right half (04b-task1). Both emerge on scroll. `id` goes on the H2 so the section can point
 * `aria-labelledby` at it. Put it inside `.lp-container`. Sections whose head is marked up or sits inside a card
 * (03-address, 04-essay, 05-examiner, 07b-bank, 08-pricing) render their own `h2.lp-h2` instead of a new variant.
 */
export function SectionHead({ id, title, sub, label, layout = 'split' }: { id: string; title: ReactNode; sub?: ReactNode; label?: string; layout?: 'split' | 'stacked' | 'centred' | 'end' }) {
  return (
    <div className={`lp-head lp-head--${layout}`}>
      <h2 id={id} aria-label={label} className="lp-h2 lp-emerge" style={{ '--order': 0 } as CSSProperties}>
        {title}
      </h2>
      {sub && (
        <p className="lp-sub lp-emerge" style={{ '--order': 1 } as CSSProperties}>
          {sub}
        </p>
      )}
    </div>
  );
}
