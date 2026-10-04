export interface OrderSummary {
  orderId: number;
  checkoutGroupId: string;
  productId: number | null;
  productTitle: string;
  productThumbnail: string | null;
  unitPrice: number;
  quantity: number;
  totalAmount: number;
  currency: string;
  status: string;
  paymentStatus: string;
  partnerId: number;
  partnerName: string;
  createdAt: string;
  paymentDueAt: string;
  completedAt: string | null;
  cancelledAt: string | null;
}

export interface OrderDetail {
  orderId: number;
  checkoutGroupId: string;
  status: string;
  buyerId: number;
  buyerName: string;
  sellerId: number;
  sellerName: string;
  productId: number | null;
  productTitle: string;
  productThumbnail: string | null;
  unitPrice: number;
  quantity: number;
  shippingRecipientName: string;
  shippingPhoneNumber: string;
  fullShippingAddress: string;
  subtotal: number;
  shippingFee: number;
  buyerSystemFee: number;
  sellerSystemFee: number;
  sellerProceeds: number;
  voucherDiscountAmount: number;
  shippingDiscountAmount: number;
  appliedVoucherCode: string | null;
  totalAmount: number;
  currency: string;
  paymentId: number | null;
  paymentMethod: string | null;
  paymentStatus: string;
  transactionCode: string | null;
  paidAt: string | null;
  heldAt: string | null;
  paymentDueAt: string;
  createdAt: string;
  completedAt: string | null;
  cancelledAt: string | null;
  cancellationReason: string | null;
}

export interface OrderActionResponse {
  orderId: number;
  status: string;
  message: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}
