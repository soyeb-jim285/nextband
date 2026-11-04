import { Link, useRouterState } from '@tanstack/react-router';
import { ClipboardCheck, Compass, CreditCard, History, Layers, Settings, TriangleAlert, type LucideIcon } from 'lucide-react';
import type { ReactNode } from 'react';
import { buttonStyles, EmptyState, PageContainer, PageHeader } from '@/components/ui';

const WHAT: Record<'history' | 'mistakes' | 'review' | 'settings' | 'mock' | 'coach' | 'checkout' | 'improve', { title: string; page: string; icon: LucideIcon; heading: string; body: string }> = {
  history: { title: 'Create an account to see your history', page: 'History', icon: History, heading: 'History', body: 'Every test you take is kept with your account, so you can open an old result and see how your bands move.' },
  mistakes: { title: 'Create an account to see your patterns', page: 'Patterns', icon: TriangleAlert, heading: 'Patterns', body: 'Your repeated errors are collected across all your tests, so you know what to fix first.' },
  review: { title: 'Create an account to drill your mistakes', page: 'Drill', icon: Layers, heading: 'Drill', body: 'After each test your own mistakes become short drills that come back just before you forget them.' },
  improve: { title: 'Create an account to get your coach', page: 'Improve', icon: Compass, heading: 'Improve', body: 'Your coach reads all your tests together, shows what keeps going wrong and turns it into short drills.' },
  mock: { title: 'Create an account to take a full mock test', page: 'Mock', icon: ClipboardCheck, heading: 'Full mock test', body: 'A mock test runs over several sections and days, and keeps your place and your bands with your account.' },
  coach: { title: 'Create an account to get your coach', page: 'Coach', icon: Compass, heading: 'Your coach', body: 'The coach reads all your tests together and tells you what to fix first, what is improving and what to do this week.' },
  checkout: { title: 'Create an account to buy a plan', page: 'Checkout', icon: CreditCard, heading: 'Checkout', body: 'The paid plan is added to your account, so you can use it on the website and in the apps.' },
  settings: { title: 'Create an account to change settings', page: 'Settings', icon: Settings, heading: 'Settings', body: 'Your target band and examiner live in your account.' },
};

/** What a guest sees instead of a personal page: what it is for and the way to get it. Never an error, never a redirect. */
export function AccountGate({ what, children, inline }: { what: keyof typeof WHAT; /** Shown under the sign-in prompt, e.g. a public page's guide. */ children?: ReactNode; /** Inside a page that already has its header (an Improve tab). */ inline?: boolean }) {
  const redirect = useRouterState({ select: (s) => s.location.href });
  const w = WHAT[what];
  const Icon = w.icon;
  const gate = (
      <EmptyState
        icon={<Icon />}
        title={w.title}
        action={
          <div className="flex flex-col gap-2 sm:flex-row">
            <Link to="/signup" search={{ redirect }} className={buttonStyles({ size: 'lg', className: 'max-sm:w-full' })}>
              Create account
            </Link>
            <Link to="/login" search={{ redirect }} className={buttonStyles({ variant: 'outline', size: 'lg', className: 'max-sm:w-full' })}>
              Sign in
            </Link>
          </div>
        }
      >
        {w.body}
      </EmptyState>
  );
  if (inline) return gate;
  return (
    <PageContainer>
      <PageHeader title={w.heading} />
      {gate}
      {children}
    </PageContainer>
  );
}
