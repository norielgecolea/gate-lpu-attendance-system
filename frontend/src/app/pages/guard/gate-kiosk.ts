import { DatePipe } from '@angular/common';
import {
  AfterViewInit,
  Component,
  ElementRef,
  HostListener,
  OnDestroy,
  OnInit,
  ViewChild,
  computed,
  inject,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  lucideBookOpen,
  lucideBuilding2,
  lucideCircleAlert,
  lucideKeyRound,
  lucideLogOut,
  lucideScanBarcode,
  lucideUserRound,
} from '@ng-icons/lucide';
import { kioskGroupFromRole, type KioskGroup } from '../../core/kiosk/kiosk-group';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmDialogService } from '@spartan-ng/helm/dialog';
import { Subscription, filter, take } from 'rxjs';
import {
  AttendanceApiService,
  type TapResponse,
} from '../../core/attendance/attendance-api.service';
import { AuthService } from '../../core/auth/auth.service';
import { FullscreenService } from '../../core/fullscreen.service';
import { NotificationService } from '../../core/notifications/notification.service';
import { applyScanInput } from '../../core/rfid/wedge-scan-buffer';
import {
  GuardDisplayApiService,
  type GuardDisplayMode,
  type GuardVideo,
  guardVideoUrl,
} from '../../core/settings/guard-display-api.service';
import { GateTonesApiService } from '../../core/settings/gate-tones-api.service';
import { studentPhotoUrl } from '../../core/students/student-photo.util';
import { ServerClockService } from '../../core/time/server-clock.service';
import { GateSounds } from './gate-sounds';
import { ChangePasswordDialog } from '../../shared/change-password/change-password-dialog';

interface ConfettiPiece {
  left: string;
  color: string;
  delay: string;
  duration: string;
  size: string;
  drift: string;
  spin: string;
}

interface BalloonPiece {
  left: string;
  color: string;
  delay: string;
  duration: string;
  scale: number;
}

@Component({
  selector: 'app-gate-kiosk',
  imports: [FormsModule, DatePipe, NgIcon, HlmButton],
  viewProviders: [
    provideIcons({
      lucideScanBarcode,
      lucideBookOpen,
      lucideBuilding2,
      lucideKeyRound,
      lucideLogOut,
      lucideUserRound,
      lucideCircleAlert,
    }),
  ],
  templateUrl: './gate-kiosk.html',
  styleUrl: './gate-kiosk.css',
  host: {
    class: 'gate-kiosk-host',
    '[attr.data-kiosk]': 'kioskGroup',
  },
})
export class GateKiosk implements OnInit, AfterViewInit, OnDestroy {
  @ViewChild('idInput') private readonly idInput?: ElementRef<HTMLInputElement>;

  private readonly api = inject(AttendanceApiService);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(HlmDialogService);
  private readonly notifications = inject(NotificationService);
  private readonly fullscreen = inject(FullscreenService);
  private readonly displayApi = inject(GuardDisplayApiService);
  private readonly tonesApi = inject(GateTonesApiService);
  private readonly serverClock = inject(ServerClockService);
  private readonly sounds = new GateSounds();
  private wsErrorSub?: Subscription;
  private wsDisplaySub?: Subscription;
  private wsTonesSub?: Subscription;

