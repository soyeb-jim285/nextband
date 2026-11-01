import { WritingDesk, type Seg } from '../WritingDesk';
import { Pen } from '../wire';

/*
 * The Writing act, Task 2, on the shared Writing desk (WritingDesk.tsx): the essay written beside the question, marked,
 * walked mistake by mistake (three different kinds) and given back improved.
 * The question, the essay and its mistakes are written for this page.
 */

const PARAS: Seg[][] = [
  [
    'These days, many teenagers spend their evenings on screens rather than in their own neighbourhoods, so it is easy to see why some people want community work to be compulsory. ',
    { k: 1, text: 'There are good and bad sides to this idea.', type: 'Unclear position' },
  ],
  [
    'On the one hand, volunteering teaches skills that school rarely does. A student who helps at a food bank learns to organise stock, talk to strangers and turn up on time when nobody is paying them. My cousin spent one summer repairing bicycles for a refugee charity, and he still says it taught him more about patience than any classroom. Older residents gain too, since a teenager who reads to them or fixes a fence brings company as well as help. Work like this also helps young people feel part of ',
    { k: 2, text: 'the society', type: 'Grammar: article' },
    ', and even a few hours a month can ',
    { k: 0, text: 'do a big difference', type: 'Vocabulary: collocation' },
    '.',
  ],
  [
    { k: 3, text: 'Moreover', type: 'Cohesion: linking word' },
    ', forcing teenagers to give up their free time could easily backfire. Many already juggle exams, part-time jobs and family duties, and one more obligation would leave them exhausted. Once volunteering is required, it also stops being a choice, so students may simply count the hours instead of caring about the people they help. At my own school, a compulsory charity week mostly produced selfies and complaints, not lasting habits.',
  ],
  [
    'In conclusion, community work is clearly worthwhile, but it should be encouraged through school credits, flexible schemes or local awards rather than imposed. Young people tend to give the most when they choose to give, and that is worth protecting.',
  ],
];

export function Essay() {
  return (
    <WritingDesk
      id="essay"
      title={
        <>
          Your essay, marked in <Pen>your own words</Pen>.
        </>
      }
      sub="Task 2 runs on the real 40-minute clock. Your result scores all four criteria, walks you through each mistake by type, and gives your essay back improved."
      srText="Illustration: a Task 2 essay on whether young people should have to do unpaid community work is written beside the question. The result gives a sample band of 7.0. The marked essay then walks three different mistakes: the introduction never gives a position (unclear position), 'the society' should be 'society' (grammar: article), and 'Moreover' should be 'However' because the paragraph turns to the other side (cohesion: linking word). Last, the whole essay comes back improved, with a clear position."
      task="Task 2"
      clock="11:48"
      split="minmax(0, 34fr) minmax(0, 66fr)"
      left={
        <>
          <p>
            <b>Some people think that young people should be required to do unpaid work in their local community in their free time.</b>
          </p>
          <p>To what extent do you agree or disagree?</p>
          <p>Give reasons for your answer and include any relevant examples from your own knowledge or experience.</p>
          <p>Write at least 250 words.</p>
        </>
      }
      paras={PARAS}
      marks={4}
      band="7.0"
      criteria={[
        ['Task Response', '6.0'],
        ['Coherence & Cohesion', '7.0'],
        ['Lexical Resource', '7.5'],
        ['Grammar', '7.0'],
      ]}
      mistakes={[
        {
          type: 'Unclear position',
          criterion: 'Task Response',
          wrote: 'There are good and bad sides to this idea.',
          fix: 'I agree this work is valuable, but making it compulsory would do more harm than good.',
          why: 'The question asks how far you agree. Say where you stand in the introduction, then keep to it.',
        },
        {
          type: 'Grammar: article',
          criterion: 'Grammar',
          wrote: 'the society',
          fix: 'society',
          why: 'Society in general takes no article. Use "the" only for a particular one: the society we live in.',
        },
        {
          type: 'Cohesion: linking word',
          criterion: 'Coherence & Cohesion',
          wrote: 'Moreover',
          fix: 'However',
          why: '"Moreover" adds a point on the same side. This paragraph turns to the other side, so it needs a contrast.',
        },
      ]}
      better={{
        lead: 'Your essay with a clear position and the mistakes fixed. Study what changed and why. Don’t memorise it.',
        paras: [
          [
            'These days, many teenagers spend their evenings on screens rather than in their neighbourhoods, so it is easy to see why some people want community work to be compulsory. ',
            { ins: 'While I agree that this kind of work is valuable, I believe making it a requirement would do more harm than good', was: 'There are good and bad sides to this idea.', why: 'States your position, which the question asks for, in the introduction.' },
            ', ',
            { ins: 'because it would turn generosity into an obligation', why: 'Gives the reason that the rest of the essay develops.' },
            '.',
          ],
          [
            'On the one hand, volunteering teaches skills that school rarely does. A student who helps at a food bank learns to organise stock, talk to strangers and turn up on time when nobody is paying them. My cousin spent one summer repairing bicycles for a refugee charity, and he still says it taught him more about patience than any classroom. Older residents gain too, since a teenager who reads to them or fixes a fence brings company as well as help. Work like this also helps young people feel part of ',
            { ins: 'society', was: 'the society', why: 'No article for society in general.' },
            ', and even a few hours a month can ',
            { ins: 'make a real difference', was: 'do a big difference', why: 'Collocation: you make a difference, not do one.' },
            '.',
          ],
          [
            { ins: 'However', was: 'Moreover', why: 'A contrast, because this paragraph argues the other side.' },
            ', forcing teenagers to give up their free time could easily backfire. Many already juggle exams, part-time jobs and family duties, and one more obligation would leave them exhausted. Once volunteering is required, it also stops being a choice, so students may simply count the hours instead of caring about the people they help. At my own school, a compulsory charity week mostly produced selfies and complaints ',
            { ins: 'rather than', was: ', not', why: 'A smoother link between the two halves of the sentence.' },
            ' lasting habits.',
          ],
          [
            'In conclusion, ',
            { ins: 'while', why: 'Joins the two ideas into one sentence that weighs them.' },
            ' community work is clearly worthwhile, ',
            { ins: 'it', why: 'The subject for the second half of the joined sentence.' },
            ' should be encouraged through school credits, flexible schemes or local awards rather than imposed. Young people tend to give the most when they choose to give, and that ',
            { ins: 'freedom', why: 'Names what is worth protecting, instead of a vague “that”.' },
            ' is worth protecting.',
          ],
        ],
      }}
      cta={{ to: '/writing', label: 'Write a Task 2 essay free' }}
    />
  );
}
