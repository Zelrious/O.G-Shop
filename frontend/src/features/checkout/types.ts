export interface AddressSummary {
  addressId: number;
  recipientName: string;
  phoneNumber: string;
  province: string;
  district: string;
  ward: string;
  detailAddress: string;
  isDefault: boolean;
}

export interface CreateAddressInput {
  recipientName: string;
  phoneNumber: string;
  province: string;
  district: string;
  ward: string;
  detailAddress: string;
  isDefault: boolean;
}

export interface VoucherSummary {
  voucherId: number;
  code: string;
  title: string;
  voucherType: string;
  discountType: string;
  discountValue: number;
  maxDiscountAmount?: number;
  minOrderAmount: number;
  sponsorType: string;
}

export interface CheckoutPreview {
  productId: number;
  productTitle: string;
  thumbnailUrl: string | null;
  sellerId: number;
  sellerName: string;
  listedPrice: number;
  shippingFee: number;
  buyerSystemFee: number;
  voucherDiscount: number;
  shippingDiscount: number;
  totalAmount: number;
  addresses: AddressSummary[];
  selectedAddress: AddressSummary | null;
  availableVouchers: VoucherSummary[];
  appliedVoucher: VoucherSummary | null;
}

export interface BuyNowInput {
  productId: number;
  addressId?: number;
  newAddress?: CreateAddressInput;
  voucherCode?: string;
}

export interface OrderCreated {
  orderId: number;
  checkoutGroupId: string;
  productId: number;
  productTitle: string;
  status: string;
  subtotal: number;
  shippingFee: number;
  buyerSystemFee: number;
  voucherDiscount: number;
  shippingDiscount: number;
  totalAmount: number;
  recipientName: string;
  phoneNumber: string;
  fullAddress: string;
  paymentDueAt: string;
  createdAt: string;
}
