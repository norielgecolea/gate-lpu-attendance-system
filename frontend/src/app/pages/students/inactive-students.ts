import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideRotateCcw, lucideSearch, lucideTrash2, lucideUpload } from '@ng-icons/lucide';
import { HlmAvatarImports } from '@spartan-ng/helm/avatar';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmInput } from '@spartan-ng/helm/input';
import { HlmTableImports } from '@spartan-ng/helm/table';
import {
  StudentsApiService,
  type StudentEnrollmentDeactivateResult,
} from '../../core/students/students-api.service';
import { studentPhotoUrl } from '../../core/students/student-photo.util';
import { PhotoPreview } from '../../shared/photo-preview/photo-preview.directive';
import type { Student } from './students.store';

@Component({
  selector: 'app-inactive-students',
  imports: [FormsModule, NgIcon, HlmButton, HlmInput, HlmTableImports, HlmAvatarImports, PhotoPreview],
  viewProviders: [provideIcons({ lucideSearch, lucideRotateCcw, lucideTrash2, lucideUpload })],
  templateUrl: './inactive-students.html',
  host: { class: 'flex h-full flex-col' },
})
export class InactiveStudents {
  private readonly api = inject(StudentsApiService);

  protected readonly students = signal<Student[]>([]);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly restoringId = signal<string | null>(null);
  protected readonly deletingId = signal<string | null>(null);
  protected readonly filter = signal('');
  protected readonly importing = signal(false);
  protected readonly importMessage = signal<string | null>(null);

  protected readonly filtered = computed(() => {
    const term = this.filter().trim().toLowerCase();
    if (!term) {
      return this.students();
    }
    return this.students().filter((s) =>
      [s.name, s.studentNo, s.rfid ?? '', s.department, s.course]
        .join(' ')
        .toLowerCase()
        .includes(term),
    );
  });

  constructor() {
    this.reload();
  }

  protected async importEnrolled(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file || this.importing()) {
      return;
    }

    this.error.set(null);
    this.importMessage.set(null);
    try {
      const numbers = mapEnrolledStudentNumbers(parseCsv(await file.text()));
      if (numbers.length === 0) {
        throw new Error('CSV does not contain any student numbers.');
      }
      const confirmed = confirm(
        `Mark every local student who is not in ${file.name} as inactive? ${numbers.length} enrolled student number(s) will stay active.`,
      );
      if (!confirmed) {
        return;
      }

      this.importing.set(true);
      this.api.deactivateNotEnrolled(numbers).subscribe({
        next: (result: StudentEnrollmentDeactivateResult) => {
          this.importing.set(false);
          this.importMessage.set(
            `Marked inactive: ${result.deactivated}. Already inactive: ${result.alreadyInactive}. Still enrolled: ${result.keptActive}. Not in the system: ${result.notInSystem}.`,
          );
          this.reload();
        },
        error: (err: { error?: { message?: string } }) => {
          this.importing.set(false);
          this.error.set(err?.error?.message ?? 'Failed to apply the enrolled list.');
        },
      });
    } catch (err) {
      this.error.set(err instanceof Error ? err.message : 'Invalid CSV file.');
    }
  }

  protected reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.listInactive().subscribe({
      next: (students) => {
        this.students.set(students);
        this.loading.set(false);
      },
      error: (err: { error?: { message?: string } }) => {
        this.loading.set(false);
        this.error.set(err?.error?.message ?? 'Failed to load inactive students');
      },
    });
  }

  protected restore(student: Student): void {
    if (!confirm(`Restore student ${student.name}?`)) {
      return;
    }
    this.error.set(null);
    this.restoringId.set(student.id);
    this.api.restore(student.id).subscribe({
      next: () => {
        this.students.update((list) => list.filter((s) => s.id !== student.id));
        this.restoringId.set(null);
      },
      error: (err: { error?: { message?: string } }) => {
        this.restoringId.set(null);
        this.error.set(err?.error?.message ?? 'Failed to restore student');
      },
    });
  }

  protected permanentlyDelete(student: Student): void {
    if (
      !confirm(
        `Permanently delete ${student.name}? This will also delete all attendance logs and tap events. This action cannot be undone.`,
      )
    ) {
      return;
    }
    this.error.set(null);
    this.deletingId.set(student.id);
    this.api.permanentlyDelete(student.id).subscribe({
      next: () => {
        this.students.update((list) => list.filter((s) => s.id !== student.id));
        this.deletingId.set(null);
      },
      error: (err: { error?: { message?: string } }) => {
        this.deletingId.set(null);
        this.error.set(err?.error?.message ?? 'Failed to permanently delete student');
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

const ENROLLED_HEADERS = new Set([
  'studentnumber',
  'studentno',
  'studentid',
  'idnumber',
  'id',
  'enrolled',
]);

function mapEnrolledStudentNumbers(rows: string[][]): string[] {
  if (rows.length === 0) {
    return [];
  }
  const header = (rows[0][0] ?? '')
    .replace(/^\uFEFF/, '')
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]/g, '');
  const start = ENROLLED_HEADERS.has(header) ? 1 : 0;
  const seen = new Set<string>();
  const numbers: string[] = [];
  for (let i = start; i < rows.length; i++) {
    const value = (rows[i][0] ?? '').replace(/^\uFEFF/, '').trim();
    if (!value || ENROLLED_HEADERS.has(value.toLowerCase().replace(/[^a-z0-9]/g, ''))) {
      continue;
    }
    const key = value.toLowerCase();
    if (seen.has(key)) {
      continue;
    }
    seen.add(key);
    numbers.push(value);
  }
  return numbers;
}
