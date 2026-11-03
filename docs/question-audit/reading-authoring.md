# Reading authoring guide (agent-written tests)

How a coding agent writes one original IELTS Reading test by hand, checks it and hands it over. Measured Cambridge numbers and the full house checklist are in `reading.md`; this page is the working standard. Everything here is written by the agent itself: no OpenRouter or other API call, and never `scripts/gen-lr.ts` or `scripts/lr-enrich.ts` (both call OpenRouter).

Slugs: Academic `gen-r-13` … `gen-r-362`, General Training `gen-rg-07` … `gen-rg-156` (`gen-r-01..12` and `gen-rg-01..06` already exist). Write only the slugs you were given, and never edit another agent's files.

## 1. The loop

1. Choose three topics from different fields. Check that no existing passage uses the same title or topic: `jq -r '.slug + "  " + (.sections[].passage.title)' data/lr-generated/gen-r*.json | sort -k2`.
2. Write `data/lr-generated/<slug>.json` (the test) and `data/lr-generated/enrich/<slug>.json` (the sidecar), plus a diagram SVG if the test has a diagram.
3. Check: `pnpm tsx scripts/reading-agent-check.ts <slug>`. Fix every `ERROR`. Read each `WARN` and fix it, or leave it only when you have a reason. Then do the editorial self-review in section 7a, which covers what the check cannot see.
4. Blind re-solve: a separate agent that has not seen the key reads `pnpm tsx scripts/reading-student-view.ts <slug>` and writes its answers as `{"1":"Ethiopia","2":"fat",...,"23":"A","24":"C"}`. For choose-TWO, give one letter to each number. Then run `pnpm tsx scripts/reading-agent-check.ts <slug> --solved answers.json`. For each disagreement, decide whether the item is ambiguous (two defensible answers, a NOT GIVEN that is arguably FALSE, a gap with two fitting words) and rewrite it, or whether the solver was simply wrong, in which case leave it. Keep the answers file in your scratchpad, not in `data/`.
5. Done when the check passes and every disagreement has been resolved. Do not import, commit or run the app: the lead imports in batches (section 9).

## 2. File formats

### Test: `data/lr-generated/<slug>.json` (type `LrTest` in `packages/core/src/lr.ts`)

A minimal example, cut down to one passage and two groups. Notes on each field follow it.

```json
{
  "slug": "gen-r-13", "skill": "reading", "variant": "academic", "source": "generated",
  "ref": "Original R13", "title": "Original practice · Reading 13",
  "sections": [
    {
      "part": 1, "title": "Passage 1",
      "passage": {
        "title": "The thermos flask",
        "paragraphs": [
          { "text": "In 1892 the Scottish chemist James Dewar needed a way to keep liquefied gases cold ..." },
          { "text": "Dewar never patented the design. 'I made it for the laboratory,' he told ..." }
        ]
      },
      "groups": [
        {
          "from": 1, "to": 5, "type": "gap", "wordLimit": "ONE WORD ONLY",
          "instructions": "Questions 1–5. Complete the notes below. Choose ONE WORD ONLY from the passage for each answer.",
          "title": "The vacuum flask",
          "content": "**Origins**\n\n- first used to store liquefied {{1}}\n- inventor did not take out a {{2}}\n- ...",
          "questions": [ { "n": 1, "answer": ["gases"] }, { "n": 2, "answer": ["patent"] } ]
        },
        {
          "from": 6, "to": 13, "type": "tfng",
          "instructions": "Questions 6–13. Do the following statements agree with the information given in Reading Passage 1? Write TRUE if the statement agrees with the information, FALSE if the statement contradicts the information, or NOT GIVEN if there is no information on this.",
          "questions": [ { "n": 6, "text": "Dewar designed the flask with domestic use in mind.", "answer": ["FALSE"] } ]
        }
      ]
    }
  ]
}
```

