import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { FullscreenService } from '../../core/fullscreen.service';

const MIN_PASSWORD_LENGTH = 8;

@Component({
  selector: 'app-password-required',
  imports: [FormsModule],
  templateUrl: './password-required.html',
})
export class PasswordRequired {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly fullscreen = inject(FullscreenService);

  protected currentPassword = '';
  protected newPassword = '';
  protected confirmPassword = '';
  protected readonly error = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly username = this.auth.user()?.username ?? 'this account';

  protected async submit(): Promise<void> {
    this.error.set(null);
    if (!this.currentPassword) {
      this.error.set('Current password is required.');
      return;
    }
    if (this.newPassword.length < MIN_PASSWORD_LENGTH) {
      this.error.set(`New password must be at least ${MIN_PASSWORD_LENGTH} characters.`);
      return;
    }
    if (this.newPassword !== this.confirmPassword) {
      this.error.set('New password and confirmation do not match.');
      return;
    }
    if (this.newPassword === this.currentPassword) {
      this.error.set('New password must be different from the current password.');
      return;
    }

    this.saving.set(true);
    try {
      await firstValueFrom(
        this.auth.changePassword({
          currentPassword: this.currentPassword,
          newPassword: this.newPassword,
        }),
      );
      if (this.auth.isKiosk() || this.auth.isMonitoring()) {
        await this.fullscreen.enter();
      }
      await this.router.navigateByUrl(this.auth.homeRoute());
    } catch (err: unknown) {
      const message =
        err && typeof err === 'object' && 'error' in err
          ? ((err as { error?: { message?: string } }).error?.message ?? null)
          : null;
      this.error.set(message ?? 'Could not update password. Please try again.');
    } finally {
      this.saving.set(false);
    }
  }
}
