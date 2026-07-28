import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink, RouterOutlet } from '@angular/router';
import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  lucideChevronDown,
  lucideChevronsUpDown,
  lucideChevronUp,
  lucideFileDown,
  lucideFileUp,
  lucideHistory,
  lucideImages,
  lucidePlus,
  lucideSearch,
  lucideSquarePen,
  lucideUserX,
} from '@ng-icons/lucide';
import { HlmAvatarImports } from '@spartan-ng/helm/avatar';
import { HlmBadge } from '@spartan-ng/helm/badge';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmCheckbox } from '@spartan-ng/helm/checkbox';
import { HlmDialogService } from '@spartan-ng/helm/dialog';
import { HlmInput } from '@spartan-ng/helm/input';
import { HlmSeparator } from '@spartan-ng/helm/separator';
import { HlmTableImports } from '@spartan-ng/helm/table';
import {
  type ColumnDef,
  type RowSelectionState,
  type SortingState,
  createAngularTable,
  getCoreRowModel,
  getSortedRowModel,
} from '@tanstack/angular-table';
import { Subject, catchError, debounceTime, distinctUntilChanged, filter, of, take } from 'rxjs';
import {
  StudentsApiService,
  type StudentAuditEvent,
  type StudentPayload,
} from '../../core/students/students-api.service';
import { studentPhotoUrl } from '../../core/students/student-photo.util';
import { infiniteScroll } from '../../shared/infinite-scroll';
import {
  StudentFormDialog,
  type StudentFormResult,
} from './student-form-dialog';
import { type Student, StudentsStore } from './students.store';

@Component({
  selector: 'app-students',
  imports: [
    FormsModule,
    RouterLink,
    RouterOutlet,
    NgIcon,
    HlmButton,
    HlmInput,
    HlmCheckbox,
    HlmBadge,
    HlmSeparator,
    HlmTableImports,
    HlmAvatarImports,
  ],
  viewProviders: [
    provideIcons({
      lucidePlus,
      lucideUserX,
      lucideFileDown,
      lucideFileUp,
      lucideImages,
      lucideSearch,
      lucideHistory,
      lucideSquarePen,
      lucideChevronsUpDown,
      lucideChevronUp,
      lucideChevronDown,
    }),
  ],
  templateUrl: './students.html',
  host: { class: 'flex h-full flex-col' },
})
export class Students {
  private readonly store = inject(StudentsStore);
  private readonly api = inject(StudentsApiService);
  private readonly dialog = inject(HlmDialogService);

  protected readonly data = this.store.students;
  protected readonly total = this.store.total;
  protected readonly hasMore = this.store.hasMore;
  protected readonly loading = this.store.loading;
  protected readonly loadingMore = this.store.loadingMore;
  protected readonly error = this.store.error;
  protected readonly actionError = signal<string | null>(null);
  protected readonly importMessage = signal<string | null>(null);
  protected readonly importing = signal(false);
  protected readonly exporting = signal(false);
  protected readonly uploadingPhotos = signal(false);

  protected readonly sorting = signal<SortingState>([]);
  protected readonly search = signal('');
  protected readonly rowSelection = signal<RowSelectionState>({});
  protected readonly scroll = infiniteScroll();
  private readonly searchChanges = new Subject<string>();

  private readonly columns: ColumnDef<Student>[] = [
    { id: 'select', enableSorting: false },
    { accessorKey: 'name', header: 'Name' },
    { accessorKey: 'rfid', header: 'RFID #' },
    { accessorKey: 'department', header: 'Department' },
    { accessorKey: 'course', header: 'Course' },
    { accessorKey: 'school', header: 'School' },
    { id: 'actions', header: 'Action', enableSorting: false },
  ];

  protected readonly table = createAngularTable<Student>(() => ({
    data: this.data(),
    columns: this.columns,
    state: {
      sorting: this.sorting(),
      rowSelection: this.rowSelection(),
    },
    enableRowSelection: true,
    getRowId: (row) => row.id,
    onSortingChange: (updater) =>
      this.sorting.set(typeof updater === 'function' ? updater(this.sorting()) : updater),
    onRowSelectionChange: (updater) =>
      this.rowSelection.set(typeof updater === 'function' ? updater(this.rowSelection()) : updater),
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
  }));

  constructor() {
    this.reload();
    // Search runs on the server so we only ever fetch matching pages.
    this.searchChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((term) => {
        this.scroll.reset();
        this.rowSelection.set({});
        this.store.load(term).subscribe({ error: () => undefined });
      });
  }

  protected onSearchChange(term: string): void {
    this.search.set(term);
    this.searchChanges.next(term.trim());
  }

  /** Grows the client reveal window and fetches the next server page near the bottom. */
  protected onTableScroll(event: Event): void {
    const loadedRows = this.table.getRowModel().rows.length;
    this.scroll.onScroll(event, loadedRows);
    const el = event.target as HTMLElement;
    if (el.scrollTop + el.clientHeight >= el.scrollHeight - 200 && this.scroll.visible() >= loadedRows) {
      this.store.loadMore();
    }
  }

