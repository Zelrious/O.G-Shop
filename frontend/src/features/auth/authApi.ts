import { AuthResponse, LoginCredentials, RegisterPayload, UserPrincipal, RegistrationChallenge, RegistrationCompletion } from './types';
import type { AuthChallenge, LoginResponse, GoogleResponse } from './types';

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1';
export const SESSION_EXPIRED_EVENT = 'og:session-expired';
let accessToken: string | null = null;
let restorePromise: Promise<AuthResponse> | null = null;
let credentialGeneration = 0;
let cachedGoogleNonce: { nonceId: string; expiresAt: number } | null = null;

interface ApiErrorBody {
  message?: string;
  code?: string;
  fieldErrors?: { message: string }[];
  accountStatus?: string;
  supportInstruction?: string;
  allowedFunctions?: string[];
}

export class AuthRequestError extends Error {
  readonly code?: string;
  readonly retryAfter: number;
  readonly details?: ApiErrorBody;
  constructor(message: string, code?: string, retryAfter = 0, details?: ApiErrorBody) {
    super(message);
    this.name = 'AuthRequestError';
    this.code = code;
    this.retryAfter = retryAfter;
    this.details = details;
  }
}

async function parseResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const body = (await response.json().catch(() => ({}))) as ApiErrorBody;
    throw new AuthRequestError(body.fieldErrors?.map(field => field.message).join(' ') || body.message
      || 'Yêu cầu không thành công (' + response.status + ').', body.code, Number(response.headers.get('Retry-After')) || 0, body);
  }
  return response.status === 204 ? (undefined as T) : ((await response.json()) as T);
}

async function publicRequest<T>(path: string, body?: unknown): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    credentials: 'include',
    headers: body ? { 'Content-Type': 'application/json' } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  });
  return parseResponse<T>(response);
}

async function sessionRequest<T extends AuthResponse = AuthResponse>(path: string, body?: unknown): Promise<T> {
  const generation = credentialGeneration;
  const result = await publicRequest<T>(path, body);
  if (generation !== credentialGeneration) throw new AuthRequestError('Yêu cầu phiên đã bị thay thế.', 'SESSION_CHANGED');
  accessToken = result.accessToken;
  return result;
}

export const authApi = {
  async login(credentials: LoginCredentials): Promise<LoginResponse> {
    const generation = ++credentialGeneration;
    restorePromise = null;
    accessToken = null;
    const result = await publicRequest<LoginResponse>('/auth/login', { email: credentials.email, password: credentials.password });
    if (generation === credentialGeneration && 'accessToken' in result) accessToken = result.accessToken;
    return result;
  },

  verifyLogin(challenge: AuthChallenge, code: string): Promise<LoginResponse> {
    const generation = ++credentialGeneration;
    restorePromise = null;
    const path = challenge.purpose === 'GOOGLE_LINK' ? '/auth/google/link/verify' : '/auth/login/verify';
    return publicRequest<LoginResponse>(path, { challengeId: challenge.challengeId, code }).then(result => {
      if (generation === credentialGeneration && 'accessToken' in result) accessToken = result.accessToken;
      return result;
    });
  },
  startReset(email: string): Promise<AuthChallenge> { return publicRequest('/auth/password-reset', { email }); },
  verifyReset(challengeId: string, code: string): Promise<AuthChallenge> { return publicRequest('/auth/password-reset/verify', { challengeId, code }); },
  async finishReset(challengeId: string, password: string, confirmPassword: string): Promise<void> {
    await publicRequest('/auth/password-reset/complete', { challengeId, password, confirmPassword });
    accessToken = null;
    window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
  },
  resendChallenge(challengeId: string): Promise<AuthChallenge> { return publicRequest('/auth/challenge/resend', { challengeId }); },
  cancelChallenge(challengeId: string): Promise<void> { return publicRequest('/auth/challenge/cancel', { challengeId }); },
  googleConfig(): Promise<{ enabled: boolean; clientId: string }> {
    return fetch(`${API_BASE}/auth/google/config`).then(parseResponse<{ enabled: boolean; clientId: string }>);
  },
  googleNonce(): Promise<{ nonceId: string }> {
    if (cachedGoogleNonce && Date.now() < cachedGoogleNonce.expiresAt) {
      return Promise.resolve({ nonceId: cachedGoogleNonce.nonceId });
    }
    return publicRequest<{ nonceId: string }>('/auth/google/nonce').then(res => {
      cachedGoogleNonce = { nonceId: res.nonceId, expiresAt: Date.now() + 4 * 60 * 1000 };
      return res;
    }).catch(err => {
      cachedGoogleNonce = null;
      throw err;
    });
  },
  clearGoogleNonce(): void {
    cachedGoogleNonce = null;
  },
  async googleLogin(credential: string, nonceId: string): Promise<GoogleResponse> {
    const generation = ++credentialGeneration;
    restorePromise = null;
    accessToken = null;
    cachedGoogleNonce = null;
    const result = await publicRequest<GoogleResponse>('/auth/google', { credential, nonceId });
    if (generation === credentialGeneration && 'accessToken' in result) accessToken = result.accessToken;
    return result;
  },

  register(payload: RegisterPayload): Promise<RegistrationChallenge> {
    return publicRequest('/auth/register', {
      fullName: payload.fullName,
      email: payload.email,
      phoneNumber: payload.phoneNumber,
      password: payload.password,
      accountType: payload.accountType,
      seller: payload.seller,
      googleTicket: payload.googleTicket,
    });
  },

  verifyRegistration(challengeId: string, code: string): Promise<RegistrationCompletion> {
    credentialGeneration++;
    return sessionRequest<RegistrationCompletion>('/auth/register/verify', { challengeId, code });
  },

  resendRegistration(challengeId: string): Promise<RegistrationChallenge> {
    return publicRequest('/auth/register/resend', { challengeId });
  },

  cancelRegistration(challengeId: string): Promise<void> {
    return publicRequest('/auth/register/cancel', { challengeId });
  },

  restoreSession(): Promise<AuthResponse> {
    if (!restorePromise) {
      const restoring = sessionRequest('/auth/session').finally(() => {
        if (restorePromise === restoring) restorePromise = null;
      });
      restorePromise = restoring;
    }
    return restorePromise;
  },

  async logout(): Promise<void> {
    credentialGeneration++;
    restorePromise = null;
    try {
      await parseResponse<void>(await fetch(`${API_BASE}/auth/logout`, {
        method: 'POST',
        credentials: 'include',
      }));
    } finally {
      accessToken = null;
    }
  },

  async currentUser(): Promise<UserPrincipal> {
    return parseResponse<UserPrincipal>(await this.authorizedFetch('/auth/me'));
  },

  async authorizedFetch(path: string, init: RequestInit = {}): Promise<Response> {
    const response = await fetch(`${API_BASE}${path}`, {
      ...init,
      credentials: 'include',
      headers: {
        ...init.headers,
        ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      },
    });
    if (response.status === 401) {
      accessToken = null;
      window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
    }
    return response;
  },

  clearAccessToken(): void {
    credentialGeneration++;
    restorePromise = null;
    accessToken = null;
  },
};
