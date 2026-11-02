import { createFileRoute } from '@tanstack/react-router';
import { AccountGate } from '@/components/community/AccountGate';
import { MockStart } from '@/components/mock/MockStart';
import { PageContainer, PageHeader } from '@/components/ui';
import { SeoGuide } from '@/components/SeoGuide';
import { useAccount } from '@/lib/query';

export const Route = createFileRoute('/_app/mock/')({ component: MockPage });

function MockPage() {
  if (!useAccount())
    return (
      <AccountGate what="mock">
        <SeoGuide path="/mock" />
      </AccountGate>
    );
  return (
    <PageContainer>
      <PageHeader title="Full mock test" description="Listening, Reading, Writing and Speaking in one guided run, with a combined overall band." />
      <MockStart />
      <SeoGuide path="/mock" />
    </PageContainer>
  );
}
