import { WebAudioPlayer } from '../../core/web-audio-player';

/** Gate kiosk sounds — louder master gain for open gate areas. */
export class GateSounds {
  private readonly player = new WebAudioPlayer(0.98);

  playTimeIn(): void {
    this.player.playTimeIn();
  }

  playTimeOut(): void {
    this.player.playTimeOut();
  }

  playSuccess(): void {
    this.player.playTimeIn();
  }

  playBirthday(): void {
    this.player.playBirthday();
  }

  playError(): void {
    this.player.playTapError();
  }

  playNotFound(): void {
    this.player.playNotFound();
  }

  playFinanceWarning(): void {
    this.player.playFinanceWarning();
  }
}
