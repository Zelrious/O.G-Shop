import { authApi } from '../auth';
import type {
  DisputePageResponse,
  DisputeDetail,
  DisputeDecisionRequest,
  DisputeDecisionResponse,
  CreateDisputeRequest,
  SellerDisputeResponseRequest,
} from './types';

export class DisputeApiError extends Error {
  constructor(message: string, readonly status: number, readonly code: string) {
    super(message);
    this.name = 'DisputeApiError';
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await authApi.authorizedFetch(path, init);
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    throw new DisputeApiError(
      body.message || `Yêu cầu tranh chấp không thành công (${response.status}).`,
      response.status,
      body.code || 'DISPUTE_REQUEST_FAILED'
    );
  }
  return response.json() as Promise<T>;
}

export const disputeApi = {
  getDisputes: (params: { status?: string; query?: string; page?: number; size?: number } = {}) => {
    const query = new URLSearchParams();
    if (params.status && params.status !== 'ALL') query.set('status', params.status);
    if (params.query) query.set('query', params.query);
    if (params.page !== undefined) query.set('page', String(params.page));
    if (params.size !== undefined) query.set('size', String(params.size));
    const qs = query.toString();
    return request<DisputePageResponse>(`/ktv/disputes${qs ? `?${qs}` : ''}`);
  },

  getDisputeDetail: (disputeId: number) => {
    return request<DisputeDetail>(`/ktv/disputes/${disputeId}`);
  },

  submitDecision: (disputeId: number, data: DisputeDecisionRequest) => {
    return request<DisputeDecisionResponse>(`/ktv/disputes/${disputeId}/decision`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    });
  },

  createBuyerDispute: (orderId: number, data: Omit<CreateDisputeRequest, 'orderId'>) => {
    return request<DisputeDetail>(`/commerce/orders/${orderId}/dispute`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ...data, orderId }),
    });
  },

  getDisputeByOrder: (orderId: number) => {
    return request<DisputeDetail>(`/commerce/orders/${orderId}/dispute`);
  },

  submitSellerResponse: (orderId: number, data: SellerDisputeResponseRequest) => {
    return request<DisputeDetail>(`/commerce/orders/${orderId}/dispute/seller-response`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    });
  },

  submitReturnShipment: (orderId: number, data?: { trackingCode?: string; carrier?: string }) => {
    return request<DisputeDetail>(`/commerce/orders/${orderId}/dispute/return-shipment`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data || {}),
    });
  },

  confirmReturnReceived: (orderId: number) => {
    return request<DisputeDetail>(`/commerce/orders/${orderId}/dispute/confirm-return-received`, {
      method: 'POST',
    });
  },
};
