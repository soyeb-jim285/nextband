import { crumbName, isKnownPath, PAGE_COPY, PAID_PLANS, pageMeta, SITE_NAME, SITEMAP } from '@ielts/core';
import type { Hono } from 'hono';
import { env } from './env';

const esc = (s: string) => s.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/'/g, '&#39;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

/** Organization, brand, site and app on every page; plus the page itself and its breadcrumb on public pages. */
function jsonLd(path: string, url: string, origin: string, title: string, description: string, index: boolean) {
  const id = (k: string) => `${origin}/#${k}`;
  const offer = (name: string, price: number) => ({ '@type': 'Offer', name, price: String(price), priceCurrency: 'BDT', url: `${origin}/pricing`, availability: 'https://schema.org/InStock' });
  const graph: object[] = [
    {
      '@type': 'Organization',
      '@id': id('org'),
      name: 'Dolphin Commerce',
      url: `${origin}/`,
      logo: `${origin}/icon-512.png`,
      email: 'support@nextband.app',
      brand: { '@id': id('brand') },
      address: { '@type': 'PostalAddress', streetAddress: 'Sutia Bazar, Ward 6, Jatia Union, Ishwarganj', addressLocality: 'Mymensingh', postalCode: '2280', addressCountry: 'BD' },
    },
    { '@type': 'Brand', '@id': id('brand'), name: SITE_NAME, logo: `${origin}/icon-512.png` },
    { '@type': 'WebSite', '@id': id('website'), name: SITE_NAME, url: `${origin}/`, inLanguage: 'en', publisher: { '@id': id('org') } },
    {
      '@type': 'WebApplication',
      '@id': id('app'),
      name: SITE_NAME,
      url: `${origin}/`,
      applicationCategory: 'EducationalApplication',
      operatingSystem: 'Web, iOS, Android',
      description: 'Free IELTS practice for Speaking, Writing, Listening and Reading: timed real-format tests, an AI examiner and band scores for every criterion.',
      educationalUse: 'Test preparation',
      about: { '@type': 'Thing', name: 'IELTS' },
      publisher: { '@id': id('org') },
      offers: {
        '@type': 'AggregateOffer',
        priceCurrency: 'BDT',
        lowPrice: '0',
        highPrice: String(PAID_PLANS.quarter.bdt),
        offerCount: 3,
        offers: [offer('Free', 0), offer('1 month', PAID_PLANS.month.bdt), offer('3 months', PAID_PLANS.quarter.bdt)],
      },
    },
  ];
  if (index) {
    const type = path === '/about' ? 'AboutPage' : path === '/contact' ? 'ContactPage' : 'WebPage';
    graph.push({ '@type': type, '@id': `${url}#page`, url, name: title, description, isPartOf: { '@id': id('website') }, inLanguage: 'en' });
    if (path !== '/')
      graph.push({
        '@type': 'BreadcrumbList',
        itemListElement: [
          { '@type': 'ListItem', position: 1, name: SITE_NAME, item: `${origin}/` },
          { '@type': 'ListItem', position: 2, name: crumbName(path), item: url },
        ],
      });
  }
  // "<" escaped so no string in the graph can close the script element.
  return `<script type="application/ld+json">${JSON.stringify({ '@context': 'https://schema.org', '@graph': graph }).replace(/</g, '\\u003c')}</script>`;
}

const PRACTISE = [['/speaking', 'Speaking practice'], ['/writing', 'Writing practice'], ['/listening', 'Listening tests'], ['/reading', 'Reading tests'], ['/mock', 'Full mock test'], ['/bank', 'Question bank']];
const FOOTER = [['/', 'Home'], ['/pricing', 'Pricing'], ['/about', 'About'], ['/contact', 'Contact'], ['/privacy', 'Privacy'], ['/terms', 'Terms'], ['/refunds', 'Refunds'], ['/delivery', 'Delivery']];
const links = (xs: string[][]) => xs.map(([h, t]) => `<a href="${h}">${t}</a>`).join(' · ');

/** The page's words as plain HTML, for crawlers that do not run the SPA. React replaces #root on mount, and the boot cover
 *  hides this until then, so a visitor never sees it; it is the same copy SeoGuide shows under each hub. */
function body(path: string, title: string, description: string, index: boolean) {
  if (!index) return `<noscript><p>${SITE_NAME} needs JavaScript to run.</p></noscript>`;
  const c = PAGE_COPY[path];
  const parts = c
    ? [`<h1>${esc(c.h1)}</h1>`, `<p>${esc(c.intro)}</p>`, ...c.sections.map((s) => `<h2>${esc(s.h)}</h2><p>${esc(s.p)}</p>`)]
    : [`<h1>${esc(title.split(' | ')[0]!)}</h1>`, `<p>${esc(description)}</p>`];
  return `<main id="ssr">${parts.join('')}<nav aria-label="Practise"><p>${links(PRACTISE)}</p></nav><footer><p>${links(FOOTER)}</p><p>${SITE_NAME} is made by Dolphin Commerce, Mymensingh, Bangladesh.</p></footer></main>`;
}

