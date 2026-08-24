import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideBanknote, lucideFileDown, lucideSearch, lucideX } from '@ng-icons/lucide';
import { HlmAvatarImports } from '@spartan-ng/helm/avatar';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmInput } from '@spartan-ng/helm/input';
import { HlmTableImports } from '@spartan-ng/helm/table';
import {
  StudentsApiService,
  type StudentFinanceTagImportResult,
} from '../../core/students/students-api.service';
import { studentPhotoUrl } from '../../core/students/student-photo.util';
import { PhotoPreview } from '../../shared/photo-preview/photo-preview.directive';
import type { Student } from './students.store';

@Component({
  selector: 'app-finance-tagged-students',
  imports: [FormsModule, NgIcon, HlmButton, HlmInput, HlmTableImports, HlmAvatarImports, PhotoPreview],
  viewProviders: [provideIcons({ lucideSearch, lucideFileDown, lucideX, lucideBanknote })],
  templateUrl: './finance-tagged-students.html',
  host: { class: 'flex h-full flex-col' },
})
export class FinanceTaggedStudents {
  private readonly api = inject(StudentsApiService);

  protected readonly students = signal<Student[]>([]);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly importMessage = signal<string | null>(null);
  protected readonly importing = signal(false);
  protected readonly untaggingId = signal<string | null>(null);
  protected readonly filter = signal('');

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

  protected reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.listFinanceTagged().subscribe({
      next: (students) => {
        this.students.set(students);
        this.loading.set(false);
      },
      error: (err: { error?: { message?: string } }) => {
        this.loading.set(false);
        this.error.set(err?.error?.message ?? 'Failed to load finance-tagged students');
      },
    });
  }

  protected async importCsv(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file || this.importing()) {
      return;
    }

    this.error.set(null);
    this.importMessage.set(null);
    try {
      const numbers = mapFinanceStudentNumbers(parseCsv(await file.text()));
      if (numbers.length === 0) {
        throw new Error('CSV does not contain any student_number values.');
      }
      if (!confirm(`Tag ${numbers.length} student number(s) from ${file.name} as finance unsettled?`)) {
        return;
      }

      this.importing.set(true);
      this.api.importFinanceTagged(numbers).subscribe({
        next: (result: StudentFinanceTagImportResult) => {
          this.importing.set(false);
          this.importMessage.set(
            `Finance tags applied: ${result.tagged}. Already tagged: ${result.alreadyTagged}. Not found: ${result.notFound}.`,
          );
          this.reload();
        },
        error: (err: { error?: { message?: string } }) => {
          this.importing.set(false);
          this.error.set(err?.error?.message ?? 'Failed to import finance tags.');
        },
      });
    } catch (err) {
      this.error.set(err instanceof Error ? err.message : 'Invalid CSV file.');
    }
  }

  protected untag(student: Student): void {
    if (!confirm(`Remove finance tag from ${student.name}?`)) {
      return;
    }
    this.error.set(null);
    this.untaggingId.set(student.id);
    this.api.financeUntag(student.id).subscribe({
      next: () => {
        this.students.update((list) => list.filter((s) => s.id !== student.id));
        this.untaggingId.set(null);
      },
      error: (err: { error?: { message?: string } }) => {
        this.untaggingId.set(null);
        this.error.set(err?.error?.message ?? 'Failed to remove finance tag.');
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

function mapFinanceStudentNumbers(rows: string[][]): string[] {
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
  const studentNoIndex = headers.findIndex((header) =>
    ['studentnumber', 'studentno', 'studentid', 'idnumber'].includes(header),
  );
  if (studentNoIndex < 0) {
    throw new Error('CSV must include a student_number column.');
  }
  const seen = new Set<string>();
  const numbers: string[] = [];
  for (let i = 1; i < rows.length; i++) {
    const value = (rows[i][studentNoIndex] ?? '').trim();
    if (!value) continue;
    const key = value.toLowerCase();
    if (seen.has(key)) continue;
    seen.add(key);
    numbers.push(value);
  }
  return numbers;
}
