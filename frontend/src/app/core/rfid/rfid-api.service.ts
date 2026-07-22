import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map, of } from 'rxjs';
import { environment } from '../../../environments/environment';

export type RfidOwnerType = 'STUDENT' | 'EMPLOYEE';

export interface RfidCheckResult {
  available: boolean;
  message?: string;
}

@Injectable({ providedIn: 'root' })
export class RfidApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/rfid`;

  /**
   * Returns null when the RFID is free (or blank), otherwise the conflict message.
   */
  checkDuplicate(
    rfid: string | null | undefined,
    ownerType?: RfidOwnerType,
    excludeId?: string | null,
  ): Observable<string | null> {
    const value = rfid?.trim() ?? '';
    if (!value) {
      return of(null);
    }

    let params = new HttpParams().set('rfid', value);
    if (ownerType) {
      params = params.set('ownerType', ownerType);
    }
    if (excludeId) {
      params = params.set('excludeId', excludeId);
    }

    return this.http.get<RfidCheckResult>(`${this.baseUrl}/check`, { params }).pipe(
      map((result) => (result.available ? null : (result.message ?? 'RFID already assigned'))),
    );
  }
}
