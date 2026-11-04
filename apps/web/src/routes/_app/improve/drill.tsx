import { review } from '@ielts/core';
import { useMutation, useQuery, useQueryClient, useSuspenseQuery } from '@tanstack/react-query';
import { createFileRoute, Link } from '@tanstack/react-router';
import { clsx } from 'clsx';
import { diffWords } from 'diff';
import { Check, CircleCheck, Layers, Volume2, X } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { AccountGate } from '@/components/community/AccountGate';
import { answerOf, type DrillCard, dueQuery, promptOf, sameAnswer } from '@/components/improve/data';
import { SESSION } from '@/components/improve/Today';
import { Button, buttonStyles, Card, EmptyState, Input, Kbd, ProgressBar, Textarea, toast } from '@/components/ui';
import { Parts } from '@/components/writing/DiffView';
import { call, client } from '@/lib/api';
import { formatRelative, plural } from '@/lib/format';
import { loadForAccount, useAccount } from '@/lib/query';
import { categoryLabel } from '@/lib/result';

type Search = { pattern?: string; ahead?: boolean };

export const Route = createFileRoute('/_app/improve/drill')({
  validateSearch: (s: Record<string, unknown>): Search => ({
    pattern: typeof s.pattern === 'string' && s.pattern ? s.pattern : undefined,
    ahead: s.ahead === true || s.ahead === 'true' ? true : undefined,
  }),
  loaderDeps: ({ search }) => search,
  loader: ({ context, deps }) => loadForAccount(context.queryClient, () => context.queryClient.ensureQueryData(dueQuery(deps))),
  component: GatedDrill,
});

/** A guest gets the sign-up gate instead of a page that would answer 403. */
function GatedDrill() {
  const search = Route.useSearch();
  // a new pattern or "ahead" starts a fresh session
  return useAccount() ? <Drill key={JSON.stringify(search)} /> : <AccountGate what="review" inline />;
}

const NOT_YET = 1;
const GOT_IT = 4;
const days = (n: number) => (n === 1 ? 'tomorrow' : n < 30 ? `in ${n} days` : `in ${Math.round(n / 30)} mo`);
const ASK = { spelling: 'Type the word, spelt correctly.', mistake: 'Rewrite it correctly.', fix: 'How would you improve this sentence?', vocab: 'Give a better word or phrase.' };

type Done = { card: DrillCard; got: boolean };

function Drill() {
  const search = Route.useSearch();
  const qc = useQueryClient();
  const { data } = useSuspenseQuery(dueQuery(search));
  // The session: up to SESSION cards, plus every "Not yet" re-queued at the end (a learning step). Only "Keep going" refills it.
  const [queue, setQueue] = useState(() => data.cards.slice(0, SESSION));
  const [i, setI] = useState(0);
  const [done, setDone] = useState<Done[]>([]);
  const [ended, setEnded] = useState(false);
  const card = ended ? undefined : queue[i];
  const finished = !card && done.length > 0;
  useEffect(() => {
    if (finished) void qc.invalidateQueries({ queryKey: ['cards', 'due'] }); // the nav badge and Today count catch up
  }, [finished, qc]);

  const keepGoing = () => {
    setQueue(data.cards.filter((c) => !done.some((d) => d.card.id === c.id && (d.got || search.ahead))).slice(0, SESSION));
    setI(0);
    setDone([]);
    setEnded(false);
  };

  // ahead of schedule every card counts, so "more" is what this visit has not drilled yet
  const more = search.ahead ? data.cards.filter((c) => !done.some((d) => d.card.id === c.id)).length : data.total;
  if (finished) return <Summary done={done} more={more} ahead={!!search.ahead} onMore={keepGoing} />;
  if (!card) return <Nothing pattern={search.pattern} ahead={!!search.ahead} deck={data.deck} />;
  return (
    <div className="max-w-[720px]">
      <div className="mb-3 flex items-center gap-3">
        {search.pattern && (
          <Link to="/improve/drill" search={{}} className="type-caption inline-flex shrink-0 items-center gap-1 rounded-sm text-ink hover:text-accent-text" aria-label={`Drilling ${categoryLabel(search.pattern)}. Show all patterns`}>
            {categoryLabel(search.pattern)}
            <X className="size-3.5" aria-hidden />
          </Link>
        )}
        <ProgressBar value={i / queue.length} label="Session progress" className="h-1.5 flex-1" />
        <span className="type-num type-caption" aria-hidden>
          {i} / {queue.length}
        </span>
        {done.length > 0 && (
          <Button variant="ghost" size="sm" className="-mr-3 text-muted" onClick={() => setEnded(true)}>
            End session
          </Button>
        )}
      </div>
      <CardView
        key={`${card.id}-${i}`}
        card={card}
        onGraded={(got, updated) => {
          setDone((d) => [...d, { card, got }]);
          if (!got) setQueue((q) => [...q, { ...card, ...updated }]);
          setI((n) => n + 1);
        }}
        onSkipped={() => setQueue((q) => q.filter((_, k) => k !== i))}
      />
      <p className="type-caption mt-4 max-w-[65ch]">Type your fix, or think it through and show the answer. Be honest: Not yet brings the card back before you finish, and tomorrow.</p>
    </div>
  );
}

