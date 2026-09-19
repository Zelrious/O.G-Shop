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
  verificationId?: number;
  isMatch: boolean;
  distance: number;
  confidenceScore?: number;
  userInstruction?: string;
  capturedImageBase64: string;
  isSimulated: boolean;
  ocrData?: CccdOcrData;
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
