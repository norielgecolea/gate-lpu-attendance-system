import { WebAudioPlayer } from '../../core/web-audio-player';
import {
  gateToneUrl,
  type GateToneEvent,
  type GateToneSettings,
} from '../../core/settings/gate-tones-api.service';

/** Gate kiosk sounds — custom uploaded tones when assigned, otherwise built-in. */
export class GateSounds {
  private readonly player = new WebAudioPlayer(0.98);
  private urls: Partial<Record<GateToneEvent, string>> = {};
  private current: HTMLAudioElement | null = null;
  /** Bumps on every play so interrupted Audio.play() promises do not run fallback. */
  private playGeneration = 0;

  /** Apply server tone library + assignments (null assignment = built-in). */
  applySettings(settings: GateToneSettings): void {
    const byId = new Map(settings.tones.map((tone) => [String(tone.id), tone]));
    const next: Partial<Record<GateToneEvent, string>> = {};
    for (const event of [
      'TIME_IN',
      'TIME_OUT',
      'ERROR',
      'FINANCE_TAGGED',
      'BIRTHDAY',
    ] as GateToneEvent[]) {
      const rawId = settings.assignments[event];
      const toneId =
        rawId != null && String(rawId).trim() !== '' ? String(rawId).trim() : null;
      const tone = toneId ? byId.get(toneId) : undefined;
      if (tone) {
        next[event] = gateToneUrl(tone.url);
      }
    }
    this.urls = next;
  }

  playTimeIn(): void {
    this.playCustomOr('TIME_IN', () => this.player.playTimeIn());
  }

  playTimeOut(): void {
    this.playCustomOr('TIME_OUT', () => this.player.playTimeOut());
  }

  playSuccess(): void {
    this.playTimeIn();
  }

  playBirthday(): void {
    this.playCustomOr('BIRTHDAY', () => this.player.playBirthday());
  }

  playError(): void {
    this.playCustomOr('ERROR', () => this.player.playTapError());
  }

  playNotFound(): void {
    this.playCustomOr('ERROR', () => this.player.playNotFound());
  }

  playFinanceWarning(): void {
    this.playCustomOr('FINANCE_TAGGED', () => this.player.playFinanceWarning());
  }

  private playCustomOr(event: GateToneEvent, fallback: () => void): void {
    const url = this.urls[event];
    if (!url) {
      fallback();
      return;
    }
    const generation = ++this.playGeneration;
    try {
      if (this.current) {
        this.current.pause();
        this.current.removeAttribute('src');
        this.current.load();
        this.current = null;
      }
      const audio = new Audio(url);
      audio.volume = 1;
      this.current = audio;
      void audio.play().catch((err: unknown) => {
        // A newer play() superseded this one (e.g. HTTP + WebSocket both fire).
        if (generation !== this.playGeneration) {
          return;
        }
        const name =
          err && typeof err === 'object' && 'name' in err
            ? String((err as { name?: string }).name)
            : '';
        if (name === 'AbortError') {
          return;
        }
        fallback();
      });
    } catch {
      if (generation === this.playGeneration) {
        fallback();
      }
    }
  }
}
