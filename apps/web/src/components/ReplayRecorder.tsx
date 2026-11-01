import { useRouter, useRouterState } from '@tanstack/react-router';
import { useEffect } from 'react';
import { startReplay } from '@/lib/replay';

/**
 * Records the tab with rrweb for the owner's support tooling (docs/admin/DESIGN.md). Renders nothing; waits for idle so it
 * never delays first paint. Not on the visitor landing page ('/'): rrweb's boot and snapshot cost the page's own load and
 * scroll, and recording starts on the first other page instead.
 */
export function ReplayRecorder() {
  const router = useRouter();
  const onLanding = useRouterState({ select: (s) => s.location.pathname === '/' });
  useEffect(() => {
    if (onLanding) return;
    const go = () => startReplay(router);
    if ('requestIdleCallback' in window) {
      const id = requestIdleCallback(go);
      return () => cancelIdleCallback(id);
    }
    const id = setTimeout(go, 2000);
    return () => clearTimeout(id);
  }, [router, onLanding]);
  return null;
}
