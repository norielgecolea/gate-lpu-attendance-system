import { Component, input } from '@angular/core';
import type { PhotoBulkUploadProgress } from '../../core/media/bulk-photo-upload';

@Component({
  selector: 'app-photo-upload-progress',
  host: { class: 'block' },
  template: `
    <div class="bg-card rounded-md border px-3 py-2" role="status" aria-live="polite">
      <div class="flex items-center justify-between gap-3 text-sm">
        <span class="text-foreground">
          Uploading pictures… {{ progress().processed }} of {{ progress().total }}
        </span>
        <span class="text-muted-foreground tabular-nums">{{ progress().percent }}%</span>
      </div>
      <div
        class="bg-muted mt-2 h-2 overflow-hidden rounded-full"
        role="progressbar"
        aria-label="Picture upload progress"
        [attr.aria-valuemin]="0"
        [attr.aria-valuemax]="100"
        [attr.aria-valuenow]="progress().percent"
      >
        <div
          class="bg-primary h-full rounded-full transition-[width] duration-200"
          [style.width.%]="progress().percent"
        ></div>
      </div>
    </div>
  `,
})
export class PhotoUploadProgressBar {
  readonly progress = input.required<PhotoBulkUploadProgress>();
}
