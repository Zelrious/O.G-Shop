import React, { useEffect, useState, useCallback, useRef } from 'react';
import { ApiError, listingApi, ModerationProduct } from '../features/listing';
import { ModerationWorkbench } from '../features/listing/components/ModerationWorkbench';
import '../features/listing/review.css';

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
      setSuccessMessage(res.message || `Phê duyệt tin #${item.productId} thành công! Kết quả kiểm duyệt đã được ghi nhận.`);
      if ((queueVersionsRef.current.get(item.productId) ?? Number.POSITIVE_INFINITY) <= item.version) {
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
      if ((queueVersionsRef.current.get(item.productId) ?? Number.POSITIVE_INFINITY) <= item.version) {
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

  const handleToggleAll = () => {
    if (items.length > 0 && items.every(i => selectedIds.has(i.productId))) {
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

  return <ModerationWorkbench
    items={items} isLoading={isLoading} busy={isBulkBusy || actionLoadingId !== null}
    total={totalElements} page={page} size={size} totalPages={totalPages}
    error={error} success={successMessage} conflict={isConflict} selectedIds={selectedIds}
    rejectId={rejectReasonId} reason={rejectReason}
    onRefresh={() => loadPending(page, size)} onPage={setPage}
    onSize={value => { setSize(value); setPage(0); }}
    onToggleAll={handleToggleAll} onToggle={handleToggleRow} onClear={() => setSelectedIds(new Set())}
    onBulk={handleBulkApprove} onApprove={handleApprove} onReject={handleRejectSubmit}
    onBeginReject={id => { setRejectReasonId(id); setRejectReason(''); setError(null); }}
    onReason={setRejectReason} onCancelReject={() => { setRejectReasonId(null); setRejectReason(''); }}
  />;
};
