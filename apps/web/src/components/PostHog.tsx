import { useQuery } from '@tanstack/react-query';
import { useEffect } from 'react';
import { meQuery } from '@/lib/query';

/** PostHog project token (public by design: write-only, safe in client code). */
const TOKEN = 'phc_mgjYUkUFzft7ziVz8jjxTPWPF6Loe8Es5xyojVXn5ff2';

type PH = typeof import('posthog-js').default;
let ph: Promise<PH> | null = null;
const dev = typeof location === 'undefined' || location.hostname === 'localhost' || location.hostname === '127.0.0.1';

/** Custom event (exam steps, errors the user saw). Safe anywhere: no-op on localhost and in tests; loads PostHog if it hasn't yet. */
export function track(event: string, props?: Record<string, unknown>) {
  if (!dev) void load().then((p) => p.capture(event, props));
}

/** /api/lr/attempts/3f2a…/submit → /api/lr/attempts/:id/submit, so events group by endpoint. */
export const endpoint = (url: string) => new URL(url, location.origin).pathname.replace(/\/[0-9a-f-]{8,}(?=\/|$)/gi, '/:id');

/** Loads and starts PostHog once, after first paint. Events go through our own /ingest proxy (apps/server/src/app.ts). */
function load(): Promise<PH> {
  ph ??= import('posthog-js').then(({ default: posthog }) => {
    posthog.init(TOKEN, {
      api_host: `${location.origin}/ingest`,
      ui_host: 'https://us.posthog.com',
      defaults: '2025-05-24', // SPA pageviews on route change
      person_profiles: 'identified_only',
      session_recording: { maskAllInputs: true, recordHeaders: false, recordBody: false },
    });
    return posthog;
  });
  return ph;
}

/**
 * Analytics, session replay, network timeline and error tracking (PostHog). Renders nothing. Off on localhost so dev sessions
 * never land in the production project. Identifies signed-in users by id + email so a support report can be matched to a replay.
 */
export function PostHog() {
  const { data: me } = useQuery(meQuery);
    useEffect(() => {
    if (dev) return;
    const go = () => void load();
    if ('requestIdleCallback' in window) {
      const id = requestIdleCallback(go, { timeout: 4000 });
      return () => cancelIdleCallback(id);
    }
    const id = setTimeout(go, 2000);
    return () => clearTimeout(id);
  }, [dev]);

  const userId = me?.user.id;
  const email = me && !me.user.isAnonymous ? me.user.email : undefined;
  const tier = me?.tier;
  useEffect(() => {
    if (dev) return;
    void load().then((p) => {
      if (userId) p.identify(userId, { email, tier, guest: !email });
      else if (p._isIdentified()) p.reset();
    });
  }, [dev, userId, email, tier]);

  return null;
}
