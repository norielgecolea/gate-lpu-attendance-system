import { DatePipe } from '@angular/common';
import {
  AfterViewInit,
  Component,
  ElementRef,
  OnInit,
  ViewChild,
  inject,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideCircleAlert, lucideScanBarcode } from '@ng-icons/lucide';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmInput } from '@spartan-ng/helm/input';
import {
  AttendanceApiService,
  type TapResponse,
} from '../../core/attendance/attendance-api.service';
import { applyScanInput } from '../../core/rfid/wedge-scan-buffer';
import { studentPhotoUrl } from '../../core/students/student-photo.util';
import { UsersApiService } from '../../core/users/users-api.service';

const LOCATION_KEY = 'admin-kiosk-location';

@Component({
  selector: 'app-admin-kiosk',
  imports: [DatePipe, FormsModule, NgIcon, HlmButton, HlmInput],
  viewProviders: [provideIcons({ lucideScanBarcode, lucideCircleAlert })],
  templateUrl: './admin-kiosk.html',
  host: { class: 'block h-full' },
})
export class AdminKiosk implements OnInit, AfterViewInit {
  @ViewChild('idInput') private readonly idInput?: ElementRef<HTMLInputElement>;

  private readonly usersApi = inject(UsersApiService);
  private readonly attendanceApi = inject(AttendanceApiService);

  protected readonly locations = signal<string[]>([]);
  protected readonly locationsLoading = signal(true);
  protected readonly locationsError = signal<string | null>(null);
  protected readonly location = signal(sessionLocation());
  protected readonly identifier = signal('');
  protected readonly tapping = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly result = signal<TapResponse | null>(null);

  private lastScanInputAt = 0;

  ngOnInit(): void {
    this.usersApi.list().subscribe({
      next: (users) => {
        const gates = [
          ...new Set(
            users
              .filter((user) => user.role === 'GUARD' && user.active && user.location?.trim())
              .map((user) => user.location!.trim()),
          ),
        ].sort((a, b) => a.localeCompare(b));
        this.locations.set(gates);
        if (!gates.includes(this.location())) {
          this.location.set(gates[0] ?? '');
        }
        this.persistLocation();
        this.locationsLoading.set(false);
      },
      error: (err: { error?: { message?: string } }) => {
        this.locationsLoading.set(false);
        this.locationsError.set(err?.error?.message ?? 'Failed to load guard locations');
      },
    });
  }

  ngAfterViewInit(): void {
    this.focusInput();
  }

  protected onLocationChange(value: string): void {
    this.location.set(value);
    this.persistLocation();
    this.focusInput();
  }

  protected onScanInput(event: Event): void {
    const el = event.target as HTMLInputElement;
    const now = performance.now();
    const elapsed = this.lastScanInputAt === 0 ? Number.POSITIVE_INFINITY : now - this.lastScanInputAt;
    this.lastScanInputAt = now;
    const cleaned = applyScanInput(this.identifier(), el.value, elapsed);
    if (el.value !== cleaned) {
      el.value = cleaned;
    }
    this.identifier.set(cleaned);
  }

  protected submit(): void {
    const gate = this.location().trim();
    const el = this.idInput?.nativeElement;
    const value = (el?.value ?? this.identifier()).trim();
    if (!gate || !value || this.tapping()) {
      return;
    }

    this.identifier.set('');
    this.lastScanInputAt = 0;
    if (el) {
      el.value = '';
    }
    this.tapping.set(true);
    this.error.set(null);

    this.attendanceApi.tap(value, gate).subscribe({
      next: (tap) => {
        this.tapping.set(false);
        this.result.set(tap);
        this.focusInput();
      },
      error: (err: { status?: number; error?: { message?: string } | string }) => {
        this.tapping.set(false);
        this.result.set(null);
        const body = err?.error;
        const apiMessage = typeof body === 'string' ? body : (body?.message ?? '');
        this.error.set(
          err?.status === 404 || /record\s*not\s*found/i.test(apiMessage)
            ? 'Record Not Found'
            : apiMessage || 'Tap failed. Please try again.',
        );
        this.focusInput();
      },
    });
  }

  protected personName(tap: TapResponse): string {
    return tap.student?.name ?? tap.employee?.name ?? '';
  }

  protected personNo(tap: TapResponse): string {
    return tap.student?.studentNo ?? tap.employee?.employeeNo ?? '';
  }

  protected photoUrl(tap: TapResponse): string | null {
    return studentPhotoUrl(tap.student?.photo ?? tap.employee?.photo ?? null);
  }

  protected actionLabel(action: string): string {
    return action === 'TIME_OUT' ? 'Time out' : 'Time in';
  }

  private persistLocation(): void {
    const value = this.location().trim();
    if (value) {
      sessionStorage.setItem(LOCATION_KEY, value);
    }
  }

  private focusInput(): void {
    queueMicrotask(() => this.idInput?.nativeElement.focus());
  }
}

function sessionLocation(): string {
  try {
    return sessionStorage.getItem(LOCATION_KEY) ?? '';
  } catch {
    return '';
  }
}
