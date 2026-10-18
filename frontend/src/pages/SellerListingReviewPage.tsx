import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { listingApi, type SellerProductDetail, LISTING_STATUS_LABELS } from '../features/listing';
import { ListingFeeDialog } from '../features/listing/components/ListingFeeDialog';
import { Alert } from '../shared/components';
import '../features/listing/review.css';

export function SellerListingReviewPage() {
  const { productId } = useParams();
  const [product, setProduct] = useState<SellerProductDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [refresh, setRefresh] = useState(0);
  const [payOpen, setPayOpen] = useState(false);
  useEffect(() => {
    let ignore = false;
    setLoading(true); setError(null);
    listingApi.getSellerProduct(productId ?? '').then(value => { if (!ignore) setProduct(value); })
      .catch((e: unknown) => { if (!ignore) setError(e instanceof Error ? e.message : 'Không tải được tin đăng.'); })
      .finally(() => { if (!ignore) setLoading(false); });
    return () => { ignore = true; };
  }, [productId, refresh]);
  return <section className="og-review-workspace"><header className="og-review-heading"><div><span className="og-eyebrow">TIN ĐĂNG CỦA BẠN</span><h1>Kết quả kiểm duyệt</h1></div><Link to="/seller/listings" className="og-button og-button--outline">Về danh sách tin</Link></header>
    {loading && <p role="status" aria-busy="true">Đang tải kết quả...</p>}
    {error && <Alert type="danger">{error}<button className="og-button og-button--outline" onClick={() => setRefresh(n => n + 1)}>Thử lại</button></Alert>}
    {!loading && !error && product && <article className="og-review-detail"><header className="og-review-detail__heading"><div><span className="og-status-pill">{LISTING_STATUS_LABELS[product.status] ?? product.status}</span><h2>{product.title}</h2><p>Tin #{product.productId} · {product.reviewedAt ? `Xét duyệt lúc ${new Date(product.reviewedAt).toLocaleString('vi-VN')}` : 'Chưa có thời điểm xét duyệt'}</p></div></header>
      <div className="og-review-history">
        {product.status === 'APPROVED_AWAITING_FEE' && <><h3>Nội dung đã được phê duyệt</h3><p>Trả phần phí còn thiếu để mở bán. Số tiền theo bản đã duyệt; thao tác thử lại không tạo khoản thu trùng.</p><button className="og-button og-button--primary" onClick={() => setPayOpen(true)}>Xem và trả phí</button></>}
        {product.status === 'REJECTED' && <><Alert type="warning">{product.rejectionReason || 'Chưa có chi tiết lý do từ KTV.'}</Alert><p>Sửa đúng phần được yêu cầu rồi gửi duyệt lại. Phí đã đóng trước đó được bảo toàn.</p><Link className="og-button og-button--primary" to={`/seller/listings/${product.productId}/edit`}>Sửa tin đăng</Link></>}
        {product.status === 'PENDING' && <><h3>Đang chờ KTV đối chiếu</h3><p>Bạn sẽ nhận thông báo khi quyết định được ghi nhận.</p></>}
        {product.status === 'ACTIVE' && <><Alert type="success">Tin hiện đang mở bán trên chợ.</Alert><Link className="og-button og-button--primary" to={`/products/${product.productId}`}>Xem trên chợ</Link></>}
        {!['ACTIVE', 'PENDING', 'REJECTED', 'APPROVED_AWAITING_FEE'].includes(product.status) && <p>Trạng thái hiện tại: {LISTING_STATUS_LABELS[product.status] ?? product.status}. Xem danh sách tin để chọn thao tác tiếp theo.</p>}
      </div></article>}
    {payOpen && product && <ListingFeeDialog productId={product.productId} onClose={() => setPayOpen(false)} onPaid={() => { setPayOpen(false); setRefresh(n => n + 1); }} />}
  </section>;
}
