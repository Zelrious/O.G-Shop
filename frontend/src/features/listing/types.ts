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
  categories?: Category[];
  requiresBuyerEkyc?: boolean;
  version: number;
  rejectionReason?: string | null;
  reviewedAt?: string | null;
  createdAt: string;
  updatedAt: string | null;
}

export interface SellerProductDetail {
  productId: number;
  sellerId?: number;
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
  categories?: Category[];
  media: ProductMedia[];
  requiresBuyerEkyc: boolean;
  version: number;
  rejectionReason?: string | null;
  reviewedAt?: string | null;
  createdAt: string;
  updatedAt: string | null;
}

export interface CreateOrUpdateProductPayload {
  categoryIds: number[];
  categoryId?: number;
  title: string;
  description: string;
  listedPrice: number;
  condition: string;
  usageDuration?: string;
  defects?: string;
  repairHistory?: string;
  includedAccessories?: string;
  location?: string;
  requiresBuyerEkyc?: boolean;
  version?: number;
}

export interface MediaUploadResponse {
  mediaId: number;
  productId: number;
  mediaType: string;
  mediaUrl: string;
  displayOrder: number;
  thumbnailUrl?: string;
  durationSeconds?: number;
  fileSizeBytes?: number;
}

export interface ModerationProduct {
  quantity?: number;
  sellerName?: string;
  history?: Array<{ decisionId: number; reviewerId: number; decision: string; reason: string | null; contentRevision: number | null; reviewedAt: string }>;
  productId: number;
  sellerId: number;
  title: string;
  description: string;
  listedPrice: number;
  currency: string;
  condition: string;
  usageDuration?: string | null;
  defects?: string | null;
  repairHistory?: string | null;
  includedAccessories?: string | null;
  location?: string | null;
  status: string;
  category?: Category;
  categories?: Category[];
  media: ProductMedia[];
  requiresBuyerEkyc: boolean;
  version: number;
  createdAt: string;
  updatedAt?: string | null;
}

export const LISTING_STATUS_LABELS: Record<string, string> = {
  APPROVED_AWAITING_FEE: 'Đã duyệt, chờ phí',
  DRAFT: 'Bản nháp',
  PENDING: 'Chờ kiểm duyệt',
  ACTIVE: 'Đang bán',
  HIDDEN: 'Đã ẩn',
  RESERVED: 'Đang giữ hàng',
  SOLD: 'Đã bán',
  REJECTED: 'Bị từ chối',
};

export interface ListingFeeSummary {
  productId: number; title: string; status: string; version: number; quantity: number;
  chargeId: string | null; policyCode: string | null; listedTotal: number | null;
  computedFee: number; totalPaid: number; amountDue: number; paymentState: string;
}

export const LISTING_STATUS_VARIANTS: Record<string, 'draft' | 'pending' | 'active' | 'hidden' | 'reserved' | 'sold' | 'rejected' | 'neutral'> = {
  APPROVED_AWAITING_FEE: 'pending',
  DRAFT: 'draft',
  PENDING: 'pending',
  ACTIVE: 'active',
  HIDDEN: 'hidden',
  RESERVED: 'reserved',
  SOLD: 'sold',
  REJECTED: 'rejected',
};

export interface ApproveProductPayload {
  expectedVersion: number;
  commandKey: string;
}

export interface RejectProductPayload {
  reason: string;
  expectedVersion: number;
  commandKey: string;
}
