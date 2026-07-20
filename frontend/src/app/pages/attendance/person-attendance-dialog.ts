import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  lucideChevronDown,
  lucideChevronsUpDown,
  lucideChevronUp,
  lucideEraser,
  lucideFileUp,
} from '@ng-icons/lucide';
import { injectBrnDialogContext } from '@spartan-ng/brain/dialog';
import { HlmAvatarImports } from '@spartan-ng/helm/avatar';
import { HlmBadge } from '@spartan-ng/helm/badge';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmDialogHeader, HlmDialogTitle } from '@spartan-ng/helm/dialog';
import { HlmInput } from '@spartan-ng/helm/input';
import { HlmLabel } from '@spartan-ng/helm/label';
import { HlmTableImports } from '@spartan-ng/helm/table';
import {
  AttendanceApiService,
  type AttendanceDailyRecord,
  type AttendanceEventRecord,
  type PersonAttendanceSummary,
  type PersonType,
} from '../../core/attendance/attendance-api.service';
import { EmployeesApiService } from '../../core/employees/employees-api.service';
import { StudentsApiService } from '../../core/students/students-api.service';
import { studentPhotoUrl } from '../../core/students/student-photo.util';
import { infiniteScroll } from '../../shared/infinite-scroll';
import type { Employee } from '../employees/employees.store';
import type { Student } from '../students/students.store';

type Tab = 'daily' | 'timeline';
type DatePreset = 'today' | 'week' | 'month' | 'custom';

@Component({
  selector: 'app-person-attendance-dialog',
  imports: [
    FormsModule,
    NgIcon,
    HlmButton,
    HlmInput,
    HlmLabel,
    HlmBadge,
    HlmDialogHeader,
    HlmDialogTitle,
    HlmAvatarImports,
    HlmTableImports,
  ],
  viewProviders: [
    provideIcons({
      lucideEraser,
      lucideFileUp,
      lucideChevronsUpDown,
      lucideChevronUp,
      lucideChevronDown,
    }),
  ],
  templateUrl: './person-attendance-dialog.html',
})
export class PersonAttendanceDialog {
  private readonly api = inject(AttendanceApiService);
  private readonly studentsApi = inject(StudentsApiService);
  private readonly employeesApi = inject(EmployeesApiService);
  private readonly context = injectBrnDialogContext<{
    personType: PersonType;
    personId: string;
  }>();

  protected readonly personType = this.context.personType;
  protected readonly personId = this.context.personId;
  protected readonly isStudent = this.personType === 'STUDENT';

  protected readonly person = signal<Student | Employee | null>(null);
  protected readonly personError = signal<string | null>(null);
  protected readonly summary = signal<PersonAttendanceSummary | null>(null);
  protected readonly dailyRows = signal<AttendanceDailyRecord[]>([]);
  protected readonly eventRows = signal<AttendanceEventRecord[]>([]);
  protected readonly dailyTotal = signal(0);
  protected readonly eventTotal = signal(0);
  protected readonly dailyHasMore = signal(false);
  protected readonly eventHasMore = signal(false);
  protected readonly loading = signal(false);
  protected readonly loadingMore = signal(false);
  protected readonly exporting = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly tab = signal<Tab>('daily');
  protected readonly preset = signal<DatePreset>('month');
  protected readonly startDate = signal(shiftDays(todayIso(), -29));
  protected readonly endDate = signal(todayIso());
  protected readonly action = signal('');
  protected readonly sortDir = signal<'asc' | 'desc'>('desc');
  protected readonly scroll = infiniteScroll();

  constructor() {
    this.loadPerson();
    this.reload();
  }

  protected applyPreset(preset: DatePreset): void {
    this.preset.set(preset);
    const today = todayIso();
    if (preset === 'today') {
      this.startDate.set(today);
      this.endDate.set(today);
    } else if (preset === 'week') {
      this.startDate.set(shiftDays(today, -6));
      this.endDate.set(today);
    } else if (preset === 'month') {
      this.startDate.set(shiftDays(today, -29));
      this.endDate.set(today);
    }
    if (preset !== 'custom') this.reload();
  }

  protected onCustomDateChange(): void {
    this.preset.set('custom');
    this.reload();
  }

  protected clear(): void {
    this.action.set('');
    this.applyPreset('month');
  }

  protected setTab(tab: Tab): void {
    this.tab.set(tab);
    this.scroll.reset();
  }

  protected toggleSort(): void {
    this.sortDir.set(this.sortDir() === 'desc' ? 'asc' : 'desc');
    this.reload();
  }

  protected sortIcon(): string {
    return this.sortDir() === 'asc' ? 'lucideChevronUp' : 'lucideChevronDown';
  }

  protected reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.scroll.reset();
    this.api
      .personSummary(this.personType, this.personId, this.startDate(), this.endDate())
      .subscribe({
        next: (summary) => this.summary.set(summary),
        error: () => undefined,
      });

