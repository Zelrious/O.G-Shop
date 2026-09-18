import { VerificationRecord, CccdOcrData, BiometricScanResult } from './types';

const STORAGE_VERIFICATIONS_KEY = 'og_shop_mock_verifications';

export const mockVerificationService = {
  getVerification(userId: number): VerificationRecord | null {
    const data = localStorage.getItem(STORAGE_VERIFICATIONS_KEY);
    if (!data) return null;
    try {
      const records: VerificationRecord[] = JSON.parse(data);
      return records.find((r) => r.userId === userId) || null;
    } catch {
      return null;
    }
  },

  // Mô phỏng AI bóc tách OCR từ ảnh thẻ CCCD (YOLO + VietOCR + Gemini Post-processing)
  async processCccdImage(file: File): Promise<CccdOcrData> {
    void file;
    await new Promise((r) => setTimeout(r, 1200)); // Giả lập AI processing

    // Tự sinh số CCCD giả lập từ kích thước file hoặc dùng mẫu thực tế
    const randomSuffix = Math.floor(100000 + Math.random() * 900000);
    const mockCccd: CccdOcrData = {
      cccdNumber: `079098${randomSuffix}`,
      fullName: 'NGUYỄN VĂN AN',
      dob: '15/08/1998',
      hometown: 'Hải Hậu, Nam Định',
      address: 'Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh',
    };

    return mockCccd;
  },

  // Mô phỏng ArcFace so khớp khuôn mặt từ Camera với ảnh thẻ (Cosine Distance <= 0.50)
  async matchFaceBiometrics(livePhotoBase64: string): Promise<BiometricScanResult> {
    await new Promise((r) => setTimeout(r, 1500)); // Giả lập mô hình ArcFace trích xuất vector

    // Giả lập điểm khoảng cách Cosine Distance (0.18 - 0.32: Rất khớp)
    const distance = Number((0.18 + Math.random() * 0.14).toFixed(4));
    const isMatch = distance <= 0.5;

    // Sinh vector 512 chiều mô phỏng
    const sampleVector = Array.from({ length: 16 }, () => Number((Math.random() * 2 - 1).toFixed(4)));

    return {
      isMatch,
      distance,
      faceEmbeddingPreview: sampleVector,
      capturedImageBase64: livePhotoBase64,
    };
  },

  // Nộp hồ sơ và lưu trữ
  async submitVerification(
    userId: number,
    ocrData: CccdOcrData,
    similarityScore: number
  ): Promise<VerificationRecord> {
    await new Promise((r) => setTimeout(r, 800));

    const data = localStorage.getItem(STORAGE_VERIFICATIONS_KEY);
    const records: VerificationRecord[] = data ? JSON.parse(data) : [];

    // Kiểm tra chống trùng số CCCD giữa các tài khoản khác nhau
    const existingSameCccd = records.find(
      (r) => r.ocrData.cccdNumber === ocrData.cccdNumber && r.userId !== userId && r.status === 'VERIFIED'
    );
    if (existingSameCccd) {
      throw new Error(
        `Số CCCD ${ocrData.cccdNumber} đã được xác minh trên một tài khoản khác. Mỗi công dân chỉ được đăng ký một gian hàng duy nhất.`
      );
    }

    // Tạo bản ghi xác minh mới
    const newRecord: VerificationRecord = {
      verificationId: records.length + 1,
      userId,
      verificationMethod: 'AI_EKYC',
      status: 'VERIFIED', // Tự động phê duyệt trong môi trường AI eKYC demo
      ocrData,
      similarityScore,
      submittedAt: new Date().toISOString(),
      reviewedAt: new Date().toISOString(),
    };

    // Cập nhật hoặc thêm mới
    const existingIndex = records.findIndex((r) => r.userId === userId);
    if (existingIndex >= 0) {
      records[existingIndex] = newRecord;
    } else {
      records.push(newRecord);
    }

    localStorage.setItem(STORAGE_VERIFICATIONS_KEY, JSON.stringify(records));
    return newRecord;
  },
};
