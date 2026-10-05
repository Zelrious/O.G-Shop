import React, { useEffect, useState, useCallback, useRef } from 'react';
import { ApiError, listingApi, ModerationProduct } from '../features/listing';
import { Alert, Badge } from '../shared/components';
import { ConsoleIcons } from '../shared/layout/ConsoleIcons';
import {
  TableActionDropdown,
  BulkActionBar,
  TableCheckbox,
} from '../shared/layout/ConsoleTableActions';
import { formatVnd } from '../shared/utils/currency';

function isConflictError(err: unknown): boolean {
  if (err instanceof ApiError) {
    if (err.status === 409) return true;
    if (
      err.code === 'PRODUCT_VERSION_CONFLICT' ||
      err.code === 'COMMAND_KEY_CONFLICT' ||
      err.code === 'PRODUCT_STATE_CONFLICT' ||
      err.code === 'DATA_CONFLICT'
    ) {
      return true;
    }
  }
  return false;
}

export const ModerationPage: React.FC = () => {
  const [items, setItems] = useState<ModerationProduct[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [actionLoadingId, setActionLoadingId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isConflict, setIsConflict] = useState(false);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [rejectReasonId, setRejectReasonId] = useState<number | null>(null);
  const [rejectReason, setRejectReason] = useState('');

  // Multi-select state
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [isBulkBusy, setIsBulkBusy] = useState(false);
  const bulkBusyRef = useRef(false);
  const inFlightRef = useRef(new Set<number>());
  const queueVersionsRef = useRef(new Map<number, number>());
  const loadSequenceRef = useRef(0);

  // Stable command keys map to guarantee same key reuse during retry
  const commandKeysRef = useRef<Map<string, string>>(new Map());

  const getOrCreateCommandKey = useCallback((prefix: string, productId: number, version: number, payloadSuffix = ''): string => {
    const mapKey = `${prefix}:${productId}:${version}:${payloadSuffix}`;
    const existing = commandKeysRef.current.get(mapKey);
    if (existing) {
      return existing;
    }
    const newKey = `ck-${prefix}-${productId}-${version}-${Math.random().toString(36).substring(2, 10)}`;
    commandKeysRef.current.set(mapKey, newKey);
    return newKey;
  }, []);

  const clearCommandKey = useCallback((prefix: string, productId: number, version: number, payloadSuffix = ''): void => {
    const mapKey = `${prefix}:${productId}:${version}:${payloadSuffix}`;
    commandKeysRef.current.delete(mapKey);
  }, []);

  // Pagination states
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);

  const loadPending = useCallback(async (targetPage = page, targetSize = size) => {
    const sequence = ++loadSequenceRef.current;
    setIsLoading(true);
    setError(null);
    setIsConflict(false);
    try {
      const res = await listingApi.getPendingProducts(targetPage, targetSize);
      if (sequence !== loadSequenceRef.current) return;
      if (targetPage > Math.max(0, (res.totalPages ?? 1) - 1)) {
        setPage(Math.max(0, (res.totalPages ?? 1) - 1));
        return;
      }
      queueVersionsRef.current = new Map((res.items || []).map(item => [item.productId, item.version]));
      setItems(res.items || []);
      setSelectedIds(prev => new Set([...prev].filter(id => queueVersionsRef.current.has(id))));
      setPage(res.page ?? targetPage);
      setSize(res.size ?? targetSize);
      setTotalPages(res.totalPages ?? 1);
      setTotalElements(res.totalElements ?? (res.items?.length || 0));
    } catch (err: unknown) {
      if (sequence !== loadSequenceRef.current) return;
      setError(err instanceof Error ? err.message : 'Không thể tải danh sách tin chờ duyệt.');
    } finally {
      if (sequence === loadSequenceRef.current) setIsLoading(false);
    }
  }, [page, size]);

  useEffect(() => {
    loadPending(page, size);
  }, [page, size, loadPending]);

  const handleApprove = async (item: ModerationProduct) => {
    if (inFlightRef.current.has(item.productId)) return false;
    inFlightRef.current.add(item.productId);
    setActionLoadingId(item.productId);
    setError(null);
    setIsConflict(false);
    setSuccessMessage(null);
    try {
      const commandKey = getOrCreateCommandKey('app', item.productId, item.version);
      const res = await listingApi.approveProduct(item.productId, {
        expectedVersion: item.version,
        commandKey,
      });
      clearCommandKey('app', item.productId, item.version);
      setSuccessMessage(res.message || `Phê duyệt tin #${item.productId} thành công! Tin đã chuyển sang trạng thái ACTIVE.`);
      if (queueVersionsRef.current.get(item.productId) === item.version) {
        queueVersionsRef.current.delete(item.productId);
        setItems((prev) => prev.filter((i) => i.productId !== item.productId));
        setTotalElements((prev) => Math.max(0, prev - 1));
      }
      setSelectedIds((prev) => {
        const next = new Set(prev);
        next.delete(item.productId);
        return next;
      });
      return true;
    } catch (err: unknown) {
      const errMsg = err instanceof Error ? err.message : 'Phê duyệt tin thất bại.';
      setError(errMsg);
      if (isConflictError(err)) {
        setIsConflict(true);
      }
      return false;
    } finally {
      inFlightRef.current.delete(item.productId);
      setActionLoadingId(null);
    }
  };

  const handleRejectSubmit = async (item: ModerationProduct) => {
    if (inFlightRef.current.has(item.productId)) return;
    const trimmed = rejectReason.trim();
    if (!trimmed) {
      setError('Vui lòng nhập lý do từ chối tin đăng (không được để trống hoặc chỉ có khoảng trắng).');
      return;
    }
    if (trimmed.length > 500) {
      setError(`Lý do từ chối không được vượt quá 500 ký tự (hiện tại: ${trimmed.length} ký tự).`);
      return;
    }

    inFlightRef.current.add(item.productId);
    setActionLoadingId(item.productId);
    setError(null);
    setIsConflict(false);
    setSuccessMessage(null);
    try {
      const commandKey = getOrCreateCommandKey('rej', item.productId, item.version, trimmed);
      const res = await listingApi.rejectProduct(item.productId, {
        reason: trimmed,
        expectedVersion: item.version,
        commandKey,
      });
      clearCommandKey('rej', item.productId, item.version, trimmed);
      setSuccessMessage(res.message || `Đã từ chối tin #${item.productId}. Tin đã chuyển sang trạng thái REJECTED.`);
      setRejectReasonId(null);
      setRejectReason('');
      if (queueVersionsRef.current.get(item.productId) === item.version) {
        queueVersionsRef.current.delete(item.productId);
        setItems((prev) => prev.filter((i) => i.productId !== item.productId));
        setTotalElements((prev) => Math.max(0, prev - 1));
      }
      setSelectedIds((prev) => {
        const next = new Set(prev);
        next.delete(item.productId);
        return next;
      });
    } catch (err: unknown) {
      const errMsg = err instanceof Error ? err.message : 'Từ chối tin thất bại.';
      setError(errMsg);
      if (isConflictError(err)) {
        setIsConflict(true);
      }
    } finally {
      inFlightRef.current.delete(item.productId);
      setActionLoadingId(null);
    }
  };

  // Multi-select handlers
  const isAllSelected = items.length > 0 && items.every((i) => selectedIds.has(i.productId));
  const isSomeSelected = items.some((i) => selectedIds.has(i.productId)) && !isAllSelected;

  const handleToggleAll = () => {
    if (isAllSelected) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(items.map((i) => i.productId)));
    }
  };

  const handleToggleRow = (productId: number) => {
    const next = new Set(selectedIds);
    if (next.has(productId)) {
      next.delete(productId);
    } else {
      next.add(productId);
    }
    setSelectedIds(next);
  };

  const handleBulkApprove = async () => {
    if (bulkBusyRef.current || inFlightRef.current.size > 0) return;
    bulkBusyRef.current = true;
    setIsBulkBusy(true);
    const batch = items.filter(item => selectedIds.has(item.productId));
    let approved = 0;
    try {
      for (const item of batch) {
        if (await handleApprove(item)) approved++;
      }
      await loadPending(page, size);
      setSuccessMessage(`Đã duyệt ${approved}/${batch.length} tin; ${batch.length - approved} tin chưa hoàn tất.`);
    } finally {
      bulkBusyRef.current = false;
      setIsBulkBusy(false);
    }
  };

  return (
    <div className="og-moderation-page" style={{ maxWidth: '1080px', margin: '0 auto' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '16px', marginBottom: '24px' }}>
        <div>
          <h1 style={{ fontSize: '1.75rem', fontWeight: 800, margin: '0 0 6px 0', color: '#0f172a' }}>
            Kiểm duyệt tin đăng đồ cũ
          </h1>
          <p style={{ margin: 0, color: '#64748b', fontSize: '0.92rem' }}>
            Kiểm tra tính trung thực, chất lượng hình ảnh và video quay cận cảnh trước khi cấp phép bán công khai trên chợ O.G Shop.
          </p>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          {items.length > 0 && (
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <TableCheckbox
                checked={isAllSelected}
                indeterminate={isSomeSelected}
                onChange={handleToggleAll}
                ariaLabel="Chọn tất cả tin"
              />
              <span style={{ fontSize: '0.84rem', color: '#64748b' }}>Chọn tất cả</span>
            </div>
          )}

          <button
            type="button"
            className="og-button og-button--outline og-button--sm"
            onClick={() => loadPending(page, size)}
            disabled={isLoading || isBulkBusy || actionLoadingId !== null}
            style={{
              whiteSpace: 'nowrap',
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              background: '#ffffff',
              border: '1px solid #cbd5e1',
              color: '#334155',
            }}
          >
            <ConsoleIcons.Refresh size={16} />
            <span>Làm mới danh sách</span>
          </button>
        </div>
      </div>

      {successMessage && (
        <div style={{ marginBottom: '16px' }}>
          <Alert type="success">{successMessage}</Alert>
        </div>
      )}

      {error && (
        <div style={{ marginBottom: '16px' }}>
          <Alert type="danger">
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              <span>{error}</span>
              {isConflict && (
                <div>
                  <button
                    type="button"
                    className="og-button og-button--primary og-button--sm"
                    onClick={() => loadPending(page, size)}
                    style={{ marginTop: '4px' }}
                  >
                    🔄 Tải lại dữ liệu mới nhất (HTTP 409)
                  </button>
                </div>
              )}
            </div>
          </Alert>
        </div>
      )}

      {/* Bulk Action Bar */}
      <BulkActionBar
        selectedCount={selectedIds.size}
        totalCount={items.length}
        onClearSelection={() => setSelectedIds(new Set())}
        actions={[
          {
            label: 'Duyệt nhanh các tin đã chọn',
            icon: <ConsoleIcons.CheckCircle size={15} />,
            variant: 'primary',
            onClick: handleBulkApprove,
            disabled: isBulkBusy || actionLoadingId !== null || isLoading,
          },
        ]}
      />

      {isLoading ? (
        <div className="og-detail-loading" aria-busy="true">
          <div className="og-spinner" />
          <p style={{ color: '#64748b' }}>Đang tải danh sách tin chờ duyệt...</p>
        </div>
      ) : items.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '60px 20px', background: '#ffffff', borderRadius: '12px', border: '1px solid #e2e8f0', boxShadow: '0 1px 3px rgba(0,0,0,0.04)' }}>
          <div style={{ color: '#059669', marginBottom: '16px', display: 'flex', justifyContent: 'center' }}>
            <ConsoleIcons.CheckCircle size={44} />
          </div>
          <h2 style={{ fontSize: '1.25rem', marginBottom: '8px', color: '#0f172a', fontWeight: 700 }}>Không có tin nào đang chờ duyệt</h2>
          <p style={{ margin: 0, color: '#64748b' }}>Tất cả tin đăng đồ cũ đều đã được xử lý xong.</p>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
          {items.map((item) => {
            const images = item.media.filter((m) => m.mediaType === 'IMAGE');
            const videos = item.media.filter((m) => m.mediaType === 'VIDEO');
            const hasRequiredMedia = images.length >= 1 && videos.length >= 1;
            const categoriesList = (item.categories && item.categories.length > 0 ? item.categories : (item.category ? [item.category] : []))
              .map((c) => c.categoryName)
              .join(' · ');
            const isSelected = selectedIds.has(item.productId);

            return (
              <div
                key={item.productId}
                style={{
                  background: isSelected ? '#f0fdf4' : '#ffffff',
                  border: isSelected ? '1px solid #86efac' : '1px solid #e2e8f0',
                  borderRadius: '12px',
                  padding: '20px',
                  boxShadow: '0 1px 3px rgba(0,0,0,0.04)',
                  transition: 'background-color 0.15s ease',
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '12px', marginBottom: '16px' }}>
                  <div style={{ display: 'flex', alignItems: 'flex-start', gap: '12px' }}>
                    <div style={{ paddingTop: '4px' }}>
                      <TableCheckbox
                        checked={isSelected}
                        onChange={() => handleToggleRow(item.productId)}
                        ariaLabel={`Chọn tin #${item.productId}`}
                      />
                    </div>
                    <div>
                      <div style={{ display: 'flex', gap: '8px', alignItems: 'center', flexWrap: 'wrap', marginBottom: '6px' }}>
                        <Badge variant="pending">PENDING</Badge>
                        {item.requiresBuyerEkyc ? (
                          <span style={{ background: '#eff6ff', color: '#2563eb', fontSize: '0.75rem', fontWeight: 600, padding: '2px 8px', borderRadius: '4px', border: '1px solid #bfdbfe' }}>
                            Yêu cầu eKYC Người mua
                          </span>
                        ) : (
                          <span style={{ background: '#f8fafc', color: '#64748b', fontSize: '0.75rem', padding: '2px 8px', borderRadius: '4px', border: '1px solid #e2e8f0' }}>
                            eKYC tùy chọn
                          </span>
                        )}
                        <span
                          style={{ background: '#f1f5f9', color: '#64748b', fontSize: '0.75rem', fontWeight: 600, padding: '2px 8px', borderRadius: '4px', border: '1px solid #cbd5e1' }}
                          title="AI tự động tạm hoãn theo chính sách DP-22"
                        >
                          Chưa kiểm tra AI
                        </span>
                        <span style={{ fontSize: '0.82rem', color: '#64748b' }}>
                          Mã tin: #{item.productId} | Người bán: #{item.sellerId} | Bản: v{item.version}
                        </span>
                      </div>
                      <h2 style={{ fontSize: '1.25rem', margin: '4px 0', color: '#0f172a', fontWeight: 700 }}>{item.title}</h2>
                      {categoriesList && (
                        <div style={{ fontSize: '0.84rem', color: '#d97706', marginTop: '2px', fontWeight: 600 }}>
                          Danh mục: {categoriesList}
                        </div>
                      )}
                    </div>
                  </div>

                  <div style={{ textAlign: 'right', display: 'flex', alignItems: 'center', gap: '12px' }}>
                    <div>
                      <div style={{ fontSize: '1.25rem', fontWeight: 800, color: '#059669', whiteSpace: 'nowrap' }}>
                        {formatVnd(item.listedPrice)}
                      </div>
                      <div style={{ fontSize: '0.82rem', color: '#64748b' }}>
                        Tình trạng: <strong style={{ color: '#0f172a' }}>{item.condition}</strong>
                      </div>
                      {item.location && (
                        <div style={{ fontSize: '0.78rem', color: '#64748b', marginTop: '2px' }}>
                          {item.location}
                        </div>
                      )}
                    </div>

                    <TableActionDropdown
                      ariaLabel={`Tùy chọn cho tin #${item.productId}`}
                      items={[
                        {
                          label: 'Phê duyệt tin (ACTIVE)',
                          icon: <ConsoleIcons.CheckCircle size={16} />,
                          disabled: isBulkBusy || !hasRequiredMedia || actionLoadingId === item.productId,
                          onClick: () => handleApprove(item),
                        },
                        {
                          label: 'Từ chối tin đăng',
                          icon: <ConsoleIcons.XCircle size={16} />,
                          variant: 'danger',
                          disabled: isBulkBusy || actionLoadingId === item.productId,
                          onClick: () => {
                            setRejectReasonId(item.productId);
                            setRejectReason('');
                          },
                        },
                        {
                          label: 'Sao chép mã tin',
                          icon: <ConsoleIcons.FileText size={16} />,
                          onClick: () => {
                            navigator.clipboard?.writeText(String(item.productId));
                            alert(`Đã sao chép mã tin #${item.productId}`);
                          },
                        },
                      ]}
                    />
                  </div>
                </div>

                {/* Description */}
                <div style={{ marginBottom: '16px', background: '#f8fafc', padding: '12px', borderRadius: '8px', border: '1px solid #e2e8f0' }}>
                  <h4 style={{ margin: '0 0 6px 0', fontSize: '0.88rem', color: '#475569', fontWeight: 600 }}>Mô tả sản phẩm:</h4>
                  <p style={{ margin: 0, fontSize: '0.88rem', whiteSpace: 'pre-wrap', color: '#1e293b' }}>
                    {item.description}
                  </p>
                </div>

                {/* Inspection checklist details */}
                <div
                  style={{
                    display: 'grid',
                    gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
                    gap: '12px',
                    marginBottom: '16px',
                    background: '#f8fafc',
                    padding: '12px',
                    borderRadius: '8px',
                    border: '1px solid #e2e8f0',
                  }}
                >
                  <div>
                    <span style={{ fontSize: '0.8rem', color: '#64748b', display: 'block' }}>Thời gian đã dùng:</span>
                    <strong style={{ fontSize: '0.88rem', color: '#0f172a' }}>{item.usageDuration || 'Chưa cung cấp'}</strong>
                  </div>
                  <div>
                    <span style={{ fontSize: '0.8rem', color: '#64748b', display: 'block' }}>Khuyết điểm / trầy xước:</span>
                    <strong style={{ fontSize: '0.88rem', color: '#0f172a' }}>{item.defects || 'Không ghi nhận'}</strong>
                  </div>
                  <div>
                    <span style={{ fontSize: '0.8rem', color: '#64748b', display: 'block' }}>Lịch sử sửa chữa:</span>
                    <strong style={{ fontSize: '0.88rem', color: '#0f172a' }}>{item.repairHistory || 'Chưa từng sửa chữa'}</strong>
                  </div>
                  <div>
                    <span style={{ fontSize: '0.8rem', color: '#64748b', display: 'block' }}>Phụ kiện kèm theo:</span>
                    <strong style={{ fontSize: '0.88rem', color: '#0f172a' }}>{item.includedAccessories || 'Không có phụ kiện'}</strong>
                  </div>
                </div>

                {/* Media Inspector */}
                <div style={{ marginBottom: '20px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '10px' }}>
                    <h4 style={{ margin: 0, fontSize: '0.92rem', color: '#0f172a', fontWeight: 600 }}>
                      Media kiểm định theo Rule V6 ({images.length} ảnh, {videos.length} video):
                    </h4>
                    {!hasRequiredMedia && (
                      <span style={{ color: '#dc2626', fontSize: '0.82rem', fontWeight: 600 }}>
                        {images.length === 0
                          ? '⚠️ Tin này thiếu ảnh mô tả sản phẩm!'
                          : '⚠️ Tin này thiếu video cận cảnh sản phẩm!'}
                      </span>
                    )}
                  </div>

                  <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
                    {item.media.map((m) => (
                      <div
                        key={m.mediaId}
                        style={{
                          width: '110px',
                          height: '90px',
                          borderRadius: '8px',
                          overflow: 'hidden',
                          background: '#f1f5f9',
                          border: '1px solid #cbd5e1',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          position: 'relative',
                        }}
                      >
                        {m.mediaType === 'IMAGE' ? (
                          <img
                            src={m.mediaUrl}
                            alt="Kiểm duyệt"
                            style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                            onError={(e) => {
                              e.currentTarget.style.display = 'none';
                            }}
                          />
                        ) : (
                          <div style={{ textAlign: 'center', padding: '6px' }}>
                            <span style={{ fontSize: '1.2rem', display: 'block' }}>🎬</span>
                            <span style={{ fontSize: '0.72rem', color: '#475569', fontWeight: 600 }}>Video cận cảnh</span>
                          </div>
                        )}
                        <span
                          style={{
                            position: 'absolute',
                            bottom: '3px',
                            right: '3px',
                            fontSize: '0.68rem',
                            fontWeight: 700,
                            padding: '1px 4px',
                            borderRadius: '3px',
                            background: m.mediaType === 'VIDEO' ? '#2563eb' : '#059669',
                            color: '#ffffff',
                          }}
                        >
                          {m.mediaType}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>

                {/* Reject Reason Form if active */}
                {rejectReasonId === item.productId && (
                  <div
                    style={{
                      background: '#fef2f2',
                      border: '1px solid #fecaca',
                      borderRadius: '8px',
                      padding: '16px',
                      marginBottom: '16px',
                    }}
                  >
                    <label
                      htmlFor={`reject-reason-${item.productId}`}
                      style={{ display: 'block', fontSize: '0.84rem', color: '#dc2626', fontWeight: 600, marginBottom: '6px' }}
                    >
                      Lý do từ chối duyệt:
                    </label>
                    <textarea
                      id={`reject-reason-${item.productId}`}
                      rows={3}
                      placeholder="Nêu rõ lý do (ví dụ: Video quay mờ, ảnh chụp sai lệch góc cạnh...)"
                      value={rejectReason}
                      onChange={(e) => setRejectReason(e.target.value)}
                      maxLength={500}
                      style={{
                        marginBottom: '12px',
                        width: '100%',
                        resize: 'vertical',
                        padding: '8px',
                        borderRadius: '6px',
                        border: '1px solid #fca5a5',
                        boxSizing: 'border-box',
                      }}
                    />
                    <div style={{ display: 'flex', gap: '8px' }}>
                      <button
                        type="button"
                        className="og-button og-button--danger og-button--sm"
                        onClick={() => handleRejectSubmit(item)}
                        disabled={isBulkBusy || actionLoadingId === item.productId || !rejectReason.trim()}
                        style={{ padding: '6px 12px', borderRadius: '6px', background: '#dc2626', color: '#ffffff', border: 'none', cursor: 'pointer', fontWeight: 600 }}
                      >
                        {actionLoadingId === item.productId ? 'Đang gửi...' : 'Xác nhận từ chối'}
                      </button>
                      <button
                        type="button"
                        className="og-button og-button--ghost og-button--sm"
                        onClick={() => { setRejectReasonId(null); setRejectReason(''); }}
                        style={{ padding: '6px 12px', borderRadius: '6px', background: '#ffffff', border: '1px solid #cbd5e1', cursor: 'pointer' }}
                      >
                        Hủy
                      </button>
                    </div>
                  </div>
                )}

                {/* Direct Action Buttons */}
                <div style={{ display: 'flex', gap: '10px', justifyContent: 'flex-end', alignItems: 'center' }}>
                  <button
                    type="button"
                    className="og-button og-button--danger"
                    onClick={() => {
                      setRejectReasonId(item.productId);
                      setRejectReason('');
                    }}
                    disabled={isBulkBusy || actionLoadingId === item.productId}
                    style={{
                      padding: '8px 14px',
                      borderRadius: '6px',
                      background: '#fef2f2',
                      color: '#dc2626',
                      border: '1px solid #fecaca',
                      cursor: 'pointer',
                      fontWeight: 600,
                      fontSize: '0.85rem',
                    }}
                  >
                    Từ chối tin
                  </button>
                  <button
                    type="button"
                    className="og-button og-button--primary"
                    onClick={() => handleApprove(item)}
                    disabled={isBulkBusy || actionLoadingId === item.productId || !hasRequiredMedia}
                    style={{
                      background: '#059669',
                      color: '#ffffff',
                      border: 'none',
                      padding: '8px 16px',
                      borderRadius: '6px',
                      fontWeight: 700,
                      fontSize: '0.85rem',
                      cursor: !hasRequiredMedia ? 'not-allowed' : 'pointer',
                      opacity: !hasRequiredMedia ? 0.6 : 1,
                    }}
                    title={!hasRequiredMedia ? 'Không thể duyệt tin khi thiếu ảnh hoặc video cận cảnh' : 'Phê duyệt tin'}
                  >
                    {actionLoadingId === item.productId ? 'Đang duyệt...' : 'Phê duyệt (ACTIVE)'}
                  </button>
                </div>
              </div>
            );
          })}

          {/* Pagination bar (Light Theme) */}
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              flexWrap: 'wrap',
              gap: '12px',
              padding: '16px 20px',
              background: '#ffffff',
              borderRadius: '8px',
              border: '1px solid #e2e8f0',
              marginTop: '12px',
            }}
          >
            <div style={{ fontSize: '0.88rem', color: '#64748b' }}>
              Trang <strong>{page + 1}</strong> / {Math.max(1, totalPages)} (Tổng <strong>{totalElements}</strong> tin chờ duyệt)
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
              <label style={{ fontSize: '0.84rem', color: '#64748b', display: 'flex', alignItems: 'center', gap: '6px' }}>
                Hiển thị:
                <select
                  value={size}
                  disabled={isBulkBusy || actionLoadingId !== null || isLoading}
                  onChange={(e) => setSize(Number(e.target.value))}
                  style={{
                    background: '#ffffff',
                    color: '#0f172a',
                    border: '1px solid #cbd5e1',
                    borderRadius: '4px',
                    padding: '4px 8px',
                    fontSize: '0.84rem',
                  }}
                >
                  <option value={10}>10 tin</option>
                  <option value={20}>20 tin</option>
                  <option value={50}>50 tin</option>
                </select>
              </label>

              <div style={{ display: 'flex', gap: '6px' }}>
                <button
                  type="button"
                  className="og-button og-button--outline og-button--sm"
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  disabled={page === 0 || isLoading || isBulkBusy || actionLoadingId !== null}
                  style={{ padding: '4px 10px', background: '#ffffff', border: '1px solid #cbd5e1', borderRadius: '4px', cursor: 'pointer' }}
                >
                  « Trang trước
                </button>
                <button
                  type="button"
                  className="og-button og-button--outline og-button--sm"
                  onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                  disabled={page >= totalPages - 1 || isLoading || isBulkBusy || actionLoadingId !== null}
                  style={{ padding: '4px 10px', background: '#ffffff', border: '1px solid #cbd5e1', borderRadius: '4px', cursor: 'pointer' }}
                >
                  Trang sau »
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
