import { DatePipe, DecimalPipe } from '@angular/common';
import { isPlatformBrowser } from '@angular/common';
import {
  Component,
  OnDestroy,
  PLATFORM_ID,
  computed,
  inject,
  signal,
} from '@angular/core';
import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  lucideActivity,
  lucideBriefcase,
  lucideDoorOpen,
  lucideExpand,
  lucideGraduationCap,
  lucideKeyRound,
  lucideLogOut,
  lucideTriangleAlert,
  lucideX,
} from '@ng-icons/lucide';
import { HlmAvatarImports } from '@spartan-ng/helm/avatar';
import { HlmDialogService } from '@spartan-ng/helm/dialog';
import { Subject, Subscription, catchError, debounceTime, filter, forkJoin, of } from 'rxjs';
import {
  AttendanceApiService,
  type AttendanceDepartmentCount,
  type AttendanceHourCount,
  type AttendanceSummary,
  type PersonType,
  type TapResponse,
} from '../../core/attendance/attendance-api.service';
import { AlertSoundService } from '../../core/alert-sound.service';
import { AuthService } from '../../core/auth/auth.service';
import { FullscreenService } from '../../core/fullscreen.service';
import { NotificationService } from '../../core/notifications/notification.service';
import { studentPhotoUrl } from '../../core/students/student-photo.util';
import { TapErrorLogsApiService } from '../../core/tap-errors/tap-error-logs-api.service';
import { ServerClockService } from '../../core/time/server-clock.service';
import { ChangePasswordDialog } from '../../shared/change-password/change-password-dialog';

const EMPTY_SUMMARY: AttendanceSummary = {
  uniquePeople: 0,
  completeDays: 0,
  openDays: 0,
  totalTaps: 0,
  currentlyIn: 0,
};

interface HourBar {
  hour: number;
  label: string;
  total: number;
}

interface TapErrorPayload {
  identifier?: string | null;
  location?: string | null;
  tappedAt?: string | null;
}

interface TapErrorAlert {
  id: number;
  identifier: string;
  location: string;
  time: Date;
}

@Component({
  selector: 'app-monitor',
  imports: [DatePipe, DecimalPipe, NgIcon, HlmAvatarImports],
  viewProviders: [
    provideIcons({
      lucideActivity,
      lucideBriefcase,
      lucideDoorOpen,
      lucideExpand,
      lucideGraduationCap,
      lucideKeyRound,
      lucideLogOut,
      lucideTriangleAlert,
      lucideX,
    }),
  ],
  templateUrl: './monitor.html',
  styles: `
    @keyframes spotlight-in {
      from {
        opacity: 0;
        transform: translateY(14px) scale(0.97);
      }
      to {
        opacity: 1;
        transform: none;
      }
    }

    @keyframes feed-in {
      from {
        opacity: 0;
        transform: translateX(-12px);
      }
      to {
        opacity: 1;
        transform: none;
      }
    }

    @keyframes alert-in {
      from {
        opacity: 0;
        transform: translateX(24px) scale(0.96);
      }
      to {
        opacity: 1;
        transform: none;
      }
    }

    .alert-card {
      animation: alert-in 0.35s cubic-bezier(0.22, 1, 0.36, 1) both;
    }

    .spotlight-card {
      animation: spotlight-in 0.45s cubic-bezier(0.22, 1, 0.36, 1) both;
    }

    .feed-row {
      animation: feed-in 0.4s cubic-bezier(0.22, 1, 0.36, 1) both;
    }

    @media (prefers-reduced-motion: reduce) {
      .spotlight-card,
      .feed-row,
      .alert-card {
        animation: none;
      }
    }

    .monitor-toolbar {
      isolation: isolate;
    }

    .monitor-action-btn {
      display: grid;
      min-height: 2.75rem;
      min-width: 2.75rem;
      place-items: center;
      border-radius: 0.625rem;
      color: rgb(148 163 184);
      touch-action: manipulation;
      -webkit-tap-highlight-color: transparent;
      transition:
        background-color 0.15s ease,
        color 0.15s ease;
    }

    .monitor-action-btn:hover {
      background-color: rgb(30 41 59);
      color: rgb(255 255 255);
    }

    .monitor-action-btn:disabled {
      opacity: 0.5;
      pointer-events: none;
    }

    .monitor-action-btn--danger:hover {
      color: rgb(248 113 113);
    }

    .recent-tappers-scroll {
      scrollbar-width: thin;
      scrollbar-color: transparent transparent;
    }

    .recent-tappers-scroll:hover,
    .recent-tappers-scroll:focus-within,
    .recent-tappers-scroll:active {
      scrollbar-color: rgb(71 85 105 / 0.9) transparent;
    }

    .recent-tappers-scroll::-webkit-scrollbar {
      width: 6px;
    }

    .recent-tappers-scroll::-webkit-scrollbar-track {
      background: transparent;
    }

    .recent-tappers-scroll::-webkit-scrollbar-thumb {
      border-radius: 9999px;
      background-color: transparent;
    }

    .recent-tappers-scroll:hover::-webkit-scrollbar-thumb,
    .recent-tappers-scroll:focus-within::-webkit-scrollbar-thumb,
    .recent-tappers-scroll:active::-webkit-scrollbar-thumb {
      background-color: rgb(71 85 105 / 0.9);
    }
  `,
  host: { class: 'block h-dvh w-full overflow-hidden' },
})
export class Monitor implements OnDestroy {
  private static readonly FEED_LIMIT = 8;
  private static readonly POLL_MS = 60_000;

