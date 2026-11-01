import { ArrowRight } from 'lucide-react';
import { Pen, StartFree, vars } from '../wire';
import './08c-close.css';

// A sample result (the owner asked for band values here); the overall is the mean rounded to the half band: 6.875 -> 7.0.
const SKILLS = [
  ['Listening', '7.5'],
  ['Reading', '7.0'],
  ['Writing', '6.5'],
  ['Speaking', '6.5'],
] as const;
// This week's three fixes, the same ones the coach plan (07-plan) shows.
const FIXES = [
  ['a best day', 'the best day'],
  ['False', 'Not given'],
  ['long pause', 'keep going'],
] as const;

/**
 * Act 8c, the close. It pays off the hero's "Overall ?.?" slip with a sample result: a band for each skill and this
 * week's three fixes under it, beside one action. The bands are tagged Sample.
 */
export function Close() {
  return (
    <section id="close" aria-labelledby="close-title" className="lp-close">
      <div className="lp-container lp-close-inner">
        <div className="lp-close-copy">
          <h2 id="close-title" className="lp-h2 lp-emerge">
            Your first full mock, marked <Pen kind="loop">tonight</Pen>.
          </h2>
          <p className="lp-sub lp-emerge" style={vars({ '--order': 1 })}>
            Know your band. Know what to fix. Then come back and fix it.
          </p>
          <div className="lp-close-actions">
            <StartFree className="lp-close-btn" />
            <a href="#pricing" className="lp-link lp-close-link">
              See pricing
            </a>
          </div>
        </div>

        <figure
          className="lp-card lp-close-slip lp-sharpen"
          role="img"
          aria-label="A sample result slip: overall band 7.0, with Listening 7.5, Reading 7.0, Writing 6.5 and Speaking 6.5, and this week's three fixes underneath."
        >
          <div aria-hidden="true">
            <div className="lp-close-top">
              <span className="lp-label">
                Overall band <span className="lp-close-sample">Sample</span>
              </span>
              <b className="lp-close-q">7.0</b>
            </div>
            <ul className="lp-close-skills">
              {SKILLS.map(([s, b]) => (
                <li key={s}>
                  <span className="lp-label">{s}</span>
                  <b className="lp-close-sq">{b}</b>
                </li>
              ))}
            </ul>
            <p className="lp-close-week">This week, fix</p>
            <ul className="lp-close-fixes">
              {FIXES.map(([a, b]) => (
                <li key={a}>
                  <s>{a}</s>
                  <ArrowRight size={14} strokeWidth={1.75} aria-hidden />
                  <b>{b}</b>
                </li>
              ))}
            </ul>
          </div>
        </figure>
      </div>
    </section>
  );
}
