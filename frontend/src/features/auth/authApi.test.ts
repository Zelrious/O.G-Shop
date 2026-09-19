import { afterEach, describe, expect, it, vi } from 'vitest';
import { authApi } from './authApi';

describe('authApi session boundary', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    authApi.clearAccessToken();
    localStorage.clear();
  });

  it('keeps refresh token out of JavaScript storage and request payload', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      user: {
        userId: 1,
        email: 'buyer@ogshop.vn',
        fullName: 'Buyer',
        roles: ['BUYER'],
        createdAt: '2026-09-19T00:00:00Z',
      },
      accessToken: 'memory-only-access-token',
      expiresIn: 900,
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }));
    vi.stubGlobal('fetch', fetchMock);

    await authApi.login({ email: 'buyer@ogshop.vn', password: 'Password123@' });

    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/auth/login'), expect.objectContaining({
      credentials: 'include',
    }));
    const request = fetchMock.mock.calls[0][1] as RequestInit;
    expect(request.body).not.toContain('role');
    expect(localStorage.length).toBe(0);
  });
});
