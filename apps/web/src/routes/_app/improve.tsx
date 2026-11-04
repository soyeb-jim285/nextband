import { useQuery } from '@tanstack/react-query';
import { createFileRoute, Link, Outlet } from '@tanstack/react-router';
import { AccountGate } from '@/components/community/AccountGate';
import { dueQuery } from '@/components/improve/data';
import { Badge, PageContainer, PageHeader } from '@/components/ui';
import { useAccount, useMe } from '@/lib/query';

export const Route = createFileRoute('/_app/improve')({
  component: ImproveLayout,
});

/** Improve: your plan (the coach), your patterns (what keeps going wrong) and the drill (short sessions on your own mistakes), one header and three tabs. */
function ImproveLayout() {
  // guests have a session and get a coach too; only a visitor with no session sees the gate
  if (!useMe().data) return <AccountGate what="improve" />;
  return (
    <PageContainer>
      <PageHeader title="Improve" description="See what keeps going wrong, drill it for a few minutes, and watch it shrink." />
      <ImproveNav />
      <div className="mt-8">
        <Outlet />
      </div>
    </PageContainer>
  );
}

const tab =
  'relative inline-flex h-11 shrink-0 items-center gap-1.5 px-3 text-sm text-muted hover:text-ink max-sm:grow max-sm:justify-center max-sm:px-1.5 md:h-10 ' +
  'focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-ring ' +
  'data-[status=active]:font-medium data-[status=active]:text-ink data-[status=active]:after:absolute data-[status=active]:after:inset-x-0 data-[status=active]:after:bottom-0 data-[status=active]:after:h-0.5 data-[status=active]:after:bg-brand';

function ImproveNav() {
  const due = useQuery({ ...dueQuery(), enabled: !!useAccount() }).data?.total ?? 0;
  return (
    <nav aria-label="Improve" className="flex border-b border-line sm:gap-1">
      <Link to="/improve" activeOptions={{ exact: true, includeSearch: false }} className={tab}>
        Your plan
      </Link>
      <Link to="/improve/patterns" activeOptions={{ includeSearch: false }} className={tab}>
        Patterns
      </Link>
      <Link to="/improve/drill" activeOptions={{ includeSearch: false }} className={tab}>
        Drill
        {due > 0 && (
          <Badge tone="accent" className="type-num h-5 px-1.5">
            {due}
            <span className="sr-only"> due</span>
          </Badge>
        )}
      </Link>
    </nav>
  );
}
