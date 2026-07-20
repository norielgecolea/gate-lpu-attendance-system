import { isPlatformBrowser } from '@angular/common';
import { Injectable, PLATFORM_ID, inject } from '@angular/core';

/**
 * Plays a short attention beep for error alerts using the Web Audio API,
 * so no audio asset is required. Browsers only allow audio after a user
 * gesture; the first successful play (e.g. after login) unlocks it.
 */
@Injectable({ providedIn: 'root' })
export class AlertSoundService {
  private readonly platformId = inject(PLATFORM_ID);
  private context: AudioContext | null = null;

  /** Two descending beeps, reminiscent of an error tone. */
  playError(): void {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }
    const ctx = this.ensureContext();
    if (!ctx) {
      return;
    }
    if (ctx.state === 'suspended') {
      void ctx.resume().catch(() => undefined);
    }
    const now = ctx.currentTime;
    this.beep(ctx, 880, now, 0.16);
    this.beep(ctx, 620, now + 0.2, 0.28);
  }

  private beep(ctx: AudioContext, frequency: number, start: number, duration: number): void {
    const oscillator = ctx.createOscillator();
    const gain = ctx.createGain();
    oscillator.type = 'square';
    oscillator.frequency.value = frequency;
    // Quick attack, smooth decay to avoid clicks.
    gain.gain.setValueAtTime(0.0001, start);
    gain.gain.exponentialRampToValueAtTime(0.25, start + 0.02);
    gain.gain.exponentialRampToValueAtTime(0.0001, start + duration);
    oscillator.connect(gain);
    gain.connect(ctx.destination);
    oscillator.start(start);
    oscillator.stop(start + duration + 0.02);
  }

  private ensureContext(): AudioContext | null {
    if (this.context) {
      return this.context;
    }
    const Ctor =
      window.AudioContext ??
      (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (!Ctor) {
      return null;
    }
    this.context = new Ctor();
    return this.context;
  }
}
