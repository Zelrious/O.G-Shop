import { authApi } from '../auth';
export interface Campaign {
  id: number; revisionId: number; version: number; code: string; title: string; description: string;
  voucherType: 'ORDER_DISCOUNT' | 'SHIPPING_DISCOUNT'; discountType: 'FIXED_AMOUNT' | 'PERCENTAGE';
  discountValue: number; maxDiscountAmount: number | null; minOrderAmount: number; scope: 'CHECKOUT' | 'PRODUCTS';
  productIds: number[]; quantity: number; startTime: string; endTime: string; active: boolean; selfClaim: boolean; issued: number; used: number;
}
export interface Grant { id: string; userId: number; userName: string; state: 'AVAILABLE' | 'REDEEMED' | 'REVOKED'; grantedAt: string; expiresAt: string; campaign: Campaign }
export interface Wallet { grants: Grant[]; receivable: Campaign[]; points: number; pointsHistory: { delta: number; type: string; reason: string; createdAt: string }[] }
export type Save = Omit<Campaign, 'id' | 'revisionId' | 'issued' | 'used'> & { commandId: string };
export interface Bulk { commandId: string; version: number; action: 'GRANT' | 'REVOKE'; ids: string[]; reason: string }
export interface Result { id: string; userId: number; userName: string; eligible: boolean; status: string; reason: string; grantId: string | null }
export interface Batch { items: Result[]; successful: number; skipped: number }
export interface Targets { items: Result[]; total: number; page: number; size: number; campaign: Campaign }
async function call<T>(path: string, method = 'GET', body?: unknown): Promise<T> {
  const res = await authApi.authorizedFetch(path, { method, ...(body === undefined ? {} : { body: JSON.stringify(body), headers: { 'Content-Type': 'application/json' } }) });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.message || `Chưa xử lý được yêu cầu (${res.status}).`);
  return data as T;
}
export const voucherApi = {
  campaigns: (role: 'admin' | 'ktv') => call<Campaign[]>(`/${role}/vouchers`),
  save: (id: number | null, body: Save) => {
    const fields = ['commandId', 'version', 'code', 'title', 'description', 'voucherType', 'discountType', 'discountValue', 'maxDiscountAmount', 'minOrderAmount', 'scope', 'productIds', 'quantity', 'startTime', 'endTime', 'active', 'selfClaim'] as const;
    return call<Campaign>(`/admin/vouchers${id === null ? '' : `/${id}`}`, id === null ? 'POST' : 'PUT', Object.fromEntries(fields.map(key => [key, body[key]])));
  },
  wallet: () => call<Wallet>('/commerce/vouchers'),
  claim: (c: Campaign, commandId: string) => call<Grant>(`/commerce/vouchers/${c.id}/claim`, 'POST', { commandId, version: c.version }),
  targets: (id: number, filters: Record<string, string>) => call<Targets>(`/ktv/vouchers/${id}/targets?${new URLSearchParams(filters)}`),
  preview: (id: number, body: Bulk) => call<Batch>(`/ktv/vouchers/${id}/preview`, 'POST', body),
  execute: (id: number, body: Bulk) => call<Batch>(`/ktv/vouchers/${id}/commands`, 'POST', body),
};
export const money = (v: number) => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(v);
export function benefit(c: Pick<Campaign, 'discountType' | 'discountValue' | 'maxDiscountAmount' | 'voucherType'>) {
  return `${c.voucherType === 'SHIPPING_DISCOUNT' ? 'Giảm phí vận chuyển' : 'Giảm'} ${c.discountType === 'PERCENTAGE' ? `${c.discountValue}%${c.maxDiscountAmount ? ` · tối đa ${money(c.maxDiscountAmount)}` : ''}` : money(c.discountValue)}`;
}
export const dateTime = (v: string) => new Date(v).toLocaleString('vi-VN', { dateStyle: 'short', timeStyle: 'short' });
