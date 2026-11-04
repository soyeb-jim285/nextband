import { createFileRoute, redirect } from '@tanstack/react-router';

/** Review is the Drill tab of Improve now. */
export const Route = createFileRoute('/_app/review')({
  beforeLoad: () => {
    throw redirect({ to: '/improve/drill', search: {}, replace: true });
  },
});
