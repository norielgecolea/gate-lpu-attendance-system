import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  lucideMusic2,
  lucidePlay,
  lucideTrash2,
  lucideUpload,
} from '@ng-icons/lucide';
import { HlmButton } from '@spartan-ng/helm/button';
import {
  GateTonesApiService,
  gateToneUrl,
  type GateTone,
  type GateToneEvent,
} from '../../core/settings/gate-tones-api.service';

interface EventOption {
  key: GateToneEvent;
  label: string;
  description: string;
}

@Component({
  selector: 'app-gate-tones-settings',
  imports: [DatePipe, DecimalPipe, FormsModule, NgIcon, HlmButton],
  viewProviders: [
    provideIcons({ lucideMusic2, lucidePlay, lucideTrash2, lucideUpload }),
  ],
  templateUrl: './gate-tones-settings.html',
  host: { class: 'flex h-full flex-col' },
})
export class GateTonesSettings {
  private static readonly MAX_DURATION_SEC = 10;

  private readonly api = inject(GateTonesApiService);

  protected readonly tones = signal<GateTone[]>([]);
  protected readonly assignments = signal<Record<GateToneEvent, string | null>>({
    TIME_IN: null,
    TIME_OUT: null,
    ERROR: null,
    FINANCE_TAGGED: null,
    BIRTHDAY: null,
  });
  protected readonly loading = signal(true);
  protected readonly uploading = signal(false);
  protected readonly saving = signal(false);
  protected readonly deletingId = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly message = signal<string | null>(null);

  protected readonly eventOptions: EventOption[] = [
    {
      key: 'TIME_IN',
      label: 'Time in',
      description: 'Played when a successful time-in is recorded.',
    },
    {
      key: 'TIME_OUT',
      label: 'Time out',
      description: 'Played when a successful time-out is recorded.',
    },
    {
      key: 'ERROR',
      label: 'Error / not found',
      description: 'Played for unrecognized RFID or failed taps.',
    },
    {
      key: 'FINANCE_TAGGED',
      label: 'Finance tagged',
      description: 'Played when a finance-tagged student taps.',
    },
    {
      key: 'BIRTHDAY',
      label: 'Birthday',
      description: 'Played when someone taps on their birthday.',
    },
  ];

  constructor() {
    this.reload();
  }

