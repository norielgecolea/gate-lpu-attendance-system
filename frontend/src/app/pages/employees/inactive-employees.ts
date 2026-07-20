import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideRotateCcw, lucideSearch, lucideTrash2 } from '@ng-icons/lucide';
import { HlmAvatarImports } from '@spartan-ng/helm/avatar';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmInput } from '@spartan-ng/helm/input';
import { HlmTableImports } from '@spartan-ng/helm/table';
import { EmployeesApiService } from '../../core/employees/employees-api.service';
import { studentPhotoUrl } from '../../core/students/student-photo.util';
import type { Employee } from './employees.store';

@Component({
  selector: 'app-inactive-employees',
  imports: [FormsModule, NgIcon, HlmButton, HlmInput, HlmTableImports, HlmAvatarImports],
  viewProviders: [provideIcons({ lucideSearch, lucideRotateCcw, lucideTrash2 })],
  templateUrl: './inactive-employees.html',
  host: { class: 'flex h-full flex-col' },
})
export class InactiveEmployees {
  private readonly api = inject(EmployeesApiService);

  protected readonly employees = signal<Employee[]>([]);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly restoringId = signal<string | null>(null);
  protected readonly deletingId = signal<string | null>(null);
  protected readonly filter = signal('');

  protected readonly filtered = computed(() => {
    const term = this.filter().trim().toLowerCase();
    if (!term) {
      return this.employees();
    }
    return this.employees().filter((e) =>
      [e.name, e.employeeNo, e.rfid ?? '', e.department, e.position]
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
      next: (employees) => {
        this.employees.set(employees);
        this.loading.set(false);
      },
      error: (err: { error?: { message?: string } }) => {
        this.loading.set(false);
        this.error.set(err?.error?.message ?? 'Failed to load inactive employees');
      },
    });
  }

  protected restore(employee: Employee): void {
    if (!confirm(`Restore employee ${employee.name}?`)) {
      return;
    }
    this.error.set(null);
    this.restoringId.set(employee.id);
    this.api.restore(employee.id).subscribe({
      next: () => {
        this.employees.update((list) => list.filter((e) => e.id !== employee.id));
        this.restoringId.set(null);
      },
      error: (err: { error?: { message?: string } }) => {
        this.restoringId.set(null);
        this.error.set(err?.error?.message ?? 'Failed to restore employee');
      },
    });
  }

  protected permanentlyDelete(employee: Employee): void {
    if (
      !confirm(
        `Permanently delete ${employee.name}? This will also delete all attendance logs and tap events. This action cannot be undone.`,
      )
    ) {
      return;
    }
    this.error.set(null);
    this.deletingId.set(employee.id);
    this.api.permanentlyDelete(employee.id).subscribe({
      next: () => {
        this.employees.update((list) => list.filter((e) => e.id !== employee.id));
        this.deletingId.set(null);
      },
      error: (err: { error?: { message?: string } }) => {
        this.deletingId.set(null);
        this.error.set(err?.error?.message ?? 'Failed to permanently delete employee');
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