- `slug` matches the file name. `variant` is `academic` for `gen-r-*` and `general` for `gen-rg-*`; lr-import stores it as is and the app's Academic/General filter reads it. `ref` is `Original R<n>` or `Original RG<n>` and `title` is `Original practice · Reading <n>` or `Original practice · General Training Reading <n>`, with `<n>` the slug number without leading zeros. The ref must be unique, because the app uses it to find the replacement for a retired test.
- `sections`: exactly parts 1, 2 and 3, numbered 1–40 with no gaps. `title` is `Passage N` (Academic) or `Section N` (GT).
- `passage.paragraphs[]`: `{ "label"?: "A", "text": "..." }`. Use a `label` only when a group refers to letters (headings, matching information, GT "For which …" panels). A labelled paragraph owns any unlabelled paragraphs that follow it. Passage text is plain prose. Two conventions are rendered: a paragraph `### Title` is a heading (GT texts only), and lines starting `• ` are bullets (separate lines with `\n`). `**bold**`, other `#` and `{{ }}` show up literally, so do not use them. Use straight quotes or ’ consistently, and put quoted experts in single quotes.
- `subtitle` (optional): a one-line standfirst. Give one to about half the passages.
- Group fields: `from`, `to`, `type` (`gap | mcq | mcq-multi | tfng | ynng | match`), `instructions`, plus `wordLimit`, `title`, `content` (markdown with `{{n}}`; `**bold**`, lists and tables are rendered here), `options` (shared list), `reusable`, `image`. Each question is `{ "n", "text"?, "options"?, "answer": [...] }`.
- Answers: optional words go in parentheses (`"(the) harbour wall"`), and alternative accepted answers are extra array entries (`["10", "ten"]` is not needed, because numbers are matched as words or figures automatically, and so are US/UK spellings). Option types take the letter or roman numeral. TFNG/YNNG take the full word.
- Do not put `review` or `vocab` in this file. They belong in the sidecar.

### Sidecar: `data/lr-generated/enrich/<slug>.json`

```json
{
  "slug": "gen-r-13",
  "tier": "B",
  "questions": {
    "1": { "evidence": "Dewar needed a way to keep liquefied gases cold for his experiments.",
           "why": "'Store' paraphrases 'keep'; the notes ask what was kept cold.",
           "paraphrase": [["store liquefied", "keep liquefied"]] },
    "6": { "evidence": "'I made it for the laboratory,' he told a colleague, and he showed no interest in selling it to households.",
           "why": "He built it for laboratory work and had no interest in households, which contradicts 'domestic use in mind'.",
           "wrong": { "TRUE": "Households are mentioned only to say he had no interest in them.", "NOT GIVEN": "The passage states his purpose directly." },
           "paraphrase": [["domestic use", "households"]] }
  },
  "sections": { "1": { "vocab": [ { "word": "liquefied", "meaning": "turned from a gas into a liquid", "example": "Dewar needed a way to keep liquefied gases cold for his experiments." } ] } }
}
```

- `tier`: `A`, `B` or `C` (section 6). Required for new tests. lr-import ignores this field.
- One entry per question, 1–40. `evidence` is copied exactly from the passage (one or two sentences; whitespace may differ). For gaps it must contain the answer. For matching information it must lie in the keyed paragraph, for headings in the item's paragraph, and for GT "For which …" in the keyed text. For NOT GIVEN, quote the nearest tempting sentence or omit `evidence`. For choose-TWO it is optional. `why` is at most 45 words. `wrong` maps other options (or the other two TFNG values) to at most 30 words each, and never names the key. `paraphrase` is a list of `[question wording, passage wording]` pairs.
- `sections.<part>.vocab`: 5–8 items per passage, with `example` copied exactly from the passage.
- The app shows all of this only after submission: evidence is highlighted in the passage, `why` and `wrong` explain the answer, and the vocab list is shown.

### Diagram asset (only for diagram-label groups)

