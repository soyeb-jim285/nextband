# Reading drill authoring guide (question-type drills)

A drill is one short passage with 6–8 questions of a single type. Learners pick it from **Reading → Practise one question type**. It trains one skill, so every item sets a named trap, and the review tells the learner which trap they fell for. Full tests stay where learners get a band.

Write drills by hand, exactly like full tests: no OpenRouter or other API call. This page covers only what differs from a full test. Everything else follows `reading-authoring.md`, mainly section 5 (rubrics and type rules), section 6 (paraphrase, traps, tiers), section 7 (language and originality) and section 7a (editorial self-review).

## 1. What a drill is

| | Full test (`gen-r-NN`) | Drill (`drill-<type>-<tier>-NN`) |
|---|---|---|
| Passages | 3 | 1, `part: 1` |
| Words | 700–1000 per passage | per subtype, see the table below |
| Paragraphs | 5–10 | per subtype; lettered (`label: "A"`…) for headings and matching information |
| Questions | 40 | per subtype, one group, numbered from 1 |
| Types | mixed | one subtype per drill (table below) |
| Sidecar | review per question | review per question **plus `trap`** |
| Tier | A/B/C per test | A/B/C in the slug and the sidecar |

Slugs: `drill-<subtype>-<tier>-NN`, e.g. `drill-tfng-a-01`, `drill-minfo-b-03`, `drill-summ-c-02`. Number each subtype-and-tier series from 01. The app serves a card's drills by tier, then number, rotating subtypes (all `a-01`s, then all `a-02`s …), so a learner meets tier A before B and C. Write only the slugs you were given.

| Subtype | Card | Group (see `reading-authoring.md` section 5 for its exact rules) | Questions | Words: target (hard) | Paragraphs | Traps (min distinct) |
|---|---|---|---|---|---|---|
| `tfng` | True/False | `tfng` or `ynng` | 6–8 | 250–400 (220–450) | 3–6 | TFNG list (4) |
| `match` | Matching | matching headings | 5–7, one per paragraph | 380–500 (330–550) | 5–7, lettered | `heading-*` (2) |
| `minfo` | Matching | matching information (which paragraph contains…) | 5–7 | 380–500 (330–550) | 5–7, lettered | `info-*` (2) |
| `mfeat` | Matching | matching features (people, dates, places) | 5–7 | 320–480 (280–530) | 4–7 | `feature-*` (2) |
| `mend` | Matching | sentence endings | 5–6 | 280–450 (250–500) | 3–6 | `ending-*` (2) |
| `mcq` | Multiple choice | `mcq` (one answer), or a mix with one choose-TWO pair (`mcq-multi`) | 5–7 | 320–480 (280–530) | 3–6 | `mcq-*` (3) |
| `sent` | Completion | sentence completion | 6–8 | 280–450 (250–500) | 3–6 | `gap-*` (2) |
| `summ` | Completion | summary (with or without a word box), notes, table or flow-chart | 6–8 | 280–450 (250–500) | 3–6 | `gap-*` (2) |
| `diag` | Completion | diagram labelling, with an SVG asset | 5–7 | 250–420 (220–470) | 3–6 | `gap-*` (2) |
| `short` | Completion | short-answer questions | 5–7 | 280–450 (250–500) | 3–6 | `gap-*` (2) |

Within a 10-drill subtype series, vary the forms: `summ` mixes plain summary, word-box summary, notes, a table and a flow-chart; `mcq` puts a choose-TWO pair in about a third of drills; `tfng` keeps YNNG to about a third.

Files:
- `data/lr-drills/<slug>.json`: the drill (`LrTest`).
- `data/lr-drills/enrich/<slug>.json`: the sidecar.

`data/` is private and never committed.

## 2. The drill file

```jsonc
{
  "slug": "drill-tfng-b-01",
  "kind": "drill",
  "skill": "reading",
  "variant": "academic",
  "source": "generated",
  "ref": "Drill TFNG B01",            // "Drill " + type upper-case + " " + tier upper-case + number
  "title": "The Night-Shift Bees",    // the same as the passage title
  "sections": [{
    "part": 1,
    "passage": { "title": "The Night-Shift Bees", "paragraphs": [{ "text": "…" }, { "text": "…" }] },
    "groups": [{
      "from": 1, "to": 7, "type": "tfng",
      "instructions": "Questions 1–7. Do the following statements agree with the information given in Reading Passage 1? Write TRUE if the statement agrees with the information, FALSE if the statement contradicts the information, or NOT GIVEN if there is no information on this.",
      "questions": [{ "n": 1, "text": "…", "answer": ["TRUE"] }]
    }]
  }]
}
```

