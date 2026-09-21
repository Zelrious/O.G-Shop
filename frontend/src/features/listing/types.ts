import { Category, ProductMedia } from '../marketplace/types';

export interface SellerProductSummary {
  productId: number;
  title: string;
  listedPrice: number;
  currency: string;
  condition: string;
  location: string | null;
  status: string;
  thumbnailUrl: string | null;
  category: Category;
  version: number;
  createdAt: string;
  updatedAt: string | null;
}

export interface SellerProductDetail {
  productId: number;
  title: string;
  description: string;
  listedPrice: number;
  currency: string;
  condition: string;
  usageDuration: string | null;
  defects: string | null;
  repairHistory: string | null;
  includedAccessories: string | null;
  location: string | null;
  status: string;
  thumbnailUrl: string | null;
  category: Category;
  media: ProductMedia[];
  version: number;
  createdAt: string;
  updatedAt: string | null;
}

export interface CreateOrUpdateProductPayload {
  categoryId: number;
  title: string;
  description: string;
  listedPrice: number;
  condition: string;
  usageDuration?: string;
  defects?: string;
  repairHistory?: string;
  includedAccessories?: string;
  location?: string;
  version?: number;
}

export const LISTING_STATUS_LABELS: Record<string, string> = {
  DRAFT: 'Bản nháp',
  ACTIVE: 'Đang bán',
  HIDDEN: 'Đã ẩn',
  RESERVED: 'Đang giữ hàng',
  SOLD: 'Đã bán',
  REJECTED: 'Từ chối',
};

export const LISTING_STATUS_VARIANTS: Record<string, 'draft' | 'active' | 'hidden' | 'reserved' | 'sold' | 'rejected' | 'neutral'> = {
  DRAFT: 'draft',
  ACTIVE: 'active',
  HIDDEN: 'hidden',
  RESERVED: 'reserved',
  SOLD: 'sold',
  REJECTED: 'rejected',
};
