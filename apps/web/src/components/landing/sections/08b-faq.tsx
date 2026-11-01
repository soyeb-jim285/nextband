import { bdt, PAID_PLANS } from '@ielts/core';
import type { ReactNode } from 'react';
import { COMPANY } from '@/components/legal/Legal';
import { SectionHead } from '../SectionHead';
import './08b-faq.css';

const m = PAID_PLANS.month;
const q = PAID_PLANS.quarter;

// Every answer is sourced: quotas from apps/server/src/quota.ts PLANS + routes/lr.ts LR_PLANS (server-only, so written
// out here; keep in step), prices from @ielts/core, payment from checkout/index.tsx, refunds from _legal/refunds.tsx,
// pending uploads from speaking/PendingUploads.tsx, "not official" and deletion from the terms and privacy pages.
const QA: [string, ReactNode][] = [
  ['Is this the official IELTS test?', 'No. NextBand is practice. Your bands are estimates to guide what you work on, not official IELTS results, and a real test may give you a different band.'],
  ['How is my band worked out?', 'Writing and Speaking are marked by AI against the public band descriptors, one criterion at a time, with the fix beside each mistake. Listening and Reading are marked against the answer key and converted to a band.'],
  ['What can I do for free?', 'Without an account: 2 Writing tests every 7 days, 5 recorded Speaking tests and 3 live examiner sessions every 30 days, and 10 Listening and 10 Reading tests. A free account raises each of those, adds the full mock test, and keeps your results, mistakes and review cards together.'],
  ['What happens to tests I take without an account?', 'They move to your account when you sign up, with their results, so nothing you did as a guest is lost.'],
  ['Academic or General Training?', 'Both. Reading and Writing Task 1 have General Training versions: without an account you get 7 Academic and 3 General Training Reading tests, and Task 1 covers charts, maps and processes as well as letters.'],
  ['What does a paid plan add?', `Unlimited Writing and recorded Speaking, the live examiner up to 5 times a day (40 every 30 days), and every one of our Listening and Reading tests. ${m.label} is ${bdt(m.bdt)} and ${q.label} is ${bdt(q.bdt)}.`],
  ['How do I pay, and can I get my money back?', "You pay once in taka through PayStation with bKash, Nagad, Rocket, Upay, Visa or Mastercard. Nothing renews by itself. If you haven't used the plan, ask within 7 days for a full refund; we reply within 2 working days."],
  ['Does it work on my phone?', "Yes, in your phone's browser. Speaking uses your phone's microphone, and if a recording can't upload on a weak signal, it waits on your phone until it can."],
  ['What happens to my recordings?', 'Your answers, essays and recordings stay yours. Deleting your account removes your tests, recordings, results and review cards.'],
];

/** Act 8b: type on paper after the wireframes. The head over nine answers (the first two open), in two columns on a wide screen. */
export function Faq() {
  return (
    <section id="faq" aria-labelledby="faq-title" className="lp-faq">
      <div className="lp-container lp-faq-inner">
        <SectionHead id="faq-title" layout="split" title="Straight answers." sub="What NextBand is, how it marks you, and what you pay." />
        <div className="lp-faq-body">
          <ol role="list" className="lp-faq-list">
            {QA.map(([question, answer], i) => (
              <li key={question} className="lp-faq-item">
                <details className="lp-faq-d" open={i < 2 || undefined}>
                  <summary className="lp-faq-sum">
                    <h3 className="type-heading lp-faq-q">{question}</h3>
                  </summary>
                  <p className="type-body lp-faq-a">{answer}</p>
                </details>
              </li>
            ))}
          </ol>
          <p className="lp-label lp-faq-more">
            Something else?{' '}
            <a className="lp-link" href={`mailto:${COMPANY.email}`}>
              {COMPANY.email}
            </a>
          </p>
        </div>
      </div>
    </section>
  );
}
