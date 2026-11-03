// @ts-nocheck -- ponytail: offline checker over loosely-typed agent JSON (like gen-lr.ts); strict index checks add noise, not safety.
// Deterministic (no AI, no network) end-to-end check of ONE agent-written Reading test: data/lr-generated/<slug>.json
// plus its sidecar data/lr-generated/enrich/<slug>.json (and any diagram asset). Standard: docs/question-audit/reading-authoring.md.
// Drills (slug drill-<tfng|match>-<a|b|c>-NN, files in data/lr-drills/): one short passage of one question type, a trap on every item.
//        Standard: docs/question-audit/reading-drills-authoring.md. Same command.
// Usage: pnpm tsx scripts/reading-agent-check.ts <slug> [slug ...]   -> ERROR lines fail (exit 1), WARN lines are advisory.
//        pnpm tsx scripts/reading-agent-check.ts <slug> --solved answers.json   also compares a blind solver's answers {"1":"Ethiopia",...} with the key.
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { canonAnswerText, drillFamilyOf, expandAnswer, LR_TRAPS, normalizeLrTest, scoreLr, validateLrTest } from '../packages/core/src/lr';

const ROOT = join(import.meta.dirname, '..');
const TESTS = process.env.LR_DIR ?? join(ROOT, 'data/lr-generated'); // LR_DIR: only for testing the checker on scratch copies
const DRILLS = process.env.LR_DRILL_DIR ?? join(ROOT, 'data/lr-drills');
let DIR = TESTS;
const ws = (s = '') => s.replace(/\s+/g, ' ').trim();
const words = (s = '') => ws(s).split(' ').filter(Boolean).length;
const toks = (s = '') => s.toLowerCase().replace(/[‘’]/g, "'").replace(/[^a-z0-9']+/g, ' ').split(' ').filter(Boolean);
const canon = (s = '') => ` ${canonAnswerText(s).replace(/[^a-z0-9' ]+/g, ' ').split(' ').map((w) => w.replace(/^'+|'+$/g, '')).filter(Boolean).join(' ')} `;
const NUMW = ['zero', 'one', 'two', 'three', 'four', 'five', 'six', 'seven', 'eight', 'nine', 'ten', 'eleven', 'twelve'];
// [hard min, target min, target max, hard max] words per passage/section (docs/question-audit/reading.md)
const WORDS = { academic: [[650, 700, 880, 1000], [700, 780, 900, 1000], [700, 800, 950, 1000]], general: [[450, 550, 720, 800], [500, 650, 800, 900], [500, 750, 900, 950]] };
// ponytail: common US spellings only; extend when a review finds another
const US = /\b(colou?r(?<!colour)s?|center(s|ed)?|behavior\w*|favorite\w*|neighbor\w*|labor(?!at)\w*|theater\w*|gray|defense|travel(ed|ing|er|ers)|analyz\w*|(organ|real|recogn|special|emphas|minim|critic|apolog|priorit|summar|categor|util)iz\w*|\w+ization)\b/gi;

/** Every other original test and drill: passages must not repeat across both. */
function others(slug: string) {
  return [TESTS, DRILLS].filter(existsSync).flatMap((d) => readdirSync(d).filter((f) => /^(gen-rg?-\d+|drill-[a-z]+-[abc]-\d+)\.json$/.test(f) && f !== `${slug}.json`).map((f) => {
    try { return JSON.parse(readFileSync(join(d, f), 'utf8')); } catch { return null; }
  })).filter(Boolean);
}

function check(slug: string): boolean {
  const E: string[] = [], W: string[] = [];
  const err = (m: string) => E.push(m), warn = (m: string) => W.push(m);
  const dm = slug.match(/^drill-(tfng|match)-([abc])-(\d{2})$/);
  const drill = slug.startsWith('drill-');
  DIR = drill ? DRILLS : TESTS;
  const file = join(DIR, `${slug}.json`);
  const m = drill ? null : slug.match(/^gen-r(g?)-(\d{2,3})$/);
  if (drill ? !dm : !m) err(drill ? `drill slug must be drill-<tfng|match>-<a|b|c>-NN (got ${slug})` : `slug must be gen-r-NN or gen-rg-NN (got ${slug})`);
  if (!existsSync(file)) { console.log(`${slug}\n  ERROR ${file} not found`); return false; }
  let t: any;
  try { t = JSON.parse(readFileSync(file, 'utf8')); } catch (e) { console.log(`${slug}\n  ERROR invalid JSON: ${(e as Error).message}`); return false; }
  const gt = m?.[1] === 'g', num = +(m?.[2] ?? 0), variant = gt ? 'general' : 'academic';

  // ---- identity + uniqueness ----
  const secs: any[] = t.sections ?? [];
  const want = drill
    ? { slug, skill: 'reading', variant: 'academic', source: 'generated', kind: 'drill', ref: `Drill ${dm?.[1]?.toUpperCase()} ${dm?.[2]?.toUpperCase()}${dm?.[3]}`, title: secs[0]?.passage?.title }
    : { slug, skill: 'reading', variant, source: 'generated', ref: `Original R${gt ? 'G' : ''}${num}`, title: `Original practice · ${gt ? 'General Training ' : ''}Reading ${num}` };
  for (const [k, v] of Object.entries(want)) if (t[k] !== v) err(`${k} must be ${JSON.stringify(v)} (got ${JSON.stringify(t[k])})`);
  const wantParts = drill ? '1' : '1,2,3';
  if (secs.map((s) => s.part).join() !== wantParts) err(`sections must be part${drill ? '' : 's'} ${wantParts} (got ${secs.map((s) => s.part).join() || 'none'})`);
  const mine = new Set<string>();
  const shingle = (txt: string, add: (k: string) => void) => { const w = toks(txt); for (let i = 0; i + 10 <= w.length; i++) add(w.slice(i, i + 10).join(' ')); };
  for (const s of secs) shingle((s.passage?.paragraphs ?? []).map((p) => p.text).join(' '), (k) => mine.add(k));
  const titles = new Set(secs.map((s) => ws(s.passage?.title).toLowerCase()).filter((x) => x && !/^section \d$/.test(x)));
  for (const o of others(slug)) {
    if (o.slug === slug) err(`slug ${slug} also used by another file`);
    if (o.ref === t.ref && o.variant === t.variant) err(`ref "${t.ref}" also used by ${o.slug}`);
    for (const s of o.sections ?? []) {
      const ti = ws(s.passage?.title).toLowerCase();
      if (titles.has(ti)) err(`passage title "${s.passage.title}" already used by ${o.slug}`);
      let hit = '';
      shingle((s.passage?.paragraphs ?? []).map((p) => p.text).join(' '), (k) => { if (!hit && mine.has(k)) hit = k; });
      if (hit) err(`passage shares a 10-word run with ${o.slug} P${s.part}: "${hit}"`);
    }
  }

  // ---- schema (same as lr-import) ----
  const norm = normalizeLrTest(structuredClone(t));
  for (const e of validateLrTest(norm)) err(`schema: ${e}`);
  if (JSON.stringify(t).includes('"review"') || secs.some((s) => s.vocab)) err('review/vocab belong in the enrich sidecar, not in the test JSON');

  // ---- sidecar ----
  const sideFile = join(DIR, 'enrich', `${slug}.json`);
  const side = existsSync(sideFile) ? JSON.parse(readFileSync(sideFile, 'utf8')) : null;
  if (!side) err(`missing sidecar ${sideFile}`);
  else if (side.slug !== slug) err(`sidecar slug must be "${slug}"`);
  const legacy = !drill && num <= (gt ? 6 : 12); // written before tiers existed
  if (drill && side && side.tier !== dm?.[2]?.toUpperCase()) err(`sidecar tier must be "${dm?.[2]?.toUpperCase()}" (the slug's tier)`);
  if (side && !legacy && !['A', 'B', 'C'].includes(side.tier)) err('sidecar needs "tier": "A" (band 5-6), "B" (6.5-7) or "C" (7.5-8.5)');
  const rev = side?.questions ?? {};
  const allN = new Set(secs.flatMap((s) => (s.groups ?? []).flatMap((g) => g.questions.map((q) => String(q.n)))));
  for (const k of Object.keys(rev)) if (!allN.has(k)) err(`sidecar has review for Q${k}, which is not in the test`);

  const tf: string[] = [], mcqKeys: string[] = [];
  let longestKey = 0, shortAns = 0, groupsTotal = 0;
  const usesLetters = (g) => /\b(paragraphs?|sections?)\b.*\b[A-Z]–[A-Z]\b|heading/i.test(`${g.instructions} ${g.title ?? ''}`);
  for (const s of secs) {
    const P = s.part, paras: any[] = s.passage?.paragraphs ?? [], groups: any[] = s.groups ?? [];
    groupsTotal += groups.length;
    const nq = groups.reduce((a, g) => a + g.questions.length, 0);
    const hd = dm?.[1] === 'match'; // headings drills: one question per paragraph
    if (drill ? (hd ? nq < 5 || nq > 7 : nq < 6 || nq > 8) : nq < 13 || nq > 14) err(`P${P}: ${nq} questions (${drill ? (hd ? '5-7 per headings drill' : '6-8 per drill') : '13 or 14 per passage'})`);
    if (drill ? groups.length !== 1 : groups.length < 2 || groups.length > 4) (drill ? err : warn)(`P${P}: ${groups.length} question groups (${drill ? 'a drill has one' : 'Cambridge: 2-4'})`);
    if (drill && groups.some((g) => drillFamilyOf(g.type) !== dm?.[1])) err(`a ${dm?.[1]} drill holds only ${dm?.[1]} questions`);
    // passage text, words, layout
    const wc = words(paras.map((p) => p.text).join(' '));
    const [h0, t0, t1, h1] = drill ? (hd ? [330, 380, 500, 550] : [220, 250, 400, 450]) : (WORDS[variant][P - 1] ?? [0, 0, 1e9, 1e9]);
    if (wc < h0 || wc > h1) err(`P${P}: ${wc} words (allowed ${h0}-${h1}, target ${t0}-${t1})`);
    else if (wc < t0 || wc > t1) warn(`P${P}: ${wc} words (target ${t0}-${t1})`);
    if (!gt && (drill ? (hd ? paras.length < 5 || paras.length > 7 : paras.length < 3 || paras.length > 6) : paras.length < 5 || paras.length > 10)) warn(`P${P}: ${paras.length} paragraphs (${drill ? (hd ? '5-7' : '3-6') : '5-10'})`);
    if (gt && P < 3 && paras.length < 10) warn(`S${P}: ${paras.length} paragraphs (GT sections 1-2 are many short blocks, each text with its own short title paragraph)`);
    // renderers know only "### Title" paragraphs (GT text headings) and "• " bullet lines; anything else shows literally
    const md = paras.flatMap((p, i) => (/\*\*|\{\{|__/.test(p.text) || (/^\s*#/.test(p.text) && !/^### \S/.test(p.text)) ? [i + 1] : []));
    if (md.length) err(`P${P} paragraphs ${md.join(',')}: only "### Title" headings and "• " bullets render; no **bold**, other #, {{ }}`);
    if (!gt && paras.some((p) => p.text.startsWith('### '))) warn(`P${P}: Academic passages have no sub-headings`);
    if (gt && P < 3 && !paras.some((p) => p.text.startsWith('### '))) warn(`S${P}: give each short text a "### Title" heading paragraph`);
    for (const p of paras) {
      for (const u of p.text.match(US) ?? []) warn(`P${P}: US spelling "${u}" (use British English)`);
      if (/\$\d/.test(p.text)) warn(`P${P}: "$" price; use £ for a UK setting`);
    }
    // paragraph labels: a labelled paragraph owns the unlabelled ones after it
    const labels: string[] = [], owner: string[] = [];
    let cur = '';
    for (const p of paras) { if (p.label) { cur = p.label; labels.push(p.label); } owner.push(cur); }
    const lettered = groups.some(usesLetters);
    if (labels.length && !lettered && !gt) warn(`P${P}: paragraphs are lettered but no group refers to letters (letter only when needed)`);
    if (lettered && !labels.length) err(`P${P}: a group refers to paragraph letters but the passage has no labels`);
    // evidence locator over the whitespace-normalised passage
    const starts: number[] = [];
    let plain = '';
    for (const p of paras) { starts.push(plain.length); plain += ws(p.text) + ' '; }
    const paraAt = (i: number) => owner[starts.findLastIndex((x) => x <= i)] ?? '';

    for (const g of groups) {
      const ins = g.instructions ?? '', id = `Q${g.from}-${g.to}`, qs: any[] = g.questions;
      const kind = g.type === 'match' ? (/heading/i.test(`${ins} ${g.title ?? ''}`) ? 'headings' : /which (paragraph|section)/i.test(ins) ? 'matchinfo' : /ending/i.test(ins) ? 'endings' : 'matchfeat')
        : g.type === 'gap' ? (g.image ? 'diagram' : g.options ? 'wordbox' : /\|/.test(g.content ?? '') ? 'table' : !g.content ? 'short' : 'completion') : g.type;
      // instructions
      const pre = g.from === g.to ? `Question ${g.from}.` : g.to === g.from + 1 ? `Questions ${g.from} and ${g.to}.` : `Questions ${g.from}–${g.to}.`;
      if (!ins.startsWith(pre)) err(`${id}: instructions must start "${pre}"`);
      if (/\b(\d+|[A-Ha-h])-(\d+|[A-Ha-h])\b/.test(ins.replace(/^Questions? [^.]+\./, '').replace(/\b[a-z]+-[a-z]+\b/g, ''))) err(`${id}: use an en dash in ranges (A–H, i–ix)`);
      if (/\bWrite (ONE|NO MORE|TWO|THREE)\b/.test(ins)) err(`${id}: say "Choose ... from the ${gt ? 'text' : 'passage'}", not "Write ..."`);
      if (gt && /Reading Passage/.test(ins)) err(`${id}: General Training says "the text", not "Reading Passage"`);
      if (!gt && /from the text\b/.test(ins)) err(`${id}: Academic says "from the passage"`);
      const rp = ins.match(/Reading Passage (\d)/);
      if (rp && +rp[1] !== P) err(`${id}: instructions name Reading Passage ${rp[1]} in passage ${P}`);
      if (!gt && /^(tfng|ynng|headings|matchinfo)$/.test(kind) && !rp) err(`${id}: instructions must name "Reading Passage ${P}"`);
      if (g.type === 'tfng' && !/information/.test(ins)) err(`${id}: TFNG rubric must be about "the information"`);
      if (g.type === 'ynng' && !/(claims|views) of the writer/.test(ins)) err(`${id}: YNNG rubric must be about "the claims/views of the writer"`);
      if (g.type === 'gap' && !g.options) {
        if (!g.wordLimit) err(`${id}: completion group needs wordLimit`);
        else if (!ins.includes(g.wordLimit)) err(`${id}: instructions must state the word limit "${g.wordLimit}"`);
        const seen = new Map<string, number>();
        for (const q of qs) { const a = canon(q.answer?.[0]); if (seen.has(a)) warn(`${id}: Q${q.n} has the same answer as Q${seen.get(a)}`); seen.set(a, q.n); }
      }
      if (kind === 'diagram') {
        if (g.image !== `lr/gen/img/${slug}-q${g.from}.png`) err(`${id}: image key must be lr/gen/img/${slug}-q${g.from}.png`);
        if (!qs.every((q) => (g.content ?? '').includes(`{{${q.n}}}`))) err(`${id}: diagram content must list every label with its {{n}}`);
      }
      if (kind === 'short') shortAns++;
      // per-kind structure
      if (g.type === 'mcq') for (const q of qs) {
        if ((q.options ?? []).map((o) => o.key).join('') !== 'ABCD') err(`Q${q.n}: MCQ needs exactly four options A-D`);
        const key = q.options?.find((o) => o.key === q.answer?.[0]);
        if (key && key.text.length >= Math.max(...q.options.map((o) => o.text.length))) longestKey++;
        mcqKeys.push(q.answer?.[0]);
      }
      if (g.type === 'mcq-multi') {
        const sets = qs.map((q) => [...(q.answer ?? [])].sort().join());
        if (new Set(sets).size !== 1 || (qs[0].answer ?? []).length !== qs.length) err(`${id}: every choose-${NUMW[qs.length]?.toUpperCase()} question must carry the full set of ${qs.length} keys`);
        if ((g.options ?? []).length < qs.length + 3) err(`${id}: choose-${qs.length} needs at least ${qs.length + 3} options (Cambridge: TWO from A–E)`);
      }
      if (kind === 'wordbox' || kind === 'endings') if ((g.options ?? []).length <= qs.length) err(`${id}: needs more options than questions`);
      if (kind === 'headings') {
        const extra = (g.options ?? []).length - qs.length;
        if (extra < 1) err(`${id}: headings need distractors (2-4 more headings than paragraphs)`); else if (extra > 4 || extra < 2) warn(`${id}: ${extra} extra headings (Cambridge: 2-4)`);
        if (!(g.options ?? []).every((o) => /^[ivx]+$/.test(o.key))) err(`${id}: heading keys are lower-case roman numerals i, ii, iii ...`);
        const used = qs.map((q) => q.answer?.[0]);
        if (new Set(used).size !== used.length) err(`${id}: a heading is used twice`);
        for (const q of qs) { const L = q.text?.match(/^(Paragraph|Section) ([A-Z])$/)?.[2]; if (!L || !labels.includes(L)) err(`Q${q.n}: heading item text must be "Paragraph X" for a lettered paragraph`); }
      }
      if (kind === 'matchinfo') {
        const keys = (g.options ?? []).map((o) => o.key).join();
        if (keys !== labels.join()) err(`${id}: options must be exactly the passage letters ${labels.join(',')} (got ${keys})`);
        if ((g.options ?? []).some((o) => o.text !== o.key)) err(`${id}: matching-information options are bare letters (text = key)`);
        const L = (g.options ?? []).map((o) => o.key);
        const c = ins.match(/has (\w+) (?:paragraphs|sections), ([A-Z])–([A-Z])/);
        if (!c || c[1] !== NUMW[labels.length] || c[2] !== labels[0] || c[3] !== labels.at(-1)) err(`${id}: instructions must say "has ${NUMW[labels.length]} paragraphs, ${labels[0]}–${labels.at(-1)}" (or sections)`);
        const ans = qs.map((q) => q.answer?.[0]);
        if (ans.length > 2 && ans.every((a, i) => !i || L.indexOf(a) > L.indexOf(ans[i - 1]))) warn(`${id}: statements follow passage order; shuffle them`);
      }
      if (kind === 'headings') {
        const c = ins.match(/has (\w+) (?:paragraphs|sections), ([A-Z])–([A-Z])/);
        if (c && (c[1] !== NUMW[labels.length] || c[2] !== labels[0] || c[3] !== labels.at(-1))) err(`${id}: instructions say "${c[0]}" but the passage has ${labels.length} letters ${labels[0]}–${labels.at(-1)}`);
      }
      if (g.type === 'tfng' || g.type === 'ynng') {
        const vals = g.type === 'tfng' ? ['TRUE', 'FALSE', 'NOT GIVEN'] : ['YES', 'NO', 'NOT GIVEN'];
        const ans = qs.map((q) => q.answer?.[0]);
        for (const v of vals) if (!ans.includes(v)) err(`${id}: ${v} never used (each answer at least once)`);
        if (qs.length >= 6 && vals.some((v) => ans.filter((a) => a === v).length < 2)) warn(`${id}: with ${qs.length} items use each answer at least twice`);
        if (/(.)\1\1\1/.test(ans.map((a) => a[0]).join(''))) warn(`${id}: four identical answers in a row`);
        for (const q of qs) { const n = words(q.text); if (n < 7 || n > 26) warn(`Q${q.n}: statement is ${n} words (Cambridge 10-22)`); }
        tf.push(...ans);
        if (!gt && !drill && g.type === 'ynng' && P === 1) warn(`${id}: YES/NO/NOT GIVEN in Passage 1 is rare (writer's views belong in P2/P3)`);
      }
      // evidence, paragraph refs, order. Measured on Cambridge (enrich evidence): TFNG/YNNG/MCQ/notes/summary keep passage order
      // (out of order in <8% of groups, mostly evidence noise); word box 28%, endings 22%, table 11% -> warn only
      const order = /^(tfng|ynng|mcq|completion|wordbox|endings|short|table)$/.test(kind);
      let lastPos = -1, lastN = 0;
      for (const q of qs) {
        const r = rev[q.n];
        if (!r) { if (side) err(`Q${q.n}: no review in sidecar`); continue; }
        if (drill) {
          const tr = LR_TRAPS[r.trap];
          if (!tr) err(`Q${q.n}: drill item needs "trap" (one of: ${Object.keys(LR_TRAPS).filter((k) => LR_TRAPS[k].family === dm?.[1]).join(', ')})`);
          else if (tr.family !== dm?.[1]) err(`Q${q.n}: trap "${r.trap}" is a ${tr.family} trap`);
          else if (g.type === 'tfng' || g.type === 'ynng') {
            const want = r.trap.startsWith('true') ? ['TRUE'] : r.trap.startsWith('false') ? ['FALSE'] : r.trap.startsWith('no-') ? ['NO'] : ['NOT GIVEN'];
            if (r.trap === 'true-paraphrase' && g.type === 'ynng') want.push('YES');
            if (r.trap.startsWith('false') && g.type === 'ynng') want.push('NO');
            if (!want.includes(q.answer?.[0])) err(`Q${q.n}: trap "${r.trap}" needs the answer ${want.join(' or ')}, key is ${q.answer?.[0]}`);
          }
        }
        if (!r.why?.trim()) err(`Q${q.n}: review needs "why"`);
        else if (words(r.why) > 45) err(`Q${q.n}: "why" is ${words(r.why)} words (max 45)`);
        const okKeys = g.type === 'tfng' ? ['TRUE', 'FALSE', 'NOT GIVEN'] : g.type === 'ynng' ? ['YES', 'NO', 'NOT GIVEN'] : (q.options ?? g.options ?? []).map((o) => o.key);
        for (const [k, v] of Object.entries(r.wrong ?? {})) {
          if (!okKeys.includes(k)) err(`Q${q.n}: wrong["${k}"] is not an option`);
          if ((q.answer ?? []).includes(k)) err(`Q${q.n}: wrong["${k}"] is the key`);
          if (words(v as string) > 30) err(`Q${q.n}: wrong["${k}"] over 30 words`);
        }
        if (r.paraphrase && !(Array.isArray(r.paraphrase) && r.paraphrase.every((x) => Array.isArray(x) && x.length === 2 && x.every((y) => typeof y === 'string' && y.trim())))) err(`Q${q.n}: paraphrase must be [["question words","passage words"], ...]`);
        if (!r.evidence?.trim()) {
          if (g.type !== 'mcq-multi' && q.answer?.[0] !== 'NOT GIVEN') err(`Q${q.n}: review needs "evidence" (exact passage substring)`);
          continue;
        }
        const pos = plain.indexOf(ws(r.evidence));
        if (pos < 0) { err(`Q${q.n}: evidence is not an exact substring of passage ${P}: "${ws(r.evidence).slice(0, 70)}..."`); continue; }
        if (words(r.evidence) > 80) warn(`Q${q.n}: evidence is ${words(r.evidence)} words (keep it to the 1-2 sentences that give the answer)`);
        const loose = ` ${toks(r.evidence).join(' ')} `, has = (a: string) => expandAnswer(a).some((v) => canon(r.evidence).includes(` ${v} `)) || [a.replace(/[()]/g, ''), a.replace(/\([^)]*\)/g, '')].some((v) => loose.includes(` ${toks(v).join(' ')} `));
        if (g.type === 'gap' && !g.options && !(q.answer ?? []).some(has)) err(`Q${q.n}: evidence does not contain the answer "${q.answer?.[0]}"`);
        const para = paraAt(pos);
        if (kind === 'matchinfo' && para !== q.answer?.[0]) err(`Q${q.n}: evidence is in paragraph ${para || '?'} but the key is ${q.answer?.[0]}`);
        if (kind === 'matchfeat' && /For which/i.test(ins) && (g.options ?? []).every((o) => labels.includes(o.key)) && para !== q.answer?.[0]) err(`Q${q.n}: evidence is in text ${para || '?'} but the key is ${q.answer?.[0]}`);
        if (kind === 'headings') { const L = q.text?.match(/([A-Z])$/)?.[1]; if (L && para !== L) err(`Q${q.n}: evidence is in paragraph ${para || '?'}, the item is paragraph ${L}`); }
        const global = g.type === 'mcq' && /best title|main (purpose|aim|idea|point)|as a whole|overall|throughout the passage/i.test(q.text ?? '');
        if (order && q.answer?.[0] !== 'NOT GIVEN' && !global) {
          if (pos < lastPos) (/^(table|wordbox|endings)$/.test(kind) ? warn : err)(`Q${q.n}: answer comes before Q${lastN}'s in the passage (${kind} questions follow passage order)`);
          lastPos = pos; lastN = q.n;
        }
      }
    }
    if (drill) {
      const used = new Set(groups.flatMap((g) => g.questions.map((q) => rev[q.n]?.trap)).filter(Boolean));
      const need = hd ? 2 : 4; // only 3 headings traps exist
      if (used.size < need) err(`a drill needs at least ${need} different traps (has ${used.size}: ${[...used].join(', ')})`);
    }
    // vocab
    const voc = side?.sections?.[P]?.vocab;
    if (side && !voc?.length) warn(`P${P}: sidecar has no vocab (${drill ? '3-5' : '5-8'} items)`);
    for (const v of voc ?? []) {
      if (!v.word || !v.meaning) err(`P${P}: vocab item needs word and meaning`);
      if (v.example && !plain.includes(ws(v.example))) err(`P${P}: vocab example for "${v.word}" is not verbatim from the passage`);
    }
  }
  // test-wide balance
  const ng = tf.filter((a) => a === 'NOT GIVEN').length;
  if (!drill && tf.length >= 8 && (ng / tf.length < 0.2 || ng / tf.length > 0.5)) warn(`NOT GIVEN is ${ng}/${tf.length} of TFNG/YNNG keys (target about a third)`);
  if (/(.)\1\1/.test(mcqKeys.join(''))) warn(`MCQ keys ${mcqKeys.join('')}: three identical in a row`);
  if (mcqKeys.length >= 4 && longestKey / mcqKeys.length > 0.3) warn(`MCQ key is the longest option in ${longestKey}/${mcqKeys.length} questions (max 30%)`);
  if (shortAns > 1) warn(`${shortAns} short-answer groups (rare: at most one per four tests)`);
  if (!drill && (groupsTotal < 6 || groupsTotal > 11)) warn(`${groupsTotal} groups in the test (Cambridge about 8)`);
  // diagram assets: render <key>.svg -> .png when the PNG is missing
  for (const g of secs.flatMap((s) => s.groups ?? []).filter((g) => g.image)) {
    const png = join(DIR, 'assets', g.image), svg = png.replace(/\.png$/, '.svg');
    if (!existsSync(png) && existsSync(svg)) {
      try { execFileSync('rsvg-convert', ['-w', '1000', '-o', png, svg]); warn(`rendered ${g.image} from its SVG`); } catch (e) { err(`rsvg-convert failed for ${svg}: ${(e as Error).message}`); }
    }
    if (!existsSync(png)) err(`missing asset ${png} (write ${svg.replace(ROOT + '/', '')}; this check renders it)`);
  }
  // reading gates (verbatim gap answers, word limits, copied statements, Cambridge overlap)
  try {
    const out = execFileSync('python3', [join(ROOT, 'scripts/reading-gates.py'), file], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] });
    for (const l of out.split('\n')) { const x = l.match(/^\s*(BAD|WARN) (.*)/); if (x) (x[1] === 'BAD' ? err : warn)(`gate: ${x[2]}`); }
  } catch (e) { err(`reading-gates.py crashed: ${String((e as any).stderr || (e as Error).message).trim().split('\n').at(-1)}`); }

  // blind re-solve: disagreements are for the author to judge (ambiguous item -> fix it; solver wrong -> keep)
  const si = process.argv.indexOf('--solved');
  if (si > 0 && !E.some((x) => x.startsWith('schema'))) {
    const sc = scoreLr(norm, JSON.parse(readFileSync(process.argv[si + 1], 'utf8')));
    console.log(`  solver agreement ${sc.raw}/${sc.total} (band ${sc.band})`);
    for (const k of sc.marks.filter((x) => !x.correct)) warn(`solver Q${k.n}: gave "${k.given}", key ${JSON.stringify(k.answer)}`);
  }
  console.log(`${slug} (${variant}): ${E.length ? `FAIL, ${E.length} error(s)` : 'PASS'}${W.length ? `, ${W.length} warning(s)` : ''}`);
  for (const x of E) console.log(`  ERROR ${x}`);
  for (const x of W) console.log(`  WARN  ${x}`);
  return !E.length;
}

const slugs = process.argv.slice(2).filter((a, i, all) => !a.startsWith('-') && all[i - 1] !== '--solved');
if (!slugs.length) { console.error('usage: pnpm tsx scripts/reading-agent-check.ts <slug> [slug ...]'); process.exit(2); }
const ok = slugs.map((s) => {
  try { return check(s); } catch (e) { console.log(`${s}: FAIL\n  ERROR malformed test (checker crashed): ${(e as Error).message}`); return false; }
}).every(Boolean);
process.exit(ok ? 0 : 1);
