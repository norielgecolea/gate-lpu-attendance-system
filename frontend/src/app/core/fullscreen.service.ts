import { isPlatformBrowser } from '@angular/common';
import { Injectable, PLATFORM_ID, inject } from '@angular/core';

/** Browser Fullscreen API helpers for the guard kiosk. */
@Injectable({ providedIn: 'root' })
export class FullscreenService {
  private readonly platformId = inject(PLATFORM_ID);

  enter(): Promise<void> {
    if (!isPlatformBrowser(this.platformId) || !document.documentElement.requestFullscreen) {
      return Promise.resolve();
    }
    if (document.fullscreenElement) {
      return Promise.resolve();
    }
    return document.documentElement.requestFullscreen().catch(() => undefined);
  }

  exit(): Promise<void> {
    if (!isPlatformBrowser(this.platformId) || !document.exitFullscreen) {
      return Promise.resolve();
    }
    if (!document.fullscreenElement) {
      return Promise.resolve();
    }
    return document.exitFullscreen().catch(() => undefined);
  }
}
