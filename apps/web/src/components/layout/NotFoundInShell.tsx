import { Link } from '@tanstack/react-router';
import { Compass } from 'lucide-react';
import { buttonStyles } from '@/components/ui';
import { useHome } from '@/lib/query';
import { AppShell } from './AppShell';
import { ErrorPage } from './RouteError';

/** 404 for a signed-in user: the app nav stays. Its own file so __root (and the login entry) doesn't bundle the whole shell. */
export default function NotFoundInShell() {
  const home = useHome();
  return (
    <AppShell>
      <ErrorPage icon={<Compass />} title="Page not found" action={<Link to={home.to} className={buttonStyles({ variant: 'outline' })}>Back to {home.label.toLowerCase()}</Link>}>
        The link may be old or mistyped.
      </ErrorPage>
    </AppShell>
  );
}
