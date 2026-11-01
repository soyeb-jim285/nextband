import { WritingDesk, type Seg } from '../WritingDesk';
import { Pen, vars } from '../wire';
import './04b-task1.css';

/*
 * The Writing act, Task 1 Academic, on the shared Writing desk (WritingDesk.tsx). The line graph draws itself as the act
 * scrolls in, then the report is written, marked, walked mistake by mistake and given back improved.
 * The data, the report and its mistakes are written for this page; the numbers in the report match the graph.
 */

// Households (%) by connection type. Mobile overtakes broadband between 2015 and 2020 (about 2017).
const YEARS = [2000, 2005, 2010, 2015, 2020];
const LINES = [
  { name: 'Mobile', values: [0, 3, 20, 58, 85] },
  { name: 'Broadband', values: [5, 36, 62, 70, 66] },
  { name: 'Dial-up', values: [40, 31, 8, 2, 0] },
];
const x = (i: number) => 34 + i * 54;
const y = (v: number) => 158 - v * 1.4;

function Graph() {
  return (
    <svg className="lp-t1-graph" viewBox="0 0 300 186" role="presentation">
      {[0, 25, 50, 75, 100].map((v) => (
        <g key={v}>
          <line className="lp-t1-grid" x1="34" x2="250" y1={y(v)} y2={y(v)} />
          <text className="lp-t1-tick" x="28" y={y(v) + 3.5} textAnchor="end">
            {v}
          </text>
        </g>
      ))}
      {YEARS.map((yr, i) => (
        <text key={yr} className="lp-t1-tick" x={x(i)} y="176" textAnchor="middle">
          {yr}
        </text>
      ))}
      {LINES.map((l, k) => (
        <g key={l.name} className={`lp-t1-line lp-t1-line--${k}`} style={vars({ '--k': k })}>
          <path pathLength={1} d={l.values.map((v, i) => `${i ? 'L' : 'M'}${x(i)} ${y(v)}`).join(' ')} />
          {l.values.map((v, i) =>
            k === 1 ? (
              <rect key={i} className="lp-t1-pt" x={x(i) - 3} y={y(v) - 3} width="6" height="6" style={vars({ '--j': i })} />
            ) : (
              <circle key={i} className="lp-t1-pt" cx={x(i)} cy={y(v)} r="3" style={vars({ '--j': i })} />
            ),
          )}
          <text className="lp-t1-name" x="256" y={y(l.values[4]!) + 4}>
            {l.name}
          </text>
        </g>
      ))}
    </svg>
  );
}

const PARAS: Seg[][] = [
  ['The line graph shows how households in one country connected to the internet between 2000 and 2020, comparing dial-up, fixed broadband and mobile connections.'],
  ['Overall, dial-up all but disappeared over the period, while mobile access grew the fastest and ended up as the most common way to get online. Broadband rose strongly at first but levelled off after 2015.'],
  [
    'In 2000, dial-up was used by 40% of homes, compared with just 5% for broadband. By 2010, the proportion of homes using dial-up ',
    { k: 1, text: 'were', type: 'Grammar: agreement' },
    ' down to only 8%, whereas broadband had climbed steadily to 62%. Mobile connections, almost unheard of at the start, now reached a fifth of households.',
  ],
  [
    'The final decade brought the biggest shift. Mobile use soared to 85% by 2020, overtaking broadband ',
    { k: 2, text: 'in 2010', type: 'Task: data accuracy' },
    '. Broadband ',
    { k: 3, text: 'made a peak of', type: 'Vocabulary: collocation' },
    ' 70% in 2015 and ',
    { k: 0, text: 'than', type: 'Spelling' },
    ' dipped slightly to 66%. Dial-up, meanwhile, had vanished completely, replaced by faster ways of getting online.',
  ],
];

