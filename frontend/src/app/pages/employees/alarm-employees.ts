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
import { EmployeesApiService } from '../../core/employees/employees-api.service';
import { studentPhotoUrl } from '../../core/students/student-photo.util';
import { PhotoPreview } from '../../shared/photo-preview/photo-preview.directive';
import type { Employee } from './employees.store';

@Component({
  selector: 'app-alarm-employees',
  imports: [FormsModule, NgIcon, HlmButton, HlmInput, HlmTableImports, HlmAvatarImports, PhotoPreview],
  viewProviders: [provideIcons({ lucideSearch, lucidePlus, lucideX, lucideBell })],
  templateUrl: './alarm-employees.html',
  host: { class: 'flex h-full flex-col' },
})
export class AlarmEmployees {
  private readonly api = inject(EmployeesApiService);
  private readonly search$ = new Subject<string>();

  protected readonly employees = signal<Employee[]>([]);
  protected readonly suggestions = signal<Employee[]>([]);
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
      next: (employees) => {
        this.employees.set(employees);
        this.loading.set(false);
      },
      error: (err: { error?: { message?: string } }) => {
        this.loading.set(false);
        this.error.set(err?.error?.message ?? 'Failed to load alarm-marked employees');
      },
    });
  }

  protected onQueryChange(value: string): void {
    this.query.set(value);
    this.search$.next(value.trim());
  }

  protected add(employee: Employee): void {
    this.error.set(null);
    this.addingId.set(employee.id);
    this.api.alarmMark(employee.id).subscribe({
      next: (marked) => {
        this.employees.update((list) =>
          [marked, ...list.filter((e) => e.id !== marked.id)].sort(byName),
        );
        this.suggestions.update((list) => list.filter((e) => e.id !== marked.id));
        this.addingId.set(null);
        this.query.set('');
        this.suggestions.set([]);
      },
      error: (err: { error?: { message?: string } }) => {
        this.addingId.set(null);
        this.error.set(err?.error?.message ?? 'Failed to add employee to Alarm.');
      },
    });
  }

  protected remove(employee: Employee): void {
    if (!confirm(`Remove ${employee.name} from Alarm? They can still tap as usual.`)) {
      return;
    }
    this.error.set(null);
    this.removingId.set(employee.id);
    this.api.alarmUnmark(employee.id).subscribe({
      next: () => {
        this.employees.update((list) => list.filter((e) => e.id !== employee.id));
        this.removingId.set(null);
      },
      error: (err: { error?: { message?: string } }) => {
        this.removingId.set(null);
        this.error.set(err?.error?.message ?? 'Failed to remove employee from Alarm.');
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
    this.api.search(term, 8).subscribe({
      next: (items) => {
        const marked = new Set(this.employees().map((e) => e.id));
        this.suggestions.set(items.filter((e) => !e.alarmMarked && !marked.has(e.id)));
        this.searching.set(false);
      },
      error: () => {
        this.searching.set(false);
        this.suggestions.set([]);
      },
    });
  }
}

function byName(a: Employee, b: Employee): number {
  return a.name.localeCompare(b.name);
}
