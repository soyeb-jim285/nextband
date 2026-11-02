import { PAGE_COPY } from '@ielts/core';
import { cn } from '@/lib/utils';

/** The plain guide under a public hub: how this part of IELTS works and what NextBand does with it. The server writes the
 *  same copy into the HTML for crawlers (apps/server/src/seo.ts), so what is indexed is what is shown. */
export function SeoGuide({ path, className }: { path: keyof typeof PAGE_COPY; className?: string }) {
  const c = PAGE_COPY[path];
  if (!c?.sections.length) return null;
  return (
    <section aria-labelledby="guide-title" className={cn('mt-16 border-t border-line pt-10', className)}>
      <h2 id="guide-title" className="type-title-sm text-balance">
        {c.h1}
      </h2>
      <p className="type-lede mt-2 max-w-[65ch]">{c.intro}</p>
      <div className="mt-8 grid gap-x-12 gap-y-8 md:grid-cols-2">
        {c.sections.map((s) => (
          <div key={s.h}>
            <h3 className="type-heading">{s.h}</h3>
            <p className="mt-2 max-w-[65ch] text-body leading-relaxed text-muted">{s.p}</p>
          </div>
        ))}
      </div>
    </section>
  );
}
