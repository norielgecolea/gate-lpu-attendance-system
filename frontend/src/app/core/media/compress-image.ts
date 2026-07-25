/** Longest edge for profile photos (enough for 2x gate display, much smaller than phone dumps). */
const DEFAULT_MAX_DIMENSION = 960;
/** JPEG quality — sharp enough for faces, lighter for faster gate loads. */
const DEFAULT_QUALITY = 0.84;
/** Skip work when the file is already a modest JPEG under this size. */
const SKIP_IF_UNDER_BYTES = 180_000;

/**
 * Resizes and re-encodes an image to JPEG for faster uploads and gate display.
 * Keeps the original basename (needed for bulk photo matching by student/employee number).
 * Falls back to the original file if the browser cannot decode the image.
 */
export async function compressImageFile(
  file: File,
  options?: { maxDimension?: number; quality?: number },
): Promise<File> {
  if (!file.type.startsWith('image/') && !hasImageExtension(file.name)) {
    return file;
  }

  const maxDimension = options?.maxDimension ?? DEFAULT_MAX_DIMENSION;
  const quality = options?.quality ?? DEFAULT_QUALITY;

  let bitmap: ImageBitmap | null = null;
  try {
    bitmap = await createImageBitmap(file);
  } catch {
    return file;
  }

  try {
    const { width, height } = bitmap;
    const scale = Math.min(1, maxDimension / Math.max(width, height));
    const targetWidth = Math.max(1, Math.round(width * scale));
    const targetHeight = Math.max(1, Math.round(height * scale));

    if (
      file.type === 'image/jpeg' &&
      scale === 1 &&
      file.size <= SKIP_IF_UNDER_BYTES
    ) {
      return file;
    }

    const canvas = document.createElement('canvas');
    canvas.width = targetWidth;
    canvas.height = targetHeight;
    const ctx = canvas.getContext('2d');
    if (!ctx) {
      return file;
    }
    ctx.imageSmoothingEnabled = true;
    ctx.imageSmoothingQuality = 'high';
    ctx.drawImage(bitmap, 0, 0, targetWidth, targetHeight);

    const blob = await canvasToJpegBlob(canvas, quality);
    if (!blob) {
      return file;
    }
    // Prefer the smaller payload; keep original only when compression did not help.
    if (blob.size >= file.size && file.type === 'image/jpeg' && scale === 1) {
      return file;
    }

    return new File([blob], replaceExtension(file.name, '.jpg'), {
      type: 'image/jpeg',
      lastModified: Date.now(),
    });
  } finally {
    bitmap.close();
  }
}

export async function compressImageFiles(files: File[]): Promise<File[]> {
  const out: File[] = [];
  for (const file of files) {
    out.push(await compressImageFile(file));
  }
  return out;
}

function canvasToJpegBlob(canvas: HTMLCanvasElement, quality: number): Promise<Blob | null> {
  return new Promise((resolve) => {
    canvas.toBlob((blob) => resolve(blob), 'image/jpeg', quality);
  });
}

function replaceExtension(filename: string, ext: string): string {
  const base = filename.replace(/^.*[/\\]/, '');
  const dot = base.lastIndexOf('.');
  const name = dot > 0 ? base.slice(0, dot) : base;
  return `${name || 'photo'}${ext}`;
}

function hasImageExtension(filename: string): boolean {
  return /\.(jpe?g|png|webp|gif)$/i.test(filename);
}
