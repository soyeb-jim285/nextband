import { infiniteQueryOptions, queryOptions, useInfiniteQuery, useMutation, useQuery, useSuspenseQuery } from '@tanstack/react-query';
import { createFileRoute, Link } from '@tanstack/react-router';
import { clsx } from 'clsx';
import { ArrowRight, Check, ChevronDown, Plus, TrendingDown, TrendingUp, TriangleAlert } from 'lucide-react';
import { useEffect, useRef, useState, type ReactNode } from 'react';
import { runs } from '@/components/bank/group';
import { LoadMore, nextPage } from '@/components/bank/LoadMore';
import { listStyles } from '@/components/bank/ListRow';
import { Patterns as Habits } from '@/components/coach/Patterns';
import { useFeedback } from '@/components/coach/CoachView';
import { coachQuery, type Report } from '@/components/coach/data';
import { AccountGate } from '@/components/community/AccountGate';
import { type Pattern, patternsQuery } from '@/components/improve/data';
import { Section } from '@/components/result';
import { Badge, Button, buttonStyles, Chip, EmptyState, Segmented, Skeleton, toast } from '@/components/ui';
import { call, client, type Schemas } from '@/lib/api';
import { formatClock, formatRelative, plural } from '@/lib/format';
import { loadForAccount, useAccount } from '@/lib/query';
import { categoryLabel } from '@/lib/result';
import { cn } from '@/lib/utils';

type Mistake = Schemas['Mistake'];
type Search = { pattern?: string; view?: 'tests' };

const mistakesQuery = (category?: string) =>
  infiniteQueryOptions({
    queryKey: ['mistakes', { category }],
    queryFn: ({ pageParam }) => call(client.GET('/api/mistakes', { params: { query: { page: pageParam, category } } })),
    initialPageParam: 1,
    getNextPageParam: nextPage,
  });
const spellingQuery = queryOptions({ queryKey: ['lr-spelling'], queryFn: () => call(client.GET('/api/lr/spelling')), staleTime: 0 });

export const Route = createFileRoute('/_app/improve/patterns')({
  validateSearch: (s: Record<string, unknown>): Search => ({
    pattern: typeof s.pattern === 'string' && s.pattern ? s.pattern : undefined,
    view: s.view === 'tests' ? 'tests' : undefined,
  }),
  loader: ({ context }) => loadForAccount(context.queryClient, () => context.queryClient.ensureQueryData(patternsQuery)),
  component: () => (useAccount() ? <PatternsPage /> : <AccountGate what="mistakes" inline />),
});

function PatternsPage() {
  const { pattern, view } = Route.useSearch();
  const navigate = Route.useNavigate();
  const { patterns, deck } = useSuspenseQuery(patternsQuery).data;
  const spelling = useQuery(spellingQuery).data?.items ?? [];
  const report = useQuery(coachQuery).data?.report;

  if (!patterns.length && !spelling.length)
    return (
      <EmptyState
        icon={<TriangleAlert />}
        title="No patterns yet"
        action={
          <Link to="/writing" className={buttonStyles()}>
            Write an essay
          </Link>
        }
      >
        Grammar slips, word choices and spelling from your results collect here, grouped by kind, so you can see which ones keep coming back and drill them.
      </EmptyState>
    );

  return (
    <div className="space-y-10">
      <div className="flex flex-wrap items-center justify-between gap-x-6 gap-y-3">
        <p className="type-lede max-w-[60ch]">
          {view === 'tests' ? 'Every correction, test by test.' : 'Your mistakes grouped by kind, the ones in the most tests first.'}
        </p>
        <Segmented
          label="Group mistakes"
          size="sm"
          value={view ?? 'patterns'}
          onChange={(v) => navigate({ search: v === 'tests' ? { view: 'tests' } : {}, replace: true })}
          options={[
            { value: 'patterns', label: 'By pattern' },
            { value: 'tests', label: 'By test' },
          ]}
        />
      </div>
      {view === 'tests' ? (
        <ByTest />
      ) : (
        <>
          <ul className={listStyles} aria-label="Patterns">
            {spelling.length > 0 && <SpellingRow items={spelling} open={pattern === 'spelling'} due={deck.find((d) => d.category === 'spelling')?.due ?? 0} cards={deck.find((d) => d.category === 'spelling')?.cards ?? 0} />}
            {patterns.map((p) => (
              <PatternRow key={p.category} p={p} open={pattern === p.category} />
            ))}
          </ul>
          {report && <HabitsSection report={report} />}
        </>
      )}
    </div>
  );
}

