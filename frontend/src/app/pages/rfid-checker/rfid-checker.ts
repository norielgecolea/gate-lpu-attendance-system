import {
  AfterViewInit,
  Component,
  ElementRef,
  HostListener,
  OnDestroy,
  ViewChild,
  computed,
  inject,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  lucideBriefcase,
  lucideCircleAlert,
  lucideGraduationCap,
  lucideIdCard,
  lucideScanBarcode,
  lucideSquarePen,
} from '@ng-icons/lucide';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmDialogService } from '@spartan-ng/helm/dialog';
import { filter, take } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import {
  RfidApiService,
  type RfidLookupResult,
  type StudentCreatedAudit,
} from '../../core/rfid/rfid-api.service';
import { applyScanInput } from '../../core/rfid/wedge-scan-buffer';
import { studentPhotoUrl } from '../../core/students/student-photo.util';
import { PhotoPreview } from '../../shared/photo-preview/photo-preview.directive';
import {
  StudentFormDialog,
  type StudentFormResult,
} from '../students/student-form-dialog';
import { type Student, StudentsStore } from '../students/students.store';
import {
  EmployeeFormDialog,
  type EmployeeFormResult,
} from '../employees/employee-form-dialog';
import { type Employee, EmployeesStore } from '../employees/employees.store';

@Component({
  selector: 'app-rfid-checker',
  imports: [RouterLink, NgIcon, HlmButton, PhotoPreview],
  viewProviders: [
    provideIcons({
      lucideScanBarcode,
      lucideIdCard,
      lucideGraduationCap,
      lucideBriefcase,
      lucideCircleAlert,
      lucideSquarePen,
    }),
  ],
  templateUrl: './rfid-checker.html',
  host: { class: 'block h-full' },
})
export class RfidChecker implements AfterViewInit, OnDestroy {
  @ViewChild('scanInput') private readonly scanInput?: ElementRef<HTMLInputElement>;

  private readonly api = inject(RfidApiService);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(HlmDialogService);
  private readonly studentsStore = inject(StudentsStore);
  private readonly employeesStore = inject(EmployeesStore);
  private focusTimer?: ReturnType<typeof setInterval>;
  private lastScanInputAt = 0;
  private scanBuffer = '';

  protected readonly loading = signal(false);
  protected readonly result = signal<RfidLookupResult | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly lastScanned = signal<string | null>(null);

  protected readonly hint = computed(() => {
    const role = this.auth.user()?.role;
    if (role === 'OSAS') {
      return 'Tap a student ID on the reader to view the record.';
    }
    if (role === 'HR') {
      return 'Tap an employee ID on the reader to view the record.';
    }
    return 'Tap a student or employee ID on the reader to view the record.';
  });

  ngAfterViewInit(): void {
    this.focusInput();
    this.focusTimer = setInterval(() => this.focusInput(), 1500);
  }

  ngOnDestroy(): void {
    if (this.focusTimer) {
      clearInterval(this.focusTimer);
    }
  }

  @HostListener('document:click')
  protected onDocumentClick(): void {
    this.focusInput();
  }

  /** Drop leftover keys when a new RFID burst starts after a pause. */
  protected onScanInput(event: Event): void {
    const el = event.target as HTMLInputElement;
    const now = performance.now();
    const elapsed = this.lastScanInputAt === 0 ? Number.POSITIVE_INFINITY : now - this.lastScanInputAt;
    this.lastScanInputAt = now;
    const cleaned = applyScanInput(this.scanBuffer, el.value, elapsed);
    this.scanBuffer = cleaned;
    if (el.value !== cleaned) {
      el.value = cleaned;
    }
  }

