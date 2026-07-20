import { environment } from '../../../environments/environment';

/** Resolves a stored photo path (/pictures/...) to a browser URL under the WAR context. */
export function studentPhotoUrl(photo: string | null | undefined): string | null {
  if (!photo) {
    return null;
  }
  if (photo.startsWith('http://') || photo.startsWith('https://') || photo.startsWith('data:')) {
    return photo;
  }
  if (photo.startsWith(environment.contextPath)) {
    return photo;
  }
  return `${environment.contextPath}${photo.startsWith('/') ? photo : `/${photo}`}`;
}
