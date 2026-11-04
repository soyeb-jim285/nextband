import { useSuspenseQuery } from '@tanstack/react-query';
import { createFileRoute } from '@tanstack/react-router';
import { CoachView } from '@/components/coach/CoachView';
import { coachQuery } from '@/components/coach/data';
import { meQuery } from '@/lib/query';

export const Route = createFileRoute('/_app/improve/')({
  // guests have a session and get a coach too (the layout gates a visitor with no session)
  loader: async ({ context }) => {
    if (await context.queryClient.ensureQueryData(meQuery)) await context.queryClient.ensureQueryData(coachQuery);
  },
  component: PlanPage,
});

function PlanPage() {
  // only this page polls while a report is being written; the dashboard card reads once
  return <CoachView data={useSuspenseQuery({ ...coachQuery, refetchInterval: (q) => (q.state.data?.updating ? 5000 : false) }).data} />;
}