  protected readonly identifier = signal('');
  protected readonly error = signal<string | null>(null);
  protected readonly errorNotFound = signal(false);
  protected readonly errorEmployeeOnly = signal(false);
  protected readonly current = signal<TapResponse | null>(null);
  protected readonly flash = signal<'in' | 'out' | 'idle' | 'error'>('idle');
  protected readonly clock = this.serverClock.now;
  protected readonly clockTz = this.serverClock.datePipeTimezone;
  protected readonly guardName = this.auth.user;
  protected readonly kioskGroup: KioskGroup = kioskGroupFromRole(this.auth.user()?.role);
  protected readonly venueBrand = 'LPU Laguna';
  protected readonly venueTitle =
    this.kioskGroup === 'LIBRARY'
      ? 'Library Attendance'
      : this.kioskGroup === 'OLIVE_HOTEL'
        ? 'Olive Hotel Attendance'
        : 'Gate Attendance';
  protected readonly idleHint =
    this.kioskGroup === 'LIBRARY'
      ? 'Ready to scan at the Library'
      : this.kioskGroup === 'OLIVE_HOTEL'
        ? 'Ready to scan at Olive Hotel'
        : 'Ready to scan';
  protected readonly notFoundHint =
    this.kioskGroup === 'LIBRARY'
      ? 'Please go to the Library desk'
      : this.kioskGroup === 'OLIVE_HOTEL'
        ? 'Please go to Olive Hotel reception'
        : 'Please go to OSAS / MIS Department.';
  protected readonly idleIcon =
    this.kioskGroup === 'LIBRARY'
      ? 'lucideBookOpen'
      : this.kioskGroup === 'OLIVE_HOTEL'
        ? 'lucideBuilding2'
        : 'lucideScanBarcode';
  protected readonly accountDialogOpen = signal(false);
  protected readonly animKey = signal(0);
  protected readonly confetti = signal<ConfettiPiece[]>([]);
  protected readonly balloons = signal<BalloonPiece[]>([]);

  /** Admin-controlled side panel: a video playlist, or nothing. */
  protected readonly displayMode = signal<GuardDisplayMode>('NONE');
  protected readonly guardVideos = signal<GuardVideo[]>([]);
  protected readonly videoIndex = signal(0);
  protected readonly sidePanel = computed<'video' | 'none'>(() => {
    if (this.displayMode() === 'VIDEO' && this.guardVideos().length > 0) {
      return 'video';
    }
    return 'none';
  });

  private hideTimer?: ReturnType<typeof setTimeout>;
  private errorTimer?: ReturnType<typeof setTimeout>;
  private focusTimer?: ReturnType<typeof setInterval>;
  private lastScanInputAt = 0;

  /** Local tap errors also broadcast on WS — ignore the echo so we don't double-play tones. */
  private ignoreTapErrorEchoUntil = 0;

  ngOnInit(): void {
    const token = this.auth.token();
    if (token) {
      this.notifications.connect(token);
    }
    // Keep capture field focused for RFID wedge scanners.
    this.focusTimer = setInterval(() => this.focusInput(), 1500);
    // Unrecognized taps from hardware readers/other sessions at this gate arrive
    // only via websocket — surface them on this screen too.
    this.wsErrorSub = this.notifications.events$
      .pipe(filter((e) => e.type === 'ATTENDANCE_TAP_ERROR'))
      .subscribe((event) => {
        if (Date.now() < this.ignoreTapErrorEchoUntil) {
          return;
        }
        const payload = (event.payload ?? {}) as { location?: string | null; kioskGroup?: string | null };
        const myGate = this.auth.user()?.location ?? null;
        const myGroup = kioskGroupFromRole(this.auth.user()?.role);
        if (payload.kioskGroup && payload.kioskGroup !== myGroup) {
          return;
        }
        // Only react to errors from this kiosk's gate (or when gates are unknown).
        if (!myGate || !payload.location || payload.location === myGate) {
          this.showError('Record Not Found', true);
        }
      });
    this.loadDisplaySettings();
    this.loadToneSettings();
    this.wsDisplaySub = this.notifications.events$
      .pipe(filter((e) => e.type === 'GUARD_DISPLAY_CHANGED'))
      .subscribe(() => this.loadDisplaySettings());
    this.wsTonesSub = this.notifications.events$
      .pipe(filter((e) => e.type === 'GATE_TONES_CHANGED'))
      .subscribe(() => this.loadToneSettings());
  }

  ngAfterViewInit(): void {
    this.focusInput();
    // Keep fullscreen from login; only attempt again for remember-me cold starts.
    void this.fullscreen.enterOnLaunch();
  }

