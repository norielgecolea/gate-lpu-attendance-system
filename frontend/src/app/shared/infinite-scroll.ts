import { signal } from '@angular/core';

/**
 * Reusable in-memory infinite scroll: reveals rows in chunks as the
 * scroll container nears its bottom. Keeps a single reveal window and
 * the near-bottom detection in one place so tables don't repeat it.
 */
export function infiniteScroll(chunk = 20) {
  const visible = signal(chunk);

  return {
    visible,
    /** Slice the full row list down to the current reveal window. */
    take: <T>(rows: readonly T[]): T[] => rows.slice(0, visible()),
    /** Reset the window, e.g. when filters change the result set. */
    reset: (): void => visible.set(chunk),
    /** Grow the window when the container is scrolled near its bottom. */
    onScroll: (event: Event, total: number): void => {
      const el = event.target as HTMLElement;
      if (el.scrollTop + el.clientHeight >= el.scrollHeight - 200 && visible() < total) {
        visible.update((v) => Math.min(v + chunk, total));
      }
    },
  };
}
