import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  lucideBriefcase,
  lucideGraduationCap,
  lucideHistory,
  lucideRefreshCw,
  lucideSearch,
} from '@ng-icons/lucide';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmInput } from '@spartan-ng/helm/input';
import { HlmTableImports } from '@spartan-ng/helm/table';
import {
  AuditLogsApiService,
  type AuditLog,
  type AuditPersonType,
} from '../../core/audit/audit-logs-api.service';

function manilaToday(): string {
  return new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Manila' });
}

@Component({
  selector: 'app-audit-logs',
  imports: [DatePipe, FormsModule, NgIcon, HlmButton, HlmInput, HlmTableImports],
  viewProviders: [
    provideIcons({
      lucideHistory,
      lucideSearch,
      lucideRefreshCw,
      lucideGraduationCap,
      lucideBriefcase,
    }),
  ],
  templateUrl: './audit-logs.html',
  host: { class: 'flex h-full flex-col' },
})
export class AuditLogs {
  private readonly api = inject(AuditLogsApiService);

  protected readonly logs = signal<AuditLog[]>([]);
  protected readonly total = signal(0);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly search = signal('');
  protected readonly date = signal(manilaToday());
  protected readonly personType = signal<AuditPersonType | ''>('');

  protected readonly filtered = computed(() => {
    const term = this.search().trim().toLowerCase();
    if (!term) {
      return this.logs();
    }
    return this.logs().filter((row) =>
      [
        row.personName,
        row.personNo,
        row.action,
        row.actorUsername ?? '',
        row.personType,
      ]
        .join(' ')
        .toLowerCase()
        .includes(term),
    );
  });

  constructor() {
    this.reload();
  }

  protected onDateChange(value: string): void {
    this.date.set(value || manilaToday());
    this.reload();
  }

  protected onPersonTypeChange(value: string): void {
    this.personType.set(value === 'STUDENT' || value === 'EMPLOYEE' ? value : '');
    this.reload();
  }

  protected reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .list({
        date: this.date(),
        personType: this.personType(),
        limit: 500,
      })
      .subscribe({
        next: (page) => {
          this.logs.set(page.items);
          this.total.set(page.total);
          this.loading.set(false);
        },
        error: (err: { error?: { message?: string } }) => {
          this.loading.set(false);
          this.error.set(err?.error?.message ?? 'Failed to load audit logs');
        },
      });
  }

  protected actionLabel(action: string): string {
    switch (action) {
      case 'CREATED':
        return 'Added';
      case 'UPDATED':
        return 'Edited';
      case 'PHOTO_UPDATED':
        return 'Photo added';
      case 'DELETED':
        return 'Deleted';
      default:
        return action;
    }
  }
}
