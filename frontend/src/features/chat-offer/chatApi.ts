import { authApi } from '../auth/authApi';
export interface ChatProduct { id: number; sellerId: number; title: string; price: number; thumbnail: string | null; status: string; version: number; revisionId: number | null; available: number; returnAllowed: boolean; requiresEkyc: boolean; deleted: boolean }
export interface Conversation { id: number; buyerId: number; sellerId: number; productId: number; title: string; thumbnail: string | null; partnerName: string; role: 'BUYER' | 'SELLER'; lastActivity: string; preview: string; unread: number; blocked: boolean }
export interface ConversationPage { items: Conversation[]; page: number; size: number; total: number; unread: number }
export interface ChatMedia { mime: string; bytes: number; duration: number | null; available: boolean }
export interface ChatMessage { id: number; senderId: number; type: 'TEXT' | 'IMAGE' | 'VIDEO' | 'OFFER' | 'SYSTEM'; content: string; createdAt: string; replyTo: number | null; offerId: number | null; media: ChatMedia | null }
export type OfferAction = 'ACCEPT' | 'REJECT' | 'COUNTER' | 'WITHDRAW';
export interface ChatOffer { id: number; conversationId: number; proposerId: number; parentId: number | null; price: number; status: 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'COUNTERED' | 'WITHDRAWN' | 'EXPIRED'; createdAt: string; expiresAt: string | null; respondedAt: string | null; respondedBy: number | null; version: number; productVersion: number; orderId: number | null; buyerFee: number; sellerFee: number; allowedActions: OfferAction[]; canCheckout: boolean }
export interface ChatQuota { images: number; videos: number; bytes: number; maxImages: number; maxVideos: number; maxBytes: number; date: string }
export interface ChatDetail { conversation: Conversation; product: ChatProduct; messages: { items: ChatMessage[]; nextBefore: number | null }; offers: ChatOffer[]; peerReadMessageId: number; canSend: boolean; canOffer: boolean; restrictionReason: string | null; quota: ChatQuota; serverTime: string }
export interface ProposalInput { clientId: string; price: number; expiresAt?: string }
export interface DecisionInput { commandKey: string; action: OfferAction; expectedVersion: number; price?: number; expiresAt?: string }
export interface BlockedBuyer { buyerId: number; name: string; blockedAt: string; reason: string | null }
export class ChatApiError extends Error { constructor(message: string, readonly status: number, readonly code?: string) { super(message); this.name = 'ChatApiError'; } }
async function result<T>(response: Response): Promise<T> { if (!response.ok) { const body = await response.json().catch(() => ({})) as { message?: string; code?: string }; throw new ChatApiError(body.message || 'Chưa thực hiện được thao tác. Thử lại sau.', response.status, body.code); } return response.status === 204 ? undefined as T : response.json() as Promise<T>; }
async function request(path: string, init: RequestInit = {}): Promise<Response> {
  const timeout = AbortSignal.timeout(init.body instanceof FormData ? 60000 : 20000);
  const signal = init.signal ? AbortSignal.any([init.signal, timeout]) : timeout;
  try { return await authApi.authorizedFetch(path, { ...init, signal }); }
  catch (error) {
    if (init.signal?.aborted) throw error;
    throw new ChatApiError(timeout.aborted ? 'Kết nối quá thời gian chờ. Giữ bản soạn và thử lại.' : 'Chưa kết nối được máy chủ. Kiểm tra mạng và thử lại.', 0);
  }
}
async function post<T>(path: string, data: unknown): Promise<T> { return result<T>(await request('/chat' + path, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data) })); }
export const chatApi = {
  async list(query = '', page = 0, signal?: AbortSignal) { return result<ConversationPage>(await request('/chat/conversations?' + new URLSearchParams({ query, page: String(page), size: '20' }), { signal })); },
  start(productId: number, orderId?: number) { return post<ChatDetail>('/conversations', { productId, orderId }); },
  async detail(id: number, before?: number, signal?: AbortSignal) { return result<ChatDetail>(await request('/chat/conversations/' + id + (before ? '?before=' + before : ''), { signal })); },
  send(id: number, clientId: string, text: string, replyTo?: number) { return post<ChatMessage>('/conversations/' + id + '/messages', { clientId, text, replyTo }); },
  read(id: number, messageId: number) { return post<void>('/conversations/' + id + '/read', { messageId }); },
  propose(id: number, input: ProposalInput) { return post<ChatOffer>('/conversations/' + id + '/offers', input); },
  decide(id: number, input: DecisionInput) { return post<ChatOffer>('/offers/' + id + '/decision', input); },
  async media(id: number, clientId: string, file: File, caption = '') { const body = new FormData(); body.append('clientId', clientId); body.append('file', file); body.append('caption', caption); return result<ChatMessage>(await request('/chat/conversations/' + id + '/media', { method: 'POST', body })); },
  async mediaBlob(id: number, signal?: AbortSignal) { const response = await request('/chat/messages/' + id + '/media', { signal }); if (!response.ok) await result<never>(response); return response.blob(); },
  async blocks() { return result<BlockedBuyer[]>(await request('/chat/blocks')); },
  async searchBuyers(query: string) { return result<{ userId: number; name: string }[]>(await request('/chat/blocks/search?' + new URLSearchParams({ query }))); },
  block(buyerId: number, blocked: boolean, reason?: string) { return post<BlockedBuyer[]>('/blocks', { buyerId, blocked, reason }); },
};
export const offerLabels: Record<ChatOffer['status'], string> = { PENDING: 'Chờ phản hồi', ACCEPTED: 'Đã chấp nhận', REJECTED: 'Đã từ chối', COUNTERED: 'Đã trả giá khác', WITHDRAWN: 'Đã rút', EXPIRED: 'Đã hết hạn' };
export const money = (value: number) => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(value);
