import { Directive, ElementRef, computed, inject, input } from '@angular/core';
import { PhotoPreviewService } from './photo-preview.service';

@Directive({
  selector: '[photoPreview]',
  host: {
    '(click)': 'onActivate($event)',
    '(keydown.enter)': 'onActivate($event)',
    '(keydown.space)': 'onActivate($event)',
    '[class.cursor-zoom-in]': 'canOpen()',
    '[attr.role]': 'canOpen() ? "button" : null',
    '[attr.tabindex]': 'canOpen() ? 0 : null',
    '[attr.aria-label]': 'ariaLabel()',
  },
})
export class PhotoPreview {
  private readonly host = inject(ElementRef<HTMLElement>);
  private readonly preview = inject(PhotoPreviewService);

  /** Image URL to open. Falls back to the host <img> src when omitted. */
  readonly photoPreview = input<string | null | undefined>(undefined);
  readonly photoPreviewAlt = input<string>('');

  protected readonly canOpen = computed(() => !!this.photoPreview()?.trim());
  protected readonly ariaLabel = computed(() =>
    this.canOpen() ? `View photo of ${this.photoPreviewAlt() || 'person'}` : null,
  );

  protected onActivate(event: Event): void {
    const src = this.resolveUrl();
    if (!src) {
      return;
    }
    event.preventDefault();
    event.stopPropagation();
    this.preview.open(src, this.resolveAlt());
  }

  private resolveUrl(): string | null {
    const bound = this.photoPreview()?.trim();
    if (bound) {
      return bound;
    }
    const el = this.host.nativeElement;
    if (el instanceof HTMLImageElement) {
      return el.currentSrc || el.src || null;
    }
    return null;
  }

  private resolveAlt(): string {
    const bound = this.photoPreviewAlt().trim();
    if (bound) {
      return bound;
    }
    const el = this.host.nativeElement;
    if (el instanceof HTMLImageElement) {
      return el.alt;
    }
    return 'Photo';
  }
}