/** The SPA shell with this path's title, description, canonical URL, share card and structured data written in, replacing
 *  the <!--seo-->…<!--/seo--> defaults of index.html; other pages than '/' also get their own body in place of the landing
 *  shell (<!--page-->…<!--/page-->). Link previews (WhatsApp, Facebook, X, Slack) and many crawlers read only this HTML. */
export function renderShell(html: string, path: string, origin = env.WEB_ORIGIN): string {
  const p = path.length > 1 ? path.replace(/\/+$/, '') : '/';
  const m = pageMeta(p);
  const index = m.index && isKnownPath(p);
  const url = origin + p;
  const img = origin + m.image;
  const tags = [
    `<title>${esc(m.title)}</title>`,
    `<meta name="description" content="${esc(m.description)}" />`,
    `<meta name="robots" content="${index ? 'index, follow, max-image-preview:large' : 'noindex, follow'}" />`,
    `<link rel="canonical" href="${esc(url)}" />`,
    `<meta property="og:type" content="website" />`,
    `<meta property="og:site_name" content="${SITE_NAME}" />`,
    `<meta property="og:locale" content="en_GB" />`,
    `<meta property="og:url" content="${esc(url)}" />`,
    `<meta property="og:title" content="${esc(m.title)}" />`,
    `<meta property="og:description" content="${esc(m.description)}" />`,
    `<meta property="og:image" content="${esc(img)}" />`,
    `<meta property="og:image:width" content="1200" />`,
    `<meta property="og:image:height" content="630" />`,
    `<meta property="og:image:alt" content="${esc(m.title)}" />`,
    `<meta name="twitter:card" content="summary_large_image" />`,
    `<meta name="twitter:title" content="${esc(m.title)}" />`,
    `<meta name="twitter:description" content="${esc(m.description)}" />`,
    `<meta name="twitter:image" content="${esc(img)}" />`,
    jsonLd(p, url, origin, m.title, m.description, index),
  ].join('\n    ');
  // Replacer functions: the URL path is user-controlled, and a replacement *string* would expand "$'" / "$`" patterns in it.
  let out = html.replace(/<!--seo-->[\s\S]*?<!--\/seo-->/, () => `<!--seo-->\n    ${tags}\n    <!--/seo-->`);
  if (p !== '/') out = out.replace(/<!--page-->[\s\S]*?<!--\/page-->/, () => `<!--page-->${body(p, m.title, m.description, index)}<!--/page-->`);
  return out;
}

const LLMS = (o: string) => `# ${SITE_NAME}

> IELTS practice for all four skills, made in Bangladesh by Dolphin Commerce. Timed real-format tests, a live AI Speaking examiner, and an estimated band for every criterion with each mistake marked. Practice, not the official test: bands are estimates.

Free with an account: 5 Writing tests a week, 10 recorded Speaking tests a month, 5 live examiner sessions a month, most Listening and Reading tests. Paid plan: ৳${PAID_PLANS.month.bdt} for 1 month or ৳${PAID_PLANS.quarter.bdt} for 3 months (one payment by bKash, Nagad, Rocket, Upay or card; no auto-renewal; full refund within 7 days if unused).

## Practise
- [Speaking practice](${o}/speaking): Part 1, 2 and 3, recorded or with the live AI examiner
- [Writing practice](${o}/writing): Academic and General Training Task 1 and Task 2
- [Listening tests](${o}/listening): full tests or one part at a time
- [Reading tests](${o}/reading): Academic and General Training
- [Full mock test](${o}/mock): all four skills with an overall band
- [Question bank](${o}/bank): Speaking topics and Writing tasks

## Company
- [Pricing](${o}/pricing)
- [About](${o}/about)
- [Contact](${o}/contact): support@nextband.app
`;

export function registerSeo(app: Hono<any>) {
  app.get('/robots.txt', (c) =>
    c.text(['User-agent: *', 'Allow: /', 'Disallow: /api/', 'Disallow: /docs', `Sitemap: ${env.WEB_ORIGIN}/sitemap.xml`, ''].join('\n')),
  );
  app.get('/sitemap.xml', (c) => {
    const urls = Object.entries(SITEMAP).map(([p, d]) => `  <url><loc>${env.WEB_ORIGIN}${p}</loc><lastmod>${d}</lastmod></url>`);
    return c.body(`<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${urls.join('\n')}\n</urlset>\n`, 200, { 'Content-Type': 'application/xml; charset=utf-8' });
  });
  app.get('/llms.txt', (c) => c.text(LLMS(env.WEB_ORIGIN)));
}
