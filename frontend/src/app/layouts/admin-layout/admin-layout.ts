import { DatePipe } from '@angular/common';
import { Component, HostListener, OnDestroy, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  NavigationEnd,
  Router,
  RouterLink,
  RouterOutlet,
} from '@angular/router';
import { Subscription, filter } from 'rxjs';
import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  lucideBookOpen,
  lucideBriefcase,
  lucideBanknote,
  lucideBell,
  lucideBuilding2,
  lucideChartColumn,
  lucideChevronDown,
  lucideChevronRight,
  lucideClock,
  lucideDatabaseBackup,
  lucideGraduationCap,
  lucideHistory,
  lucideIdCard,
  lucideKeyRound,
  lucideLayoutDashboard,
  lucideLogOut,
  lucideMenu,
  lucideMonitorPlay,
  lucideMusic2,
  lucidePanelLeft,
  lucideScanBarcode,
  lucideShieldCheck,
  lucideTriangleAlert,
  lucideUserMinus,
  lucideUserRound,
  lucideUsers,
  lucideUserX,
  lucideX,
} from '@ng-icons/lucide';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmDialogService } from '@spartan-ng/helm/dialog';
import {
  HlmNavigationMenu,
  HlmNavigationMenuItem,
  HlmNavigationMenuLink,
  HlmNavigationMenuList,
} from '@spartan-ng/helm/navigation-menu';
import { AlertSoundService } from '../../core/alert-sound.service';
import { isAlarmMarked, type TapResponse } from '../../core/attendance/attendance-api.service';
import { AuthService } from '../../core/auth/auth.service';
import { canAccessAdminRoute } from '../../core/auth/role-access';
import { isVenueAdmin, kioskGroupFromRole, seesAllTapErrors } from '../../core/kiosk/kiosk-group';
import { NotificationService } from '../../core/notifications/notification.service';
import { ChangePasswordDialog } from '../../shared/change-password/change-password-dialog';

interface NavItem {
  label: string;
  icon: string;
  route: string;
}

interface NavSection {
  label: string | null;
  items: NavItem[];
}

interface TapErrorPayload {
  identifier?: string | null;
  location?: string | null;
  tappedAt?: string | null;
  kioskGroup?: string | null;
}

interface TapErrorAlert {
  id: number;
  identifier: string;
  location: string;
  time: Date;
}

interface AlarmTapAlert {
  id: number;
  name: string;
  personType: string;
  location: string;
  time: Date;
}

@Component({
  selector: 'app-admin-layout',
  imports: [
    DatePipe,
    RouterOutlet,
    RouterLink,
    NgIcon,
    HlmButton,
    HlmNavigationMenu,
    HlmNavigationMenuList,
    HlmNavigationMenuItem,
    HlmNavigationMenuLink,
  ],
  viewProviders: [
    provideIcons({
      lucideLayoutDashboard,
      lucideBookOpen,
      lucideBuilding2,
      lucideChartColumn,
      lucideIdCard,
      lucideMonitorPlay,
      lucideMusic2,
      lucideUsers,
      lucideClock,
      lucideUserX,
      lucideUserMinus,
      lucideBriefcase,
      lucideBanknote,
      lucideBell,
      lucideScanBarcode,
      lucideShieldCheck,
      lucideHistory,
      lucideDatabaseBackup,
      lucidePanelLeft,
      lucideChevronDown,
      lucideChevronRight,
      lucideMenu,
      lucideGraduationCap,
      lucideKeyRound,
      lucideUserRound,
      lucideLogOut,
      lucideTriangleAlert,
      lucideX,
    }),
  ],
  templateUrl: './admin-layout.html',
  styles: `
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

    @media (prefers-reduced-motion: reduce) {
      .alert-card {
        animation: none;
      }
    }

    .sidebar-nav {
      scrollbar-width: thin;
      scrollbar-color: color-mix(in oklch, var(--sidebar-border) 80%, transparent) transparent;
    }

    .sidebar-nav::-webkit-scrollbar {
      width: 4px;
    }

    .sidebar-nav::-webkit-scrollbar-thumb {
      border-radius: 9999px;
      background: color-mix(in oklch, var(--sidebar-border) 80%, transparent);
    }
  `,
})
export class AdminLayout implements OnDestroy {
  protected readonly sidebarOpen = signal(true);
  protected readonly mobileNavOpen = signal(false);
  protected readonly loggingOut = signal(false);
  protected readonly accountMenuOpen = signal(false);
  protected readonly tapErrors = signal<TapErrorAlert[]>([]);
  protected readonly alarmAlerts = signal<AlarmTapAlert[]>([]);
  private nextAlertId = 1;
  private readonly alertTimers = new Set<ReturnType<typeof setTimeout>>();
  private readonly tapErrorSub: Subscription;
  private readonly alarmTapSub: Subscription;

