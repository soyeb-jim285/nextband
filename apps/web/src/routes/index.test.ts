import { QueryClient } from '@tanstack/react-query';
import { afterEach, expect, it, vi } from 'vitest';
import { ACCOUNT_HINT, meQuery } from '@/lib/query';
import { Route } from './index';

// The redirect rules of the visitor landing page ('/'): an account goes to /overview, everyone else stays.
const beforeLoad = (qc: QueryClient) => (Route.options.beforeLoad as (a: { context: { queryClient: QueryClient } }) => Promise<unknown>)({ context: { queryClient: qc } });
const account = { user: { isAnonymous: false } } as never;
const guest = { user: { isAnonymous: true } } as never;

afterEach(() => localStorage.clear());

it('sends a known account on to /overview without waiting', async () => {
  const qc = new QueryClient();
  qc.setQueryData(meQuery.queryKey, account);
  await expect(beforeLoad(qc)).rejects.toMatchObject({ options: { to: '/overview' } });
});

it('waits for /api/me when the browser last saw an account, then redirects', async () => {
  localStorage.setItem(ACCOUNT_HINT, '1');
  const qc = new QueryClient();
  qc.ensureQueryData = vi.fn(async () => (qc.setQueryData(meQuery.queryKey, account), account)) as never;
  await expect(beforeLoad(qc)).rejects.toMatchObject({ options: { to: '/overview' } });
});

it('shows the landing when the hint is there but /api/me fails, and for a guest or a first visit', async () => {
  localStorage.setItem(ACCOUNT_HINT, '1');
  const failing = new QueryClient();
  failing.ensureQueryData = vi.fn().mockRejectedValue(new Error('offline')) as never;
  await expect(beforeLoad(failing)).resolves.toBeUndefined();

  const qc = new QueryClient();
  qc.setQueryData(meQuery.queryKey, guest);
  localStorage.clear();
  await expect(beforeLoad(qc)).resolves.toBeUndefined();
  await expect(beforeLoad(new QueryClient())).resolves.toBeUndefined();
});
