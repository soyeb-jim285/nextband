// Prints a Reading test the way a candidate sees it: passages and questions, never answers, evidence or explanations
// (the enrich sidecar is not read; answers are removed with the app's own stripAnswers). Use it to re-solve a test cold.
// Usage: pnpm tsx scripts/reading-student-view.ts <slug> [--part N]   (slug gen-r-13, gen-rg-07, or a Cambridge model such as cam-17-1-r / cam-15-1-rg)
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { normalizeLrTest, pickParts, stripAnswers, type LrTest } from '../packages/core/src/lr';

const [slug] = process.argv.slice(2).filter((a) => !a.startsWith('-'));
if (!slug) { console.error('usage: pnpm tsx scripts/reading-student-view.ts <slug> [--part N]'); process.exit(2); }
const pi = process.argv.indexOf('--part');
const cam = slug.match(/^cam-(\d+)-(\d+)-r(g?)$/);
const file = cam ? `../data/cambridge-lr/C${cam[1]}-T${cam[2]}-reading${cam[3] ? '-gt' : ''}.json` : `../data/${slug.startsWith('drill-') ? 'lr-drills' : 'lr-generated'}/${slug}.json`;
const raw = JSON.parse(readFileSync(join(import.meta.dirname, file), 'utf8')) as LrTest;
const t = stripAnswers(pickParts(normalizeLrTest(raw), pi > 0 ? [+process.argv[pi + 1]!] : null));
const gap = (s: string) => s.replace(/\{\{(\d+)\}\}/g, '($1) ________');
const out: string[] = [`${t.title}  [${t.variant === 'general' ? 'General Training' : 'Academic'}]`];
for (const s of t.sections) {
  const p = s.passage!;
  out.push('', '='.repeat(72), `${t.variant === 'general' ? 'SECTION' : 'READING PASSAGE'} ${s.part}`, '', p.title, ...(p.subtitle ? [p.subtitle] : []), '');
  for (const para of p.paragraphs) out.push(`${para.label ? `${para.label}  ` : ''}${para.text.replace(/^### /, '## ')}`, '');
  for (const g of s.groups) {
    out.push('-'.repeat(72), g.instructions);
    if (g.title) out.push('', g.title);
    if (g.image) out.push(`[image: data/${cam ? "cambridge-lr" : slug.startsWith("drill-") ? "lr-drills" : "lr-generated"}/assets/${g.image}]`);
    if (g.content) out.push('', gap(g.content));
    if (g.options) out.push('', ...g.options.map((o) => (o.text === o.key ? o.key : `${o.key}  ${o.text}`)));
    out.push('');
    const multi = g.type === 'mcq-multi';
    for (const q of g.questions) {
      if (g.content && !q.text) continue; // the gap sits in the content above
      if (multi && q.n !== g.from) continue; // one stem for the whole choose-TWO set
      out.push(`${multi ? `${g.from}-${g.to}` : q.n}  ${gap(q.text ?? '')}`, ...(q.options ?? []).map((o) => `     ${o.key}  ${o.text}`));
    }
    out.push('');
  }
}
console.log(out.join('\n'));
