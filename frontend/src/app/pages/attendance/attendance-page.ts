import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  lucideChevronDown,
  lucideChevronsUpDown,
  lucideChevronUp,
  lucideFileUp,
  lucideHistory,
  lucideSearch,
} from '@ng-icons/lucide';
import { HlmAvatarImports } from '@spartan-ng/helm/avatar';
import { HlmBadge } from '@spartan-ng/helm/badge';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmDialogService } from '@spartan-ng/helm/dialog';
import { HlmInput } from '@spartan-ng/helm/input';
import { HlmLabel } from '@spartan-ng/helm/label';
import { HlmSeparator } from '@spartan-ng/helm/separator';
import { HlmTableImports } from '@spartan-ng/helm/table';
import {
  type ColumnDef,
  type SortingState,
  createAngularTable,
  getCoreRowModel,
} from '@tanstack/angular-table';
import { Subject, debounceTime, distinctUntilChanged, filter } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import {
  AttendanceApiService,
  type AttendanceDailyRecord,
  type AttendancePersonFilter,
  type AttendanceSummary,
} from '../../core/attendance/attendance-api.service';
import {
  KIOSK_GROUPS,
  KIOSK_GROUP_LABELS,
  canPickExportKiosk,
  kioskGroupFromRole,
  kioskGroupSlug,
  type KioskGroup,
} from '../../core/kiosk/kiosk-group';
import { studentPhotoUrl } from '../../core/students/student-photo.util';
import { infiniteScroll } from '../../shared/infinite-scroll';
import { PhotoPreview } from '../../shared/photo-preview/photo-preview.directive';
import {
  AttendanceExportDialog,
  type AttendanceExportResult,
} from './attendance-export-dialog';

type DatePreset = 'today' | 'week' | 'month' | 'custom';

@Component({
  selector: 'app-attendance-page',
  imports: [
    FormsModule,
    RouterLink,
    NgIcon,
    HlmButton,
    HlmInput,
    HlmLabel,
    HlmBadge,
    HlmSeparator,
    HlmTableImports,
    HlmAvatarImports,
    PhotoPreview,
  ],
  viewProviders: [
    provideIcons({
      lucideFileUp,
      lucideSearch,
      lucideHistory,
      lucideChevronsUpDown,
      lucideChevronUp,
      lucideChevronDown,
    }),
  ],
  templateUrl: './attendance-page.html',
  host: { class: 'flex h-full flex-col' },
})
export class AttendancePage {
  private readonly api = inject(AttendanceApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(HlmDialogService);
  private readonly searchChanges = new Subject<string>();

  protected readonly personType = signal<AttendancePersonFilter>(
    (this.route.snapshot.data['personType'] as AttendancePersonFilter) ?? 'STUDENT',
  );
  protected readonly isCombined = () => this.personType() === 'ALL';
  protected readonly isStudent = () => this.personType() === 'STUDENT';
  protected readonly canPickKiosk = canPickExportKiosk(this.auth.user()?.role);
  protected readonly kioskGroups = KIOSK_GROUPS;
  protected readonly kioskLabels = KIOSK_GROUP_LABELS;
  protected readonly listKioskGroup = signal<KioskGroup>(
    kioskGroupFromRole(this.auth.user()?.role),
  );

  protected readonly rows = signal<AttendanceDailyRecord[]>([]);
  protected readonly total = signal(0);
  protected readonly hasMore = signal(false);
  protected readonly loading = signal(false);
  protected readonly loadingMore = signal(false);
  protected readonly exporting = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly summary = signal<AttendanceSummary>({
    uniquePeople: 0,
    completeDays: 0,
    openDays: 0,
    totalTaps: 0,
    currentlyIn: 0,
  });
  protected readonly search = signal('');
  protected readonly preset = signal<DatePreset>('today');
  protected readonly startDate = signal(todayIso());
  protected readonly endDate = signal(todayIso());
  protected readonly sorting = signal<SortingState>([{ id: 'date', desc: true }]);
  protected readonly scroll = infiniteScroll();

  private readonly columns: ColumnDef<AttendanceDailyRecord>[] = [
    { accessorKey: 'personType', header: 'Type', id: 'personType' },
    { accessorKey: 'name', header: 'Name', id: 'name' },
    { accessorKey: 'department', header: 'Department', id: 'department' },
    { accessorKey: 'course', header: 'Course', id: 'course' },
    { accessorKey: 'school', header: 'School', id: 'school' },
    { accessorKey: 'position', header: 'Position', id: 'position' },
    { accessorKey: 'attendanceDate', header: 'Date', id: 'date' },
    { accessorKey: 'timeIn', header: 'Time in', id: 'timeIn' },
    { accessorKey: 'timeOut', header: 'Time out', id: 'timeOut' },
    { accessorKey: 'tapCount', header: 'Taps', id: 'tapCount' },
    { accessorKey: 'status', header: 'Status', id: 'status' },
    { id: 'actions', header: 'Action', enableSorting: false },
  ];

  protected readonly table = createAngularTable<AttendanceDailyRecord>(() => ({
    data: this.rows(),
    columns: this.visibleColumns(),
    state: { sorting: this.sorting() },
    manualSorting: true,
    getRowId: (row) => row.id,
    onSortingChange: (updater) => {
      this.sorting.set(typeof updater === 'function' ? updater(this.sorting()) : updater);
      this.reload();
    },
    getCoreRowModel: getCoreRowModel(),
  }));

  constructor() {
    this.searchChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((term) => {
        this.search.set(term);
        this.reload();
      });
    this.applyPreset('today');
    this.reload();
  }

  protected visibleColumns(): ColumnDef<AttendanceDailyRecord>[] {
    const combined = this.isCombined();
    const student = this.isStudent();
    return this.columns.filter((col) => {
      if (col.id === 'personType') return combined;
      if (col.id === 'course') return student || combined;
      if (col.id === 'school') return student;
      if (col.id === 'position') return !student || combined;
      return true;
    });
  }

  protected searchPlaceholder(): string {
    if (this.isCombined()) return 'Search students and employees';
    return this.isStudent() ? 'Search students' : 'Search employees';
  }

  protected onSearchChange(term: string): void {
    this.searchChanges.next(term.trim());
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
    if (preset !== 'custom') {
      this.reload();
    }
  }

  protected onCustomDateChange(): void {
    this.preset.set('custom');
    this.reload();
  }

  protected setListKiosk(group: KioskGroup): void {
    this.listKioskGroup.set(group);
    this.reload();
  }

  protected reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.scroll.reset();
    const query = this.currentQuery(0);
    this.api.page(query).subscribe({
      next: (page) => {
        this.rows.set(page.items);
        this.total.set(page.total);
        this.hasMore.set(page.items.length < page.total);
        this.loading.set(false);
      },
      error: (err: { error?: { message?: string } }) => {
        this.loading.set(false);
        this.error.set(err?.error?.message ?? 'Failed to load attendance');
      },
    });
    this.api.summary(query).subscribe({
      next: (summary) => this.summary.set(summary),
      error: () => undefined,
    });
  }

