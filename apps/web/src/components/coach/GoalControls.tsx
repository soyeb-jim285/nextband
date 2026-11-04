import { CalendarDays } from 'lucide-react';
import { useId, useState } from 'react';
import { Calendar } from '@/components/ui/shadcn/calendar';
import { useUpdateSettings } from '@/components/settings/useUpdateSettings';
import { Button, controlStyles, Popover, Select } from '@/components/ui';
import { formatDate } from '@/lib/format';
import { cn } from '@/lib/utils';
import { useMe } from '@/lib/query';

const BANDS = Array.from({ length: 11 }, (_, i) => 4 + i * 0.5); // same range as the settings slider and API
const today = () => new Date().toLocaleDateString('en-CA'); // local YYYY-MM-DD

/** Whole days from today to `date` (YYYY-MM-DD), or null when unset or past. */
export const daysUntil = (date: string | null | undefined) => {
  if (!date) return null;
  const d = Math.round((Date.parse(date) - Date.parse(today())) / 86_400_000);
  return d < 0 ? null : d;
};

/** "2026-12-05" as a local date (new Date("2026-12-05") would be UTC midnight, the day before west of Greenwich). */
const local = (ymd: string) => {
  const [y, m, d] = ymd.split('-').map(Number);
  return new Date(y!, m! - 1, d!);
};

/** Exam date picker (shadcn Calendar in a popover), shared by Improve and Settings. Past days are off; "Clear date" removes it. */
export function ExamDateInput({ className }: { className?: string }) {
  const value = useMe().data?.settings.examDate ?? '';
  const { mutate } = useUpdateSettings();
  const id = useId();
  const chosen = value ? local(value) : undefined;
  return (
    <div className={className}>
      <label htmlFor={id} className="mb-1.5 block text-sm leading-normal font-medium text-ink">
        Exam date
      </label>
      <Popover
        align="start"
        className="w-auto p-0"
        trigger={(p) => (
          <button {...p} id={id} type="button" className={cn(controlStyles, 'flex w-full min-w-40 items-center justify-between gap-2 text-left', !chosen && 'text-muted')}>
            <span className="type-num">{chosen ? formatDate(chosen) : 'Pick a date'}</span>
            <CalendarDays className="size-4 shrink-0 text-muted" aria-hidden />
          </button>
        )}
      >
        {(close) => (
          <div>
            <Calendar
              mode="single"
              selected={chosen}
              defaultMonth={chosen}
              startMonth={new Date()}
              disabled={{ before: local(today()) }}
              onSelect={(d) => {
                if (!d) return;
                mutate({ examDate: d.toLocaleDateString('en-CA') });
                close();
              }}
            />
            {chosen && (
              <div className="border-t border-line p-2">
                <Button variant="ghost" size="sm" className="w-full" onClick={() => (mutate({ examDate: null }), close())}>
                  Clear date
                </Button>
              </div>
            )}
          </div>
        )}
      </Popover>
    </div>
  );
}

/** Coach header controls: target band and exam date. `chosen` is the target the report used (null when the student never picked one). */
export function GoalControls({ chosen }: { chosen: number | null }) {
  const [target, setTarget] = useState(chosen);
  const { mutate } = useUpdateSettings();
  return (
    <div className="flex flex-wrap items-end gap-3 max-sm:grid max-sm:w-full max-sm:grid-cols-2">
      <Select
        label="Target band"
        value={target ?? ''}
        className="sm:w-32"
        onChange={(e) => {
          const v = Number(e.target.value);
          setTarget(v);
          mutate({ targetBand: v });
        }}
      >
        {target == null && (
          <option value="" disabled>
            Choose
          </option>
        )}
        {BANDS.map((b) => (
          <option key={b} value={b}>
            {b.toFixed(1)}
          </option>
        ))}
      </Select>
      <ExamDateInput className="sm:w-44" />
    </div>
  );
}
