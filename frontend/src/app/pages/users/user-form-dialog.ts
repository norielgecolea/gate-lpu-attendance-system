import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BrnDialogRef, injectBrnDialogContext } from '@spartan-ng/brain/dialog';
import { HlmButton } from '@spartan-ng/helm/button';
import {
  HlmDialogDescription,
  HlmDialogFooter,
  HlmDialogHeader,
  HlmDialogTitle,
} from '@spartan-ng/helm/dialog';
import { HlmFieldImports } from '@spartan-ng/helm/field';
import { HlmInput } from '@spartan-ng/helm/input';
import {
  type AppUser,
  ROLE_LABELS,
  USER_ROLES,
  type UserPayload,
} from '../../core/users/users-api.service';

export interface UserFormContext {
  mode: 'create' | 'edit';
  user?: AppUser;
}

@Component({
  selector: 'app-user-form-dialog',
  imports: [
    FormsModule,
    HlmButton,
    HlmDialogHeader,
    HlmDialogTitle,
    HlmDialogDescription,
    HlmDialogFooter,
    HlmFieldImports,
    HlmInput,
  ],
  templateUrl: './user-form-dialog.html',
})
export class UserFormDialog {
  private readonly dialogRef = inject<BrnDialogRef<UserPayload | null>>(BrnDialogRef);
  private readonly context = injectBrnDialogContext<UserFormContext>();

  protected readonly mode = this.context.mode;
  protected readonly roles = USER_ROLES;
  protected readonly roleLabels = ROLE_LABELS;
  protected readonly error = signal<string | null>(null);

  protected username = this.context.user?.username ?? '';
  protected password = '';
  protected role = this.context.user?.role ?? 'OSAS';
  protected location = this.context.user?.location ?? '';

  protected cancel(): void {
    this.dialogRef.close(null);
  }

  protected submit(): void {
    this.error.set(null);
    if (!this.username.trim()) {
      this.error.set('Username is required.');
      return;
    }
    if (this.mode === 'create' && this.password.length < 8) {
      this.error.set('Password must be at least 8 characters.');
      return;
    }
    if (this.mode === 'edit' && this.password.length > 0 && this.password.length < 8) {
      this.error.set('New password must be at least 8 characters (leave blank to keep current).');
      return;
    }

    this.dialogRef.close({
      username: this.username.trim(),
      password: this.password,
      role: this.role,
      location: this.location.trim() || null,
    });
  }
}
