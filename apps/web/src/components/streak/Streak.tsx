import { Tooltip as Tip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/shadcn/tooltip';
import { useQuery } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { ArrowRight, Award, Check, Flame, Share2, Snowflake } from 'lucide-react';
import { type CSSProperties, useEffect, useRef, useState } from 'react';
import { Badge, Button, buttonStyles, Card, ProgressBar, Sheet, toast } from '@/components/ui';
import { isAccount, progressQuery, useMe } from '@/lib/query';
import { plural } from '@/lib/format';
import { addDays, atRisk, bandJourney, celebrationLine, claimCelebration, milestoneProgress, shareText, streakQuery, type Streak } from '@/lib/streak';
import { cn } from '@/lib/utils';

const reduced = () => typeof matchMedia !== 'undefined' && matchMedia('(prefers-reduced-motion: reduce)').matches;

/** The streak for whoever is browsing (account or guest); nothing for a visitor with no session. */
export function useStreak() {
  const me = useMe().data;
  return useQuery({ ...streakQuery, enabled: !!me }).data;
}

export const flameLabel = (s: Pick<Streak, 'current' | 'today'>) => `${s.current} day streak, ${s.today === 'done' ? 'practised today' : 'not practised yet today'}`;

const DOW = ['M', 'T', 'W', 'T', 'F', 'S', 'S'];
const STATE_LABEL = { done: 'practised', frozen: 'freeze used', missed: 'missed', today: 'today, not practised yet', future: 'still to come' } as const;
const dayName = (day: string) => new Date(`${day}T12:00`).toLocaleDateString('en', { weekday: 'long' });

/** Mon–Sun: done ● filled, frozen ❄, missed faint, today hollow ring, future dotted. */
export function WeekStrip({ week, settle, className }: { week: Streak['week']; /** a day whose dot just filled: it settles in */ settle?: string; className?: string }) {
  return (
    <ol className={cn('grid grid-cols-7 gap-1 text-center', className)}>
      {week.map((d, i) => (
        <li key={d.day} className="flex flex-col items-center gap-1.5">
          <span className="text-caption text-muted" aria-hidden>
            {DOW[i]}
          </span>
          <span
            role="img"
            aria-label={`${dayName(d.day)}: ${STATE_LABEL[d.state]}`}
            className={cn(
              'grid size-8 place-items-center rounded-full transition-colors duration-200',
              d.state === 'done' && 'bg-brand text-brand-ink',
              d.state === 'done' && d.day === settle && 'streak-settle',
              d.state === 'frozen' && 'bg-sky-soft text-sky-text',
              d.state === 'missed' && 'bg-surface-2',
              d.state === 'today' && 'border-2 border-brand',
              d.state === 'future' && 'border border-dashed border-line-strong',
            )}
          >
            {d.state === 'done' && <Check className="size-4" strokeWidth={3} aria-hidden />}
            {d.state === 'frozen' && <Snowflake className="size-4" aria-hidden />}
          </span>
        </li>
      ))}
    </ol>
  );
}

// Visual rules (§3.4): brand tints by fill strength, so the levels read without hue; warn stays on the flame and count only.
// Level 0 lifts to a line-strong wash in dark mode, where surface-2 all but vanishes against the card.
const HEAT = ['bg-surface-2 dark:bg-line-strong/30', 'bg-brand/30', 'bg-brand/60', 'bg-brand'];
const HEAT_LABEL = ['no practice', 'drills', 'a test', 'a test and drills'];
const ROW_LABEL = ['Mon', '', 'Wed', '', 'Fri', '', ''];
const CELL = 'aspect-square w-full rounded-[22%]';

/**
 * GitHub-style heatmap: weeks as columns (oldest left), Monday-Sunday rows, ending with this week. Freeze days are ice with ❄,
 * today is outlined until it counts, days after today are left empty. Each cell carries its own label.
 * Cells are `--cell` wide, or with `fluid` they share the width, so the whole range fits without a sideways scroll.
 */
export function Heatmap({ days, today, weeks, monthLabels, fluid, className }: { days: Streak['days']; today: string; weeks: number; monthLabels?: boolean; fluid?: boolean; className?: string }) {
  const byDay = new Map(days.map((d) => [d.day, d]));
  const monday = addDays(today, -((new Date(`${today}T00:00:00Z`).getUTCDay() + 6) % 7));
  const start = addDays(monday, -7 * (weeks - 1));
  const cols = Array.from({ length: weeks }, (_, w) => Array.from({ length: 7 }, (_, r) => addDays(start, w * 7 + r)));
  const short = (day: string, o: Intl.DateTimeFormatOptions) => new Date(`${day}T12:00`).toLocaleDateString('en', o);
  return (
    <TooltipProvider delayDuration={0} skipDelayDuration={300}>
    <div
      className={cn('grid grid-flow-col gap-(--gap) text-micro leading-none text-muted [--cell:1rem] [--gap:3px]', className)}
      style={{ gridTemplateRows: `${monthLabels ? '1rem ' : ''}repeat(7, auto)`, gridTemplateColumns: `auto repeat(${weeks}, ${fluid ? 'minmax(0, 1fr)' : 'var(--cell)'})` }}
    >
      {monthLabels && <span aria-hidden />}
      {ROW_LABEL.map((l, i) => (
        <span key={i} className="flex h-0 items-center self-center pr-1.5" aria-hidden>
          {l}
        </span>
      ))}
      {cols.map((col, w) => {
        const first = col.find((d) => d.endsWith('-01')) ?? (w === 0 ? col[0] : undefined);
        return [
          monthLabels && (
            <span key={`m${col[0]}`} className="relative" aria-hidden>
              <span className="absolute top-0 left-0 whitespace-nowrap">{first && (w > 0 || Number(first.slice(8)) < 22) ? short(first, { month: 'short' }) : ''}</span>
            </span>
          ),
          ...col.map((day) => {
            const d = byDay.get(day);
            if (!d || day > today) return <span key={day} className={CELL} aria-hidden />;
            const pending = day === today && !d.level;
            const what = d.level ? HEAT_LABEL[d.level] : d.frozen ? 'freeze used' : pending ? 'today, not practised yet' : HEAT_LABEL[0];
            const label = `${short(day, { weekday: 'short', month: 'short', day: 'numeric' })}: ${what}`;
            return (
              <Tip key={day}>
                <TooltipTrigger asChild>
                  <span
                    role="img"
                    aria-label={label}
                    className={cn(CELL, 'grid place-items-center', !d.level && d.frozen ? 'bg-sky-soft text-sky-text' : HEAT[d.level], pending && 'ring-2 ring-brand ring-inset')}
                  >
                    {!d.level && d.frozen && <Snowflake className="size-[72%]" strokeWidth={2.5} aria-hidden />}
                  </span>
                </TooltipTrigger>
                <TooltipContent side="top">{label}</TooltipContent>
              </Tip>
            );
          }),
        ];
      })}
    </div>
    </TooltipProvider>
  );
}

/** Less ▢▢▢▢ More · ❄ Freeze · ▢ Today. */
export function HeatLegend({ className }: { className?: string }) {
  const swatch = 'size-3.5 rounded-[3px]';
  return (
    <p className={cn('flex flex-wrap items-center gap-x-1.5 gap-y-2 text-caption text-muted', className)} aria-hidden>
      Less
      {HEAT.map((c) => (
        <span key={c} className={cn(swatch, c)} />
      ))}
      <span className="mr-3">More</span>
      <span className={cn(swatch, 'grid place-items-center bg-sky-soft text-sky-text')}>
        <Snowflake className="size-2.5" strokeWidth={2.5} />
      </span>
      <span className="mr-3">Freeze</span>
      <span className={cn(swatch, HEAT[0], 'ring-2 ring-brand ring-inset')} />
      Today
    </p>
  );
}

/**
 * Overview home card (§3.4): count, the 35-day line and a 5-week heatmap read as one unit (heatmap right beside the words, not pushed to
 * the far edge). The whole card opens /streak. At risk the line becomes the midnight warning with a Drill button: under the words on
 * wider screens, full width under both on a phone.
 */
export function StreakCard() {
  const s = useStreak();
  if (!s) return null;
  const risky = atRisk(s);
  return (
    <Card interactive className="relative grid gap-6 sm:grid-cols-[minmax(0,18rem)_minmax(0,1fr)] sm:items-center sm:gap-10 sm:px-8 sm:py-6">
      <div className="space-y-3">
        <h2 className="flex items-center gap-2">
          <Flame className={cn('size-7 shrink-0', s.today === 'done' ? 'fill-warn text-warn' : 'text-muted')} aria-hidden />
          <Link
            to="/streak"
            aria-label={`Streak: ${flameLabel(s)}`}
            className="flex items-baseline gap-2 after:absolute after:inset-0 after:rounded-xl focus-visible:outline-none focus-visible:after:outline-2 focus-visible:after:outline-offset-2 focus-visible:after:outline-ring"
          >
            <span className="font-serif text-4xl leading-none font-medium text-warn-text tabular-nums">{s.current}</span>
            <span className="text-base font-semibold whitespace-nowrap text-ink">day streak</span>
          </Link>
        </h2>
        {risky ? (
          <p className="type-body font-medium text-pretty text-ink">Ends at midnight: a 2-minute drill saves it</p>
        ) : (
          <p className="type-body text-pretty text-muted">
            You practised <span className="font-medium text-ink">{s.practicedLast35} of the last 35 days</span>
          </p>
        )}
        <p className="flex flex-wrap items-center gap-x-3 gap-y-1 text-caption text-muted">
          <span>Longest {plural(s.longest, 'day')}</span>
          <span className="flex items-center gap-1">
            <Snowflake className="size-3.5 text-sky-text" aria-hidden />
            {plural(s.freezes, 'freeze')} saved
          </span>
        </p>
        {s.nextMilestone != null && (
          <div className="max-w-60 space-y-1.5 pt-1">
            <p className="text-caption text-muted">
              <span className="font-semibold text-ink tabular-nums">{s.nextMilestone - s.current}</span> {s.nextMilestone - s.current === 1 ? 'day' : 'days'} to the {s.nextMilestone}-day milestone
            </p>
            <ProgressBar value={milestoneProgress(s) ?? 1} label={`Progress to the ${s.nextMilestone}-day milestone`} className="h-1.5" />
          </div>
        )}
        {risky && (
          <Link to="/improve/drill" search={{}} className={buttonStyles({ className: 'relative z-10 mt-2 max-sm:w-full' })}>
            Drill now <ArrowRight aria-hidden />
          </Link>
        )}
      </div>
      {/* Twelve weeks that fill the right side; cells grow with the space (about 24px on a phone, 28px on desktop). */}
      <Heatmap days={s.days} today={s.date} weeks={12} monthLabels fluid className="relative z-10 w-full max-w-[26rem] [--gap:4px] sm:justify-self-end" />
    </Card>
  );
}

const FLAME = 'M8.5 14.5A2.5 2.5 0 0 0 11 12c0-1.38-.5-2-1-3-1.072-2.143-.224-4.054 2-6 .5 2.5 2 4.9 4 6.5 2 1.6 3 3.5 3 5.5a7 7 0 1 1-14 0c0-1.153.433-2.294 1-3a2.5 2.5 0 0 0 2.5 2.5z';

/** Draws the 1080² share card: wordmark, flame + count, this week's dots, the band journey when it rose, the domain. Brand teal, no server. */
async function drawShareCard(c: HTMLCanvasElement, n: number, text: string, week?: Streak['week']) {
  await document.fonts?.ready;
  c.width = c.height = 1080;
  const g = c.getContext('2d')!;
  const serif = '"Newsreader Variable", Georgia, serif';
  const sans = '"Hanken Grotesk Variable", system-ui, sans-serif';
  g.fillStyle = '#0f766e';
  g.fillRect(0, 0, 1080, 1080);
  g.textBaseline = 'alphabetic';
  g.textAlign = 'left';
  g.fillStyle = '#ddf3ef';
  g.font = `600 40px ${sans}`;
  g.fillText('NextBand', 80, 120);
  g.textAlign = 'center';
  // lucide Flame, 24-unit path scaled to 168 px
  g.save();
  g.translate(540 - 84, 168);
  g.scale(7, 7);
  g.fillStyle = '#fbbf24';
  g.fill(new Path2D(FLAME));
  g.restore();
  g.fillStyle = '#ffffff';
  g.font = `500 260px ${serif}`;
  g.fillText(String(n), 540, 590);
  g.font = `600 56px ${sans}`;
  g.fillText('day streak', 540, 680);
  // this week, Monday to Sunday: practised days filled, a freeze ringed in ice, the rest faint
  if (week) {
    const r = 22;
    const gap = 30;
    const x0 = 540 - ((week.length - 1) * (2 * r + gap)) / 2;
    week.forEach((d, i) => {
      const x = x0 + i * (2 * r + gap);
      g.beginPath();
      g.arc(x, 770, r, 0, Math.PI * 2);
      if (d.state === 'done') {
        g.fillStyle = '#ffffff';
        g.fill();
      } else if (d.state === 'frozen') {
        g.lineWidth = 6;
        g.strokeStyle = '#bae6fd';
        g.stroke();
      } else {
        g.fillStyle = 'rgba(255, 255, 255, 0.18)';
        g.fill();
      }
    });
  }
  const band = text.split(' · ')[1];
  if (band) {
    g.font = `600 44px ${sans}`;
    const w = g.measureText(band).width + 64;
    g.fillStyle = 'rgba(255, 255, 255, 0.12)';
    g.beginPath();
    g.roundRect(540 - w / 2, 838, w, 76, 16);
    g.fill();
    g.fillStyle = '#ffffff';
    g.fillText(band, 540, 891);
  }
  g.fillStyle = '#ddf3ef';
  g.font = `500 36px ${sans}`;
  g.fillText('nextband.app', 540, 1010);
}

/** PNG share card: shares the file where the browser can, else downloads it. */
async function shareCard(n: number, text: string, week?: Streak['week']) {
  const c = document.createElement('canvas');
  await drawShareCard(c, n, text, week);
  const blob = await new Promise<Blob | null>((r) => c.toBlob(r, 'image/png'));
  if (!blob) throw new Error('canvas');
  const file = new File([blob], `nextband-${n}-day-streak.png`, { type: 'image/png' });
  if (navigator.canShare?.({ files: [file] })) {
    try {
      await navigator.share({ files: [file], text: `${text} · nextband.app` });
    } catch (e) {
      if ((e as Error).name !== 'AbortError') throw e;
    }
    return;
  }
  const a = Object.assign(document.createElement('a'), { href: URL.createObjectURL(blob), download: file.name });
  a.click();
  setTimeout(() => URL.revokeObjectURL(a.href), 1000);
}

export function ShareButton({ n, week }: { n: number; week?: Streak['week'] }) {
  const account = isAccount(useMe().data);
  const trend = useQuery({ ...progressQuery, enabled: account }).data?.trend;
  const [busy, setBusy] = useState(false);
  const share = () => {
    setBusy(true);
    shareCard(n, shareText(n, bandJourney(trend)), week)
      .catch(() => toast('Could not make the image. Try again.', { tone: 'bad' }))
      .finally(() => setBusy(false));
  };
  return (
    <Button variant="outline" icon={<Share2 />} loading={busy} onClick={share}>
      Share
    </Button>
  );
}

const SPARKS = Array.from({ length: 10 }, (_, i) => i);

/**
 * Big flame with the count; `from` → the count steps up once after a beat while the flame lights with a warm bloom behind it (instant
 * with reduced motion). `sparks` adds a quiet ring of brand and warm sparks for a milestone.
 */
export function BigFlame({ count, from, size = 'lg', sparks }: { count: number; from?: number; size?: 'lg' | 'xl'; sparks?: boolean }) {
  const [n, setN] = useState(from ?? count);
  useEffect(() => {
    if (from == null || reduced()) return setN(count);
    const t = setTimeout(() => setN(count), 450);
    return () => clearTimeout(t);
  }, [count, from]);
  const lit = from == null || n === count;
  const lighting = from != null && n === count;
  return (
    <div className="relative mx-auto flex w-fit items-center justify-center gap-3" aria-hidden>
      {lighting && <span className="streak-glow" />}
      {lighting &&
        sparks &&
        SPARKS.map((i) => <span key={i} className={cn('streak-spark', i % 2 ? 'bg-brand' : 'bg-warn')} style={{ '--a': `${i * 18 - 81}deg`, '--d': `${(i % 3) * 60}ms` } as CSSProperties} />)}
      <Flame className={cn('relative', size === 'xl' ? 'size-16' : 'size-12', 'transition-colors duration-300', lit ? 'fill-warn text-warn' : 'text-muted')} />
      <span key={n} className={cn('relative font-serif leading-none font-medium text-warn-text tabular-nums', size === 'xl' ? 'text-7xl' : 'text-6xl', lighting && 'streak-pop')}>
        {n}
      </span>
    </div>
  );
}

/**
 * Daily celebration (§3.4). Mounted once in the _app layout so it survives exam → result navigation: when a refetched streak goes
 * from today 'pending' to 'done', it opens once per local day. Held back while an exam route is on screen.
 */
export function StreakCelebration({ hidden }: { hidden: boolean }) {
  const s = useStreak();
  const prev = useRef<Streak | undefined>(undefined);
  const [shown, setShown] = useState<Streak | null>(null);
  useEffect(() => {
    const before = prev.current;
    prev.current = s;
    if (s && before?.today === 'pending' && s.today === 'done' && claimCelebration(s.date)) setShown(s);
  }, [s]);
  // Today's square starts hollow and fills with the count.
  const [filled, setFilled] = useState(false);
  const open = !!shown && !hidden;
  useEffect(() => {
    if (!open) return setFilled(false);
    if (reduced()) return setFilled(true);
    const t = setTimeout(() => setFilled(true), 450);
    return () => clearTimeout(t);
  }, [open]);
  if (!shown) return null;
  const milestone = shown.justReached != null && shown.justReached === shown.current;
  const week = shown.week.map((d) => (d.day === shown.date && !filled ? { ...d, state: 'today' as const } : d));
  return (
    <Sheet
      open={open}
      onClose={() => setShown(null)}
      title={milestone ? `${shown.current}-day milestone` : 'Practice day done'}
      description={celebrationLine(shown.current)}
      footer={
        <>
          {milestone && <ShareButton n={shown.current} week={shown.week} />}
          <Button onClick={() => setShown(null)}>Continue</Button>
        </>
      }
    >
      <div className="space-y-6 pt-6 pb-2">
        <p className="sr-only" role="status">
          {flameLabel(shown)}
        </p>
        <BigFlame count={shown.current} from={Math.max(0, shown.current - 1)} size={milestone ? 'xl' : 'lg'} sparks={milestone} />
        {milestone && (
          <Badge tone="accent" className="mx-auto flex w-fit">
            <Award aria-hidden />
            {shown.current} days in a row
          </Badge>
        )}
        <WeekStrip week={week} settle={filled ? shown.date : undefined} className="mx-auto max-w-sm" />
        {shown.nextMilestone != null && (
          <p className="text-center text-caption text-muted">
            Next milestone: {shown.nextMilestone} days, {plural(shown.nextMilestone - shown.current, 'day')} to go
          </p>
        )}
      </div>
    </Sheet>
  );
}
