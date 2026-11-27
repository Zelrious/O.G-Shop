const BACKEND_ORIGIN = (
  import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1'
).replace(/\/api\/v1\/?$/, '');

/**
 * Resolve backend-relative evidence media URLs (e.g. `/api/v1/media/<uuid>.jpg`)
 * against the configured Backend API origin so images and videos load cleanly
 * when the Frontend dev server runs on a separate port (e.g. `localhost:5174`).
 */
export function resolveReturnMediaUrl(objectKey: string | null | undefined): string {
  if (!objectKey) return '';
  if (/^https?:\/\//i.test(objectKey) || objectKey.startsWith('blob:') || objectKey.startsWith('data:')) {
    return objectKey;
  }
  if (objectKey.startsWith('/')) {
    return `${BACKEND_ORIGIN}${objectKey}`;
  }
  return `${BACKEND_ORIGIN}/${objectKey}`;
}