/** "2.3 → 1.0 a test": the trend in words and an arrow, colour only as a second signal. */
function Trend({ p }: { p: Pattern }) {
  if (p.recent == null || p.before == null || (!p.recent && !p.before)) return null;
  const down = p.recent < p.before;
  const flat = p.recent === p.before;
  const Icon = down ? TrendingDown : TrendingUp;
  return (
    <span className={cn('type-caption type-num inline-flex items-center gap-1', !flat && (down ? 'text-good-text' : 'text-warn-text'))}>
      {!flat && <Icon className="size-3.5" aria-hidden />}
      {p.before} → {p.recent} a test
      <span className="sr-only">{flat ? ', steady' : down ? ', going down' : ', going up'}</span>
    </span>
  );
}

function DrillLink({ category, due, cards }: { category: string; due: number; cards?: number }) {
  if (cards === 0) return null;
  return (
    <Link to="/improve/drill" search={{ pattern: category, ...(due === 0 && { ahead: true }) }} className={buttonStyles({ variant: 'outline', size: 'sm', className: 'shrink-0' })}>
      Drill{due > 0 && <span className="type-num font-normal text-muted">· {due} due</span>}
      <ArrowRight aria-hidden />
    </Link>
  );
}

/** One kind of mistake: how often, the trend, the newest example, drill it; open it for every example. */
function PatternRow({ p, open: initial }: { p: Pattern; open: boolean }) {
  const [open, setOpen] = useState(initial);
  const ref = useRef<HTMLLIElement>(null);
  useEffect(() => {
    if (initial) ref.current?.scrollIntoView({ block: 'start' });
  }, [initial]);
  const id = `pattern-${p.category}`;
  return (
    <li ref={ref} className="scroll-mt-24 py-5">
      <div className="grid gap-x-8 gap-y-3 md:grid-cols-[minmax(0,1fr)_auto] md:items-start">
        <div className="min-w-0">
          <h2 className="type-subheading">{categoryLabel(p.category)}</h2>
          <p className="type-caption mt-0.5 flex flex-wrap items-center gap-x-3 gap-y-1">
            <span>
              In {p.tests} of your {plural(p.outOf, 'test')}, {plural(p.count, 'time')} in all
            </span>
            <Trend p={p} />
          </p>
          {p.example.original.trim() !== p.example.correction.trim() && (
            <p className="type-reading-sm mt-2 max-w-[68ch] truncate">
              <del className="text-bad-text decoration-bad/60">{p.example.original}</del>
              <ArrowRight role="img" aria-label="corrected to" className="mx-2 inline size-4 text-muted" />
              <ins className="font-medium text-good-text no-underline">{p.example.correction}</ins>
            </p>
          )}
        </div>
        <div className="flex flex-wrap items-center gap-2 md:justify-end">
          <DrillLink category={p.category} due={p.due} cards={p.cards} />
          <Button variant="ghost" size="sm" aria-expanded={open} aria-controls={id} onClick={() => setOpen(!open)} className="max-md:min-h-11">
            {open ? 'Hide examples' : 'All examples'}
            <ChevronDown className={clsx('transition-transform duration-200', open && 'rotate-180')} aria-hidden />
          </Button>
        </div>
      </div>
      {open && (
        <div id={id} className="mt-3">
          <Examples category={p.category} />
        </div>
      )}
    </li>
  );
}

/** Every mistake of one category, newest first, with the test it came from. */
function Examples({ category }: { category: string }) {
  const { data, hasNextPage, isFetchingNextPage, fetchNextPage, isPending } = useInfiniteQuery(mistakesQuery(category));
  if (isPending) return <Skeleton className="h-24 w-full" />;
  const items = data?.pages.flatMap((p) => p.items) ?? [];
  return (
    <>
      <ul className="divide-y divide-line rounded-lg bg-surface-2 px-4">
        {items.map((m) => (
          <MistakeItem key={m.id} m={m} source />
        ))}
      </ul>
      <LoadMore hasMore={!!hasNextPage} loading={isFetchingNextPage} onLoad={() => void fetchNextPage()} />
    </>
  );
}

