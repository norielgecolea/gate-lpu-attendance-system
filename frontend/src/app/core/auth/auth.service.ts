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
import { isKioskRole } from '../kiosk/kiosk-group';
import { NotificationService } from '../notifications/notification.service';

const TOKEN_KEY = 'lpu_auth_token';
const USER_KEY = 'lpu_auth_user';
/** Survives a kiosk reboot even when the browser drops localStorage. */
const KIOSK_COOKIE = 'lpu_kiosk_session';
const KIOSK_COOKIE_MAX_AGE = 60 * 60 * 24 * 365 * 10;

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

  readonly passwordChangeRequired = computed(
    () => !!this.userSignal()?.passwordChangeRequired && !this.isKiosk(),
  );

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
          if (!this.passwordChangeRequired()) {
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
    if (this.isKiosk()) {
      return;
    }
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
        if (!isKioskRole(this.userSignal()?.role)) {
          this.clearSession();
        }
        return of(null);
      }),
    );
  }

  restoreSession(): void {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }
    this.promoteKioskSession();
    const token = this.tokenSignal();
    if (
      token &&
      !this.passwordChangeRequired() &&
      (this.isAdminPortal() || this.isKiosk() || this.isMonitoring())
    ) {
      this.notifications.connect(token);
    }
    // A short token is upgraded in the background. Failure must not sign the kiosk out:
    // the server still accepts an expired kiosk token, and a reboot often races Tomcat.
    if (this.isKiosk() && token && !isDurableToken(token)) {
      this.renewKioskSession();
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
      passwordChangeRequired: !!response.passwordChangeRequired && !isKioskRole(response.role),
    };
    this.tokenSignal.set(response.token);
    this.userSignal.set(user);

    if (!isPlatformBrowser(this.platformId)) {
      return;
    }

    // Kiosk terminals reboot. Keep the session in localStorage either way.
    const durable = rememberMe || isKioskRole(response.role);
    const storage = durable ? localStorage : sessionStorage;
    const other = durable ? sessionStorage : localStorage;
    other.removeItem(TOKEN_KEY);
    other.removeItem(USER_KEY);
    storage.setItem(TOKEN_KEY, response.token);
    storage.setItem(USER_KEY, JSON.stringify(user));
    if (isKioskRole(response.role)) {
      this.writeKioskCookie(response.token, JSON.stringify(user));
    } else {
      this.clearKioskCookie();
    }
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

  /** Moves a kiosk session out of sessionStorage so the next reboot still has it. */
  private promoteKioskSession(): void {
    if (!isKioskRole(this.userSignal()?.role)) {
      return;
    }
    const cookie = readKioskCookie();
    const token = localStorage.getItem(TOKEN_KEY) ?? sessionStorage.getItem(TOKEN_KEY) ?? cookie?.token;
    const rawUser = localStorage.getItem(USER_KEY) ?? sessionStorage.getItem(USER_KEY) ?? cookie?.user;
    if (!token || !rawUser) {
      return;
    }
    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(USER_KEY, rawUser);
    sessionStorage.removeItem(TOKEN_KEY);
    sessionStorage.removeItem(USER_KEY);
    this.writeKioskCookie(token, rawUser);
    this.tokenSignal.set(token);
  }

  /** Replaces an 8-hour kiosk token with the long-lived one. A failure leaves the kiosk signed in. */
  private renewKioskSession(): void {
    this.http.post<LoginResponse>(`${environment.apiBaseUrl}/auth/kiosk-session`, {}).subscribe({
      next: (response) => {
        this.storeDurableToken(response.token);
        if (!this.passwordChangeRequired()) {
          this.notifications.connect(response.token);
        }
      },
      error: () => {
        // Leave the stored kiosk session in place.
      },
    });
  }

  private storeDurableToken(token: string): void {
    this.tokenSignal.set(token);
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }
    const raw = localStorage.getItem(USER_KEY) ?? sessionStorage.getItem(USER_KEY) ?? readKioskCookie()?.user;
    localStorage.setItem(TOKEN_KEY, token);
    if (raw) {
      localStorage.setItem(USER_KEY, raw);
      this.writeKioskCookie(token, raw);
    }
    sessionStorage.removeItem(TOKEN_KEY);
    sessionStorage.removeItem(USER_KEY);
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
    this.clearKioskCookie();
  }

  private readStoredToken(): string | null {
    if (!isPlatformBrowser(this.platformId)) {
      return null;
    }
    return (
      localStorage.getItem(TOKEN_KEY) ??
      sessionStorage.getItem(TOKEN_KEY) ??
      readKioskCookie()?.token ??
      null
    );
  }

  private writeKioskCookie(token: string, userJson: string): void {
    if (!isPlatformBrowser(this.platformId) || typeof document === 'undefined') {
      return;
    }
    const value = encodeURIComponent(JSON.stringify({ token, user: userJson }));
    const secure = window.location.protocol === 'https:' ? '; Secure' : '';
    document.cookie = `${KIOSK_COOKIE}=${value}; Max-Age=${KIOSK_COOKIE_MAX_AGE}; Path=/; SameSite=Lax${secure}`;
  }

  private clearKioskCookie(): void {
    if (!isPlatformBrowser(this.platformId) || typeof document === 'undefined') {
      return;
    }
    document.cookie = `${KIOSK_COOKIE}=; Max-Age=0; Path=/; SameSite=Lax`;
  }

  private readStoredUser(): AuthUser | null {
    if (!isPlatformBrowser(this.platformId)) {
      return null;
    }
    const raw = localStorage.getItem(USER_KEY) ?? sessionStorage.getItem(USER_KEY) ?? readKioskCookie()?.user;
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

function readKioskCookie(): { token: string; user: string } | null {
  if (typeof document === 'undefined') {
    return null;
  }
  const prefix = `${KIOSK_COOKIE}=`;
  for (const part of document.cookie.split(';')) {
    const entry = part.trim();
    if (!entry.startsWith(prefix)) {
      continue;
    }
    try {
      const parsed = JSON.parse(decodeURIComponent(entry.slice(prefix.length))) as {
        token?: string;
        user?: string;
      };
      if (!parsed.token || !parsed.user) {
        return null;
      }
      return { token: parsed.token, user: parsed.user };
    } catch {
      return null;
    }
  }
  return null;
}

/** Remember-me tokens last years. A normal login lasts 8 hours. */
function isDurableToken(token: string): boolean {
  try {
    const part = token.split('.')[1];
    if (!part) {
      return false;
    }
    const base64 = part.replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
    const json = atob(padded);
    const payload = JSON.parse(json) as { exp?: number; iat?: number; rm?: boolean };
    if (payload.rm === true) {
      return true;
    }
    if (typeof payload.exp !== 'number' || typeof payload.iat !== 'number') {
      return false;
    }
    return payload.exp - payload.iat > 86_400;
  } catch {
    return false;
  }
}
