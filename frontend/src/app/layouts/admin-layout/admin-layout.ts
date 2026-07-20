import { DatePipe } from '@angular/common';
import { Component, OnDestroy, inject, signal } from '@angular/core';
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
  lucideBriefcase,
  lucideBanknote,
  lucideClock,
  lucideGraduationCap,
  lucideLayoutDashboard,
  lucideLogOut,
  lucideMonitorPlay,
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
import {
  HlmNavigationMenu,
  HlmNavigationMenuItem,
  HlmNavigationMenuLink,
  HlmNavigationMenuList,
} from '@spartan-ng/helm/navigation-menu';
import { AlertSoundService } from '../../core/alert-sound.service';
import { AuthService } from '../../core/auth/auth.service';
import { NotificationService } from '../../core/notifications/notification.service';

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
}

interface TapErrorAlert {
  id: number;
  identifier: string;
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
      lucideMonitorPlay,
      lucideUsers,
      lucideClock,
      lucideUserX,
      lucideUserMinus,
      lucideBriefcase,
      lucideBanknote,
      lucideScanBarcode,
      lucideShieldCheck,
      lucidePanelLeft,
      lucideGraduationCap,
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
  `,
})
export class AdminLayout implements OnDestroy {
  protected readonly sidebarOpen = signal(true);
  protected readonly loggingOut = signal(false);
  protected readonly tapErrors = signal<TapErrorAlert[]>([]);
  private nextAlertId = 1;
  private readonly alertTimers = new Set<ReturnType<typeof setTimeout>>();
  private readonly tapErrorSub: Subscription;

  protected readonly navSections: NavSection[] = [
    {
      label: null,
      items: [{ label: 'Dashboard', icon: 'lucideLayoutDashboard', route: '/dashboard' }],
    },
    {
      label: 'Students',
      items: [
        { label: 'Student Management', icon: 'lucideUsers', route: '/students' },
        { label: 'Student Attendance', icon: 'lucideClock', route: '/students/attendance' },
        { label: 'RFID Registration', icon: 'lucideScanBarcode', route: '/students/rfid' },
        { label: 'Inactive Students', icon: 'lucideUserX', route: '/students/inactive' },
        { label: 'Finance Tagged', icon: 'lucideBanknote', route: '/students/finance-tagged' },
      ],
    },
    {
      label: 'Employees',
      items: [
        { label: 'Employee Management', icon: 'lucideBriefcase', route: '/employees' },
        { label: 'Employee Attendance', icon: 'lucideClock', route: '/employees/attendance' },
        { label: 'RFID Registration', icon: 'lucideScanBarcode', route: '/employees/rfid' },
        { label: 'Inactive Employees', icon: 'lucideUserMinus', route: '/employees/inactive' },
      ],
    },
    {
      label: 'Administration',
      items: [
        { label: 'User Management', icon: 'lucideShieldCheck', route: '/users' },
        {
          label: 'Guard Display',
          icon: 'lucideMonitorPlay',
          route: '/settings/guard-display',
        },
        {
          label: 'RFID Error Logs',
          icon: 'lucideTriangleAlert',
          route: '/tap-errors',
        },
      ],
    },
  ];

  private readonly allNavItems: NavItem[] = this.navSections.flatMap((s) => s.items);

  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
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
      });

    this.tapErrorSub = this.notifications.events$
      .pipe(filter((e) => e.type === 'ATTENDANCE_TAP_ERROR'))
      .subscribe((event) => {
        const payload = (event.payload ?? {}) as TapErrorPayload;
        this.pushTapError(payload);
      });
  }

  ngOnDestroy(): void {
    this.tapErrorSub.unsubscribe();
    this.alertTimers.forEach((t) => clearTimeout(t));
    this.alertTimers.clear();
  }

  /** Longest-prefix match so /students/inactive highlights only its own item. */
  protected isActive(route: string): boolean {
    return this.bestMatch() === route;
  }

  private bestMatch(): string | null {
    const url = this.currentUrl().split('?')[0];
    let best: string | null = null;
    for (const item of this.allNavItems) {
      const matches = url === item.route || url.startsWith(`${item.route}/`);
      if (matches && (best === null || item.route.length > best.length)) {
        best = item.route;
      }
    }
    return best;
  }

  private resolveTitle(): string {
    const best = this.bestMatch();
    return this.allNavItems.find((i) => i.route === best)?.label ?? 'Dashboard';
  }

  protected toggleSidebar(): void {
    this.sidebarOpen.set(!this.sidebarOpen());
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

  private pushTapError(payload: TapErrorPayload): void {
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