/** The old log: one block per test, its prompt named once, its corrections underneath, filterable by category. */
function ByTest() {
  const [category, setCategory] = useState<string>();
  const { data, hasNextPage, isFetchingNextPage, fetchNextPage, isPending } = useInfiniteQuery(mistakesQuery(category));
  const groups = data?.pages[0]?.groups ?? [];
  const all = groups.reduce((n, g) => n + g.count, 0);
  const items = data?.pages.flatMap((p) => p.items) ?? [];
  if (isPending) return <Skeleton className="h-64 w-full" />;
  return (
    <div>
      <div
        className="-mx-4 mb-8 flex snap-x snap-proximity scroll-px-4 gap-2 overflow-x-auto px-4 pb-1 [scrollbar-width:none] [mask-image:linear-gradient(to_right,black_calc(100%-2.5rem),transparent)] sm:-mx-6 sm:scroll-px-6 sm:px-6 md:mx-0 md:flex-wrap md:[mask-image:none] md:px-0"
        role="group"
        aria-label="Filter by category"
      >
        <Chip className="shrink-0 snap-start" selected={!category} onClick={() => setCategory(undefined)}>
          All <span className="type-num opacity-70">{all}</span>
        </Chip>
        {groups.map((g) => (
          <Chip key={g.category} className="shrink-0 snap-start" selected={category === g.category} onClick={() => setCategory(g.category)}>
            {categoryLabel(g.category)} <span className="type-num opacity-70">{g.count}</span>
          </Chip>
        ))}
      </div>
      {runs(items, (m) => m.attemptId).map((g) => (
        <AttemptGroup key={`${g.key}-${g.items[0]!.id}`} first={g.items[0]!}>
          <ul className={listStyles}>
            {g.items.map((m) => (
              <MistakeItem key={m.id} m={m} showCategory={!category} />
            ))}
          </ul>
        </AttemptGroup>
      ))}
      <LoadMore hasMore={!!hasNextPage} loading={isFetchingNextPage} onLoad={() => void fetchNextPage()} />
    </div>
  );
}

const resultLink = (m: Mistake) =>
  m.skill === 'speaking'
    ? ({ to: '/speaking/result/$attemptId', params: { attemptId: m.attemptId }, search: { tab: 'transcript' } } as const)
    : ({ to: '/writing/result/$attemptId', params: { attemptId: m.attemptId }, search: { tab: 'essay' } } as const);
const where = (m: Mistake) => `${m.skill === 'speaking' ? `Speaking Part ${m.part}` : `Writing Task ${m.part}`}, ${formatRelative(m.createdAt)}`;

/** Heading of one attempt's corrections: the prompt title (links to the result), then where and when. */
function AttemptGroup({ first: m, children }: { first: Mistake; children: ReactNode }) {
  return (
    <section aria-label={m.promptTitle} className="[&:not(:first-of-type)]:mt-12">
      <div className="mb-2 flex flex-col gap-0.5 sm:flex-row sm:items-baseline sm:justify-between sm:gap-6">
        <h2 className="min-w-0">
          <Link {...resultLink(m)} className="type-reading-sm block truncate rounded-sm underline decoration-line decoration-1 underline-offset-4 hover:text-accent-text hover:decoration-accent-text">
            {m.promptTitle}
          </Link>
        </h2>
        <span className="type-caption shrink-0">{where(m)}</span>
      </div>
      {children}
    </section>
  );
}

/** One correction. `source` names and links its test (inside a pattern); "Add to drills" covers one the drill picker left out. */
function MistakeItem({ m, showCategory, source }: { m: Mistake; showCategory?: boolean; source?: boolean }) {
  const [added, setAdded] = useState(m.inDeck);
  const same = m.original.trim() === m.correction.trim(); // nothing to diff: the explanation carries it
  // Off-topic (task.relevance) spans quote whole answers: clamp them to two lines each. ponytail: length heuristic, not measured overflow.
  const long = !same && m.original.length + m.correction.length > 200;
  const [expanded, setExpanded] = useState(false);
  const clamp = long && !expanded;
  const add = useMutation({
    mutationFn: () => call(client.POST('/api/mistakes/{id}/card', { params: { path: { id: m.id } } })),
    onSuccess: () => {
      setAdded(true);
      toast('Added to your drills', { tone: 'good' });
    },
    onError: (e) => toast(e.message, { tone: 'bad' }),
  });

  return (
    <li className={cn('grid gap-x-8 gap-y-2 py-5', source ? 'md:grid-cols-[minmax(0,1fr)_auto]' : 'md:grid-cols-[9rem_minmax(0,1fr)_auto]')}>
      {!source && (
        <div className="flex flex-wrap items-center gap-x-3 gap-y-1 md:flex-col md:items-start md:gap-y-1.5">
          {showCategory && <Badge>{categoryLabel(m.category)}</Badge>}
          {m.time != null && <span className="type-caption type-num">at {formatClock(m.time)}</span>}
        </div>
      )}
      <div className="min-w-0 max-w-[68ch]">
        {!same && (
          <p className="type-reading-sm flex flex-col gap-1">
            <del className={clsx('text-bad-text decoration-bad/60', clamp && 'line-clamp-2')}>{m.original}</del>
            <span className="flex min-w-0 items-start gap-2">
              <ArrowRight role="img" className="mt-1.5 size-4 shrink-0 text-muted" aria-label="corrected to" />
              <ins className={clsx('min-w-0 font-medium text-good-text no-underline', clamp && 'line-clamp-2')}>{m.correction}</ins>
            </span>
          </p>
        )}
        {long && (
          <Button variant="link" className="-ml-1 px-1 max-md:min-h-11" onClick={() => setExpanded(!expanded)} aria-expanded={expanded}>
            {expanded ? 'Show less' : 'Show more'}
          </Button>
        )}
        <p className={clsx('type-body text-pretty', same ? 'text-ink' : 'mt-2 text-muted')}>{m.explanation}</p>
        {source && (
          <p className="type-caption mt-1">
            <Link {...resultLink(m)} className="underline decoration-line underline-offset-4 hover:text-accent-text">
              {m.promptTitle}
            </Link>{' '}
            · {where(m)}
            {m.time != null && <span className="type-num"> · at {formatClock(m.time)}</span>}
          </p>
        )}
      </div>
      <div className="md:justify-self-end">
        <Button size="sm" variant="ghost" className="-ml-3 px-3 max-md:min-h-11 md:ml-0 md:-mr-3" icon={added ? <Check /> : <Plus />} loading={add.isPending} disabled={added} onClick={() => add.mutate()}>
          {added ? 'In drills' : 'Add to drills'}
        </Button>
      </div>
    </li>
  );
}

