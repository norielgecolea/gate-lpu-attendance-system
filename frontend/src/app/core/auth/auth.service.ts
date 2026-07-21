import { isPlatformBrowser } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Injectable, PLATFORM_ID, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, of, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthUser, LoginRequest, LoginResponse } from './auth.models';
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
  readonly isGuard = computed(() => this.userSignal()?.role === 'GUARD');
  readonly isMonitoring = computed(() => this.userSignal()?.role === 'MONITORING');

  homeRoute(): string {
    if (this.isGuard()) {
      return '/guard';
    }
    if (this.isMonitoring()) {
      return '/monitor';
    }
    if (this.isSuperAdmin()) {
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
          this.notifications.connect(response.token);
        }),
      );
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
    if (token && (this.isSuperAdmin() || this.isGuard() || this.isMonitoring())) {
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
