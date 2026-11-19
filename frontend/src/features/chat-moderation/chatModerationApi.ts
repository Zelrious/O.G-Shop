import { authApi } from '../auth/authApi';

export interface ReportSummary {
  reportId: number;
  conversationId: number;
  flaggedMessageId?: number | null;
  offenderId: number;
  offenderName: string;
  offenderEmail: string;
  reporterId: number | null;
  violationType: string;
  severityLevel: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  status: 'PENDING' | 'RESOLVED' | 'DISMISSED';
  flaggedContent: string;
  createdAt: string;
  pendingViolationCount?: number;
}

export interface ContextMessage {
  messageId: number;
  senderId: number;
  type: string;
  content: string;
  createdAt: string;
  isMaskedForUsers?: boolean;
}

export interface ReportDetail {
  reportId: number;
  conversationId: number;
  flaggedMessageId?: number | null;
  offenderId: number;
  offenderName: string;
  offenderEmail: string;
  offenderViolationScore: number;
  offenderReputation: number;
  offenderChatRestrictedUntil: string | null;
  reporterId: number | null;
  violationType: string;
  severityLevel: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  status: 'PENDING' | 'RESOLVED' | 'DISMISSED';
  flaggedContent: string;
  contextSnippet: string;
  decisionAction: string | null;
  reputationPenalty: number;
  decisionNote: string | null;
  resolvedBy: number | null;
  resolverName: string | null;
  resolvedAt: string | null;
  createdAt: string;
}

export interface ReportDecisionPayload {
  action: 'DISMISS' | 'WARN' | 'RESTRICT_CHAT' | 'LOCK_ACCOUNT';
  reputationPenalty: number;
  restrictionDays?: number | null;
  decisionNote: string;
}

export interface ReportPageResponse {
  items: ReportSummary[];
  page: number;
  size: number;
  total: number;
  pendingCount: number;
}

export interface ModerationStats {
  pendingCount: number;
}

export interface KtvMessageItem {
  messageId: number;
  conversationId: number;
  senderId: number;
  senderName: string;
  senderAvatarUrl?: string | null;
  type: string;
  content: string;
  rawContent?: string;
  maskedContent?: string | null;
  isMaskedForUsers: boolean;
  hasViolation?: boolean;
  flaggedReportId?: number | null;
  violationType?: string | null;
  severityLevel?: string | null;
  moderationStatus?: string | null;
  createdAt: string;
}

export interface ConversationChunkResponse {
  conversationId: number;
  messages: KtvMessageItem[];
  hasOlder: boolean;
  hasNewer: boolean;
  oldestMessageId: number | null;
  newestMessageId: number | null;
  pivotMessageId: number | null;
}

export interface UserViolationHistoryItem {
  reportId: number;
  conversationId: number;
  flaggedMessageId: number | null;
  violationType: string;
  severityLevel: string;
  status: string;
  flaggedContent: string;
  decisionAction: string | null;
  reputationPenalty: number;
  decisionNote: string | null;
  resolverName: string | null;
  resolvedAt: string | null;
  createdAt: string;
}

export interface UserViolationHistoryResponse {
  userId: number;
  fullName: string;
  email: string;
  reputationScore: number;
  chatViolationScore: number;
  chatRestrictedUntil: string | null;
  accountStatus: string;
  reports: UserViolationHistoryItem[];
}

export interface ManualFlagPayload {
  violationType: string;
  severityLevel?: string;
  action: 'DISMISS' | 'WARN' | 'RESTRICT_CHAT' | 'LOCK_ACCOUNT';
  reputationPenalty: number;
  restrictionDays?: number | null;
  decisionNote: string;
}

export interface BatchDecisionPayload {
  conversationId: number;
  messageIds: number[];
  reportIds: number[];
  action: 'DISMISS' | 'WARN' | 'RESTRICT_CHAT' | 'LOCK_ACCOUNT';
  reputationPenalty: number;
  restrictionDays?: number | null;
  decisionNote: string;
}

export interface BatchDecisionResponse {
  resolvedCount: number;
  offenderId: number;
  action: string;
  reputationPenalty: number;
  message: string;
}

export interface PendingViolationItem {
  messageId: number;
  reportId: number | null;
  violationType: string;
  severityLevel: string;
  content: string;
  createdAt: string;
}

