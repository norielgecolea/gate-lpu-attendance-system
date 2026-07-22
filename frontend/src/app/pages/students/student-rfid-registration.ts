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
import { RfidApiService } from '../../core/rfid/rfid-api.service';
import { studentPhotoUrl } from '../../core/students/student-photo.util';
import { StudentsApiService } from '../../core/students/students-api.service';
import type { Student } from './students.store';

@Component({
  selector: 'app-student-rfid-registration',
  imports: [FormsModule, NgIcon, HlmButton, HlmInput],
  viewProviders: [
    provideIcons({
      lucideSearch,
      lucideScanBarcode,
      lucideCircleCheck,
    }),
  ],
  templateUrl: './student-rfid-registration.html',
  host: { class: 'block h-full' },
})
export class StudentRfidRegistration {
  @ViewChild('rfidInput') private readonly rfidInput?: ElementRef<HTMLInputElement>;

  private readonly api = inject(StudentsApiService);
  private readonly rfidApi = inject(RfidApiService);

  protected readonly students = signal<Student[]>([]);
  protected readonly loading = signal(true);
  protected readonly numberInput = signal('');
  protected readonly record = signal<Student | null>(null);
  protected readonly rfidValue = signal('');
  protected readonly saving = signal(false);
  protected readonly success = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  constructor() {
    this.api.list().subscribe({
      next: (students) => {
        this.students.set(students);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Failed to load students.');
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
    const match = this.students().find((s) => s.studentNo.toLowerCase() === term);
    if (!match) {
      this.error.set(`No student found with number "${this.numberInput().trim()}".`);
      return;
    }
    this.record.set(match);
    setTimeout(() => this.rfidInput?.nativeElement.focus());
  }

  /** RFID wedge scanners type the card number and press Enter — this handles both scan and manual entry. */
  protected register(): void {
    const student = this.record();
    const rfid = this.rfidValue().trim();
    if (!student || !rfid || this.saving()) {
      return;
    }
    this.error.set(null);
    this.saving.set(true);
    this.rfidApi.checkDuplicate(rfid, 'STUDENT', student.id).subscribe({
      next: (conflict) => {
        if (conflict) {
          this.saving.set(false);
          this.error.set(conflict);
          this.rfidValue.set('');
          setTimeout(() => this.rfidInput?.nativeElement.focus());
          return;
        }
        this.api
          .update(student.id, {
            name: student.name,
            studentNo: student.studentNo,
            photo: student.photo ?? null,
            rfid,
            birthdate: student.birthdate ?? null,
            department: student.department,
            course: student.course,
            school: student.school,
          })
          .subscribe({
            next: (updated) => {
              this.saving.set(false);
              this.students.update((list) => list.map((s) => (s.id === updated.id ? updated : s)));
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
