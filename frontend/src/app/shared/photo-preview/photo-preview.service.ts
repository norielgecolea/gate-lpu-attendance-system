import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class PhotoPreviewService {
  readonly src = signal<string | null>(null);
  readonly alt = signal('Photo');

  open(src: string, alt = 'Photo'): void {
    this.src.set(src);
    this.alt.set(alt.trim() || 'Photo');
  }

  close(): void {
    this.src.set(null);
    this.alt.set('Photo');
  }
}
