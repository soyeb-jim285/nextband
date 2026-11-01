import { bdt, PAID_PLANS } from '@ielts/core';
import { Link } from '@tanstack/react-router';
import { StartFree, vars } from '../wire';
import './08-pricing.css';

const m = PAID_PLANS.month;
const q = PAID_PLANS.quarter;

// What each plan includes. Quotas from apps/server/src/quota.ts:24-26 (PLANS guest / community / subscriber) and the
// Listening and Reading sets from apps/server/src/routes/lr.ts:37-49 (LR_PLANS: guests 10 of each, a free account 20
// more). The mock, mistakes log and review cards are behind AccountGate (routes/_app/mock/index.tsx, mistakes.tsx,
// review.tsx); a guest session gets a coach plan (routes/_app/coach.tsx). Keep in step with components/pricing/Plans.tsx.
const COLS = ['Without an account', 'Free account', 'Paid'] as const;
const ROWS: [string, string, string, string][] = [
  ['Writing tests', '2 / week', '5 / week', 'Unlimited'],
  ['Recorded Speaking', '5 / month', '10 / month', 'Unlimited'],
  ['Live examiner', '3 / month', '5 / month', '5 a day, 40 / month'],
  ['Listening and Reading', '10 of each', '30 of each', 'Every test'],
  ['Full mock test', 'No', 'Yes', 'Yes'],
  ['Mistakes log and review cards', 'No', 'Yes', 'Yes'],
  ['Coach plan', 'Yes', 'Yes', 'Yes'],
];

const METHODS = ['bKash', 'Nagad', 'Rocket', 'Upay', 'Visa', 'Mastercard'];

const order = (n: number) => vars({ '--order': n });

/** Act 8, the offer: three plan cards (free, 1 month, 3 months), how to pay, and a ruled ledger of what each plan includes with the Paid column lifted. */
export function Pricing() {
  return (
    <section id="pricing" aria-labelledby="pricing-title" className="lp-pricing">
      <div className="lp-container lp-pricing-inner">
        <div className="lp-pricing-head">
          <h2 id="pricing-title" className="lp-h2 lp-emerge">
            Start tonight.
          </h2>
          <p className="lp-sub lp-emerge" style={order(1)}>
            Free to begin. Pay once in taka if you stay.
          </p>
        </div>

        <div className="lp-pricing-plans">
          <article className="lp-offer-card" aria-labelledby="plan-free">
            <h3 id="plan-free" className="lp-offer-name">
              Free
            </h3>
            <p className="lp-pricing-focal">
              <span className="lp-pricing-amount" aria-hidden="true">
                {bdt(0)}
              </span>
              <span className="sr-only">0 taka</span>
            </p>
            <p className="lp-offer-note">The full mock, 5 essays a week and your coach plan. No card.</p>
            <Link to="/signup" className="lp-btn lp-btn--secondary lp-pricing-cta">
              Create a free account
            </Link>
          </article>
          <article className="lp-offer-card lp-offer-card--main" aria-labelledby="plan-month">
            <h3 id="plan-month" className="lp-offer-name">
              {m.label}
            </h3>
            <p className="lp-pricing-focal">
              <span className="lp-pricing-amount" aria-hidden="true">
                {bdt(m.bdt)}
              </span>
              <span className="sr-only">{m.bdt.toLocaleString('en-IN')} taka</span>
            </p>
            <p className="lp-offer-note">One payment, no auto-renewal.</p>
            <Link to="/checkout" search={{ plan: 'month' }} className="lp-btn lp-btn--primary lp-pricing-cta">
              Buy 1 month
            </Link>
          </article>
          <article className="lp-offer-card" aria-labelledby="plan-quarter">
            <h3 id="plan-quarter" className="lp-offer-name">
              {q.label}
            </h3>
            <p className="lp-pricing-focal">
              <span className="lp-pricing-amount" aria-hidden="true">
                {bdt(q.bdt)}
              </span>
              <span className="sr-only">{q.bdt.toLocaleString('en-IN')} taka</span>
            </p>
            <p className="lp-offer-note">
              {bdt(Math.round(q.bdt / 3))} a month. Save {bdt(m.bdt * 3 - q.bdt)}.
            </p>
            <Link to="/checkout" search={{ plan: 'quarter' }} className="lp-btn lp-btn--secondary lp-pricing-cta">
              Buy 3 months
            </Link>
          </article>
        </div>

        <div className="lp-pricing-pay">
          <span className="lp-label">Pay with</span>
          <ul role="list" className="lp-pricing-methods">
            {METHODS.map((name) => (
              <li key={name} className="lp-chip">
                {name}
              </li>
            ))}
          </ul>
          <span className="lp-label">Unused? Full refund within 7 days.</span>
        </div>

        <details className="lp-pricing-more">
          <summary className="lp-pricing-sum">
            <span>What each plan includes</span>
          </summary>
          <div className="lp-pricing-scroll">
          <table className="lp-pricing-table">
            <caption className="sr-only">What each plan includes</caption>
            <thead>
              <tr>
                <td />
                {COLS.map((c) => (
                  <th key={c} scope="col">
                    {c}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {ROWS.map(([name, ...cells]) => (
                <tr key={name}>
                  <th scope="row">{name}</th>
                  {cells.map((c, i) => (
                    <td key={i} data-label={COLS[i]}>
                      {c}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
          </div>
        </details>

        <div className="lp-pricing-actions">
          <p className="lp-label lp-pricing-free">Free needs no card. Practice tests need no account either, and they move to your account when you sign up.</p>
          <StartFree className="lp-pricing-cta" />
          <Link to="/pricing" className="lp-link lp-pricing-full">
            See full pricing
          </Link>
        </div>
      </div>
    </section>
  );
}
