import { AuthResponse, LoginCredentials, RegisterPayload, UserPrincipal } from './types';

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1';
export const SESSION_EXPIRED_EVENT = 'og:session-expired';
let accessToken: string | null = null;
let restorePromise: Promise<AuthResponse> | null = null;

interface ApiErrorBody {
  message?: string;
}

async function parseResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const body = (await response.json().catch(() => ({}))) as ApiErrorBody;
    throw new Error(body.message || `Yêu cầu không thành công (${response.status}).`);
  }
  return response.status === 204 ? (undefined as T) : ((await response.json()) as T);
}

async function sessionRequest(path: string, body?: unknown): Promise<AuthResponse> {
  const response = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    credentials: 'include',
    headers: body ? { 'Content-Type': 'application/json' } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  });
  const result = await parseResponse<AuthResponse>(response);
  accessToken = result.accessToken;
  return result;
}

export const authApi = {
  login(credentials: LoginCredentials): Promise<AuthResponse> {
    return sessionRequest('/auth/login', { email: credentials.email, password: credentials.password });
  },

  register(payload: RegisterPayload): Promise<AuthResponse> {
    return sessionRequest('/auth/register', {
      fullName: payload.fullName,
      email: payload.email,
      phoneNumber: payload.phoneNumber,
      password: payload.password,
    });
  },

  restoreSession(): Promise<AuthResponse> {
    if (!restorePromise) {
      restorePromise = sessionRequest('/auth/session').finally(() => {
        restorePromise = null;
      });
    }
    return restorePromise;
  },

  async logout(): Promise<void> {
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
    accessToken = null;
  },
};
