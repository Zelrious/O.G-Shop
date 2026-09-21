export interface Category {
  categoryId: number;
  categoryName: string;
  slug: string;
  description: string;
  displayOrder: number;
}

export interface SellerSummary {
  sellerId: number;
  displayName: string;
  trustLabel: string;
}

export interface ProductMedia {
  mediaId: number;
  mediaType: string;
  mediaUrl: string;
  displayOrder: number;
}

export interface ProductSummary {
  productId: number;
  title: string;
  listedPrice: number;
  currency: string;
  condition: string;
  location: string | null;
  thumbnailUrl: string | null;
  category: Category;
  seller: SellerSummary;
  createdAt: string;
}

export interface ProductDetail {
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
  thumbnailUrl: string | null;
  category: Category;
  seller: SellerSummary;
  media: ProductMedia[];
  createdAt: string;
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

export interface ProductFilterParams {
  query?: string;
  categoryId?: number;
  condition?: string;
  minPrice?: number;
  maxPrice?: number;
  page?: number;
  size?: number;
  sort?: string;
}

export const CONDITION_LABELS: Record<string, string> = {
  LIKE_NEW: 'Như mới (Like New)',
  GOOD: 'Tốt (Đã dùng kỹ)',
  FAIR: 'Khá (Trầy xước nhẹ)',
  POOR: 'Cũ (Có hao mòn)',
  FOR_PARTS: 'Rã xác / Lấy linh kiện',
};
