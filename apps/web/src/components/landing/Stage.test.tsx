import { act, render } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { past, Stage } from './Stage';

// A stub IntersectionObserver the test drives by hand: fire(targets, isIntersecting).
let observers: { cb: IntersectionObserverCallback; targets: Element[] }[] = [];
class IO {
  targets: Element[] = [];
  constructor(public cb: IntersectionObserverCallback) {
    observers.push(this);
  }
  observe(t: Element) {
    this.targets.push(t);
  }
  disconnect() {
    observers = observers.filter((o) => o !== this);
  }
  unobserve() {}
  takeRecords() {
    return [];
  }
}
const fire = (targets: Element[], on: boolean) =>
  act(() => {
    for (const o of observers) o.cb(targets.filter((t) => o.targets.includes(t)).map((target) => ({ target, isIntersecting: on }) as IntersectionObserverEntry), o as unknown as IntersectionObserver);
  });

const media = (reduce: boolean, narrow: boolean) =>
  vi.fn((q: string) => ({ matches: q.includes('no-preference') ? !reduce : q.includes('motion: reduce') ? reduce : narrow, addEventListener() {}, removeEventListener() {} }));

const stageOf = (c: HTMLElement) => c.querySelector('.lp-stage')!;

describe('Stage', () => {
  beforeEach(() => {
    observers = [];
    vi.stubGlobal('IntersectionObserver', IO);
  });
  afterEach(() => vi.unstubAllGlobals());

  it('steps with the number of sentinels scrolled past, clamped to the last step, and reverses', () => {
    vi.stubGlobal('matchMedia', media(false, false));
    const { container } = render(<Stage steps={4}>x</Stage>);
    const s = [...container.querySelectorAll('.lp-sentinel')];
    expect(s).toHaveLength(3);
    expect(stageOf(container).getAttribute('data-step')).toBe('0');
    fire(s.slice(0, 2), true);
    expect(stageOf(container).getAttribute('data-step')).toBe('2');
    expect(stageOf(container).getAttribute('data-past')).toBe('0 1 2');
    fire(s, true);
    expect(stageOf(container).getAttribute('data-step')).toBe('3');
    fire([s[2]!, s[1]!], false);
    expect(stageOf(container).getAttribute('data-step')).toBe('1');
  });

  it('shows the last step at once under reduced motion', () => {
    vi.stubGlobal('matchMedia', media(true, false));
    const { container } = render(<Stage steps={5}>x</Stage>);
    expect(stageOf(container).getAttribute('data-step')).toBe('4');
  });

  it('a flat phone track paints its last step first, or plays the steps on a timer when asked', () => {
    vi.useFakeTimers();
    vi.stubGlobal('matchMedia', media(false, true));
    const flat = render(<Stage steps={4} className="lp-track--mobile-flat">x</Stage>);
    expect(stageOf(flat.container).getAttribute('data-step')).toBe('3');
    flat.unmount();

    const play = render(
      <Stage steps={3} className="lp-track--mobile-flat" play>
        x
      </Stage>,
    );
    expect(stageOf(play.container).getAttribute('data-step')).toBe('0');
    fire([play.container.querySelector('.lp-track')!], true);
    act(() => vi.advanceTimersByTime(2000));
    expect(stageOf(play.container).getAttribute('data-step')).toBe('2');
    vi.useRealTimers();
  });

  it('past lists every step reached', () => {
    expect(past(0)).toBe('0');
    expect(past(3)).toBe('0 1 2 3');
  });
});
