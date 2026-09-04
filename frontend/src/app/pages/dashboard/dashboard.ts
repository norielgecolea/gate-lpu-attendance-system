import { DatePipe, DecimalPipe, NgClass } from '@angular/common';
import { Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  lucideActivity,
  lucideBriefcase,
  lucideCalendarDays,
  lucideDoorOpen,
  lucideGraduationCap,
  lucideIdCard,
  lucideUserRound,
  lucideUsers,
  lucideUserX,
} from '@ng-icons/lucide';
import { HlmAvatarImports } from '@spartan-ng/helm/avatar';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { Subject, Subscription, catchError, debounceTime, filter, forkJoin, of } from 'rxjs';
import {
  AttendanceApiService,
  type AttendanceDepartmentCount,
  type AttendanceSummary,
  type PersonType,
  type TapResponse,
} from '../../core/attendance/attendance-api.service';
import { AuthService } from '../../core/auth/auth.service';
import {
  kioskGroupFromRole,
  KIOSK_GROUP_LABELS,
  isVenueAdmin,
} from '../../core/kiosk/kiosk-group';
import { NotificationService } from '../../core/notifications/notification.service';
import { studentPhotoUrl } from '../../core/students/student-photo.util';
import { PhotoPreview } from '../../shared/photo-preview/photo-preview.directive';
import { StudentsApiService } from '../../core/students/students-api.service';

interface StatCard {
  label: string;
  value: number;
  icon: string;
  tone: 'primary' | 'emerald' | 'amber' | 'rose';
  /** Optional 0-100 coverage shown as a progress bar. */
  percent: number | null;
  caption: string | null;
}

const EMPTY_SUMMARY: AttendanceSummary = {
  uniquePeople: 0,
  completeDays: 0,
  openDays: 0,
  totalTaps: 0,
  currentlyIn: 0,
};

const DONUT_CIRCUMFERENCE = 2 * Math.PI * 42;

@Component({
  selector: 'app-dashboard',
  imports: [DecimalPipe, DatePipe, NgClass, NgIcon, HlmCardImports, HlmAvatarImports, PhotoPreview],
  viewProviders: [
    provideIcons({
      lucideActivity,
      lucideBriefcase,
      lucideCalendarDays,
      lucideDoorOpen,
      lucideGraduationCap,
      lucideIdCard,
      lucideUserRound,
      lucideUsers,
      lucideUserX,
    }),
  ],
  templateUrl: './dashboard.html',
  styles: `
    @keyframes tap-card-in {
      from {
        opacity: 0;
        transform: translateX(-16px) scale(0.94);
      }
      to {
        opacity: 1;
        transform: none;
      }
    }

    @keyframes tap-badge-pop {
      0% {
        transform: scale(0.4);
      }
      60% {
        transform: scale(1.18);
      }
      100% {
        transform: scale(1);
      }
    }

    @keyframes bar-grow-x {
      from {
        transform: scaleX(0);
      }
      to {
        transform: scaleX(1);
      }
    }

    .tap-card {
      animation: tap-card-in 0.5s cubic-bezier(0.22, 1, 0.36, 1) both;
    }

    .tap-card .tap-badge {
      animation: tap-badge-pop 0.45s cubic-bezier(0.34, 1.56, 0.64, 1) 0.15s both;
    }

    .dept-bar {
      transform-origin: left;
      animation: bar-grow-x 0.55s cubic-bezier(0.22, 1, 0.36, 1) both;
    }

    @media (prefers-reduced-motion: reduce) {
      .tap-card,
      .tap-card .tap-badge,
      .dept-bar {
        animation: none;
      }
    }
  `,
})
export class Dashboard implements OnDestroy {
  private static readonly RECENT_LIMIT = 5;

