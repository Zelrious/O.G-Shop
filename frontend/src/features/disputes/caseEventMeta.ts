export function parseCaseEventAction(metadata?: string | null): string | null {
  if (!metadata) return null;
  try {
    const parsed = JSON.parse(metadata) as { action?: unknown };
    return typeof parsed?.action === 'string' ? parsed.action : null;
  } catch {
    return null;
  }
}

