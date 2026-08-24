import { isPlatformBrowser } from '@angular/common';
import { Component, DestroyRef, PLATFORM_ID, effect, inject } from '@angular/core';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideX } from '@ng-icons/lucide';
import { PhotoPreviewService } from './photo-preview.service';

@Component({
  selector: 'app-photo-preview-overlay',
  imports: [NgIcon],
  viewProviders: [provideIcons({ lucideX })],
  template: `
    @if (preview.src(); as src) {
      <div
        class="fixed inset-0 z-[3000] flex items-center justify-center bg-black/80 p-4 sm:p-8"
        role="dialog"
        aria-modal="true"
        [attr.aria-label]="preview.alt()"
        (click)="preview.close()"
      >
        <button
          type="button"
          class="absolute top-3 right-3 grid size-10 place-items-center rounded-full bg-white/10 text-white transition-colors hover:bg-white/20"
          aria-label="Close photo"
          (click)="preview.close(); $event.stopPropagation()"
        >
          <ng-icon name="lucideX" class="text-xl" />
        </button>
        <img
          [src]="src"
          [alt]="preview.alt()"
          class="max-h-[90vh] max-w-[min(90vw,56rem)] rounded-xl object-contain shadow-2xl"
          (click)="$event.stopPropagation()"
        />
      </div>
    }
  `,
})
export class PhotoPreviewOverlay {
  protected readonly preview = inject(PhotoPreviewService);
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));

  constructor() {
    if (!this.isBrowser) {
      return;
    }
    const onKeydown = (event: KeyboardEvent): void => {
      if (event.key === 'Escape' && this.preview.src()) {
        event.preventDefault();
        event.stopImmediatePropagation();
        this.preview.close();
      }
    };
    document.addEventListener('keydown', onKeydown, true);
    inject(DestroyRef).onDestroy(() => {
      document.removeEventListener('keydown', onKeydown, true);
      document.body.style.overflow = '';
    });
    effect(() => {
      document.body.style.overflow = this.preview.src() ? 'hidden' : '';
    });
  }
}
