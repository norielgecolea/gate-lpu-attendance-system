import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  lucideDownload,
  lucideEraser,
  lucideSearch,
  lucideTriangleAlert,
  lucideUpload,
} from '@ng-icons/lucide';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmInput } from '@spartan-ng/helm/input';
import {
  BackupApiService,
  backupErrorMessage,
  type BackupRestoreResult,
  type PhotoCleanupResult,
} from '../../core/backup/backup-api.service';

@Component({
  selector: 'app-backup',
  imports: [FormsModule, NgIcon, HlmButton, HlmInput],
  viewProviders: [
    provideIcons({ lucideDownload, lucideUpload, lucideTriangleAlert, lucideSearch, lucideEraser }),
  ],
  templateUrl: './backup.html',
  host: { class: 'flex h-full flex-col' },
})
export class Backup {
  static readonly CONFIRM_PHRASE = 'RESTORE';

  private readonly api = inject(BackupApiService);

  protected readonly downloading = signal(false);
  protected readonly restoring = signal(false);
  protected readonly scanningPictures = signal(false);
  protected readonly cleaningPictures = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly message = signal<string | null>(null);
  protected readonly selectedFile = signal<File | null>(null);
  protected readonly confirmText = signal('');
  protected readonly pictureCleanup = signal<PhotoCleanupResult | null>(null);
  protected readonly busy = computed(
    () => this.downloading() || this.restoring() || this.scanningPictures() || this.cleaningPictures(),
  );
  protected readonly canRestore = computed(
    () =>
      !!this.selectedFile() &&
      this.confirmText() === Backup.CONFIRM_PHRASE &&
      !this.busy(),
  );

  protected onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    input.value = '';
    this.error.set(null);
    this.message.set(null);
    this.confirmText.set('');
    if (file && !file.name.toLowerCase().endsWith('.zip')) {
      this.selectedFile.set(null);
      this.error.set('Backup must be a .zip file created by this page.');
      return;
    }
    this.selectedFile.set(file);
  }

  protected downloadBackup(): void {
    if (this.busy()) {
      return;
    }
    this.downloading.set(true);
    this.error.set(null);
    this.message.set(null);
    this.api.download().subscribe({
      next: (blob) => {
        downloadBlob(blob, backupFilename());
        this.downloading.set(false);
        this.message.set('Backup downloaded. Store it somewhere safe.');
      },
      error: (err: unknown) => {
        void backupErrorMessage(err, 'Failed to create backup').then((text) => {
          this.error.set(text);
          this.downloading.set(false);
        });
      },
    });
  }

  protected restoreBackup(): void {
    const file = this.selectedFile();
    if (!file || this.confirmText() !== Backup.CONFIRM_PHRASE || this.busy()) {
      return;
    }
    if (
      !confirm(
        `This will replace ALL current data, photos, videos, and tones with "${file.name}". Continue?`,
      )
    ) {
      return;
    }
    this.restoring.set(true);
    this.error.set(null);
    this.message.set(null);
    this.api.restore(file).subscribe({
      next: (result) => {
        this.restoring.set(false);
        this.selectedFile.set(null);
        this.confirmText.set('');
        this.message.set(restoreSuccessMessage(result));
      },
      error: (err: unknown) => {
        void backupErrorMessage(err, 'Failed to restore backup').then((text) => {
          this.error.set(text);
          this.restoring.set(false);
        });
      },
    });
  }

  protected scanUnusedPictures(): void {
    if (this.busy()) {
      return;
    }
    this.scanningPictures.set(true);
    this.error.set(null);
    this.message.set(null);
    this.api.unusedPictures().subscribe({
      next: (result) => {
        this.scanningPictures.set(false);
        this.pictureCleanup.set(result);
        this.message.set(
          result.unused === 0
            ? 'No unused picture files. Every file on disk is still assigned to a student or employee.'
            : `Found ${result.unused} unused picture file(s) (${result.onDisk} on disk, ${result.referenced} still assigned).`,
        );
      },
      error: (err: unknown) => {
        void backupErrorMessage(err, 'Failed to scan unused pictures').then((text) => {
          this.error.set(text);
          this.scanningPictures.set(false);
        });
      },
    });
  }

  protected cleanupUnusedPictures(): void {
    const preview = this.pictureCleanup();
    if (this.busy() || !preview || preview.unused === 0) {
      return;
    }
    if (
      !confirm(
        `Delete ${preview.unused} unused picture file(s)? Photos still assigned to students or employees (including inactive records) are kept.`,
      )
    ) {
      return;
    }
    this.cleaningPictures.set(true);
    this.error.set(null);
    this.message.set(null);
    this.api.cleanupUnusedPictures().subscribe({
      next: (result) => {
        this.cleaningPictures.set(false);
        this.pictureCleanup.set(result);
        this.message.set(
          `Deleted ${result.deleted} unused picture file(s)` +
            (result.failed > 0 ? `. ${result.failed} could not be removed.` : '.'),
        );
      },
      error: (err: unknown) => {
        void backupErrorMessage(err, 'Failed to delete unused pictures').then((text) => {
          this.error.set(text);
          this.cleaningPictures.set(false);
        });
      },
    });
  }
}

function restoreSuccessMessage(result: BackupRestoreResult): string {
  const files = result.picturesCopied + result.videosCopied + result.tonesCopied;
  return (
    `Restore complete (${files} media file(s) copied). ` +
    'Refresh the page. Sign in again if your account was different in the backup.'
  );
}

function backupFilename(): string {
  const parts = Object.fromEntries(
    new Intl.DateTimeFormat('en-CA', {
      timeZone: 'Asia/Manila',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false,
    })
      .formatToParts(new Date())
      .map((part) => [part.type, part.value]),
  );
  return `lpu-attendance-backup-${parts['year']}${parts['month']}${parts['day']}-${parts['hour']}${parts['minute']}${parts['second']}.zip`;
}

function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  URL.revokeObjectURL(url);
}
