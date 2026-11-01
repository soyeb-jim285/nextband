import { createFileRoute, redirect, useNavigate } from '@tanstack/react-router';
import { useQuery } from '@tanstack/react-query';
import { useEffect } from 'react';
import { Landing } from '@/components/landing/Landing';
import { hasAccountHint, isAccount, meQuery } from '@/lib/query';

/**
 * The visitor landing page, outside the app shell. A browser that last saw an account (hasAccountHint, set whenever
 * /api/me answers) waits for /api/me and is sent on to its dashboard before anything paints. Everyone else gets the
 * landing at once, with no wait; if /api/me then turns out to be an account (a first sign-in elsewhere, cleared storage),
 * Home redirects as soon as it answers.
 */
export const Route = createFileRoute('/')({
  beforeLoad: async ({ context }) => {
    const qc = context.queryClient;
    if (hasAccountHint()) await qc.ensureQueryData(meQuery).catch(() => null);
    if (isAccount(qc.getQueryData(meQuery.queryKey))) throw redirect({ to: '/overview', replace: true });
  },
  component: Home,
});

function Home() {
  const { data: me } = useQuery(meQuery);
  const navigate = useNavigate();
  const account = isAccount(me);
  useEffect(() => {
    if (account) void navigate({ to: '/overview', replace: true });
  }, [account, navigate]);
  return account ? null : <Landing />;
}