Keep the exam rubric word for word, including "Reading Passage 1". Learners should practise on the real wording.

## 3. The sidecar

This is the same format as a full test (`reading-authoring.md`, section 2), plus a `trap` on every question.

```jsonc
{
  "slug": "drill-tfng-b-01",
  "tier": "B",
  "questions": {
    "3": {
      "evidence": "Bumblebees were among the earliest species to be tracked at night.",
      "why": "The passage says 'among the earliest', not the first, so the claim is never made.",
      "wrong": { "TRUE": "'Among the earliest' is not 'the first'.", "FALSE": "The passage does not say another species came first either." },
      "paraphrase": [["the first", "among the earliest"]],
      "trap": "ng-unstated-superlative"
    }
  },
  "sections": { "1": { "vocab": [{ "word": "forage", "meaning": "search for food", "example": "…exact sentence from the passage…" }] } }
}
```

`vocab`: 3–5 items.

## 4. Traps

Trap ids are fixed. They live in `LR_TRAPS` in `packages/core/src/lr.ts`; the app shows each one's label and explanation under a wrong answer and counts them per learner. To add a trap, add it there first.

**TFNG / YNNG** (`drill-tfng-*`):

| id | answer | the item |
|---|---|---|
| `true-paraphrase` | TRUE / YES | the whole claim is supported, but in different words: synonyms plus restructured syntax |
| `false-number` | FALSE / NO | one number, date or name is changed |
| `false-qualifier` | FALSE / NO | all/some, always/usually, only/first or must/may is changed |
| `false-comparison` | FALSE / NO | more/less, rise/fall, before/after or cause/effect is reversed |
| `false-cause` | FALSE / NO | the passage gives a different reason |
| `ng-unstated-comparison` | NOT GIVEN | both things are in the passage, but the comparison is never made |
| `ng-unstated-superlative` | NOT GIVEN | the passage never says first, most or only |
| `ng-unstated-reason` | NOT GIVEN | the event is there, but no reason is given |
| `ng-reused-words` | NOT GIVEN | passage words reused for a claim the passage never makes |
| `ng-outside-knowledge` | NOT GIVEN | true in the real world, absent from the text |
| `no-writer-rejects` | NO (YNNG only) | the writer argues against a view someone in the passage holds |
| `ng-others-view` | NOT GIVEN (YNNG only) | the writer reports a view without endorsing or rejecting it |

**Matching headings** (`drill-match-*`): `heading-detail`, `heading-keyword` and `heading-scope`. Tag each item with the trap its strongest distractor heading sets for that paragraph.

**Matching information** (`drill-minfo-*`):
| id | the item |
|---|---|
| `info-paraphrase` | the right paragraph gives the information without the question's words |
| `info-keyword` | another paragraph repeats the question's words but lacks the information |
| `info-kind` | another paragraph covers the topic, but not the kind of information asked (an example, a reason, a comparison, a description) |

**Matching features** (`drill-mfeat-*`):
| id | the item |
|---|---|
| `feature-paraphrase` | the person's view or finding in different words |
| `feature-nearby` | another name sits close to the idea in the passage |
| `feature-opposite` | another listed person holds the opposite view on the same point |

**Sentence endings** (`drill-mend-*`):
| id | the item |
|---|---|
| `ending-grammar` | a wrong ending makes a grammatical sentence that the passage does not support |
| `ending-reused-words` | a wrong ending reuses passage words from another point |
| `ending-half-right` | a wrong ending matches part of the idea but changes cause, result or scope |

**Completion** (`drill-sent-*`, `drill-summ-*`, `drill-diag-*`, `drill-short-*`):
| id | the item |
|---|---|
| `gap-word-form` | the passage word must keep the form the gap's grammar needs (plural, noun not verb); a near form is tempting |
| `gap-word-limit` | the passage phrase is longer than the limit; only its core noun fits |
| `gap-nearby-word` | another word close to the answer fits the grammar but not the meaning |
| `gap-paraphrase` | the words around the gap are paraphrased, so the answer sits under different words |
| `gap-box-synonym` | word box only: the right box word is a synonym of the passage word |
| `gap-box-distractor` | word box only: another box word fits grammar and topic but not the passage |

**Multiple choice** (`drill-mcq-*`): tag each item with the trap its strongest wrong option sets.
| id | the strongest wrong option |
|---|---|
| `mcq-keyword` | repeats passage words but says something else |
| `mcq-partial` | half matches the passage |
| `mcq-true-not-answer` | is true in the passage but does not answer the question |
| `mcq-opposite` | reverses the passage |
| `mcq-too-strong` | says all, always or only where the passage is weaker |

