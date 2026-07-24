import { DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  lucideActivity,
  lucideBriefcase,
  lucideCalendarDays,
  lucideChevronLeft,
  lucideChevronRight,
  lucideDoorOpen,
  lucideGraduationCap,
  lucideTriangleAlert,
} from '@ng-icons/lucide';
import { HlmButton } from '@spartan-ng/helm/button';
import { catchError, forkJoin, of } from 'rxjs';
import {
  AttendanceApiService,
  type AttendanceDepartmentCount,
  type AttendanceHourCount,
  type AttendanceSummary,
  type PersonType,
} from '../../core/attendance/attendance-api.service';
import { TapErrorLogsApiService } from '../../core/tap-errors/tap-error-logs-api.service';

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

@Component({
  selector: 'app-daily-recap',
  imports: [DecimalPipe, FormsModule, NgIcon, HlmButton],
  viewProviders: [
    provideIcons({
      lucideActivity,
      lucideBriefcase,
      lucideCalendarDays,
      lucideChevronLeft,
      lucideChevronRight,
      lucideDoorOpen,
      lucideGraduationCap,
      lucideTriangleAlert,
    }),
  ],
  templateUrl: './daily-recap.html',
  host: { class: 'flex h-full flex-col' },
})
export class DailyRecap {
  private readonly attendanceApi = inject(AttendanceApiService);
  private readonly tapErrorApi = inject(TapErrorLogsApiService);

  protected readonly manilaToday = this.manilaDate(0);
  protected readonly selectedDate = signal(this.manilaToday);
  protected readonly loading = signal(false);

  protected readonly studentSummary = signal<AttendanceSummary>(EMPTY_SUMMARY);
  protected readonly employeeSummary = signal<AttendanceSummary>(EMPTY_SUMMARY);
  protected readonly studentDepts = signal<AttendanceDepartmentCount[]>([]);
  protected readonly employeeDepts = signal<AttendanceDepartmentCount[]>([]);
  protected readonly hours = signal<AttendanceHourCount[]>([]);
  protected readonly rfidErrorCount = signal(0);

  protected readonly totalTaps = computed(
    () => this.studentSummary().totalTaps + this.employeeSummary().totalTaps,
  );
  protected readonly studentsInside = computed(() => this.studentSummary().currentlyIn);
  protected readonly employeesInside = computed(() => this.employeeSummary().currentlyIn);
  protected readonly isToday = computed(() => this.selectedDate() === this.manilaDate(0));

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

  protected readonly topStudentDepts = computed(() => this.studentDepts().slice(0, 8));
  protected readonly topEmployeeDepts = computed(() => this.employeeDepts().slice(0, 8));
  protected readonly studentDeptMax = computed(() =>
    Math.max(...this.topStudentDepts().map((d) => d.count), 1),
  );
  protected readonly employeeDeptMax = computed(() =>
    Math.max(...this.topEmployeeDepts().map((d) => d.count), 1),
  );

  protected readonly dateLabel = computed(() => {
    try {
      return new Intl.DateTimeFormat('en-PH', {
        weekday: 'long',
        month: 'long',
        day: 'numeric',
        year: 'numeric',
        timeZone: 'Asia/Manila',
      }).format(new Date(`${this.selectedDate()}T12:00:00+08:00`));
    } catch {
      return this.selectedDate();
    }
  });

  constructor() {
    this.load();
  }

  protected onDateInput(value: string): void {
    if (!value || value === this.selectedDate()) {
      return;
    }
    this.selectedDate.set(value);
    this.load();
  }

  protected shiftDay(delta: number): void {
    const next = this.addDays(this.selectedDate(), delta);
    const today = this.manilaDate(0);
    if (next > today) {
      return;
    }
    this.selectedDate.set(next);
    this.load();
  }

  protected goToday(): void {
    const today = this.manilaDate(0);
    if (this.selectedDate() === today) {
      return;
    }
    this.selectedDate.set(today);
    this.load();
  }

  protected barPct(value: number, max: number): number {
    return Math.round((value / max) * 100);
  }

  private load(): void {
    const day = this.selectedDate();
    this.loading.set(true);

    const summaryFor = (personType: PersonType) =>
      this.attendanceApi
        .summary({ personType, startDate: day, endDate: day })
        .pipe(catchError(() => of(EMPTY_SUMMARY)));
    const deptsFor = (personType: PersonType) =>
      this.attendanceApi
        .byDepartment(personType, day, day)
        .pipe(catchError(() => of([] as AttendanceDepartmentCount[])));

    forkJoin({
      students: summaryFor('STUDENT'),
      employees: summaryFor('EMPLOYEE'),
      studentDepts: deptsFor('STUDENT'),
      employeeDepts: deptsFor('EMPLOYEE'),
      hours: this.attendanceApi.byHour(day).pipe(catchError(() => of([] as AttendanceHourCount[]))),
      rfidErrors: this.tapErrorApi.count(day).pipe(catchError(() => of(0))),
    }).subscribe({
      next: ({ students, employees, studentDepts, employeeDepts, hours, rfidErrors }) => {
        this.studentSummary.set(students);
        this.employeeSummary.set(employees);
        this.studentDepts.set(studentDepts);
        this.employeeDepts.set(employeeDepts);
        this.hours.set(hours);
        this.rfidErrorCount.set(rfidErrors);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  private hourLabel(hour: number): string {
    const period = hour >= 12 ? 'PM' : 'AM';
    const h12 = hour % 12 === 0 ? 12 : hour % 12;
    return `${h12}${period}`;
  }

  private manilaDate(offsetDays: number): string {
    const now = new Date(
      new Date().toLocaleString('en-US', { timeZone: 'Asia/Manila' }),
    );
    now.setDate(now.getDate() + offsetDays);
    const y = now.getFullYear();
    const m = String(now.getMonth() + 1).padStart(2, '0');
    const d = String(now.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
  }

  private addDays(isoDate: string, delta: number): string {
    const dt = new Date(`${isoDate}T12:00:00+08:00`);
    dt.setDate(dt.getDate() + delta);
    const y = dt.getFullYear();
    const m = String(dt.getMonth() + 1).padStart(2, '0');
    const d = String(dt.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
  }
}
