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
  fullName: string;
  phoneNumber?: string;
  avatarUrl?: string;
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
}

export interface UserAddress {
  id: number;
  recipientName: string;
  phoneNumber: string;
  province: string;
  district: string;
  ward: string;
  detailAddress: string;
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
  isDefault?: boolean;
}

export interface UpdateAddressPayload {
  recipientName: string;
  phoneNumber: string;
  province: string;
  district: string;
  ward: string;
  detailAddress: string;
  isDefault?: boolean;
}