  protected submit(): void {
    const el = this.scanInput?.nativeElement;
    const value = (el?.value ?? this.scanBuffer).trim();
    if (!value || this.loading()) {
      return;
    }

    this.scanBuffer = '';
    this.lastScanInputAt = 0;
    if (el) {
      el.value = '';
    }
    this.focusInput();

    this.loading.set(true);
    this.error.set(null);
    this.result.set(null);
    this.lastScanned.set(value);

    this.api.lookup(value).subscribe({
      next: (res) => {
        this.loading.set(false);
        this.result.set(res);
        if (!res.found) {
          this.error.set(res.message ?? 'No matching record found.');
        }
        this.focusInput();
      },
      error: (err: { error?: { message?: string } }) => {
        this.loading.set(false);
        this.error.set(err?.error?.message ?? 'Lookup failed. Please try again.');
        this.focusInput();
      },
    });
  }

  protected focusInput(): void {
    const el = this.scanInput?.nativeElement;
    if (!el || this.loading()) {
      return;
    }
    if (document.activeElement !== el) {
      el.focus({ preventScroll: true });
    }
  }

  protected photoSrc(photo: string | null | undefined): string | null {
    return studentPhotoUrl(photo);
  }

  protected canEditStudent(): boolean {
    const role = this.auth.user()?.role;
    return role === 'SUPERADMIN' || role === 'OSAS';
  }

  protected canEditEmployee(): boolean {
    const role = this.auth.user()?.role;
    return role === 'SUPERADMIN' || role === 'HR';
  }

  protected openStudentEdit(student: Student, audit: StudentCreatedAudit | null | undefined): void {
    const ref = this.dialog.open(StudentFormDialog, {
      context: { mode: 'edit', student, createdAuditLabel: this.createdLabel(audit) },
      contentClass: 'person-form-dialog',
    });
    ref.closed$
      .pipe(
        take(1),
        filter((form): form is StudentFormResult => !!form),
      )
      .subscribe((form) => {
        this.error.set(null);
        this.studentsStore.saveFromForm('edit', form, student.id).subscribe({
          next: (updated) => this.replaceStudentResult(updated),
          error: (err: { error?: { message?: string } }) =>
            this.error.set(err?.error?.message ?? 'Failed to save student record.'),
        });
      });
  }

  protected openEmployeeEdit(employee: Employee, audit: StudentCreatedAudit | null | undefined): void {
    const ref = this.dialog.open(EmployeeFormDialog, {
      context: { mode: 'edit', employee, createdAuditLabel: this.createdLabel(audit) },
      contentClass: 'person-form-dialog',
    });
    ref.closed$
      .pipe(
        take(1),
        filter((form): form is EmployeeFormResult => !!form),
      )
      .subscribe((form) => {
        this.error.set(null);
        this.employeesStore.saveFromForm('edit', form, employee.id).subscribe({
          next: (updated) => this.replaceEmployeeResult(updated),
          error: (err: { error?: { message?: string } }) =>
            this.error.set(err?.error?.message ?? 'Failed to save employee record.'),
        });
      });
  }

  protected initials(name: string): string {
    const parts = name.replace(',', '').trim().split(/\s+/);
    return ((parts[0]?.[0] ?? '') + (parts[1]?.[0] ?? '')).toUpperCase();
  }

  protected studentCreatedLabel(audit: StudentCreatedAudit | null | undefined): string {
    return this.createdLabel(audit);
  }

  protected createdLabel(audit: StudentCreatedAudit | null | undefined): string {
    if (!audit) {
      return 'Added by Unknown creator / legacy record';
    }
    const actor = audit.actorUsername?.trim() || 'Unknown creator';
    const createdAt = new Date(audit.createdAt);
    const when = Number.isNaN(createdAt.getTime()) ? audit.createdAt : createdAt.toLocaleString();
    return `Added by ${actor} on ${when}`;
  }

  private replaceStudentResult(student: Student): void {
    this.result.update((current) => (current ? { ...current, student } : current));
  }

  private replaceEmployeeResult(employee: Employee): void {
    this.result.update((current) => (current ? { ...current, employee } : current));
  }
}
