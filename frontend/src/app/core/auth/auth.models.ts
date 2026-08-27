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

export interface AuthEventMessage {
  type: string;
  username?: string;
  message?: string;
  timestamp?: string;
  action?: string;
  payload?: unknown;
  /** Present on GUARD_PRESENCE (and optionally other) events. */
  locations?: string[];
}
