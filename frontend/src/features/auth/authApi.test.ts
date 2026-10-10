import { afterEach, describe, expect, it, vi } from 'vitest';
import { authApi } from './authApi';

describe('authApi session boundary', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    authApi.clearAccessToken();
    localStorage.clear();
  });

  it('keeps login credentials out of JavaScript storage and ignores client roles', async () => {
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

  it('restores only the existing session and never requests renewal', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      user: { userId: 1, roles: ['BUYER'] }, accessToken: 'still-valid', expiresIn: 45,
    }), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);
    const session = await authApi.restoreSession();
    expect(session.expiresIn).toBe(45);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/auth/session'),
      expect.objectContaining({ method: 'POST', credentials: 'include' }));
  });

  it('does not refresh or retry a business request after authentication expires', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response('{}', { status: 401 }));
    const expired = vi.fn();
    window.addEventListener('og:session-expired', expired);
    vi.stubGlobal('fetch', fetchMock);
    try {
      const response = await authApi.authorizedFetch('/profile');
      expect(response.status).toBe(401);
      expect(fetchMock).toHaveBeenCalledTimes(1);
      expect(expired).toHaveBeenCalledTimes(1);
    } finally {
      window.removeEventListener('og:session-expired', expired);
    }
  });
});
