import { authApi } from '../auth/authApi';

export type ReturnReasonCode = 'CHANGE_MIND' | 'NOT_AS_DESCRIBED' | 'DAMAGED_OR_DEFECTIVE';

export interface ReturnEvidenceItemInput {
  mediaType: 'IMAGE' | 'VIDEO' | 'TEXT';
  objectKey?: string | null;
  textContent?: string | null;
  contentDigest?: string | null;
  fileSizeBytes?: number | null;
  durationSeconds?: number | null;
}

export interface ReturnCaseDetail {
  caseId: number;
  orderId: number;
  caseType?: string;
  state: string;
  reasonCode?: string | null;
  description?: string | null;
  assignedTo?: number | null;
  approvedAt?: string | null;
  validReturnedAt?: string | null;
  sellerResponseDueAt?: string | null;
  createdAt?: string | null;
  closedAt?: string | null;
}

export interface SettlementOperationSummary {
  operationId: string;
  orderId: number;
  componentId: string;
  operationType: string;
  amount: number;
  state: string;
  provider?: string | null;
  providerOperationId?: string | null;
  requestedAt?: string | null;
  confirmedAt?: string | null;
}

export interface SettlementConfirmResult {
  operationId: string;
  state: string;
  coinsCredited: number;
  expiresAt?: string | null;
  message: string;
}

export interface KtvEvidenceRequestResponse {
  requestId: string;
  caseId: number;
  roundId: number;
  status: string;
  reason: string;
  deadlineAt: string;
}

async function parseJsonOrThrow<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const body = (await res.json().catch(() => ({}))) as { message?: string };
    throw new Error(body.message || `Yêu cầu không thành công (${res.status}).`);
  }
  return res.json() as Promise<T>;
}

export function createUc83CommandKey(prefix: string, id: number | string): string {
  const randomPart =
    typeof globalThis.crypto !== 'undefined' && typeof globalThis.crypto.randomUUID === 'function'
      ? globalThis.crypto.randomUUID()
      : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
  return `${prefix}:${id}:${randomPart}`;
}

