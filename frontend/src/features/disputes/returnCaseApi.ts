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
  cleanDescription?: string | null;
  createdBy?: number | null;
  returnShipment?: { deliveryMethod: string; trackingNumber?: string | null } | null;
  pendingEvidenceRequest?: { requestId: string; reason: string; deadlineAt: string } | null;
  currentRound?: { validDamageRequestAt?: string | null } | null;
  fullDescription?: string | null;
  evidence?: { evidenceId: string; objectKey?: string | null; mediaType?: string; textContent?: string }[];
  events?: { eventId: string; eventType: string; reason: string; metadata?: string; occurredAt: string }[];
  decisions?: { reason: string }[];
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

export interface AdminEscalatedCaseSummary {
  caseId: number;
  orderId: number;
  caseType?: string;
  state: string;
  reasonCode?: string | null;
  assignedKtvId?: number | null;
  roundNumber?: number;
  escalatedAt?: string | null;
  slaDeadline?: string | null;
  createdAt?: string | null;
}

export interface AdminEscalatedCasePage {
  content: AdminEscalatedCaseSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface KtvEvidenceRequestResponse {
  requestId: string;
  caseId: number;
  roundId: number;
  status: string;
  reason: string;
  deadlineAt: string;
}

export interface AdminAssigneeOption {
  userId: number;
  fullName: string;
  email: string;
}

export interface CaseCompensationResponse {
  eventId: string;
  caseId: number;
  compensationType: string;
  recipientId: number;
  grantId?: string | null;
  message: string;
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
  async submitShipment(caseId: number, deliveryMethod: string, carrier: string | null, trackingNumber: string | null, commandKey: string): Promise<ReturnCaseDetail> {
    return parseJsonOrThrow(await authApi.authorizedFetch('/trustsafety/returns/' + caseId + '/shipment', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({deliveryMethod, carrier, trackingNumber, commandKey}),
    }));
  },
  async uploadEvidence(
    file: File,
    mediaType: 'IMAGE' | 'VIDEO' = 'IMAGE',
    durationSeconds?: number,
  ): Promise<ReturnEvidenceItemInput> {
    if (mediaType === 'VIDEO' && durationSeconds == null) {
      const url = URL.createObjectURL(file);
      try {
        durationSeconds = await new Promise<number>((resolve, reject) => {
          const video = document.createElement('video');
          video.preload = 'metadata';
          video.onloadedmetadata = () => Number.isFinite(video.duration)
            ? resolve(Math.ceil(video.duration)) : reject(new Error('Không đọc được thời lượng video.'));
          video.onerror = () => reject(new Error('Không đọc được video đã chọn.'));
          video.src = url;
        });
      } finally {
        URL.revokeObjectURL(url);
      }
    }
    const formData = new FormData();
    formData.append('file', file);
    formData.append('mediaType', mediaType);
    if (durationSeconds != null) {
      formData.append('durationSeconds', String(durationSeconds));
    }
    const res = await authApi.authorizedFetch('/trustsafety/returns/evidence/upload', {
      method: 'POST',
      body: formData,
    });
    return parseJsonOrThrow<ReturnEvidenceItemInput>(res);
  },

  // Buyer order-scoped endpoints
  async getActiveReturnCase(orderId: number): Promise<ReturnCaseDetail | null> {
    const res = await authApi.authorizedFetch(`/commerce/orders/${orderId}/return-case`);
    if (res.status === 404) return null;
    return parseJsonOrThrow<ReturnCaseDetail>(res);
  },