function CardView({ card, onGraded, onSkipped }: { card: DrillCard; onGraded: (got: boolean, updated: DrillCard) => void; onSkipped: () => void }) {
  const [typed, setTyped] = useState('');
  const [checked, setChecked] = useState(false);
  const { label, text } = promptOf(card);
  const { answer, note } = answerOf(card.back);
  const spelling = card.category === 'spelling';
  const kind = spelling ? 'spelling' : card.source;
  const right = checked && typed.trim() !== '' && sameAnswer(typed, answer);
  const tried = checked && typed.trim() !== '';
  const word = spelling && card.front.startsWith('🎧') ? answer : null;
  // the word in the test speaker's voice, cut from the recording; the device voice when the word can't be located
  const clip = useQuery({ queryKey: ['card-clip', card.id], queryFn: () => call(client.GET('/api/lr/cards/{id}/clip', { params: { path: { id: card.id } } })), enabled: !!word && !!card.attemptId, retry: false, staleTime: 50 * 60_000 });
  const suggested = useRef<HTMLButtonElement>(null);
  const gotIn = days(review({ ...card, due: new Date(card.due) }, GOT_IT).interval);

  const grade = useMutation({
    mutationFn: (g: number) => call(client.POST('/api/cards/{id}/review', { params: { path: { id: card.id } }, body: { grade: g } })),
    onSuccess: (updated, g) => onGraded(g === GOT_IT, updated),
    onError: (e) => toast(`Couldn't save that: ${e.message}`, { tone: 'bad' }),
  });
  const skip = useMutation({
    mutationFn: () => call(client.POST('/api/cards/{id}/skip', { params: { path: { id: card.id } } })),
    onSuccess: () => {
      toast("Skipped. That card won't come back.");
      onSkipped();
    },
    onError: (e) => toast(`Couldn't skip it: ${e.message}`, { tone: 'bad' }),
  });

  useEffect(() => {
    if (tried) suggested.current?.focus(); // Enter confirms the verdict the comparison suggests
  }, [tried]);
  useEffect(() => {
    if (!checked) return;
    const onKey = (e: KeyboardEvent) => {
      if (grade.isPending || e.metaKey || e.ctrlKey || e.altKey || e.target instanceof HTMLInputElement || e.target instanceof HTMLTextAreaElement) return;
      if (e.key === '1') grade.mutate(NOT_YET);
      if (e.key === '2') grade.mutate(GOT_IT);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [checked, grade]);

  const check = (e?: { preventDefault(): void }) => {
    e?.preventDefault();
    setChecked(true);
  };
  const origin = card.origin && `${card.origin.skill === 'writing' ? 'Writing Task' : 'Speaking Part'} ${card.origin.part}, ${formatRelative(card.origin.at)}`;
  const field = {
    value: typed,
    onChange: (e: { target: { value: string } }) => setTyped(e.target.value),
    disabled: checked,
    autoComplete: 'off',
    autoCapitalize: 'off',
    spellCheck: false, // the browser must not fix the spelling for them
    // a phone keyboard would cover the card; on a laptop typing is the point
    autoFocus: typeof matchMedia !== 'undefined' && matchMedia('(pointer: fine)').matches,
  };

  return (
    <Card className="flex min-h-[24rem] flex-col overflow-clip" padded={false}>
      <div className="page-enter flex flex-1 flex-col">
        <div className="flex items-center justify-between gap-3 px-5 pt-3 sm:px-8 sm:pt-4">
          <p className="type-caption flex min-w-0 items-center gap-2">
            <Layers className="size-4 shrink-0" aria-hidden />
            <span className="truncate">
              <span className="font-medium text-ink">{card.category ? categoryLabel(card.category) : card.source === 'fix' ? 'Fix from your results' : 'Vocabulary'}</span>
              {origin && <span> · {origin}</span>}
            </span>
          </p>
          <Button variant="ghost" size="sm" className="-mr-3 shrink-0 text-muted" loading={skip.isPending} onClick={() => skip.mutate()} aria-label="Skip this card for good">
            Skip
          </Button>
        </div>
        <form id={`drill-${card.id}`} onSubmit={check} className="flex flex-1 flex-col justify-center gap-5 px-5 py-6 sm:px-8">
          <div>
            <p className="type-caption mb-3">{ASK[kind]}</p>
            {label && <p className="mb-2 text-sm font-medium text-accent-text">{label}</p>}
            <p className={clsx('max-w-[52ch] text-pretty whitespace-pre-line', text.length > 90 ? 'type-reading' : 'type-title-sm')}>{text}</p>
            {word && (
              <Button type="button" variant="outline" className="mt-4 w-fit" icon={<Volume2 aria-hidden />} onClick={() => void hear(word, clip.data)}>
                Hear the word
              </Button>
            )}
          </div>
          {spelling ? (
            <Input label="Your spelling" {...field} className="max-w-sm" />
          ) : (
            <Textarea
              label="Your correction"
              hint={checked ? undefined : 'Optional. Enter to check, Shift+Enter for a new line.'}
              rows={2}
              {...field}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) check(e);
              }}
            />
          )}
          <div aria-live="polite">
            {checked && (
              <div className="space-y-3 border-t border-line pt-5 motion-safe:animate-[rise-in_320ms_var(--ease-out-expo)]">
                {right ? (
                  <p className="type-body flex items-center gap-2 font-medium text-good-text">
                    <Check className="size-4" aria-hidden />
                    That's it.
                  </p>
                ) : tried ? (
                  <div>
                    <p className="type-caption mb-1">Your version, with the changes it needs</p>
                    <p className="type-reading max-w-[60ch]">
                      <Parts parts={diffWords(typed.trim(), answer)} />
                    </p>
                  </div>
                ) : null}
                <div>
                  <p className="type-caption mb-1">Correction</p>
                  <p className="type-reading max-w-[60ch] font-medium text-pretty text-ink">{answer}</p>
                </div>
                {note && <p className="type-body max-w-[65ch] text-pretty whitespace-pre-line text-muted">{note}</p>}
              </div>
            )}
          </div>
        </form>
      </div>
      <div className="border-t border-line bg-surface-2 p-3 sm:p-4">
        {!checked ? (
          <Button type="submit" form={`drill-${card.id}`} size="lg" className="w-full">
            {typed.trim() ? 'Check' : 'Show answer'} <Kbd onBrand className="ml-1 hidden sm:inline-flex">Enter</Kbd>
          </Button>
        ) : (
          <div className="grid grid-cols-2 gap-2" role="group" aria-label="Did you get it?">
            {[
              { g: NOT_YET, label: 'Not yet', sub: 'again this session', key: '1', tone: 'text-bad-text', pick: tried && !right },
              { g: GOT_IT, label: 'Got it', sub: gotIn, key: '2', tone: 'text-good-text', pick: right },
            ].map((b) => (
              <Button
                key={b.g}
                ref={b.pick ? suggested : undefined}
                variant="outline"
                disabled={grade.isPending}
                onClick={() => grade.mutate(b.g)}
                className="h-auto min-h-14 min-w-0 flex-col gap-0.5 px-2 py-2 md:h-auto"
              >
                <span className={clsx('text-sm font-semibold', b.tone)}>{b.label}</span>
                <span className="type-num flex items-center gap-1.5 text-xs font-normal text-muted">
                  {b.sub}
                  <Kbd className="hidden sm:inline-flex" aria-hidden>
                    {b.key}
                  </Kbd>
                </span>
              </Button>
            ))}
          </div>
        )}
      </div>
    </Card>
  );
}

