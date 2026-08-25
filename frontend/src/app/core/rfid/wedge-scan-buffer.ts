/**
 * Max gap between keystrokes that still counts as one RFID wedge burst.
 * Wider than a typical reader interval so a lagging kiosk PC does not split one tap.
 * Accidental keys then a real tap still have a much larger pause, so the stale prefix is dropped.
 */
export const SCAN_BURST_GAP_MS = 400;

/**
 * Isolates a keyboard-wedge RFID burst from leftover keys.
 * A pause longer than {@link SCAN_BURST_GAP_MS} starts a new scan and drops the stale prefix.
 */
export function applyScanInput(
  previous: string,
  next: string,
  elapsedMs: number,
  gapMs = SCAN_BURST_GAP_MS,
): string {
  if (elapsedMs <= gapMs) {
    return next;
  }
  if (next.length <= previous.length || !next.startsWith(previous)) {
    return next;
  }
  return next.slice(previous.length);
}
