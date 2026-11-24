import { authApi } from '../auth/authApi';
import { BuyNowInput, CheckoutPreview, OrderCreated } from './types';

interface ApiErrorBody {
  message?: string;
  code?: string;
}

export interface CheckoutApiError extends Error {
  code?: string;
  status?: number;
}

async function handleResponse<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const errorBody = (await res.json().catch(() => ({}))) as ApiErrorBody;
    const msg = errorBody.message || `Yêu cầu không thành công (${res.status}).`;
    const err = new Error(msg) as CheckoutApiError;
    err.code = errorBody.code;
    err.status = res.status;
    throw err;
  }
  return res.json() as Promise<T>;
}

export function createBuyNowCommandKey(productId: number): string {
  const randomPart =
    typeof globalThis.crypto !== 'undefined' && typeof globalThis.crypto.randomUUID === 'function'
      ? globalThis.crypto.randomUUID()
      : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
  return `BUY-NOW-${productId}-${randomPart}`;
}

export interface GroupCheckoutInput {
  commandKey?: string;
  shippingAddress: {
    recipientName: string;
    phoneNumber: string;
    province: string;
    district: string;
    ward: string;
    detailAddress: string;
  };
  deliveryMethod: 'CARRIER' | 'SELF_PICKUP' | 'SELF_DELIVERY';
  carrier?: string | null;
  paymentMethod: string;
  voucherCode?: string | null;
  coinsRequested?: number;
  items: Array<{ productId: number; quantity: number; acceptedOfferId?: number | null }>;
  quickAuthResultId?: string | null;
}

export interface GroupCheckoutResponse {
  groupId: string;
  buyerId: number;
  commandKey: string;
  state: 'READY' | 'PAID' | 'CANCELLED' | 'EXPIRED';
  expectedTotal: number;
  groupSubtotal: number;
  groupShippingFee: number;
  groupVoucherDiscount: number;
  groupPointsDiscount: number;
  coinsUsed: number;
  orders: Array<{
    orderId: number;
    sellerId: number;
    totalAmount: number;
    status: string;
  }>;
}

export interface CoinAdjustmentResult {
  adjustedCoins: number;
  wasAdjusted: boolean;
  coinsOnGoods: number;
  coinsOnShipping: number;
  expectedTotal: number;
  isZeroAmount: boolean;
}

/**
 * Mirrors V58 backend rule: Voucher takes priority first, then Coins offset remaining goods
 * and remaining shipping fee. Excess requested coins are automatically clamped so the buyer
 * never loses coins when applying a voucher after selecting coins.
 */
export function calculateAdjustedCoinsAfterVoucher(params: {
  subtotal: number;
  shippingFee: number;
  goodsVoucherDiscount: number;
  shippingVoucherDiscount: number;
  coinsRequested: number;
  coinBalance: number;
  maxRedemptionPct?: number;
}): CoinAdjustmentResult {
  const remainingGoods = Math.max(0, params.subtotal - params.goodsVoucherDiscount);
  const remainingShipping = Math.max(0, params.shippingFee - params.shippingVoucherDiscount);
  const maxPayableAfterVoucher = remainingGoods + remainingShipping;
  const pct = params.maxRedemptionPct ?? 100;
  const maxByPolicy = Math.floor((maxPayableAfterVoucher * pct) / 100);
  const maxAllowed = Math.max(0, Math.min(params.coinBalance, maxByPolicy));
  const requested = Math.max(0, Math.floor(params.coinsRequested));
  const adjustedCoins = Math.min(requested, maxAllowed);
  const coinsOnGoods = Math.min(adjustedCoins, remainingGoods);
  const coinsOnShipping = Math.min(adjustedCoins - coinsOnGoods, remainingShipping);
  const expectedTotal = Math.max(0, maxPayableAfterVoucher - adjustedCoins);
  return {
    adjustedCoins,
    wasAdjusted: adjustedCoins < requested,
    coinsOnGoods,
    coinsOnShipping,
    expectedTotal,
    isZeroAmount: expectedTotal === 0,
  };
}

export const checkoutApi = {
  async getCheckoutPreview(productId: number, voucherCode?: string, offerId?: number): Promise<CheckoutPreview> {
    const params = new URLSearchParams({ productId: String(productId) });
    if (offerId) params.set('offerId', String(offerId));
    if (voucherCode && voucherCode.trim()) {
      params.append('voucherCode', voucherCode.trim());
    }
    const res = await authApi.authorizedFetch(`/commerce/checkout/preview?${params.toString()}`);
    return handleResponse<CheckoutPreview>(res);
  },

  async buyNow(input: BuyNowInput): Promise<OrderCreated> {
    const commandKey = input.commandKey && input.commandKey.trim()
      ? input.commandKey.trim()
      : createBuyNowCommandKey(input.productId);
    const res = await authApi.authorizedFetch('/commerce/checkout/buy-now', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ...input, commandKey }),
    });
    return handleResponse<OrderCreated>(res);
  },

  async groupCheckout(input: GroupCheckoutInput): Promise<GroupCheckoutResponse> {
    const primaryProductId = input.items[0]?.productId ?? 0;
    const commandKey = input.commandKey && input.commandKey.trim()
      ? input.commandKey.trim()
      : createBuyNowCommandKey(primaryProductId);
    const res = await authApi.authorizedFetch('/commerce/checkout/group', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ...input, commandKey }),
    });
    return handleResponse<GroupCheckoutResponse>(res);
  },

  async getOrder(orderId: number): Promise<OrderCreated> {
    const res = await authApi.authorizedFetch(`/commerce/orders/${orderId}`);
    return handleResponse<OrderCreated>(res);
  },
};
