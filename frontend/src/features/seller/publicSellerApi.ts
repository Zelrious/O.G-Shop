const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1';

export interface PublicSellerStats {
  averageRating: number | null;
  totalReviews: number;
  activeProductsCount: number;
  completedTransactionsCount: number;
  ratingDistribution: Record<string, number>;
}

export interface PublicSellerProfile {
  sellerId: number;
  displayName: string;
  avatarUrl: string | null;
  ekycVerified: boolean;
  joinedAt: string | null;
  stats: PublicSellerStats;
}

export interface PublicSellerProductSummary {
  productId: number;
  title: string;
  listedPrice: number;
  currency: string;
  condition: string;
  location: string | null;
  thumbnailUrl: string | null;
  categoryName: string;
  quantityAvailable: number;
  requiresBuyerEkyc: boolean;
  createdAt: string;
}

export interface PublicSellerReviewItem {
  reviewId: number;
  reviewerDisplayName: string;
  rating: number;
  comment: string;
  createdAt: string;
}

export interface PublicPageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

async function fetchPublicJson<T>(path: string): Promise<T> {
  const res = await fetch(`${API_BASE_URL}${path}`, {
    method: 'GET',
    cache: 'no-store',
    headers: { Accept: 'application/json' },
  });
  if (res.status === 404) {
    throw new Error('SELLER_PROFILE_NOT_FOUND');
  }
  if (!res.ok) {
    const body = (await res.json().catch(() => ({}))) as { message?: string };
    throw new Error(body.message || `Không thể tải dữ liệu (${res.status}).`);
  }
  return res.json() as Promise<T>;
}

export const publicSellerApi = {
  getPublicProfile(sellerId: number | string): Promise<PublicSellerProfile> {
    return fetchPublicJson<PublicSellerProfile>(`/public/sellers/${sellerId}`);
  },

  getPublicProducts(
    sellerId: number | string,
    page = 0,
    size = 12,
    sort: 'newest' | 'price_asc' | 'price_desc' = 'newest',
  ): Promise<PublicPageResponse<PublicSellerProductSummary>> {
    const qs = new URLSearchParams({
      page: String(page),
      size: String(size),
      sort,
    });
    return fetchPublicJson<PublicPageResponse<PublicSellerProductSummary>>(
      `/public/sellers/${sellerId}/products?${qs.toString()}`,
    );
  },

  getPublicReviews(
    sellerId: number | string,
    page = 0,
    size = 10,
    rating?: number | null,
  ): Promise<PublicPageResponse<PublicSellerReviewItem>> {
    const qs = new URLSearchParams({
      page: String(page),
      size: String(size),
    });
    if (rating && rating >= 1 && rating <= 5) {
      qs.set('rating', String(rating));
    }
    return fetchPublicJson<PublicPageResponse<PublicSellerReviewItem>>(
      `/public/sellers/${sellerId}/reviews?${qs.toString()}`,
    );
  },
};