export function Task1() {
  return (
    <WritingDesk
      id="task1"
      title={
        <>
          Describe the graph. See <Pen>what it cost you</Pen>.
        </>
      }
      sub="Task 1 runs like the real test: 20 minutes, 150 words, the chart beside your answer. Every mistake comes back named, with the fix, the criterion it hurt and an improved version of your own report."
      srText="Illustration: a Task 1 Academic line graph of households using dial-up, broadband and mobile internet from 2000 to 2020 draws itself, and a report is written beside it. The result gives a sample band of 6.5. The marked report then walks three mistakes: 'were' should be 'was' (grammar: agreement), mobile overtook broadband in about 2017, not 2010 (task: data accuracy), and 'made a peak of' should be 'peaked at' (vocabulary: collocation). Last, the whole report comes back improved."
      task="Task 1 Academic"
      clock="06:12"
      split="minmax(0, 42fr) minmax(0, 58fr)"
      left={
        <>
          <p className="lp-t1-ask">The graph below shows the percentage of households in one country using three types of internet connection from 2000 to 2020.</p>
          <p className="lp-t1-more">Summarise the information by selecting and reporting the main features, and make comparisons where relevant.</p>
          <p className="lp-t1-axis">Households (%)</p>
          <Graph />
        </>
      }
      paras={PARAS}
      marks={4}
      band="6.5"
      criteria={[
        ['Task Achievement', '6.0'],
        ['Coherence & Cohesion', '7.0'],
        ['Lexical Resource', '6.5'],
        ['Grammar', '6.5'],
      ]}
      mistakes={[
        {
          type: 'Grammar: agreement',
          criterion: 'Grammar',
          wrote: 'were',
          fix: 'was',
          why: 'The subject is "the proportion", which is singular. The verb agrees with it, not with "homes".',
        },
        {
          type: 'Task: data accuracy',
          criterion: 'Task Achievement',
          wrote: 'in 2010',
          fix: 'in around 2017',
          why: 'In 2010 mobile was at 20% and broadband at 62%. The lines cross between 2015 and 2020. A wrong figure costs Task Achievement.',
        },
        {
          type: 'Vocabulary: collocation',
          criterion: 'Lexical Resource',
          wrote: 'made a peak of',
          fix: 'peaked at',
          why: 'For a highest point, use "peak" as a verb with "at": broadband peaked at 70%.',
        },
      ]}
      better={{
        lead: 'Your report with the mistakes fixed and the figures made exact. Study what changed and why. Don’t memorise it.',
        paras: [
          ['The line graph ', { ins: 'illustrates', was: 'shows', why: 'A more precise reporting verb for describing a graph.' }, ' how households in one country connected to the internet between 2000 and 2020, comparing dial-up, fixed broadband and mobile connections.'],
          [
            'Overall, dial-up all but disappeared over the period, while mobile access grew ',
            { ins: 'the most rapidly', was: 'the fastest', why: 'An adverb, because it describes how something grew.' },
            ' and ended up as the most common way to get online. Broadband rose strongly at first but levelled off after 2015.',
          ],
          [
            'In 2000, dial-up was used by 40% of homes, compared with just 5% for broadband. By 2010, the proportion of homes using dial-up ',
            { ins: 'had fallen', was: 'were down', why: 'Fixes the agreement: “the proportion” takes a singular verb. Past perfect suits a change finished by 2010.' },
            ' to only 8%, whereas broadband had climbed steadily to 62%. Mobile connections, almost unheard of at the start, ',
            { ins: 'had reached', was: 'now reached', why: '“By 2010” needs past perfect, and “now” clashes with a past time.' },
            ' a fifth of households.',
          ],
          [
            'The final decade brought the ',
            { ins: 'most dramatic', was: 'biggest', why: 'A stronger, more academic word for the size of a change.' },
            ' shift. Mobile use ',
            { ins: 'surged from 20% to 85%', was: 'soared to 85%', why: 'Both figures make the size of the jump clear.' },
            ', overtaking broadband ',
            { ins: 'in around 2017', was: 'in 2010', why: 'Fixes the data: the two lines cross between 2015 and 2020.' },
            ' ',
            { ins: 'to become the most popular type of connection', why: 'Says what the crossover means, not just when it happened.' },
            '. Broadband, ',
            { ins: 'by contrast', why: 'Signals that broadband went the other way.' },
            ', ',
            { ins: 'peaked at', was: 'made a peak of', why: 'Fixes the collocation: something peaks at a figure.' },
            ' 70% in 2015 and ',
            { ins: 'then', was: 'than', why: 'Fixes the spelling: “then” is about time, “than” compares.' },
            ' dipped slightly to 66%. Dial-up, meanwhile, had vanished completely, replaced by faster ways of getting online.',
          ],
        ],
      }}
      cta={{ to: '/writing', label: 'Try a Task 1 free' }}
    />
  );
}
