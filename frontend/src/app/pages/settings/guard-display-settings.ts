import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  lucideClock,
  lucideMonitorOff,
  lucideTrash2,
  lucideUpload,
  lucideVideo,
} from '@ng-icons/lucide';
import { HlmButton } from '@spartan-ng/helm/button';
import {
  GuardDisplayApiService,
  type GuardDisplayMode,
  type GuardVideo,
  guardVideoUrl,
} from '../../core/settings/guard-display-api.service';

interface ModeOption {
  value: GuardDisplayMode;
  label: string;
  description: string;
  icon: string;
}

@Component({
  selector: 'app-guard-display-settings',
  imports: [DatePipe, DecimalPipe, NgIcon, HlmButton],
  viewProviders: [
    provideIcons({ lucideClock, lucideVideo, lucideMonitorOff, lucideUpload, lucideTrash2 }),
  ],
  templateUrl: './guard-display-settings.html',
  host: { class: 'flex h-full flex-col' },
})
export class GuardDisplaySettings {
  private readonly api = inject(GuardDisplayApiService);

  protected readonly mode = signal<GuardDisplayMode>('RECENT_TAPS');
  protected readonly videos = signal<GuardVideo[]>([]);
  protected readonly loading = signal(true);
  protected readonly savingMode = signal(false);
  protected readonly uploading = signal(false);
  protected readonly deletingId = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  protected readonly modeOptions: ModeOption[] = [
    {
      value: 'RECENT_TAPS',
      label: 'Recent Taps',
      description: 'Live feed of today\u2019s taps beside the scan result.',
      icon: 'lucideClock',
    },
    {
      value: 'VIDEO',
      label: 'Video',
      description: 'Loop admin-uploaded videos on the side panel.',
      icon: 'lucideVideo',
    },
    {
      value: 'NONE',
      label: 'None',
      description: 'Hide the side panel and show only the scan result.',
      icon: 'lucideMonitorOff',
    },
  ];

  constructor() {
    this.api.getDisplay().subscribe({
      next: (settings) => {
        this.mode.set(settings.mode);
        this.videos.set(settings.videos);
        this.loading.set(false);
      },
      error: (err: { error?: { message?: string } }) => {
        this.loading.set(false);
        this.error.set(err?.error?.message ?? 'Failed to load display settings');
      },
    });
  }

  protected selectMode(mode: GuardDisplayMode): void {
    if (mode === this.mode() || this.savingMode()) {
      return;
    }
    const previous = this.mode();
    this.mode.set(mode);
    this.savingMode.set(true);
    this.error.set(null);
    this.api.setMode(mode).subscribe({
      next: (settings) => {
        this.mode.set(settings.mode);
        this.videos.set(settings.videos);
        this.savingMode.set(false);
      },
      error: (err: { error?: { message?: string } }) => {
        this.mode.set(previous);
        this.savingMode.set(false);
        this.error.set(err?.error?.message ?? 'Failed to save display mode');
      },
    });
  }

  protected onFilesSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    input.value = '';
    if (files.length === 0 || this.uploading()) {
      return;
    }
    this.uploading.set(true);
    this.error.set(null);
    this.api.uploadVideos(files).subscribe({
      next: (videos) => {
        this.videos.set(videos);
        this.uploading.set(false);
      },
      error: (err: { error?: { message?: string } }) => {
        this.uploading.set(false);
        this.error.set(err?.error?.message ?? 'Failed to upload videos');
      },
    });
  }

  protected deleteVideo(video: GuardVideo): void {
    if (!confirm(`Delete video "${video.originalName}"? Guard pages will stop playing it.`)) {
      return;
    }
    this.deletingId.set(video.id);
    this.error.set(null);
    this.api.deleteVideo(video.id).subscribe({
      next: () => {
        this.videos.update((list) => list.filter((v) => v.id !== video.id));
        this.deletingId.set(null);
      },
      error: (err: { error?: { message?: string } }) => {
        this.deletingId.set(null);
        this.error.set(err?.error?.message ?? 'Failed to delete video');
      },
    });
  }

  protected videoSrc(video: GuardVideo): string {
    return guardVideoUrl(video.url);
  }

  protected sizeMb(bytes: number): number {
    return bytes / (1024 * 1024);
  }
}
