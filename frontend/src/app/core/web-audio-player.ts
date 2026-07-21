/** Shared Web Audio helpers — loud, friendly kiosk/admin tones without asset files. */
export class WebAudioPlayer {
  private ctx: AudioContext | null = null;
  private master: GainNode | null = null;

  constructor(private readonly masterVolume = 0.92) {}

  playRfidError(): void {
    const ctx = this.ensureContext();
    if (!ctx) {
      return;
    }
    const t = ctx.currentTime;
    // Three punchy descending alerts — easy to hear across a busy office.
    this.chime(ctx, t, 988, 0.14, 0.82, 'triangle');
    this.chime(ctx, t + 0.17, 740, 0.14, 0.8, 'triangle');
    this.chime(ctx, t + 0.34, 554, 0.24, 0.88, 'triangle');
    this.chime(ctx, t + 0.34, 277, 0.24, 0.35, 'sine');
  }

  playTapError(): void {
    const ctx = this.ensureContext();
    if (!ctx) {
      return;
    }
    const t = ctx.currentTime;
    this.chime(ctx, t, 360, 0.2, 0.95, 'triangle');
    this.slide(ctx, t + 0.14, 360, 240, 0.32, 1.0, 'triangle');
  }

  /** Loud reject pattern for unknown RFID / no matching record. */
  playNotFound(): void {
    const ctx = this.ensureContext();
    if (!ctx) {
      return;
    }
    const t = ctx.currentTime;
    // Four staccato "buzz" hits — unmistakable in a noisy gate area.
    const hits = [0, 0.13, 0.26, 0.39];
    for (const at of hits) {
      this.buzz(ctx, t + at, 196, 0.1, 1.0);
      this.buzz(ctx, t + at, 392, 0.08, 0.45, 'square');
    }
    // Low finishing slide — "record missing" feel.
    this.slide(ctx, t + 0.52, 330, 110, 0.45, 1.0, 'triangle');
    this.chime(ctx, t + 0.52, 82.41, 0.45, 0.55, 'sine');
  }

  playTimeIn(): void {
    const ctx = this.ensureContext();
    if (!ctx) {
      return;
    }
    const t = ctx.currentTime;
    // Rising welcome chime — clearly "entering".
    this.chime(ctx, t, 392.0, 0.14, 0.92, 'sine');
    this.chime(ctx, t + 0.11, 493.88, 0.14, 0.94, 'sine');
    this.chime(ctx, t + 0.22, 587.33, 0.14, 0.96, 'sine');
    this.chime(ctx, t + 0.33, 783.99, 0.38, 1.0, 'sine');
    this.chime(ctx, t + 0.33, 392.0, 0.38, 0.35, 'triangle');
    this.chime(ctx, t + 0.45, 987.77, 0.12, 0.7, 'sine');
  }

  playTimeOut(): void {
    const ctx = this.ensureContext();
    if (!ctx) {
      return;
    }
    const t = ctx.currentTime;
    // Descending farewell — clearly "leaving", different contour from time-in.
    this.chime(ctx, t, 987.77, 0.14, 0.95, 'sine');
    this.chime(ctx, t + 0.12, 783.99, 0.14, 0.96, 'sine');
    this.chime(ctx, t + 0.24, 587.33, 0.14, 0.97, 'sine');
    this.chime(ctx, t + 0.36, 440.0, 0.4, 1.0, 'sine');
    this.chime(ctx, t + 0.36, 220.0, 0.4, 0.4, 'triangle');
    this.chime(ctx, t + 0.5, 329.63, 0.16, 0.65, 'sine');
  }

  playTapSuccess(): void {
    this.playTimeIn();
  }