export const chatModerationApi = {
  async getQueue(
    status = 'PENDING',
    severity?: string,
    type?: string,
    page = 0,
    size = 15,
    query?: string,
    hasPenalty?: boolean,
    minScore?: number
  ): Promise<ReportPageResponse> {
    const params = new URLSearchParams();
    if (query && query.trim()) params.append('q', query.trim());
    if (status && status !== 'ALL') params.append('status', status);
    if (severity && severity !== 'ALL') params.append('severity', severity);
    if (type && type !== 'ALL') params.append('type', type);
    if (hasPenalty !== undefined && hasPenalty !== null) params.append('hasPenalty', String(hasPenalty));
    if (minScore !== undefined && minScore !== null && minScore > 0) params.append('minScore', String(minScore));
    params.append('page', String(page));
    params.append('size', String(size));

    const response = await authApi.authorizedFetch(`/ktv/chat-moderation/reports?${params.toString()}`);
    if (!response.ok) {
      throw new Error(`Không thể tải hàng đợi kiểm duyệt tin nhắn (${response.status})`);
    }
    return response.json();
  },

  async getDetail(reportId: number): Promise<ReportDetail> {
    const response = await authApi.authorizedFetch(`/ktv/chat-moderation/reports/${reportId}`);
    if (!response.ok) {
      throw new Error(`Không thể tải chi tiết hồ sơ kiểm duyệt (${response.status})`);
    }
    return response.json();
  },

  async submitDecision(reportId: number, payload: ReportDecisionPayload): Promise<ReportDetail> {
    const response = await authApi.authorizedFetch(`/ktv/chat-moderation/reports/${reportId}/decision`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    if (!response.ok) {
      const err = await response.json().catch(() => ({}));
      throw new Error(err.message || `Xử lý quyết định thất bại (${response.status})`);
    }
    return response.json();
  },

  async getConversationMessages(
    conversationId: number,
    options?: { around?: number; before?: number; after?: number; size?: number }
  ): Promise<ConversationChunkResponse> {
    const params = new URLSearchParams();
    if (options?.around) params.append('around', String(options.around));
    if (options?.before) params.append('before', String(options.before));
    if (options?.after) params.append('after', String(options.after));
    if (options?.size) params.append('size', String(options.size));

    const response = await authApi.authorizedFetch(
      `/ktv/chat-moderation/conversations/${conversationId}/messages?${params.toString()}`
    );
    if (!response.ok) {
      throw new Error(`Không thể tải phân đoạn hội thoại (${response.status})`);
    }
    return response.json();
  },

  async getUserViolationHistory(userId: number): Promise<UserViolationHistoryResponse> {
    const response = await authApi.authorizedFetch(`/ktv/chat-moderation/users/${userId}/violation-history`);
    if (!response.ok) {
      throw new Error(`Không thể tải lịch sử vi phạm người dùng (${response.status})`);
    }
    return response.json();
  },

  async manualFlag(
    conversationId: number,
    messageId: number,
    payload: ManualFlagPayload
  ): Promise<ReportDetail> {
    const response = await authApi.authorizedFetch(
      `/ktv/chat-moderation/conversations/${conversationId}/messages/${messageId}/manual-flag`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      }
    );
    if (!response.ok) {
      const err = await response.json().catch(() => ({}));
      throw new Error(err.message || `Gắn cờ vi phạm thủ công thất bại (${response.status})`);
    }
    return response.json();
  },

  async getStats(): Promise<ModerationStats> {
    const response = await authApi.authorizedFetch('/ktv/chat-moderation/stats');
    if (!response.ok) {
      return { pendingCount: 0 };
    }
    return response.json();
  },

  async batchDecision(payload: BatchDecisionPayload): Promise<BatchDecisionResponse> {
    const response = await authApi.authorizedFetch('/ktv/chat-moderation/batch-decision', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    if (!response.ok) {
      const err = await response.json().catch(() => ({}));
      throw new Error(err.message || `Xử lý vi phạm hàng loạt thất bại (${response.status})`);
    }
    return response.json();
  },

  async getPendingViolations(conversationId: number): Promise<PendingViolationItem[]> {
    const response = await authApi.authorizedFetch(
      `/ktv/chat-moderation/conversations/${conversationId}/pending-violations`
    );
    if (!response.ok) {
      throw new Error(`Không thể tải danh sách vi phạm đang chờ xử lý (${response.status})`);
    }
    return response.json();
  },

  async getMessageMediaBlob(messageId: number, signal?: AbortSignal): Promise<Blob> {
    const response = await authApi.authorizedFetch(
      `/ktv/chat-moderation/messages/${messageId}/media`,
      { signal }
    );
    if (!response.ok) {
      throw new Error(`Không thể tải tệp đa phương tiện (#${messageId})`);
    }
    return response.blob();
  },
};
