import { authApi } from '../auth';

export interface CccdData {
  cccdNumber: string;
  fullName: string;
  dob: string;
  gender: string;
  hometown: string;
  address: string;
  aiStandardized?: boolean;
  mrzVerified?: boolean;
}

export interface EkycStatus {
  hasProfile: boolean;
  profileId?: number;
  revisionNo?: number;
  state: 'NOT_STARTED' | 'DRAFT' | 'IN_PROGRESS' | 'PENDING' | 'VERIFIED' | 'REJECTED';
  submittedData?: CccdData;
  matchDistance?: number;
  confidenceScore?: number;
  rejectionReason?: string;
  submittedAt?: string;
  verifiedAt?: string;
  canSubmit: boolean;
  isSeller: boolean;
  nonmatchCount: number;
  frontAssetId?: number;
  backAssetId?: number;
  liveAssetId?: number;
  portraitAssetId?: number;
  hasVerifiedProfile?: boolean;
}

export interface EkycOcrResult {
  status: 'SUCCESS' | 'ERROR';
  errorCode?: string;
  errorMessage?: string;
  ocrData: CccdData;
  frontAssetId: number;
  backAssetId?: number;
  portraitAssetId?: number;
}

export interface EkycSubmitResult {
  profileId: number;
  state: string;
  isMatch: boolean;
  distance: number;
  confidenceScore: number;
  message: string;
}

export interface EkycCheckFaceResult {
  isMatch: boolean;
  matchStatus: string;
  distance: number;
  confidenceScore: number;
  message: string;
  frameStatus?: string;
  directionalCue?: string;
  distanceCard?: number;
  distancePortrait?: number;
  cardMatched?: boolean;
  portraitMatched?: boolean;
  livenessStatus?: string;
  framesCollected?: number;
}

async function extractErrorMessage(response: Response, fallback: string): Promise<string> {
  try {
    const data = await response.json();
    return data.message || data.detail || data.error || fallback;
  } catch {
    return fallback;
  }
}

export const ekycApi = {
  async getStatus(): Promise<EkycStatus> {
    const response = await authApi.authorizedFetch('/ekyc/status');
    if (!response.ok) {
      throw new Error(await extractErrorMessage(response, 'Không thể tải trạng thái eKYC.'));
    }
    return response.json();
  },

  async startOrResume(quickAuthResultId?: string): Promise<{ profileId?: number; revisionNo?: number; state: string }> {
    const response = await authApi.authorizedFetch('/ekyc/start', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(quickAuthResultId ? { quickAuthResultId } : {}),
    });
    if (!response.ok) {
      throw new Error(await extractErrorMessage(response, 'Không thể khởi tạo hồ sơ eKYC.'));
    }
    return response.json();
  },

  async uploadCccd(frontFile: File, backFile: File, portraitFile: File): Promise<EkycOcrResult> {
    const formData = new FormData();
    formData.append('frontFile', frontFile);
    formData.append('backFile', backFile);
    formData.append('portraitFile', portraitFile);
    const response = await authApi.authorizedFetch('/ekyc/upload-cccd', {
      method: 'POST',
      body: formData,
    });
    if (!response.ok) {
      throw new Error(await extractErrorMessage(response, 'Tải ảnh CCCD hoặc xử lý OCR thất bại.'));
    }
    const result = await response.json();
    if (result.status !== 'SUCCESS') {
      throw new Error(result.errorMessage || 'Mô hình AI không thể bóc tách thông tin thẻ căn cước này.');
    }
    return result;
  },

  async checkFace(frontAssetId: number, liveFrameBase64: string, portraitAssetId?: number): Promise<EkycCheckFaceResult> {
    const response = await authApi.authorizedFetch('/ekyc/check-face', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        frontAssetId,
        liveFrameBase64,
        portraitAssetId,
      }),
    });
    if (!response.ok) {
      throw new Error(await extractErrorMessage(response, 'Kiểm tra khuôn mặt thất bại.'));
    }
    return response.json();
  },

  async submitVerification(
    confirmedData: CccdData,
    liveFrameBase64: string,
    frontAssetId: number,
    backAssetId?: number,
    quickAuthResultId?: string,
    referenceFramesBase64?: string[],
    portraitAssetId?: number,
    manualCapture?: boolean
  ): Promise<EkycSubmitResult> {
    const response = await authApi.authorizedFetch('/ekyc/submit', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        confirmedData,
        liveFrameBase64,
        frontAssetId,
        backAssetId,
        quickAuthResultId,
        referenceFramesBase64,
        portraitAssetId,
        manualCapture,
      }),
    });
    if (!response.ok) {
      throw new Error(await extractErrorMessage(response, 'Nộp hồ sơ eKYC thất bại.'));
    }
    return response.json();
  },

  getAssetUrl(assetId: number): string {
    return `/api/v1/ekyc/assets/${assetId}`;
  },
};
