import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideEye, lucideEyeOff } from '@ng-icons/lucide';
import { BrnDialogRef } from '@spartan-ng/brain/dialog';
import { HlmButton } from '@spartan-ng/helm/button';
import {
  HlmDialogDescription,
  HlmDialogFooter,
  HlmDialogHeader,
  HlmDialogTitle,
  HlmDialogService,
} from '@spartan-ng/helm/dialog';
import { HlmFieldImports } from '@spartan-ng/helm/field';
import { HlmInput } from '@spartan-ng/helm/input';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';

const MIN_PASSWORD_LENGTH = 8;

@Component({
  selector: 'app-change-password-dialog',
  imports: [
    FormsModule,
    NgIcon,
    HlmButton,
    HlmDialogHeader,
    HlmDialogTitle,
    HlmDialogDescription,
    HlmDialogFooter,
    HlmFieldImports,
    HlmInput,
  ],
  viewProviders: [provideIcons({ lucideEye, lucideEyeOff })],
  templateUrl: './change-password-dialog.html',
})
export class ChangePasswordDialog {
  private readonly dialogRef = inject<BrnDialogRef<boolean>>(BrnDialogRef);
  private readonly auth = inject(AuthService);

  protected currentPassword = '';
  protected newPassword = '';
  protected confirmPassword = '';

  protected readonly showCurrent = signal(false);
  protected readonly showNew = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly username = this.auth.user()?.username ?? 'this account';

  static open(dialog: HlmDialogService) {
    return dialog.open(ChangePasswordDialog, { contentClass: 'sm:max-w-md' });
  }

  protected cancel(): void {
    this.dialogRef.close(false);
  }

  protected async submit(): Promise<void> {
    this.error.set(null);
    const currentPassword = this.currentPassword;
    const newPassword = this.newPassword;
    const confirmPassword = this.confirmPassword;

    if (!currentPassword) {
      this.error.set('Current password is required.');
      return;
    }
    if (newPassword.length < MIN_PASSWORD_LENGTH) {
      this.error.set(`New password must be at least ${MIN_PASSWORD_LENGTH} characters.`);
      return;
    }
    if (newPassword !== confirmPassword) {
      this.error.set('New password and confirmation do not match.');
      return;
    }
    if (newPassword === currentPassword) {
      this.error.set('New password must be different from the current password.');
      return;
    }

    this.saving.set(true);
    try {
      await firstValueFrom(this.auth.changePassword({ currentPassword, newPassword }));
      this.dialogRef.close(true);
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
