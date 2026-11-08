import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  chatModerationApi,
  type ReportSummary,
  type ReportDetail,
  type ReportDecisionPayload,
  type ContextMessage,
  type KtvMessageItem,
  type UserViolationHistoryResponse,
  type ManualFlagPayload,
  type BatchDecisionPayload,
} from '../../features/chat-moderation/chatModerationApi';
import { Alert, Button, EmptyState, MediaLightboxModal } from '../../shared/components';
import { Select, type SelectOption } from '../../shared/components/Select/Select';
import { KtvChatAttachment } from '../../features/chat-moderation/KtvChatAttachment';
import { ConsoleIcons } from '../../shared/layout/ConsoleIcons';
import '../../features/chat-moderation/chatModeration.css';

const VIOLATION_TYPE_LABELS: Record<string, string> = {
  POLITICAL_SUBVERSIVE: 'Chính trị / Khủng bố',
  HATE_SPEECH_REGIONAL: 'Kỳ thị vùng miền / Chủng tộc',
  HARASSMENT_THREAT: 'Đe dọa / Công kích cá nhân',
  OFFENSIVE_PROFANITY: 'Tục tĩu / Chửi thề',
  PHONE_LEAK: 'Rò rỉ số điện thoại',
  PLATFORM_REDIRECT: 'Điều hướng ngoại sàn',
  OFF_PLATFORM_PAYMENT: 'Giao dịch / Cọc ngoài sàn',
  PII_LEAK: 'Rò rỉ CCCD / CMND',
};

const STATUS_OPTIONS: SelectOption[] = [
  { value: 'PENDING', label: 'Chờ xử lý' },
  { value: 'RESOLVED', label: 'Đã xử phạt' },
  { value: 'DISMISSED', label: 'Đã bỏ qua' },
  { value: 'ALL', label: 'Tất cả trạng thái' },
];

const SEVERITY_OPTIONS: SelectOption[] = [
  { value: 'ALL', label: 'Tất cả mức độ' },
  { value: 'CRITICAL', label: 'Nguy cấp' },
  { value: 'HIGH', label: 'Cao' },
  { value: 'MEDIUM', label: 'Trung bình' },
  { value: 'LOW', label: 'Thấp' },
];

const TYPE_OPTIONS: SelectOption[] = [
  { value: 'ALL', label: 'Tất cả loại vi phạm' },
  { value: 'POLITICAL_SUBVERSIVE', label: 'Chính trị / Khủng bố' },
  { value: 'HATE_SPEECH_REGIONAL', label: 'Kỳ thị vùng miền / Chủng tộc' },
  { value: 'HARASSMENT_THREAT', label: 'Đe dọa vũ lực / Công kích' },
  { value: 'OFFENSIVE_PROFANITY', label: 'Tục tĩu / Chửi thề' },
  { value: 'PHONE_LEAK', label: 'Rò rỉ số điện thoại' },
  { value: 'PLATFORM_REDIRECT', label: 'Điều hướng ngoại sàn' },
  { value: 'OFF_PLATFORM_PAYMENT', label: 'Giao dịch ngoài sàn' },
  { value: 'PII_LEAK', label: 'Rò rỉ CCCD / CMND' },
];

const PENALTY_OPTIONS: SelectOption[] = [
  { value: 'ALL', label: 'Tất cả' },
  { value: 'YES', label: 'Có trừ điểm uy tín' },
  { value: 'NO', label: 'Không trừ điểm' },
];

const MIN_SCORE_OPTIONS: SelectOption[] = [
  { value: '0', label: 'Mọi mức điểm' },
  { value: '3', label: 'Từ 3 điểm trở lên' },
  { value: '5', label: 'Từ 5 điểm trở lên' },
  { value: '10', label: 'Từ 10 điểm trở lên' },
];

const ACTION_OPTIONS: SelectOption[] = [
  { value: 'RESTRICT_CHAT', label: 'Tạm ngưng quyền gửi tin nhắn' },
  { value: 'WARN', label: 'Cảnh cáo nhắc nhở vi phạm' },
  { value: 'LOCK_ACCOUNT', label: 'Khóa tài khoản' },
  { value: 'DISMISS', label: 'Bỏ qua báo cáo' },
];

const PENALTY_SCORE_OPTIONS: SelectOption[] = [
  { value: '0', label: 'Không trừ điểm uy tín' },
  { value: '3', label: 'Trừ 3 điểm' },
  { value: '5', label: 'Trừ 5 điểm' },
  { value: '7', label: 'Trừ 7 điểm' },
  { value: '10', label: 'Trừ 10 điểm' },
  { value: '20', label: 'Trừ 20 điểm' },
];

const DAYS_OPTIONS: SelectOption[] = [
  { value: '7', label: '1 tuần' },
  { value: '14', label: '2 tuần' },
  { value: '30', label: '1 tháng' },
  { value: '90', label: '3 tháng' },
];

