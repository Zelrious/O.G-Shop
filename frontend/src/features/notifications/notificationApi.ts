import { authApi } from '../auth';

export interface NotificationItem {
  id: number; type: string; title: string; content: string; read: boolean; createdAt: string;
  destination: 'SELLER_LISTING' | 'KTV_MODERATION' | 'ACCOUNT_SELF' | 'KTV_ACCOUNT' | 'CONVERSATION' | 'VOUCHER_WALLET' | 'ADMIN_VOUCHERS' | 'KTV_VOUCHERS' | null; productId: number | null; userId?: number | null; conversationId?: number | null;
}
export interface NotificationPage { items: NotificationItem[]; page: number; size: number; totalElements: number; unreadCount: number }
async function response<T>(value: Response): Promise<T> {
  if (!value.ok) { const body = await value.json().catch(() => ({})); throw new Error(body.message || `Không tải được thông báo (${value.status}).`); }
  return value.json() as Promise<T>;
}
export const notificationApi = {
  async list(page = 0, size = 20) { return response<NotificationPage>(await authApi.authorizedFetch(`/notifications?page=${page}&size=${size}`)); },
  async read(id: number) { const value = await authApi.authorizedFetch(`/notifications/${id}/read`, { method: 'POST' }); if (!value.ok) throw new Error('Chưa cập nhật được trạng thái đã đọc.'); },
};
export function notificationTarget(item: NotificationItem, roles: string[]): string | null {
  if (item.destination === 'VOUCHER_WALLET' && roles.some(role => ['BUYER', 'SELLER'].includes(role)) && !roles.some(role => ['KTV', 'ADMIN'].includes(role))) return '/vouchers';
  if (item.destination === 'ADMIN_VOUCHERS' && roles.includes('ADMIN')) return '/admin/vouchers';
  if (item.destination === 'KTV_VOUCHERS' && roles.includes('KTV')) return '/ktv/vouchers';
  if (item.destination === 'CONVERSATION' && item.conversationId && roles.some(role => ['BUYER', 'SELLER'].includes(role)) && !roles.some(role => ['KTV', 'ADMIN'].includes(role))) return '/messages?conversationId=' + item.conversationId;
  if (item.destination === 'ACCOUNT_SELF' && roles.some(role => ['BUYER', 'SELLER'].includes(role))) return '/account/status';
  if (item.destination === 'KTV_ACCOUNT' && item.userId && roles.includes('KTV')) return `/ktv/users?userId=${item.userId}`;
  if (!item.productId) return null;
  if (item.destination === 'SELLER_LISTING' && roles.includes('SELLER')) return `/seller/listings/${item.productId}/review`;
  if (item.destination === 'KTV_MODERATION' && roles.some(role => ['KTV', 'ADMIN'].includes(role))) return `/ktv/moderation?productId=${item.productId}`;
  return null;
}