  async getLatestReturnCase(orderId: number): Promise<ReturnCaseDetail | null> {
    const res = await authApi.authorizedFetch(`/commerce/orders/${orderId}/return-case?includeClosed=true`);
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
    requestId?: string,
  ): Promise<ReturnCaseDetail> {
    const res = await authApi.authorizedFetch(`/commerce/orders/${orderId}/return-case/damage-exemption`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        reason,
        evidenceList,
        requestId,
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

  async getAdminEscalatedCases(params?: { page?: number; size?: number; ktvId?: number }): Promise<AdminEscalatedCasePage> {
    const qs = new URLSearchParams();
    qs.set('page', String(params?.page ?? 0));
    qs.set('size', String(params?.size ?? 20));
    if (params?.ktvId != null) {
      qs.set('ktvId', String(params.ktvId));
    }
    const res = await authApi.authorizedFetch(`/admin/cases/escalated?${qs.toString()}`);
    return parseJsonOrThrow<AdminEscalatedCasePage>(res);
  },

  async adminDecideRound2(
    caseId: number,
    payload: {
      outcome: 'FULL_GOODS_REFUND' | 'EXEMPT_GOODS_REFUND';
      reason: string;
      commandKey?: string;
    },
  ): Promise<ReturnCaseDetail> {
    const commandKey = payload.commandKey || createUc83CommandKey('ADMIN-R2', caseId);
    const res = await authApi.authorizedFetch(`/admin/cases/${caseId}/decide-round2`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ...payload, commandKey }),
    });
    return parseJsonOrThrow<ReturnCaseDetail>(res);
  },

  async getKtvWorkQueue(scope: 'AVAILABLE' | 'MY_CASES', round: 1 | 2): Promise<ReturnCaseDetail[]> {
    const res = await authApi.authorizedFetch(`/ktv/work-queue?scope=${scope}&round=${round}`);
    return parseJsonOrThrow<ReturnCaseDetail[]>(res);
  },

  async assignCaseToKtv(caseId: number): Promise<ReturnCaseDetail> {
    const res = await authApi.authorizedFetch(`/ktv/cases/${caseId}/assign`, { method: 'POST' });
    return parseJsonOrThrow<ReturnCaseDetail>(res);
  },

  async getReturnCaseById(caseId: number): Promise<ReturnCaseDetail> {
    const res = await authApi.authorizedFetch(`/trustsafety/returns/${caseId}`);
    return parseJsonOrThrow<ReturnCaseDetail>(res);
  },

  async ktvDecideRound1(
    caseId: number,
    payload: { outcome: 'APPROVE_RETURN' | 'REJECT_RETURN'; reason: string; commandKey?: string },
  ): Promise<ReturnCaseDetail> {
    const commandKey = payload.commandKey || createUc83CommandKey('KTV-R1', caseId);
    const res = await authApi.authorizedFetch(`/ktv/cases/${caseId}/decide-round1`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ...payload, commandKey }),
    });
    return parseJsonOrThrow<ReturnCaseDetail>(res);
  },

  async ktvDecideRound2(
    caseId: number,
    payload: { outcome: 'FULL_GOODS_REFUND' | 'EXEMPT_GOODS_REFUND'; reason: string; commandKey?: string },
  ): Promise<ReturnCaseDetail> {
    const commandKey = payload.commandKey || createUc83CommandKey('KTV-R2', caseId);
    const res = await authApi.authorizedFetch(`/ktv/cases/${caseId}/decide-round2`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ...payload, commandKey }),
    });
    return parseJsonOrThrow<ReturnCaseDetail>(res);
  },

  async mySystemIssues(): Promise<ReturnCaseDetail[]> {
    return parseJsonOrThrow(await authApi.authorizedFetch('/trustsafety/system-issues'));
  },

  async createSystemIssue(description: string, reference: { orderId?: number; productId?: number; listingChargeId?: string; paymentIntentId?: string }, evidence: string, commandKey: string): Promise<ReturnCaseDetail> {
    return parseJsonOrThrow(await authApi.authorizedFetch('/trustsafety/system-issues', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ description, ...reference, commandKey, evidenceList: evidence ? [{ mediaType: 'TEXT', textContent: evidence }] : [] }),
    }));
  },

  async issueProgress(path: string, note = '', requestId?: string): Promise<ReturnCaseDetail> {
    return parseJsonOrThrow(await authApi.authorizedFetch(path, {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ note, requestId }),
    }));
  },

  async getKtvAdmins(): Promise<AdminAssigneeOption[]> {
    const res = await authApi.authorizedFetch('/ktv/admins');
    return parseJsonOrThrow<AdminAssigneeOption[]>(res);
  },

  async getAdminAssignees(): Promise<AdminAssigneeOption[]> {
    const res = await authApi.authorizedFetch('/admin/cases/admins');
    return parseJsonOrThrow<AdminAssigneeOption[]>(res);
  },

  async ktvEscalateCase(caseId: number, recipientAdminId: number, reason: string): Promise<ReturnCaseDetail> {
    const res = await authApi.authorizedFetch(`/ktv/cases/${caseId}/escalate`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        recipientAdminId,
        reason,
        commandKey: createUc83CommandKey('KTV-ESC', caseId),
      }),
    });
    return parseJsonOrThrow<ReturnCaseDetail>(res);
  },

  async adminHandoverCase(caseId: number, newAdminId: number, reason: string): Promise<ReturnCaseDetail> {
    const res = await authApi.authorizedFetch(`/admin/cases/${caseId}/handover`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        newAdminId,
        reason,
        commandKey: createUc83CommandKey('ADMIN-HANDOVER', caseId),
      }),
    });
    return parseJsonOrThrow<ReturnCaseDetail>(res);
  },

  async getKtvSystemIssues(): Promise<ReturnCaseDetail[]> {
    const res = await authApi.authorizedFetch('/ktv/system-issues');
    return parseJsonOrThrow<ReturnCaseDetail[]>(res);
  },

  async getAdminSystemIssues(): Promise<ReturnCaseDetail[]> {
    const res = await authApi.authorizedFetch('/admin/cases/system-issues');
    return parseJsonOrThrow<ReturnCaseDetail[]>(res);
  },

  async ktvDecideSystemIssue(
    caseId: number,
    resolution: 'CONFIRM_ISSUE' | 'REJECT_ISSUE',
    reason: string,
  ): Promise<ReturnCaseDetail> {
    const res = await authApi.authorizedFetch(`/ktv/cases/${caseId}/decide-system-issue`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        resolution,
        reason,
        commandKey: createUc83CommandKey('KTV-SYS', caseId),
      }),
    });
    return parseJsonOrThrow<ReturnCaseDetail>(res);
  },

  async adminDecideSystemIssue(
    caseId: number,
    resolution: 'CONFIRM_ISSUE' | 'REJECT_ISSUE',
    reason: string,
  ): Promise<ReturnCaseDetail> {
    const res = await authApi.authorizedFetch(`/admin/cases/${caseId}/decide-system-issue`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        resolution,
        reason,
        commandKey: createUc83CommandKey('ADMIN-SYS', caseId),
      }),
    });
    return parseJsonOrThrow<ReturnCaseDetail>(res);
  },

  async grantAdminCompensation(
    caseId: number,
    payload: {
      compensationType: 'VOUCHER' | 'DEADLINE_EXTENSION';
      recipientId: number;
      voucherId?: number;
      targetReturnCaseId?: number;
      affectedFrom?: string;
      affectedUntil?: string;
      reason: string;
    },
  ): Promise<CaseCompensationResponse> {
    const res = await authApi.authorizedFetch(`/admin/cases/${caseId}/compensations`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        ...payload,
        commandKey: createUc83CommandKey('ADMIN-COMP', caseId),
      }),
    });
    return parseJsonOrThrow<CaseCompensationResponse>(res);
  },
};
