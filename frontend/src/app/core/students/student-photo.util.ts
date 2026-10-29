import { environment } from '../../../environments/environment';

/** Resolves a stored media path (/pictures, /videos, /tones) under the WAR context. */
export function appMediaUrl(path: string | null | undefined): string | null {
  if (!path) {
    return null;
  }
  const value = path.trim();
  if (
    value.startsWith('http://') ||
    value.startsWith('https://') ||
    value.startsWith('data:') ||
    value.startsWith('//')
  ) {
    return null;
  }
  if (value.startsWith(environment.contextPath + '/') || value === environment.contextPath) {
    return value;
  }
  return `${environment.contextPath}${value.startsWith('/') ? value : `/${value}`}`;
}

/** Resolves a stored photo path (/pictures/...) to a browser URL under the WAR context. */
export function studentPhotoUrl(photo: string | null | undefined): string | null {
  return appMediaUrl(photo);
}
