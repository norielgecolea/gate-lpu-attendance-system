import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideRotateCcw, lucideSearch, lucideTrash2 } from '@ng-icons/lucide';
import { HlmAvatarImports } from '@spartan-ng/helm/avatar';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmInput } from '@spartan-ng/helm/input';
import { HlmTableImports } from '@spartan-ng/helm/table';
import { StudentsApiService } from '../../core/students/students-api.service';
import { studentPhotoUrl } from '../../core/students/student-photo.util';
import type { Student } from './students.store';

@Component({
  selector: 'app-inactive-students',
  imports: [FormsModule, NgIcon, HlmButton, HlmInput, HlmTableImports, HlmAvatarImports],
  viewProviders: [provideIcons({ lucideSearch, lucideRotateCcw, lucideTrash2 })],
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
