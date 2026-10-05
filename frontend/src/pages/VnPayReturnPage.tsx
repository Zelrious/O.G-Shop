import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { paymentApi } from '../features/payment/paymentApi';
import type { PaymentInfo } from '../features/payment/types';

export function VnPayReturnPage() {
  const [params] = useSearchParams();
  const rawOrderId = params.get('orderId');
  const orderId = rawOrderId && /^\d+$/.test(rawOrderId) ? Number(rawOrderId) : NaN;
  const [info, setInfo] = useState<PaymentInfo | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [reload, setReload] = useState(0);
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    let active = true;
    setLoading(true); setError(null);
    if (!Number.isSafeInteger(orderId) || orderId <= 0) {
      setError('Mã đơn hàng không hợp lệ.'); setLoading(false); return;
    }
    paymentApi.getPaymentInfo(orderId).then(result => { if (active) setInfo(result); })
      .catch(err => { if (active) setError(err instanceof Error ? err.message : 'Không thể kiểm tra giao dịch.'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [orderId, reload]);
  const status = info?.paymentStatus;
  const confirmed = ['HELD', 'PAID', 'RELEASED'].includes(status ?? '');
  return <main className="og-container" style={{ padding: 32 }}>
    <h1>Kết quả thanh toán VNPAY</h1>
    {loading ? <p>Đang kiểm tra giao dịch…</p> : error ? <p role="alert">{error}</p>
      : confirmed ? <p>Thanh toán đã được hệ thống xác nhận.</p>
      : status === 'REFUND_PENDING' ? <p>Giao dịch cần đối soát hoàn tiền vì đơn đã hết hạn hoặc bị hủy.</p>
      : status === 'REFUNDED' ? <p>Giao dịch đã được hoàn tiền.</p>
      : status === 'FAILED' ? <p>Giao dịch không thành công. Bạn có thể thử lại nếu đơn còn hạn.</p>
      : <p>Đang chờ xác nhận từ VNPAY. Vui lòng kiểm tra lại trước khi thực hiện thanh toán khác.</p>}
    <button type="button" disabled={loading} onClick={() => setReload(v => v + 1)}>Kiểm tra lại</button>{' '}
    <Link to="/orders">Về đơn mua</Link>
  </main>;
}
