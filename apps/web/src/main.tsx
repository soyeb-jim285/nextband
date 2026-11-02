import { pageMeta } from '@ielts/core';
import { QueryClientProvider } from '@tanstack/react-query';
import { createRouter, RouterProvider } from '@tanstack/react-router';
import { StrictMode } from 'react';
import { flushSync } from 'react-dom';
import { createRoot } from 'react-dom/client';
import { RouteError } from '@/components/layout/RouteError';
import { PageSkeleton } from '@/components/ui';
import { isStale } from '@/lib/freshBuild';
import { queryClient } from '@/lib/query';
import { routeTree } from './routeTree.gen';
import './styles.css';

// A reload of the landing page opens on the hero, not wherever the visitor had scrolled: its pinned acts make a restored
// mid-page position land between frames. Blocks the browser's own restore here and the router's on the first render.
const nav = performance.getEntriesByType('navigation')[0] as PerformanceNavigationTiming | undefined;
// A reload also drops an in-page hash (#pricing, from the rail or a jump link); a fresh visit to a /#link still jumps.
let reloadHome = location.pathname === '/' && nav?.type === 'reload';
if (reloadHome) {
  history.scrollRestoration = 'manual';
  if (location.hash) history.replaceState(history.state, '', location.pathname + location.search);
  scrollTo(0, 0);
}

const router = createRouter({
  routeTree,
  context: { queryClient },
  defaultPreload: 'intent',
  defaultPreloadStaleTime: 0, // TanStack Query owns caching
  defaultPendingComponent: PageSkeleton,
  defaultErrorComponent: RouteError, // loader errors render inside the parent layout (AppShell)
  scrollRestoration: () => {
    if (!reloadHome) return true;
    reloadHome = false;
    return false;
  },
});

// Hard load: /api/me is already in flight from index.html; start the matched routes' split chunks now too, so the
// router doesn't discover them only after the entry has rendered. _app's loader awaits me in parallel with the page loaders.
for (const r of router.getMatchedRoutes(location.pathname)[0]) router.loadRouteChunk(r)?.catch(() => {}); // the router retries and reports on navigation

// The boot cover (index.html) stays until the first page is on screen, so a hard load never flashes an unstyled page.
function unboot() {
  const w = window as { __nbReady?: boolean; __nbSplashClose?: () => void };
  w.__nbReady = true; // a slow-load splash (index.html) that has not started yet stays away
  w.__nbSplashClose?.();
  const boot = document.getElementById('boot');
  if (!boot) return;
  boot.style.opacity = '0';
  setTimeout(() => boot.remove(), 200);
}

// The tab title follows the page (the server writes the same one into the HTML for crawlers and link previews).
router.subscribe('onResolved', ({ toLocation }) => {
  const m = pageMeta(toLocation.pathname);
  document.title = m.title;
  // Keep the rest of the server's head block (apps/server/src/seo.ts) in step, so the live DOM matches the page.
  document.querySelector('meta[name="description"]')?.setAttribute('content', m.description);
  const canonical = document.querySelector<HTMLLinkElement>('link[rel="canonical"]');
  if (canonical) canonical.href = new URL(toLocation.pathname, canonical.href).href;
  document.querySelector('meta[name="robots"]')?.setAttribute('content', m.index ? 'index, follow, max-image-preview:large' : 'noindex, follow');
});

// After a deploy, reload into the new build at the next navigation between ordinary pages (never into or out of an exam screen).
let stale = false;
const check = () => void isStale().then((s) => (stale ||= s));
setInterval(check, 10 * 60_000);
document.addEventListener('visibilitychange', () => document.visibilityState === 'visible' && check());
const inExam = (path: string) => router.getMatchedRoutes(path)[0].some((r) => r.options.staticData?.exam);
router.subscribe('onBeforeNavigate', ({ fromLocation, toLocation }) => {
  if (stale && !inExam(toLocation.pathname) && !(fromLocation && inExam(fromLocation.pathname))) location.assign(toLocation.href);
});

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router;
  }
  interface StaticDataRouteOption {
    /** Timed exam screen: rendered in ExamShell without the AppShell nav. */
    exam?: boolean;
  }
}

// React mounts only once the first page has loaded (route chunks, loaders), and commits it in one go: the static shell
// in #root (index.html: the landing headline, or a page's own text under the boot cover) stays up until the real page
// replaces it, instead of an empty first commit while the router is still loading.
const root = createRoot(document.getElementById('root')!);
void router
  .load()
  .catch(() => {}) // a failed load renders its error component
  .then(() => {
    flushSync(() =>
      root.render(
        <StrictMode>
          <QueryClientProvider client={queryClient}>
            <RouterProvider router={router} />
          </QueryClientProvider>
        </StrictMode>,
      ),
    );
    unboot();
  });
