import { CccdOcrData, BiometricScanResult } from './types';
import { mockVerificationService } from './mockVerificationService';

const EKYC_API_BASE = import.meta.env.VITE_EKYC_API_URL || 'http://localhost:8001/api/v1/ekyc';

export interface OcrApiResult {
  ocrData: CccdOcrData;
  cardFaceEmbedding: number[] | null;
  cardImageBase64: string | null;
  isRealAi: boolean;
}

export const verificationApi = {
  async checkHealth(): Promise<boolean> {
    try {
      const res = await fetch(`${EKYC_API_BASE}/health`, { method: 'GET' });
      return res.ok;
    } catch {
      return false;
    }
  },

  async processCccdImage(file: File): Promise<OcrApiResult> {
    const isServiceUp = await this.checkHealth();
    if (isServiceUp) {
      try {
        const formData = new FormData();
        formData.append('file', file);

        const response = await fetch(`${EKYC_API_BASE}/ocr`, {
          method: 'POST',
          body: formData,
        });

        if (!response.ok) {
          const errData = (await response.json().catch(() => ({}))) as { detail?: string };
          throw new Error(errData.detail || `Lỗi bóc tách thẻ CCCD (${response.status})`);
        }

        const data = (await response.json()) as {
          status: string;
          error_message?: string;
          extracted_data: {
            so_cccd?: string;
            ho_va_ten?: string;
            ngay_sinh?: string;
            gioi_tinh?: string;
            que_quan?: string;
            noi_thuong_tru?: string;
          };
          card_face_embedding?: number[];
        };

        if (data.status !== 'SUCCESS') {
          throw new Error(data.error_message || 'Mô hình OCR không thể đọc thẻ này. Vui lòng chụp lại rõ nét hơn.');
        }

        // Đọc ảnh thẻ base64 để hỗ trợ preview và so khớp khuôn mặt tiếp theo
        const cardImageBase64 = await new Promise<string>((resolve) => {
          const reader = new FileReader();
          reader.onloadend = () => resolve(reader.result as string);
          reader.readAsDataURL(file);
        });

        const ocrData: CccdOcrData = {
          cccdNumber: data.extracted_data.so_cccd || '',
          fullName: data.extracted_data.ho_va_ten || '',
          dob: data.extracted_data.ngay_sinh || '',
          gender: data.extracted_data.gioi_tinh || '',
          hometown: data.extracted_data.que_quan || '',
          address: data.extracted_data.noi_thuong_tru || '',
        };

        return {
          ocrData,
          cardFaceEmbedding: data.card_face_embedding || null,
          cardImageBase64,
          isRealAi: true,
        };
      } catch (err) {
        console.warn('[eKYC] Lỗi gọi API thật, chuyển sang bộ giả lập an toàn:', err);
      }
    }

    // Fallback sang mock service nếu service AI chưa chạy
    const mockData = await mockVerificationService.processCccdImage(file);
    return {
      ocrData: mockData,
      cardFaceEmbedding: null,
      cardImageBase64: null,
      isRealAi: false,
    };
  },

  async matchFace(
    liveFrameBase64: string,
    cardEmbedding?: number[] | null,
    cardImageBase64?: string | null
  ): Promise<BiometricScanResult> {
    const isServiceUp = await this.checkHealth();
    if (isServiceUp) {
      try {
        const payload = {
          live_frame_base64: liveFrameBase64,
          card_embedding: cardEmbedding || undefined,
          card_image_base64: (!cardEmbedding && cardImageBase64) ? cardImageBase64 : undefined,
          circle_center: [320, 240],
          circle_radius: 140,
        };

        const response = await fetch(`${EKYC_API_BASE}/match-face`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify(payload),
        });

        if (!response.ok) {
          const errData = (await response.json().catch(() => ({}))) as { detail?: string };
          throw new Error(errData.detail || `Lỗi so khớp khuôn mặt (${response.status})`);
        }

        const data = (await response.json()) as {
          is_match: boolean;
          distance: number;
          confidence_score: number;
          user_instruction?: string;
          live_embedding?: number[];
        };

        return {
          isMatch: data.is_match,
          distance: data.distance,
          confidenceScore: data.confidence_score,
          userInstruction: data.user_instruction,
          faceEmbeddingPreview: data.live_embedding?.slice(0, 16),
          capturedImageBase64: liveFrameBase64,
          isRealAi: true,
        };
      } catch (err) {
        console.warn('[eKYC] Lỗi gọi API so khớp thật, chuyển sang bộ giả lập an toàn:', err);
      }
    }

    // Fallback sang mock
    const mockRes = await mockVerificationService.matchFaceBiometrics(liveFrameBase64);
    return {
      ...mockRes,
      isRealAi: false,
    };
  },
};
