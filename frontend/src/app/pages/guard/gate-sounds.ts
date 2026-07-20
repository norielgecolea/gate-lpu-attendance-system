/** Lightweight kiosk tones via Web Audio API (no asset files). */
export class GateSounds {
  private ctx: AudioContext | null = null;

  private ensureContext(): AudioContext | null {
    if (typeof window === 'undefined') {
      return null;
    }
    const AudioCtx =
      window.AudioContext ||
      (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (!AudioCtx) {
      return null;
    }
    if (!this.ctx) {
      this.ctx = new AudioCtx();
    }
    if (this.ctx.state === 'suspended') {
      void this.ctx.resume();
    }
    return this.ctx;
  }

  /** Rising chime for successful time in / time out. */
  playSuccess(): void {
    const ctx = this.ensureContext();
    if (!ctx) {
      return;
    }
    const now = ctx.currentTime;
    this.tone(ctx, 523.25, now, 0.14, 0.8); // C5
    this.tone(ctx, 659.25, now + 0.12, 0.18, 0.85); // E5
    this.tone(ctx, 783.99, now + 0.26, 0.28, 0.8); // G5
  }

  /** Lively party fanfare for birthday taps: bouncy arpeggio, flourish, and sparkles. */
  playBirthday(): void {
    const ctx = this.ensureContext();
    if (!ctx) {
      return;
    }
    const now = ctx.currentTime;

    // Bouncy ascending arpeggio (C major run up).
    this.tone(ctx, 523.25, now, 0.12, 0.8); // C5
    this.tone(ctx, 659.25, now + 0.1, 0.12, 0.8); // E5
    this.tone(ctx, 783.99, now + 0.2, 0.12, 0.8); // G5
    this.tone(ctx, 1046.5, now + 0.3, 0.2, 0.85); // C6

    // "Ta-da!" flourish (three simultaneous voices — peaks sum to ~1 to avoid clipping).
    this.tone(ctx, 783.99, now + 0.52, 0.1, 0.7); // G5
    this.tone(ctx, 1046.5, now + 0.62, 0.34, 0.55); // C6 held
    this.tone(ctx, 1318.51, now + 0.62, 0.34, 0.28); // E6 harmony
    // Warm body an octave below the flourish.
    this.tone(ctx, 523.25, now + 0.62, 0.34, 0.17, 'triangle'); // C5

    // Sprinkle of high sparkles, like confetti.
    this.tone(ctx, 1567.98, now + 0.78, 0.08, 0.5, 'triangle'); // G6
    this.tone(ctx, 2093.0, now + 0.9, 0.08, 0.45, 'triangle'); // C7
    this.tone(ctx, 1760.0, now + 1.02, 0.08, 0.45, 'triangle'); // A6
    this.tone(ctx, 2093.0, now + 1.14, 0.12, 0.5, 'triangle'); // C7
  }

  /** Descending beep for failed tap. */
  playError(): void {
    const ctx = this.ensureContext();
    if (!ctx) {
      return;
    }
    const now = ctx.currentTime;
    this.tone(ctx, 392.0, now, 0.16, 0.8, 'triangle'); // G4
    this.tone(ctx, 311.13, now + 0.15, 0.22, 0.75, 'triangle'); // Eb4
  }

  private tone(
    ctx: AudioContext,
    frequency: number,
    start: number,
    duration: number,
    peakGain: number,
    type: OscillatorType = 'sine',
  ): void {
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.type = type;
    osc.frequency.value = frequency;
    gain.gain.setValueAtTime(0.0001, start);
    gain.gain.exponentialRampToValueAtTime(peakGain, start + 0.02);
    gain.gain.exponentialRampToValueAtTime(0.0001, start + duration);
    osc.connect(gain);
    gain.connect(ctx.destination);
    osc.start(start);
    osc.stop(start + duration + 0.02);
  }
}
