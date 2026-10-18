import { afterEach, describe, expect, it, vi } from 'vitest';
import { authApi } from './authApi';

describe('authApi session boundary', () => {
  it('cannot restore an older token over a pending login OTP', async () => {
    let resolveRestore!: (response: Response) => void;
    const fetchMock = vi.fn().mockImplementationOnce(() => new Promise<Response>(resolve => { resolveRestore = resolve; }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ status: 'OTP_REQUIRED', purpose: 'LOGIN_EMAIL_2FA', challengeId: 'pending' }), { status: 202 }))
      .mockResolvedValueOnce(new Response('{}', { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);
    const restore = authApi.restoreSession();
    const rejected = expect(restore).rejects.toMatchObject({ code: 'SESSION_CHANGED' });
    await authApi.login({ email: 'fixture@example.invalid', password: 'Password123@' });
    resolveRestore(new Response(JSON.stringify({ user: { userId: 1 }, accessToken: 'older-token', expiresIn: 900 }), { status: 200 }));
    await rejected;await authApi.authorizedFetch('/profile');
    expect(fetchMock.mock.calls[2][1].headers).not.toHaveProperty('Authorization');
  });
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

  it('does not authenticate a pending registration and accepts token only after verification', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ status: 'OTP_REQUIRED', challengeId: 'draft' }), { status: 202 }))
      .mockResolvedValueOnce(new Response('{}', { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ user: { userId: 1 }, accessToken: 'verified', expiresIn: 900, awardedCoins: 3000, nextStep: 'PROFILE' }), { status: 200 }))
      .mockResolvedValueOnce(new Response('{}', { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);
    await authApi.register({ fullName: 'Buyer', email: 'fixture@example.invalid', password: 'Password123@', accountType: 'BUYER' });
    await authApi.authorizedFetch('/profile');
    expect(fetchMock.mock.calls[1][1].headers).not.toHaveProperty('Authorization');
    await authApi.verifyRegistration('draft', '123456');
    await authApi.authorizedFetch('/profile');
    expect(fetchMock.mock.calls[3][1].headers).toHaveProperty('Authorization', 'Bearer verified');
    expect(localStorage.length).toBe(0);
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

  it.each(['register', 'resend'] as const)('preserves Retry-After from a 429 %s response', async operation => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      code: 'OTP_RESEND_TOO_SOON', message: 'Vui lòng chờ trước khi gửi mã mới.',
    }), { status: 429, headers: { 'Retry-After': '60' } }));
    vi.stubGlobal('fetch', fetchMock);

    const request = operation === 'register'
      ? authApi.register({ fullName: 'Buyer', email: 'buyer@example.invalid', password: 'Password123@', accountType: 'BUYER' })
      : authApi.resendRegistration('draft');
    await expect(request).rejects.toMatchObject({
      name: 'AuthRequestError', code: 'OTP_RESEND_TOO_SOON', retryAfter: 60,
    });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
