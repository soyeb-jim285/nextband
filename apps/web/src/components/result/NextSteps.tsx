import { Link } from '@tanstack/react-router';
import { ArrowRight } from 'lucide-react';
import { Badge, Button } from '@/components/ui';
import { cn } from '@/lib/utils';
import { rowStyles } from '@/components/bank/ListRow';
import { Section } from './Section';
import { SKILLS, type SkillKey } from './SkillBandStrip';

export type DeeperTab = { label: string; desc: string; count?: number; onOpen: () => void };

const SKILL_TO = { listening: '/listening', reading: '/reading', writing: '/writing', speaking: '/speaking' } as const;

/** Switch tab, then bring the tab bar (first tablist on the page) to the top so the new panel is in view, not the hero. */
const open = (t: DeeperTab) => {
  t.onOpen();
  requestAnimationFrame(() => document.querySelector('[role="tablist"]')?.scrollIntoView({ block: 'start' }));
};

/**
 * End of a result's Overview: the other tabs as full rows (people missed the underline tab bar), then the other three skills.
 * Arrows are always visible here, unlike list rows, because discoverability is the whole point.
 */
/** Primary "go on" button under a result's Summary: the summary is where people stopped reading. */
export function NextTabButton({ tab }: { tab: DeeperTab }) {
  return (
    <Button onClick={() => open(tab)}>
      {tab.label}
      <ArrowRight aria-hidden />
    </Button>
  );
}

export function NextSteps({ tabs, skill }: { tabs: DeeperTab[]; skill: SkillKey }) {
  const others = (Object.keys(SKILLS) as SkillKey[]).filter((k) => k !== skill);
  return (
    <>
      {tabs.length > 0 && (
        <Section title="See the full breakdown" caption="The summary is only the start. Each tab shows where you lost marks and how to fix it.">
          <ul className="grid gap-3 sm:grid-cols-2">
            {tabs.map((t) => (
              <li key={t.label}>
                <button type="button" onClick={() => open(t)} className={cn(rowStyles, 'h-full rounded-lg border border-line px-4 before:inset-x-0')}>
                  <span className="min-w-0 flex-1">
                    <span className="type-subheading flex items-center gap-2">
                      {t.label}
                      {t.count != null && t.count > 0 && <Badge className="type-num h-5 px-1.5">{t.count}</Badge>}
                    </span>
                    <span className="type-caption mt-0.5 block text-pretty">{t.desc}</span>
                  </span>
                  <ArrowRight className="size-4 shrink-0 text-accent-text" aria-hidden />
                </button>
              </li>
            ))}
          </ul>
        </Section>
      )}
      <Section title="Practise another skill">
        <ul className="grid grid-cols-1 gap-3 sm:grid-cols-3">
          {others.map((k) => {
            const { label, Icon } = SKILLS[k];
            return (
              <li key={k}>
                <Link to={SKILL_TO[k]} className={cn(rowStyles, 'rounded-lg border border-line px-4 before:inset-x-0')}>
                  <Icon className="size-5 shrink-0 text-accent-text" aria-hidden />
                  <span className="type-subheading flex-1">{label}</span>
                  <ArrowRight className="size-4 shrink-0 text-muted" aria-hidden />
                </Link>
              </li>
            );
          })}
        </ul>
      </Section>
    </>
  );
}