  playBirthday(): void {
    const ctx = this.ensureContext();
    if (!ctx) {
      return;
    }
    const t = ctx.currentTime;
    // Festive fanfare — bouncy and unmistakable.
    this.chime(ctx, t, 587.33, 0.11, 0.8, 'sine');
    this.chime(ctx, t + 0.09, 739.99, 0.11, 0.82, 'sine');
    this.chime(ctx, t + 0.18, 880.0, 0.11, 0.84, 'sine');
    this.chime(ctx, t + 0.27, 1174.66, 0.22, 0.9, 'sine');

    this.chime(ctx, t + 0.5, 880.0, 0.1, 0.75, 'triangle');
    this.chime(ctx, t + 0.58, 1174.66, 0.38, 0.72, 'sine');
    this.chime(ctx, t + 0.58, 1479.98, 0.38, 0.45, 'sine');
    this.chime(ctx, t + 0.58, 587.33, 0.38, 0.3, 'triangle');

    this.chime(ctx, t + 0.78, 1760.0, 0.09, 0.7, 'sine');
    this.chime(ctx, t + 0.9, 2093.0, 0.09, 0.68, 'sine');
    this.chime(ctx, t + 1.02, 2349.32, 0.09, 0.66, 'sine');
    this.chime(ctx, t + 1.14, 2637.02, 0.16, 0.72, 'sine');
  }

  playFinanceWarning(): void {
    const ctx = this.ensureContext();
    if (!ctx) {
      return;
    }
    const t = ctx.currentTime;
    // Alternating two-note caution — distinct from errors and success.
    const pattern = [
      { freq: 830.61, at: 0 },
      { freq: 622.25, at: 0.16 },
      { freq: 830.61, at: 0.32 },
      { freq: 622.25, at: 0.48 },
      { freq: 830.61, at: 0.64 },
    ];
    for (const note of pattern) {
      this.chime(ctx, t + note.at, note.freq, 0.14, 0.84, 'triangle');
      this.chime(ctx, t + note.at, note.freq / 2, 0.14, 0.22, 'sine');
    }
  }

  private buzz(
    ctx: AudioContext,
    start: number,
    frequency: number,
    duration: number,
    peak: number,
    type: OscillatorType = 'triangle',
  ): void {
    this.chime(ctx, start, frequency, duration, peak, type);
  }

  private chime(
    ctx: AudioContext,
    start: number,
    frequency: number,
    duration: number,
    peak: number,
    type: OscillatorType,
  ): void {
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.type = type;
    osc.frequency.value = frequency;
    const attack = 0.005;
    gain.gain.setValueAtTime(0.0001, start);
    gain.gain.exponentialRampToValueAtTime(Math.max(peak, 0.0002), start + attack);
    gain.gain.exponentialRampToValueAtTime(0.0001, start + duration);
    osc.connect(gain);
    gain.connect(this.output(ctx));
    osc.start(start);
    osc.stop(start + duration + 0.04);
  }

  private slide(
    ctx: AudioContext,
    start: number,
    fromHz: number,
    toHz: number,
    duration: number,
    peak: number,
    type: OscillatorType,
  ): void {
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.type = type;
    osc.frequency.setValueAtTime(fromHz, start);
    osc.frequency.exponentialRampToValueAtTime(Math.max(toHz, 1), start + duration);
    gain.gain.setValueAtTime(0.0001, start);
    gain.gain.exponentialRampToValueAtTime(peak, start + 0.006);
    gain.gain.exponentialRampToValueAtTime(0.0001, start + duration);
    osc.connect(gain);
    gain.connect(this.output(ctx));
    osc.start(start);
    osc.stop(start + duration + 0.04);
  }

  private output(ctx: AudioContext): AudioNode {
    return this.master ?? ctx.destination;
  }

  private ensureContext(): AudioContext | null {
    if (typeof window === 'undefined') {
      return null;
    }
    const Ctor =
      window.AudioContext ??
      (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (!Ctor) {
      return null;
    }
    if (!this.ctx) {
      this.ctx = new Ctor();
      this.master = this.ctx.createGain();
      this.master.gain.value = this.masterVolume;
      this.master.connect(this.ctx.destination);
    }
    if (this.ctx.state === 'suspended') {
      void this.ctx.resume().catch(() => undefined);
    }
    return this.ctx;
  }
}
