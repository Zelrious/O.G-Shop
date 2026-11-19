import { authApi } from '../auth';

export interface SellerSettings {
  userId: number;
  storeName: string;
  hotlinePhone?: string | null;
  pickupAddressId?: number | null;
  returnAddressId?: number | null;
  bankName?: string | null;
  bankAccountNumberMasked?: string | null;
  bankAccountHolder?: string | null;
  verifiedSeller: boolean;
  ekycState: string;
  hasStoreInfo?: boolean;
  sellerOnboarded?: boolean;
}

export interface UpdateSellerSettingsInput {
  storeName?: string;
  pickupAddressId?: number | null;
  returnAddressId?: number | null;
}

async function parseResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    if (response.status === 403) {
      throw new Error('Tính năng này yêu cầu hoàn tất xác thực eKYC và kích hoạt gian hàng.');
    }
    const body = (await response.json().catch(() => ({}))) as { message?: string };
    throw new Error(body.message || `Yêu cầu thất bại (${response.status}).`);
  }
  return response.json() as Promise<T>;
}

export const sellerSettingsApi = {
  async getSettings(): Promise<SellerSettings> {
    const res = await authApi.authorizedFetch('/seller/settings');
    return parseResponse<SellerSettings>(res);
  },

  async updateSettings(data: UpdateSellerSettingsInput): Promise<SellerSettings> {
    const res = await authApi.authorizedFetch('/seller/settings', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    });
    return parseResponse<SellerSettings>(res);
  },
};