  protected onTableScroll(event: Event): void {
    const loaded = this.table.getRowModel().rows.length;
    this.scroll.onScroll(event, loaded);
    const el = event.target as HTMLElement;
    if (
      el.scrollTop + el.clientHeight >= el.scrollHeight - 200 &&
      this.scroll.visible() >= loaded &&
      this.hasMore() &&
      !this.loadingMore()
    ) {
      this.loadMore();
    }
  }

  protected loadMore(): void {
    if (this.loadingMore() || !this.hasMore()) return;
    this.loadingMore.set(true);
    this.api.page(this.currentQuery(this.rows().length)).subscribe({
      next: (page) => {
        this.rows.update((list) => [...list, ...page.items]);
        this.total.set(page.total);
        this.hasMore.set(this.rows().length < page.total);
        this.loadingMore.set(false);
      },
      error: () => this.loadingMore.set(false),
    });
  }

  protected openExport(): void {
    const ref = AttendanceExportDialog.open(this.dialog, {
      startDate: this.startDate(),
      endDate: this.endDate(),
      canPickKiosk: this.canPickKiosk,
      lockedKioskGroup: this.listKioskGroup(),
    });
    ref.closed$
      .pipe(filter((result): result is AttendanceExportResult => !!result))
      .subscribe((result) => this.exportCsv(result));
  }

  protected exportCsv(result: AttendanceExportResult): void {
    this.exporting.set(true);
    this.error.set(null);
    const query = {
      ...this.currentQuery(0),
      startDate: result.allTime ? undefined : result.startDate,
      endDate: result.allTime ? undefined : result.endDate,
      kioskGroup: result.kioskGroup,
    };
    this.api.exportCsv(query).subscribe({
      next: (blob) => {
        downloadBlob(blob, this.exportFilename(result));
        this.exporting.set(false);
      },
      error: (err: { error?: { message?: string } }) => {
        this.exporting.set(false);
        this.error.set(err?.error?.message ?? 'Failed to export attendance');
      },
    });
  }

  protected detailLink(row: AttendanceDailyRecord): string[] {
    const student = row.personType === 'STUDENT' || this.isStudent();
    return student
      ? ['/students', row.personId, 'attendance']
      : ['/employees', row.personId, 'attendance'];
  }

  protected personTypeLabel(value: string | undefined): string {
    return value === 'EMPLOYEE' ? 'Employee' : 'Student';
  }

  protected sortIcon(state: false | 'asc' | 'desc'): string {
    if (state === 'asc') return 'lucideChevronUp';
    if (state === 'desc') return 'lucideChevronDown';
    return 'lucideChevronsUpDown';
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
    return date.toLocaleTimeString('en-PH', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: true,
      timeZone: 'Asia/Manila',
    });
  }

  private currentQuery(offset: number) {
    const sort = this.sorting()[0];
    return {
      personType: this.personType(),
      startDate: this.startDate() || undefined,
      endDate: this.endDate() || undefined,
      search: this.search() || undefined,
      kioskGroup: this.listKioskGroup(),
      sortBy: sort?.id ?? 'date',
      sortDir: sort ? (sort.desc ? 'desc' : 'asc') : 'desc',
      offset,
      limit: 50,
    };
  }

  private exportFilename(result: AttendanceExportResult): string {
    const type = this.personType().toLowerCase();
    const group = kioskGroupSlug(result.kioskGroup);
    const range = result.allTime
      ? 'all-time'
      : `${result.startDate ?? 'start'}-to-${result.endDate ?? 'end'}`;
    return `${type}-attendance-${group}-${range}.csv`;
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

function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  URL.revokeObjectURL(url);
}
