import { describe, expect, it } from 'vitest';
import { SCAN_BURST_GAP_MS, applyScanInput } from './wedge-scan-buffer';

describe('applyScanInput', () => {
  it('drops a stale prefix when a later scan arrives as one event', () => {
    expect(applyScanInput('a', 'a12345678', SCAN_BURST_GAP_MS + 500)).toBe('12345678');
  });

  it('keeps appending while characters arrive within the burst gap', () => {
    expect(applyScanInput('12', '123', 20)).toBe('123');
  });

  it('keeps the full next value when the gap equals the threshold', () => {
    expect(applyScanInput('12', '123', SCAN_BURST_GAP_MS)).toBe('123');
  });

  it('keeps appending when lag stretches the burst under the gap', () => {
    expect(applyScanInput('12', '123', 1500)).toBe('123');
  });

  it('starts a new scan on the first character after a pause', () => {
    expect(applyScanInput('a', 'a1', SCAN_BURST_GAP_MS + 100)).toBe('1');
  });

  it('keeps a first character when the previous buffer is empty', () => {
    expect(applyScanInput('', 'a', 9999)).toBe('a');
  });

  it('keeps a replacement that does not share the previous prefix', () => {
    expect(applyScanInput('a', '12345678', SCAN_BURST_GAP_MS + 500)).toBe('12345678');
  });
});
