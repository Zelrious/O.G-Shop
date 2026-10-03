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
