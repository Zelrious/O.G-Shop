import { authApi } from '../auth';
import { CccdData } from './ekycApi';

export interface KtvEkycItem {
  profileId: number;
  userId: number;
  username: string;
  fullName: string;
  email: string;
  phoneNumber?: string;
  revisionNo: number;
  state: 'PENDING' | 'VERIFIED' | 'REJECTED';
  submittedAt: string;
  matchDistance?: number;
  maskedCccdNumber: string;
}

export interface KtvEkycPage {
  items: KtvEkycItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface KtvEkycDetail {
  profileId: number;
  userId: number;
  username: string;
  fullName: string;
  email: string;
  phoneNumber?: string;
  revisionNo: number;
  state: 'PENDING' | 'VERIFIED' | 'REJECTED';
  submittedAt: string;
  submittedData?: CccdData;
  ocrData?: CccdData;
  matchDistance?: number;
  confidenceScore?: number;
  thresholdUsed?: number;
  modelName?: string;
  frontAssetId?: number;
  backAssetId?: number;
  liveAssetId?: number;
  portraitAssetId?: number;
  // Reference asset from previous verified profile (for comparison)
  referenceAssetId?: number;
  referenceAssetUrl?: string;
  rejectionReason?: string;
  reviewedAt?: string;
  reviewedBy?: number;
}

export interface KtvDecisionResult {
  profileId: number;
  outcome: 'VERIFIED' | 'REJECTED';
  message: string;
}

async function extractErrorMessage(response: Response, fallback: string): Promise<string> {
  try {
    const data = await response.json();
    return data.message || data.detail || data.error || fallback;
  } catch {
    return fallback;
  }
}

export const ekycKtvApi = {
  async getQueue(page = 0, size = 10): Promise<KtvEkycPage> {
    const response = await authApi.authorizedFetch(`/ktv/ekyc?page=${page}&size=${size}`);
    if (!response.ok) {
      throw new Error(await extractErrorMessage(response, 'Không thể tải hàng đợi eKYC của KTV.'));
    }
    return response.json();
  },

  async getDetail(profileId: number): Promise<KtvEkycDetail> {
    const response = await authApi.authorizedFetch(`/ktv/ekyc/${profileId}`);
    if (!response.ok) {
      throw new Error(await extractErrorMessage(response, 'Không thể tải chi tiết hồ sơ eKYC.'));
    }
    return response.json();
  },

  async approve(profileId: number, note?: string): Promise<KtvDecisionResult> {
    const response = await authApi.authorizedFetch(`/ktv/ekyc/${profileId}/approve`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ note: note || 'Đã đối chiếu thông tin hợp lệ' }),
    });
    if (!response.ok) {
      throw new Error(await extractErrorMessage(response, 'Phê duyệt hồ sơ eKYC thất bại.'));
    }
    return response.json();
  },

  async reject(profileId: number, reason: string): Promise<KtvDecisionResult> {
    const response = await authApi.authorizedFetch(`/ktv/ekyc/${profileId}/reject`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ reason }),
    });
    if (!response.ok) {
      throw new Error(await extractErrorMessage(response, 'Từ chối hồ sơ eKYC thất bại.'));
    }
    return response.json();
  },
};
