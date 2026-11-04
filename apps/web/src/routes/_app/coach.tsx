import { createFileRoute, redirect } from '@tanstack/react-router';

/** The coach is the "Your plan" tab of Improve now; old links (emails, bookmarks) land there. */
export const Route = createFileRoute('/_app/coach')({
  beforeLoad: () => {
    throw redirect({ to: '/improve', replace: true });
  },
});
