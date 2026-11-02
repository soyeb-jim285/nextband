import { ChevronRight, Search } from 'lucide-react';
import { Link } from '@tanstack/react-router';
import type { CSSProperties } from 'react';
import { Stage } from '../Stage';
import { vars, WireCard, WirePill } from '../wire';
import './07b-bank.css';

const ICON = { size: 16, strokeWidth: 1.75, 'aria-hidden': true } as const;
const Q = 'bus';

type Row = readonly [type: string, prompt: string, topic: string];

// Every prompt is verbatim from the public generated bank (restricted=false, source=generated); labels from lib/writing typeLabel.
const SPEAKING: Row[] = [
  ['Part 1 opening topic', 'Do you live in a house or a flat?', 'Accommodation'],
  ['Part 1 topic', 'Where do you notice adverts most in your daily life?', 'Advertisements'],
  ['Cue card', 'Describe a beautiful view you remember.', 'Places'],
  ['Cue card', 'Describe a childhood friend you remember well.', 'People'],
  ['Part 3 (linked to cue card)', 'What kinds of scenery are people in your country proud of?', 'Places'],
];
// Index 1 is the search hit: it stays and slides to the top when the search runs.
const WRITING: Row[] = [
  ['Line graph', 'Average one-way commuting time in two cities, 2005 to 2025', 'Transport'],
  ['Formal letter', 'The local bus company has announced that it will stop running the bus service that you use every day to get to work.', 'Transport'],
  ['Two-part question', 'More and more adults are returning to education in their forties and fifties.', 'Education'],
  ['Problem & solution', 'As people live longer, the number of elderly people in many countries is rising, placing pressure on health and care services.', 'Health'],
  ['Opinion', 'It has been suggested that artificial intelligence tutors will soon be so effective that human teachers will no longer be needed in secondary schools.', 'Technology'],
];
const HIT = 1;
// More real Writing prompts that contain "bus" (substring, as the bank searches), filling the slots under the hit.
const MATCHES: Row[] = [
  ['Problem & solution', 'Many rural areas have lost most of their bus services over the past decade, leaving older residents and young people without reliable access to shops, hospitals and jobs.', 'Transport'],
  ['Formal letter', "From the start of this term, the school bus that serves your daughter's secondary school leaves 25 minutes earlier in the morning.", 'Education'],
  ['Discussion', 'Some people think that advertising on billboards, bus shelters and the sides of buses makes towns more lively and interesting.', 'Advertising'],
  ['Advantages & disadvantages', 'Many cities now charge drivers who enter the busiest central areas during working hours.', 'Transport'],
];

/** The prompt with every "bus" wrapped in a mark whose underline draws when the search lands. */
const marked = (text: string) => text.split(/(bus)/i).map((part, i) => (i % 2 ? <span key={i} className="lp-bank-mark">{part}</span> : part));

function Rows({ rows, hit, mark }: { rows: Row[]; hit?: number; mark?: boolean }) {
  return rows.map(([type, prompt, topic], i) => (
    <li key={prompt} className={i === hit ? 'lp-bank-row lp-bank-row--hit' : 'lp-bank-row'} style={vars({ '--i': i })}>
      <span className="lp-chip lp-bank-type">{type}</span>
      <span className="type-reading-sm lp-bank-prompt">{mark || i === hit ? marked(prompt) : prompt}</span>
      <span className="type-caption lp-bank-topic">{topic}</span>
      <WirePill className="lp-bank-go">Practise</WirePill>
      <ChevronRight {...ICON} className="lp-bank-chev" />
    </li>
  ));
}

function Tabs({ tabs, className }: { tabs: string[]; className: string }) {
  return (
    <span className={`lp-bank-tabs ${className}`}>
      {tabs.map((t, i) => (
        <span key={t} className={i === 0 ? 'lp-bank-tab lp-bank-tab--on' : 'lp-bank-tab'}>
          {t}
        </span>
      ))}
    </span>
  );
}

const SKILLS = [
  ['Speaking', '/speaking'],
  ['Writing', '/writing'],
  ['Listening', '/listening'],
  ['Reading', '/reading'],
] as const;

/**
 * Act 7b: the question bank, open without an account. The H2 sits in a search field. Steps: Speaking list, Writing list
 * (the base: reduced motion and no-pin views rest here), then "bus" typed and the real matches. No counts anywhere.
 */
export function Bank() {
  return (
    <section id="bank" className="lp-bank">
      <Stage steps={3} track="150svh" className="lp-bank-track lp-track--mobile-flat" play>
        <div className="lp-container lp-bank-frame">
          <div className="lp-bank-head">
            <div className="lp-bank-field lp-emerge" style={{ '--order': 0 } as CSSProperties}>
              <Search size={24} strokeWidth={1.5} aria-hidden className="lp-bank-field-icon" />
              <h2 id="bank-title" className="lp-h2 lp-bank-h2">
                Every question, open to search.
                <span aria-hidden className="lp-bank-caret" />
              </h2>
            </div>
            <p className="lp-sub lp-emerge" style={{ '--order': 1 } as CSSProperties}>
              Browse every Speaking topic and Writing task by skill, part and type. No account needed.
            </p>
          </div>
          <p className="sr-only">
            Illustration: the question bank filtered to Speaking, then to Writing, then searched for 'bus'. Rows show real prompts such as 'Describe a beautiful view you remember.' and 'The local bus company has announced that it will stop running the bus service that you use every day to get to work.'
          </p>
          <WireCard className="lp-bank-card">
            <div className="lp-bank-tools" aria-hidden="true">
              <span className="lp-bank-seg">
                <span className="lp-bank-seg-thumb" />
                <span className="lp-bank-seg-opt lp-bank-seg-opt--s">Speaking</span>
                <span className="lp-bank-seg-opt lp-bank-seg-opt--w">Writing</span>
              </span>
              <span className="lp-bank-tabset">
                <Tabs className="lp-bank-tabs--s" tabs={['All', 'Part 1', 'Part 2', 'Part 3']} />
                <Tabs className="lp-bank-tabs--w" tabs={['All', 'T1 Academic', 'T1 General', 'Task 2']} />
              </span>
              <span className="lp-bank-search">
                <Search {...ICON} />
                <span className="lp-bank-search-text">
                  <span className="type-caption lp-bank-placeholder">Search titles and questions</span>
                  <span className="type-body lp-bank-typed">{Q}</span>
                </span>
              </span>
            </div>
            <div className="lp-bank-list" aria-hidden="true" data-lp-play>
              <ul className="lp-bank-layer lp-bank-layer--s">
                <Rows rows={SPEAKING} />
              </ul>
              <ul className="lp-bank-layer lp-bank-layer--w">
                <Rows rows={WRITING} hit={HIT} />
              </ul>
              <ul className="lp-bank-layer lp-bank-layer--q">
                <Rows rows={MATCHES} mark />
              </ul>
            </div>
          </WireCard>
        </div>
      </Stage>
      <div className="lp-container lp-act-cta lp-act-cta--centre">
        <Link to="/bank" className="lp-btn lp-btn--secondary">
          Open the question bank
        </Link>
        <ul className="lp-bank-skills" aria-label="Practise by skill">
          {SKILLS.map(([label, to]) => (
            <li key={to}>
              <Link to={to} className="lp-link">
                {label}
              </Link>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