  protected readonly navSections: NavSection[] = [
    {
      label: 'Dashboards',
      items: [
        { label: 'Gate Dashboard', icon: 'lucideLayoutDashboard', route: '/dashboard' },
        { label: 'Library Dashboard', icon: 'lucideBookOpen', route: '/dashboard/library' },
        { label: 'Olive Hotel Dashboard', icon: 'lucideBuilding2', route: '/dashboard/olive' },
      ],
    },
    {
      label: null,
      items: [
        { label: 'Attendance', icon: 'lucideClock', route: '/attendance' },
        { label: 'RFID Checker', icon: 'lucideIdCard', route: '/rfid-checker' },
        { label: 'Daily Recap', icon: 'lucideChartColumn', route: '/daily-recap' },
      ],
    },
    {
      label: 'Students',
      items: [
        { label: 'Students', icon: 'lucideUsers', route: '/students' },
        { label: 'Student Attendance', icon: 'lucideClock', route: '/students/attendance' },
        { label: 'RFID Registration', icon: 'lucideScanBarcode', route: '/students/rfid' },
        { label: 'Inactive Students', icon: 'lucideUserX', route: '/students/inactive' },
        { label: 'Finance Tagged', icon: 'lucideBanknote', route: '/students/finance-tagged' },
        { label: 'Alarm', icon: 'lucideBell', route: '/students/alarm' },
      ],
    },
    {
      label: 'Employees',
      items: [
        { label: 'Employees', icon: 'lucideBriefcase', route: '/employees' },
        { label: 'Employee Attendance', icon: 'lucideClock', route: '/employees/attendance' },
        { label: 'RFID Registration', icon: 'lucideScanBarcode', route: '/employees/rfid' },
        { label: 'Inactive Employees', icon: 'lucideUserMinus', route: '/employees/inactive' },
        { label: 'Alarm', icon: 'lucideBell', route: '/employees/alarm' },
      ],
    },
    {
      label: 'Administration',
      items: [
        { label: 'User Management', icon: 'lucideShieldCheck', route: '/users' },
        { label: 'Audit Logs', icon: 'lucideHistory', route: '/audit-logs' },
        { label: 'Backup & Restore', icon: 'lucideDatabaseBackup', route: '/backup' },
        {
          label: 'Guard Display',
          icon: 'lucideMonitorPlay',
          route: '/settings/guard-display',
        },
        {
          label: 'Gate Tones',
          icon: 'lucideMusic2',
          route: '/settings/gate-tones',
        },
        {
          label: 'RFID Error Logs',
          icon: 'lucideTriangleAlert',
          route: '/tap-errors',
        },
      ],
    },
  ];

  protected readonly collapsedNav = signal<Record<string, boolean>>(readCollapsedNav());

