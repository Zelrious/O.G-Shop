import { useEffect, useRef, useState } from 'react';
import { listingApi } from '../listingApi';
import type { ListingFeeSummary } from '../types';
import { Alert } from '../../../shared/components';
import '../review.css';

interface Props { productId: number; onClose: () => void; onPaid: () => void }
const money = (amount: number) => new Intl.NumberFormat('vi-VN').format(amount) + ' ₫';

export function ListingFeeDialog({ productId, onClose, onPaid }: Props) {
  const [fee, setFee] = useState<ListingFeeSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [refresh, setRefresh] = useState(0);
  const command = useRef<{ chargeId: string; version: number; key: string } | null>(null);
  const inFlight = useRef(false);
  const dialog = useRef<HTMLDivElement>(null);
  useEffect(() => {
    let ignore = false;
    setLoading(true); setError(null);
    listingApi.getListingFee(productId).then(value => { if (!ignore) setFee(value); })
      .catch((e: unknown) => { if (!ignore) setError(e instanceof Error ? e.message : 'Không tải được khoản phí.'); })
      .finally(() => { if (!ignore) setLoading(false); });
    return () => { ignore = true; };
  }, [productId, refresh]);
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    dialog.current?.focus();
    const overflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => { document.body.style.overflow = overflow; previous?.focus(); };
  }, []);
  const pay = async () => {
    if (!fee?.chargeId || inFlight.current) return;
    inFlight.current = true;
    if (command.current?.chargeId !== fee.chargeId || command.current?.version !== fee.version)
      command.current = { chargeId: fee.chargeId, version: fee.version, key: crypto.randomUUID() };
    setBusy(true); setError(null);
    try {
      const result = await listingApi.payListingFee(productId, { chargeId: command.current.chargeId, expectedVersion: command.current.version, commandKey: command.current.key });
      if (result.status !== 'ACTIVE') { setFee(result); setError('Phí đã ghi nhận. Tin hiện chưa ở trạng thái mở bán; hãy tải lại kết quả.'); }
      else onPaid();
    } catch (e: unknown) { setError(e instanceof Error ? e.message : 'Chưa xác nhận được thanh toán. Thử lại với cùng mã.'); }
    finally { inFlight.current = false; setBusy(false); }
  };
  return <div className="og-modal-backdrop" onClick={() => { if (!busy) onClose(); }}>
    <div className="og-fee-dialog" role="dialog" aria-modal="true" aria-labelledby="listing-fee-title" ref={dialog} tabIndex={-1}
      onClick={e => e.stopPropagation()} onKeyDown={e => {
        if (e.key === 'Escape' && !busy) onClose();
        if (e.key === 'Tab') {
          const buttons = dialog.current?.querySelectorAll<HTMLButtonElement>('button:not(:disabled)');
          if (!buttons?.length) { e.preventDefault(); return; }
          const first = buttons[0], last = buttons[buttons.length - 1];
          if (e.shiftKey && (document.activeElement === first || document.activeElement === dialog.current)) { e.preventDefault(); last.focus(); }
          else if (!e.shiftKey && (document.activeElement === last || document.activeElement === dialog.current)) { e.preventDefault(); first.focus(); }
        }
      }}>
      <header><div><span className="og-eyebrow">PHÍ ĐĂNG TIN</span><h2 id="listing-fee-title">Hoàn tất để mở bán</h2></div><button className="og-button og-button--ghost" aria-label="Đóng khoản phí" onClick={onClose} disabled={busy}>×</button></header>
      {loading ? <p aria-busy="true" role="status">Đang tải khoản phí đã được xác định...</p> : fee && <>
        <h3>{fee.title}</h3><span className="og-status-pill">{fee.status === 'APPROVED_AWAITING_FEE' ? 'Đã duyệt, chờ phí' : fee.status === 'ACTIVE' ? 'Đang bán' : 'Trạng thái đã cập nhật'}</span>
        <dl className="og-fee-summary"><div><dt>Tổng giá niêm yết đã duyệt</dt><dd>{fee.listedTotal === null ? 'Chưa xác định' : money(fee.listedTotal)}</dd></div>
          <div><dt>Số lượng được duyệt</dt><dd>{fee.quantity}</dd></div><div><dt>Phí theo biểu tại lượt duyệt</dt><dd>{money(fee.computedFee)}</dd></div>
          <div><dt>Tổng phí đã đóng của tin</dt><dd>{money(fee.totalPaid)}</dd></div><div className="og-fee-summary__total"><dt>Còn phải trả</dt><dd>{money(fee.amountDue)}</dd></div></dl>
        <p>Phí đã đóng được bảo toàn. Chỉ thu phần còn thiếu khi tổng giá niêm yết tăng; giảm giá không hoàn phí cũ.</p>
        <Alert type="info">Thanh toán mô phỏng phục vụ kiểm thử đồ án. Không chuyển tiền thật. Khoản phí chỉ được xác nhận một lần.</Alert>
      </>}
      {error && <div role="alert"><Alert type="danger">{error}</Alert></div>}
      <footer><button className="og-button og-button--outline" onClick={() => setRefresh(n => n + 1)} disabled={busy || loading}>Tải lại khoản phí</button>
        <button className="og-button og-button--primary" disabled={busy || loading || !fee?.chargeId || fee.amountDue <= 0 || fee.status !== 'APPROVED_AWAITING_FEE'} onClick={pay}>{busy ? 'Đang xác nhận...' : 'Xác nhận thanh toán mô phỏng'}</button></footer>
    </div>
  </div>;
}