  private readonly attendanceApi = inject(AttendanceApiService);
  private readonly tapErrorApi = inject(TapErrorLogsApiService);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(HlmDialogService);
  private readonly alertSound = inject(AlertSoundService);
  private readonly fullscreen = inject(FullscreenService);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly serverClock = inject(ServerClockService);
  protected readonly notifications = inject(NotificationService);

  protected readonly now = this.serverClock.now;
  protected readonly clockTz = this.serverClock.datePipeTimezone;
  protected readonly isFullscreen = signal(false);
  protected readonly loggingOut = signal(false);

  protected readonly taps = signal<TapResponse[]>([]);
  protected readonly tapErrors = signal<TapErrorAlert[]>([]);
  private nextAlertId = 1;
  private readonly alertTimers = new Set<ReturnType<typeof setTimeout>>();
  protected readonly studentSummary = signal<AttendanceSummary>(EMPTY_SUMMARY);
  protected readonly employeeSummary = signal<AttendanceSummary>(EMPTY_SUMMARY);
  protected readonly studentDepts = signal<AttendanceDepartmentCount[]>([]);
  protected readonly employeeDepts = signal<AttendanceDepartmentCount[]>([]);
  protected readonly hours = signal<AttendanceHourCount[]>([]);

  protected readonly spotlight = computed(() => this.taps()[0] ?? null);
  protected readonly feed = computed(() => this.taps().slice(1, Monitor.FEED_LIMIT));

  protected readonly totalTaps = computed(
    () => this.studentSummary().totalTaps + this.employeeSummary().totalTaps,
  );
  protected readonly studentsInside = computed(() => this.studentSummary().currentlyIn);
  protected readonly employeesInside = computed(() => this.employeeSummary().currentlyIn);
  protected readonly rfidErrorCount = signal(0);

  /** Continuous hour range from first to last activity, minimum 06:00–18:00. */
  protected readonly hourBars = computed<HourBar[]>(() => {
    const data = new Map(this.hours().map((h) => [h.hour, h.timeIn + h.timeOut]));
    const active = [...data.keys()];
    const start = Math.min(6, ...(active.length ? [Math.min(...active)] : []));
    const end = Math.max(18, ...(active.length ? [Math.max(...active)] : []));
    const bars: HourBar[] = [];
    for (let hour = start; hour <= end; hour++) {
      bars.push({ hour, label: this.hourLabel(hour), total: data.get(hour) ?? 0 });
    }
    return bars;
  });

  protected readonly hourMax = computed(() =>
    Math.max(...this.hourBars().map((b) => b.total), 1),
  );

  protected readonly topStudentDepts = computed(() => this.studentDepts().slice(0, 5));
  protected readonly topEmployeeDepts = computed(() => this.employeeDepts().slice(0, 5));
  protected readonly studentDeptMax = computed(() =>
    Math.max(...this.topStudentDepts().map((d) => d.count), 1),
  );
  protected readonly employeeDeptMax = computed(() =>
    Math.max(...this.topEmployeeDepts().map((d) => d.count), 1),
  );

  private readonly refresh = new Subject<void>();
  private readonly subs: Subscription[] = [];
  private pollTimer?: ReturnType<typeof setInterval>;
  private readonly onFullscreenChange = () =>
    this.isFullscreen.set(!!document.fullscreenElement);

  constructor() {
    this.loadAll();

    this.subs.push(
      this.notifications.events$
        .pipe(filter((e) => e.type === 'ATTENDANCE_TAP'))
        .subscribe((event) => {
          const tap = event.payload as TapResponse | undefined;
          if (!tap?.attendanceId) {
            return;
          }
          this.taps.update((list) =>
            [tap, ...list.filter((t) => t.attendanceId !== tap.attendanceId)].slice(
              0,
              Monitor.FEED_LIMIT + 1,
            ),
          );
          this.refresh.next();
        }),
    );

    this.subs.push(
      this.notifications.events$
        .pipe(filter((e) => e.type === 'ATTENDANCE_TAP_ERROR'))
        .subscribe((event) => {
          const payload = (event.payload ?? {}) as TapErrorPayload;
          this.pushTapError(payload);
          this.rfidErrorCount.update((n) => n + 1);
        }),
    );

    this.subs.push(
      this.refresh.pipe(debounceTime(600)).subscribe(() => this.loadStats()),
    );

    if (isPlatformBrowser(this.platformId)) {
      this.pollTimer = setInterval(() => this.loadAll(), Monitor.POLL_MS);
      this.isFullscreen.set(!!document.fullscreenElement);
      document.addEventListener('fullscreenchange', this.onFullscreenChange);
      // Auto-fullscreen; falls back to the on-screen button when the browser
      // requires a fresh user gesture.
      void this.fullscreen.enter().then(() => this.onFullscreenChange());
    }
  }