  ngOnDestroy(): void {
    if (this.focusTimer) {
      clearInterval(this.focusTimer);
    }
    this.clearHideTimer();
    this.clearErrorTimer();
    this.wsErrorSub?.unsubscribe();
    this.wsDisplaySub?.unsubscribe();
    this.wsTonesSub?.unsubscribe();
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
    const cleaned = applyScanInput(this.identifier(), el.value, elapsed);
    if (el.value !== cleaned) {
      el.value = cleaned;
    }
    this.identifier.set(cleaned);
  }

  protected submit(): void {
    const el = this.idInput?.nativeElement;
    // Prefer the native value — wedge scanners can finish Enter before ngModel catches up.
    const value = (el?.value ?? this.identifier()).trim();
    if (!value) {
      return;
    }

    // Clear immediately so the next rapid tap starts a fresh scan buffer.
    this.identifier.set('');
    this.lastScanInputAt = 0;
    if (el) {
      el.value = '';
    }
    this.focusInput();

    this.clearErrorTimer();
    this.api.tap(value).subscribe({
      next: (tap) => {
        this.error.set(null);
        this.applyTap(tap, true);
        this.focusInput();
      },
      error: (err: { status?: number; error?: { message?: string } | string }) => {
        const body = err?.error;
        const apiMessage =
          typeof body === 'string'
            ? body
            : (body?.message ?? '');
        const notFound =
          err?.status === 404 || /record\s*not\s*found/i.test(apiMessage);
        const employeeOnly = /employees only/i.test(apiMessage);
        // Backend also emits ATTENDANCE_TAP_ERROR — suppress that echo for this tap.
        this.ignoreTapErrorEchoUntil = Date.now() + 2000;
        this.showError(
          employeeOnly
            ? 'This kiosk is for Employees only'
            : notFound
              ? 'Record Not Found'
              : (apiMessage || 'Tap failed. Please try again.'),
          notFound,
          employeeOnly,
        );
        this.focusInput();
      },
    });
  }

  protected logout(): void {
    void this.fullscreen.exit();
    this.auth.logout().subscribe();
  }

