import { Component, ElementRef, ViewChild, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  lucideCircleCheck,
  lucideScanBarcode,
  lucideSearch,
} from '@ng-icons/lucide';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmInput } from '@spartan-ng/helm/input';
import { EmployeesApiService } from '../../core/employees/employees-api.service';
import { RfidApiService } from '../../core/rfid/rfid-api.service';
import { studentPhotoUrl } from '../../core/students/student-photo.util';
import { PhotoPreview } from '../../shared/photo-preview/photo-preview.directive';
import type { Employee } from './employees.store';

@Component({
  selector: 'app-employee-rfid-registration',
  imports: [FormsModule, NgIcon, HlmButton, HlmInput, PhotoPreview],
  viewProviders: [
    provideIcons({
      lucideSearch,
      lucideScanBarcode,
      lucideCircleCheck,
    }),
  ],
  templateUrl: './employee-rfid-registration.html',
  host: { class: 'block h-full' },
})
export class EmployeeRfidRegistration {
  @ViewChild('rfidInput') private readonly rfidInput?: ElementRef<HTMLInputElement>;

  private readonly api = inject(EmployeesApiService);
  private readonly rfidApi = inject(RfidApiService);

  protected readonly employees = signal<Employee[]>([]);
  protected readonly loading = signal(true);
  protected readonly numberInput = signal('');
  protected readonly record = signal<Employee | null>(null);
  protected readonly rfidValue = signal('');
  protected readonly saving = signal(false);
  protected readonly success = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  constructor() {
    this.api.list().subscribe({
      next: (employees) => {
        this.employees.set(employees);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Failed to load employees.');
      },
    });
  }

  protected find(): void {
    const term = this.numberInput().trim().toLowerCase();
    this.success.set(null);
    this.error.set(null);
    this.record.set(null);
    this.rfidValue.set('');
    if (!term) {
      return;
    }
    const match = this.employees().find((e) => e.employeeNo.toLowerCase() === term);
    if (!match) {
      this.error.set(`No employee found with number "${this.numberInput().trim()}".`);
      return;
    }
    this.record.set(match);
    setTimeout(() => this.rfidInput?.nativeElement.focus());
  }

  /** RFID wedge scanners type the card number and press Enter — this handles both scan and manual entry. */
  protected register(): void {
    const employee = this.record();
    const rfid = this.rfidValue().trim();
    if (!employee || !rfid || this.saving()) {
      return;
    }
    this.error.set(null);
    this.saving.set(true);
    this.rfidApi.checkDuplicate(rfid, 'EMPLOYEE', employee.id).subscribe({
      next: (conflict) => {
        if (conflict) {
          this.saving.set(false);
          this.error.set(conflict);
          this.rfidValue.set('');
          setTimeout(() => this.rfidInput?.nativeElement.focus());
          return;
        }
        this.api
          .update(employee.id, {
            name: employee.name,
            employeeNo: employee.employeeNo,
            photo: employee.photo ?? null,
            rfid,
            birthdate: employee.birthdate ?? null,
            lpuEmail: employee.lpuEmail ?? null,
            department: employee.department,
            position: employee.position,
          })
          .subscribe({
            next: (updated) => {
              this.saving.set(false);
              this.employees.update((list) => list.map((e) => (e.id === updated.id ? updated : e)));
              this.record.set(updated);
              this.rfidValue.set('');
              this.success.set(rfid);
            },
            error: (err: { error?: { message?: string } }) => {
              this.saving.set(false);
              this.error.set(err?.error?.message ?? 'Failed to register RFID.');
            },
          });
      },
      error: () => {
        this.saving.set(false);
        this.error.set('Failed to verify RFID. Please try again.');
      },
    });
  }

  protected reset(): void {
    this.numberInput.set('');
    this.record.set(null);
    this.rfidValue.set('');
    this.success.set(null);
    this.error.set(null);
  }

  protected initials(name: string): string {
    const parts = name.replace(',', '').trim().split(/\s+/);
    return ((parts[0]?.[0] ?? '') + (parts[1]?.[0] ?? '')).toUpperCase();
  }

  protected photoSrc(photo: string | null | undefined): string | null {
    return studentPhotoUrl(photo);
  }
}
