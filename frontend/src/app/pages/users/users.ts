import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  lucidePlus,
  lucideSearch,
  lucideSquarePen,
  lucideUserCheck,
  lucideUserX,
} from '@ng-icons/lucide';
import { HlmBadge } from '@spartan-ng/helm/badge';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmDialogService } from '@spartan-ng/helm/dialog';
import { HlmInput } from '@spartan-ng/helm/input';
import { HlmTableImports } from '@spartan-ng/helm/table';
import { filter, take } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import {
  type AppUser,
  ROLE_LABELS,
  UsersApiService,
  type UserPayload,
} from '../../core/users/users-api.service';
import { UserFormDialog } from './user-form-dialog';

@Component({
  selector: 'app-users',
  imports: [DatePipe, FormsModule, NgIcon, HlmButton, HlmInput, HlmBadge, HlmTableImports],
  viewProviders: [
    provideIcons({ lucidePlus, lucideSearch, lucideSquarePen, lucideUserCheck, lucideUserX }),
  ],
  templateUrl: './users.html',
  host: { class: 'flex h-full flex-col' },
})
export class Users {
  private readonly api = inject(UsersApiService);
  private readonly dialog = inject(HlmDialogService);
  private readonly auth = inject(AuthService);

  protected readonly users = signal<AppUser[]>([]);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly busyId = signal<string | null>(null);
  protected readonly filter = signal('');

  protected readonly roleLabels = ROLE_LABELS;
  protected readonly currentUsername = computed(() => this.auth.user()?.username ?? '');

  protected readonly filtered = computed(() => {
    const term = this.filter().trim().toLowerCase();
    if (!term) {
      return this.users();
    }
    return this.users().filter((u) =>
      [u.username, u.role, u.location ?? ''].join(' ').toLowerCase().includes(term),
    );
  });

  constructor() {
    this.reload();
  }

  protected reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.list().subscribe({
      next: (users) => {
        this.users.set(users);
        this.loading.set(false);
      },
      error: (err: { error?: { message?: string } }) => {
        this.loading.set(false);
        this.error.set(err?.error?.message ?? 'Failed to load users');
      },
    });
  }

  protected openCreate(): void {
    this.openForm('create');
  }

  protected openEdit(user: AppUser): void {
    this.openForm('edit', user);
  }

  protected toggleActive(user: AppUser): void {
    const action = user.active ? 'Deactivate' : 'Activate';
    if (!confirm(`${action} user ${user.username}?`)) {
      return;
    }
    this.error.set(null);
    this.busyId.set(user.id);
    const request = user.active ? this.api.deactivate(user.id) : this.api.activate(user.id);
    request.subscribe({
      next: (updated) => {
        this.users.update((list) => list.map((u) => (u.id === updated.id ? updated : u)));
        this.busyId.set(null);
      },
      error: (err: { error?: { message?: string } }) => {
        this.busyId.set(null);
        this.error.set(err?.error?.message ?? `Failed to ${action.toLowerCase()} user`);
      },
    });
  }

  protected roleBadgeClass(role: string): string {
    switch (role) {
      case 'SUPERADMIN':
        return 'bg-primary/10 text-primary';
      case 'OSAS':
        return 'bg-emerald-500/10 text-emerald-600';
      case 'HR':
        return 'bg-violet-500/10 text-violet-600';
      case 'MONITORING':
        return 'bg-amber-500/10 text-amber-600';
      default:
        return 'bg-muted text-muted-foreground';
    }
  }

  private openForm(mode: 'create' | 'edit', user?: AppUser): void {
    const ref = this.dialog.open(UserFormDialog, {
      context: { mode, user },
      contentClass: 'sm:max-w-lg',
    });

    ref.closed$
      .pipe(
        take(1),
        filter((result): result is UserPayload => !!result),
      )
      .subscribe((result) => {
        this.error.set(null);
        const request =
          mode === 'create' ? this.api.create(result) : this.api.update(user!.id, result);
        request.subscribe({
          next: (saved) => {
            this.users.update((list) => {
              if (mode === 'create') {
                return [...list, saved].sort((a, b) => a.username.localeCompare(b.username));
              }
              return list.map((u) => (u.id === saved.id ? saved : u));
            });
          },
          error: (err: { error?: { message?: string } }) =>
            this.error.set(err?.error?.message ?? 'Failed to save user'),
        });
      });
  }
}