export const KtvChatModerationPage: React.FC = () => {
  const [params, setParams] = useSearchParams();
  const selectedReportId = Number(params.get('reportId')) || null;

  // Filter states
  const [status, setStatus] = useState<string>('PENDING');
  const [severity, setSeverity] = useState<string>('ALL');
  const [type, setType] = useState<string>('ALL');
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [hasPenaltyFilter, setHasPenaltyFilter] = useState<string>('ALL');
  const [minScoreFilter, setMinScoreFilter] = useState<number>(0);
  const [showAdvancedFilters, setShowAdvancedFilters] = useState<boolean>(false);
  const [page, setPage] = useState<number>(0);

  // Queue Data states
  const [reports, setReports] = useState<ReportSummary[]>([]);
  const [totalElements, setTotalElements] = useState<number>(0);
  const [pendingCount, setPendingCount] = useState<number>(0);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Detail states
  const [detail, setDetail] = useState<ReportDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState<boolean>(false);
  const [detailError, setDetailError] = useState<string | null>(null);
  const [copyFeedback, setCopyFeedback] = useState<boolean>(false);

  // Chunked Chat Stream states
  const [streamMessages, setStreamMessages] = useState<KtvMessageItem[]>([]);
  const [hasOlder, setHasOlder] = useState<boolean>(false);
  const [hasNewer, setHasNewer] = useState<boolean>(false);
  const [streamLoading, setStreamLoading] = useState<boolean>(false);
  const [loadingOlder, setLoadingOlder] = useState<boolean>(false);
  const [loadingNewer, setLoadingNewer] = useState<boolean>(false);
  const chatStreamRef = useRef<HTMLDivElement>(null);

  // Modal sanction states (Report Decision)
  const [showModal, setShowModal] = useState<boolean>(false);
  const [actionChoice, setActionChoice] = useState<'DISMISS' | 'WARN' | 'RESTRICT_CHAT' | 'LOCK_ACCOUNT'>('RESTRICT_CHAT');
  const [penaltyChoice, setPenaltyChoice] = useState<number>(5);
  const [daysChoice, setDaysChoice] = useState<number>(14);
  const [decisionNote, setDecisionNote] = useState<string>('');
  const [submitting, setSubmitting] = useState<boolean>(false);
  const [feedback, setFeedback] = useState<string | null>(null);

  // Manual Flag Modal states
  const [showManualModal, setShowManualModal] = useState<boolean>(false);
  const [manualTarget, setManualTarget] = useState<KtvMessageItem | null>(null);
  const [manualType, setManualType] = useState<string>('OFFENSIVE_PROFANITY');
  const [manualSeverity, setManualSeverity] = useState<string>('MEDIUM');
  const [manualAction, setManualAction] = useState<'DISMISS' | 'WARN' | 'RESTRICT_CHAT' | 'LOCK_ACCOUNT'>('RESTRICT_CHAT');
  const [manualPenalty, setManualPenalty] = useState<number>(5);
  const [manualDays, setManualDays] = useState<number>(14);
  const [manualNote, setManualNote] = useState<string>('');
  const [submittingManual, setSubmittingManual] = useState<boolean>(false);

  // User Violation History Modal states
  const [showHistoryModal, setShowHistoryModal] = useState<boolean>(false);
  const [historyLoading, setHistoryLoading] = useState<boolean>(false);
  const [historyData, setHistoryData] = useState<UserViolationHistoryResponse | null>(null);
  const [historyError, setHistoryError] = useState<string | null>(null);

  // Multi-Select Mode states (UC56 / UC74)
  const [isMultiSelectMode, setIsMultiSelectMode] = useState<boolean>(false);
  const [selectedMessageIds, setSelectedMessageIds] = useState<number[]>([]);
  const [activeMenuMsgId, setActiveMenuMsgId] = useState<number | null>(null);

  // Batch Sanction Modal states
  const [showBatchModal, setShowBatchModal] = useState<boolean>(false);
  const [batchActionChoice, setBatchActionChoice] = useState<'DISMISS' | 'WARN' | 'RESTRICT_CHAT' | 'LOCK_ACCOUNT'>('RESTRICT_CHAT');
  const [batchPenaltyChoice, setBatchPenaltyChoice] = useState<number>(5);
  const [batchDaysChoice, setBatchDaysChoice] = useState<number>(14);
  const [batchDecisionNote, setBatchDecisionNote] = useState<string>('');
  const [submittingBatch, setSubmittingBatch] = useState<boolean>(false);

  // Lightbox Modal states (UC56 / UC74 Image & Video Inspection)
  const [lightboxState, setLightboxState] = useState<{
    isOpen: boolean;
    src: string | null;
    type: string;
    caption?: string;
  }>({
    isOpen: false,
    src: null,
    type: 'IMAGE',
  });

  const handleOpenLightbox = (src: string, mediaType: string, caption?: string) => {
    setLightboxState({
      isOpen: true,
      src,
      type: mediaType,
      caption,
    });
  };

  const handleCloseLightbox = () => {
    setLightboxState((prev) => ({ ...prev, isOpen: false }));
  };

  const fetchQueue = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const penaltyParam = hasPenaltyFilter === 'YES' ? true : hasPenaltyFilter === 'NO' ? false : undefined;
      const minScoreParam = minScoreFilter > 0 ? minScoreFilter : undefined;
      const qParam = searchQuery.trim() ? searchQuery.trim() : undefined;

      const res = await chatModerationApi.getQueue(
        status,
        severity,
        type,
        page,
        20,
        qParam,
        penaltyParam,
        minScoreParam
      );
      setReports(res.items);
      setTotalElements(res.total);
      setPendingCount(res.pendingCount);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Không thể tải danh sách vi phạm.');
    } finally {
      setLoading(false);
    }
  }, [status, severity, type, page, searchQuery, hasPenaltyFilter, minScoreFilter]);

  useEffect(() => {
    fetchQueue();
  }, [fetchQueue]);

  const targetId = selectedReportId ?? reports[0]?.reportId ?? null;

  // Load Report Detail
  useEffect(() => {
    if (!targetId) {
      setDetail(null);
      return;
    }
    let active = true;
    setDetailLoading(true);
    setDetailError(null);
    chatModerationApi
      .getDetail(targetId)
      .then((data) => {
        if (active) setDetail(data);
      })
      .catch((err: unknown) => {
        if (active) setDetailError(err instanceof Error ? err.message : 'Lỗi khi tải chi tiết hồ sơ.');
      })
      .finally(() => {
        if (active) setDetailLoading(false);
      });
    return () => {
      active = false;
    };
  }, [targetId]);

  // Load Conversation Chunk when Detail is loaded
  const loadConversationChunk = useCallback(async (conversationId: number, aroundMsgId?: number | null) => {
    setStreamLoading(true);
    try {
      const res = await chatModerationApi.getConversationMessages(conversationId, {
        around: aroundMsgId || undefined,
        size: 60,
      });
      setStreamMessages(res.messages || []);
      setHasOlder(res.hasOlder);
      setHasNewer(res.hasNewer);

      // Auto scroll to flagged message with smooth behavior
      setTimeout(() => {
        if (aroundMsgId) {
          const el = document.getElementById(`msg-${aroundMsgId}`);
          if (el && typeof el.scrollIntoView === 'function') {
            el.scrollIntoView({ behavior: 'smooth', block: 'center' });
          }
        }
      }, 150);
    } catch {
      // Fallback: If chunk loading fails, keep empty and fallback to context snippet
      setStreamMessages([]);
    } finally {
      setStreamLoading(false);
    }
  }, []);

  useEffect(() => {
    if (detail?.conversationId) {
      loadConversationChunk(detail.conversationId, detail.flaggedMessageId);
    } else {
      setStreamMessages([]);
    }
  }, [detail?.conversationId, detail?.flaggedMessageId, loadConversationChunk]);

  // Handle Esc key to exit multi-select mode and close menus
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        if (activeMenuMsgId !== null) {
          setActiveMenuMsgId(null);
        } else if (isMultiSelectMode) {
          setIsMultiSelectMode(false);
          setSelectedMessageIds([]);
        }
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [activeMenuMsgId, isMultiSelectMode]);

  // Handle clicking outside 3-dot menu popover
  useEffect(() => {
    if (activeMenuMsgId === null) return;
    const handleClickOutside = (e: MouseEvent) => {
      const target = e.target as HTMLElement;
      if (!target.closest('.og-msg-more-btn') && !target.closest('.og-msg-menu-popover')) {
        setActiveMenuMsgId(null);
      }
    };
    document.addEventListener('click', handleClickOutside);
    return () => document.removeEventListener('click', handleClickOutside);
  }, [activeMenuMsgId]);

  // Reset multi-select when switching conversations
  useEffect(() => {
    setIsMultiSelectMode(false);
    setSelectedMessageIds([]);
    setActiveMenuMsgId(null);
  }, [targetId]);

  // Load older messages (Scroll up pagination)
  const handleLoadOlder = async () => {
    if (!detail?.conversationId || streamMessages.length === 0 || loadingOlder) return;
    const oldestId = streamMessages[0].messageId;
    setLoadingOlder(true);
    try {
      const res = await chatModerationApi.getConversationMessages(detail.conversationId, {
        before: oldestId,
        size: 30,
      });
      if (res.messages && res.messages.length > 0) {
        setStreamMessages((prev) => [...res.messages, ...prev]);
      }
      setHasOlder(res.hasOlder);
    } catch {
      // Ignored
    } finally {
      setLoadingOlder(false);
    }
  };

  // Load newer messages (Scroll down pagination)
  const handleLoadNewer = async () => {
    if (!detail?.conversationId || streamMessages.length === 0 || loadingNewer) return;
    const newestId = streamMessages[streamMessages.length - 1].messageId;
    setLoadingNewer(true);
    try {
      const res = await chatModerationApi.getConversationMessages(detail.conversationId, {
        after: newestId,
        size: 30,
      });
      if (res.messages && res.messages.length > 0) {
        setStreamMessages((prev) => [...prev, ...res.messages]);
      }
      setHasNewer(res.hasNewer);
    } catch {
      // Ignored
    } finally {
      setLoadingNewer(false);
    }
  };

  // Reset all filters to default
  const handleResetFilters = () => {
    setSearchQuery('');
    setStatus('PENDING');
    setSeverity('ALL');
    setType('ALL');
    setHasPenaltyFilter('ALL');
    setMinScoreFilter(0);
    setPage(0);
  };

  // Copy violation raw content
  const handleCopyEvidence = (text: string) => {
    navigator.clipboard.writeText(text);
    setCopyFeedback(true);
    setTimeout(() => setCopyFeedback(false), 2000);
  };

  // Open Decision Modal
  const handleOpenSanction = (initialAction: 'DISMISS' | 'WARN' | 'RESTRICT_CHAT' | 'LOCK_ACCOUNT' = 'RESTRICT_CHAT') => {
    setActionChoice(initialAction);
    if (initialAction === 'DISMISS' || initialAction === 'WARN') {
      setPenaltyChoice(0);
    } else if (initialAction === 'LOCK_ACCOUNT') {
      setPenaltyChoice(20);
    } else {
      setPenaltyChoice(detail?.severityLevel === 'HIGH' ? 7 : 5);
    }
    setDecisionNote(
      initialAction === 'DISMISS'
        ? 'Đối chiếu ngữ cảnh xác định không đủ yếu tố vi phạm hoặc dương tính giả.'
        : `Xử lý vi phạm ${VIOLATION_TYPE_LABELS[detail?.violationType || ''] || 'chính sách O.G Shop'}.`
    );
    setShowModal(true);
  };

  // Submit Decision
  const handleSubmitDecision = async () => {
    if (!detail) return;
    setSubmitting(true);
    try {
      const payload: ReportDecisionPayload = {
        action: actionChoice,
        reputationPenalty: penaltyChoice,
        restrictionDays: actionChoice === 'RESTRICT_CHAT' ? daysChoice : null,
        decisionNote: decisionNote.trim() || 'Xử lý theo quy định UC74',
      };
      const updated = await chatModerationApi.submitDecision(detail.reportId, payload);
      setDetail(updated);
      setShowModal(false);
      setFeedback(`Đã thực hiện quyết định [${actionChoice}] cho hồ sơ #${detail.reportId}`);
      fetchQueue();
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'Xử lý quyết định thất bại.');
    } finally {
      setSubmitting(false);
    }
  };

  // Open Manual Flag Modal
  const handleOpenManualFlag = (msg: KtvMessageItem) => {
    setManualTarget(msg);
    setManualType(msg.violationType || 'OFFENSIVE_PROFANITY');
    setManualSeverity(msg.severityLevel || 'MEDIUM');
    setManualAction('RESTRICT_CHAT');
    setManualPenalty(5);
    setManualDays(14);
    setManualNote(`KTV gắn cờ thủ công tin nhắn #${msg.messageId}: "${(msg.rawContent || msg.content).slice(0, 80)}"`);
    setShowManualModal(true);
  };

  // Submit Manual Flag
  const handleSubmitManualFlag = async () => {
    if (!detail || !manualTarget) return;
    setSubmittingManual(true);
    try {
      const payload: ManualFlagPayload = {
        violationType: manualType,
        severityLevel: manualSeverity,
        action: manualAction,
        reputationPenalty: manualPenalty,
        restrictionDays: manualAction === 'RESTRICT_CHAT' ? manualDays : null,
        decisionNote: manualNote.trim() || 'KTV can thiệp thủ công từ dòng hội thoại.',
      };
      await chatModerationApi.manualFlag(detail.conversationId, manualTarget.messageId, payload);
      setShowManualModal(false);
      setFeedback(`Đã gắn cờ và thi hành chế tài đối với tin nhắn #${manualTarget.messageId}`);
      loadConversationChunk(detail.conversationId, manualTarget.messageId);
      fetchQueue();
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'Gắn cờ thủ công thất bại.');
    } finally {
      setSubmittingManual(false);
    }
  };

  // Check if a message was already resolved or dismissed
  const isMessageResolved = (msg: KtvMessageItem, currentDetail: ReportDetail | null): boolean => {
    if (msg.moderationStatus === 'RESOLVED' || msg.moderationStatus === 'DISMISSED') {
      return true;
    }
    if (currentDetail && currentDetail.status !== 'PENDING') {
      if (msg.messageId === currentDetail.flaggedMessageId) {
        return true;
      }
    }
    return false;
  };

  // Check if message content is just default system media placeholder
  const isMediaOnly = (msgType: string, content: string): boolean => {
    if (msgType !== 'IMAGE' && msgType !== 'VIDEO') return false;
    const trimmed = (content || '').trim();
    return !trimmed || trimmed === 'Đã gửi ảnh.' || trimmed === 'Đã gửi video.';
  };

  // Multi-Select & Batch Decision Handlers
  const toggleMessageSelection = (msg: KtvMessageItem) => {
    if (isMessageResolved(msg, detail)) return;
    setSelectedMessageIds((prev) =>
      prev.includes(msg.messageId) ? prev.filter((id) => id !== msg.messageId) : [...prev, msg.messageId]
    );
  };

  const handleSelectAllViolations = () => {
    const pendingViolationIds = streamMessages
      .filter((m) => (m.hasViolation || m.isMaskedForUsers) && !isMessageResolved(m, detail))
      .map((m) => m.messageId);
    if (pendingViolationIds.length > 0) {
      setSelectedMessageIds(pendingViolationIds);
      setIsMultiSelectMode(true);
    } else {
      setFeedback('Tất cả tin nhắn vi phạm trong hội thoại này đã được xử lý xong.');
    }
  };

  const handleOpenBatchModal = () => {
    if (selectedMessageIds.length === 0) return;
    setBatchActionChoice('RESTRICT_CHAT');
    setBatchPenaltyChoice(detail?.severityLevel === 'HIGH' ? 7 : 5);
    setBatchDaysChoice(14);
    setBatchDecisionNote(`Xử phạt gộp ${selectedMessageIds.length} tin nhắn vi phạm quy chuẩn trao đổi.`);
    setShowBatchModal(true);
  };

  const handleSubmitBatch = async () => {
    if (!detail || selectedMessageIds.length === 0) return;
    setSubmittingBatch(true);
    try {
      const payload: BatchDecisionPayload = {
        conversationId: detail.conversationId,
        messageIds: selectedMessageIds,
        reportIds: detail.reportId ? [detail.reportId] : [],
        action: batchActionChoice,
        reputationPenalty: batchPenaltyChoice,
        restrictionDays: batchActionChoice === 'RESTRICT_CHAT' ? batchDaysChoice : null,
        decisionNote: batchDecisionNote.trim() || 'Xử lý vi phạm hàng loạt',
      };
      const res = await chatModerationApi.batchDecision(payload);
      setShowBatchModal(false);
      setIsMultiSelectMode(false);
      setSelectedMessageIds([]);
      setFeedback(res.message || `Đã xử phạt gộp thành công ${selectedMessageIds.length} tin nhắn.`);
      loadConversationChunk(detail.conversationId);
      fetchQueue();
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'Xử lý hàng loạt thất bại.');
    } finally {
      setSubmittingBatch(false);
    }
  };

  // Open User Violation History Modal
  const handleOpenHistory = async (userId: number) => {
    setShowHistoryModal(true);
    setHistoryLoading(true);
    setHistoryError(null);
    try {
      const data = await chatModerationApi.getUserViolationHistory(userId);
      setHistoryData(data);
    } catch (err: unknown) {
      setHistoryError(err instanceof Error ? err.message : 'Không thể tải lịch sử vi phạm người dùng.');
    } finally {
      setHistoryLoading(false);
    }
  };

  // Parse context messages fallback
  const parseContext = (): ContextMessage[] => {
    if (!detail?.contextSnippet) return [];
    try {
      const parsed = JSON.parse(detail.contextSnippet);
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  };

  const contextMessages = parseContext();

  return (
    <section className="og-chat-moderation">
      {/* 1. Header: Compact Title & Refresh Queue Button */}
      <header className="og-chat-mod-heading">
        <div className="og-chat-mod-heading-text">
          <span className="og-eyebrow">ĐIỀU PHỐI KỸ THUẬT VIÊN (UC56 / UC74)</span>
          <h1>Kiểm duyệt tin nhắn & An toàn hội thoại</h1>
          <p>
            Đối chiếu ngữ cảnh vi phạm, bảo vệ an ninh quốc gia, chống rò rỉ giao dịch và công kích cá nhân.
          </p>
        </div>
        <div className="og-chat-mod-heading__actions">
          <Button variant="outline" onClick={() => fetchQueue()} disabled={loading}>
            <svg
              width="14"
              height="14"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.6"
              strokeLinecap="round"
              strokeLinejoin="round"
              style={{ marginRight: 6 }}
              aria-hidden="true"
            >
              <path d="M23 4v6h-6" />
              <path d="M1 20v-6h6" />
              <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15" />
            </svg>
            Làm mới hàng đợi ({pendingCount} chờ xử lý)
          </Button>
        </div>
      </header>

      {feedback && <Alert type="success">{feedback}</Alert>}
      {error && <Alert type="danger">{error}</Alert>}

      {/* 2. Filter Toolbar using Shared Accessible <Select> */}
      <div className="og-chat-mod-filters-card">
        {/* Row 1: Search Box + Clear + Filter Actions */}
        <div className="og-filters-row-primary">
          <div className="og-search-box">
            <svg
              width="15"
              height="15"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.6"
              strokeLinecap="round"
              strokeLinejoin="round"
              style={{ color: 'var(--og-color-text-secondary)', flexShrink: 0 }}
              aria-hidden="true"
            >
              <circle cx="11" cy="11" r="8" />
              <line x1="21" y1="21" x2="16.65" y2="16.65" />
            </svg>
            <input
              type="text"
              placeholder="Tìm theo tên, email, từ khóa, nội dung tin nhắn..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  setPage(0);
                  fetchQueue();
                }
              }}
            />
            {searchQuery && (
              <button
                type="button"
                className="og-search-clear-btn"
                onClick={() => {
                  setSearchQuery('');
                  setPage(0);
                }}
                title="Xóa tìm kiếm"
              >
                ✕
              </button>
            )}
          </div>

          <button
            type="button"
            className={`og-filter-btn-toggle ${showAdvancedFilters ? 'og-filter-btn-toggle--active' : ''}`}
            onClick={() => setShowAdvancedFilters(!showAdvancedFilters)}
          >
            <ConsoleIcons.Sliders size={14} />
            <span>Bộ lọc nâng cao</span>
            <span style={{ fontSize: '0.7rem' }}>{showAdvancedFilters ? '▲' : '▼'}</span>
          </button>

          {(searchQuery || status !== 'PENDING' || severity !== 'ALL' || type !== 'ALL' || hasPenaltyFilter !== 'ALL' || minScoreFilter > 0) && (
            <button
              type="button"
              className="og-filter-btn-reset"
              onClick={handleResetFilters}
              title="Đặt lại toàn bộ bộ lọc về mặc định"
            >
              <ConsoleIcons.History size={13} />
              <span>Xóa bộ lọc</span>
            </button>
          )}
        </div>

        {/* Row 2: Standard Filter Dropdowns using shared <Select> */}
        <div className="og-filters-row-secondary">
          <div className="og-filter-field og-filter-field--status">
            <span className="og-filter-field__label">Trạng thái:</span>
            <Select
              hideLabel
              label="Trạng thái"
              size="sm"
              value={status}
              onChange={(val) => {
                setStatus(val);
                setPage(0);
              }}
              options={STATUS_OPTIONS}
            />
          </div>

          <div className="og-filter-field og-filter-field--severity">
            <span className="og-filter-field__label">Mức độ:</span>
            <Select
              hideLabel
              label="Mức độ"
              size="sm"
              value={severity}
              onChange={(val) => {
                setSeverity(val);
                setPage(0);
              }}
              options={SEVERITY_OPTIONS}
            />
          </div>

          <div className="og-filter-field og-filter-field--type">
            <span className="og-filter-field__label">Loại vi phạm:</span>
            <Select
              hideLabel
              label="Loại vi phạm"
              size="sm"
              value={type}
              onChange={(val) => {
                setType(val);
                setPage(0);
              }}
              options={TYPE_OPTIONS}
            />
          </div>
        </div>

        {/* Row 3 (Collapsible): Advanced Filters */}
        {showAdvancedFilters && (
          <div className="og-filters-advanced-row">
            <div className="og-filter-field og-filter-field--penalty">
              <span className="og-filter-field__label">Trừ điểm uy tín:</span>
              <Select
                hideLabel
                label="Trừ điểm uy tín"
                size="sm"
                value={hasPenaltyFilter}
                onChange={(val) => {
                  setHasPenaltyFilter(val);
                  setPage(0);
                }}
                options={PENALTY_OPTIONS}
              />
            </div>

            <div className="og-filter-field og-filter-field--min-score">
              <span className="og-filter-field__label">Điểm vi phạm ≥:</span>
              <Select
                hideLabel
                label="Điểm vi phạm tối thiểu"
                size="sm"
                value={String(minScoreFilter)}
                onChange={(val) => {
                  setMinScoreFilter(Number(val));
                  setPage(0);
                }}
                options={MIN_SCORE_OPTIONS}
              />
            </div>
          </div>
        )}
      </div>

      {/* 3. Main 3-Column Workspace (Queue | Conversation | Violation Details) */}
      <div className="og-chat-mod-workspace">
        {/* ======================================================================
            COLUMN 1: HÀNG ĐỢI KIỂM DUYỆT (QUEUE)
            ====================================================================== */}
        <aside className="og-chat-mod-queue" aria-label="Hàng đợi vi phạm tin nhắn">
          <div className="og-chat-mod-queue__header">
            <span>Hàng đợi ({totalElements})</span>
            <span className="og-queue-count-badge">
              {status === 'PENDING' ? `${pendingCount} chờ xử lý` : 'Lịch sử xử lý'}
            </span>
          </div>

          {loading ? (
            <div style={{ padding: 24, textAlign: 'center', color: 'var(--og-color-text-secondary)', fontSize: '0.86rem' }}>
              Đang tải danh sách vi phạm...
            </div>
          ) : reports.length === 0 ? (
            <EmptyState
              title="Không có vi phạm"
              description="Hiện không có tin nhắn nào bị gắn cờ theo bộ lọc này."
            />
          ) : (
            <ul className="og-chat-mod-queue__list">
              {reports.map((item) => {
                const isSelected = item.reportId === targetId;
                const severityClass = `og-badge-severity--${item.severityLevel.toLowerCase()}`;
                return (
                  <li key={item.reportId} className="og-chat-mod-queue__item">
                    <button
                      type="button"
                      className="og-chat-mod-row"
                      aria-current={isSelected}
                      onClick={() => {
                        setParams({ reportId: String(item.reportId) });
                      }}
                    >
                      <div className="og-chat-mod-row__top">
                        <span className="og-chat-mod-row__name">{item.offenderName}</span>
                        <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                          {item.pendingViolationCount && item.pendingViolationCount > 1 ? (
                            <span
                              className="og-badge-severity og-badge-severity--high"
                              title="Số tin nhắn vi phạm chờ xử lý trong cuộc trò chuyện"
                            >
                              {item.pendingViolationCount} vi phạm
                            </span>
                          ) : null}
                          <span className={`og-badge-severity ${severityClass}`}>
                            {item.severityLevel}
                          </span>
                        </div>
                      </div>
                      <div className="og-chat-mod-row__sub">
                        {item.offenderEmail} · #{item.offenderId}
                      </div>
                      <div className="og-chat-mod-row__snippet">
                        {item.flaggedContent}
                      </div>
                      <div className="og-chat-mod-row__footer">
                        <span className="og-chat-mod-row__type-badge">
                          {VIOLATION_TYPE_LABELS[item.violationType] || item.violationType}
                        </span>
                        <span>
                          {new Date(item.createdAt).toLocaleTimeString('vi-VN', {
                            hour: '2-digit',
                            minute: '2-digit',
                          })}
                        </span>
                      </div>
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
        </aside>

        {/* ======================================================================
            COLUMN 2: NỘI DUNG HỘI THOẠI (CONVERSATION VIEWER)
            ====================================================================== */}
        <section className="og-chat-mod-conversation" aria-label="Nội dung hội thoại">
          {detailLoading ? (
            <div style={{ padding: 40, textAlign: 'center', color: 'var(--og-color-text-secondary)', fontSize: '0.9rem' }}>
              Đang tải phân đoạn hội thoại...
            </div>
          ) : !detail ? (
            <EmptyState
              title="Chưa chọn hồ sơ vi phạm"
              description="Chọn một hồ sơ từ hàng đợi bên trái để xem ngữ cảnh dòng trò chuyện."
            />
          ) : (
            <>
              {/* Header: Clean Conversation Title & Message Count & Select All Violations */}
              <div className="og-conversation-header">
                <div className="og-conversation-header__info">
                  <h3 className="og-conversation-title">
                    Dòng tin nhắn hội thoại #{detail.conversationId}
                  </h3>
                  <span className="og-conversation-header__meta">
                    ({streamMessages.length > 0 ? `${streamMessages.length} tin` : `${contextMessages.length} tin`})
                  </span>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <button
                    type="button"
                    className={`og-btn-select-all-violations ${isMultiSelectMode ? 'og-btn-select-all-violations--active' : ''}`}
                    onClick={handleSelectAllViolations}
                    title="Chọn tất cả tin nhắn vi phạm trong hội thoại"
                  >
                    <svg
                      width="14"
                      height="14"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      strokeWidth="1.6"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      aria-hidden="true"
                    >
                      <polyline points="9 11 12 14 22 4" />
                      <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11" />
                    </svg>
                    <span>Chọn tất cả tin vi phạm</span>
                  </button>
                </div>
              </div>

              {/* Chat Stream Area */}
              <div className="og-chat-stream-box" ref={chatStreamRef}>
                {/* Top: Load older messages */}
                <div className="og-chat-stream-load-bar">
                  <button
                    type="button"
                    className="og-chat-stream-load-btn"
                    disabled={!hasOlder || loadingOlder}
                    onClick={handleLoadOlder}
                  >
                    {loadingOlder ? (
                      'Đang tải tin cũ hơn...'
                    ) : hasOlder ? (
                      'Tải tin cũ hơn'
                    ) : (
                      'Đã tới đầu cuộc trò chuyện'
                    )}
                  </button>
                </div>

                {streamLoading ? (
                  <div style={{ textAlign: 'center', padding: 24, color: 'var(--og-color-text-secondary)', fontSize: '0.88rem' }}>
                    Đang tải tin nhắn...
                  </div>
                ) : streamMessages.length > 0 ? (
                  streamMessages.map((msg) => {
                    const isOffender = msg.senderId === detail.offenderId;
                    const isFlaggedNeo = msg.messageId === detail.flaggedMessageId;
                    const isResolved = isMessageResolved(msg, detail);
                    const hasViolation = Boolean(msg.hasViolation || isFlaggedNeo || msg.isMaskedForUsers);
                    const rowClass = isOffender ? 'og-chat-bubble-row--self' : 'og-chat-bubble-row--peer';
                    const displayContent = msg.rawContent || msg.content;
                    const isSelected = selectedMessageIds.includes(msg.messageId);

                    let bubbleClass = isOffender ? 'og-chat-bubble--offender' : 'og-chat-bubble--peer';
                    if (isResolved) {
                      bubbleClass += ' og-chat-bubble--resolved';
                    } else if (hasViolation) {
                      const sev = (msg.severityLevel || detail.severityLevel || 'MEDIUM').toUpperCase();
                      if (sev === 'CRITICAL') {
                        bubbleClass += ' og-chat-bubble--violation-critical';
                      } else {
                        bubbleClass += ' og-chat-bubble--violation-medium';
                      }
                    }
                    if (isFlaggedNeo && !isResolved) {
                      bubbleClass += ' og-chat-bubble--flagged-pulse';
                    }

                    const violationLabel = VIOLATION_TYPE_LABELS[msg.violationType || ''] || msg.violationType;

                    return (
                      <div
                        key={msg.messageId}
                        id={`msg-${msg.messageId}`}
                        className={`og-chat-bubble-row ${rowClass} ${isMultiSelectMode && !isResolved ? 'og-chat-bubble-row--selectable' : ''}`}
                        onClick={() => {
                          if (isMultiSelectMode && !isResolved) {
                            toggleMessageSelection(msg);
                          }
                        }}
                      >
                        {/* Multi-Select Checkbox (Disabled if already resolved) */}
                        {isMultiSelectMode && (
                          <button
                            type="button"
                            className={`og-msg-checkbox ${isSelected ? 'og-msg-checkbox--checked' : ''} ${isResolved ? 'og-msg-checkbox--disabled' : ''}`}
                            disabled={isResolved}
                            onClick={(e) => {
                              e.stopPropagation();
                              if (!isResolved) toggleMessageSelection(msg);
                            }}
                            title={isResolved ? 'Tin nhắn này đã được xử lý' : `Chọn tin nhắn #${msg.messageId}`}
                            aria-label={isResolved ? 'Tin nhắn đã xử lý' : `Chọn tin nhắn #${msg.messageId}`}
                          >
                            {isSelected && (
                              <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
                                <polyline points="20 6 9 17 4 12" />
                              </svg>
                            )}
                          </button>
                        )}

                        {/* Chat Bubble */}
                        <div className={`og-chat-bubble ${bubbleClass}`}>
                          {/* Pre-marked Pill Tag */}
                          {hasViolation && (
                            <div
                              className={`og-violation-type-tag ${
                                isResolved
                                  ? 'og-violation-type-tag--resolved'
                                  : (msg.severityLevel || detail.severityLevel) === 'CRITICAL'
                                  ? 'og-violation-type-tag--critical'
                                  : ''
                              }`}
                            >
                              {isResolved && (
                                <svg
                                  width="11"
                                  height="11"
                                  viewBox="0 0 24 24"
                                  fill="none"
                                  stroke="currentColor"
                                  strokeWidth="2.5"
                                  strokeLinecap="round"
                                  strokeLinejoin="round"
                                  style={{ marginRight: 2 }}
                                  aria-hidden="true"
                                >
                                  <polyline points="20 6 9 17 4 12" />
                                </svg>
                              )}
                              {isResolved
                                ? `[Đã xử lý: ${violationLabel || 'Vi phạm'}]`
                                : `[Vi phạm: ${violationLabel || 'Chính sách hội thoại'}]`}
                            </div>
                          )}

                          {/* Media preview (IMAGE / VIDEO) for KTV inspection */}
                          {(msg.type === 'IMAGE' || msg.type === 'VIDEO') && (
                            <KtvChatAttachment
                              messageId={msg.messageId}
                              type={msg.type}
                              caption={!isMediaOnly(msg.type, displayContent) ? displayContent : undefined}
                              onOpenLightbox={handleOpenLightbox}
                            />
                          )}

                          {/* Minimalist Message Text (Clean, no extra underline or highlight) */}
                          {!isMediaOnly(msg.type, displayContent) && (
                            <div>{displayContent}</div>
                          )}

                          <div className="og-chat-bubble__meta">
                            <span>
                              {msg.senderName || (isOffender ? detail.offenderName : 'Đối tác')} · #{msg.messageId}
                            </span>
                            <span>
                              {new Date(msg.createdAt).toLocaleTimeString('vi-VN', {
                                hour: '2-digit',
                                minute: '2-digit',
                              })}
                            </span>
                          </div>
                        </div>

                        {/* Actions Group (Only 3-Dot Menu Button) */}
                        {!isMultiSelectMode && (
                          <div className="og-msg-actions-group" style={{ position: 'relative' }}>
                            {/* 3-Dot Button */}
                            <button
                              type="button"
                              className={`og-msg-more-btn ${activeMenuMsgId === msg.messageId ? 'og-msg-more-btn--active' : ''}`}
                              onClick={(e) => {
                                e.stopPropagation();
                                setActiveMenuMsgId(activeMenuMsgId === msg.messageId ? null : msg.messageId);
                              }}
                              title="Tùy chọn thao tác"
                              aria-label={`Tùy chọn thao tác tin nhắn #${msg.messageId}`}
                            >
                              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
                                <circle cx="12" cy="12" r="1.2" />
                                <circle cx="19" cy="12" r="1.2" />
                                <circle cx="5" cy="12" r="1.2" />
                              </svg>
                            </button>

                            {/* Dropdown Popover */}
                            {activeMenuMsgId === msg.messageId && (
                              <div className="og-msg-menu-popover" onClick={(e) => e.stopPropagation()}>
                                {isResolved ? (
                                  <div className="og-msg-menu-item og-msg-menu-item--disabled">
                                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                      <polyline points="20 6 9 17 4 12" />
                                    </svg>
                                    <span>Tin nhắn đã được xử lý</span>
                                  </div>
                                ) : (
                                  <>
                                    <button
                                      type="button"
                                      className="og-msg-menu-item"
                                      onClick={() => {
                                        setIsMultiSelectMode(true);
                                        setSelectedMessageIds([msg.messageId]);
                                        setActiveMenuMsgId(null);
                                      }}
                                    >
                                      <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
                                        <polyline points="9 11 12 14 22 4" />
                                        <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11" />
                                      </svg>
                                      <span>Chọn nhiều tin nhắn</span>
                                    </button>
                                    <button
                                      type="button"
                                      className="og-msg-menu-item"
                                      onClick={() => {
                                        handleOpenManualFlag(msg);
                                        setActiveMenuMsgId(null);
                                      }}
                                    >
                                      <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
                                        <circle cx="12" cy="12" r="10" />
                                        <line x1="12" y1="8" x2="12" y2="12" />
                                        <line x1="12" y1="16" x2="12.01" y2="16" />
                                      </svg>
                                      <span>Xử lý tin nhắn này</span>
                                    </button>
                                  </>
                                )}
                              </div>
                            )}
                          </div>
                        )}
                      </div>
                    );
                  })
                ) : contextMessages.length > 0 ? (
                  // Fallback context messages
                  contextMessages.map((msg) => {
                    const isOffender = msg.senderId === detail.offenderId;
                    const isMaskedPlaceholder =
                      msg.content === '***' || msg.content.includes('[SĐT') || msg.content.includes('[Liên kết');

                    const displayContent = isMaskedPlaceholder && isOffender ? detail.flaggedContent : msg.content;
                    const isFlagged =
                      displayContent === detail.flaggedContent ||
                      (detail.flaggedContent && displayContent.includes(detail.flaggedContent.slice(0, 20)));

                    const bubbleClass = isFlagged
                      ? 'og-chat-bubble--flagged og-chat-bubble--flagged-pulse'
                      : isOffender
                      ? 'og-chat-bubble--offender'
                      : 'og-chat-bubble--peer';

                    return (
                      <div
                        key={msg.messageId}
                        className={`og-chat-bubble-row ${isOffender ? 'og-chat-bubble-row--self' : 'og-chat-bubble-row--peer'}`}
                      >
                        <div className={`og-chat-bubble ${bubbleClass}`}>
                          {(msg.type === 'IMAGE' || msg.type === 'VIDEO') && (
                            <KtvChatAttachment
                              messageId={msg.messageId}
                              type={msg.type}
                              caption={!isMediaOnly(msg.type, displayContent) ? displayContent : undefined}
                              onOpenLightbox={handleOpenLightbox}
                            />
                          )}
                          {!isMediaOnly(msg.type, displayContent) && (
                            <div>{displayContent}</div>
                          )}
                          <div className="og-chat-bubble__meta">
                            <span>{isOffender ? detail.offenderName : 'Đối tác'} · #{msg.messageId}</span>
                            <span>
                              {new Date(msg.createdAt).toLocaleTimeString('vi-VN', {
                                hour: '2-digit',
                                minute: '2-digit',
                              })}
                            </span>
                          </div>
                        </div>
                      </div>
                    );
                  })
                ) : (
                  <div style={{ color: 'var(--og-color-text-muted)', fontSize: '0.88rem', textAlign: 'center', padding: 24 }}>
                    Không có tin nhắn hội thoại đi kèm.
                  </div>
                )}

                {/* Bottom: Load newer messages */}
                <div className="og-chat-stream-load-bar">
                  <button
                    type="button"
                    className="og-chat-stream-load-btn"
                    disabled={!hasNewer || loadingNewer}
                    onClick={handleLoadNewer}
                  >
                    {loadingNewer ? (
                      'Đang tải tin mới hơn...'
                    ) : hasNewer ? (
                      'Tải tin mới hơn'
                    ) : (
                      'Đã tới tin nhắn mới nhất'
                    )}
                  </button>
                </div>
              </div>

              {/* Floating Action Bar in Multi-Select Mode */}
              {isMultiSelectMode && (
                <div className="og-floating-action-bar">
                  <span className="og-floating-action-bar__count">
                    Đã chọn: {selectedMessageIds.length} tin nhắn
                  </span>
                  <div className="og-floating-action-bar__actions">
                    <Button
                      variant="danger"
                      size="sm"
                      disabled={selectedMessageIds.length === 0}
                      onClick={handleOpenBatchModal}
                    >
                      <ConsoleIcons.Scale size={13} style={{ marginRight: 6 }} />
                      Xử lý chung các tin đã chọn
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => {
                        setIsMultiSelectMode(false);
                        setSelectedMessageIds([]);
                      }}
                    >
                      ✕ Thoát chọn nhiều
                    </Button>
                  </div>
                </div>
              )}
            </>
          )}
        </section>

        {/* ======================================================================
            COLUMN 3: THÔNG TIN VI PHẠM & QUYẾT ĐỊNH XỬ LÝ (VIOLATION DETAILS & ACTIONS)
            ====================================================================== */}
        <aside className="og-chat-mod-detail-pane" aria-label="Chi tiết hồ sơ vi phạm">
          {detailLoading ? (
            <div style={{ padding: 30, textAlign: 'center', color: 'var(--og-color-text-secondary)', fontSize: '0.88rem' }}>
              Đang tải chi tiết hồ sơ...
            </div>
          ) : detailError ? (
            <div style={{ padding: 14 }}>
              <Alert type="danger">{detailError}</Alert>
            </div>
          ) : !detail ? (
            <EmptyState
              title="Chọn một vi phạm để xử lý"
              description="Chi tiết nội dung vi phạm, thông tin tài khoản và công cụ xử lý sẽ hiển thị ở đây."
            />
          ) : (
            <>
              {/* Scrollable details body */}
              <div className="og-detail-scrollable">
                {/* A. Offender Profile Card */}
                <div className="og-detail-offender-card">
                  <div className="og-detail-offender-top">
                    <div className="og-detail-avatar">
                      {detail.offenderName.charAt(0).toUpperCase()}
                    </div>
                    <div className="og-detail-offender-meta">
                      <div className="og-detail-offender-name-row">
                        <h2>{detail.offenderName}</h2>
                        <button
                          type="button"
                          className="og-chat-stream-load-btn"
                          style={{ fontSize: '0.76rem', padding: '2px 8px' }}
                          onClick={() => handleOpenHistory(detail.offenderId)}
                          title="Xem toàn bộ lịch sử vi phạm của người dùng này"
                        >
                          <ConsoleIcons.FileText size={12} />
                          <span>Lịch sử vi phạm</span>
                        </button>
                      </div>
                      <span className="og-detail-offender-email">
                        {detail.offenderEmail} · ID: #{detail.offenderId}
                      </span>
                    </div>
                  </div>

                  <div className="og-chat-mod-stats-ribbon">
                    <span className="og-chat-mod-stat-pill">
                      Điểm uy tín: <strong>{detail.offenderReputation}/100</strong>
                    </span>
                    <span className="og-chat-mod-stat-pill">
                      Điểm vi phạm chat: <strong>{detail.offenderViolationScore}</strong>
                    </span>
                    {detail.offenderChatRestrictedUntil && (
                      <span className="og-chat-mod-stat-pill" style={{ borderColor: 'var(--og-color-danger)', color: 'var(--og-color-danger)' }}>
                        Cấm chat đến:{' '}
                        <strong>
                          {new Date(detail.offenderChatRestrictedUntil).toLocaleDateString('vi-VN')}
                        </strong>
                      </span>
                    )}
                  </div>
                </div>

                {/* B. Phân loại vi phạm */}
                <div className="og-detail-section-card">
                  <h4 className="og-detail-section-title">
                    Phân loại vi phạm
                    <span
                      className={`og-badge-severity og-badge-severity--${detail.severityLevel.toLowerCase()}`}
                    >
                      {detail.severityLevel}
                    </span>
                  </h4>
                  <div className="og-detail-keyval-row">
                    <span>Loại vi phạm:</span>
                    <strong>{VIOLATION_TYPE_LABELS[detail.violationType] || detail.violationType}</strong>
                  </div>
                  <div className="og-detail-keyval-row">
                    <span>Mã cuộc trò chuyện:</span>
                    <strong>#{detail.conversationId}</strong>
                  </div>
                  {detail.flaggedMessageId && (
                    <div className="og-detail-keyval-row">
                      <span>Mã tin nhắn:</span>
                      <strong>#{detail.flaggedMessageId}</strong>
                    </div>
                  )}
                  <div className="og-detail-keyval-row">
                    <span>Thời gian phát hiện:</span>
                    <span>
                      {new Date(detail.createdAt).toLocaleString('vi-VN', {
                        day: '2-digit',
                        month: '2-digit',
                        year: 'numeric',
                        hour: '2-digit',
                        minute: '2-digit',
                      })}
                    </span>
                  </div>
                  <div className="og-detail-keyval-row">
                    <span>Trạng thái:</span>
                    <strong
                      style={{
                        color:
                          detail.status === 'RESOLVED'
                            ? 'var(--og-color-success, #047857)'
                            : detail.status === 'PENDING'
                            ? 'var(--og-color-warning, #b45309)'
                            : 'var(--og-color-text-secondary)',
                      }}
                    >
                      {detail.status === 'PENDING'
                        ? 'Chờ xử lý'
                        : detail.status === 'RESOLVED'
                        ? 'Đã xử phạt'
                        : 'Đã bỏ qua'}
                    </strong>
                  </div>
                </div>

                {/* C. Nội dung bị phát hiện (Evidence Box) */}
                <div className="og-detail-evidence-card">
                  <div className="og-detail-evidence-header">
                    <h4 className="og-detail-evidence-title">
                      VĂN BẢN GỐC (RAW TEXT)
                    </h4>
                    <button
                      type="button"
                      className="og-btn-copy-evidence"
                      onClick={() => handleCopyEvidence(detail.flaggedContent)}
                      title="Sao chép nội dung vi phạm vào bộ nhớ tạm"
                    >
                      <svg
                        width="12"
                        height="12"
                        viewBox="0 0 24 24"
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="1.8"
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        aria-hidden="true"
                      >
                        <rect x="9" y="9" width="13" height="13" rx="2" ry="2" />
                        <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
                      </svg>
                      {copyFeedback ? 'Đã sao chép' : 'Sao chép'}
                    </button>
                  </div>
                  <div className="og-detail-evidence-content">
                    {detail.flaggedContent}
                  </div>
                </div>

                {/* D. Quyết định đã thi hành (Nếu không còn Pending) */}
                {detail.status !== 'PENDING' && (
                  <div className="og-chat-mod-resolution-box">
                    <div style={{ display: 'flex', justifyContent: 'space-between', fontWeight: 700 }}>
                      <span>Biện pháp: {detail.decisionAction}</span>
                      <span style={{ color: 'var(--og-color-danger)' }}>Trừ: {detail.reputationPenalty} điểm</span>
                    </div>
                    <div style={{ color: 'var(--og-color-text-secondary)' }}>
                      Ghi chú: {detail.decisionNote || 'Không có ghi chú'}
                    </div>
                    <div style={{ color: 'var(--og-color-text-muted)', fontSize: '0.74rem' }}>
                      Xử lý bởi: {detail.resolverName || 'KTV'} ·{' '}
                      {detail.resolvedAt ? new Date(detail.resolvedAt).toLocaleString('vi-VN') : ''}
                    </div>
                  </div>
                )}
              </div>

              {/* Sticky Action Footer - Shortened & Symmetrical Buttons */}
              <div className="og-detail-actions-footer">
                {detail.status === 'PENDING' ? (
                  <div className="og-detail-actions-btn-group">
                    <Button variant="outline" onClick={() => handleOpenSanction('DISMISS')}>
                      Bỏ qua
                    </Button>
                    <Button variant="danger" onClick={() => handleOpenSanction('RESTRICT_CHAT')}>
                      <ConsoleIcons.Scale size={14} style={{ marginRight: 6 }} />
                      Xử phạt
                    </Button>
                  </div>
                ) : (
                  <div
                    style={{
                      textAlign: 'center',
                      padding: '8px 12px',
                      borderRadius: 6,
                      fontWeight: 700,
                      fontSize: '0.84rem',
                      background: detail.status === 'RESOLVED' ? 'rgba(39, 99, 71, 0.12)' : 'var(--og-color-bg)',
                      color: detail.status === 'RESOLVED' ? 'var(--og-color-primary)' : 'var(--og-color-text-secondary)',
                      border: '1px solid currentColor',
                    }}
                  >
                    {detail.status === 'RESOLVED' ? 'ĐÃ XỬ LÝ VI PHẠM' : 'ĐÃ BỎ QUA HỒ SƠ'}
                  </div>
                )}
              </div>
            </>
          )}
        </aside>
      </div>

      {/* Sanction Dialog Modal (UC74 - Existing Report) */}
      {showModal && (
        <div className="og-modal-backdrop" onClick={() => !submitting && setShowModal(false)}>
          <div className="og-modal-dialog" onClick={(e) => e.stopPropagation()}>
            <h2>Quyết định xử lý vi phạm</h2>
            <p style={{ color: 'var(--og-color-text-secondary)', fontSize: '0.86rem', margin: 0 }}>
              Áp dụng chế tài đối với tài khoản <strong>{detail?.offenderName}</strong> (ID #{detail?.offenderId}).
            </p>

            <div className="og-modal-form-group">
              <label htmlFor="action-choice">Hành động xử phạt</label>
              <Select
                hideLabel
                label="Hành động xử phạt"
                value={actionChoice}
                onChange={(val) => {
                  const actionVal = val as any;
                  setActionChoice(actionVal);
                  if (actionVal === 'DISMISS') setPenaltyChoice(0);
                  else if (actionVal === 'LOCK_ACCOUNT') setPenaltyChoice(20);
                  else setPenaltyChoice(5);
                }}
                options={ACTION_OPTIONS}
              />
            </div>

            {actionChoice !== 'DISMISS' && (
              <div className="og-modal-form-group">
                <label htmlFor="penalty-choice">Trừ điểm uy tín</label>
                <Select
                  hideLabel
                  label="Trừ điểm uy tín"
                  value={String(penaltyChoice)}
                  onChange={(val) => setPenaltyChoice(Number(val))}
                  options={PENALTY_SCORE_OPTIONS}
                />
              </div>
            )}

            {actionChoice === 'RESTRICT_CHAT' && (
              <div className="og-modal-form-group">
                <label htmlFor="days-choice">Thời hạn cấm gửi tin nhắn</label>
                <Select
                  hideLabel
                  label="Thời hạn cấm gửi tin nhắn"
                  value={String(daysChoice)}
                  onChange={(val) => setDaysChoice(Number(val))}
                  options={DAYS_OPTIONS}
                />
              </div>
            )}

            <div className="og-modal-form-group">
              <label htmlFor="decision-note">Ghi chú / Căn cứ quyết định (Bắt buộc)</label>
              <textarea
                id="decision-note"
                placeholder="Nêu rõ lý do xử phạt và điều khoản vi phạm..."
                value={decisionNote}
                onChange={(e) => setDecisionNote(e.target.value)}
              />
            </div>

            <div className="og-modal-actions">
              <Button variant="ghost" onClick={() => setShowModal(false)} disabled={submitting}>
                Hủy bỏ
              </Button>
              <Button
                variant={actionChoice === 'DISMISS' ? 'outline' : 'danger'}
                onClick={handleSubmitDecision}
                disabled={submitting}
              >
                {submitting ? 'Đang lưu quyết định...' : 'Xác nhận thi hành'}
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* Batch Sanction Modal */}
      {showBatchModal && (
        <div className="og-modal-backdrop" onClick={() => !submittingBatch && setShowBatchModal(false)}>
          <div className="og-modal-dialog" onClick={(e) => e.stopPropagation()}>
            <h2>Xử lý chung các tin nhắn vi phạm</h2>
            <p style={{ color: 'var(--og-color-text-secondary)', fontSize: '0.86rem', margin: 0 }}>
              Áp dụng chế tài tập trung cho <strong>{selectedMessageIds.length} tin nhắn</strong> của người dùng{' '}
              <strong>{detail?.offenderName}</strong> (ID #{detail?.offenderId}).
            </p>

            <div
              style={{
                background: 'var(--og-color-bg)',
                border: '1px solid var(--og-color-border)',
                padding: '10px 14px',
                borderRadius: 6,
                fontSize: '0.84rem',
                color: 'var(--og-color-text-secondary)',
                display: 'flex',
                alignItems: 'center',
                gap: 8,
              }}
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" style={{ flexShrink: 0, color: 'var(--og-color-primary)' }}>
                <circle cx="12" cy="12" r="10" />
                <line x1="12" y1="16" x2="12" y2="12" />
                <line x1="12" y1="8" x2="12.01" y2="8" />
              </svg>
              <span>
                Điểm phạt uy tín sẽ được trừ <strong>duy nhất 1 lần</strong> cho toàn bộ đợt xử phạt này, không nhân dồn theo từng tin nhắn.
              </span>
            </div>

            <div className="og-modal-form-group">
              <label htmlFor="batch-action-choice">Hành động xử phạt</label>
              <Select
                hideLabel
                label="Hành động xử phạt"
                value={batchActionChoice}
                onChange={(val) => {
                  const actionVal = val as any;
                  setBatchActionChoice(actionVal);
                  if (actionVal === 'DISMISS') setBatchPenaltyChoice(0);
                  else if (actionVal === 'LOCK_ACCOUNT') setBatchPenaltyChoice(20);
                  else setBatchPenaltyChoice(5);
                }}
                options={ACTION_OPTIONS}
              />
            </div>

            {batchActionChoice !== 'DISMISS' && (
              <div className="og-modal-form-group">
                <label htmlFor="batch-penalty-choice">Trừ điểm uy tín (Áp dụng 1 lần)</label>
                <Select
                  hideLabel
                  label="Trừ điểm uy tín"
                  value={String(batchPenaltyChoice)}
                  onChange={(val) => setBatchPenaltyChoice(Number(val))}
                  options={PENALTY_SCORE_OPTIONS}
                />
              </div>
            )}

            {batchActionChoice === 'RESTRICT_CHAT' && (
              <div className="og-modal-form-group">
                <label htmlFor="batch-days-choice">Thời hạn cấm gửi tin nhắn</label>
                <Select
                  hideLabel
                  label="Thời hạn cấm gửi tin nhắn"
                  value={String(batchDaysChoice)}
                  onChange={(val) => setBatchDaysChoice(Number(val))}
                  options={DAYS_OPTIONS}
                />
              </div>
            )}

            <div className="og-modal-form-group">
              <label htmlFor="batch-decision-note">Ghi chú quyết định (Gửi thông báo đến người vi phạm)</label>
              <textarea
                id="batch-decision-note"
                placeholder="Nêu rõ lý do xử phạt tập trung..."
                value={batchDecisionNote}
                onChange={(e) => setBatchDecisionNote(e.target.value)}
              />
            </div>

            <div className="og-modal-actions">
              <Button variant="ghost" onClick={() => setShowBatchModal(false)} disabled={submittingBatch}>
                Hủy bỏ
              </Button>
              <Button
                variant={batchActionChoice === 'DISMISS' ? 'outline' : 'danger'}
                onClick={handleSubmitBatch}
                disabled={submittingBatch}
              >
                {submittingBatch ? 'Đang thực hiện...' : 'Xác nhận xử lý chung'}
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* Manual Flag Modal (Triggered by [!] button) */}
      {showManualModal && manualTarget && (
        <div className="og-modal-backdrop" onClick={() => !submittingManual && setShowManualModal(false)}>
          <div className="og-modal-dialog" onClick={(e) => e.stopPropagation()}>
            <h2>Gắn cờ vi phạm thủ công (UC74)</h2>
            <p style={{ color: 'var(--og-color-text-secondary)', fontSize: '0.86rem', margin: 0 }}>
              Gắn cờ vi phạm đối với tin nhắn #{manualTarget.messageId} của người dùng{' '}
              <strong>{manualTarget.senderName || detail?.offenderName}</strong> (ID #{manualTarget.senderId}).
            </p>

            <div
              style={{
                background: 'var(--og-color-bg)',
                border: '1px solid var(--og-color-border)',
                padding: '8px 12px',
                borderRadius: 6,
                fontSize: '0.86rem',
                fontStyle: 'italic',
                color: 'var(--og-color-danger, #ef4444)',
              }}
            >
              &ldquo;{manualTarget.rawContent || manualTarget.content}&rdquo;
            </div>

            <div className="og-modal-form-group">
              <label htmlFor="manual-type">Loại vi phạm</label>
              <Select
                hideLabel
                label="Loại vi phạm"
                value={manualType}
                onChange={setManualType}
                options={TYPE_OPTIONS.filter((o) => o.value !== 'ALL')}
              />
            </div>

            <div className="og-modal-form-group">
              <label htmlFor="manual-severity">Mức độ nghiêm trọng</label>
              <Select
                hideLabel
                label="Mức độ nghiêm trọng"
                value={manualSeverity}
                onChange={setManualSeverity}
                options={SEVERITY_OPTIONS.filter((o) => o.value !== 'ALL')}
              />
            </div>

            <div className="og-modal-form-group">
              <label htmlFor="manual-action">Hành động xử phạt</label>
              <Select
                hideLabel
                label="Hành động xử phạt"
                value={manualAction}
                onChange={(val) => {
                  const act = val as any;
                  setManualAction(act);
                  if (act === 'DISMISS') setManualPenalty(0);
                  else if (act === 'LOCK_ACCOUNT') setManualPenalty(20);
                  else setManualPenalty(5);
                }}
                options={ACTION_OPTIONS}
              />
            </div>

            {manualAction !== 'DISMISS' && (
              <div className="og-modal-form-group">
                <label htmlFor="manual-penalty">Trừ điểm uy tín</label>
                <Select
                  hideLabel
                  label="Trừ điểm uy tín"
                  value={String(manualPenalty)}
                  onChange={(val) => setManualPenalty(Number(val))}
                  options={PENALTY_SCORE_OPTIONS}
                />
              </div>
            )}

            {manualAction === 'RESTRICT_CHAT' && (
              <div className="og-modal-form-group">
                <label htmlFor="manual-days">Thời hạn cấm gửi tin nhắn</label>
                <Select
                  hideLabel
                  label="Thời hạn cấm gửi tin nhắn"
                  value={String(manualDays)}
                  onChange={(val) => setManualDays(Number(val))}
                  options={DAYS_OPTIONS}
                />
              </div>
            )}

            <div className="og-modal-form-group">
              <label htmlFor="manual-note">Ghi chú quyết định</label>
              <textarea
                id="manual-note"
                value={manualNote}
                onChange={(e) => setManualNote(e.target.value)}
              />
            </div>

            <div className="og-modal-actions">
              <Button variant="ghost" onClick={() => setShowManualModal(false)} disabled={submittingManual}>
                Hủy bỏ
              </Button>
              <Button variant="danger" onClick={handleSubmitManualFlag} disabled={submittingManual}>
                {submittingManual ? 'Đang thi hành...' : 'Xác nhận gắn cờ & Thi hành'}
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* User Violation History Modal / Timeline */}
      {showHistoryModal && (
        <div className="og-modal-backdrop" onClick={() => setShowHistoryModal(false)}>
          <div className="og-modal-dialog og-history-modal-dialog" onClick={(e) => e.stopPropagation()}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <h2>Lịch sử vi phạm tài khoản</h2>
              <button
                type="button"
                style={{
                  background: 'transparent',
                  border: 'none',
                  fontSize: '1.2rem',
                  cursor: 'pointer',
                  color: 'var(--og-color-text-secondary)',
                }}
                onClick={() => setShowHistoryModal(false)}
                aria-label="Đóng"
              >
                ✕
              </button>
            </div>

            {historyLoading ? (
              <div style={{ padding: 32, textAlign: 'center', color: 'var(--og-color-text-secondary)' }}>
                Đang tải lịch sử vi phạm...
              </div>
            ) : historyError ? (
              <Alert type="danger">{historyError}</Alert>
            ) : !historyData ? (
              <EmptyState title="Không tìm thấy" description="Không có thông tin lịch sử người dùng." />
            ) : (
              <>
                <div
                  style={{
                    background: 'var(--og-color-bg)',
                    border: '1px solid var(--og-color-border)',
                    borderRadius: 8,
                    padding: '12px 14px',
                    display: 'flex',
                    justifyContent: 'space-between',
                    flexWrap: 'wrap',
                    gap: 10,
                  }}
                >
                  <div>
                    <strong style={{ fontSize: '0.98rem', color: 'var(--og-color-text-primary)' }}>
                      {historyData.fullName}
                    </strong>
                    <div style={{ fontSize: '0.82rem', color: 'var(--og-color-text-secondary)' }}>
                      {historyData.email} · ID #{historyData.userId}
                    </div>
                  </div>
                  <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                    <span className="og-chat-mod-stat-pill">
                      Điểm uy tín: <strong>{historyData.reputationScore}/100</strong>
                    </span>
                    <span className="og-chat-mod-stat-pill">
                      Tổng số vi phạm: <strong>{historyData.reports.length}</strong>
                    </span>
                  </div>
                </div>

                <div className="og-history-timeline">
                  {historyData.reports.length === 0 ? (
                    <div style={{ padding: 24, textAlign: 'center', color: 'var(--og-color-text-muted)' }}>
                      Tài khoản chưa từng có tiền án hoặc báo cáo vi phạm nào.
                    </div>
                  ) : (
                    historyData.reports.map((rep) => {
                      const severityClass = `og-badge-severity--${rep.severityLevel.toLowerCase()}`;
                      return (
                        <div key={rep.reportId} className="og-history-item">
                          <div className="og-history-item__header">
                            <span style={{ fontWeight: 700, color: 'var(--og-color-text-primary)' }}>
                              Hồ sơ #{rep.reportId} · {VIOLATION_TYPE_LABELS[rep.violationType] || rep.violationType}
                            </span>
                            <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                              <span className={`og-badge-severity ${severityClass}`}>
                                {rep.severityLevel}
                              </span>
                              <span
                                style={{
                                  fontSize: '0.74rem',
                                  padding: '2px 6px',
                                  borderRadius: 4,
                                  fontWeight: 700,
                                  background:
                                    rep.status === 'RESOLVED'
                                      ? 'rgba(39, 99, 71, 0.12)'
                                      : rep.status === 'PENDING'
                                      ? 'rgba(245, 158, 11, 0.15)'
                                      : 'var(--og-color-bg)',
                                  color:
                                    rep.status === 'RESOLVED'
                                      ? 'var(--og-color-primary)'
                                      : rep.status === 'PENDING'
                                      ? 'var(--og-color-warning)'
                                      : 'var(--og-color-text-secondary)',
                                }}
                              >
                                {rep.status}
                              </span>
                            </div>
                          </div>

                          <div className="og-history-item__content">
                            &ldquo;{rep.flaggedContent}&rdquo;
                          </div>

                          {rep.decisionAction && (
                            <div style={{ fontSize: '0.8rem', color: 'var(--og-color-text-secondary)' }}>
                              <strong>Xử lý:</strong> {rep.decisionAction} (Trừ {rep.reputationPenalty} điểm)
                              {rep.decisionNote && <span> — {rep.decisionNote}</span>}
                            </div>
                          )}

                          <div className="og-history-item__footer">
                            <span>
                              Tạo lúc:{' '}
                              {new Date(rep.createdAt).toLocaleString('vi-VN', {
                                day: '2-digit',
                                month: '2-digit',
                                year: 'numeric',
                                hour: '2-digit',
                                minute: '2-digit',
                              })}
                            </span>
                            {rep.status === 'PENDING' && (
                              <button
                                type="button"
                                className="og-chat-stream-load-btn"
                                style={{
                                  background: 'rgba(239, 68, 68, 0.12)',
                                  borderColor: 'var(--og-color-danger)',
                                  color: 'var(--og-color-danger)',
                                  fontWeight: 700,
                                }}
                                onClick={() => {
                                  setShowHistoryModal(false);
                                  setParams({ reportId: String(rep.reportId) });
                                }}
                              >
                                Xử lý ngay
                              </button>
                            )}
                          </div>
                        </div>
                      );
                    })
                  )}
                </div>
              </>
            )}
          </div>
        </div>
      )}

      {/* Lightbox / Zoom Modal for Image & Video Inspection */}
      <MediaLightboxModal
        isOpen={lightboxState.isOpen}
        src={lightboxState.src}
        type={lightboxState.type}
        alt="Tệp đính kèm trong hội thoại"
        caption={lightboxState.caption}
        onClose={handleCloseLightbox}
      />
    </section>
  );
};
