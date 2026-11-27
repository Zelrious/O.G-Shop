export const PRIVATE_EVIDENCE_PATH_PREFIX = '/api/v1/trustsafety/returns/evidence/';

export function isPrivateEvidenceKey(objectKey?: string | null): boolean {
  if (!objectKey) return false;
  const trimmed = objectKey.trim();
  if (trimmed.startsWith(PRIVATE_EVIDENCE_PATH_PREFIX)) return true;
  if (trimmed.startsWith('http://') || trimmed.startsWith('https://')) {
    try {
      return new URL(trimmed).pathname.startsWith(PRIVATE_EVIDENCE_PATH_PREFIX);
    } catch {
      return false;
    }
  }
  return false;
}

export function extractAuthorizedFetchPath(objectKey: string): string {
  const trimmed = objectKey.trim();
  if (trimmed.startsWith('http://') || trimmed.startsWith('https://')) {
    const parsed = new URL(trimmed);
    const path = parsed.pathname;
    if (path.startsWith('/api/v1/')) {
      return path.slice('/api/v1'.length);
    }
    return path;
  }
  if (trimmed.startsWith('/api/v1/')) {
    return trimmed.slice('/api/v1'.length);
  }
  return trimmed;
}

