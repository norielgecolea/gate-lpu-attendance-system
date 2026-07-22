import { isPlatformBrowser } from '@angular/common';
import { Injectable, PLATFORM_ID, inject } from '@angular/core';

/**
 * Fullscreen helpers for the guard kiosk.
 *
 * Browsers block Fullscreen API calls without a user gesture, so remember-me
 * cold starts cannot rely on requestFullscreen alone. For installed Edge/Chrome
 * apps we also maximize the window, and the web app manifest uses
 * display: "fullscreen" so a properly installed app opens immersive.
 */
@Injectable({ providedIn: 'root' })
export class FullscreenService {
  private readonly platformId = inject(PLATFORM_ID);

  isActive(): boolean {
    if (!isPlatformBrowser(this.platformId)) {
      return false;
    }
    return !!document.fullscreenElement || this.isDisplayFullscreen();
  }

  /** Manifest display-mode: fullscreen (installed PWA / Edge app). */
  isDisplayFullscreen(): boolean {
    if (!isPlatformBrowser(this.platformId)) {
      return false;
    }
    return window.matchMedia('(display-mode: fullscreen)').matches;
  }

  /** Best-effort enter on page load — no user gesture available after remember-me. */
  enterOnLaunch(): Promise<boolean> {
    if (!isPlatformBrowser(this.platformId)) {
      return Promise.resolve(false);
    }
    // Login already entered fullscreen via the click gesture — do not call
    // moveTo/resizeTo here; those exit fullscreen in Chromium/Edge.
    if (document.fullscreenElement) {
      return Promise.resolve(true);
    }
    this.maximizeWindow();
    return this.enter();
  }

  enter(): Promise<boolean> {
    if (!isPlatformBrowser(this.platformId) || !document.documentElement.requestFullscreen) {
      return Promise.resolve(this.isActive());
    }
    if (document.fullscreenElement) {
      return Promise.resolve(true);
    }
    return document.documentElement
      .requestFullscreen()
      .then(() => true)
      .catch(() => {
        // Expected when there is no user gesture (remember-me session restore).
        if (!document.fullscreenElement) {
          this.maximizeWindow();
        }
        return this.isActive();
      });
  }

  /**
   * Installed Edge/Chrome app windows often allow resize without a gesture.
   * Never call this while the Fullscreen API is active — resize exits fullscreen.
   */
  maximizeWindow(): void {
    if (!isPlatformBrowser(this.platformId) || document.fullscreenElement) {
      return;
    }
    try {
      window.moveTo(0, 0);
      window.resizeTo(window.screen.width, window.screen.height);
    } catch {
      // Some browsers block window resizing for normal tabs.
    }
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
