export interface PaymentInfo {
  orderId: number;
  paymentId: number | null;
  productTitle: string;
  productThumbnail: string | null;
  totalAmount: number;
  currency: string;
  orderStatus: string;
  paymentStatus: string;
  paymentMethod: string;
  transactionCode: string | null;
  paymentDueAt: string;
  remainingSeconds: number;
  qrCodeMockUrl: string;
  bankName: string;
  accountNumber: string;
  accountName: string;
  transferContent: string;
  recipientName?: string;
  phoneNumber?: string;
  fullAddress?: string;
  subtotal?: number;
  shippingFee?: number;
  voucherDiscount?: number;
  sellerId?: number;
}

export interface ProcessMockPaymentPayload {
  orderId: number;
  paymentMethod: string;
  simulateSuccess: boolean;
}

export interface PaymentProcessResult {
  paymentId: number;
  orderId: number;
  orderStatus: string;
  paymentStatus: string;
  transactionCode: string | null;
  message: string;
}
