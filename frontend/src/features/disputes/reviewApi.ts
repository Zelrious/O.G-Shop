import { authApi } from '../auth';
import type { OrderReview, OrderReviewsResponse, UserReviewsSummary } from './types';

export class ReviewApiError extends Error {
  constructor(message: string, readonly status: number, readonly code: string) {
    super(message);
    this.name = 'ReviewApiError';
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await authApi.authorizedFetch(path, init);
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    throw new ReviewApiError(
      body.message || `Yêu cầu đánh giá không thành công (${response.status}).`,
      response.status,
      body.code || 'REVIEW_REQUEST_FAILED'
    );
  }
  return response.json() as Promise<T>;
}

export const reviewApi = {
  createReview: (orderId: number, rating: number, comment?: string) => {
    return request<OrderReview>(`/commerce/orders/${orderId}/review`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ orderId, rating, comment: comment || '' }),
    });
  },

  getOrderReviews: (orderId: number) => {
    return request<OrderReviewsResponse>(`/commerce/orders/${orderId}/review`);
  },

  getUserReviews: (userId: number, limit = 10) => {
    return request<UserReviewsSummary>(`/commerce/users/${userId}/reviews?limit=${limit}`);
  },
};
