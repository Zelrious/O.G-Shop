import { authApi } from '../auth';
import {
  ChangePasswordPayload,
  CreateAddressPayload,
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
  return response.status === 204 ? (undefined as T) : ((await response.json()) as T);
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
};
