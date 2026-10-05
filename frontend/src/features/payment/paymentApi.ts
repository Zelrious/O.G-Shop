import { authApi } from '../auth/authApi';
import { PaymentInfo, ProcessMockPaymentPayload, PaymentProcessResult } from './types';

interface ApiErrorBody {
  message?: string;
  code?: string;
}

export interface PaymentApiError extends Error {
  code?: string;
  status?: number;
}

async function handleResponse<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const errorBody = (await res.json().catch(() => ({}))) as ApiErrorBody;
    const msg = errorBody.message || `Giao dịch không thành công (${res.status}).`;
    const err = new Error(msg) as PaymentApiError;
    err.code = errorBody.code;
    err.status = res.status;
    throw err;
  }
  return res.json() as Promise<T>;
}

export const paymentApi = {
  async createVnPayUrl(orderId: number): Promise<string> {
    const res = await authApi.authorizedFetch('/payments/vnpay-url', {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ orderId }),
    });
    const result = await handleResponse<{ paymentUrl: string }>(res);
    const url = new URL(result.paymentUrl);
    if (url.protocol !== 'https:' || !['sandbox.vnpayment.vn', 'pay.vnpay.vn'].includes(url.hostname)) {
      throw new Error('Địa chỉ thanh toán VNPAY không hợp lệ.');
    }
    return result.paymentUrl;
  },
  async getPaymentInfo(orderId: number): Promise<PaymentInfo> {
    const res = await authApi.authorizedFetch(`/payments/orders/${orderId}`);
    return handleResponse<PaymentInfo>(res);
  },

  async processMockPayment(payload: ProcessMockPaymentPayload): Promise<PaymentProcessResult> {
    const res = await authApi.authorizedFetch('/payments/mock-process', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    return handleResponse<PaymentProcessResult>(res);
  },
};
