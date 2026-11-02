import { PAGE_COPY } from './seo-copy';
/** Page titles, descriptions and share images. The server writes them into the HTML for crawlers and link previews
 *  (which do not run the SPA); the web app sets the same title as you navigate. */
export const SITE_NAME = 'NextBand';
export const SITE_TAGLINE = 'Free IELTS practice with instant band scores';

export type PageMeta = { title: string; description: string; image: string; index: boolean };

const DEFAULT: PageMeta = {
  title: `${SITE_NAME}: ${SITE_TAGLINE}`,
  description:
    'Free IELTS practice for Speaking, Writing, Listening and Reading. Timed real-format tests, an AI examiner and a band for every criterion, each mistake marked.',
  image: '/og/home.png?v=2',
  index: true,
};

/** Public pages worth indexing; everything else (results, history, settings, exam screens) gets a title but noindex. */
const PAGES: Record<string, Omit<PageMeta, 'index'>> = {
  '/': {
    title: `Free IELTS practice tests with band scores | ${SITE_NAME}`,
    description:
      'Free IELTS practice tests for Listening, Reading, Writing and Speaking, with an AI examiner and a band for every criterion. Each mistake marked, made in Bangladesh.',
    image: '/og/landing.png?v=2',
  },
  '/speaking': {
    title: `Free IELTS Speaking practice test with band score | ${SITE_NAME}`,
    description:
      'Free IELTS Speaking practice: Part 1, 2 and 3 with a live AI examiner or a recorded one. A band for each criterion, with every pause and mistake marked.',
    image: '/og/speaking.png?v=2',
  },
  '/writing': {
    title: `Free IELTS Writing Task 1 & 2 practice | ${SITE_NAME}`,
    description:
      'Free IELTS Writing Task 1 and Task 2 practice, Academic and General. A band for each criterion, every error marked with the fix, and a rewrite one band higher.',
    image: '/og/writing.png?v=2',
  },
  '/listening': {
    title: `Free IELTS Listening practice tests online | ${SITE_NAME}`,
    description:
      'Free IELTS Listening practice tests with real-exam audio and timing. Instant score and band, the transcript at each answer, and spelling practice for misses.',
    image: '/og/listening.png?v=2',
  },
  '/reading': {
    title: `Free IELTS Reading practice tests, Academic & GT | ${SITE_NAME}`,
    description:
      'Free IELTS Academic and General Training Reading tests. Instant band, where each answer sits in the passage, and True/False/Not Given analysis.',
    image: '/og/reading.png?v=2',
  },
  '/bank': {
    title: `IELTS question bank: Speaking & Writing topics | ${SITE_NAME}`,
    description: 'Browse hundreds of IELTS Speaking cue cards, Part 1 and Part 3 topics, and Writing Task 1 and 2 questions free, and practise any of them.',
    image: '/og/home.png?v=2',
  },
  '/mock': {
    title: `Free full IELTS mock test online | ${SITE_NAME}`,
    description: 'Free full IELTS mock test online: Listening, Reading, Writing and Speaking with an AI examiner, in test-day order. A band for each skill and one overall.',
    image: DEFAULT.image,
  },
  '/signup': { title: `Create your free account | ${SITE_NAME}`, description: DEFAULT.description, image: DEFAULT.image }, // noindex, like /login
  '/privacy': {
    title: `Privacy policy | ${SITE_NAME}`,
    description: 'What NextBand collects, how session recording works, who sees it, and how long it is kept.',
    image: DEFAULT.image,
  },
  '/about': {
    title: `About NextBand: IELTS practice made in Bangladesh`,
    description: 'NextBand is made by Dolphin Commerce, a software company in Mymensingh, Bangladesh: why we built it, how scores are worked out, and the company details.',
    image: DEFAULT.image,
  },
  '/pricing': {
    title: `Pricing: IELTS practice from ৳499 a month | ${SITE_NAME}`,
    description: 'Practise IELTS free. Or pay once from ৳499 a month by bKash, Nagad or card for unlimited Writing and recorded Speaking, and every Listening and Reading test.',
    image: DEFAULT.image,
  },
  '/terms': {
    title: `Terms of service | ${SITE_NAME}`,
    description: 'The terms for using NextBand: accounts, guest practice, paid plans and how scores should be read.',
    image: DEFAULT.image,
  },
  '/refunds': {
    title: `Refund policy | ${SITE_NAME}`,
    description: 'How refunds work for NextBand plans, including a full refund for an unused plan within 7 days.',
    image: DEFAULT.image,
  },
  '/delivery': {
    title: `Delivery policy | ${SITE_NAME}`,
    description: 'How a NextBand plan is delivered: access opens on your account as soon as your payment is confirmed.',
    image: DEFAULT.image,
  },
  '/contact': {
    title: `Contact NextBand support`,
    description: 'How to reach NextBand and Dolphin Commerce for support, billing or anything else.',
    image: DEFAULT.image,
  },
  '/login': { title: `Sign in | ${SITE_NAME}`, description: DEFAULT.description, image: DEFAULT.image },
};

