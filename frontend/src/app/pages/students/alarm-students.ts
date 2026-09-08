import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideBell, lucidePlus, lucideSearch, lucideX } from '@ng-icons/lucide';
import { HlmAvatarImports } from '@spartan-ng/helm/avatar';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmInput } from '@spartan-ng/helm/input';
import { HlmTableImports } from '@spartan-ng/helm/table';
import { Subject, debounceTime, distinctUntilChanged } from 'rxjs';
import { StudentsApiService } from '../../core/students/students-api.service';
import { studentPhotoUrl } from '../../core/students/student-photo.util';
import { PhotoPreview } from '../../shared/photo-preview/photo-preview.directive';
import type { Student } from './students.store';

@Component({
  selector: 'app-alarm-students',
  imports: [FormsModule, NgIcon, HlmButton, HlmInput, HlmTableImports, HlmAvatarImports, PhotoPreview],
  viewProviders: [provideIcons({ lucideSearch, lucidePlus, lucideX, lucideBell })],
  templateUrl: './alarm-students.html',
  host: { class: 'flex h-full flex-col' },
})
export class AlarmStudents {
  private readonly api = inject(StudentsApiService);
  private readonly search$ = new Subject<string>();

  protected readonly students = signal<Student[]>([]);
  protected readonly suggestions = signal<Student[]>([]);
  protected readonly loading = signal(false);
  protected readonly searching = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly query = signal('');
  protected readonly addingId = signal<string | null>(null);
  protected readonly removingId = signal<string | null>(null);

  constructor() {
    this.reload();
    this.search$
      .pipe(debounceTime(250), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((term) => this.runSearch(term));
  }

  protected reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.listAlarmMarked().subscribe({
      next: (students) => {
        this.students.set(students);
        this.loading.set(false);
      },
      error: (err: { error?: { message?: string } }) => {
        this.loading.set(false);
        this.error.set(err?.error?.message ?? 'Failed to load alarm-marked students');
      },
    });
  }

  protected onQueryChange(value: string): void {
    this.query.set(value);
    this.search$.next(value.trim());
  }

  protected add(student: Student): void {
    this.error.set(null);
    this.addingId.set(student.id);
    this.api.alarmMark(student.id).subscribe({
      next: (marked) => {
        this.students.update((list) =>
          [marked, ...list.filter((s) => s.id !== marked.id)].sort(byName),
        );
        this.suggestions.update((list) => list.filter((s) => s.id !== marked.id));
        this.addingId.set(null);
        this.query.set('');
        this.suggestions.set([]);
      },
      error: (err: { error?: { message?: string } }) => {
        this.addingId.set(null);
        this.error.set(err?.error?.message ?? 'Failed to add student to Alarm.');
      },
    });
  }

  protected remove(student: Student): void {
    if (!confirm(`Remove ${student.name} from Alarm? They can still tap as usual.`)) {
      return;
    }
    this.error.set(null);
    this.removingId.set(student.id);
    this.api.alarmUnmark(student.id).subscribe({
      next: () => {
        this.students.update((list) => list.filter((s) => s.id !== student.id));
        this.removingId.set(null);
      },
      error: (err: { error?: { message?: string } }) => {
        this.removingId.set(null);
        this.error.set(err?.error?.message ?? 'Failed to remove student from Alarm.');
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

  private runSearch(term: string): void {
    if (term.length < 2) {
      this.suggestions.set([]);
      this.searching.set(false);
      return;
    }
    this.searching.set(true);
    this.api.page(term, 0, 8).subscribe({
      next: (page) => {
        const marked = new Set(this.students().map((s) => s.id));
        this.suggestions.set(
          page.items.filter((s) => !s.alarmMarked && !marked.has(s.id)),
        );
        this.searching.set(false);
      },
      error: () => {
        this.searching.set(false);
        this.suggestions.set([]);
      },
    });
  }
}

function byName(a: Student, b: Student): number {
  return a.name.localeCompare(b.name);
}