  protected openChangePassword(): void {
    this.accountDialogOpen.set(true);
    ChangePasswordDialog.open(this.dialog)
      .closed$.pipe(take(1))
      .subscribe(() => {
        this.accountDialogOpen.set(false);
        this.focusInput();
      });
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

  protected formatTime(iso: string | null | undefined): string {
    if (!iso) {
      return '—';
    }
    try {
      return new Intl.DateTimeFormat('en-PH', {
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        hour12: true,
        timeZone: this.serverClock.zoneId(),
      }).format(new Date(iso));
    } catch {
      return iso;
    }
  }

  private showError(message: string, notFound = false, employeeOnly = false): void {
    this.clearHideTimer();
    this.current.set(null);
    this.error.set(message);
    this.errorNotFound.set(notFound);
    this.errorEmployeeOnly.set(employeeOnly);
    this.flash.set('idle');
    this.animKey.update((k) => k + 1);
    if (employeeOnly) {
      this.sounds.playEmployeeOnly();
    } else if (notFound) {
      this.sounds.playNotFound();
    } else {
      this.sounds.playError();
    }
    this.clearErrorTimer();
    queueMicrotask(() => this.flash.set('error'));
    this.errorTimer = setTimeout(() => {
      this.error.set(null);
      this.errorNotFound.set(false);
      this.errorEmployeeOnly.set(false);
      this.flash.set('idle');
      this.focusInput();
    }, 5000);
  }

  private applyTap(tap: TapResponse, playSound: boolean): void {
    this.error.set(null);
    this.errorNotFound.set(false);
    this.errorEmployeeOnly.set(false);
    this.clearErrorTimer();
    this.current.set(tap);
    this.animKey.update((k) => k + 1);
    if (tap.birthday) {
      this.launchParty();
    } else {
      this.confetti.set([]);
      this.balloons.set([]);
    }
    this.flash.set('idle');
    queueMicrotask(() => this.flash.set(tap.action === 'TIME_OUT' ? 'out' : 'in'));
    if (playSound) {
      if (tap.student?.financeTagged || tap.financeTagged) {
        this.sounds.playFinanceWarning();
      } else if (tap.birthday) {
        this.sounds.playBirthday();
      } else if (tap.action === 'TIME_OUT') {
        this.sounds.playTimeOut();
      } else {
        this.sounds.playTimeIn();
      }
    }
    this.scheduleHide();
  }

  /** Fresh randomized confetti + balloons for a birthday tap. */
  private launchParty(): void {
    const colors = ['#ef4444', '#f59e0b', '#22c55e', '#3b82f6', '#a855f7', '#ec4899', '#eab308'];
    const pick = (arr: string[]) => arr[Math.floor(Math.random() * arr.length)];
    this.confetti.set(
      Array.from({ length: 90 }, () => ({
        left: `${Math.random() * 100}%`,
        color: pick(colors),
        delay: `${Math.random() * 2.2}s`,
        duration: `${2.6 + Math.random() * 2.4}s`,
        size: `${6 + Math.random() * 7}px`,
        drift: `${(Math.random() - 0.5) * 180}px`,
        spin: `${540 + Math.random() * 720}deg`,
      })),
    );
    this.balloons.set(
      Array.from({ length: 10 }, (_, i) => ({
        left: `${4 + i * 10 + Math.random() * 5}%`,
        color: pick(colors),
        delay: `${Math.random() * 1.6}s`,
        duration: `${4.5 + Math.random() * 3}s`,
        scale: 0.75 + Math.random() * 0.5,
      })),
    );
  }

  private scheduleHide(): void {
    this.clearHideTimer();
    this.hideTimer = setTimeout(() => {
      this.current.set(null);
      this.flash.set('idle');
      this.confetti.set([]);
      this.balloons.set([]);
      this.focusInput();
    }, 5000);
  }

  private clearHideTimer(): void {
    if (this.hideTimer) {
      clearTimeout(this.hideTimer);
      this.hideTimer = undefined;
    }
  }

  private clearErrorTimer(): void {
    if (this.errorTimer) {
      clearTimeout(this.errorTimer);
      this.errorTimer = undefined;
    }
  }

  protected currentVideoSrc(): string | null {
    const videos = this.guardVideos();
    if (videos.length === 0) {
      return null;
    }
    const index = this.videoIndex() % videos.length;
    return guardVideoUrl(videos[index].url);
  }

  /** Guard displays must stay silent no matter how the file was encoded. */
  protected muteVideo(event: Event): void {
    const video = event.target as HTMLVideoElement;
    video.muted = true;
    video.volume = 0;
  }

  protected onVideoEnded(): void {
    const count = this.guardVideos().length;
    if (count > 1) {
      this.videoIndex.update((i) => (i + 1) % count);
    }
  }

  private loadDisplaySettings(): void {
    this.displayApi.getDisplay().subscribe({
      next: (settings) => {
        this.displayMode.set(settings.mode === 'VIDEO' ? 'VIDEO' : 'NONE');
        this.guardVideos.set(settings.videos);
        if (this.videoIndex() >= settings.videos.length) {
          this.videoIndex.set(0);
        }
      },
      error: () => undefined, // Keep the current panel if the setting can't load.
    });
  }

  private loadToneSettings(): void {
    this.tonesApi.getSettings().subscribe({
      next: (settings) => this.sounds.applySettings(settings),
      error: () => undefined, // Keep built-in sounds if tones can't load.
    });
  }

  private focusInput(): void {
    if (this.accountDialogOpen()) {
      return;
    }
    const el = this.idInput?.nativeElement;
    if (!el) {
      return;
    }
    if (document.activeElement !== el) {
      el.focus({ preventScroll: true });
    }
  }
}