    if (this.tab() === 'daily') {
      this.api
        .personDaily(this.personType, this.personId, {
          startDate: this.startDate(),
          endDate: this.endDate(),
          sortBy: 'date',
          sortDir: this.sortDir(),
          offset: 0,
          limit: 50,
        })
        .subscribe({
          next: (page) => {
            this.dailyRows.set(page.items);
            this.dailyTotal.set(page.total);
            this.dailyHasMore.set(page.items.length < page.total);
            this.loading.set(false);
          },
          error: (err: { error?: { message?: string } }) => {
            this.loading.set(false);
            this.error.set(err?.error?.message ?? 'Failed to load attendance');
          },
        });
    } else {
      this.api
        .personEvents(this.personType, this.personId, {
          startDate: this.startDate(),
          endDate: this.endDate(),
          action: this.action() || undefined,
          sortDir: this.sortDir(),
          offset: 0,
          limit: 50,
        })
        .subscribe({
          next: (page) => {
            this.eventRows.set(page.items);
            this.eventTotal.set(page.total);
            this.eventHasMore.set(page.items.length < page.total);
            this.loading.set(false);
          },
          error: (err: { error?: { message?: string } }) => {
            this.loading.set(false);
            this.error.set(err?.error?.message ?? 'Failed to load tap timeline');
          },
        });
    }
  }

  protected onScroll(event: Event): void {
    const total =
      this.tab() === 'daily' ? this.dailyRows().length : this.eventRows().length;
    this.scroll.onScroll(event, total);
    const el = event.target as HTMLElement;
    const hasMore = this.tab() === 'daily' ? this.dailyHasMore() : this.eventHasMore();
    if (
      el.scrollTop + el.clientHeight >= el.scrollHeight - 160 &&
      this.scroll.visible() >= total &&
      hasMore &&
      !this.loadingMore()
    ) {
      this.loadMore();
    }
  }

  protected loadMore(): void {
    if (this.loadingMore()) return;
    this.loadingMore.set(true);
    if (this.tab() === 'daily') {
      this.api
        .personDaily(this.personType, this.personId, {
          startDate: this.startDate(),
          endDate: this.endDate(),
          sortBy: 'date',
          sortDir: this.sortDir(),
          offset: this.dailyRows().length,
          limit: 50,
        })
        .subscribe({
          next: (page) => {
            this.dailyRows.update((list) => [...list, ...page.items]);
            this.dailyTotal.set(page.total);
            this.dailyHasMore.set(this.dailyRows().length < page.total);
            this.loadingMore.set(false);
          },
          error: () => this.loadingMore.set(false),
        });
    } else {
      this.api
        .personEvents(this.personType, this.personId, {
          startDate: this.startDate(),
          endDate: this.endDate(),
          action: this.action() || undefined,
          sortDir: this.sortDir(),
          offset: this.eventRows().length,
          limit: 50,
        })
        .subscribe({
          next: (page) => {
            this.eventRows.update((list) => [...list, ...page.items]);
            this.eventTotal.set(page.total);
            this.eventHasMore.set(this.eventRows().length < page.total);
            this.loadingMore.set(false);
          },
          error: () => this.loadingMore.set(false),
        });
    }
  }

  protected exportCsv(): void {
    this.exporting.set(true);
    this.api
      .exportPersonEvents(this.personType, this.personId, {
        startDate: this.startDate(),
        endDate: this.endDate(),
        action: this.action() || undefined,
      })
      .subscribe({
        next: (blob) => {
          const url = URL.createObjectURL(blob);
          const a = document.createElement('a');
          a.href = url;
          a.download = `${this.personType.toLowerCase()}-${this.personId}-attendance.csv`;
          a.click();
          URL.revokeObjectURL(url);
          this.exporting.set(false);
        },
        error: (err: { error?: { message?: string } }) => {
          this.exporting.set(false);
          this.error.set(err?.error?.message ?? 'Failed to export');
        },
      });
  }

  protected initials(name: string): string {
    const parts = name.replace(',', '').trim().split(/\s+/);
    return ((parts[0]?.[0] ?? '') + (parts[1]?.[0] ?? '')).toUpperCase();
  }

  protected photoSrc(photo: string | null | undefined): string | null {
    return studentPhotoUrl(photo);
  }

  protected formatTime(value: string | null | undefined): string {
    if (!value) return '—';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return value;
    return date.toLocaleString('en-PH', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: true,
      timeZone: 'Asia/Manila',
    });
  }

  protected formatDateTime(value: string | null | undefined): string {
    if (!value) return '—';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return value;
    return date.toLocaleString('en-PH', {
      year: 'numeric',
      month: 'short',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: true,
      timeZone: 'Asia/Manila',
    });
  }

  protected personNo(person: Student | Employee): string {
    return this.isStudent ? (person as Student).studentNo : (person as Employee).employeeNo;
  }

  private loadPerson(): void {
    if (this.isStudent) {
      this.studentsApi.getById(this.personId).subscribe({
        next: (person) => this.person.set(person),
        error: () => this.personError.set('Student not found.'),
      });
      return;
    }
    this.employeesApi.getById(this.personId).subscribe({
      next: (person) => this.person.set(person),
      error: () => this.personError.set('Employee not found.'),
    });
  }
}

function todayIso(): string {
  return new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Manila' });
}

function shiftDays(iso: string, days: number): string {
  const date = new Date(`${iso}T00:00:00`);
  date.setDate(date.getDate() + days);
  return date.toLocaleDateString('en-CA');
}
