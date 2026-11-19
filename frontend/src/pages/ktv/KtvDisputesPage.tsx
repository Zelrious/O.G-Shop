import React, { useState, useEffect, useCallback, useRef } from 'react';
import { PageHeader } from '../../shared/components';
import { Select } from '../../shared/components/Select/Select';
import { MediaLightboxModal } from '../../shared/components/MediaLightboxModal';
import { ConsoleIcons } from '../../shared/layout/ConsoleIcons';
import {
  TableActionDropdown,
  BulkActionBar,
  TableCheckbox,
} from '../../shared/layout/ConsoleTableActions';
import { formatVnd } from '../../shared/utils/currency';
import { useManagementStore } from '../../features/management/useManagementStore';
import { disputeApi } from '../../features/disputes/disputeApi';
import type { DisputeDetail, DisputeVerdictAction } from '../../features/disputes/types';

interface UnifiedDisputeItem {
  id: string;
  disputeId: number;
  orderId: number;
  orderCode: string;
  buyerName: string;
  sellerName: string;
  productTitle: string;
  disputeAmount: number;
  reason: string;
  description?: string;
  buyerEvidenceUrls: string[];
  videoUnboxingUrl?: string | null;
  sellerResponse?: string | null;
  sellerEvidenceUrls?: string[];
  status: string;
  createdAt: string;
}

const FILTER_TABS = [
  { value: 'ALL', label: 'Tất cả' },
  { value: 'WAITING_KTV_REVIEW', label: 'Chờ KTV phán quyết' },
  { value: 'WAITING_SELLER_RESPONSE', label: 'Chờ Seller' },
  { value: 'RETURN_APPROVED', label: 'Chấp thuận trả hàng' },
  { value: 'RESOLVED_REFUND', label: 'Đã hoàn tiền' },
  { value: 'RESOLVED_RELEASE', label: 'Đã giải ngân' },
] as const;

const matchesStatus = (status: string, filter: string) => {
  if (filter === 'ALL') return true;
  if (filter === 'WAITING_KTV_REVIEW') {
    return status === 'WAITING_KTV_REVIEW' || status === 'OPEN' || status === 'INVESTIGATING';
  }
  if (filter === 'WAITING_SELLER_RESPONSE') {
    return status === 'WAITING_SELLER_RESPONSE' || status === 'SELLER_REJECTED';
  }
  return status === filter;
};

const matchesSearch = (item: UnifiedDisputeItem, q: string) => {
  if (!q.trim()) return true;
  const lower = q.trim().toLowerCase();
  const digits = lower.replace(/\D/g, '');
  return (
    item.orderCode.toLowerCase().includes(lower) ||
    item.productTitle.toLowerCase().includes(lower) ||
    item.buyerName.toLowerCase().includes(lower) ||
    item.sellerName.toLowerCase().includes(lower) ||
    item.reason.toLowerCase().includes(lower) ||
    Boolean(item.description && item.description.toLowerCase().includes(lower)) ||
    item.id.toLowerCase().includes(lower) ||
    (digits.length > 0 && (item.id.includes(digits) || item.orderCode.includes(digits)))
  );
};

