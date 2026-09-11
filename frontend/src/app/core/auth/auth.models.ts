export interface LoginRequest {
  username: string;
  password: string;
  rememberMe?: boolean;
}

export interface LoginResponse {
  token: string;
  tokenType: string;
  username: string;
  role: string;
  location?: string | null;
  expiresInMs: number;
}

export interface AuthUser {
  username: string;
  role: string;
  location?: string | null;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export type KioskPings = Record<string, Record<string, number>>;

export interface AuthEventMessage {
  type: string;
  id?: string;
  username?: string;
  message?: string;
  timestamp?: string;
  action?: string;
  payload?: unknown;
  /** Present on GUARD_PRESENCE (and optionally other) events. */
  locations?: string[];
  kiosks?: Record<string, string[]>;
  pings?: KioskPings;
}