  protected readonly visibleNavSections = computed(() => {
    const role = this.auth.user()?.role;
    const sections = this.navSections
      .map((section) => ({
        ...section,
        items: section.items
          .filter((item) => canAccessAdminRoute(role, item.route))
          .map((item) => this.relabelDashboard(item, role)),
      }))
      .filter((section) => section.items.length > 0);

    if (!isVenueAdmin(role)) {
      return sections;
    }

    const directoryItems = sections
      .filter((section) => section.label === 'Students' || section.label === 'Employees')
      .flatMap((section) => section.items);
    const rest = sections.filter(
      (section) => section.label !== 'Students' && section.label !== 'Employees',
    );
    if (directoryItems.length === 0) {
      return rest;
    }
    const directory: NavSection = { label: 'Directory', items: directoryItems };
    const insertAt = rest.findIndex((section) => section.label === 'Administration');
    if (insertAt < 0) {
      return [...rest, directory];
    }
    return [...rest.slice(0, insertAt), directory, ...rest.slice(insertAt)];
  });

  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(HlmDialogService);
  private readonly alertSound = inject(AlertSoundService);
  protected readonly notifications = inject(NotificationService);

  private readonly currentUrl = signal(this.router.url);
  protected readonly pageTitle = signal(this.resolveTitle());
  protected readonly currentUser = this.auth.user;