/** Words misspelt (or wrongly pluralised) in Listening and Reading gap answers, as one pattern row; each misspelling is already a drill card. */
function SpellingRow({ items, open: initial, due, cards }: { items: Schemas['LrSpelling']['items']; open: boolean; due: number; cards: number }) {
  const [open, setOpen] = useState(initial);
  const [more, setMore] = useState(false);
  const list = more ? items : items.slice(0, 8);
  const repeats = items.filter((w) => w.count > 1).length;
  const top = items[0]!;
  return (
    <li className="py-5">
      <div className="grid gap-x-8 gap-y-3 md:grid-cols-[minmax(0,1fr)_auto] md:items-start">
        <div className="min-w-0">
          <h2 className="type-subheading">Spelling and plurals</h2>
          <p className="type-caption mt-0.5">
            {plural(items.length, 'word')} in Listening and Reading{repeats > 0 && `, ${repeats} more than once`}
          </p>
          <p className="type-reading-sm mt-2 truncate">
            <del className="text-bad-text decoration-bad/60">{top.typed[0]}</del>
            <ArrowRight role="img" aria-label="corrected to" className="mx-2 inline size-4 text-muted" />
            <ins className="font-medium text-good-text no-underline">{top.word}</ins>
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2 md:justify-end">
          <DrillLink category="spelling" due={due} cards={cards} />
          <Button variant="ghost" size="sm" aria-expanded={open} aria-controls="pattern-spelling" onClick={() => setOpen(!open)} className="max-md:min-h-11">
            {open ? 'Hide words' : 'All words'}
            <ChevronDown className={clsx('transition-transform duration-200', open && 'rotate-180')} aria-hidden />
          </Button>
        </div>
      </div>
      {open && (
        <div id="pattern-spelling" className="mt-3">
          <ul className="grid gap-x-10 border-t border-line md:grid-cols-2">
            {list.map((w) => (
              <li key={`${w.kind}-${w.word}`} className="flex items-baseline justify-between gap-4 border-b border-line py-3">
                <div className="min-w-0">
                  <p className="type-num font-semibold">
                    {w.word} <span className="type-caption font-normal">{w.kind === 'plural' ? 'plural' : 'spelling'}</span>
                  </p>
                  <p className="type-caption">
                    You wrote <span className="sr-only">: </span>
                    {w.typed.map((t, i) => (
                      <span key={t}>
                        {i > 0 && ', '}
                        <del className="text-bad-text">{t}</del>
                      </span>
                    ))}
                  </p>
                </div>
                <span className={cn('type-num shrink-0 text-sm', w.count > 1 ? 'font-semibold text-warn-text' : 'text-muted')}>
                  {w.count}
                  <span className="sr-only"> {w.count === 1 ? 'time' : 'times'}</span>
                  <span aria-hidden>×</span>
                </span>
              </li>
            ))}
          </ul>
          {items.length > 8 && (
            <Button variant="ghost" size="sm" className="mt-2 max-md:min-h-11" onClick={() => setMore(!more)} aria-expanded={more}>
              {more ? 'Show fewer' : `Show all ${items.length}`}
            </Button>
          )}
        </div>
      )}
    </li>
  );
}

/** The coach's view across skills (words to swap, how you write and speak, where Listening and Reading marks go). Spelling has its own row above. */
function HabitsSection({ report }: { report: Report }) {
  const fb = useFeedback(report);
  return (
    <Section title="Habits across your tests" caption="From your coach: words you lean on, and where Listening and Reading marks go.">
      {/* spelling and the writing/speaking mistake kinds are the rows above */}
      <Habits facts={{ ...report.facts, spelling: [], patterns: { ...report.facts.patterns, writing: [], speaking: [] } }} fb={fb} />
    </Section>
  );
}