  private readonly attendanceApi = inject(AttendanceApiService);
  private readonly studentsApi = inject(StudentsApiService);
  private readonly auth = inject(AuthService);
  protected readonly notifications = inject(NotificationService);
  protected readonly kioskGroup = kioskGroupFromRole(this.auth.user()?.role);
  protected readonly venueTitle =
    this.kioskGroup === 'LIBRARY'
      ? 'Library Overview'
      : this.kioskGroup === 'OLIVE_HOTEL'
        ? 'Olive Hotel Overview'
        : 'Campus Overview';
  protected readonly venueSubtitle =
    this.kioskGroup === 'MAIN_GATES'
      ? 'Realtime gate activity across LPU-Laguna'
      : `Realtime ${KIOSK_GROUP_LABELS[this.kioskGroup]} attendance`;
  protected readonly showDirectoryStats = !isVenueAdmin(this.auth.user()?.role);
  protected readonly onlineKioskLocations = computed(() =>
    this.notifications.onlineLocationsFor(this.kioskGroup),
  );

  protected readonly recentTaps = signal<TapResponse[]>([]);
  protected readonly tapsLoading = signal(true);

  private readonly activeCount = signal(0);
  private readonly rfidCount = signal(0);
  private readonly inactiveCount = signal(0);

  protected readonly studentDepts = signal<AttendanceDepartmentCount[]>([]);
  protected readonly employeeDepts = signal<AttendanceDepartmentCount[]>([]);
  protected readonly deptsLoading = signal(true);
  private readonly todayStudents = signal<AttendanceSummary>(EMPTY_SUMMARY);
  private readonly todayEmployees = signal<AttendanceSummary>(EMPTY_SUMMARY);

  private readonly wsSub: Subscription;
  private readonly refreshSub: Subscription;
  /** Debounces summary refreshes so a burst of taps triggers a single reload. */
  private readonly presenceRefresh = new Subject<void>();

  protected readonly todayLabel = new Date().toLocaleDateString('en-PH', {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
    year: 'numeric',
    timeZone: 'Asia/Manila',
  });

  protected readonly hero = computed(() => {
    const s = this.todayStudents();
    const e = this.todayEmployees();
    return {
      students: s.uniquePeople,
      employees: e.uniquePeople,
      taps: s.totalTaps + e.totalTaps,
      inside: s.currentlyIn + e.currentlyIn,
    };
  });

  protected readonly stats = computed<StatCard[]>(() => {
    const active = this.activeCount();
    const rfid = this.rfidCount();
    const pct = this.rfidPercent();
    return [
      {
        label: 'Active Students',
        value: active,
        icon: 'lucideUsers',
        tone: 'primary',
        percent: null,
        caption: null,
      },
      {
        label: 'With RFID',
        value: rfid,
        icon: 'lucideIdCard',
        tone: 'emerald',
        percent: pct,
        caption: `${pct}% of active students`,
      },
      {
        label: 'No RFID',
        value: active - rfid,
        icon: 'lucideUserRound',
        tone: 'amber',
        percent: active > 0 ? 100 - pct : null,
        caption: active > 0 ? `${100 - pct}% still unregistered` : null,
      },
      {
        label: 'Inactive Students',
        value: this.inactiveCount(),
        icon: 'lucideUserX',
        tone: 'rose',
        percent: null,
        caption: null,
      },
    ];
  });

  protected readonly withRfid = computed(() => this.rfidCount());
  protected readonly noRfid = computed(() => this.activeCount() - this.rfidCount());

  protected readonly rfidPercent = computed(() => {
    const total = this.activeCount();
    return total > 0 ? Math.round((this.rfidCount() / total) * 100) : 0;
  });

  protected readonly donutDash = computed(() => {
    const filled = (this.rfidPercent() / 100) * DONUT_CIRCUMFERENCE;
    return `${filled.toFixed(2)} ${DONUT_CIRCUMFERENCE.toFixed(2)}`;
  });

  protected readonly studentDeptMax = computed(() =>
    Math.max(...this.studentDepts().map((d) => d.count), 1),
  );

  protected readonly employeeDeptMax = computed(() =>
    Math.max(...this.employeeDepts().map((d) => d.count), 1),
  );

