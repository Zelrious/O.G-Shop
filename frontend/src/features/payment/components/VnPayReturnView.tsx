import React, { useEffect, useState, useCallback, useRef } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { paymentApi } from '../paymentApi';
import { VnPayVerifyResponse } from '../types';

export const VnPayReturnView: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();

  const [loading, setLoading] = useState(true);
  const [result, setResult] = useState<VnPayVerifyResponse | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [pollCount, setPollCount] = useState<number>(0);
  const [isPolling, setIsPolling] = useState(false);

  const pollTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const performVerification = useCallback(async (currentPoll: number) => {
    try {
      if (currentPoll === 0) {
        setLoading(true);
      } else {
        setIsPolling(true);
      }
      setErrorMsg(null);

      const res = await paymentApi.verifyVnPayReturn(location.search);
      setResult(res);

      if (res.status === 'PENDING_CONFIRMATION' && currentPoll < 5) {
        pollTimerRef.current = setTimeout(() => {
          setPollCount(currentPoll + 1);
          performVerification(currentPoll + 1);
        }, 2000);
      }
    } catch (err: unknown) {
      const error = err as Error;
      setErrorMsg(error.message || 'Không thể xác thực giao dịch với máy chủ.');
    } finally {
      setLoading(false);
      setIsPolling(false);
    }
  }, [location.search]);

  useEffect(() => {
    performVerification(0);

    return () => {
      if (pollTimerRef.current) {
        clearTimeout(pollTimerRef.current);
      }
    };
  }, [performVerification]);

  const handleManualRetry = () => {
    if (pollTimerRef.current) {
      clearTimeout(pollTimerRef.current);
    }
    setPollCount(0);
    performVerification(0);
  };

  if (loading) {
    return (
      <div style={{
        maxWidth: 680,
        margin: '60px auto',
        padding: '40px 24px',
        background: 'var(--og-color-surface)',
        borderRadius: 16,
        border: '1px solid var(--og-color-border)',
        textAlign: 'center',
        boxShadow: '0 4px 20px rgba(0,0,0,0.06)'
      }}>
        <div style={{ fontSize: 48, marginBottom: 16 }}>🔄</div>
        <h2 style={{ fontSize: '1.25rem', color: 'var(--og-color-text-primary)', margin: '0 0 12px' }}>
          Đang xác nhận kết quả thanh toán từ VNPay...
        </h2>
        <p style={{ color: 'var(--og-color-text-secondary)', fontSize: '0.9rem' }}>
          Hệ thống đang kiểm tra chữ ký điện tử và tình trạng ghi nhận từ cổng thanh toán. Vui lòng không đóng trình duyệt.
        </p>
      </div>
    );
  }

  if (errorMsg) {
    return (
      <div style={{
        maxWidth: 680,
        margin: '60px auto',
        padding: '36px 24px',
        background: '#fff',
        borderRadius: 16,
        border: '1px solid #fecaca',
        textAlign: 'center',
        boxShadow: '0 4px 20px rgba(220,38,38,0.06)'
      }}>
        <div style={{ fontSize: 48, marginBottom: 12 }}>⚠️</div>
        <h2 style={{ fontSize: '1.25rem', color: '#991b1b', margin: '0 0 12px' }}>
          Không thể kết nối máy chủ xác minh
        </h2>
        <p style={{ color: '#7f1d1d', fontSize: '0.9rem', marginBottom: 24 }}>
          {errorMsg}
        </p>
        <div style={{ display: 'flex', gap: 12, justifyContent: 'center' }}>
          <button
            type="button"
            className="og-button og-button--primary"
            onClick={handleManualRetry}
          >
            🔄 Thử kiểm tra lại
          </button>
          <button
            type="button"
            className="og-button og-button--secondary"
            onClick={() => navigate('/orders')}
          >
            📦 Về Quản lý đơn mua
          </button>
        </div>
      </div>
    );
  }

  if (!result) return null;

  // Status: SUCCESS
  if (result.status === 'SUCCESS') {
    return (
      <div style={{
        maxWidth: 680,
        margin: '40px auto',
        padding: '40px 32px',
        background: 'var(--og-color-surface)',
        borderRadius: 16,
        border: '1px solid #86efac',
        boxShadow: '0 8px 30px rgba(34,197,94,0.08)',
        textAlign: 'center'
      }}>
        <div style={{
          width: 72,
          height: 72,
          background: 'linear-gradient(135deg, #22c55e 0%, #16a34a 100%)',
          color: '#fff',
          borderRadius: '50%',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          fontSize: 36,
          margin: '0 auto 20px',
          boxShadow: '0 4px 16px rgba(34,197,94,0.3)'
        }}>
          ✓
        </div>

        <h1 style={{ fontSize: '1.5rem', fontWeight: 800, color: '#166534', margin: '0 0 10px' }}>
          Thanh toán Ký quỹ Thành công!
        </h1>

        <p style={{ fontSize: '0.95rem', color: 'var(--og-color-text-secondary)', marginBottom: 24 }}>
          {result.message}
        </p>

        {/* Escrow Badge */}
        <div style={{
          background: '#f0fdf4',
          border: '1px solid #bbf7d0',
          borderRadius: 12,
          padding: '16px 20px',
          marginBottom: 28,
          textAlign: 'left',
          display: 'flex',
          gap: 14,
          alignItems: 'center'
        }}>
          <span style={{ fontSize: 32 }}>🛡️</span>
          <div>
            <div style={{ fontWeight: 700, color: '#166534', fontSize: '0.95rem', marginBottom: 2 }}>
              Cơ chế Bảo vệ Tiền gửi Ký quỹ (Escrow Protection)
            </div>
            <div style={{ fontSize: '0.83rem', color: '#15803d' }}>
              Khoản tiền của bạn đang được đóng băng an toàn tại Old but Gold. Người bán sẽ chỉ nhận được tiền sau khi bạn nhận hàng, đồng kiểm và bấm xác nhận hài lòng.
            </div>
          </div>
        </div>

        {/* Transaction details card */}
        <div style={{
          background: 'var(--og-color-bg-subtle)',
          borderRadius: 10,
          padding: '14px 18px',
          marginBottom: 28,
          fontSize: '0.88rem',
          display: 'flex',
          flexDirection: 'column',
          gap: 8,
          textAlign: 'left'
        }}>
          {result.orderId && (
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <span style={{ color: 'var(--og-color-text-secondary)' }}>Mã đơn hàng:</span>
              <strong>#{result.orderId}</strong>
            </div>
          )}
          {result.txnRef && (
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <span style={{ color: 'var(--og-color-text-secondary)' }}>Mã phiên giao dịch (TxnRef):</span>
              <code style={{ fontSize: '0.85rem' }}>{result.txnRef}</code>
            </div>
          )}
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <span style={{ color: 'var(--og-color-text-secondary)' }}>Phương thức:</span>
            <strong>Cổng thanh toán VNPay Sandbox</strong>
          </div>
        </div>

        <div style={{ display: 'flex', gap: 12, justifyContent: 'center' }}>
          {result.orderId && (
            <button
              type="button"
              className="og-button og-button--primary"
              style={{ padding: '12px 24px', fontWeight: 600 }}
              onClick={() => navigate(`/orders/${result.orderId}`)}
            >
              📦 Xem Chi tiết Đơn hàng
            </button>
          )}
          <button
            type="button"
            className="og-button og-button--secondary"
            style={{ padding: '12px 24px', fontWeight: 600 }}
            onClick={() => navigate('/orders')}
          >
            Danh sách Đơn mua
          </button>
        </div>
      </div>
    );
  }

  // Status: UNDER_REVIEW (Mã 07)
  if (result.status === 'UNDER_REVIEW') {
    return (
      <div style={{
        maxWidth: 680,
        margin: '40px auto',
        padding: '40px 32px',
        background: '#fff',
        borderRadius: 16,
        border: '1px solid #fde68a',
        boxShadow: '0 8px 30px rgba(245,158,11,0.08)',
        textAlign: 'center'
      }}>
        <div style={{
          width: 72,
          height: 72,
          background: 'linear-gradient(135deg, #f59e0b 0%, #d97706 100%)',
          color: '#fff',
          borderRadius: '50%',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          fontSize: 36,
          margin: '0 auto 20px',
          boxShadow: '0 4px 16px rgba(245,158,11,0.3)'
        }}>
          🛡️
        </div>

        <h1 style={{ fontSize: '1.45rem', fontWeight: 800, color: '#92400e', margin: '0 0 10px' }}>
          Giao dịch Đang Được Rà Soát An Toàn (Mã 07)
        </h1>

        <p style={{ fontSize: '0.92rem', color: '#78350f', marginBottom: 24, lineHeight: 1.6 }}>
          Ngân hàng phát hành ghi nhận giao dịch của bạn thuộc diện cần rà soát an toàn bổ sung.
          Đơn hàng của bạn đã được <strong>bảo lưu trạng thái và không bị tự động hủy</strong>.
          Đội ngũ vận hành Old but Gold đang phối hợp cùng cổng VNPay để đối soát trong vòng 48 giờ làm việc.
        </p>

        <div style={{
          background: '#fffbeb',
          border: '1px solid #fde68a',
          borderRadius: 12,
          padding: '16px 20px',
          marginBottom: 28,
          textAlign: 'left',
          fontSize: '0.85rem',
          color: '#b45309'
        }}>
          <ul style={{ margin: 0, paddingLeft: 20 }}>
            <li>Tiền của bạn không bị thất lạc; nếu giao dịch hợp lệ, đơn hàng sẽ lập tức chuyển sang chuẩn bị giao hàng.</li>
            <li>Nếu có sự cố nghi ngờ gian lận, số tiền sẽ được hỗ trợ hoàn lại đầy đủ theo quy định.</li>
          </ul>
        </div>

        <div style={{ display: 'flex', gap: 12, justifyContent: 'center' }}>
          {result.orderId && (
            <button
              type="button"
              className="og-button og-button--primary"
              style={{ padding: '12px 24px', fontWeight: 600 }}
              onClick={() => navigate(`/orders/${result.orderId}`)}
            >
              📦 Theo dõi Đơn hàng #{result.orderId}
            </button>
          )}
          <button
            type="button"
            className="og-button og-button--secondary"
            style={{ padding: '12px 24px', fontWeight: 600 }}
            onClick={() => navigate('/orders')}
          >
            Quản lý Đơn mua
          </button>
        </div>
      </div>
    );
  }

  // Status: PENDING_CONFIRMATION (Return arrived before IPN webhook)
  if (result.status === 'PENDING_CONFIRMATION') {
    return (
      <div style={{
        maxWidth: 680,
        margin: '40px auto',
        padding: '40px 32px',
        background: 'var(--og-color-surface)',
        borderRadius: 16,
        border: '1px solid var(--og-color-border)',
        boxShadow: '0 8px 30px rgba(0,0,0,0.06)',
        textAlign: 'center'
      }}>
        <div style={{ fontSize: 52, marginBottom: 16 }}>⏳</div>

        <h1 style={{ fontSize: '1.35rem', fontWeight: 800, color: 'var(--og-color-text-primary)', margin: '0 0 10px' }}>
          Đang Chờ Xác Nhận Từ Cổng VNPay...
        </h1>

        <p style={{ fontSize: '0.92rem', color: 'var(--og-color-text-secondary)', marginBottom: 20 }}>
          Trình duyệt của bạn đã quay lại cửa hàng, trong khi tín hiệu IPN bảo mật từ VNPay đang được tiếp nhận và xử lý.
        </p>

        {pollCount < 5 ? (
          <div style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: 8,
            background: 'var(--og-color-bg-subtle)',
            padding: '8px 16px',
            borderRadius: 20,
            marginBottom: 24,
            fontSize: '0.85rem',
            color: 'var(--og-color-text-secondary)'
          }}>
            <span>🔄</span>
            <span>Tự động kiểm tra ({pollCount + 1}/5)...</span>
          </div>
        ) : (
          <div style={{
            background: '#fef3c7',
            border: '1px solid #fde68a',
            color: '#92400e',
            borderRadius: 10,
            padding: '12px 16px',
            marginBottom: 24,
            fontSize: '0.88rem'
          }}>
            Quá trình kiểm tra tự động đã tạm dừng. Bạn có thể bấm nút kiểm tra lại bên dưới hoặc quay lại trang Quản lý đơn mua sau vài phút.
          </div>
        )}

        <div style={{ display: 'flex', gap: 12, justifyContent: 'center' }}>
          <button
            type="button"
            disabled={isPolling}
            className="og-button og-button--primary"
            style={{ padding: '12px 24px', fontWeight: 600 }}
            onClick={handleManualRetry}
          >
            {isPolling ? 'Đang kiểm tra...' : '🔄 Kiểm tra Lại Trạng Thái'}
          </button>
          <button
            type="button"
            className="og-button og-button--secondary"
            style={{ padding: '12px 24px', fontWeight: 600 }}
            onClick={() => navigate('/orders')}
          >
            📦 Về Danh sách Đơn mua
          </button>
        </div>
      </div>
    );
  }

  // Failure cases: FAILED, INVALID_SIGNATURE, NOT_FOUND, AMOUNT_MISMATCH
  return (
    <div style={{
      maxWidth: 680,
      margin: '40px auto',
      padding: '40px 32px',
      background: '#fff',
      borderRadius: 16,
      border: '1px solid #fecaca',
      boxShadow: '0 8px 30px rgba(220,38,38,0.06)',
      textAlign: 'center'
    }}>
      <div style={{
        width: 72,
        height: 72,
        background: '#fee2e2',
        color: '#dc2626',
        borderRadius: '50%',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        fontSize: 36,
        margin: '0 auto 20px'
      }}>
        ✕
      </div>

      <h1 style={{ fontSize: '1.45rem', fontWeight: 800, color: '#991b1b', margin: '0 0 10px' }}>
        Thanh toán Chưa Thành công
      </h1>

      <p style={{ fontSize: '0.92rem', color: '#7f1d1d', marginBottom: 24, lineHeight: 1.5 }}>
        {result.message || 'Giao dịch của bạn đã bị hủy hoặc không thể hoàn tất qua cổng thanh toán VNPay.'}
      </p>

      {result.status === 'AMOUNT_MISMATCH' && (
        <div style={{
          background: '#fef2f2',
          border: '1px solid #fca5a5',
          borderRadius: 8,
          padding: '10px 14px',
          marginBottom: 20,
          fontSize: '0.85rem',
          color: '#991b1b'
        }}>
          Phát hiện số tiền hoặc loại tiền tệ không khớp với đơn hàng gốc. Giao dịch đã bị từ chối để bảo vệ an toàn tài khoản.
        </div>
      )}

      <div style={{ display: 'flex', gap: 12, justifyContent: 'center', flexWrap: 'wrap' }}>
        {result.orderId ? (
          <button
            type="button"
            className="og-button og-button--primary"
            style={{ padding: '12px 24px', fontWeight: 600 }}
            onClick={() => navigate(`/checkout/payment?orderId=${result.orderId}`)}
          >
            🔄 Thử Lại Thanh Toán
          </button>
        ) : null}
        <button
          type="button"
          className="og-button og-button--secondary"
          style={{ padding: '12px 24px', fontWeight: 600 }}
          onClick={() => navigate('/marketplace')}
        >
          Tiếp tục Mua sắm
        </button>
      </div>
    </div>
  );
};
