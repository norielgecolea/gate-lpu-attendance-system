import { isPlatformBrowser } from '@angular/common';
import { Injectable, PLATFORM_ID, inject } from '@angular/core';
import { WebAudioPlayer } from './web-audio-player';

/**
 * Loud attention tones for admin/monitor RFID error alerts.
 * Uses the Web Audio API so no audio assets are required.
 */
@Injectable({ providedIn: 'root' })
export class AlertSoundService {
  private readonly platformId = inject(PLATFORM_ID);
  private readonly player = new WebAudioPlayer();

  playError(): void {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }
    this.player.playRfidError();
  }
}
