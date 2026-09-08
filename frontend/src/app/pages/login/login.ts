import { Component, OnDestroy, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideEye, lucideEyeOff } from '@ng-icons/lucide';
import { firstValueFrom } from 'rxjs';
import { APP_NAME, APP_VERSION } from '../../core/app-info';
import { AuthService } from '../../core/auth/auth.service';
import { FullscreenService } from '../../core/fullscreen.service';

@Component({
  selector: 'app-login',
  imports: [FormsModule, NgIcon, RouterLink],
  viewProviders: [provideIcons({ lucideEye, lucideEyeOff })],
  templateUrl: './login.html',
  styles: `
    @keyframes login-rise {
      from {
        opacity: 0;
        transform: translateY(18px) scale(0.985);
      }
      to {
        opacity: 1;
        transform: translateY(0) scale(1);
      }
    }

    @keyframes login-float {
      0%,
      100% {
        transform: translate3d(0, 0, 0);
      }
      50% {
        transform: translate3d(0, -14px, 0);
      }
    }

    .animate-rise {
      animation: login-rise 0.55s cubic-bezier(0.22, 1, 0.36, 1) both;
    }

    .animate-float {
      animation: login-float 7s ease-in-out infinite;
    }

    @media (prefers-reduced-motion: reduce) {
      .animate-rise,
      .animate-float {
        animation: none;
      }
    }
  `,
})
export class Login implements OnDestroy {
  protected readonly username = signal('');
  protected readonly password = signal('');
  protected readonly showPassword = signal(false);
  protected readonly rememberMe = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly loading = signal(false);

  protected readonly appName = APP_NAME;
  protected readonly appVersion = APP_VERSION;

  protected readonly heroImages = [
    { src: '/lpu-building.webp', alt: 'LPU Laguna campus' },
    { src: '/background.webp', alt: 'LPU Laguna building' },
  ];
  protected readonly activeImage = signal(0);
  private readonly slideshowTimer = setInterval(
    () => this.activeImage.update((i) => (i + 1) % this.heroImages.length),
    8000,
  );

  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly fullscreen = inject(FullscreenService);

  ngOnDestroy(): void {
    clearInterval(this.slideshowTimer);
  }

  protected async onSubmit(): Promise<void> {
    this.error.set(null);
    const username = this.username().trim();
    const password = this.password();

    if (!username || !password) {
      this.error.set('Username and password are required.');
      return;
    }

    this.loading.set(true);
    try {
      await firstValueFrom(this.auth.login({ username, password }, this.rememberMe()));
      // Fullscreen only for kiosk roles — admin must never enter then exit.
      if (this.auth.isKiosk() || this.auth.isMonitoring()) {
        await this.fullscreen.enter();
      }
      await this.router.navigateByUrl(this.auth.homeRoute());
    } catch (err: unknown) {
      const message =
        err && typeof err === 'object' && 'error' in err
          ? ((err as { error?: { message?: string } }).error?.message ?? null)
          : null;
      this.error.set(message ?? 'Login failed. Please try again.');
    } finally {
      this.loading.set(false);
    }
  }
}
