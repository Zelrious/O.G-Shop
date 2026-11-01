export interface UserProfile {
  id: number;
  email: string;
  fullName: string;
  phoneNumber?: string | null;
  avatarUrl?: string | null;
  roles: string[];
  bankName?: string | null;
  bankAccountNumberMasked?: string | null;
  bankAccountHolder?: string | null;
  verifiedSeller: boolean;
  createdAt: string;
}

export interface UpdateProfilePayload {
  fullName?: string;
  phoneNumber?: string;
  avatarUrl?: string;
  quickAuthResultId?: string;
  challengeId?: string;
  otpCode?: string;
}

export interface PhoneOtpChallengeResponse {
  challengeId: string;
  maskedPhone?: string;
  maskedEmail?: string;
  expiresAt: string;
  resendAt: string;
}

export interface PhoneVerificationVerifyPayload {
  challengeId: string;
  code: string;
}

export interface AvatarProfilePayload {
  fullName: string;
  phoneNumber?: string;
}

export interface ChangePasswordPayload {
  currentPassword: string;
  newPassword: string;
}

export interface UpdateBankPayload {
  bankName: string;
  bankAccountNumber: string;
  bankAccountHolder: string;
  quickAuthResultId?: string;
  challengeId?: string;
  otpCode?: string;
}

export interface BankOtpChallengeResponse {
  challengeId: string;
  maskedEmail: string;
  expiresAt: string;
  resendAt: string;
}

export interface PhoneAvailabilityResult {
  available: boolean;
  reason: 'AVAILABLE' | 'SAME_AS_CURRENT' | 'ALREADY_EXISTS' | 'INVALID_FORMAT' | string;
  message: string;
}

export interface UserAddress {
  id: number;
  recipientName: string;
  phoneNumber: string;
  province: string;
  district: string;
  ward: string;
  detailAddress: string;
  ghnProvinceId?: number | null;
  ghnDistrictId?: number | null;
  ghnWardCode?: string | null;
  isDefault: boolean;
  createdAt: string;
}

export interface CreateAddressPayload {
  recipientName: string;
  phoneNumber: string;
  province: string;
  district: string;
  ward: string;
  detailAddress: string;
  ghnProvinceId?: number | null;
  ghnDistrictId?: number | null;
  ghnWardCode?: string | null;
  isDefault?: boolean;
}

export interface UpdateAddressPayload {
  recipientName: string;
  phoneNumber: string;
  province: string;
  district: string;
  ward: string;
  detailAddress: string;
  ghnProvinceId?: number | null;
  ghnDistrictId?: number | null;
  ghnWardCode?: string | null;
  isDefault?: boolean;
}

export interface GhnProvince {
  provinceId: number;
  provinceName: string;
  code?: string;
}

export interface GhnDistrict {
  districtId: number;
  provinceId: number;
  districtName: string;
  code?: string;
}

export interface GhnWard {
  wardCode: string;
  districtId: number;
  wardName: string;
}