  protected reload(): void {
    this.actionError.set(null);
    // Always use the visible search box value so leaving/returning the page
    // (empty input) reloads the default list instead of a stale store term.
    this.store.load(this.search().trim()).subscribe({ error: () => undefined });
  }

  protected openCreate(): void {
    this.openForm('create');
  }

  protected openEdit(student: Student): void {
    this.api
      .getAuditEvents(student.id)
      .pipe(
        take(1),
        catchError(() => of([] as StudentAuditEvent[])),
      )
      .subscribe((events) => {
        this.openForm('edit', student, this.formatCreatedAuditLabel(events));
      });
  }

  protected deleteOne(student: Student): void {
    if (!confirm(`Mark student ${student.name} as inactive?`)) {
      return;
    }
    this.actionError.set(null);
    this.store.delete(student.id).subscribe({
      next: () => this.rowSelection.set({}),
      error: (err: { error?: { message?: string } }) =>
        this.actionError.set(err?.error?.message ?? 'Failed to deactivate student'),
    });
  }

  protected deleteSelected(): void {
    const ids = Object.keys(this.rowSelection()).filter((id) => this.rowSelection()[id]);
    if (ids.length === 0) {
      this.actionError.set('Select at least one student to deactivate.');
      return;
    }
    if (!confirm(`Mark ${ids.length} selected student(s) as inactive?`)) {
      return;
    }
    this.actionError.set(null);
    this.store.deleteMany(ids).subscribe({
      next: () => this.rowSelection.set({}),
      error: (err: { error?: { message?: string } }) =>
        this.actionError.set(err?.error?.message ?? 'Failed to deactivate students'),
    });
  }

  protected async importCsv(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file || this.importing()) {
      return;
    }

    this.actionError.set(null);
    this.importMessage.set(null);
    try {
      const rows = parseCsv(await file.text());
      const payload = mapStudentRows(rows);
      if (payload.length === 0) {
        throw new Error('The CSV does not contain any student records.');
      }
      if (!confirm(`Import ${payload.length} student record(s) from ${file.name}?`)) {
        return;
      }

      this.importing.set(true);
      this.api.importCsv(payload).subscribe({
        next: (result) => {
          this.importing.set(false);
          this.importMessage.set(
            `Imported ${result.imported} new, updated ${result.updated} existing. Skipped ${result.skippedDuplicates} duplicate RFID(s).`,
          );
          this.reload();
        },
        error: (err: { error?: { message?: string } }) => {
          this.importing.set(false);
          this.actionError.set(err?.error?.message ?? 'Failed to import students.');
        },
      });
    } catch (error) {
      this.actionError.set(error instanceof Error ? error.message : 'Invalid CSV file.');
    }
  }

  protected exportCsv(): void {
    if (this.exporting()) {
      return;
    }
    this.actionError.set(null);
    this.exporting.set(true);
    this.api.exportCsv().subscribe({
      next: (blob) => {
        downloadBlob(blob, 'students.csv');
        this.exporting.set(false);
        this.importMessage.set('Exported all active student records.');
      },
      error: (err: { error?: { message?: string } }) => {
        this.exporting.set(false);
        this.actionError.set(err?.error?.message ?? 'Failed to export students.');
      },
    });
  }

  protected uploadPhotos(event: Event): void {
    const input = event.target as HTMLInputElement;
    const files = input.files ? Array.from(input.files) : [];
    input.value = '';
    if (files.length === 0 || this.uploadingPhotos()) {
      return;
    }
    if (!confirm(`Upload ${files.length} photo(s)? Filenames must match student numbers (e.g. 2021-0001.jpg).`)) {
      return;
    }

    this.actionError.set(null);
    this.importMessage.set(null);
    this.uploadingPhotos.set(true);
    this.api.bulkUploadPhotos(files).subscribe({
      next: (result) => {
        this.uploadingPhotos.set(false);
        this.importMessage.set(
          `Photos updated: ${result.updated}. Not found: ${result.notFound}. Skipped invalid: ${result.skippedInvalid}.`,
        );
        this.reload();
      },
      error: (err: { error?: { message?: string } }) => {
        this.uploadingPhotos.set(false);
        this.actionError.set(err?.error?.message ?? 'Failed to upload photos.');
      },
    });
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

  private openForm(mode: 'create' | 'edit', student?: Student, createdAuditLabel?: string | null): void {
    const ref = this.dialog.open(StudentFormDialog, {
      context: { mode, student, createdAuditLabel },
      contentClass: 'person-form-dialog',
    });

    ref.closed$
      .pipe(
        take(1),
        filter((result): result is StudentFormResult => !!result),
      )
      .subscribe((result) => {
        this.actionError.set(null);
        this.store.saveFromForm(mode, result, student?.id).subscribe({
          error: (err: { error?: { message?: string } }) =>
            this.actionError.set(err?.error?.message ?? 'Failed to save student'),
        });
      });
  }

  private formatCreatedAuditLabel(events: StudentAuditEvent[]): string | null {
    const createdEvent = events.find((event) => event.action === 'CREATED');
    if (!createdEvent) {
      return null;
    }

    const actor = createdEvent.actorUsername?.trim() || 'Unknown creator';
    const createdAt = new Date(createdEvent.createdAt);
    const when = Number.isNaN(createdAt.getTime()) ? createdEvent.createdAt : createdAt.toLocaleString();
    return `Added by ${actor} on ${when}`;
  }
}

