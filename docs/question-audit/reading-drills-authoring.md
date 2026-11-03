# Reading drill authoring guide (question-type drills)

A drill is one short passage with 6–8 questions of a single type. Learners pick it from **Reading → Practise one question type**. It trains one skill, so every item sets a named trap, and the review tells the learner which trap they fell for. Full tests stay where learners get a band.

Write drills by hand, exactly like full tests: no OpenRouter or other API call. This page covers only what differs from a full test. Everything else follows `reading-authoring.md`, mainly section 5 (rubrics and type rules), section 6 (paraphrase, traps, tiers), section 7 (language and originality) and section 7a (editorial self-review).

## 1. What a drill is

| | Full test (`gen-r-NN`) | Drill (`drill-<type>-<tier>-NN`) |
|---|---|---|
| Passages | 3 | 1, `part: 1` |
| Words | 700–1000 per passage | **250–400** (hard limits 220–450); headings **380–500** (330–550) |
| Paragraphs | 5–10 | 3–6; headings 5–7, all lettered |
| Questions | 40 | **6–8**, one group, numbered from 1; headings 5–7 (one per paragraph) |
| Types | mixed | one family: `tfng` (TFNG or YNNG) or `match` (matching headings) |
| Sidecar | review per question | review per question **plus `trap`** |
| Tier | A/B/C per test | A/B/C in the slug and the sidecar |

Slugs: `drill-tfng-a-01`, `drill-tfng-b-01`, `drill-match-c-03`. Number each family-and-tier series from 01. The app serves drills of a type in slug order, so a learner meets tier A before B and C. Write only the slugs you were given.

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

Rules (the check enforces the first three):
- Every item has a `trap`, and the trap agrees with the key: a `false-*` trap is keyed FALSE (or NO).
- A drill uses at least 4 different traps (headings drills: at least 2 of the 3).
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
