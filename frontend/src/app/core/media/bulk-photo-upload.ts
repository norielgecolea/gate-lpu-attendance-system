import { HttpClient, HttpEventType, HttpResponse } from '@angular/common/http';
import { Observable, defer, filter, firstValueFrom, from, map, tap } from 'rxjs';
import { compressImageFile } from './compress-image';

/** Stay under Tomcat 11's default maxPartCount of 50. */
export const PHOTO_UPLOAD_CHUNK_SIZE = 40;
/** Stay under Cloudflare's ~100MB request cap after compression. */
export const PHOTO_UPLOAD_MAX_CHUNK_BYTES = 80 * 1024 * 1024;

export interface PhotoBulkUploadResult {
  updated: number;
  notFound: number;
  skippedInvalid: number;
}

export interface PhotoBulkUploadProgress {
  processed: number;
  total: number;
  percent: number;
  result: PhotoBulkUploadResult;
}

export function emptyPhotoBulkUploadResult(): PhotoBulkUploadResult {
  return { updated: 0, notFound: 0, skippedInvalid: 0 };
}

export function emptyPhotoBulkUploadProgress(total = 0): PhotoBulkUploadProgress {
  return {
    processed: 0,
    total,
    percent: 0,
    result: emptyPhotoBulkUploadResult(),
  };
}

/**
 * Compresses then uploads photos in small multipart requests so a large folder
 * is not rejected by Tomcat's part limit or Cloudflare's body size cap.
 */
export function uploadPhotosInChunks(
  files: File[],
  postChunk: (
    chunk: File[],
    onHttpProgress: (loaded: number, total: number) => void,
  ) => Observable<PhotoBulkUploadResult>,
  onProgress?: (progress: PhotoBulkUploadProgress) => void,
): Observable<PhotoBulkUploadResult> {
  return defer(() =>
    from(
      (async () => {
        const total = files.length;
        let result = emptyPhotoBulkUploadResult();
        let processed = 0;
        let pending: File[] = [];
        let pendingBytes = 0;

        const emit = (percent: number) => {
          onProgress?.({
            processed,
            total,
            percent: total === 0 ? 100 : Math.min(100, Math.max(0, percent)),
            result,
          });
        };

        emit(0);

        const flush = async () => {
          if (pending.length === 0) {
            return;
          }
          const chunk = pending;
          pending = [];
          pendingBytes = 0;
          const chunkResult = await firstValueFrom(
            postChunk(chunk, (loaded, totalBytes) => {
              const fraction = totalBytes > 0 ? Math.min(1, loaded / totalBytes) : 0;
              emit(Math.round(((processed + fraction * chunk.length) / total) * 100));
            }),
          );
          result = {
            updated: result.updated + chunkResult.updated,
            notFound: result.notFound + chunkResult.notFound,
            skippedInvalid: result.skippedInvalid + chunkResult.skippedInvalid,
          };
          processed += chunk.length;
          emit(Math.round((processed / total) * 100));
        };

        for (const file of files) {
          const compressed = await compressImageFile(file);
          if (
            pending.length > 0 &&
            (pending.length >= PHOTO_UPLOAD_CHUNK_SIZE ||
              pendingBytes + compressed.size > PHOTO_UPLOAD_MAX_CHUNK_BYTES)
          ) {
            await flush();
          }
          pending.push(compressed);
          pendingBytes += compressed.size;
        }
        await flush();
        return result;
      })(),
    ),
  );
}

export function postPhotoChunk(
  http: HttpClient,
  url: string,
  files: File[],
  onHttpProgress: (loaded: number, total: number) => void,
): Observable<PhotoBulkUploadResult> {
  const formData = new FormData();
  for (const file of files) {
    formData.append('files', file, file.name);
  }
  return http
    .post<PhotoBulkUploadResult>(url, formData, {
      reportProgress: true,
      observe: 'events',
    })
    .pipe(
      tap((event) => {
        if (event.type === HttpEventType.UploadProgress && event.total) {
          onHttpProgress(event.loaded, event.total);
        }
      }),
      filter((event): event is HttpResponse<PhotoBulkUploadResult> => event.type === HttpEventType.Response),
      map((event) => {
        if (!event.body) {
          throw new Error('Photo upload returned an empty response');
        }
        return event.body;
      }),
    );
}