export const KtvDisputesPage: React.FC = () => {
  const { disputes: mockDisputes, resolveDispute: mockResolveDispute } = useManagementStore();
  const mockDisputesRef = useRef(mockDisputes);

  useEffect(() => {
    mockDisputesRef.current = mockDisputes;
  }, [mockDisputes]);

  const [disputes, setDisputes] = useState<UnifiedDisputeItem[]>([]);
  const [allDisputesPool, setAllDisputesPool] = useState<UnifiedDisputeItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [searchTerm, setSearchTerm] = useState('');
  const [debouncedQuery, setDebouncedQuery] = useState('');

  // 300ms debounce on search input to prevent rapid API requests and UI flickering
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedQuery(searchTerm.trim());
    }, 300);
    return () => clearTimeout(timer);
  }, [searchTerm]);

  const [selectedDispute, setSelectedDispute] = useState<DisputeDetail | UnifiedDisputeItem | null>(null);
  const [verdictAction, setVerdictAction] = useState<DisputeVerdictAction>('RETURN_APPROVED');
  const [verdictNotes, setVerdictNotes] = useState('');
  const [penaltyPoints, setPenaltyPoints] = useState<number>(0);
  const [submitting, setSubmitting] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  // Lightbox modal state for 2-way visual evidence inspection
  const [lightboxMedia, setLightboxMedia] = useState<{
    src: string;
    type: 'IMAGE' | 'VIDEO';
    caption?: string;
  } | null>(null);

  // Multi-select state
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());

  const fetchDisputes = useCallback(async (activeSignal?: { cancelled: boolean }) => {
    setLoading(true);
    try {
      let pool: UnifiedDisputeItem[] = [];

      try {
        const allRes = await disputeApi.getDisputes({ page: 0, size: 100 });
        if (allRes && allRes.items && allRes.items.length > 0) {
          pool = allRes.items.map((item) => ({
            id: String(item.disputeId),
            disputeId: item.disputeId,
            orderId: item.orderId,
            orderCode: item.orderCode,
            buyerName: item.buyerName,
            sellerName: item.sellerName,
            productTitle: item.productTitle,
            disputeAmount: item.disputeAmount,
            reason: item.reason,
            buyerEvidenceUrls: [],
            status: item.status,
            createdAt: item.createdAt,
          }));
        }
      } catch {
        // Fallback to mock pool if backend unreachable
      }

      if (pool.length === 0) {
        pool = mockDisputesRef.current.map((d) => ({
          id: d.id,
          disputeId: parseInt(d.id.replace(/\D/g, '') || '1', 10),
          orderId: 1000 + parseInt(d.id.replace(/\D/g, '') || '1', 10),
          orderCode: d.orderCode,
          buyerName: d.buyerName,
          sellerName: d.sellerName,
          productTitle: d.productTitle,
          disputeAmount: d.disputeAmount,
          reason: d.reason,
          description: d.description,
          buyerEvidenceUrls: d.buyerEvidenceUrls || [],
          videoUnboxingUrl: d.buyerEvidenceUrls?.find((u) => u.includes('mp4') || u.includes('video')),
          sellerResponse: d.sellerResponse,
          sellerEvidenceUrls: [],
          status: d.status,
          createdAt: d.createdAt,
        }));
      }

      if (activeSignal?.cancelled) return;
      setAllDisputesPool(pool);

      // Query filtered items via API or client pool fallback
      let filteredItems: UnifiedDisputeItem[] | null = null;
      try {
        const res = await disputeApi.getDisputes({
          status: statusFilter === 'ALL' ? undefined : statusFilter,
          query: debouncedQuery || undefined,
          page: 0,
          size: 50,
        });

        if (activeSignal?.cancelled) return;

        if (res && Array.isArray(res.items)) {
          filteredItems = res.items.map((item) => ({
            id: String(item.disputeId),
            disputeId: item.disputeId,
            orderId: item.orderId,
            orderCode: item.orderCode,
            buyerName: item.buyerName,
            sellerName: item.sellerName,
            productTitle: item.productTitle,
            disputeAmount: item.disputeAmount,
            reason: item.reason,
            buyerEvidenceUrls: [],
            status: item.status,
            createdAt: item.createdAt,
          }));
        }
      } catch {
        // Fallback to pool filter
      }

      if (filteredItems === null || pool.length === mockDisputesRef.current.length) {
        filteredItems = pool.filter(
          (item) => matchesStatus(item.status, statusFilter) && matchesSearch(item, debouncedQuery)
        );
      }

      setDisputes(filteredItems);
    } catch {
      if (activeSignal?.cancelled) return;
      const fallback = mockDisputesRef.current
        .map((d) => ({
          id: d.id,
          disputeId: parseInt(d.id.replace(/\D/g, '') || '1', 10),
          orderId: 1000 + parseInt(d.id.replace(/\D/g, '') || '1', 10),
          orderCode: d.orderCode,
          buyerName: d.buyerName,
          sellerName: d.sellerName,
          productTitle: d.productTitle,
          disputeAmount: d.disputeAmount,
          reason: d.reason,
          description: d.description,
          buyerEvidenceUrls: d.buyerEvidenceUrls || [],
          videoUnboxingUrl: d.buyerEvidenceUrls?.find((u) => u.includes('mp4') || u.includes('video')),
          sellerResponse: d.sellerResponse,
          sellerEvidenceUrls: [],
          status: d.status,
          createdAt: d.createdAt,
        }))
        .filter((item) => matchesStatus(item.status, statusFilter) && matchesSearch(item, debouncedQuery));
      setAllDisputesPool(mockDisputesRef.current.map((d) => ({
        id: d.id,
        disputeId: parseInt(d.id.replace(/\D/g, '') || '1', 10),
        orderId: 1000 + parseInt(d.id.replace(/\D/g, '') || '1', 10),
        orderCode: d.orderCode,
        buyerName: d.buyerName,
        sellerName: d.sellerName,
        productTitle: d.productTitle,
        disputeAmount: d.disputeAmount,
        reason: d.reason,
        description: d.description,
        buyerEvidenceUrls: d.buyerEvidenceUrls || [],
        videoUnboxingUrl: d.buyerEvidenceUrls?.find((u) => u.includes('mp4') || u.includes('video')),
        sellerResponse: d.sellerResponse,
        sellerEvidenceUrls: [],
        status: d.status,
        createdAt: d.createdAt,
      })));
      setDisputes(fallback);
    } finally {
      if (!activeSignal?.cancelled) {
        setLoading(false);
      }
    }
  }, [statusFilter, debouncedQuery]);

  useEffect(() => {
    const signal = { cancelled: false };
    fetchDisputes(signal);
    return () => {
      signal.cancelled = true;
    };
  }, [fetchDisputes]);

  const isAllSelected =
    disputes.length > 0 && disputes.every((d) => selectedIds.has(d.id));
  const isSomeSelected =
    disputes.some((d) => selectedIds.has(d.id)) && !isAllSelected;

  const handleToggleAll = () => {
    if (isAllSelected) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(disputes.map((d) => d.id)));
    }
  };

  const handleToggleRow = (id: string) => {
    const next = new Set(selectedIds);
    if (next.has(id)) {
      next.delete(id);
    } else {
      next.add(id);
    }
    setSelectedIds(next);
  };

  const handleOpenArbitration = async (item: UnifiedDisputeItem) => {
    setActionError(null);
    setVerdictAction('RETURN_APPROVED');
    setVerdictNotes('');
    setPenaltyPoints(0);

    try {
      const detail = await disputeApi.getDisputeDetail(item.disputeId);
      setSelectedDispute(detail);
    } catch {
      setSelectedDispute(item);
    }
  };

  const handleApplyVerdict = async () => {
    if (!selectedDispute || !verdictNotes.trim()) return;

    setActionError(null);
    setSubmitting(true);
    try {
      const disputeId = selectedDispute.disputeId;

      await disputeApi.submitDecision(disputeId, {
        verdictAction,
        verdictNotes: verdictNotes.trim(),
        penaltyPoints: Number(penaltyPoints) || 0,
      });

      // Also sync mock store for client-side state
      try {
        const mockVerdict =
          verdictAction === 'RESOLVED_RELEASE' ? 'RELEASE_SELLER' : 'FULL_REFUND';
        const mockId =
          'id' in selectedDispute
            ? (selectedDispute as UnifiedDisputeItem).id
            : String(selectedDispute.disputeId);
        mockResolveDispute(mockId, mockVerdict, verdictNotes.trim());
      } catch {
        // Ignore if dispute exists only in backend DB and not in local mock store
      }

      setSuccessMessage(
        `Đã ghi nhận phán quyết (${verdictAction}) cho vụ việc đơn ${selectedDispute.orderCode} thành công.`
      );
      setSelectedDispute(null);
      fetchDisputes();
      setTimeout(() => setSuccessMessage(null), 5000);
    } catch (error) {
      setActionError(
        error instanceof Error ? error.message : 'Không thể ghi nhận quyết định phán quyết.'
      );
    } finally {
      setSubmitting(false);
    }
  };

  const renderStatus = (status: string) => {
    switch (status) {
      case 'OPEN':
      case 'WAITING_KTV_REVIEW':
        return (
          <span
            style={{
              padding: '3px 8px',
              borderRadius: '12px',
              fontSize: '0.74rem',
              fontWeight: 700,
              background: '#fee2e2',
              color: '#b91c1c',
              border: '1px solid #fecaca',
            }}
          >
            CHỜ KTV PHÁN QUYẾT
          </span>
        );
      case 'WAITING_SELLER_RESPONSE':
        return (
          <span
            style={{
              padding: '3px 8px',
              borderRadius: '12px',
              fontSize: '0.74rem',
              fontWeight: 700,
              background: '#fef3c7',
              color: '#b45309',
              border: '1px solid #fde68a',
            }}
          >
            CHỜ NGƯỜI BÁN GIẢI TRÌNH
          </span>
        );
      case 'SELLER_REJECTED':
        return (
          <span
            style={{
              padding: '3px 8px',
              borderRadius: '12px',
              fontSize: '0.74rem',
              fontWeight: 700,
              background: '#ffedd5',
              color: '#c2410c',
              border: '1px solid #fed7aa',
            }}
          >
            NGƯỜI BÁN TỪ CHỐI
          </span>
        );
      case 'RETURN_APPROVED':
        return (
          <span
            style={{
              padding: '3px 8px',
              borderRadius: '12px',
              fontSize: '0.74rem',
              fontWeight: 700,
              background: '#dcfce7',
              color: '#15803d',
              border: '1px solid #bbf7d0',
            }}
          >
            CHẤP THUẬN TRẢ HÀNG
          </span>
        );
      case 'RESOLVED_REFUND':
        return (
          <span
            style={{
              padding: '3px 8px',
              borderRadius: '12px',
              fontSize: '0.74rem',
              fontWeight: 700,
              background: '#dbeafe',
              color: '#1e40af',
              border: '1px solid #bfdbfe',
            }}
          >
            ĐÃ HOÀN TIỀN
          </span>
        );
      case 'RESOLVED_RELEASE':
        return (
          <span
            style={{
              padding: '3px 8px',
              borderRadius: '12px',
              fontSize: '0.74rem',
              fontWeight: 700,
              background: '#eff6ff',
              color: '#1d4ed8',
              border: '1px solid #bfdbfe',
            }}
          >
            GIẢI NGÂN CHO SELLER
          </span>
        );
      default:
        return (
          <span
            style={{
              padding: '3px 8px',
              borderRadius: '12px',
              fontSize: '0.74rem',
              fontWeight: 600,
              background: '#f1f5f9',
              color: '#475569',
            }}
          >
            {status}
          </span>
        );
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
      <style>{`
        @keyframes og-search-spin {
          0% { transform: translateY(-50%) rotate(0deg); }
          100% { transform: translateY(-50%) rotate(360deg); }
        }
      `}</style>

      {/* Page Header */}
      <PageHeader
        title="Phân xử tranh chấp & Khiếu nại giao dịch"
        description="Kiểm tra đối soát 2 chiều (Video mở kiện của Người mua vs Ảnh đóng gói của Người bán), phân xử giải ngân Escrow hoặc hoàn tiền theo UC62, UC71, UC75, UC83."
      />

      {actionError && (
        <div
          role="alert"
          style={{
            background: '#fef2f2',
            border: '1px solid #f87171',
            borderRadius: '8px',
            padding: '12px 16px',
            color: '#b91c1c',
            fontSize: '0.88rem',
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
          }}
        >
          <ConsoleIcons.AlertTriangle size={18} />
          <span>{actionError}</span>
        </div>
      )}

      {successMessage && (
        <div
          style={{
            background: 'var(--og-color-primary-light)',
            border: '1px solid var(--og-color-border)',
            borderRadius: '8px',
            padding: '12px 16px',
            color: 'var(--og-color-success)',
            fontSize: '0.88rem',
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
          }}
        >
          <ConsoleIcons.CheckCircle size={18} />
          <span>{successMessage}</span>
        </div>
      )}

      {/* Filter and Search Bar */}
      <div
        style={{
          display: 'flex',
          gap: '12px',
          flexWrap: 'wrap',
          alignItems: 'center',
          justifyContent: 'space-between',
          background: 'var(--og-color-surface)',
          padding: '12px 16px',
          borderRadius: '10px',
          border: '1px solid var(--og-color-border)',
        }}
      >
        <div style={{ display: 'flex', gap: '8px', alignItems: 'center', flexWrap: 'wrap' }}>
          <span style={{ fontSize: '0.84rem', fontWeight: 600, color: 'var(--og-color-text-secondary)' }}>
            Lọc trạng thái:
          </span>
          {FILTER_TABS.map((tab) => {
            const isSelected = statusFilter === tab.value;
            const count = allDisputesPool.filter((d) => matchesStatus(d.status, tab.value)).length;
            return (
              <button
                key={tab.value}
                type="button"
                onClick={() => setStatusFilter(tab.value)}
                style={{
                  padding: '6px 12px',
                  borderRadius: '6px',
                  fontSize: '0.8rem',
                  fontWeight: 600,
                  border: '1px solid',
                  cursor: 'pointer',
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: '6px',
                  borderColor: isSelected ? 'var(--og-color-primary)' : 'var(--og-color-border)',
                  background: isSelected ? 'var(--og-color-primary-light)' : 'transparent',
                  color: isSelected ? 'var(--og-color-primary)' : 'var(--og-color-text-secondary)',
                  transition: 'all 0.15s ease',
                }}
              >
                <span>{tab.label}</span>
                <span
                  style={{
                    padding: '1px 6px',
                    borderRadius: '10px',
                    fontSize: '0.72rem',
                    fontWeight: 700,
                    background: isSelected ? 'var(--og-color-primary)' : 'var(--og-color-surface-subtle)',
                    color: isSelected ? '#ffffff' : 'var(--og-color-text-secondary)',
                    border: isSelected ? 'none' : '1px solid var(--og-color-border)',
                    minWidth: '16px',
                    textAlign: 'center',
                  }}
                >
                  {count}
                </span>
              </button>
            );
          })}
        </div>

        <div style={{ position: 'relative', width: '280px' }}>
          <input
            type="text"
            placeholder="Tìm theo mã đơn hoặc người dùng..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            style={{
              width: '100%',
              padding: searchTerm ? '8px 58px 8px 12px' : '8px 36px 8px 12px',
              borderRadius: '6px',
              border: '1px solid var(--og-color-border)',
              fontSize: '0.84rem',
              boxSizing: 'border-box',
              background: 'var(--og-color-surface)',
              color: 'var(--og-color-text-primary)',
            }}
          />
          {searchTerm && (
            <button
              type="button"
              aria-label="Xóa tìm kiếm"
              onClick={() => setSearchTerm('')}
              style={{
                position: 'absolute',
                right: loading ? '32px' : '10px',
                top: '50%',
                transform: 'translateY(-50%)',
                background: 'transparent',
                border: 'none',
                color: 'var(--og-color-text-secondary)',
                cursor: 'pointer',
                padding: '2px 4px',
                fontSize: '14px',
                lineHeight: 1,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                borderRadius: '4px',
              }}
              onMouseEnter={(e) => (e.currentTarget.style.color = 'var(--og-color-danger)')}
              onMouseLeave={(e) => (e.currentTarget.style.color = 'var(--og-color-text-secondary)')}
            >
              ✕
            </button>
          )}
          {loading && (
            <div
              style={{
                position: 'absolute',
                right: '10px',
                top: '50%',
                width: '14px',
                height: '14px',
                border: '2px solid var(--og-color-border-strong)',
                borderTopColor: 'var(--og-color-primary)',
                borderRadius: '50%',
                animation: 'og-search-spin 0.65s linear infinite',
                pointerEvents: 'none',
              }}
            />
          )}
        </div>
      </div>

      {/* Bulk Action Bar */}
      <BulkActionBar
        selectedCount={selectedIds.size}
        totalCount={disputes.length}
        onClearSelection={() => setSelectedIds(new Set())}
        actions={[
          {
            label: 'Đẩy lên Admin xử lý hàng loạt',
            icon: <ConsoleIcons.AlertTriangle size={15} />,
            variant: 'warning',
            onClick: () => {
              alert(`Đã đẩy ${selectedIds.size} vụ việc lên Admin cấp cao.`);
              setSelectedIds(new Set());
            },
          },
        ]}
      />

      {/* Disputes Table */}
      <div
        style={{
          background: 'var(--og-color-surface)',
          borderRadius: '12px',
          border: '1px solid var(--og-color-border)',
          boxShadow: '0 1px 3px rgba(0,0,0,0.04)',
        }}
      >
        <div
          style={{
            overflowX: 'auto',
            overflowY: 'visible',
            minHeight: '440px',
            paddingBottom: '80px',
          }}
        >
          <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.88rem' }}>
            <thead>
              <tr style={{ borderBottom: '1px solid var(--og-color-border)', color: 'var(--og-color-text-secondary)', background: 'var(--og-color-bg)' }}>
                <th style={{ padding: '14px 16px', width: '40px', textAlign: 'center', whiteSpace: 'nowrap' }}>
                  <TableCheckbox
                    checked={isAllSelected}
                    indeterminate={isSomeSelected}
                    onChange={handleToggleAll}
                    ariaLabel="Chọn tất cả vụ việc"
                  />
                </th>
                <th style={{ padding: '14px 16px', whiteSpace: 'nowrap' }}>Mã vụ việc & Đơn</th>
                <th style={{ padding: '14px 16px', whiteSpace: 'nowrap' }}>Sản phẩm & Lý do</th>
                <th style={{ padding: '14px 16px', whiteSpace: 'nowrap' }}>Người mua / Người bán</th>
                <th style={{ padding: '14px 16px', whiteSpace: 'nowrap' }}>Tiền giữ Escrow</th>
                <th style={{ padding: '14px 16px', whiteSpace: 'nowrap' }}>Trạng thái</th>
                <th style={{ padding: '14px 16px', textAlign: 'right', minWidth: '95px', whiteSpace: 'nowrap' }}>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {disputes.length === 0 ? (
                <tr>
                  <td colSpan={7} style={{ padding: '48px 16px', textAlign: 'center' }}>
                    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: '12px' }}>
                      <div
                        style={{
                          width: '48px',
                          height: '48px',
                          borderRadius: '50%',
                          background: 'var(--og-color-surface-subtle)',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          color: 'var(--og-color-text-secondary)',
                        }}
                      >
                        <ConsoleIcons.Search size={22} />
                      </div>
                      <div style={{ fontWeight: 600, color: 'var(--og-color-text-primary)', fontSize: '0.92rem' }}>
                        {searchTerm
                          ? `Không tìm thấy vụ việc nào khớp với từ khóa "${searchTerm}"`
                          : statusFilter !== 'ALL'
                            ? `Không có vụ việc nào ở trạng thái "${FILTER_TABS.find((t) => t.value === statusFilter)?.label || statusFilter}"`
                            : 'Hiện chưa có vụ việc tranh chấp nào cần xử lý.'}
                      </div>
                      {(searchTerm || statusFilter !== 'ALL') && (
                        <button
                          type="button"
                          onClick={() => {
                            setSearchTerm('');
                            setStatusFilter('ALL');
                          }}
                          style={{
                            padding: '6px 14px',
                            borderRadius: '6px',
                            border: '1px solid var(--og-color-border)',
                            background: 'var(--og-color-surface)',
                            color: 'var(--og-color-primary)',
                            fontSize: '0.82rem',
                            fontWeight: 600,
                            cursor: 'pointer',
                          }}
                        >
                          Xóa bộ lọc & đặt lại tìm kiếm
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ) : (
                disputes.map((item) => {
                  const isSelected = selectedIds.has(item.id);
                  const canArbitrate =
                    item.status === 'OPEN' ||
                    item.status === 'INVESTIGATING' ||
                    item.status === 'WAITING_KTV_REVIEW' ||
                    item.status === 'WAITING_SELLER_RESPONSE' ||
                    item.status === 'SELLER_REJECTED';

                  return (
                    <tr
                      key={item.id}
                      style={{
                        borderBottom: '1px solid var(--og-color-surface-subtle)',
                        background: isSelected ? 'var(--og-color-primary-light)' : 'transparent',
                        transition: 'background-color 0.15s ease',
                      }}
                    >
                      <td style={{ padding: '14px 16px', textAlign: 'center' }}>
                        <TableCheckbox
                          checked={isSelected}
                          onChange={() => handleToggleRow(item.id)}
                          ariaLabel={`Chọn vụ việc ${item.orderCode}`}
                        />
                      </td>
                      <td style={{ padding: '14px 16px' }}>
                        <div style={{ fontWeight: 700, color: 'var(--og-color-text-primary)', fontFamily: 'monospace' }}>
                          {item.orderCode}
                        </div>
                        <div style={{ fontSize: '0.74rem', color: 'var(--og-color-text-secondary)' }}>
                          ID #{item.disputeId}
                        </div>
                      </td>
                      <td style={{ padding: '14px 16px', maxWidth: '280px' }}>
                        <div style={{ fontWeight: 600, color: 'var(--og-color-text-primary)' }}>{item.productTitle}</div>
                        <div style={{ fontSize: '0.78rem', color: 'var(--og-color-text-secondary)', marginTop: '2px' }}>
                          {item.reason}
                        </div>
                      </td>
                      <td style={{ padding: '14px 16px' }}>
                        <div style={{ fontSize: '0.84rem', color: 'var(--og-color-text-primary)' }}>
                          Mua: <strong style={{ color: '#2563eb' }}>{item.buyerName}</strong>
                        </div>
                        <div style={{ fontSize: '0.84rem', color: 'var(--og-color-text-secondary)' }}>
                          Bán: <strong style={{ color: 'var(--og-color-primary)' }}>{item.sellerName}</strong>
                        </div>
                      </td>
                      <td style={{ padding: '14px 16px', fontWeight: 700, color: 'var(--og-color-primary)', fontSize: '0.95rem', whiteSpace: 'nowrap' }}>
                        {formatVnd(item.disputeAmount)}
                      </td>
                      <td style={{ padding: '14px 16px' }}>{renderStatus(item.status)}</td>
                      <td style={{ padding: '14px 16px', textAlign: 'right', position: 'relative' }}>
                        <TableActionDropdown
                          direction="auto"
                          ariaLabel={`Thao tác phân xử đơn ${item.orderCode}`}
                          items={[
                            ...(canArbitrate
                              ? [
                                  {
                                    label: 'Đối soát & Phán quyết',
                                    icon: <ConsoleIcons.Scale size={16} />,
                                    onClick: () => handleOpenArbitration(item),
                                  },
                                ]
                              : [
                                  {
                                    label: 'Xem hồ sơ đối soát',
                                    icon: <ConsoleIcons.Eye size={16} />,
                                    onClick: () => handleOpenArbitration(item),
                                  },
                                ]),
                            {
                              label: 'Đẩy lên Admin xử lý',
                              icon: <ConsoleIcons.AlertTriangle size={16} />,
                              variant: 'danger',
                              onClick: () =>
                                alert(`Đã đẩy vụ việc ${item.orderCode} lên Admin cấp cao do tính chất phức tạp.`),
                            },
                            {
                              label: 'Sao chép mã đơn',
                              icon: <ConsoleIcons.FileText size={16} />,
                              onClick: () => {
                                navigator.clipboard?.writeText(item.orderCode);
                                alert(`Đã sao chép mã ${item.orderCode}`);
                              },
                            },
                          ]}
                        />
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Modal 2-Way Visual Evidence Inspection & Arbitration Verdict */}
      {selectedDispute && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(15, 23, 42, 0.55)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 1000,
            padding: '20px',
            backdropFilter: 'blur(3px)',
          }}
        >
          <div
            style={{
              background: 'var(--og-color-surface)',
              borderRadius: '16px',
              border: '1px solid var(--og-color-border)',
              padding: '24px',
              width: '100%',
              maxWidth: '920px',
              maxHeight: '90vh',
              overflowY: 'auto',
              boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.25)',
            }}
          >
            {/* Modal Header */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '16px' }}>
              <div>
                <h2 style={{ margin: '0 0 4px', fontSize: '1.35rem', color: 'var(--og-color-text-primary)', fontWeight: 700 }}>
                  Đối soát & Phán quyết: {selectedDispute.orderCode}
                </h2>
                <div style={{ fontSize: '0.86rem', color: 'var(--og-color-text-secondary)' }}>
                  Sản phẩm: <strong>{selectedDispute.productTitle}</strong> | Escrow đang giữ:{' '}
                  <strong style={{ color: 'var(--og-color-primary)', fontSize: '0.98rem' }}>
                    {formatVnd(selectedDispute.disputeAmount)}
                  </strong>
                </div>
              </div>
              <button
                type="button"
                onClick={() => setSelectedDispute(null)}
                style={{
                  background: 'transparent',
                  border: 'none',
                  fontSize: '1.25rem',
                  cursor: 'pointer',
                  color: 'var(--og-color-text-secondary)',
                }}
              >
                ✕
              </button>
            </div>

            {/* TWO-WAY VISUAL EVIDENCE SECTION */}
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: '1fr 1fr',
                gap: '16px',
                marginBottom: '20px',
                background: 'var(--og-color-bg)',
                padding: '16px',
                borderRadius: '12px',
                border: '1px solid var(--og-color-border)',
              }}
            >
              {/* Buyer Side Evidence */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontWeight: 700, color: '#2563eb' }}>
                  <ConsoleIcons.Users size={18} />
                  <span>Người mua ({selectedDispute.buyerName})</span>
                </div>
                <div style={{ fontSize: '0.82rem', background: '#fff', padding: '10px', borderRadius: '8px', border: '1px solid var(--og-color-border)' }}>
                  <div><strong>Lý do:</strong> {selectedDispute.reason}</div>
                  {selectedDispute.description && (
                    <div style={{ marginTop: '4px', color: 'var(--og-color-text-secondary)' }}>
                      <strong>Mô tả:</strong> {selectedDispute.description}
                    </div>
                  )}
                </div>

                {/* Video Unboxing Preview */}
                <div>
                  <div style={{ fontSize: '0.82rem', fontWeight: 600, marginBottom: '6px', color: 'var(--og-color-text-secondary)' }}>
                    🎥 Video mở kiện hàng (Unboxing Video):
                  </div>
                  {selectedDispute.videoUnboxingUrl ? (
                    <div style={{ borderRadius: '8px', overflow: 'hidden', border: '1px solid var(--og-color-border)', background: '#000' }}>
                      <video
                        src={selectedDispute.videoUnboxingUrl}
                        controls
                        style={{ width: '100%', maxHeight: '180px', display: 'block' }}
                      />
                      <button
                        type="button"
                        onClick={() =>
                          setLightboxMedia({
                            src: selectedDispute.videoUnboxingUrl!,
                            type: 'VIDEO',
                            caption: `Video unboxing của người mua đơn ${selectedDispute.orderCode}`,
                          })
                        }
                        style={{
                          width: '100%',
                          padding: '6px',
                          background: '#1e293b',
                          color: '#fff',
                          border: 'none',
                          fontSize: '0.78rem',
                          cursor: 'pointer',
                        }}
                      >
                        🔍 Phóng to video trong Lightbox
                      </button>
                    </div>
                  ) : (
                    <div style={{ padding: '16px', background: '#f8fafc', borderRadius: '8px', textAlign: 'center', fontSize: '0.82rem', color: '#94a3b8' }}>
                      Chưa có video unboxing
                    </div>
                  )}
                </div>

                {/* Buyer Images */}
                {selectedDispute.buyerEvidenceUrls && selectedDispute.buyerEvidenceUrls.length > 0 && (
                  <div>
                    <div style={{ fontSize: '0.82rem', fontWeight: 600, marginBottom: '6px', color: 'var(--og-color-text-secondary)' }}>
                      🖼️ Ảnh kiện hàng mở ({selectedDispute.buyerEvidenceUrls.length} ảnh):
                    </div>
                    <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                      {selectedDispute.buyerEvidenceUrls.map((url, idx) => (
                        <button
                          key={idx}
                          type="button"
                          onClick={() =>
                            setLightboxMedia({
                              src: url,
                              type: 'IMAGE',
                              caption: `Ảnh bằng chứng người mua #${idx + 1}`,
                            })
                          }
                          style={{
                            padding: 0,
                            border: '1px solid var(--og-color-border)',
                            borderRadius: '6px',
                            overflow: 'hidden',
                            cursor: 'pointer',
                            background: '#000',
                          }}
                        >
                          <img
                            src={url}
                            alt={`Bằng chứng người mua ${idx + 1}`}
                            style={{ width: '56px', height: '56px', objectFit: 'cover', display: 'block' }}
                          />
                        </button>
                      ))}
                    </div>
                  </div>
                )}
              </div>

              {/* Seller Side Evidence */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontWeight: 700, color: 'var(--og-color-primary)' }}>
                  <ConsoleIcons.Shield size={18} />
                  <span>Người bán ({selectedDispute.sellerName})</span>
                </div>
                <div style={{ fontSize: '0.82rem', background: '#fff', padding: '10px', borderRadius: '8px', border: '1px solid var(--og-color-border)', minHeight: '60px' }}>
                  <strong>Phản hồi / Giải trình:</strong>
                  <div style={{ marginTop: '4px', color: selectedDispute.sellerResponse ? 'var(--og-color-text-primary)' : '#94a3b8' }}>
                    {selectedDispute.sellerResponse || 'Người bán chưa gửi phản hồi giải trình.'}
                  </div>
                </div>

                {/* Seller Packaging Photo Gallery */}
                <div>
                  <div style={{ fontSize: '0.82rem', fontWeight: 600, marginBottom: '6px', color: 'var(--og-color-text-secondary)' }}>
                    📦 Bộ ảnh đóng gói kiện hàng trước khi gửi:
                  </div>
                  {selectedDispute.sellerEvidenceUrls && selectedDispute.sellerEvidenceUrls.length > 0 ? (
                    <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                      {selectedDispute.sellerEvidenceUrls.map((url, idx) => (
                        <button
                          key={idx}
                          type="button"
                          onClick={() =>
                            setLightboxMedia({
                              src: url,
                              type: 'IMAGE',
                              caption: `Ảnh đóng gói người bán #${idx + 1} (Đơn ${selectedDispute.orderCode})`,
                            })
                          }
                          style={{
                            padding: 0,
                            border: '1px solid var(--og-color-border)',
                            borderRadius: '6px',
                            overflow: 'hidden',
                            cursor: 'pointer',
                            background: '#000',
                          }}
                        >
                          <img
                            src={url}
                            alt={`Ảnh đóng gói ${idx + 1}`}
                            style={{ width: '64px', height: '64px', objectFit: 'cover', display: 'block' }}
                          />
                        </button>
                      ))}
                    </div>
                  ) : (
                    <div style={{ padding: '24px 16px', background: '#f8fafc', borderRadius: '8px', textAlign: 'center', fontSize: '0.82rem', color: '#94a3b8' }}>
                      Chưa có ảnh đóng gói từ người bán
                    </div>
                  )}
                </div>
              </div>
            </div>

            {/* ARBITRATION VERDICT FORM */}
            <div style={{ borderTop: '1px solid var(--og-color-border)', paddingTop: '16px' }}>
              <h3 style={{ margin: '0 0 12px', fontSize: '1.05rem', color: 'var(--og-color-text-primary)', fontWeight: 700 }}>
                Quyết định phán quyết của Kỹ thuật viên
              </h3>

              {/* Verdict Action */}
              <div style={{ marginBottom: '14px' }}>
                <label style={{ display: 'block', fontSize: '0.84rem', color: 'var(--og-color-text-secondary)', marginBottom: '6px', fontWeight: 600 }}>
                  Phương án xử lý khoản thanh toán và đơn hàng *
                </label>
                <Select
                  hideLabel
                  label="Chọn phương án phân xử"
                  value={verdictAction}
                  onChange={(val) => setVerdictAction(val as DisputeVerdictAction)}
                  options={[
                    {
                      value: 'RETURN_APPROVED',
                      label: 'Chấp thuận trả hàng (UC62/UC75) - Chờ người mua gửi trả hàng về cho người bán',
                    },
                    {
                      value: 'RESOLVED_REFUND',
                      label: 'Hoàn tiền 100% cho Người mua ngay (UC83) - Do lỗi người bán / hàng giả / hỏng nặng',
                    },
                    {
                      value: 'RESOLVED_RELEASE',
                      label: 'Bác khiếu nại & Giải phóng Escrow cho Người bán (UC71) - Người mua khiếu nại sai / bom hàng',
                    },
                  ]}
                />
                <p style={{ margin: '6px 0 0', fontSize: '0.78rem', color: '#64748b' }}>
                  {verdictAction === 'RETURN_APPROVED' &&
                    'ℹ️ Chấp thuận trả hàng chưa hoàn tiền ngay. Tiền hàng sẽ được hoàn sau khi người mua gửi trả hàng thành công và người bán xác nhận hoặc quá 2 ngày không phản hồi. Phí vận chuyển chiều đi không hoàn.'}
                  {verdictAction === 'RESOLVED_REFUND' &&
                    'ℹ️ Hoàn tiền 100% tiền hàng cho người mua, giải phóng ký quỹ và hủy đơn. 100% xu đã dùng sẽ được hoàn trả; voucher không cấp lại.'}
                  {verdictAction === 'RESOLVED_RELEASE' &&
                    'ℹ️ Tiền hàng trong Escrow được giải phóng và chuyển thẳng cho Người bán. Đơn hàng hoàn tất và không thể khiếu nại lại.'}
                </p>
              </div>

              {/* Penalty Points */}
              <div style={{ marginBottom: '14px' }}>
                <label style={{ display: 'block', fontSize: '0.84rem', color: 'var(--og-color-text-secondary)', marginBottom: '6px', fontWeight: 600 }}>
                  Trừ điểm uy tín (Penalty Points) theo UC74 / TASK-0071
                </label>
                <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                  <input
                    type="number"
                    min={0}
                    max={20}
                    value={penaltyPoints}
                    onChange={(e) => setPenaltyPoints(Math.max(0, Math.min(20, parseInt(e.target.value) || 0)))}
                    style={{
                      width: '80px',
                      padding: '7px 10px',
                      borderRadius: '6px',
                      border: '1px solid var(--og-color-border)',
                      fontSize: '0.88rem',
                      fontWeight: 700,
                    }}
                  />
                  <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap' }}>
                    {[
                      { label: '0đ (Không phạt)', val: 0 },
                      { label: '-5đ (Khiếu nại ảo/bom hàng)', val: 5 },
                      { label: '-10đ (Gian dối tình trạng hàng)', val: 10 },
                      { label: '-20đ (Gian lận nghiêm trọng)', val: 20 },
                    ].map((btn) => (
                      <button
                        key={btn.val}
                        type="button"
                        onClick={() => setPenaltyPoints(btn.val)}
                        style={{
                          padding: '5px 10px',
                          borderRadius: '6px',
                          border: '1px solid var(--og-color-border)',
                          background: penaltyPoints === btn.val ? 'var(--og-color-primary-light)' : 'transparent',
                          color: penaltyPoints === btn.val ? 'var(--og-color-primary)' : 'var(--og-color-text-secondary)',
                          fontSize: '0.78rem',
                          fontWeight: 600,
                          cursor: 'pointer',
                        }}
                      >
                        {btn.label}
                      </button>
                    ))}
                  </div>
                </div>
              </div>

              {/* Verdict Notes */}
              <div style={{ marginBottom: '16px' }}>
                <label style={{ display: 'block', fontSize: '0.84rem', color: 'var(--og-color-text-secondary)', marginBottom: '6px', fontWeight: 600 }}>
                  Biên bản giải trình & Lý do phán quyết *
                </label>
                <textarea
                  rows={3}
                  required
                  placeholder="Ghi rõ kết quả đối soát video unboxing người mua vs ảnh đóng gói người bán, căn cứ phán quyết..."
                  value={verdictNotes}
                  onChange={(e) => setVerdictNotes(e.target.value)}
                  style={{
                    width: '100%',
                    padding: '9px 12px',
                    borderRadius: '8px',
                    border: '1px solid var(--og-color-border-strong)',
                    fontSize: '0.88rem',
                    boxSizing: 'border-box',
                    fontFamily: 'inherit',
                  }}
                />
              </div>

              {/* Modal Buttons */}
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px' }}>
                <button
                  type="button"
                  onClick={() => setSelectedDispute(null)}
                  style={{
                    padding: '9px 16px',
                    background: 'var(--og-color-surface-subtle)',
                    border: '1px solid var(--og-color-border-strong)',
                    color: 'var(--og-color-text-secondary)',
                    borderRadius: '8px',
                    fontSize: '0.88rem',
                    fontWeight: 600,
                    cursor: 'pointer',
                  }}
                >
                  Đóng
                </button>
                <button
                  type="button"
                  onClick={handleApplyVerdict}
                  disabled={!verdictNotes.trim() || submitting}
                  style={{
                    padding: '9px 20px',
                    background: 'var(--og-color-primary-fill)',
                    border: 'none',
                    color: '#ffffff',
                    borderRadius: '8px',
                    fontSize: '0.88rem',
                    fontWeight: 600,
                    cursor: !verdictNotes.trim() || submitting ? 'not-allowed' : 'pointer',
                    opacity: !verdictNotes.trim() || submitting ? 0.6 : 1,
                  }}
                >
                  {submitting ? 'Đang ghi nhận...' : 'Xác nhận ban hành phán quyết'}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Media Lightbox Modal for zooming / inspecting evidence */}
      <MediaLightboxModal
        isOpen={!!lightboxMedia}
        src={lightboxMedia?.src || null}
        type={lightboxMedia?.type || 'IMAGE'}
        caption={lightboxMedia?.caption}
        onClose={() => setLightboxMedia(null)}
      />
    </div>
  );
};
