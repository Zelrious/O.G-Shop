import { authApi } from '../auth';
export type AccountScope = 'NEW_LISTINGS' | 'CHAT' | 'BUY_EKYC' | 'BUY_ALL' | 'NEW_TRANSACTIONS';
export type AccountAction = 'RESTRICT' | 'LIFT' | 'LOCK' | 'ACTIVATE' | 'DEACTIVATE' | 'COMPENSATE';
export interface AccountUser { userId: number; fullName: string; email: string; phoneNumber: string | null; roles: string[]; status: string; authenticationStatus: string; version: number; reputation: number; activeRestrictions: number; createdAt: string }
export interface AccountRestriction { id: string; scope: string; imposedBy: number; reason: string; openedAt: string; expiresAt: string | null; closedAt: string | null; legacy: boolean }
export interface AccountOrder { orderId: number; role: 'BUYER' | 'SELLER'; status: string; total: number; createdAt: string; open: boolean }
export interface AccountCase { caseId: number; orderId: number | null; type: string; state: string; summary: string; createdAt: string; responseDeadline: string | null; canRespond: boolean; canCompensate: boolean }
export interface ReputationEvent { id: string; source: string; pointsDelta: number; reason: string; decidedBy: number | null; createdAt: string }
export interface AccountHistory { id: number; actorId: number; targetId: number; action: AccountAction; createdAt: string; request: { reason: string; systemCaseId?: number; targetCaseId?: number }; before: { version: number; state: string }; after: { version: number; state: string } }
export interface Outage { systemCaseId: number; affectedFrom: string; affectedUntil: string }
export interface AccountDetail { user: AccountUser; reason: string | null; expiresAt: string | null; restrictions: AccountRestriction[]; orders: AccountOrder[]; cases: AccountCase[]; reputation: { score: number; events: ReputationEvent[] }; history: AccountHistory[]; historyTotal: number; allowedScopes: AccountScope[]; allowedActions: AccountAction[]; allowedWeeks: number[]; outages: Outage[]; serverTime: string }
export interface AccountPage { items: AccountUser[]; page: number; size: number; totalElements: number }
export interface AccountHistoryPage { items: AccountHistory[]; page: number; size: number; totalElements: number }
export interface AccountCommand { action: AccountAction; expectedVersion: number; commandKey: string; reason: string; scope?: AccountScope; restrictionId?: string; expiresAt?: string; durationWeeks?: number; systemCaseId?: number; targetCaseId?: number; affectedFrom?: string; affectedUntil?: string }
export interface CaseResponse { id: string; actorId: number; text: string; createdAt: string }
export class AccountApiError extends Error { constructor(message: string, readonly status: number, readonly code: string) { super(message); } }
async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await authApi.authorizedFetch(path, init);
  if (!response.ok) { const body = await response.json().catch(() => ({})); throw new AccountApiError(body.message || `Chưa xử lý được yêu cầu (${response.status}).`, response.status, body.code || 'ACCOUNT_REQUEST_FAILED'); }
  return response.json() as Promise<T>;
}
export const accountApi = {
  list: (query = '', status = 'ALL', role = 'ALL', page = 0) => request<AccountPage>(`/ktv/users?${new URLSearchParams({ query, status, role, page: String(page), size: '20' })}`),
  detail: (id: number) => request<AccountDetail>(`/ktv/users/${id}`),
  self: () => request<AccountDetail>('/account'),
  command: (id: number, data: AccountCommand) => request<{ userId: number; version: number; action: string; message: string }>(`/ktv/users/${id}/commands`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data) }),
  history: (userId: number | null, page = 0) => request<AccountHistoryPage>(`${userId === null ? '/account/history' : `/ktv/users/${userId}/history`}?page=${page}&size=20`),
  responses: (caseId: number) => request<CaseResponse[]>(`/account/cases/${caseId}/responses`),
  respond: (caseId: number, text: string, commandKey: string) => request<{ id: string }>(`/account/cases/${caseId}/responses`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ text, commandKey }) }),
};
export const scopeLabels: Record<string, string> = { NEW_LISTINGS: 'Đăng và cập nhật tin', CHAT: 'Chat mới', BUY_EKYC: 'Mua tin yêu cầu eKYC', BUY_ALL: 'Mua hàng nói chung', NEW_TRANSACTIONS: 'Hoạt động mới (đăng/chat/mua)', LOGIN: 'Đăng nhập (hạn chế cũ)' };
export const actionLabels: Record<AccountAction, string> = { RESTRICT: 'Áp dụng hạn chế', LIFT: 'Gỡ hạn chế', LOCK: 'Khóa hoạt động mới', ACTIVATE: 'Kích hoạt lại', DEACTIVATE: 'Ngừng mềm tài khoản', COMPENSATE: 'Bù thời gian phản hồi hồ sơ' };
export const accountStatusLabels: Record<string, string> = { ACTIVE: 'Đang hoạt động', LOCKED: 'Khóa hoạt động mới', SUSPENDED: 'Tạm ngưng', DEACTIVATED: 'Đã ngừng mềm' };
