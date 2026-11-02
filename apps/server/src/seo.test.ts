import { expect, it } from 'vitest';
import { renderShell } from './seo';

const shell = '<head>\n<!--seo-->\n<title>x</title>\n<!--/seo-->\n</head>';

it('writes each page its own title, share card and canonical URL; private pages are noindex', () => {
  const sp = renderShell(shell, '/speaking/', 'https://ielts.example');
  expect(sp).toContain('<title>Free IELTS Speaking practice test with band score | NextBand</title>');
  expect(sp).toContain('<meta property="og:image" content="https://ielts.example/og/speaking.png" />');
  expect(sp).toContain('<link rel="canonical" href="https://ielts.example/speaking" />');
  expect(sp).toContain('content="index, follow, max-image-preview:large"');
  expect(sp).not.toContain('<title>x</title>');
  const result = renderShell(shell, '/speaking/result/abc', 'https://ielts.example');
  expect(result).toContain('<title>Speaking result | NextBand</title>');
  expect(result).toContain('content="noindex, follow"');
  expect(renderShell(shell, '/', 'https://ielts.example')).toContain('og/landing.png');
});

it('inserts a hostile path literally: no $-pattern expansion, no markup', () => {
  const html = renderShell(`<p>before</p>${shell}<p>after</p>`, "/x$'$`\"<script>'", 'https://ielts.example');
  expect(html).not.toContain('<script>');
  expect(html.match(/<p>before<\/p>/g)).toHaveLength(1);
  expect(html.match(/<p>after<\/p>/g)).toHaveLength(1);
  expect(html).toContain('href="https://ielts.example/x$&#39;$`&quot;&lt;script&gt;&#39;"');
});

it('titles the privacy page and keeps /admin noindex', () => {
  expect(renderShell(shell, '/privacy', 'https://ielts.example')).toContain('<title>Privacy policy | NextBand</title>');
  const admin = renderShell(shell, '/admin/users', 'https://ielts.example');
  expect(admin).toContain('<title>Admin | NextBand</title>');
  expect(admin).toContain('content="noindex, follow"');
});

const page = '<head>\n<!--seo-->\n<title>x</title>\n<!--/seo-->\n</head><body><div id="root"><!--page--><h1>Know your band.</h1><!--/page--></div></body>';

it('gives each public page its own body and structured data on the canonical origin', () => {
  const sp = renderShell(page, '/speaking', 'https://ielts.example');
  expect(sp).toContain('<h1>Free IELTS Speaking practice with a band score</h1>');
  expect(sp).not.toContain('Know your band.');
  expect(sp).toContain('"@type":"BreadcrumbList"');
  expect(sp).toContain('"url":"https://ielts.example/"');
  expect(sp).not.toContain('soyebjim');
  expect(renderShell(page, '/about', 'https://ielts.example')).toContain('"@type":"AboutPage"');
  // the landing page keeps its own shell
  expect(renderShell(page, '/', 'https://ielts.example')).toContain('<h1>Know your band.</h1>');
});

it('treats unknown paths and auth pages as noindex with no page copy', () => {
  for (const p of ['/nonexistent-zz', '/signup', '/.well-known/ai-catalog.json']) {
    const html = renderShell(page, p, 'https://ielts.example');
    expect(html).toContain('content="noindex, follow"');
    expect(html).not.toContain('<main id="ssr">');
    expect(html).not.toContain('"@type":"WebPage"');
  }
});
