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
} from '@ng-icons/lucide';
import { HlmButton } from '@spartan-ng/helm/button';
import { AuthService } from '../../core/auth/auth.service';
import {
  RfidApiService,
  type RfidLookupResult,
  type StudentCreatedAudit,
} from '../../core/rfid/rfid-api.service';
import { studentPhotoUrl } from '../../core/students/student-photo.util';

@Component({
  selector: 'app-rfid-checker',
  imports: [RouterLink, NgIcon, HlmButton],
  viewProviders: [
    provideIcons({
      lucideScanBarcode,
      lucideIdCard,
      lucideGraduationCap,
      lucideBriefcase,
      lucideCircleAlert,
    }),
  ],
  templateUrl: './rfid-checker.html',
  host: { class: 'block h-full' },
})
export class RfidChecker implements AfterViewInit, OnDestroy {
  @ViewChild('scanInput') private readonly scanInput?: ElementRef<HTMLInputElement>;

  private readonly api = inject(RfidApiService);
  private readonly auth = inject(AuthService);
  private focusTimer?: ReturnType<typeof setInterval>;

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

  protected submit(): void {
    const el = this.scanInput?.nativeElement;
    const value = (el?.value ?? '').trim();
    if (!value || this.loading()) {
      return;
    }

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
}
