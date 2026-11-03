/**
 * The plain-text guide for each public hub page. One source for two places: the server writes it into the HTML shell
 * (crawlers that do not run the SPA read it there), and the web app shows the same words under each hub (SeoGuide),
 * so what a search engine indexes is what a visitor sees. Facts here must match the product (quota.ts plans, pricing).
 */
export type GuideSection = { h: string; p: string };
export type PageCopy = { h1: string; intro: string; sections: GuideSection[] };

const BANDS_NOTE = 'NextBand is practice, not the official test, so every band is an estimate.';

export const PAGE_COPY: Record<string, PageCopy> = {
  '/speaking': {
    h1: 'Free IELTS Speaking practice with a band score',
    intro:
      'Answer real-format IELTS Speaking questions out loud and get a band for each of the four criteria, with every pause, slip and mispronounced word marked in your transcript.',
    sections: [
      {
        h: 'How the IELTS Speaking test works',
        p: 'The Speaking test takes 11 to 14 minutes in three parts. Part 1 is 4 to 5 minutes of questions about familiar topics like your home, work or studies. In Part 2 you get a cue card, one minute to prepare and make notes, and up to two minutes to talk. Part 3 is a 4 to 5 minute discussion of wider questions linked to the Part 2 topic.',
      },
      {
        h: 'Two ways to practise',
        p: 'Talk to the live AI examiner for the whole test: it asks the questions, follows up on what you say, keeps the timing and moves you through all three parts. Or record one part at your own pace: the examiner reads each question aloud and you record your answer.',
      },
      {
        h: 'How your answer is marked',
        p: 'Each answer is scored against the four public IELTS Speaking criteria: Fluency and Coherence, Lexical Resource, Grammatical Range and Accuracy, and Pronunciation. Your result shows the transcript with grammar and vocabulary corrections, where you paused and for how long, your speaking pace, the sounds to work on, and a top-band version of your own answer.',
      },
      {
        h: 'Free and paid',
        p: `A free account gets 10 recorded Speaking tests and 5 live examiner sessions a month. The paid plan, from 499 taka a month, makes recorded Speaking unlimited and gives the live examiner up to 5 sessions a day. ${BANDS_NOTE}`,
      },
    ],
  },
  '/writing': {
    h1: 'Free IELTS Writing Task 1 and Task 2 practice with feedback',
    intro:
      'Write IELTS Academic or General Training answers under exam timing and get a band for each criterion, every mistake underlined with the fix and the reason, and a rewrite one band higher.',
    sections: [
      {
        h: 'How the IELTS Writing test works',
        p: 'The Writing test is 60 minutes for two tasks. Task 1 asks for at least 150 words in about 20 minutes: Academic candidates describe a chart, table, map or process, and General Training candidates write a letter. Task 2 is an essay of at least 250 words in about 40 minutes, and it counts for twice as much as Task 1.',
      },
      {
        h: 'Practise one task or the full test',
        p: 'Pick a Task 1 or Task 2 question from the question bank, or sit the full Writing test with both tasks on one 60-minute clock. You can set the timer to submit when time runs out and block pasting, as in the real test.',
      },
      {
        h: 'How your essay is marked',
        p: 'Your answer is scored against the four public IELTS Writing criteria: Task Achievement (Task Response for Task 2), Coherence and Cohesion, Lexical Resource, and Grammatical Range and Accuracy. Each mistake is located in your own text with a correction and a short reason, and the corrections are collected so the same habits come back as review cards.',
      },
      {
        h: 'Free and paid',
        p: `A free account gets 5 Writing tests a week. The paid plan, from 499 taka a month, makes Writing unlimited. ${BANDS_NOTE}`,
      },
    ],
  },
  '/listening': {
    h1: 'Free IELTS Listening practice tests online',
    intro:
      'Full IELTS Listening tests with exam-style audio and timing, or one part at a time. You get your score and band straight away, with the line in the transcript where each answer was.',
    sections: [
      {
        h: 'How the IELTS Listening test works',
        p: 'The Listening test has four parts and 40 questions and takes about 30 minutes. Part 1 is an everyday conversation, Part 2 an everyday monologue, Part 3 a conversation in an education or training setting, and Part 4 an academic lecture. You hear each recording once. Question types include form and note completion, multiple choice, matching, and map or plan labelling.',
      },
      {
        h: 'Exam mode or practice mode',
        p: 'Exam mode runs the test the way the real one does: the audio plays once and the clock keeps going. Practice mode has no time limit: pause, rewind, slow the audio down to 0.75 times and replay any part.',
      },
      {
        h: 'After the test',
        p: 'Your result shows the raw score out of 40 and an estimated band, the transcript at the exact line that held each answer, why a wrong answer was wrong, and spelling practice for the words you missed.',
      },
      {
        h: 'Free and paid',
        p: `30 Listening tests are free with an account. The paid plan, from 499 taka a month, opens every test. ${BANDS_NOTE}`,
      },
    ],
  },
  '/reading': {
    h1: 'Free IELTS Reading practice tests, Academic and General Training',
    intro:
      'Full IELTS Academic and General Training Reading tests, or one passage at a time. Get your band straight away and see where each answer sits in the passage.',
    sections: [
      {
        h: 'How the IELTS Reading test works',
        p: 'The Reading test has 40 questions and lasts 60 minutes, with no extra time to transfer answers. Academic Reading has three long passages from books, journals and newspapers. General Training Reading uses shorter everyday and work texts in its first sections and one longer passage at the end.',
      },
      {
        h: 'Question types',
        p: 'You will meet True, False, Not Given and Yes, No, Not Given, matching headings, matching information, multiple choice, sentence and summary completion, and short answers. True, False, Not Given trips up most candidates, so NextBand tracks how you handle it across tests.',
      },
      {
        h: 'After the test',
        p: 'Your result shows the raw score out of 40 and an estimated band, the sentence in the passage that holds each answer, and patterns in your mistakes, such as choosing False where the answer was Not Given.',
      },
      {
        h: 'Free and paid',
        p: `30 Reading tests, Academic and General Training, are free with an account. The paid plan, from 499 taka a month, opens every test. ${BANDS_NOTE}`,
      },
    ],
  },
  '/mock': {
    h1: 'Free full IELTS mock test online',
    intro:
      'Sit Listening, Reading, Writing and Speaking in one guided run, the way test day works, and get a band for each skill and one overall band. Free with an account, and made for candidates in Bangladesh.',
    sections: [
      {
        h: 'What is in the mock test',
        p: 'The skills run in test-day order: Listening (about 30 minutes plus a 2-minute check, 40 questions), Reading (60 minutes, 40 questions), Writing (60 minutes for Task 1 and Task 2) and Speaking (11 to 14 minutes, three parts), with the live AI examiner or as a recorded test. There is no pausing inside a timed section. If you leave, you resume with the time already used kept, and Speaking can wait: the mock stays open for 7 days.',
      },
      {
        h: 'Your result',
        p: 'Each skill gets its own band and the four are combined into an overall band, rounded the way IELTS rounds it. Every Writing and Speaking mistake is marked, and Listening and Reading answers point to the line that held them.',
      },
      {
        h: 'Cost',
        p: `The mock test is free with a free account; no card is needed. If you want unlimited practice afterwards, the paid plan is 499 taka for a month or 1,199 taka for three months, paid by bKash, Nagad, Rocket, Upay or card. ${BANDS_NOTE}`,
      },
    ],
  },
  '/bank': {
    h1: 'IELTS question bank: Speaking topics and Writing tasks',
    intro:
      'Browse IELTS Speaking Part 1 topics, Part 2 cue cards and Part 3 questions, and Writing Task 1 and Task 2 questions by part and type. No account is needed to browse, and you can practise any question.',
    sections: [],
  },
  '/pricing': {
    h1: 'NextBand pricing in taka',
    intro:
      'Start free. The paid plan is 499 taka for 1 month or 1,199 taka for 3 months, paid once by bKash, Nagad, Rocket, Upay or card. It does not renew by itself, and an unused plan is fully refunded within 7 days.',
    sections: [
      { h: 'Free, with an account', p: '5 Writing tests a week, 10 recorded Speaking tests a month, 5 live examiner sessions a month, and 30 Listening and 30 Reading tests.' },
      { h: 'Paid plan', p: 'Unlimited Writing tests, unlimited recorded Speaking tests, the live examiner up to 5 times a day and 40 times a month, and every Listening and Reading test.' },
    ],
  },
  '/about': {
    h1: 'About NextBand',
    intro:
      'NextBand is an IELTS practice platform made by Dolphin Commerce, a software business in Mymensingh, Bangladesh. It marks every essay and spoken answer against the public IELTS band descriptors, any time of day, at a price a student can pay.',
    sections: [
      {
        h: 'How scores are worked out',
        p: 'Listening and Reading are marked against the answer key and the raw score out of 40 is converted to a band. Writing and Speaking are scored by AI against the four public band descriptors for each skill, with the reasons and corrections shown. NextBand is practice, not the official test: every band is an estimate, and the official IELTS test is run only by its test partners.',
      },
    ],
  },
  '/contact': {
    h1: 'Contact NextBand',
    intro: 'Email support@nextband.app for help with your account, a payment or anything else. NextBand is made by Dolphin Commerce, Mymensingh, Bangladesh.',
    sections: [],
  },
};
