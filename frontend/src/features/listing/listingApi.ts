import { authApi } from '../auth';
import { PageResponse } from '../marketplace/types';
import {
  ApproveProductPayload,
  CreateOrUpdateProductPayload,
  ModerationProduct,
  RejectProductPayload,
  SellerProductDetail,
  SellerProductSummary,
} from './types';

export class ApiError extends Error {
  status: number;
  code?: string;
  fields?: Array<{ field: string; message: string }>;

  constructor(message: string, status: number, code?: string, fields?: Array<{ field: string; message: string }>) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.fields = fields;
  }
}

async function handleResponse<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new ApiError(
      body.message || `Yêu cầu thất bại (${res.status}).`,
      res.status,
      body.code,
      body.fields
    );
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

  async submitProduct(productId: number | string): Promise<SellerProductDetail> {
    const res = await authApi.authorizedFetch(`/seller/products/${productId}/submit`, {
      method: 'POST',
    });
    return handleResponse<SellerProductDetail>(res);
  },

  async uploadMedia(
    productId: number | string,
    file: File,
    mediaType: 'IMAGE' | 'VIDEO'
  ): Promise<{ mediaId: number; mediaUrl: string; mediaType: string }> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('mediaType', mediaType);

    const res = await authApi.authorizedFetch(`/seller/products/${productId}/media`, {
      method: 'POST',
      body: formData,
    });
    return handleResponse<{ mediaId: number; mediaUrl: string; mediaType: string }>(res);
  },

  async deleteMedia(productId: number | string, mediaId: number | string): Promise<void> {
    const res = await authApi.authorizedFetch(`/seller/products/${productId}/media/${mediaId}`, {
      method: 'DELETE',
    });
    if (!res.ok && res.status !== 204) {
      const body = await res.json().catch(() => ({}));
      throw new Error(body.message || 'Xóa media thất bại.');
    }
  },

  async hideProduct(productId: number | string): Promise<SellerProductDetail> {
    const res = await authApi.authorizedFetch(`/seller/products/${productId}/hide`, {
      method: 'POST',
    });
    return handleResponse<SellerProductDetail>(res);
  },

  // UC70 Moderation
  async getPendingProducts(page = 0, size = 20): Promise<PageResponse<ModerationProduct>> {
    const res = await authApi.authorizedFetch(`/moderation/products?page=${page}&size=${size}`);
    return handleResponse<PageResponse<ModerationProduct>>(res);
  },

  async getModerationDetail(productId: number | string): Promise<ModerationProduct> {
    const res = await authApi.authorizedFetch(`/moderation/products/${productId}`);
    return handleResponse<ModerationProduct>(res);
  },

  async approveProduct(
    productId: number | string,
    payload: ApproveProductPayload
  ): Promise<{ productId: number; status: string; message: string }> {
    const res = await authApi.authorizedFetch(`/moderation/products/${productId}/approve`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    return handleResponse<{ productId: number; status: string; message: string }>(res);
  },

  async rejectProduct(
    productId: number | string,
    payload: RejectProductPayload
  ): Promise<{ productId: number; status: string; message: string }> {
    const res = await authApi.authorizedFetch(`/moderation/products/${productId}/reject`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    return handleResponse<{ productId: number; status: string; message: string }>(res);
  },
};
