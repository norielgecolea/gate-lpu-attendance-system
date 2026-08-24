/** Max gap between keystrokes that still counts as one RFID wedge burst. */
export const SCAN_BURST_GAP_MS = 120;

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