const NOINDEX_TITLES: [RegExp, string][] = [
  [/^\/overview/, 'Dashboard'],
  [/^\/speaking\/(session|live)/, 'Speaking test'],
  [/^\/speaking\/result/, 'Speaking result'],
  [/^\/writing\/(task|full)/, 'Writing test'],
  [/^\/writing\/result/, 'Writing result'],
  [/^\/lr\/run/, 'Test in progress'],
  [/^\/lr\/result/, 'Test result'],
  [/^\/history/, 'History'],
  [/^\/review/, 'Review cards'],
  [/^\/mistakes/, 'Mistakes'],
  [/^\/settings/, 'Settings'],
  [/^\/admin/, 'Admin'],
  [/^\/checkout/, 'Checkout'],
  [/^\/mock/, 'Mock test'], // needs an account (AccountGate), so it stays out of the index with a proper title
  [/^\/(forgot|reset)-password/, 'Reset password'],
];

/** Paths listed in sitemap.xml, with the date their content last changed (bump it when you edit the page). */
export const SITEMAP: Record<string, string> = {
  '/': '2026-11-02',
  '/speaking': '2026-11-02',
  '/writing': '2026-11-02',
  '/listening': '2026-11-02',
  '/reading': '2026-11-02',
  '/bank': '2026-11-02',
  '/mock': '2026-11-02',
  '/pricing': '2026-11-02',
  '/about': '2026-11-02',
  '/privacy': '2026-10-29',
  '/terms': '2026-10-29',
  '/refunds': '2026-10-29',
  '/delivery': '2026-10-29',
  '/contact': '2026-11-02',
};
export const SITEMAP_PATHS = Object.keys(SITEMAP);

/** Every path the web app can render (apps/web routes; the web test routes.test checks the two stay in step). Anything else
 *  is answered with the SPA shell (its not-found screen) but HTTP 404, so crawlers see no soft 404s. */
const ROUTES = [
  /^\/(overview|speaking|speaking\/live|speaking\/session|writing|writing\/full|listening|reading|mock|bank|coach|history|mistakes|review|settings|checkout|checkout\/done|about|contact|delivery|pricing|privacy|refunds|terms|login|signup|forgot-password|reset-password|live-demo|styleguide)?$/,
  /^\/(speaking\/result|writing\/result|writing\/task|lr\/run|lr\/result|mock|admin\/replays|admin\/users)\/[^/]+$/,
  /^\/admin(\/(activity|content|costs|emails|feedback|funnel|growth|health|logs|playground|replays|tests|users))?$/,
];
export const isKnownPath = (path: string) => ROUTES.some((re) => re.test(path.length > 1 ? path.replace(/\/+$/, '') : path));

/** Breadcrumb name for a public page (structured data). */
export const crumbName = (path: string) => PAGE_COPY[path]?.h1 ?? PAGES[path]?.title.split(' | ')[0] ?? SITE_NAME;

export function pageMeta(path: string): PageMeta {
  const p = path.length > 1 ? path.replace(/\/+$/, '') : path;
  const page = PAGES[p];
  if (page) return { ...page, index: p !== '/login' && p !== '/signup' };
  const named = NOINDEX_TITLES.find(([re]) => re.test(p));
  return { ...DEFAULT, title: named ? `${named[1]} | ${SITE_NAME}` : DEFAULT.title, index: false };
}
