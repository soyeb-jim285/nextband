import { Smartphone } from 'lucide-react';
import { EmptyState, PageContainer } from '@/components/ui';

/** Shown instead of pricing/checkout in a tab opened from the Play app (lib/inApp.ts). Plain text, no link onward. */
export function InAppNotice() {
  return (
    <PageContainer>
      <EmptyState
        icon={<Smartphone />}
        title="Plans aren't available here"
      >
        You opened this page from the NextBand app, where plans can't be bought. Your free tests and any plan you already have keep
        working in the app.
      </EmptyState>
    </PageContainer>
  );
}
