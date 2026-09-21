import { authApi } from '../auth';
import { PageResponse } from '../marketplace/types';
import { CreateOrUpdateProductPayload, SellerProductDetail, SellerProductSummary } from './types';

async function handleResponse<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(body.message || `Yêu cầu thất bại (${res.status}).`);
  }
  return res.json() as Promise<T>;
}

export const listingApi = {
  async getSellerProducts(page = 0, size = 20): Promise<PageResponse<SellerProductSummary>> {
    const res = await authApi.authorizedFetch(`/seller/products?page=${page}&size=${size}`);
    return handleResponse<PageResponse<SellerProductSummary>>(res);
  },

  async getSellerProduct(productId: number | string): Promise<SellerProductDetail> {
    const res = await authApi.authorizedFetch(`/seller/products/${productId}`);
    return handleResponse<SellerProductDetail>(res);
  },

  async createProduct(payload: CreateOrUpdateProductPayload): Promise<SellerProductDetail> {
    const res = await authApi.authorizedFetch('/seller/products', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    return handleResponse<SellerProductDetail>(res);
  },

  async updateProduct(productId: number | string, payload: CreateOrUpdateProductPayload): Promise<SellerProductDetail> {
    const res = await authApi.authorizedFetch(`/seller/products/${productId}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    return handleResponse<SellerProductDetail>(res);
  },

  async publishProduct(productId: number | string): Promise<SellerProductDetail> {
    const res = await authApi.authorizedFetch(`/seller/products/${productId}/publish`, {
      method: 'POST',
    });
    return handleResponse<SellerProductDetail>(res);
  },

  async hideProduct(productId: number | string): Promise<SellerProductDetail> {
    const res = await authApi.authorizedFetch(`/seller/products/${productId}/hide`, {
      method: 'POST',
    });
    return handleResponse<SellerProductDetail>(res);
  },
};