function parseCsv(text: string): string[][] {
  const rows: string[][] = [];
  let row: string[] = [];
  let field = '';
  let quoted = false;

  for (let i = 0; i < text.length; i++) {
    const char = text[i];
    if (quoted) {
      if (char === '"' && text[i + 1] === '"') {
        field += '"';
        i++;
      } else if (char === '"') {
        quoted = false;
      } else {
        field += char;
      }
    } else if (char === '"') {
      quoted = true;
    } else if (char === ',') {
      row.push(field);
      field = '';
    } else if (char === '\n') {
      row.push(field);
      rows.push(row);
      row = [];
      field = '';
    } else if (char !== '\r') {
      field += char;
    }
  }
  if (field || row.length) {
    row.push(field);
    rows.push(row);
  }
  return rows;
}

function mapStudentRows(rows: string[][]): StudentPayload[] {
  if (rows.length < 2) {
    return [];
  }
  const headers = rows[0].map((header) =>
    header
      .replace(/^\uFEFF/, '')
      .trim()
      .toLowerCase()
      .replace(/[^a-z0-9]/g, ''),
  );
  const indexOf = (...aliases: string[]) => headers.findIndex((header) => aliases.includes(header));
  const indexes = {
    name: indexOf('name', 'fullname', 'studentname'),
    studentNo: indexOf('idnumber', 'studentnumber', 'studentno', 'id'),
    rfid: indexOf('rfid', 'rfidnumber', 'cardnumber'),
    department: indexOf('department', 'dept'),
    course: indexOf('course', 'program'),
    school: indexOf('schoolgroup', 'school'),
    birthdate: indexOf('birthday', 'birthdate', 'dateofbirth', 'dob'),
  };
  const missing = Object.entries(indexes)
    .filter(([key, index]) => key !== 'rfid' && key !== 'birthdate' && index < 0)
    .map(([key]) => key);
  if (missing.length) {
    throw new Error(`CSV is missing required column(s): ${missing.join(', ')}.`);
  }

  const value = (row: string[], index: number) => (index >= 0 ? (row[index] ?? '').trim() : '');
  return rows.slice(1).flatMap((row, rowIndex) => {
    if (row.every((cell) => !cell.trim())) {
      return [];
    }
    const required = {
      name: value(row, indexes.name),
      studentNo: value(row, indexes.studentNo),
      department: value(row, indexes.department),
      course: value(row, indexes.course),
      school: value(row, indexes.school),
    };
    const empty = Object.entries(required).find(([, cell]) => !cell);
    if (empty) {
      throw new Error(`Row ${rowIndex + 2} is missing ${empty[0]}.`);
    }
    const birthday = value(row, indexes.birthdate);
    return [{
      ...required,
      photo: null,
      rfid: value(row, indexes.rfid) || null,
      birthdate: birthday ? normalizeBirthdate(birthday, rowIndex + 2) : null,
    }];
  });
}

function normalizeBirthdate(value: string, rowNumber: number): string {
  const iso = value.match(/^(\d{4})-(\d{1,2})-(\d{1,2})$/);
  const slash = value.match(/^(\d{1,2})[/-](\d{1,2})[/-](\d{4})$/);
  let year: number;
  let month: number;
  let day: number;
  if (iso) {
    [, year, month, day] = iso.map(Number);
  } else if (slash) {
    month = Number(slash[1]);
    day = Number(slash[2]);
    year = Number(slash[3]);
  } else if (/^\d{5}$/.test(value)) {
    const excelDate = new Date(Date.UTC(1899, 11, 30 + Number(value)));
    year = excelDate.getUTCFullYear();
    month = excelDate.getUTCMonth() + 1;
    day = excelDate.getUTCDate();
  } else {
    throw new Error(`Row ${rowNumber} has an invalid Birthday. Use YYYY-MM-DD or MM/DD/YYYY.`);
  }
  const date = new Date(Date.UTC(year, month - 1, day));
  if (
    date.getUTCFullYear() !== year ||
    date.getUTCMonth() + 1 !== month ||
    date.getUTCDate() !== day
  ) {
    throw new Error(`Row ${rowNumber} has an invalid Birthday.`);
  }
  return `${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day
    .toString()
    .padStart(2, '0')}`;
}

function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  URL.revokeObjectURL(url);
}
