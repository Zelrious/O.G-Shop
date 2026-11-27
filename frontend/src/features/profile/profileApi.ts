import { authApi } from '../auth';
import {
  BankOtpChallengeResponse,
  ChangePasswordPayload,
  CreateAddressPayload,
  GhnDistrict,
  GhnProvince,
  GhnWard,
  PhoneAvailabilityResult,
  PhoneOtpChallengeResponse,
  PhoneVerificationVerifyPayload,
  UpdateAddressPayload,
  UpdateBankPayload,
  UpdateProfilePayload,
  UserAddress,
  UserProfile,
} from './types';

async function parseResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const body = (await response.json().catch(() => ({}))) as { message?: string };
    throw new Error(body.message || `Yêu cầu thất bại (${response.status}).`);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export const profileApi = {
  async getProfile(): Promise<UserProfile> {
    const res = await authApi.authorizedFetch('/profile');
    return parseResponse<UserProfile>(res);
  },

  async updateProfile(payload: UpdateProfilePayload): Promise<UserProfile> {
    const res = await authApi.authorizedFetch('/profile', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    return parseResponse<UserProfile>(res);
  },

  async updateProfileWithAvatar(
    payload: { fullName: string; phoneNumber?: string },
    avatarFile: File
  ): Promise<UserProfile> {
    const formData = new FormData();
    const profileBlob = new Blob([JSON.stringify(payload)], { type: 'application/json' });
    formData.append('profile', profileBlob);
    formData.append('avatar', avatarFile);

    const res = await authApi.authorizedFetch('/profile/with-avatar', {
      method: 'PUT',
      body: formData,
    });
    return parseResponse<UserProfile>(res);
  },

  async changePassword(payload: ChangePasswordPayload): Promise<void> {
    const res = await authApi.authorizedFetch('/profile/password', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    return parseResponse<void>(res);
  },

  async requestBankChangeOtp(): Promise<BankOtpChallengeResponse> {
    const res = await authApi.authorizedFetch('/profile/bank/otp-challenge', {
      method: 'POST',
    });
    return parseResponse<BankOtpChallengeResponse>(res);
  },

  async requestPhoneChangeOtp(newPhone?: string): Promise<PhoneOtpChallengeResponse> {
    const res = await authApi.authorizedFetch(`/profile/phone/otp-challenge${newPhone ? `?newPhone=${encodeURIComponent(newPhone)}` : ''}`, {
      method: 'POST',
    });
    return parseResponse<PhoneOtpChallengeResponse>(res);
  },

  async checkPhoneAvailability(phoneNumber: string): Promise<PhoneAvailabilityResult> {
    const res = await authApi.authorizedFetch(`/profile/phone/check-availability?phoneNumber=${encodeURIComponent(phoneNumber)}`);
    return parseResponse<PhoneAvailabilityResult>(res);
  },

  async verifyPhoneOtp(payload: PhoneVerificationVerifyPayload): Promise<void> {
    const res = await authApi.authorizedFetch('/profile/phone/verify', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    return parseResponse<void>(res);
  },

  async updateBankAccount(payload: UpdateBankPayload): Promise<UserProfile> {
    const res = await authApi.authorizedFetch('/profile/bank', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    return parseResponse<UserProfile>(res);
  },

  async getAddresses(): Promise<UserAddress[]> {
    const res = await authApi.authorizedFetch('/profile/addresses');
    return parseResponse<UserAddress[]>(res);
  },

  async createAddress(payload: CreateAddressPayload): Promise<UserAddress> {
    const res = await authApi.authorizedFetch('/profile/addresses', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    return parseResponse<UserAddress>(res);
  },

  async updateAddress(addressId: number, payload: UpdateAddressPayload): Promise<UserAddress> {
    const res = await authApi.authorizedFetch(`/profile/addresses/${addressId}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    return parseResponse<UserAddress>(res);
  },

  async deleteAddress(addressId: number): Promise<void> {
    const res = await authApi.authorizedFetch(`/profile/addresses/${addressId}`, {
      method: 'DELETE',
    });
    return parseResponse<void>(res);
  },

  async setDefaultAddress(addressId: number): Promise<UserAddress> {
    const res = await authApi.authorizedFetch(`/profile/addresses/${addressId}/default`, {
      method: 'PUT',
    });
    return parseResponse<UserAddress>(res);
  },

  async getProvinces(): Promise<GhnProvince[]> {
    interface RawProvince {
      provinceId?: number | string;
      ProvinceID?: number | string;
      provinceName?: string;
      ProvinceName?: string;
      code?: string;
      Code?: string;
    }
    const res = await authApi.authorizedFetch('/shipping/master-data/provinces');
    const raw = await parseResponse<RawProvince[]>(res);
    return (raw || [])
      .map((item) => ({
        provinceId: Number(item.provinceId ?? item.ProvinceID),
        provinceName: String(item.provinceName ?? item.ProvinceName ?? ''),
        code: item.code ?? item.Code,
      }))
      .filter((p) => {
        if (!p.provinceId || !p.provinceName) return false;
        const name = p.provinceName.trim().toLowerCase();
        // Lọc bỏ các tỉnh rác/test của GHN Sandbox (như "Hà Nội 02" id=2002 hoặc "Test - Alert - Tỉnh - 001" id=298)
        if (p.provinceId >= 1000) return false;
        if (name.includes('test') || name.includes('alert') || /\b0\d+$/.test(name)) return false;
        return true;
      });
  },

  async getDistricts(provinceId: number): Promise<GhnDistrict[]> {
    interface RawDistrict {
      districtId?: number | string;
      DistrictID?: number | string;
      provinceId?: number | string;
      ProvinceID?: number | string;
      districtName?: string;
      DistrictName?: string;
      code?: string;
      Code?: string;
    }
    const res = await authApi.authorizedFetch(`/shipping/master-data/districts?provinceId=${provinceId}`);
    const raw = await parseResponse<RawDistrict[]>(res);
    return (raw || []).map((item) => ({
      districtId: Number(item.districtId ?? item.DistrictID),
      provinceId: Number(item.provinceId ?? item.ProvinceID ?? provinceId),
      districtName: String(item.districtName ?? item.DistrictName ?? ''),
      code: item.code ?? item.Code,
    }));
  },

  async getWards(districtId: number): Promise<GhnWard[]> {
    interface RawWard {
      wardCode?: string;
      WardCode?: string;
      districtId?: number | string;
      DistrictID?: number | string;
      wardName?: string;
      WardName?: string;
    }
    const res = await authApi.authorizedFetch(`/shipping/master-data/wards?districtId=${districtId}`);
    const raw = await parseResponse<RawWard[]>(res);
    return (raw || []).map((item) => ({
      wardCode: String(item.wardCode ?? item.WardCode ?? ''),
      districtId: Number(item.districtId ?? item.DistrictID ?? districtId),
      wardName: String(item.wardName ?? item.WardName ?? ''),
    }));
  },
};
