import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface AppUser {
  id: string;
  username: string;
  role: string;
  location: string | null;
  active: boolean;
  createdAt: string;
}

export interface UserPayload {
  username: string;
  /** Required on create; empty string on update keeps the current password. */
  password: string;
  role: string;
  location: string | null;
}

export const USER_ROLES = ['SUPERADMIN', 'OSAS', 'HR', 'MONITORING', 'GUARD'] as const;

export const ROLE_LABELS: Record<string, string> = {
  SUPERADMIN: 'Superadmin',
  OSAS: 'OSAS',
  HR: 'HR',
  MONITORING: 'Monitoring',
  GUARD: 'Guard',
};

@Injectable({ providedIn: 'root' })
export class UsersApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/users`;

  list(): Observable<AppUser[]> {
    return this.http.get<AppUser[]>(this.baseUrl);
  }

  create(payload: UserPayload): Observable<AppUser> {
    return this.http.post<AppUser>(this.baseUrl, payload);
  }

  update(id: string, payload: UserPayload): Observable<AppUser> {
    return this.http.put<AppUser>(`${this.baseUrl}/${id}`, payload);
  }

  activate(id: string): Observable<AppUser> {
    return this.http.post<AppUser>(`${this.baseUrl}/${id}/activate`, {});
  }

  deactivate(id: string): Observable<AppUser> {
    return this.http.post<AppUser>(`${this.baseUrl}/${id}/deactivate`, {});
  }
}