Rules (the check enforces the first three):
- Every item has a `trap`, and the trap agrees with the key: a `false-*` trap is keyed FALSE (or NO).
- A drill uses at least the subtype's minimum of different traps (table in section 1).
- TFNG and YNNG drills use each answer value at least twice.
- Across a tier's series, every trap of the family appears at least twice per 5 drills. Rotate them; do not lean on `false-number`.
- The trap sits at the tier's level (`reading-authoring.md`, section 6):
  - tier A: NOT GIVEN is clearly off-topic within the paragraph, and FALSE changes a number or name;
  - tier C: NOT GIVEN is 90% supported, FALSE works through implication, and YNNG views come from tone.

## 5. Writing a short passage that carries the traps

Write the passage around the items, not the items after the passage:
1. Pick 6–8 traps.
2. For each trap, plan the sentence that sets it. A NOT GIVEN comparison needs both things in the passage. A qualifier trap needs a qualifier ("most", "in some cases") worth changing.
3. Write a passage that reads as one coherent text and contains those sentences.

Every sentence still has to earn its place: no list of facts with one fact per statement.

- One idea per paragraph. Items go in passage order (TFNG and YNNG).
- Each item tests one sentence. Two items never test the same fact.
- Plant a distractor near each answer: a second number, a nearby comparison, a quoted view the writer then rejects.
- YNNG drills need a writer with views: an argued piece, not a report.
- Matching information drills: 5–7 lettered paragraphs; statements are noun phrases ("an example of …", "a reason why …"), not in passage order; one or two paragraphs may be used twice, and say so with the NB line.
- Matching features drills: 3–5 people (or dates, places) as options, each with a clear, distinct position; statements paraphrase what they said or found and are not in passage order. Invented people need invented generic contexts (see Facts).
- Sentence-endings drills: beginnings in passage order, 2–3 spare endings; every wrong ending is grammatical with every beginning it could attach to.
- Completion drills: answers are taken word for word from the passage within the word limit; the completed text is grammatical; gaps follow passage order (word box and table may vary). Diagram drills need a passage that really describes a structure or mechanism, and the SVG asset at `data/lr-drills/assets/lr/gen/img/<slug>-q1.svg` (rules in `reading-authoring.md` section 2).
- Multiple-choice drills: four options of similar length; spread keys over A–D; the key is the longest option at most once per drill.
- Headings drills: group `type: "match"`, `title: "List of Headings"`, options keyed `i, ii, iii …`, items `"text": "Paragraph A"` in paragraph order, rubric `Reading Passage 1 has five paragraphs, A–E. Choose the correct heading for each paragraph from the list of headings below. Write the correct number, i–viii.` (adjust the counts). Keep 5–7 lettered paragraphs (`label: "A"`…), every one with a heading item, 2–3 spare headings, and every rule in the "Matching headings" part of `reading-authoring.md` section 5 (one level of abstraction above the paragraph, about 30% shared content words).
- Facts: a passage about a real event, place, person or discovery keeps every specific fact real and accurate: dates, numbers, names, places. Never attach invented researchers, institutions, studies or figures to a real event. If the passage needs a study with numbers, invent the whole case and keep it generic ("a study of 120 first-year students at one university"), with no real person or named institution.
- Register: neutral, formal written British English, like a quality magazine or textbook. No second person ("you"), no rhetorical questions in factual pieces, no clichés ("in today's fast-paced world"). YNNG pieces use a first-person or clearly opinionated writer.
- Topics: academic and varied, never one already used by a full test or another drill. The check compares passages for 10-word overlaps across both folders. List the existing titles with `jq -r '.sections[].passage.title' data/lr-generated/gen-r*.json data/lr-drills/drill-*.json | sort`.

## 6. The loop

1. Write `data/lr-drills/<slug>.json` and `data/lr-drills/enrich/<slug>.json`.
2. `pnpm tsx scripts/reading-agent-check.ts <slug>`: fix every ERROR, and read every WARN.
3. Editorial self-review (`reading-authoring.md` section 7a), plus: does each item really set the trap its id names? Would a careful band-7 reader get it right for the right reason?
4. Blind re-solve: a separate agent reads `pnpm tsx scripts/reading-student-view.ts <slug>` and answers. Then run `pnpm tsx scripts/reading-agent-check.ts <slug> --solved answers.json` and resolve every disagreement. Do not import.

Import (lead only): `pnpm import:lr "$PWD/data/lr-drills"`. It sets `lr_tests.kind = 'drill'` from the JSON. Drills never appear in test lists, mock tests or plan counts, and every user can open them.