  ngOnDestroy(): void {
    this.subs.forEach((s) => s.unsubscribe());
    if (this.pollTimer) {
      clearInterval(this.pollTimer);
    }
    this.alertTimers.forEach((t) => clearTimeout(t));
    this.alertTimers.clear();
    if (isPlatformBrowser(this.platformId)) {
      document.removeEventListener('fullscreenchange', this.onFullscreenChange);
    }
  }

  protected dismissTapError(id: number): void {
    this.tapErrors.update((list) => list.filter((a) => a.id !== id));
  }

  private pushTapError(payload: TapErrorPayload): void {
    const alert: TapErrorAlert = {
      id: this.nextAlertId++,
      identifier: payload.identifier?.trim() || 'Unknown ID',
      location: payload.location?.trim() || 'Unknown gate',
      time: payload.tappedAt ? new Date(payload.tappedAt) : this.serverClock.now(),
    };
    // Keep at most 4 stacked alerts; each dismisses itself after 12s.
    this.tapErrors.update((list) => [alert, ...list].slice(0, 4));
    this.alertSound.playError();
    const timer = setTimeout(() => {
      this.dismissTapError(alert.id);
      this.alertTimers.delete(timer);
    }, 12_000);
    this.alertTimers.add(timer);
  }

  protected enterFullscreen(): void {
    void this.fullscreen.enter().then(() => this.onFullscreenChange());
  }

  protected openChangePassword(): void {
    ChangePasswordDialog.open(this.dialog);
  }

  protected logout(): void {
    this.loggingOut.set(true);
    void this.fullscreen.exit();
    this.auth.logout().subscribe({
      next: () => this.loggingOut.set(false),
      error: () => this.loggingOut.set(false),
    });
  }

  protected tapTime(tap: TapResponse): string {
    return tap.action === 'TIME_OUT' && tap.timeOut ? tap.timeOut : tap.timeIn;
  }

  protected personName(tap: TapResponse): string {
    return tap.student?.name ?? tap.employee?.name ?? '';
  }

  protected personPhoto(tap: TapResponse): string | null {
    return studentPhotoUrl(tap.student?.photo ?? tap.employee?.photo ?? null);
  }

  protected personSub(tap: TapResponse): string {
    if (tap.student) {
      return [tap.student.studentNo, tap.student.department].filter(Boolean).join(' · ');
    }
    return [tap.employee?.department, tap.employee?.position].filter(Boolean).join(' · ');
  }

  protected personKind(tap: TapResponse): string {
    return tap.student ? 'Student' : 'Employee';
  }

  protected initials(name: string): string {
    const parts = name.replace(',', '').trim().split(/\s+/);
    return ((parts[0]?.[0] ?? '') + (parts[1]?.[0] ?? '')).toUpperCase();
  }

  protected barPct(value: number, max: number): number {
    return Math.round((value / max) * 100);
  }

  protected hourLabel(hour: number): string {
    const h12 = hour % 12 === 0 ? 12 : hour % 12;
    return `${h12}${hour < 12 ? 'am' : 'pm'}`;
  }

  private loadAll(): void {
    this.attendanceApi.recent(Monitor.FEED_LIMIT + 1).subscribe({
      next: (taps) => this.taps.set(taps),
      error: () => undefined,
    });
    this.loadStats();
  }

  private loadStats(): void {
    const today = this.serverClock.todayIso();
    const summaryFor = (personType: PersonType) =>
      this.attendanceApi
        .summary({ personType, startDate: today, endDate: today })
        .pipe(catchError(() => of(EMPTY_SUMMARY)));
    const deptsFor = (personType: PersonType) =>
      this.attendanceApi
        .byDepartment(personType, today, today)
        .pipe(catchError(() => of([] as AttendanceDepartmentCount[])));

    forkJoin({
      students: summaryFor('STUDENT'),
      employees: summaryFor('EMPLOYEE'),
      studentDepts: deptsFor('STUDENT'),
      employeeDepts: deptsFor('EMPLOYEE'),
      hours: this.attendanceApi.byHour().pipe(catchError(() => of([] as AttendanceHourCount[]))),
      rfidErrors: this.tapErrorApi.count(today).pipe(catchError(() => of(0))),
    }).subscribe(({ students, employees, studentDepts, employeeDepts, hours, rfidErrors }) => {
      this.studentSummary.set(students);
      this.employeeSummary.set(employees);
      this.studentDepts.set(studentDepts);
      this.employeeDepts.set(employeeDepts);
      this.hours.set(hours);
      this.rfidErrorCount.set(rfidErrors);
    });
  }
}