  constructor() {
    this.attendanceApi.recent(Dashboard.RECENT_LIMIT).subscribe({
      next: (taps) => {
        this.recentTaps.set(taps);
        this.tapsLoading.set(false);
      },
      error: () => this.tapsLoading.set(false),
    });

    if (this.showDirectoryStats) {
      this.studentsApi.list().subscribe({
        next: (students) => {
          this.activeCount.set(students.length);
          this.rfidCount.set(students.filter((s) => !!s.rfid).length);
        },
        error: () => undefined,
      });
      this.studentsApi.listInactive().subscribe({
        next: (students) => this.inactiveCount.set(students.length),
        error: () => undefined,
      });
    }

    this.loadTodayPresence();

    // Live updates: a TIME_OUT replaces the existing TIME_IN card for the same log,
    // and every tap also refreshes the hero figures and department charts.
    this.wsSub = this.notifications.events$
      .pipe(filter((e) => e.type === 'ATTENDANCE_TAP'))
      .subscribe((event) => {
        const tap = event.payload as TapResponse | undefined;
        if (!tap?.attendanceId) {
          return;
        }
        if (tap.kioskGroup && tap.kioskGroup !== this.kioskGroup) {
          return;
        }
        this.recentTaps.update((list) =>
          [tap, ...list.filter((t) => t.attendanceId !== tap.attendanceId)].slice(
            0,
            Dashboard.RECENT_LIMIT,
          ),
        );
        this.presenceRefresh.next();
      });

    this.refreshSub = this.presenceRefresh
      .pipe(debounceTime(500))
      .subscribe(() => this.loadTodayPresence());
  }

  ngOnDestroy(): void {
    this.wsSub.unsubscribe();
    this.refreshSub.unsubscribe();
  }

  protected barPct(value: number, max: number): number {
    return Math.round((value / max) * 100);
  }

  protected tapTime(tap: TapResponse): string {
    return tap.action === 'TIME_OUT' && tap.timeOut ? tap.timeOut : tap.timeIn;
  }

  protected initials(name: string): string {
    const parts = name.replace(',', '').trim().split(/\s+/);
    return ((parts[0]?.[0] ?? '') + (parts[1]?.[0] ?? '')).toUpperCase();
  }

  protected photoSrc(photo: string | null | undefined): string | null {
    return studentPhotoUrl(photo);
  }

  protected personName(tap: TapResponse): string {
    return tap.student?.name ?? tap.employee?.name ?? '';
  }

  protected personPhoto(tap: TapResponse): string | null {
    return studentPhotoUrl(tap.student?.photo ?? tap.employee?.photo ?? null);
  }

  /** Students show their number; employees show department · position. */
  protected personSub(tap: TapResponse): string {
    if (tap.student) {
      return tap.student.studentNo;
    }
    return [tap.employee?.department, tap.employee?.position].filter(Boolean).join(' · ');
  }

  protected isStudent(tap: TapResponse): boolean {
    return tap.personType === 'STUDENT' || !!tap.student;
  }

  /** Loads today's summaries and per-department presence for students and employees. */
  private loadTodayPresence(): void {
    const today = this.manilaDate(0);
    const summaryFor = (personType: PersonType) =>
      this.attendanceApi
        .summary({ personType, startDate: today, endDate: today })
        .pipe(catchError(() => of(EMPTY_SUMMARY)));
    const deptsFor = (personType: PersonType) =>
      this.attendanceApi
        .byDepartment(personType, today, today)
        .pipe(catchError(() => of([] as AttendanceDepartmentCount[])));

    forkJoin({
      studentSummary: summaryFor('STUDENT'),
      employeeSummary: summaryFor('EMPLOYEE'),
      studentDepts: deptsFor('STUDENT'),
      employeeDepts: deptsFor('EMPLOYEE'),
    }).subscribe({
      next: ({ studentSummary, employeeSummary, studentDepts, employeeDepts }) => {
        this.todayStudents.set(studentSummary);
        this.todayEmployees.set(employeeSummary);
        this.studentDepts.set(studentDepts);
        this.employeeDepts.set(employeeDepts);
        this.deptsLoading.set(false);
      },
      error: () => this.deptsLoading.set(false),
    });
  }

  private manilaDate(daysAgo: number): string {
    const d = new Date(Date.now() - daysAgo * 86_400_000);
    return d.toLocaleDateString('en-CA', { timeZone: 'Asia/Manila' });
  }
}
