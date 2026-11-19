import { authApi } from '../auth';

export interface QuickAuthInitResponse {
  alreadyVerified: boolean;
  sessionId?: string;
  resultId?: string;
  expiresAt?: string;
  message: string;
}

export interface QuickAuthVerifyResponse {
  match: boolean;
  resultId?: string;
  distance?: number;
  confidenceScore?: number;
  remainingAttempts: number;
  message: string;
  frameStatus?: string;
  directionalCue?: string;
}

export interface QuickAuthStatusResponse {
  hasValidResult: boolean;
  resultId?: string;
  expiresAt?: string;
  purpose?: string;
}

async function extractErrorMessage(response: Response, fallback: string): Promise<string> {
  try {
    const data = await response.json();
    return data.message || data.detail || data.error || fallback;
  } catch {
    return fallback;
  }
}

export const quickAuthApi = {
  async init(purpose?: string): Promise<QuickAuthInitResponse> {
    const response = await authApi.authorizedFetch('/ekyc/quick-auth/init', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ purpose }),
    });
    if (!response.ok) {
      throw new Error(await extractErrorMessage(response, 'Khởi tạo phiên xác thực nhanh thất bại.'));
    }
    return response.json();
  },

  async verify(sessionId: string, liveFrameBase64: string): Promise<QuickAuthVerifyResponse> {
    const response = await authApi.authorizedFetch('/ekyc/quick-auth/verify', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ sessionId, liveFrameBase64 }),
    });
    if (!response.ok) {
      throw new Error(await extractErrorMessage(response, 'Xác thực khuôn mặt thất bại.'));
    }
    return response.json();
  },

  async getStatus(purpose?: string): Promise<QuickAuthStatusResponse> {
    const query = purpose ? `?purpose=${encodeURIComponent(purpose)}` : '';
    const response = await authApi.authorizedFetch(`/ekyc/quick-auth/status${query}`);
    if (!response.ok) {
      throw new Error(await extractErrorMessage(response, 'Không thể kiểm tra trạng thái xác thực nhanh.'));
    }
    return response.json();
  },
};