Set `"image": "lr/gen/img/<slug>-q<from>.png"` and write the drawing as SVG at `data/lr-generated/assets/lr/gen/img/<slug>-q<from>.svg`. Use a white background, black 2 px lines, sans-serif labels of at least 16 px, and blank label boxes numbered with the question numbers. The check renders the PNG with `rsvg-convert` when it is missing; delete the PNG to re-render after editing. `content` still lists every label line with its `{{n}}` (see `cam-16-4-r` Q1–6), so the test also works when the image fails to load.

## 3. Academic standard

| | Passage 1 | Passage 2 | Passage 3 |
|---|---|---|---|
| Words (target; check fails under 650 or over 1,000) | 700–880 | 780–900 | 800–950 |
| Questions | 13 | 13 | 14 (or 13/14/13 overall) |
| Groups | 2–3 | 3 | 3 |
| Register | factual, descriptive or historical; narrative of a discovery, product, animal or place | more analytical: a problem and its proposed solutions, several researchers, a debate | argumentative or discursive; the writer takes positions; often a book-review or opinion frame |
| Typical types | TFNG + notes/table/summary/flow-chart | matching information, headings, matching features, choose-TWO, summary, sentence completion | MCQ (writer's purpose and attitude), YNNG, word-box summary, sentence endings, matching features |
| Difficulty | locate and match facts | paraphrase across sentences | inference, attitude, implied claims |

- Sources look like New Scientist, The Economist, History Today or a university press book review: written for a non-specialist reader, without equations or jargon left unexplained.
- Make the passage dense with checkable facts: invented named people with an institution and role, dates, places, quantities, and 1–3 quotations in single quotes. Sentences average 20–24 words. Use few very long words (about 14% of words have 9+ letters). Paragraphs vary in length (60–170 words), 5–10 per passage.
- Measured targets (Cambridge 15–19, the same count as the one-liner in section 10): passages average 850 / 890 / 900 words for P1 / P2 / P3, so write in the upper half of the word range; 9 paragraphs per passage on average; paragraph lengths spread widely (standard deviation about a third of the mean), so every passage has at least one paragraph under 70 words and one over 150; 12% of words have 9+ letters. Both pilots came in at about 790 words, 7 near-equal paragraphs of 90–140 words, and 8% long words: too short, too even and too plain.
- Every sentence advances the paragraph's point. Do not bolt a stray fact onto the end of a paragraph to create NOT GIVEN bait or a vocabulary item ("Several residents of the nearby village said they had never seen the reef so pale before."; "Textbooks often repeat the story without citing a source."). Weave trap material into sentences that belong there: a second figure, a rival claim, a hope stated as a hope.
- Fields: draw each passage of a test from a different field (life science, earth/environment, technology/engineering, history/archaeology, psychology/social science, business/economics, arts/language/culture, health, urban studies, education).
- Passage 3 has a thesis. The writer weighs evidence, cites opponents, concedes points and concludes. YNNG and attitude MCQs test exactly these moves.

## 4. General Training standard

| | Section 1 (Q1–14) | Section 2 (Q15–27) | Section 3 (Q28–40) |
|---|---|---|---|
| Words (target) | 550–720 in total | 650–800 in total | 750–900 |
| Texts | 2–3 short everyday texts: notices, adverts, timetables, product/course/accommodation panels with 4–6 lettered items, consumer advice | 2 workplace texts: health and safety, staff entitlements, induction, job/training descriptions, codes of conduct | one long general-interest article (history of an everyday thing, a hobby, a social trend), title and standfirst, 6–8 lettered paragraphs |
| Typical types | TFNG on one text + "For which … are the following statements true?" on a lettered panel; or notes/sentence completion | notes (`AND/OR A NUMBER`), sentence completion, table, matching information | MCQ, matching information, headings, summary, matching features, YNNG (occasionally) |
| Difficulty | scanning for details | workplace vocabulary, rules and conditions | Academic P1–P2 level |

- Each short text in Sections 1–2 starts with a `### Title` paragraph and is split into many short blocks (16 on average per section), with bullets (`• `) where real leaflets use them. A lettered panel is a set of paragraphs labelled A–F, each beginning with the item's name and a line break (`"Day hire\nHybrid bicycle with ..."`). The option texts are those names.
- Use realistic UK detail: £ prices, 24-hour or am/pm times, UK addresses and phone formats (01632 960xxx), dates such as 14 March.
- Instructions say "the text", never "Reading Passage N": `Do the following statements agree with the information given in the text?`, `Choose NO MORE THAN TWO WORDS AND/OR A NUMBER from the text for each answer.`, `The text has seven sections, A–G.`

## 5. Question types: rules, traps and Cambridge models

Every `instructions` string starts `Questions N–M.` (en dash), `Questions N and M.` for a pair, or `Question N.` for a single question. It uses en dashes in every range (`A–G`, `i–ix`) and the verb "Choose … from the passage/text", never "Write …". Model groups below are given as `slug Qa–b`. Read any of them with `pnpm tsx scripts/reading-student-view.ts cam-16-1-r --part 2`, or the JSON with its key in `data/cambridge-lr/C16-T1-reading.json` (private: study the pattern, never copy wording).

The TFNG/YNNG and matching-information rules below are measured on Cambridge books 7–19 (`reading.md` section 1).

**TRUE / FALSE / NOT GIVEN** (`tfng`). Rubric: `Do the following statements agree with the information given in Reading Passage 1? Write TRUE if the statement agrees with the information, FALSE if the statement contradicts the information, or NOT GIVEN if there is no information on this.` Write 5–8 statements of 10–22 words each, in passage order. Use each value at least once (twice in groups of 6+). Across the test, NOT GIVEN is about a third of the keys.
- TRUE: the whole statement is supported, but in different words (synonym plus restructured syntax).
- FALSE: the statement changes one detail: a quantity, a direction, a cause, a comparative, a sequence, or a qualifier (all/some, always/usually, first/only).
- NOT GIVEN traps: the statement reuses passage words and is plausible, but the specific claim is absent (a comparison never made, a reason never given, a "first" or "most" never stated, a future outcome). Never use the template "the passage states how many …". Do not write FALSE items whose "contradiction" needs outside knowledge.
- Models: `cam-16-1-r` Q1–7, `cam-16-2-r` Q1–8, `cam-15-1-rg` Q1–6.

**YES / NO / NOT GIVEN** (`ynng`). Rubric: `Do the following statements agree with the claims of the writer in Reading Passage 3? Write YES if the statement agrees with the claims of the writer, NO if the statement contradicts the claims of the writer, or NOT GIVEN if it is impossible to say what the writer thinks about this.` ("views of the writer" is equally valid.) Statements test the writer's opinions, evaluations and predictions, not neutral facts. NO is the writer rejecting a view that other people hold in the passage. NOT GIVEN is a view on the topic that the writer reports from others but never endorses or rejects; this is the commonest high-band trap. Do not start every statement with "The writer believes …".
- Every statement must be something the writer could hold an opinion on. A plain fact from the passage ("Some towns ordered clocks before a reliable escapement had been developed.") belongs in TFNG, not YNNG. An off-topic NOT GIVEN ("The locksmith had already made clocks for other towns.") is a band-5 item; at tier B and above, NOT GIVEN sits on a topic the writer does discuss, through a superlative, a comparison or a judgement the writer never makes ("Lind's book has done more than any other to spread the story."; "The council regarded its clock as poor value for money.").
- Do not write a NOT GIVEN next to a broad endorsement that could cover it. "The writer accepts Brandt's dating of the escapement" is ambiguous when the passage says "Brandt is right about the technology".
- Models: `cam-16-2-r` Q21–26, `cam-17-2-r` Q32–36, `cam-16-4-r` Q36–40.

**Matching headings** (`match`, `title: "List of Headings"`). Rubric: `Reading Passage 2 has seven paragraphs, A–G. Choose the correct heading for each paragraph from the list of headings below. Write the correct number, i–ix.` Options are keyed `i, ii, iii …`, with 2–4 more headings than paragraphs. Each question's `text` is `Paragraph C`, and items go in paragraph order. Each heading is used at most once.
- A heading is the main idea of the whole paragraph, not a detail.
- Write headings at one level of abstraction above the paragraph, in different words. A heading that repeats the paragraph's key noun is a keyword match, not a heading. In Cambridge, a heading shares about 30% of its content words with its paragraph. The Haiku pilot reached 67%, for example "Algae that tolerate heat better" for a paragraph that says "Some strains … tolerate higher temperatures". The rewrite was "Some partners cope better than others". Similarly, "A reef that lost its colour twice in a decade" became "Too little time between two blows".
- Never put headings and matching information on the same passage. No Cambridge passage in books 7–19 does this, because the matching statements end up restating the headings.
- Distractors: a heading that names a detail mentioned in a paragraph but not its point; a heading that is true of the topic but in no paragraph; and two headings close in meaning that differ in scope (cause vs effect, past vs future, problem vs solution).
- Models: `cam-16-1-r` Q14–20, `cam-18-3-r` Q14–20, `cam-15-2-rg` Q28–33.

**Matching information** (`match`, `reusable: true`). Rubric: `Reading Passage 2 has seven paragraphs, A–G. Which paragraph contains the following information? Write the correct letter, A–G. NB You may use any letter more than once.` Options are the bare letters (`{"key":"A","text":"A"}`), exactly the passage's labels. Statements are noun phrases ("a reference to …", "an explanation of …", "an example of …", "a comparison between …") and are NOT in passage order.
- The information is often a minor detail inside a paragraph about something else, and other paragraphs share its keywords.
- Models: `cam-17-1-r` Q14–17, `cam-16-3-r` Q14–19, `cam-15-1-rg` Q32–36.

**Matching features** (`match`, `reusable: true`). Rubric: `Look at the following statements (Questions 35–40) and the list of researchers below. Match each statement with the correct researcher, A, B, C or D. NB You may use any letter more than once.` Options are 3–5 names, dates, places or categories. Statements paraphrase what each one claimed or found and are not in passage order.
- Each name appears more than once in the passage, and the claims are spread out; distractor claims are attributed to someone else nearby.
- GT variant: `For which bicycle hire option are the following statements true?` on a lettered panel.
- Models: `cam-16-1-r` Q35–40, `cam-18-4-r` Q17–22, `cam-15-3-rg` Q36–40; GT panel models: `cam-15-1-rg` Q7–14, `cam-15-3-rg` Q8–14.

**Sentence endings** (`match`, `reusable: false`). Rubric: `Complete each sentence with the correct ending, A–F, below.` Write 3–5 sentence beginnings in passage order, with 2–3 more endings than beginnings.
- Every ending must fit every beginning grammatically, so that only meaning decides. Distractor endings are true of a nearby sentence.
- Models: `cam-19-3-r` Q31–34, `cam-10-1-r` Q31–35, `cam-15-3-r` Q27–31.

**Sentence completion** (`gap`). Rubric: `Complete the sentences below. Choose NO MORE THAN TWO WORDS from the passage for each answer.` `content` is a `- ` list, with one `{{n}}` per sentence, in passage order. The sentence paraphrases the source sentence; only the answer word is the same. The completed sentence must be grammatical.
- Models: `cam-18-1-r` Q22–26, `cam-17-3-r` Q23–26, `cam-15-1-rg` Q23–27.

**Summary completion without a box** (`gap`). Rubric: `Complete the summary below. Choose ONE WORD ONLY from the passage for each answer.` `title` is the summary title and `content` is one paragraph covering one or two passage paragraphs, in order.
- Use a noun/verb/adjective mix. The gap's grammar must allow only one candidate word from the source sentence, and nearby words in the passage are tempting but wrong in form or meaning.
- Models: `cam-17-1-r` Q18–22, `cam-16-3-r` Q6–13, `cam-18-1-r` Q32–35.

**Summary with a word box** (`gap` with `options`, no `wordLimit`). Rubric: `Complete the summary using the list of words, A–H, below.` Answers are letters. Give 6–9 options for 4–6 gaps (at least 3 spare), all the same part of speech where possible.
- Box words are synonyms rather than passage words. Distractors fit the grammar and appear in the passage near the right answer.
- Bad: a key that is the passage word itself ("shorthand" for "useful as shorthand", "civic" for "civic demand"); a distractor that never appears in the passage ("solar-powered"); a distractor that is a near-synonym of the key ("fiction" next to "simplification"), which makes two answers defensible. Good: "municipal" for "town councils", with "monastic" (the view the writer rejects) as the distractor; "human supervision" for "hired a second clock-keeper to adjust the mechanism each week", with "costly repairs" (repairs that appear in a different paragraph) as the distractor.
- Models: `cam-16-1-r` Q31–34, `cam-17-1-r` Q27–31, `cam-16-2-r` Q17–20.

**Note completion** (`gap`). Rubric: `Complete the notes below. Choose ONE WORD ONLY from the passage for each answer.` `content` uses `**Heading**` lines and `- ` bullets, in passage order, written as terse notes rather than sentences.
- Models: `cam-17-1-r` Q1–6, `cam-18-2-r` Q1–8, `cam-15-1-rg` Q15–22.

**Table completion** (`gap`). Rubric: `Complete the table below. Choose …`. `content` is a markdown table (header row, `|---|` row) with `{{n}}` in cells. Fill some cells with given information. Gaps run row by row and should follow the passage.
- Models: `cam-16-1-r` Q8–13, `cam-17-4-r` Q7–13, `cam-15-2-rg` Q15–20.

**Flow-chart completion** (`gap`). Rubric: `Complete the flow-chart below. Choose …`. `content` lists the stages as `**Step 1**` paragraphs, or as lines separated by a line containing only `↓`, following the process in the passage. This type is rare: use it at most once every 15 tests.
- Models: `cam-9-1-r` Q34–39, `cam-5-2-r` Q4–8.

**Diagram labelling** (`gap` with `image`). Rubric: `Label the diagram below. Choose ONE WORD ONLY from the passage for each answer.` See the asset rules in section 2. Only use it when the passage really describes a physical structure or mechanism. This type is rare: use it at most once every 10 tests.
- Models: `cam-16-4-r` Q1–6, `cam-11-2-r` Q9–13, `cam-8-1-r` Q9–13.

**Multiple choice, one answer** (`mcq`). Rubric: `Choose the correct letter, A, B, C or D.` Write 3–6 questions with exactly four options of similar length, and stems in passage order. A final "What is the writer's main purpose / best title" item may refer to the whole passage. Across the test, spread keys over A–D, never repeat the same key three times in a row, and make the key the longest option no more than 30% of the time.
- Every distractor uses real passage content: a true detail answering a different question, an over-generalisation, the opposite view of someone the writer quotes, or a correct claim attributed to the wrong person. P3 stems ask about the writer's purpose, attitude or the point of an example.
- No giveaway distractors. Avoid absolutes that nothing in the passage supports ("councils always paid more for clocks than for bridges"). For an attitude question, do not give three plainly wrong moods such as hostile, indifferent and uncertain. Each wrong attitude should match part of the text: "admiring, since her research overturns the monastic story" (the writer does use her sources) or "dismissive of her explanation of how the escapement worked" (the writer disagrees with her elsewhere).
- Models: `cam-16-1-r` Q27–30, `cam-17-1-r` Q36–40, `cam-16-4-r` Q14–17.

**Multiple choice, choose TWO** (`mcq-multi`). Rubric: `Questions 23 and 24. Choose TWO letters, A–E. Which TWO … does the writer mention?` The shared `options` are A–E. Both questions repeat the stem as `text`, and both carry the full key set (`["A","C"]`). Distractors are mentioned in the passage but fail the stem (mentioned as a hope rather than a fact, or applied to a different group).
- Models: `cam-16-1-r` Q25–26, `cam-17-1-r` Q23–24, `cam-16-3-r` Q23–24.

**Short-answer questions** (`gap`, no `content`). Rubric: `Answer the questions below. Choose NO MORE THAN TWO WORDS from the passage for each answer.` Each question `text` ends with ` {{n}}`. This type is rare: use it at most once every four tests, and never in P3.
- Models: `cam-16-4-r` Q11–13, `cam-10-1-r` Q6–8.

**Word limits and answers.** Use the limits `ONE WORD ONLY`, `NO MORE THAN TWO WORDS`, rarely `THREE`, adding `AND/OR A NUMBER` when a number is keyed. Gap answers are copied exactly from the passage and keep its form (a plural stays plural). Each answer is unique in context and fits the gap grammatically. Never make two gaps in a group take the same word. Do not key a word that the gap's own sentence already contains.

## 6. Paraphrase, traps and difficulty tiers

**Paraphrase, not copy.** A candidate must not be able to answer by matching keywords.
- Statements, stems, options and notes never share a run of 6+ words with the passage (the check fails on this). Usually only names, numbers and the gap answer are the same.
- Use synonyms (decline / fell), word-class changes (to expand / expansion), voice changes, reordered clauses, negation swaps (rarely / not often), and general for specific (cattle and sheep / livestock).
- Make paragraphs rich in distractors. Near each answer, place a second number, a second name or a contrasting claim that a keyword-matcher would pick.
- Locator words (names, dates) may stay the same, so that the candidate can find the place. The tested idea must be reworded.
- One fact, one question. Two groups must not test the same sentence. In the Haiku pilot, TFNG items and word-box gaps tested the same facts three times: light readings, midday and longitude, and breeding success. Given text in notes, tables or summaries must not answer another group's question either: the gen-r-14 notes line "arrived in British glasshouses shortly before 1862" settled TFNG Q8.

**Tiers.** Record the tier in the sidecar (`"tier"`). Within every test, difficulty still rises from P1 to P3. Spread across a batch: about 25% A, 50% B, 25% C.

| | A: band 5–6 | B: band 6.5–7 (Cambridge median) | C: band 7.5–8.5 |
|---|---|---|---|
| Passage | lower word range, concrete topics, 18–21-word sentences, explicit opinions | as section 3 | upper word range, abstract or contested topics, hedged claims, views implied by evaluation, 24–28-word sentences |
| Paraphrase | single-word synonyms, the same clause order | synonym plus restructured clause | whole-clause restatement, nominalisation, inference across 2 sentences |
| TFNG/YNNG | NOT GIVEN clearly off-topic within the paragraph; FALSE by a changed number or name | FALSE by a changed qualifier or comparison; NOT GIVEN reuses passage words | NOT GIVEN 90% supported (only the specific link is missing); FALSE through implication; YNNG where the writer's view must be inferred from tone |
| Matching | distinctive keywords in each target paragraph | some keyword overlap between paragraphs | heavy overlap; target detail inside a paragraph about something else; headings 4 extras, two near-synonyms |
| MCQ / word box | one distractor clearly off | every distractor from the passage | every distractor partly true; the key needs the whole paragraph |

**Vary every test.** Within one batch of tests, do not repeat the same three-passage combination of group types. Rotate positions: TFNG is not always in P1, and headings move between P2 and P3. Use 7–9 groups per test. Across about 20 tests, aim for the Cambridge shares: TFNG in about 78% of tests, YNNG 68%, headings 51%, choose-TWO 36%, a word box in about half, a table or flow-chart in about a third, a diagram at most once in 10, and short answer at most once in 4. Vary group sizes (TFNG 5–8, matching 4–7, MCQ 3–6) and word limits.

## 7. Language and originality

- Write in British English (colour, centre, behaviour, organise, programme, travelled, grey, defence, licence as a noun). Use £, metric units and dates as "14 March 2019". Avoid stock essay phrasing ("It is important to note", "In conclusion") and never say a study is fictional.
- Originality: invent every person, institution, study and figure, and make them plausible (never attribute invented findings to a real living person or a real named study). Well-known historical facts may be used, retold in your own words. Never imitate a Cambridge passage or a real article's structure and sentences. The check fails any 7+ word run shared with Cambridge data and any 10-word run shared with another generated test, and warns on a lone 6-word overlap with Cambridge.

## 7a. Editorial self-review (the check cannot see these)

`reading-agent-check.ts` passed both pilots, including the faults listed in this section, so read your own test as an examiner would before the blind re-solve. Measure the passages with this one-liner. It prints, per passage: words, paragraph lengths, spread (target 0.25 or more) and the percentage of words with 9+ letters (target 11 or more):

```bash
python3 -c "
import json,re,sys,statistics as s
for x in json.load(open(sys.argv[1]))['sections']:
  P=[re.findall(r\"[A-Za-z']+\",p['text']) for p in x['passage']['paragraphs']]; W=sum(P,[]); n=[len(p) for p in P]
  print(x['part'], len(W), n, round(s.pstdev(n)/s.mean(n),2), round(100*sum(len(w)>=9 for w in W)/len(W)))" data/lr-generated/<slug>.json
```

Then answer each question with yes or no, and fix every no:
1. Is every passage in the upper half of its word range, with 7–10 paragraphs that include one under 70 words and one over 150?
2. Does every sentence belong to its paragraph's argument, with no bolted-on bait (section 3)?
3. Are headings abstract, and do they avoid the paragraph's key noun? Is there no passage that has both headings and matching information?
4. Is each fact tested once, with no group's given text answering another group?
5. Is every word-box key a synonym, every distractor taken from the passage, and no distractor a synonym of the key?
6. Is every YNNG statement an opinion, with no NOT GIVEN that is off-topic or that sits next to a broad endorsement?
7. Is every MCQ distractor partly true, with no unsupported absolute and no throwaway mood?
8. Does the test sit at its tier, with paraphrase at least at the level of the tier's row in section 6?

## 8. Commands

| Command | Purpose |
|---|---|
| `pnpm tsx scripts/reading-agent-check.ts <slug>` | Full deterministic check. Covers: identity (slug, variant, ref, title); uniqueness (slug, ref, passage titles, 10-word overlap with other generated tests); `validateLrTest` (same as import); instruction wording; per-type structure; TFNG/YNNG balance; heading and paragraph-letter consistency; words, paragraphs and questions per passage; evidence present as an exact substring, in the right paragraph, containing gap answers, in passage order where the type requires it; sidecar shape and tier; vocab examples; British spelling; diagram assets; and `scripts/reading-gates.py` (verbatim gap answers, word limits, copied statements, Cambridge overlap). Exits 1 on any ERROR. |
| `pnpm tsx scripts/reading-agent-check.ts <slug> --solved a.json` | Same check, plus a comparison of a blind solver's answers with the key. |
| `pnpm tsx scripts/reading-student-view.ts <slug> [--part N]` | Prints the test as a candidate sees it (no answers, evidence or notes). Works for `cam-*` model slugs too. |
| `python3 scripts/reading-gates.py data/lr-generated/<slug>.json` | The gates on their own. |

## 9. Import (lead only, after a batch passes)

```bash
pnpm import:lr --dry "$PWD/data/lr-generated"   # validates every test + sidecar exactly as the import will; nothing written
docker start ielts-prtactice-db-1
pnpm import:lr "$PWD/data/lr-generated"         # upserts lr_tests rows by slug (variant from the JSON), uploads missing assets, merges enrich sidecars
```

The import reads every `*.json` in the directory, merges `enrich/<slug>.json` into each test (reviews and vocab; `tier` is ignored), runs `validateLrTest`, checks that every `image` file exists under `assets/`, and upserts by slug. Running it again is safe. Pass an absolute path, because the command runs from `apps/server`. Generated tests are open to every user. A test that fails validation is skipped with `SKIP <file>: <reason>`. To replace a published test, rewrite it under a new slug and add `--retire old-slug`.
