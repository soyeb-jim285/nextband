import { createFileRoute, redirect } from '@tanstack/react-router';

/** Mistakes is the Patterns tab of Improve now; a ?category= link opens that pattern. */
export const Route = createFileRoute('/_app/mistakes')({
  beforeLoad: ({ search }) => {
    const c = (search as { category?: unknown }).category;
    throw redirect({ to: '/improve/patterns', search: typeof c === 'string' && c ? { pattern: c } : {}, replace: true });
  },
});
