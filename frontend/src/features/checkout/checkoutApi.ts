import { authApi } from '../auth/authApi';
import { BuyNowInput, CheckoutPreview, OrderCreated } from './types';

interface ApiErrorBody {
  message?: string;
  code?: string;
}

export interface CheckoutApiError extends Error {
  code?: string;
  status?: number;
}

async function handleResponse<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const errorBody = (await res.json().catch(() => ({}))) as ApiErrorBody;
    const msg = errorBody.message || `Yêu cầu không thành công (${res.status}).`;
    const err = new Error(msg) as CheckoutApiError;
    err.code = errorBody.code;
    err.status = res.status;
    throw err;
  }
  return res.json() as Promise<T>;
}

export const checkoutApi = {
  async getCheckoutPreview(productId: number, voucherCode?: string): Promise<CheckoutPreview> {
    const params = new URLSearchParams({ productId: String(productId) });
    if (voucherCode && voucherCode.trim()) {
      params.append('voucherCode', voucherCode.trim());
    }
    const res = await authApi.authorizedFetch(`/commerce/checkout/preview?${params.toString()}`);
    return handleResponse<CheckoutPreview>(res);
  },

  async buyNow(input: BuyNowInput): Promise<OrderCreated> {
    const res = await authApi.authorizedFetch('/commerce/checkout/buy-now', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    });
    return handleResponse<OrderCreated>(res);
  },

  async getOrder(orderId: number): Promise<OrderCreated> {
    const res = await authApi.authorizedFetch(`/commerce/orders/${orderId}`);
    return handleResponse<OrderCreated>(res);
  },
};