  protected reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.getSettings().subscribe({
      next: (settings) => {
        this.tones.set(settings.tones);
        this.assignments.set({
          TIME_IN: settings.assignments.TIME_IN ?? null,
          TIME_OUT: settings.assignments.TIME_OUT ?? null,
          ERROR: settings.assignments.ERROR ?? null,
          FINANCE_TAGGED: settings.assignments.FINANCE_TAGGED ?? null,
          BIRTHDAY: settings.assignments.BIRTHDAY ?? null,
        });
        this.loading.set(false);
      },
      error: (err: { error?: { message?: string } }) => {
        this.loading.set(false);
        this.error.set(err?.error?.message ?? 'Failed to load gate tones');
      },
    });
  }

  protected async onFilesSelected(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const files = input.files ? Array.from(input.files) : [];
    input.value = '';
    if (files.length === 0 || this.uploading()) {
      return;
    }

    this.uploading.set(true);
    this.error.set(null);
    this.message.set(null);

    try {
      const accepted: File[] = [];
      const rejected: string[] = [];
      for (const file of files) {
        const duration = await audioDurationSeconds(file);
        if (duration > GateTonesSettings.MAX_DURATION_SEC) {
          rejected.push(
            `${file.name} (${duration.toFixed(1)}s — max ${GateTonesSettings.MAX_DURATION_SEC}s)`,
          );
          continue;
        }
        accepted.push(file);
      }

      if (rejected.length > 0) {
        this.error.set(
          `Tone must be ${GateTonesSettings.MAX_DURATION_SEC} seconds or shorter. Skipped: ${rejected.join('; ')}`,
        );
      }
      if (accepted.length === 0) {
        this.uploading.set(false);
        return;
      }

      this.api.upload(accepted).subscribe({
        next: (tones) => {
          this.tones.set(tones);
          this.uploading.set(false);
          this.message.set(`Uploaded ${accepted.length} tone(s).`);
        },
        error: (err: { error?: { message?: string } }) => {
          this.uploading.set(false);
          this.error.set(err?.error?.message ?? 'Failed to upload tones');
        },
      });
    } catch (err) {
      this.uploading.set(false);
      this.error.set(err instanceof Error ? err.message : 'Could not read audio duration.');
    }
  }

  protected setAssignment(eventType: GateToneEvent, toneId: string): void {
    const normalized = toneId != null && String(toneId).trim() !== '' ? String(toneId).trim() : null;
    this.assignments.update((current) => ({
      ...current,
      [eventType]: normalized,
    }));
  }

  protected saveAssignments(): void {
    if (this.saving()) {
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.message.set(null);
    // Always send every event key as a string (empty = default) so Jackson Map<String,String>
    // never receives null values, which some configs reject and can drop later keys like BIRTHDAY.
    const current = this.assignments();
    const payload: Record<GateToneEvent, string> = {
      TIME_IN: current.TIME_IN ?? '',
      TIME_OUT: current.TIME_OUT ?? '',
      ERROR: current.ERROR ?? '',
      FINANCE_TAGGED: current.FINANCE_TAGGED ?? '',
      BIRTHDAY: current.BIRTHDAY ?? '',
    };
    this.api.setAssignments(payload).subscribe({
      next: (settings) => {
        this.tones.set(settings.tones);
        this.assignments.set({
          TIME_IN: settings.assignments.TIME_IN ?? null,
          TIME_OUT: settings.assignments.TIME_OUT ?? null,
          ERROR: settings.assignments.ERROR ?? null,
          FINANCE_TAGGED: settings.assignments.FINANCE_TAGGED ?? null,
          BIRTHDAY: settings.assignments.BIRTHDAY ?? null,
        });
        this.saving.set(false);
        this.message.set('Tone assignments saved. Guard screens update instantly.');
      },
      error: (err: { error?: { message?: string } }) => {
        this.saving.set(false);
        this.error.set(err?.error?.message ?? 'Failed to save assignments');
      },
    });
  }

  protected deleteTone(tone: GateTone): void {
    if (!confirm(`Delete tone "${tone.originalName}"?`)) {
      return;
    }
    this.deletingId.set(tone.id);
    this.error.set(null);
    this.api.delete(tone.id).subscribe({
      next: () => {
        this.deletingId.set(null);
        this.reload();
        this.message.set('Tone deleted.');
      },
      error: (err: { error?: { message?: string } }) => {
        this.deletingId.set(null);
        this.error.set(err?.error?.message ?? 'Failed to delete tone');
      },
    });
  }

  protected preview(tone: GateTone): void {
    const audio = new Audio(gateToneUrl(tone.url));
    void audio.play().catch(() => undefined);
  }

  protected toneSrc(tone: GateTone): string {
    return gateToneUrl(tone.url);
  }

  protected sizeKb(bytes: number): number {
    return bytes / 1024;
  }
}

/** Reads duration via browser metadata; rejects unreadable files. */
function audioDurationSeconds(file: File): Promise<number> {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file);
    const audio = new Audio();
    audio.preload = 'metadata';
    const cleanup = () => URL.revokeObjectURL(url);
    audio.onloadedmetadata = () => {
      const duration = audio.duration;
      cleanup();
      if (!Number.isFinite(duration) || duration <= 0) {
        reject(new Error(`Could not read duration for ${file.name}.`));
        return;
      }
      resolve(duration);
    };
    audio.onerror = () => {
      cleanup();
      reject(new Error(`Could not read audio file ${file.name}.`));
    };
    audio.src = url;
  });
}
