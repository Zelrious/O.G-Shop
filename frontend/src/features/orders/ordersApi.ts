import { authApi } from '../auth/authApi';
import { OrderSummary, OrderDetail, OrderActionResponse, PageResponse } from './types';

interface ApiErrorBody {
  message?: string;
  code?: string;
}

export interface ApiError extends Error {
  code?: string;
  status?: number;
}

async function handleResponse<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const errorBody = (await res.json().catch(() => ({}))) as ApiErrorBody;
    const msg = errorBody.message || `Yêu cầu không thành công (${res.status}).`;
    const err = new Error(msg) as ApiError;
    err.code = errorBody.code;
    err.status = res.status;
    throw err;
  }
  return res.json() as Promise<T>;
}

export const ordersApi = {
  // Buyer API
  async getBuyerOrders(status?: string, page = 0, size = 10): Promise<PageResponse<OrderSummary>> {
    const params = new URLSearchParams({
      page: String(page),
      size: String(size),
    });
    if (status && status !== 'ALL') {
      params.append('status', status);
    }
    const res = await authApi.authorizedFetch(`/commerce/orders?${params.toString()}`);
    return handleResponse<PageResponse<OrderSummary>>(res);
  },

  async getBuyerOrderDetail(orderId: number): Promise<OrderDetail> {
    const res = await authApi.authorizedFetch(`/commerce/orders/${orderId}/detail`);
    return handleResponse<OrderDetail>(res);
  },

  async cancelBuyerOrder(orderId: number, reason?: string): Promise<OrderActionResponse> {
    const res = await authApi.authorizedFetch(`/commerce/orders/${orderId}/cancel`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ reason }),
    });
    return handleResponse<OrderActionResponse>(res);
  },

  // Seller API
  async getSellerOrders(status?: string, page = 0, size = 10): Promise<PageResponse<OrderSummary>> {
    const params = new URLSearchParams({
      page: String(page),
      size: String(size),
    });
    if (status && status !== 'ALL') {
      params.append('status', status);
    }
    const res = await authApi.authorizedFetch(`/commerce/seller/orders?${params.toString()}`);
    return handleResponse<PageResponse<OrderSummary>>(res);
  },

  async getSellerOrderDetail(orderId: number): Promise<OrderDetail> {
    const res = await authApi.authorizedFetch(`/commerce/seller/orders/${orderId}/detail`);
    return handleResponse<OrderDetail>(res);
  },

  async confirmSellerOrder(orderId: number): Promise<OrderActionResponse> {
    const res = await authApi.authorizedFetch(`/commerce/seller/orders/${orderId}/confirm`, {
      method: 'POST',
    });
    return handleResponse<OrderActionResponse>(res);
  },
};
