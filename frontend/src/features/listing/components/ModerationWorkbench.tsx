import { useEffect, useRef, useState } from 'react';
import { listingApi } from '../listingApi';
import type { ModerationProduct } from '../types';
import { Alert, EmptyState, PageHeader, Pagination } from '../../../shared/components';
import { BulkActionBar, TableCheckbox } from '../../../shared/layout/ConsoleTableActions';
import { CONDITION_LABELS } from '../../marketplace/types';

interface Props {
  items: ModerationProduct[]; isLoading: boolean; busy: boolean; total: number;
  page: number; size: number; totalPages: number; error: string | null; success: string | null;
  conflict: boolean; selectedIds: Set<number>; rejectId: number | null; reason: string;
  onRefresh: () => void; onPage: (page: number) => void; onSize: (size: number) => void;
  onToggleAll: () => void; onToggle: (id: number) => void; onClear: () => void; onBulk: () => void;
  onApprove: (item: ModerationProduct) => void; onReject: (item: ModerationProduct) => void;
  onBeginReject: (id: number) => void; onReason: (reason: string) => void; onCancelReject: () => void;
}
const money = (price: number) => new Intl.NumberFormat('vi-VN').format(price) + ' ₫';
const statusLabels: Record<string, string> = { PENDING: 'Chờ duyệt', ACTIVE: 'Đang bán', APPROVED_AWAITING_FEE: 'Đã duyệt, chờ phí', REJECTED: 'Bị từ chối', HIDDEN: 'Đã ẩn', SOLD: 'Đã bán', RESERVED: 'Đang giữ hàng' };

