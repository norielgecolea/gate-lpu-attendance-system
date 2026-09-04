import { DatePipe } from '@angular/common';
import { Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideEraser, lucideRefreshCw, lucideSearch, lucideTriangleAlert } from '@ng-icons/lucide';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmInput } from '@spartan-ng/helm/input';
import { HlmTableImports } from '@spartan-ng/helm/table';
import { Subscription, filter } from 'rxjs';
import { NotificationService } from '../../core/notifications/notification.service';
import { AuthService } from '../../core/auth/auth.service';
import { kioskGroupFromRole } from '../../core/kiosk/kiosk-group';
import {
  TapErrorLogsApiService,
  type TapErrorLog,
} from '../../core/tap-errors/tap-error-logs-api.service';

function manilaToday(): string {
  return new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Manila' });
}

@Component({
  selector: 'app-tap-error-logs',
  imports: [DatePipe, FormsModule, NgIcon, HlmButton, HlmInput, HlmTableImports],
  viewProviders: [
    provideIcons({ lucideSearch, lucideEraser, lucideRefreshCw, lucideTriangleAlert }),
  ],
  templateUrl: './tap-error-logs.html',
  host: { class: 'flex h-full flex-col' },
})
export class TapErrorLogs implements OnDestroy {
  private readonly api = inject(TapErrorLogsApiService);
  private readonly notifications = inject(NotificationService);
  private readonly auth = inject(AuthService);
  private readonly liveSub: Subscription;

  protected readonly logs = signal<TapErrorLog[]>([]);
  protected readonly loading = signal(false);
  protected readonly clearing = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly filter = signal('');
  /** Campus (Asia/Manila) date — defaults to today so lists stay small and relevant. */
  protected readonly date = signal(manilaToday());

  protected readonly filtered = computed(() => {
    const term = this.filter().trim().toLowerCase();
    if (!term) {
      return this.logs();
    }
    return this.logs().filter((row) =>
      [row.identifier, row.location ?? ''].join(' ').toLowerCase().includes(term),
    );
  });

  constructor() {
    this.reload();
    // Keep the table current when new unrecognized taps arrive for the selected day.
    this.liveSub = this.notifications.events$
      .pipe(filter((e) => e.type === 'ATTENDANCE_TAP_ERROR'))
      .subscribe((event) => {
        const payload = (event.payload ?? {}) as { tappedAt?: string | null; kioskGroup?: string | null };
        const myGroup = kioskGroupFromRole(this.auth.user()?.role);
        if (payload.kioskGroup && payload.kioskGroup !== myGroup) {
          return;
        }
        const eventDay = payload.tappedAt
          ? new Date(payload.tappedAt).toLocaleDateString('en-CA', { timeZone: 'Asia/Manila' })
          : manilaToday();
        if (eventDay === this.date()) {
          this.reload();
        }
      });
  }

  ngOnDestroy(): void {
    this.liveSub.unsubscribe();
  }

  protected onDateChange(value: string): void {
    this.date.set(value || manilaToday());
    this.reload();
  }

  protected reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.list({ date: this.date(), limit: 2000 }).subscribe({
      next: (logs) => {
        this.logs.set(logs);
        this.loading.set(false);
      },
      error: (err: { error?: { message?: string } }) => {
        this.loading.set(false);
        this.error.set(err?.error?.message ?? 'Failed to load RFID error logs');
      },
    });
  }

  protected clearLogs(): void {
    if (this.logs().length === 0) {
      return;
    }
    const day = this.date();
    if (
      !confirm(
        `Clear ${this.logs().length} RFID error log(s) for ${day}? This cannot be undone.`,
      )
    ) {
      return;
    }
    this.clearing.set(true);
    this.error.set(null);
    this.api.clear(day).subscribe({
      next: () => {
        this.logs.set([]);
        this.clearing.set(false);
      },
      error: (err: { error?: { message?: string } }) => {
        this.clearing.set(false);
        this.error.set(err?.error?.message ?? 'Failed to clear RFID error logs');
      },
    });
  }
}
