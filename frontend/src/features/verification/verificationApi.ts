import { authApi } from '../auth';
import { BiometricScanResult, CccdOcrData } from './types';

interface ExtractedDataResponse {
  so_cccd?: string;
  ho_va_ten?: string;
  ngay_sinh?: string;
  gioi_tinh?: string;
  que_quan?: string;
  noi_thuong_tru?: string;
}

interface OcrResponse {
  status: string;
  errorMessage?: string;
  error_message?: string;
  extractedData?: ExtractedDataResponse;
  extracted_data?: ExtractedDataResponse;
}

interface VerificationResponse {
  verificationId?: number;
  extractedData: ExtractedDataResponse;
  match: {
    match: boolean;
    distance: number;
    confidenceScore: number;
    userInstruction?: string;
    simulated: boolean;
  };
}

export interface OcrApiResult {
  ocrData: CccdOcrData;
  cardFile: File;
  isSimulated: boolean;
}

function mapOcr(data: ExtractedDataResponse = {}): CccdOcrData {
  return {
    cccdNumber: data.so_cccd || '',
    fullName: data.ho_va_ten || '',
    dob: data.ngay_sinh || '',
    gender: data.gioi_tinh || '',
    hometown: data.que_quan || '',
    address: data.noi_thuong_tru || '',
  };
}

async function errorMessage(response: Response): Promise<string> {
  const body = (await response.json().catch(() => ({}))) as { message?: string; detail?: string };
  return body.message || body.detail || `Yêu cầu eKYC không thành công (${response.status}).`;
}

export const verificationApi = {
  async processCccdImage(file: File): Promise<OcrApiResult> {
    const formData = new FormData();
    formData.append('file', file);
    const response = await authApi.authorizedFetch('/ekyc/ocr', { method: 'POST', body: formData });
    if (!response.ok) throw new Error(await errorMessage(response));
    const data = (await response.json()) as OcrResponse;
    if (data.status !== 'SUCCESS') {
      throw new Error(data.errorMessage || data.error_message || 'Mô hình OCR không thể đọc thẻ này.');
    }
    return {
      ocrData: mapOcr(data.extractedData || data.extracted_data),
      cardFile: file,
      isSimulated: true,
    };
  },

  async verifyIdentity(cardFile: File, liveFrameBase64: string): Promise<BiometricScanResult> {
    const formData = new FormData();
    formData.append('cardFile', cardFile);
    formData.append('liveFrame', liveFrameBase64);
    const response = await authApi.authorizedFetch('/ekyc/verify', { method: 'POST', body: formData });
    if (!response.ok) throw new Error(await errorMessage(response));
    const data = (await response.json()) as VerificationResponse;
    return {
      verificationId: data.verificationId,
      isMatch: data.match.match,
      distance: data.match.distance,
      confidenceScore: data.match.confidenceScore,
      userInstruction: data.match.userInstruction,
      capturedImageBase64: liveFrameBase64,
      isSimulated: data.match.simulated,
      ocrData: mapOcr(data.extractedData),
    };
  },
};