export function ModerationWorkbench(p: Props) {
  const [selectedId, setSelectedId] = useState<number | null>(() => {
    const value = Number(new URLSearchParams(window.location.search).get('productId'));
    return value > 0 ? value : null;
  });
  const queueItem = p.items.find(item => item.productId === selectedId) ?? (selectedId === null ? p.items[0] : undefined);
  const targetId = selectedId ?? queueItem?.productId;
  const [detail, setDetail] = useState<ModerationProduct | null>(null);
  const [detailError, setDetailError] = useState<string | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailRefresh, setDetailRefresh] = useState(0);
  const mediaFocus = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!targetId) return;
    let ignore = false;
    setDetail(null); setDetailError(null); setDetailLoading(true);
    listingApi.getModerationDetail(targetId).then(value => {
      if (!ignore) setDetail(value);
    }).catch((error: unknown) => {
      if (!ignore) setDetailError(error instanceof Error ? error.message : 'Không tải được chi tiết tin.');
    }).finally(() => { if (!ignore) setDetailLoading(false); });
    return () => { ignore = true; };
  }, [targetId, queueItem?.version, detailRefresh]);

  const item = detail?.productId === targetId ? detail : queueItem;
  const canDecide = item?.status === 'PENDING' && !p.busy && !detailLoading && !detailError;
  const hasMedia = item?.media.some(m => m.mediaType === 'IMAGE') && item.media.some(m => m.mediaType === 'VIDEO');
  const choose = (id: number) => { setSelectedId(id); p.onCancelReject(); mediaFocus.current?.focus(); };
  return (
    <section className="og-review-workspace" aria-label="Kiểm duyệt tin đăng">
      <PageHeader eyebrow="KÊNH KỸ THUẬT VIÊN" title="Kiểm duyệt tin đăng" description="Đối chiếu nội dung và media. Tin được duyệt chỉ mở bán khi đủ phí và quyền bán." actions={<button className="og-button og-button--outline" onClick={p.onRefresh} disabled={p.isLoading || p.busy}>Làm mới danh sách</button>} />
      {p.success && <div role="status"><Alert type="success">{p.success}</Alert></div>}
      {p.error && <Alert type="danger">{p.error}{p.conflict && <button className="og-button og-button--outline og-button--sm" onClick={() => { p.onRefresh(); setDetailRefresh(n => n + 1); }}>Tải lại dữ liệu mới nhất (HTTP 409)</button>}</Alert>}
      <div className="og-review-columns">
        <aside className="og-review-queue" aria-label="Danh sách tin chờ duyệt">
          <div className="og-review-queue__heading"><strong>Tổng {p.total} tin chờ duyệt</strong>
            <TableCheckbox checked={p.items.length > 0 && p.items.every(i => p.selectedIds.has(i.productId))}
              indeterminate={p.items.some(i => p.selectedIds.has(i.productId)) && !p.items.every(i => p.selectedIds.has(i.productId))}
              onChange={p.onToggleAll} ariaLabel="Chọn tất cả tin" /></div>
          <BulkActionBar selectedCount={p.selectedIds.size} totalCount={p.items.length} onClearSelection={p.onClear}
            actions={[{ label: 'Duyệt nhanh các tin đã chọn', variant: 'primary', onClick: p.onBulk, disabled: p.busy || p.isLoading }]} />
          {p.isLoading ? <p className="og-review-empty" aria-busy="true">Đang tải danh sách tin chờ duyệt...</p> : p.items.length === 0
            ? <EmptyState title="Không có tin nào đang chờ duyệt" description="Bạn đã xử lý tất cả tin hiện tại." actionText="Làm mới" onAction={p.onRefresh} />
            : <ul className="og-review-queue__list">{p.items.map(row => (
              <li key={row.productId} className={targetId === row.productId ? 'is-selected' : ''}>
                <TableCheckbox checked={p.selectedIds.has(row.productId)} onChange={() => p.onToggle(row.productId)} ariaLabel={`Chọn tin #${row.productId}`} />
                <button className="og-review-queue__item" onClick={() => choose(row.productId)} aria-current={targetId === row.productId ? 'true' : undefined}>
                  {row.media.find(m => m.mediaType === 'IMAGE') ? <ReviewImage src={row.media.find(m => m.mediaType === 'IMAGE')!.mediaUrl} alt="" /> : <span className="og-review-thumb">Ảnh</span>}
                  <span><small>#{row.productId} · Chờ duyệt</small><strong>{row.title}</strong><span>{money(row.listedPrice)}</span><small>{row.sellerName || `Người bán #${row.sellerId}`}</small></span>
                </button>
              </li>))}</ul>}
          <div className="og-review-pagination"><Pagination total={p.total} page={p.page} totalPages={p.totalPages} size={p.size} onSize={p.onSize} onPage={p.onPage} disabled={p.busy || p.isLoading} /></div>
        </aside>
        <div className="og-review-detail" ref={mediaFocus} tabIndex={-1} aria-label="Chi tiết tin được chọn" aria-busy={detailLoading}>
          {detailError && <Alert type="danger">{detailError}<button className="og-button og-button--outline og-button--sm" onClick={() => setDetailRefresh(n => n + 1)}>Thử lại chi tiết</button></Alert>}
          {item ? <>
            <header className="og-review-detail__heading"><div><span className="og-status-pill">{statusLabels[item.status] || item.status}</span><span className="og-review-meta">Bản nội dung đang xét · phiên bản {item.version}</span>
              <h2>{item.title}</h2><p>{item.sellerName || `Người bán #${item.sellerId}`} · {item.categories?.map(c => c.categoryName).join(' · ') || item.category?.categoryName}</p></div>
              <div className="og-review-price"><strong>{money(item.listedPrice)}</strong><span>Giá mỗi sản phẩm · số lượng {item.quantity ?? 1}</span></div></header>
            <div className="og-review-inspection">
              <div className="og-review-media"><h3>Ảnh và video cận cảnh</h3>
                <p className="og-review-meta">{item.media.filter(m => m.mediaType === 'IMAGE').length} ảnh · {item.media.filter(m => m.mediaType === 'VIDEO').length} video</p>
                <div className="og-review-gallery">{item.media.filter(m => m.mediaType === 'IMAGE').map(m => <a key={m.mediaId} href={m.mediaUrl} target="_blank" rel="noopener noreferrer" aria-label={`Xem ảnh sản phẩm ${m.displayOrder + 1}`}><ReviewImage key={m.mediaUrl} src={m.mediaUrl} alt={`Ảnh kiểm duyệt ${item.title}`} /></a>)}</div>
                {item.media.filter(m => m.mediaType === 'VIDEO').map(m => <video key={m.mediaId} controls preload="metadata" aria-label="Video quay cận cảnh" src={m.mediaUrl} />)}
                {!hasMedia && <Alert type="warning">Thiếu ảnh hoặc video bắt buộc. Chỉ rõ phần cần bổ sung khi từ chối.</Alert>}
                <h3>Mô tả của Người bán</h3><p className="og-review-description">{item.description}</p>
              </div>
              <div className="og-review-checklist"><h3>Thông tin đối chiếu</h3><dl>
                {([['Tình trạng', CONDITION_LABELS[item.condition] || item.condition], ['Thời gian sử dụng', item.usageDuration], ['Khuyết điểm', item.defects], ['Lịch sử sửa chữa', item.repairHistory], ['Phụ kiện kèm theo', item.includedAccessories], ['Khu vực', item.location]] as const).map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value?.trim() || 'Chưa cung cấp'}</dd></div>)}
              </dl><div className="og-review-note"><strong>Chưa kiểm tra AI</strong><p>KTV đối chiếu thủ công và quyết định trên nội dung hiện tại.</p></div>
                <div className="og-review-note"><strong>{item.requiresBuyerEkyc ? 'Yêu cầu eKYC Người mua' : 'Không yêu cầu eKYC Người mua'}</strong><p>Phí đăng tin được tính sau duyệt; phí đã đóng của cùng tin được bảo toàn.</p></div></div>
            </div>
            <section className="og-review-history"><h3>Lịch sử kiểm duyệt</h3>{detailLoading ? <p role="status">Đang tải lịch sử...</p> : detailError ? <p>Lịch sử chưa tải được.</p> : item.history?.length ? <ol>{item.history.map(entry => <li key={entry.decisionId}><strong>{entry.decision === 'APPROVED' ? 'Đã phê duyệt' : 'Đã từ chối'}</strong><span>Người duyệt #{entry.reviewerId} · {new Date(entry.reviewedAt).toLocaleString('vi-VN')} · bản {entry.contentRevision ?? 'legacy'}</span>{entry.reason && <p>{entry.reason}</p>}</li>)}</ol> : <p>Chưa có quyết định được ghi nhận.</p>}</section>
            {p.rejectId === item.productId && <form className="og-review-rejection" onSubmit={e => { e.preventDefault(); p.onReject(item); }}>
              <label htmlFor="review-reason">Lý do từ chối <span>(bắt buộc)</span></label><textarea id="review-reason" rows={4} maxLength={500} value={p.reason} onChange={e => p.onReason(e.target.value)} placeholder="Chỉ rõ nội dung, ảnh hoặc video cần sửa..." autoFocus />
              <span className="og-review-meta">{p.reason.length}/500 ký tự</span><div><button type="button" className="og-button og-button--outline" onClick={p.onCancelReject} disabled={p.busy}>Hủy</button><button className="og-button og-button--danger" disabled={!canDecide || !p.reason.trim()}>Xác nhận từ chối</button></div>
              {!p.reason.trim() && <p>Vui lòng nhập lý do từ chối tin đăng.</p>}
            </form>}
            <footer className="og-review-actions"><p>Phê duyệt xác nhận nội dung. Hệ thống kiểm tra phí và quyền bán trước khi mở bán.</p><div>
              <button className="og-button og-button--outline" disabled={!canDecide} onClick={() => p.onBeginReject(item.productId)}>Từ chối tin</button>
              <button className="og-button og-button--primary" disabled={!canDecide || !hasMedia} onClick={() => p.onApprove(item)}>Phê duyệt</button></div></footer>
          </> : <div className="og-review-empty"><h2>Chọn một tin để kiểm duyệt</h2><p>Ảnh, video và thông tin đối chiếu sẽ xuất hiện ở đây.</p></div>}
        </div>
      </div>
    </section>
  );
}

function ReviewImage({ src, alt }: { src: string; alt: string }) {
  const [failed, setFailed] = useState(false);
  return failed ? <span className="og-review-image-error" role="img" aria-label={alt || 'Ảnh chưa tải được'}>Ảnh chưa tải được</span>
    : <img src={src} alt={alt} onError={() => setFailed(true)} />;
}