/** End of a session: how it went per pattern, then keep going or back to the plan. */
function Summary({ done, more, ahead, onMore }: { done: Done[]; more: number; ahead: boolean; onMore: () => void }) {
  const firstTry = new Map<string, Done>(); // a card's first verdict counts; its re-runs are practice
  for (const d of done) if (!firstTry.has(d.card.id)) firstTry.set(d.card.id, d);
  const got = [...firstTry.values()].filter((d) => d.got).length;
  const byPattern = new Map<string, { n: number; got: number }>();
  for (const d of firstTry.values()) {
    const k = d.card.category ? categoryLabel(d.card.category) : 'Other';
    const v = byPattern.get(k) ?? { n: 0, got: 0 };
    byPattern.set(k, { n: v.n + 1, got: v.got + (d.got ? 1 : 0) });
  }
  return (
    <div className="max-w-[720px]">
      <EmptyState
        icon={<CircleCheck />}
        title="Session done"
        action={
          <div className="flex flex-wrap gap-2">
            {more > 0 && (
              <Button onClick={onMore}>
                Keep going · {ahead ? `${more} more` : `${plural(more, 'card')} due`}
              </Button>
            )}
            <Link to="/improve" className={buttonStyles({ variant: more > 0 ? 'outline' : 'primary' })}>
              Back to your plan
            </Link>
          </div>
        }
      >
        {`You knew ${got} of ${plural(firstTry.size, 'card')} first time. Each one comes back just before you would forget it.`}
      </EmptyState>
      <ul className="mt-6 max-w-md divide-y divide-line border-y border-line">
        {[...byPattern].map(([k, v]) => (
          <li key={k} className="type-body flex justify-between gap-4 py-2.5">
            <span className="min-w-0 truncate">{k}</span>
            <span className="type-num shrink-0 text-muted">
              {v.got} / {v.n} first time
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}

/** Nothing to drill: due-but-empty offers the same cards ahead of schedule; an empty deck says what fills it. */
function Nothing({ pattern, ahead, deck }: { pattern?: string; ahead: boolean; deck: number }) {
  if (!deck)
    return (
      <EmptyState
        icon={<Layers />}
        title="No drill cards yet"
        action={
          <div className="flex flex-wrap gap-2">
            <Link to="/writing" className={buttonStyles()}>
              Write an essay
            </Link>
            <Link to="/speaking" className={buttonStyles({ variant: 'outline' })}>
              Practise speaking
            </Link>
          </div>
        }
      >
        After each Speaking or Writing test, the mistakes worth practising become drill cards here. A few minutes a day is enough.
      </EmptyState>
    );
  const name = pattern && categoryLabel(pattern).toLowerCase();
  return (
    <EmptyState
      icon={<CircleCheck />}
      title={ahead ? `No ${name ?? ''} cards to drill`.replace('  ', ' ') : `Nothing ${name ? `in ${name} ` : ''}due right now`}
      action={
        <div className="flex flex-wrap gap-2">
          {!ahead && (
            <Link to="/improve/drill" search={{ pattern, ahead: true }} className={buttonStyles()}>
              Drill anyway
            </Link>
          )}
          <Link to="/improve/patterns" search={{}} className={buttonStyles({ variant: ahead ? 'primary' : 'outline' })}>
            See your patterns
          </Link>
        </div>
      }
    >
      {ahead ? 'Every card in this pattern was skipped.' : 'Each card comes back just before you would forget it. You can drill ahead of schedule; it counts as a normal review.'}
    </EmptyState>
  );
}

/** Plays the word: its clip from the test recording when there is one, else the device's British English voice (a little slow). */
async function hear(word: string, clip?: { url: string; from: number; to: number }) {
  if (clip) {
    try {
      const a = new Audio(clip.url);
      a.currentTime = clip.from;
      await a.play();
      setTimeout(() => a.pause(), (clip.to - clip.from) * 1000);
      return;
    } catch {
      // the recording would not load or play: try the device voice
    }
  }
  const voices = 'speechSynthesis' in window ? speechSynthesis.getVoices() : [];
  // Chrome on Linux (and some Android builds) has the API but no voice installed: speak() then plays nothing, silently
  if (!voices.length) {
    toast("This device has no English voice to read the word. Use the letters shown, or open the test's review to replay the recording.", { tone: 'bad' });
    return;
  }
  const u = new SpeechSynthesisUtterance(word);
  u.lang = 'en-GB';
  u.rate = 0.85;
  u.voice = voices.find((v) => v.lang === 'en-GB') ?? voices.find((v) => v.lang.startsWith('en')) ?? null;
  speechSynthesis.cancel();
  setTimeout(() => speechSynthesis.speak(u), 50); // Chrome drops an utterance queued in the same tick as cancel()
}