export const returnCaseApi = {
  // Buyer order-scoped endpoints
  async getActiveReturnCase(orderId: number): Promise<ReturnCaseDetail | null> {
    const res = await authApi.authorizedFetch(`/commerce/orders/${orderId}/return-case`);
    if (res.status === 404) return null;
    return parseJsonOrThrow<ReturnCaseDetail>(res);
  },

  async createReturnRequest(
    orderId: number,
    payload: {
      reasonCode: ReturnReasonCode;
      detail?: string;
      evidenceList: ReturnEvidenceItemInput[];
      commandKey?: string;
    },
  ): Promise<ReturnCaseDetail> {
    const commandKey = payload.commandKey || createUc83CommandKey('RETURN-REQ', orderId);
    const res = await authApi.authorizedFetch(`/commerce/orders/${orderId}/return-request`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ...payload, commandKey }),
    });
    return parseJsonOrThrow<ReturnCaseDetail>(res);
  },

  async withdrawReturnRequest(orderId: number, reason?: string): Promise<ReturnCaseDetail> {
    const res = await authApi.authorizedFetch(`/commerce/orders/${orderId}/return-case/withdraw`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        reason: reason || 'Người mua tự rút yêu cầu trả hàng',
        commandKey: createUc83CommandKey('WITHDRAW', orderId),
      }),
    });
    return parseJsonOrThrow<ReturnCaseDetail>(res);
  },

  async supplementBuyerEvidenceRound1(
    orderId: number,
    payload: {
      note?: string;
      evidenceList: ReturnEvidenceItemInput[];
      commandKey?: string;
    },
  ): Promise<ReturnCaseDetail> {
    const commandKey = payload.commandKey || createUc83CommandKey('SUPP-R1', orderId);
    const res = await authApi.authorizedFetch(`/commerce/orders/${orderId}/return-case/supplement-evidence`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ...payload, commandKey }),
    });
    return parseJsonOrThrow<ReturnCaseDetail>(res);
  },

  async confirmDirectHandover(orderId: number, note?: string): Promise<ReturnCaseDetail> {
    const res = await authApi.authorizedFetch(`/commerce/orders/${orderId}/return-shipment/confirm-direct-handover`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        deliveredAt: new Date().toISOString(),
        note: note || 'Xác nhận bàn giao hàng trả trực tiếp',
        commandKey: createUc83CommandKey('HANDOVER', orderId),
      }),
    });
    return parseJsonOrThrow<ReturnCaseDetail>(res);
  },

  // Seller order-scoped endpoints
  async sellerRespondRound1(
    orderId: number,
    outcome: 'APPROVE_RETURN' | 'SELLER_DECLINED',
    reason?: string,
  ): Promise<ReturnCaseDetail> {
    const res = await authApi.authorizedFetch(`/commerce/orders/${orderId}/return-case/seller-response`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        outcome,
        reason: reason || (outcome === 'APPROVE_RETURN' ? 'Người bán chấp thuận trả hàng' : 'Người bán không đồng ý, chuyển KTV phân xử'),
        commandKey: createUc83CommandKey('SELLER-R1', orderId),
      }),
    });
    return parseJsonOrThrow<ReturnCaseDetail>(res);
  },

  async sellerAgreeRefundRound2(orderId: number, reason?: string): Promise<ReturnCaseDetail> {
    const res = await authApi.authorizedFetch(`/commerce/orders/${orderId}/return-case/agree-refund`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        reason: reason || 'Hàng trả đúng tình trạng, đồng ý hoàn tiền cho Người mua',
        commandKey: createUc83CommandKey('SELLER-REFUND', orderId),
      }),
    });
    return parseJsonOrThrow<ReturnCaseDetail>(res);
  },

  async sellerSubmitDamageExemption(
    orderId: number,
    reason: string,
    evidenceList: ReturnEvidenceItemInput[],
  ): Promise<ReturnCaseDetail> {
    const res = await authApi.authorizedFetch(`/commerce/orders/${orderId}/return-case/damage-exemption`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        reason,
        evidenceList,
        commandKey: createUc83CommandKey('SELLER-EXEMPT', orderId),
      }),
    });
    return parseJsonOrThrow<ReturnCaseDetail>(res);
  },

  // KTV & Admin UC83 endpoints
  async ktvRequestRound1Evidence(
    caseId: number,
    reason: string,
    requestedItems?: string[],
  ): Promise<KtvEvidenceRequestResponse> {
    const res = await authApi.authorizedFetch(`/trustsafety/returns/${caseId}/ktv/request-round1-evidence`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        reason,
        requestedItems: requestedItems || [],
        commandKey: createUc83CommandKey('KTV-REQ-R1', caseId),
      }),
    });
    return parseJsonOrThrow<KtvEvidenceRequestResponse>(res);
  },

  async ktvRequestRound2Evidence(
    caseId: number,
    reason: string,
    requestedItems?: string[],
  ): Promise<KtvEvidenceRequestResponse> {
    const res = await authApi.authorizedFetch(`/trustsafety/returns/${caseId}/ktv/request-evidence`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        reason,
        requestedItems: requestedItems || [],
        commandKey: createUc83CommandKey('KTV-REQ-R2', caseId),
      }),
    });
    return parseJsonOrThrow<KtvEvidenceRequestResponse>(res);
  },

  async listOrderSettlements(orderId: number): Promise<SettlementOperationSummary[]> {
    const res = await authApi.authorizedFetch(`/admin/payments/orders/${orderId}/settlements`);
    return parseJsonOrThrow<SettlementOperationSummary[]>(res);
  },

  async confirmSettlementOperation(operationId: string): Promise<SettlementConfirmResult> {
    const res = await authApi.authorizedFetch(`/admin/payments/settlements/${operationId}/confirm`, {
      method: 'POST',
    });
    return parseJsonOrThrow<SettlementConfirmResult>(res);
  },
};
