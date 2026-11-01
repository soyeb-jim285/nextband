import { fileURLToPath, URL } from 'node:url';
import tailwindcss from '@tailwindcss/vite';
import { tanstackRouter } from '@tanstack/router-plugin/vite';
import react from '@vitejs/plugin-react';
import { defineConfig, type Plugin } from 'vite';

/** Preload the serif (the hero H1; the sans body face loads with the CSS, so it does not compete with the entry script on a slow link) so it downloads with the HTML, not after the CSS is parsed (vite doesn't hash-resolve hand-written preload hrefs). */
const preloadFonts = (): Plugin => ({
  name: 'preload-fonts',
  transformIndexHtml: {
    order: 'post',
    handler: (_html, ctx) =>
      Object.keys(ctx.bundle ?? {})
        .filter((f) => /newsreader-latin-wght-normal.*\.woff2$/.test(f))
        .map((f) => ({ tag: 'link', attrs: { rel: 'preload', as: 'font', type: 'font/woff2', crossorigin: '', href: `/${f}` }, injectTo: 'head' as const })),
  },
});

/** The styleguide is a dev-only reference (its menu link is gated on import.meta.env.DEV too): production builds swap the 40 KB page for a 404 stub so it is not emitted. */
const stubStyleguide = (): Plugin => ({
  name: 'stub-styleguide',
  apply: 'build',
  enforce: 'pre',
  transform: (_code, id) =>
    id.replace(/\\/g, '/').endsWith('/routes/_app/styleguide.tsx')
      ? "import { createFileRoute, notFound } from '@tanstack/react-router';\nexport const Route = createFileRoute('/_app/styleguide')({ beforeLoad: () => { throw notFound(); } });"
      : null,
});

/**
 * The landing page ('/') is the first thing a visitor opens, and its lazy route chunk, that chunk's CSS and its imports
 * only start downloading after the entry has run. Preload them early so they arrive with the entry (one round trip less
 * before the hero paints on a slow mobile link). Every route shares this HTML, so the links are added by a tiny inline
 * script only when index.html marked the page as the landing (data-lp-pre: '/' and no account hint): deep links such as
 * /writing or /overview never download the landing.
 */
const preloadLanding = (): Plugin => ({
  name: 'preload-landing',
  apply: 'build',
  transformIndexHtml: {
    order: 'post',
    handler: (_html, ctx) => {
      const bundle = ctx.bundle ?? {};
      const landing = Object.values(bundle).find((c) => c.type === 'chunk' && /[\\/]src[\\/]routes[\\/]index\.tsx/.test(c.facadeModuleId ?? ''));
      if (!landing || landing.type !== 'chunk') return [];
      const entry = new Set(ctx.chunk ? [ctx.chunk.fileName, ...ctx.chunk.imports] : []); // already preloaded by Vite
      const links = [
        ...[landing.fileName, ...landing.imports].filter((f) => !entry.has(f)).map((f) => ['modulepreload', `/${f}`]),
        ...[...(landing.viteMetadata?.importedCss ?? [])].map((f) => ['style', `/${f}`]),
      ];
      const js = `if(document.documentElement.hasAttribute('data-lp-pre'))for(const[r,h]of ${JSON.stringify(links)}){const l=document.createElement('link');if(r==='style'){l.rel='preload';l.as='style'}else{l.rel=r;l.crossOrigin=''}l.href=h;document.head.appendChild(l)}`;
      return [{ tag: 'script', children: js, injectTo: 'head' as const }];
    },
  },
});

export default defineConfig({
  plugins: [stubStyleguide(), tanstackRouter({ target: 'react', autoCodeSplitting: true }), react(), tailwindcss(), preloadFonts(), preloadLanding()],
  build: {
    rolldownOptions: {
      // The ui barrel (components/ui/index.ts) re-exports every primitive; its modules only declare components, so importing Button must not drag in the menu/dialog/slider code too.
      treeshake: { moduleSideEffects: [{ test: /src[\\/]components[\\/]ui[\\/]/, sideEffects: false }] },
      output: {
        codeSplitting: {
          groups: [
            // What /login, /signup and the other signed-out pages need (buttons, inputs, alerts, the cn helper): the only UI in the entry's load.
            { name: 'ui-core', priority: 20, test: /src[\\/](lib[\\/]utils|components[\\/]ui[\\/](Button|Alert|Badge|Card|Field|IconTile|EmptyState|PageContainer|PageHeader|Skeleton|Toast|shadcn[\\/](button|input|label|alert|badge|card|textarea|skeleton)))\.tsx?$|node_modules[\\/](clsx|tailwind-merge|class-variance-authority|@radix-ui[\\/](react-slot|react-label|react-primitive|react-compose-refs))/ },
            // Menus, dialogs, popovers, sliders, command palette: only the authed app's lazy routes use them.
            { name: 'overlay', priority: 10, test: /node_modules[\\/](@radix-ui|@floating-ui|radix-ui|cmdk|react-remove-scroll|react-remove-scroll-bar|react-style-singleton|use-sidecar|use-callback-ref|aria-hidden|tslib)[\\/]|src[\\/]components[\\/]ui[\\/]/ },
          ],
        },
      },
    },
  },
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  server: { port: Number(process.env.WEB_PORT ?? 5173), proxy: { '/api': { target: process.env.API_PROXY ?? 'http://localhost:8787', changeOrigin: false } } },
});
