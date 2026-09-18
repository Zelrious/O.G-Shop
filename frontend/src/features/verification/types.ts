export type VerificationStatus = 'NONE' | 'PENDING' | 'VERIFIED' | 'REJECTED';

export interface CccdOcrData {
  cccdNumber: string;
  fullName: string;
  dob: string;
  gender?: string;
  hometown: string;
  address: string;
}

export interface BiometricScanResult {
  isMatch: boolean;
  distance: number;              // Cosine distance (<= 0.50 là match)
  confidenceScore?: number;       // Điểm tin cậy (0 - 1.0)
  userInstruction?: string;      // Hướng dẫn khuôn mặt
  faceEmbeddingPreview?: number[]; // 512 dimensions (rút gọn preview 16 phần tử)
  capturedImageBase64: string;
  isRealAi?: boolean;            // Đánh dấu kết quả từ mô hình AI thật
}

export interface VerificationRecord {
  verificationId: number;
  userId: number;
  verificationMethod: 'MANUAL_ID_DOCUMENT' | 'AI_EKYC';
  status: VerificationStatus;
  ocrData: CccdOcrData;
  similarityScore?: number;
  submittedAt: string;
  reviewedAt?: string;
  rejectionReason?: string;
}
