import { isPlatformBrowser } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Injectable, PLATFORM_ID, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, of, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AuthUser,
  ChangePasswordRequest,
  LoginRequest,
  LoginResponse,
  PasswordChangeResponse,
} from './auth.models';
import { NotificationService } from '../notifications/notification.service';

const TOKEN_KEY = 'lpu_auth_token';
const USER_KEY = 'lpu_auth_user';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly notifications = inject(NotificationService);

  private readonly userSignal = signal<AuthUser | null>(this.readStoredUser());
  private readonly tokenSignal = signal<string | null>(this.readStoredToken());

  readonly user = this.userSignal.asReadonly();
  readonly token = this.tokenSignal.asReadonly();
  readonly isAuthenticated = computed(() => !!this.tokenSignal() && !!this.userSignal());
  readonly isSuperAdmin = computed(() => this.userSignal()?.role === 'SUPERADMIN');
  readonly isOsas = computed(() => this.userSignal()?.role === 'OSAS');
  readonly isHr = computed(() => this.userSignal()?.role === 'HR');
  readonly isGuard = computed(() => this.userSignal()?.role === 'GUARD');
  readonly isKiosk = computed(
    () =>
      this.userSignal()?.role === 'GUARD' ||
      this.userSignal()?.role === 'LIBRARY_KIOSK' ||
      this.userSignal()?.role === 'OLIVE_KIOSK',
  );
  readonly isMonitoring = computed(() => this.userSignal()?.role === 'MONITORING');
  readonly isLibrarian = computed(() => this.userSignal()?.role === 'LIBRARIAN');
  readonly isOlive = computed(() => this.userSignal()?.role === 'OLIVE');
  readonly isAdminPortal = computed(
    () =>
      this.isSuperAdmin() ||
      this.isOsas() ||
      this.isHr() ||
      this.isLibrarian() ||
      this.isOlive(),
  );

  readonly passwordChangeRequired = computed(() => !!this.userSignal()?.passwordChangeRequired);

  homeRoute(): string {
    if (this.passwordChangeRequired()) {
      return '/password-required';
    }
    if (this.isKiosk()) {
      return '/guard';
    }
    if (this.isMonitoring()) {
      return '/monitor';
    }
    if (this.isAdminPortal()) {
      return '/dashboard';
    }
    return '/';
  }

  login(request: LoginRequest, rememberMe: boolean): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${environment.apiBaseUrl}/auth/login`, {
        ...request,
        rememberMe,
      })
      .pipe(
        tap((response) => {
          this.persistSession(response, rememberMe);
          if (!response.passwordChangeRequired) {
            this.notifications.connect(response.token);
          }
        }),
      );
  }

  changePassword(request: ChangePasswordRequest): Observable<PasswordChangeResponse> {
    return this.http
      .post<PasswordChangeResponse>(`${environment.apiBaseUrl}/auth/change-password`, request)
      .pipe(
        tap((response) => {
          if (response.token) {
            this.replaceToken(response.token);
          }
          this.clearPasswordChangeFlag();
          if (response.token) {
            this.notifications.connect(response.token);
          }
        }),
      );
  }

  /** Sends an already signed-in account to the required password change screen. */
  requirePasswordChange(): void {
    const user = this.userSignal();
    if (user && !user.passwordChangeRequired) {
      this.writeUser({ ...user, passwordChangeRequired: true });
    }
    this.notifications.disconnect();
    const path = this.router.url.split('?')[0];
    if (path !== '/password-required') {
      void this.router.navigateByUrl('/password-required');
    }
  }

  logout(): Observable<unknown> {
    const token = this.tokenSignal();
    const finish = () => {
      this.clearSession();
      this.notifications.disconnect();
      void this.router.navigate(['/']);
    };

    if (!token) {
      finish();
      return of(null);
    }

    return this.http.post(`${environment.apiBaseUrl}/auth/logout`, {}).pipe(
      catchError(() => of(null)),
      tap(() => finish()),
    );
  }

  me(): Observable<AuthUser | null> {
    if (!this.tokenSignal()) {
      return of(null);
    }
    return this.http.get<AuthUser>(`${environment.apiBaseUrl}/auth/me`).pipe(
      tap((user) => this.userSignal.set(user)),
      catchError(() => {
        this.clearSession();
        return of(null);
      }),
    );
  }

  restoreSession(): void {
    const token = this.tokenSignal();
    if (
      token &&
      !this.passwordChangeRequired() &&
      (this.isAdminPortal() || this.isKiosk() || this.isMonitoring())
    ) {
      this.notifications.connect(token);
    }
  }

  getAuthorizationHeader(): string | null {
    const token = this.tokenSignal();
    return token ? `Bearer ${token}` : null;
  }

  private persistSession(response: LoginResponse, rememberMe: boolean): void {
    const user: AuthUser = {
      username: response.username,
      role: response.role,
      location: response.location ?? null,
      passwordChangeRequired: !!response.passwordChangeRequired,
    };
    this.tokenSignal.set(response.token);
    this.userSignal.set(user);

    if (!isPlatformBrowser(this.platformId)) {
      return;
    }

    const storage = rememberMe ? localStorage : sessionStorage;
    const other = rememberMe ? sessionStorage : localStorage;
    other.removeItem(TOKEN_KEY);
    other.removeItem(USER_KEY);
    storage.setItem(TOKEN_KEY, response.token);
    storage.setItem(USER_KEY, JSON.stringify(user));
  }

  private replaceToken(token: string): void {
    this.tokenSignal.set(token);
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }
    if (localStorage.getItem(TOKEN_KEY)) {
      localStorage.setItem(TOKEN_KEY, token);
    } else {
      sessionStorage.setItem(TOKEN_KEY, token);
    }
  }

  private clearPasswordChangeFlag(): void {
    const user = this.userSignal();
    if (!user?.passwordChangeRequired) {
      return;
    }
    this.writeUser({ ...user, passwordChangeRequired: false });
  }

  private writeUser(user: AuthUser): void {
    this.userSignal.set(user);
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }
    const raw = JSON.stringify(user);
    if (localStorage.getItem(USER_KEY)) {
      localStorage.setItem(USER_KEY, raw);
    } else if (sessionStorage.getItem(USER_KEY)) {
      sessionStorage.setItem(USER_KEY, raw);
    }
  }

  private clearSession(): void {
    this.tokenSignal.set(null);
    this.userSignal.set(null);
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    sessionStorage.removeItem(TOKEN_KEY);
    sessionStorage.removeItem(USER_KEY);
  }

  private readStoredToken(): string | null {
    if (!isPlatformBrowser(this.platformId)) {
      return null;
    }
    return localStorage.getItem(TOKEN_KEY) ?? sessionStorage.getItem(TOKEN_KEY);
  }

  private readStoredUser(): AuthUser | null {
    if (!isPlatformBrowser(this.platformId)) {
      return null;
    }
    const raw = localStorage.getItem(USER_KEY) ?? sessionStorage.getItem(USER_KEY);
    if (!raw) {
      return null;
    }
    try {
      return JSON.parse(raw) as AuthUser;
    } catch {
      return null;
    }
  }
}