  constructor() {
    this.router.events
      .pipe(
        filter((e) => e instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => {
        this.currentUrl.set(this.router.url);
        this.pageTitle.set(this.resolveTitle());
        this.mobileNavOpen.set(false);
        this.accountMenuOpen.set(false);
      });

    this.tapErrorSub = this.notifications.events$
      .pipe(filter((e) => e.type === 'ATTENDANCE_TAP_ERROR'))
      .subscribe((event) => {
        const payload = (event.payload ?? {}) as TapErrorPayload;
        this.pushTapError(payload);
      });

    this.alarmTapSub = this.notifications.events$
      .pipe(filter((e) => e.type === 'ATTENDANCE_TAP'))
      .subscribe((event) => {
        const tap = event.payload as TapResponse | undefined;
        if (!tap || !isAlarmMarked(tap) || this.auth.user()?.role !== 'SUPERADMIN') {
          return;
        }
        this.pushAlarmTap(tap);
      });
  }

  ngOnDestroy(): void {
    this.tapErrorSub.unsubscribe();
    this.alarmTapSub.unsubscribe();
    this.alertTimers.forEach((t) => clearTimeout(t));
    this.alertTimers.clear();
  }

  /** Longest-prefix match so /students/inactive highlights only its own item. */
  protected isActive(route: string): boolean {
    return this.bestMatch() === route;
  }

  private bestMatch(): string | null {
    const url = this.currentUrl().split('?')[0];
    const items = this.visibleNavSections().flatMap((section) => section.items);
    let best: string | null = null;
    for (const item of items) {
      const matches = url === item.route || url.startsWith(`${item.route}/`);
      if (matches && (best === null || item.route.length > best.length)) {
        best = item.route;
      }
    }
    return best;
  }

  private resolveTitle(): string {
    const best = this.bestMatch();
    const items = this.visibleNavSections().flatMap((section) => section.items);
    return items.find((i) => i.route === best)?.label ?? 'Dashboard';
  }

  protected toggleSidebar(): void {
    if (typeof window !== 'undefined' && window.matchMedia('(max-width: 767px)').matches) {
      this.mobileNavOpen.update((open) => !open);
      return;
    }
    this.sidebarOpen.update((open) => !open);
  }

  protected isSectionCollapsed(label: string | null): boolean {
    if (!label || !this.showSidebarLabels()) {
      return false;
    }
    return !!this.collapsedNav()[label];
  }

  protected toggleNavSection(label: string | null, event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    if (!label) {
      return;
    }
    this.collapsedNav.update((current) => {
      const next = { ...current, [label]: !current[label] };
      writeCollapsedNav(next);
      return next;
    });
  }

  private relabelDashboard(item: NavItem, role: string | null | undefined): NavItem {
    if (item.route !== '/dashboard') {
      return item;
    }
    if (role === 'LIBRARIAN') {
      return { ...item, label: 'Library Dashboard' };
    }
    if (role === 'OLIVE') {
      return { ...item, label: 'Olive Hotel Dashboard' };
    }
    return item;
  }

  protected closeMobileNav(): void {
    this.mobileNavOpen.set(false);
  }

  protected showSidebarLabels(): boolean {
    return this.sidebarOpen() || this.mobileNavOpen();
  }

  protected toggleAccountMenu(event: Event): void {
    event.stopPropagation();
    this.accountMenuOpen.update((open) => !open);
  }

  @HostListener('document:click')
  protected closeAccountMenu(): void {
    this.accountMenuOpen.set(false);
  }

  protected openChangePassword(): void {
    this.accountMenuOpen.set(false);
    this.closeMobileNav();
    ChangePasswordDialog.open(this.dialog);
  }

  protected logout(): void {
    this.loggingOut.set(true);
    this.auth.logout().subscribe({
      next: () => this.loggingOut.set(false),
      error: () => this.loggingOut.set(false),
    });
  }

  protected dismissTapError(id: number): void {
    this.tapErrors.update((list) => list.filter((a) => a.id !== id));
  }

  protected dismissAlarm(id: number): void {
    this.alarmAlerts.update((list) => list.filter((a) => a.id !== id));
  }

  private pushAlarmTap(tap: TapResponse): void {
    const alert: AlarmTapAlert = {
      id: this.nextAlertId++,
      name: tap.student?.name ?? tap.employee?.name ?? 'Unknown',
      personType: tap.personType === 'EMPLOYEE' || tap.employee ? 'Employee' : 'Student',
      location: tap.location?.trim() || 'Unknown gate',
      time: new Date(tap.action === 'TIME_OUT' && tap.timeOut ? tap.timeOut : tap.timeIn),
    };
    this.alarmAlerts.update((list) => [alert, ...list].slice(0, 4));
    this.alertSound.playAlarm();
    const timer = setTimeout(() => {
      this.dismissAlarm(alert.id);
      this.alertTimers.delete(timer);
    }, 12_000);
    this.alertTimers.add(timer);
  }

  private pushTapError(payload: TapErrorPayload & { kioskGroup?: string | null }): void {
    const role = this.auth.user()?.role;
    if (!seesAllTapErrors(role)) {
      const myGroup = kioskGroupFromRole(role);
      if (payload.kioskGroup && payload.kioskGroup !== myGroup) {
        return;
      }
    }
    const alert: TapErrorAlert = {
      id: this.nextAlertId++,
      identifier: payload.identifier?.trim() || 'Unknown ID',
      location: payload.location?.trim() || 'Unknown gate',
      time: payload.tappedAt ? new Date(payload.tappedAt) : new Date(),
    };
    this.tapErrors.update((list) => [alert, ...list].slice(0, 4));
    this.alertSound.playError();
    const timer = setTimeout(() => {
      this.dismissTapError(alert.id);
      this.alertTimers.delete(timer);
    }, 12_000);
    this.alertTimers.add(timer);
  }
}

const NAV_COLLAPSE_KEY = 'lpu-admin-nav-collapsed';

function readCollapsedNav(): Record<string, boolean> {
  if (typeof localStorage === 'undefined') {
    return {};
  }
  try {
    const raw = localStorage.getItem(NAV_COLLAPSE_KEY);
    if (!raw) {
      return {};
    }
    const parsed = JSON.parse(raw) as unknown;
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      return {};
    }
    return parsed as Record<string, boolean>;
  } catch {
    return {};
  }
}

function writeCollapsedNav(value: Record<string, boolean>): void {
  if (typeof localStorage === 'undefined') {
    return;
  }
  try {
    localStorage.setItem(NAV_COLLAPSE_KEY, JSON.stringify(value));
  } catch {
    // ignore quota / private mode
  }
}
