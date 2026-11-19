export type DisputeVerdictAction = 'RETURN_APPROVED' | 'RESOLVED_REFUND' | 'RESOLVED_RELEASE';

export type DisputeStatus =
  | 'WAITING_SELLER_RESPONSE'
  | 'WAITING_KTV_REVIEW'
  | 'SELLER_REJECTED'
  | 'RETURN_APPROVED'
  | 'RESOLVED_REFUND'
  | 'RESOLVED_RELEASE'
  | 'CLOSED';

export interface DisputeSummary {
  disputeId: number;
  orderId: number;
  orderCode: string;
  productTitle: string;
  disputeAmount: number;
  reason: string;
  status: DisputeStatus | string;
  createdAt: string;
  buyerName: string;
  sellerName: string;
}

export interface DisputePageResponse {
  items: DisputeSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface DisputeDetail {
  disputeId: number;
  orderId: number;
  orderCode: string;
  buyerId: number;
  buyerName: string;
  buyerEmail: string;
  sellerId: number;
  sellerName: string;
  sellerEmail: string;
  productTitle: string;
  disputeAmount: number;
  reason: string;
  description: string;
  buyerEvidenceUrls: string[];
  videoUnboxingUrl: string | null;
  sellerResponse: string | null;
  sellerEvidenceUrls: string[];
  sellerResponseDueAt: string | null;
  status: DisputeStatus | string;
  verdictAction: string | null;
  verdictNotes: string | null;
  refundAmount: number | null;
  penaltyPoints: number | null;
  resolvedBy: number | null;
  resolvedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface DisputeDecisionRequest {
  verdictAction: DisputeVerdictAction;
  verdictNotes: string;
  penaltyPoints?: number;
}

export interface DisputeDecisionResponse {
  disputeId: number;
  verdictAction: string;
  status: string;
  message: string;
}

export interface CreateDisputeRequest {
  orderId: number;
  reason: string;
  description?: string;
  buyerEvidenceUrls?: string[];
  videoUnboxingUrl?: string;
}

export interface SellerDisputeResponseRequest {
  sellerResponse: string;
  sellerEvidenceUrls?: string[];
}

export interface OrderReview {
  reviewId: number;
  orderId: number;
  reviewerId: number;
  reviewerName: string;
  revieweeId: number;
  revieweeName: string;
  rating: number;
  comment: string;
  createdAt: string;
  updatedAt: string;
}

export interface OrderReviewsResponse {
  orderId: number;
  buyerReview: OrderReview | null;
  sellerReview: OrderReview | null;
}

export interface UserReviewsSummary {
  userId: number;
  averageRating: number;
  totalReviews: number;
  reviews: OrderReview[];
}
