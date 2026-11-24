import { describe, it, expect } from 'vitest';
import { resolveReturnMediaUrl } from './mediaUrl';

describe('resolveReturnMediaUrl', () => {
  it('prepends backend origin for relative /api/v1/media paths so port 5174 loads evidence images', () => {
    expect(resolveReturnMediaUrl('/api/v1/media/abc-123.jpg')).toBe(
      'http://localhost:8080/api/v1/media/abc-123.jpg'
    );
  });

  it('preserves absolute http/https, blob, and data URLs unchanged', () => {
    expect(resolveReturnMediaUrl('https://cdn.example.com/a.png')).toBe('https://cdn.example.com/a.png');
    expect(resolveReturnMediaUrl('blob:http://localhost:5174/1234')).toBe('blob:http://localhost:5174/1234');
    expect(resolveReturnMediaUrl('')).toBe('');
  });
});
