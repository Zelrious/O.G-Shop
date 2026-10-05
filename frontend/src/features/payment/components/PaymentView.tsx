import React, { useState, useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { paymentApi } from '../paymentApi';
import { PaymentInfo, PaymentProcessResult } from '../types';

interface PaymentViewProps {
  orderId: number;
}

export const PaymentView: React.FC<PaymentViewProps> = ({ orderId }) => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const methodParam = searchParams.get('method');

  const [loading, setLoading] = useState(true);
  const [processing, setProcessing] = useState(false);
  const [paymentInfo, setPaymentInfo] = useState<PaymentInfo | null>(null);
  const [selectedMethod, setSelectedMethod] = useState<string>(
    methodParam && ['BANK_TRANSFER_MOCK', 'E_WALLET_MOCK', 'COD_MOCK'].includes(methodParam)
      ? methodParam
      : 'BANK_TRANSFER_MOCK'
  );
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [successResult, setSuccessResult] = useState<PaymentProcessResult | null>(null);
  const [copiedField, setCopiedField] = useState<string | null>(null);

  // Countdown timer: 1 hour = 3600 seconds
  const [secondsRemaining, setSecondsRemaining] = useState<number>(3600);

  useEffect(() => {
    let mounted = true;
    paymentApi.getPaymentInfo(orderId)
      .then((info) => {
        if (!mounted) return;
        setPaymentInfo(info);
        setSecondsRemaining(Math.max(0, info.remainingSeconds));
        if (methodParam && ['BANK_TRANSFER_MOCK', 'E_WALLET_MOCK', 'COD_MOCK'].includes(methodParam)) {
          setSelectedMethod(methodParam);
        } else {
          setSelectedMethod(info.paymentMethod || 'BANK_TRANSFER_MOCK');
        }
        if (info.orderStatus === 'PAID_HELD') {
          setSuccessResult({
            paymentId: info.paymentId || 0,
            orderId: info.orderId,
            orderStatus: info.orderStatus,
            paymentStatus: info.paymentStatus,
            transactionCode: info.transactionCode,
            message: 'Đơn hàng này đã được thanh toán Ký quỹ thành công trước đó.',
          });
        }
      })
      .catch((err) => {
        if (!mounted) return;
        setErrorMsg(err.message || 'Không thể tải thông tin thanh toán.');
      })
      .finally(() => {
        if (mounted) setLoading(false);
      });

    return () => {
      mounted = false;
    };
  }, [orderId, methodParam]);

  useEffect(() => {
    if (secondsRemaining <= 0 || successResult) return;
    const timer = setInterval(() => {
      setSecondsRemaining((prev) => Math.max(0, prev - 1));
    }, 1000);
    return () => clearInterval(timer);
  }, [secondsRemaining, successResult]);

  const handleCopy = (text: string, fieldName: string) => {
    navigator.clipboard.writeText(text);
    setCopiedField(fieldName);
    setTimeout(() => setCopiedField(null), 2000);
  };

  const handleProcessPayment = async (simulateSuccess: boolean) => {
    setProcessing(true);
    setErrorMsg(null);
    try {
      const result = await paymentApi.processMockPayment({
        orderId,
        paymentMethod: selectedMethod,
        simulateSuccess,
      });

      if (simulateSuccess) {
        setSuccessResult(result);
      } else {
        setErrorMsg(result.message || 'Giao dịch thanh toán chưa thành công (Mô phỏng). Đơn hàng vẫn được giữ chỗ trong 1 giờ.');
        // Refresh payment status
        const updated = await paymentApi.getPaymentInfo(orderId);
        setPaymentInfo(updated);
      }
    } catch (err: unknown) {
      const error = err as Error;
      setErrorMsg(error.message || 'Có lỗi xảy ra khi xử lý giao dịch.');
    } finally {
      setProcessing(false);
    }
  };

  const handleVnPay = async () => {
    if (processing) return;
    setProcessing(true);
    setErrorMsg(null);
    try {
      window.location.assign(await paymentApi.createVnPayUrl(orderId));
    } catch (error) {
      setErrorMsg(error instanceof Error ? error.message : 'Không thể mở VNPAY.');
      setProcessing(false);
    }
  };

  const formatVnd = (amount: number) => {
    return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount);
  };

  const formatTimer = (seconds: number) => {
    const hours = Math.floor(seconds / 3600);
    const mins = Math.floor((seconds % 3600) / 60);
    const secs = seconds % 60;
    if (hours > 0) {
      return `${hours.toString().padStart(2, '0')}:${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
    }
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  };

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: '60px 16px' }}>
        <div className="og-spinner" style={{ margin: '0 auto 16px' }} />
        <p style={{ color: 'var(--og-color-text-secondary)', fontSize: '0.95rem' }}>
          Đang tải cổng thanh toán Ký quỹ...
        </p>
      </div>
    );
  }

  if (errorMsg && !paymentInfo) {
    return (
      <div style={{ maxWidth: 600, margin: '40px auto', padding: '0 16px' }}>
        <div style={{
          background: '#fef2f2',
          border: '1px solid #fecaca',
          borderRadius: 12,
          padding: 24,
          textAlign: 'center'
        }}>
          <h2 style={{ color: '#991b1b', margin: '0 0 8px', fontSize: '1.25rem' }}>Không thể mở trang thanh toán</h2>
          <p style={{ color: '#7f1d1d', marginBottom: 20 }}>{errorMsg}</p>
          <button
            type="button"
            className="og-button og-button--primary"
            onClick={() => navigate('/orders')}
          >
            Quay lại danh sách Đơn mua
          </button>
        </div>
      </div>
    );
  }

  // --- Success State Screen ---
  if (successResult) {
    return (
      <div style={{ maxWidth: 640, margin: '40px auto', padding: '0 16px' }}>
        <div style={{
          background: 'var(--og-color-surface)',
          border: '1px solid var(--og-color-border)',
          borderRadius: 16,
          padding: 32,
          boxShadow: '0 10px 25px -5px rgba(0, 0, 0, 0.05)',
          textAlign: 'center',
        }}>
          <div style={{
            width: 72,
            height: 72,
            borderRadius: '50%',
            background: '#dcfce7',
            color: '#16a34a',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: 36,
            margin: '0 auto 16px',
          }}>
            ✓
          </div>

          <h1 style={{ fontSize: '1.5rem', fontWeight: 700, color: 'var(--og-color-text-primary)', margin: '0 0 8px' }}>
            Thanh toán Ký quỹ Thành công!
          </h1>
          <p style={{ color: 'var(--og-color-text-secondary)', fontSize: '0.95rem', margin: '0 0 24px' }}>
            {successResult.message}
          </p>

          <div style={{
            background: 'var(--og-color-bg-subtle)',
            borderRadius: 12,
            padding: 20,
            textAlign: 'left',
            marginBottom: 24,
            border: '1px solid var(--og-color-border)',
          }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 10, fontSize: '0.9rem' }}>
              <span style={{ color: 'var(--og-color-text-secondary)' }}>Mã đơn hàng:</span>
              <strong style={{ color: 'var(--og-color-text-primary)' }}>#{orderId}</strong>
            </div>
            {successResult.transactionCode && (
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 10, fontSize: '0.9rem' }}>
                <span style={{ color: 'var(--og-color-text-secondary)' }}>Mã giao dịch:</span>
                <code style={{ background: '#e0f2fe', color: '#0369a1', padding: '2px 6px', borderRadius: 4, fontWeight: 600 }}>
                  {successResult.transactionCode}
                </code>
              </div>
            )}
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 10, fontSize: '0.9rem' }}>
              <span style={{ color: 'var(--og-color-text-secondary)' }}>Trạng thái đơn:</span>
              <span style={{
                background: '#e0e7ff',
                color: '#3730a3',
                padding: '2px 8px',
                borderRadius: 12,
                fontSize: '0.8rem',
                fontWeight: 600,
              }}>
                ĐÃ GIỮ TIỀN (PAID_HELD)
              </span>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '1rem', borderTop: '1px dashed var(--og-color-border)', paddingTop: 12 }}>
              <span style={{ fontWeight: 600, color: 'var(--og-color-text-primary)' }}>Số tiền đã ký quỹ:</span>
              <strong style={{ color: 'var(--og-color-primary)', fontSize: '1.15rem' }}>
                {formatVnd(paymentInfo?.totalAmount || 0)}
              </strong>
            </div>
          </div>

          <div style={{
            background: '#fffbeb',
            border: '1px solid #fef3c7',
            borderRadius: 8,
            padding: '12px 16px',
            fontSize: '0.85rem',
            color: '#92400e',
            textAlign: 'left',
            marginBottom: 24,
            display: 'flex',
            gap: 10,
          }}>
            <span>🛡️</span>
            <div>
              <strong>Bảo vệ Escrow 100%:</strong> Người bán chỉ nhận được tiền sau khi bạn nhận hàng, đồng kiểm và bấm "Xác nhận nhận hàng" (hoặc quá 3 ngày không khiếu nại).
            </div>
          </div>

          <div style={{ display: 'flex', gap: 12 }}>
            <button
              type="button"
              className="og-button og-button--primary"
              style={{ flex: 1, padding: '12px 16px', fontWeight: 600 }}
              onClick={() => navigate('/orders')}
            >
              📦 Quản lý Đơn Mua
            </button>
            <button
              type="button"
              className="og-button og-button--secondary"
              style={{ flex: 1, padding: '12px 16px', fontWeight: 600 }}
              onClick={() => navigate('/marketplace')}
            >
              Tiếp tục mua sắm
            </button>
          </div>
        </div>
      </div>
    );
  }

  if (!paymentInfo) return null;

  const isExpired = secondsRemaining <= 0;

  return (
    <div style={{ maxWidth: 840, margin: '32px auto', padding: '0 16px 60px' }}>
      {/* Header Escrow Trust Banner */}
      <div style={{
        background: 'linear-gradient(135deg, #fffbeb 0%, #fef3c7 100%)',
        border: '1px solid #fde68a',
        borderRadius: 12,
        padding: '16px 20px',
        marginBottom: 24,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        flexWrap: 'wrap',
        gap: 12,
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <span style={{ fontSize: 28 }}>🪙</span>
          <div>
            <div style={{ fontWeight: 700, color: '#92400e', fontSize: '1rem' }}>
              Cổng Thanh toán Ký quỹ Escrow — Old but Gold
            </div>
            <div style={{ fontSize: '0.82rem', color: '#b45309' }}>
              Tiền của bạn được sàn bảo quản an toàn. Người bán chưa nhận tiền cho đến khi bạn đồng kiểm sản phẩm.
            </div>
          </div>
        </div>

        {/* Countdown badge */}
        <div style={{
          background: isExpired ? '#fee2e2' : '#ffffff',
          border: `1px solid ${isExpired ? '#fca5a5' : '#f59e0b'}`,
          borderRadius: 8,
          padding: '6px 14px',
          display: 'flex',
          alignItems: 'center',
          gap: 6,
        }}>
          <span>⏱️</span>
          <span style={{ fontSize: '0.82rem', color: isExpired ? '#991b1b' : '#78350f', fontWeight: 500 }}>
            {isExpired ? 'Hết hạn giữ đơn:' : 'Thời gian giữ đơn còn:'}
          </span>
          <strong style={{
            fontSize: '1rem',
            color: isExpired ? '#dc2626' : '#b45309',
            fontFamily: 'monospace',
          }}>
            {formatTimer(secondsRemaining)}
          </strong>
        </div>
      </div>

      {errorMsg && (
        <div style={{
          background: '#fef2f2',
          border: '1px solid #fecaca',
          color: '#991b1b',
          padding: '16px 20px',
          borderRadius: 12,
          marginBottom: 20,
          fontSize: '0.9rem',
          display: 'flex',
          flexDirection: 'column',
          gap: 12,
          boxShadow: '0 2px 8px rgba(220, 38, 38, 0.08)',
        }}>
          <div>
            <strong>⚠️ Thông báo thanh toán:</strong> {errorMsg}
          </div>
          <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
            <button
              type="button"
              className="og-button og-button--primary"
              style={{ padding: '6px 14px', fontSize: '0.85rem', cursor: 'pointer' }}
              onClick={() => setErrorMsg(null)}
            >
              🔄 Thử thanh toán lại
            </button>
            <button
              type="button"
              style={{
                padding: '6px 14px',
                fontSize: '0.85rem',
                background: '#fff',
                border: '1px solid #fca5a5',
                borderRadius: 6,
                color: '#991b1b',
                fontWeight: 600,
                cursor: 'pointer',
              }}
              onClick={() => navigate(`/?pendingOrder=${orderId}`)}
            >
              🏠 Thoát về Trang chủ (Đơn lưu Chờ thanh toán)
            </button>
          </div>
        </div>
      )}

      {isExpired && (
        <div style={{
          background: '#fef2f2',
          border: '1px solid #fecaca',
          borderRadius: 12,
          padding: 20,
          marginBottom: 24,
          textAlign: 'center',
        }}>
          <h3 style={{ color: '#991b1b', margin: '0 0 8px' }}>Đã hết hạn 1 giờ thanh toán!</h3>
          <p style={{ color: '#7f1d1d', fontSize: '0.9rem', marginBottom: 16 }}>
            Đơn hàng #{orderId} đã quá hạn giữ chỗ 1 giờ. Món đồ đã được tự động giải phóng để bán lại. Vui lòng đặt hàng lại.
          </p>
          <button
            type="button"
            className="og-button og-button--primary"
            onClick={() => navigate('/marketplace')}
          >
            Quay lại Chợ đồ cũ
          </button>
        </div>
      )}

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 340px', gap: 24, alignItems: 'start' }}>
        {/* Left Column: Payment Method Details */}
        <div style={{
          background: 'var(--og-color-surface)',
          border: '1px solid var(--og-color-border)',
          borderRadius: 12,
          padding: 24,
        }}>
          <h2 style={{ fontSize: '1.15rem', fontWeight: 700, margin: '0 0 16px', color: 'var(--og-color-text-primary)' }}>
            Chọn Phương thức Thanh toán Mô phỏng
          </h2>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 12, marginBottom: 24 }}>
            {/* Option 1: VietQR Bank Transfer */}
            <label
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 12,
                padding: '14px 16px',
                border: `2px solid ${selectedMethod === 'BANK_TRANSFER_MOCK' ? 'var(--og-color-primary)' : 'var(--og-color-border)'}`,
                borderRadius: 10,
                cursor: 'pointer',
                background: selectedMethod === 'BANK_TRANSFER_MOCK' ? 'rgba(226, 91, 41, 0.04)' : 'transparent',
              }}
            >
              <input
                type="radio"
                name="paymentMethod"
                value="BANK_TRANSFER_MOCK"
                checked={selectedMethod === 'BANK_TRANSFER_MOCK'}
                onChange={() => setSelectedMethod('BANK_TRANSFER_MOCK')}
              />
              <span style={{ fontSize: 22 }}>🏦</span>
              <div style={{ flex: 1 }}>
                <div style={{ fontWeight: 600, color: 'var(--og-color-text-primary)', fontSize: '0.95rem' }}>
                  Chuyển khoản Ngân hàng (Mã QR VietQR)
                </div>
                <div style={{ fontSize: '0.8rem', color: 'var(--og-color-text-secondary)' }}>
                  Quét mã QR qua app ngân hàng MB, Vietcombank, Techcombank...
                </div>
              </div>
              <span style={{
                background: '#dbeafe',
                color: '#1e40af',
                fontSize: '0.72rem',
                fontWeight: 700,
                padding: '2px 8px',
                borderRadius: 4,
              }}>
                Khuyên Dùng
              </span>
            </label>

            {/* Option 2: E-Wallet */}
            <label
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 12,
                padding: '14px 16px',
                border: `2px solid ${selectedMethod === 'E_WALLET_MOCK' ? 'var(--og-color-primary)' : 'var(--og-color-border)'}`,
                borderRadius: 10,
                cursor: 'pointer',
                background: selectedMethod === 'E_WALLET_MOCK' ? 'rgba(226, 91, 41, 0.04)' : 'transparent',
              }}
            >
              <input
                type="radio"
                name="paymentMethod"
                value="E_WALLET_MOCK"
                checked={selectedMethod === 'E_WALLET_MOCK'}
                onChange={() => setSelectedMethod('E_WALLET_MOCK')}
              />
              <span style={{ fontSize: 22 }}>📱</span>
              <div style={{ flex: 1 }}>
                <div style={{ fontWeight: 600, color: 'var(--og-color-text-primary)', fontSize: '0.95rem' }}>
                  Ví điện tử (MoMo / ZaloPay / VNPay)
                </div>
                <div style={{ fontSize: '0.8rem', color: 'var(--og-color-text-secondary)' }}>
                  Thanh toán tiện lợi, xác thực qua ứng dụng ví điện tử
                </div>
              </div>
            </label>

            {/* Option 3: COD */}
            <label
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 12,
                padding: '14px 16px',
                border: `2px solid ${selectedMethod === 'COD_MOCK' ? 'var(--og-color-primary)' : 'var(--og-color-border)'}`,
                borderRadius: 10,
                cursor: 'pointer',
                background: selectedMethod === 'COD_MOCK' ? 'rgba(226, 91, 41, 0.04)' : 'transparent',
              }}
            >
              <input
                type="radio"
                name="paymentMethod"
                value="COD_MOCK"
                checked={selectedMethod === 'COD_MOCK'}
                onChange={() => setSelectedMethod('COD_MOCK')}
              />
              <span style={{ fontSize: 22 }}>💵</span>
              <div style={{ flex: 1 }}>
                <div style={{ fontWeight: 600, color: 'var(--og-color-text-primary)', fontSize: '0.95rem' }}>
                  Thanh toán khi nhận hàng (COD Mô phỏng)
                </div>
                <div style={{ fontSize: '0.8rem', color: 'var(--og-color-text-secondary)' }}>
                  Kiểm tra hàng đồng kiểm trước khi thanh toán tiền mặt
                </div>
              </div>
            </label>
          </div>

          {/* Conditional Method Body */}
          {selectedMethod === 'BANK_TRANSFER_MOCK' && (
            <div style={{
              background: 'var(--og-color-bg-subtle)',
              border: '1px solid var(--og-color-border)',
              borderRadius: 12,
              padding: 20,
            }}>
              <div style={{ textAlign: 'center', marginBottom: 20 }}>
                <div style={{
                  display: 'inline-block',
                  background: '#ffffff',
                  padding: 12,
                  borderRadius: 12,
                  boxShadow: '0 4px 12px rgba(0,0,0,0.08)',
                }}>
                  <img
                    src={paymentInfo.qrCodeMockUrl}
                    alt="VietQR Chuyển khoản"
                    style={{ width: 220, height: 220, objectFit: 'contain', display: 'block' }}
                    onError={(e) => {
                      // Fallback if VietQR image service is blocked
                      (e.target as HTMLElement).style.display = 'none';
                    }}
                  />
                  <div style={{ fontSize: '0.8rem', color: '#6b7280', marginTop: 6, fontWeight: 500 }}>
                    Quét mã qua mọi App Ngân hàng
                  </div>
                </div>
              </div>

              <div style={{ display: 'flex', flexDirection: 'column', gap: 10, fontSize: '0.88rem' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ color: 'var(--og-color-text-secondary)' }}>Ngân hàng:</span>
                  <strong style={{ color: 'var(--og-color-text-primary)' }}>{paymentInfo.bankName}</strong>
                </div>

                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ color: 'var(--og-color-text-secondary)' }}>Chủ tài khoản:</span>
                  <strong style={{ color: 'var(--og-color-text-primary)' }}>{paymentInfo.accountName}</strong>
                </div>

                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ color: 'var(--og-color-text-secondary)' }}>Số tài khoản:</span>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <code style={{ fontSize: '1rem', fontWeight: 700, color: 'var(--og-color-primary)' }}>
                      {paymentInfo.accountNumber}
                    </code>
                    <button
                      type="button"
                      style={{ border: 'none', background: '#f3f4f6', cursor: 'pointer', borderRadius: 4, padding: '2px 6px', fontSize: '0.75rem' }}
                      onClick={() => handleCopy(paymentInfo.accountNumber, 'acc')}
                    >
                      {copiedField === 'acc' ? '✓ Đã chép' : 'Sao chép'}
                    </button>
                  </div>
                </div>

                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ color: 'var(--og-color-text-secondary)' }}>Số tiền:</span>
                  <strong style={{ color: '#e25b29', fontSize: '1.05rem' }}>
                    {formatVnd(paymentInfo.totalAmount)}
                  </strong>
                </div>

                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ color: 'var(--og-color-text-secondary)' }}>Nội dung CK:</span>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <code style={{ fontWeight: 700, color: '#111827', background: '#fef08a', padding: '2px 8px', borderRadius: 4 }}>
                      {paymentInfo.transferContent}
                    </code>
                    <button
                      type="button"
                      style={{ border: 'none', background: '#f3f4f6', cursor: 'pointer', borderRadius: 4, padding: '2px 6px', fontSize: '0.75rem' }}
                      onClick={() => handleCopy(paymentInfo.transferContent, 'content')}
                    >
                      {copiedField === 'content' ? '✓ Đã chép' : 'Sao chép'}
                    </button>
                  </div>
                </div>
              </div>
            </div>
          )}

          {selectedMethod === 'E_WALLET_MOCK' && (
            <div style={{
              background: 'var(--og-color-bg-subtle)',
              border: '1px solid var(--og-color-border)',
              borderRadius: 12,
              padding: 20,
              textAlign: 'center',
            }}>
              <div style={{ fontSize: 40, marginBottom: 8 }}>💳</div>
              <h3 style={{ margin: '0 0 6px', fontSize: '1rem' }}>Cổng thanh toán Ví điện tử O.G Escrow</h3>
              <p style={{ color: 'var(--og-color-text-secondary)', fontSize: '0.85rem', margin: '0 0 16px' }}>
                Hệ thống liên kết thanh toán tức thời qua tài khoản ví điện tử.
              </p>
              <div style={{ fontSize: '1.1rem', fontWeight: 700, color: '#e25b29', marginBottom: 12 }}>
                {formatVnd(paymentInfo.totalAmount)}
              </div>
            </div>
          )}

          {selectedMethod === 'COD_MOCK' && (
            <div style={{
              background: 'var(--og-color-bg-subtle)',
              border: '1px solid var(--og-color-border)',
              borderRadius: 12,
              padding: 20,
            }}>
              <h3 style={{ margin: '0 0 6px', fontSize: '1rem', color: '#111827' }}>
                Cam kết nhận hàng & Thanh toán COD
              </h3>
              <p style={{ color: 'var(--og-color-text-secondary)', fontSize: '0.85rem', margin: '0 0 12px' }}>
                Đơn hàng sẽ được ghi nhận và gửi thông báo cho người bán chuẩn bị hàng. Bạn sẽ thanh toán tiền mặt khi nhân viên giao hàng đến và bạn đồng kiểm thành công.
              </p>
            </div>
          )}

          {/* Action Simulation Buttons */}
          <div style={{ marginTop: 24, display: 'flex', flexDirection: 'column', gap: 10 }}>
            <button
              type="button"
              disabled={processing || isExpired || paymentInfo.paymentMethod === 'VNPAY'}
              className="og-button og-button--primary"
              style={{
                padding: '14px 20px',
                fontSize: '1rem',
                fontWeight: 700,
                width: '100%',
                cursor: isExpired ? 'not-allowed' : 'pointer',
              }}
              onClick={() => handleProcessPayment(true)}
            >
              {processing ? 'Đang xác thực giao dịch...' : 'Mô phỏng thanh toán thành công (Thử nghiệm)'}
            </button>

            <button type="button" className="og-button og-button--primary" disabled={processing || isExpired}
              onClick={handleVnPay}>Thanh toán qua VNPAY</button>

            <button
              type="button"
              disabled={processing || isExpired || paymentInfo.paymentMethod === 'VNPAY'}
              style={{
                padding: '10px 16px',
                fontSize: '0.88rem',
                fontWeight: 600,
                width: '100%',
                background: 'transparent',
                border: '1px dashed #fca5a5',
                color: '#dc2626',
                borderRadius: 8,
                cursor: isExpired ? 'not-allowed' : 'pointer',
              }}
              onClick={() => handleProcessPayment(false)}
            >
              ❌ Giả lập lỗi thanh toán (Thử nghiệm)
            </button>
          </div>
        </div>

        {/* Right Column: Order Bill Card */}
        <div style={{
          background: 'var(--og-color-surface)',
          border: '1px solid var(--og-color-border)',
          borderRadius: 12,
          padding: 20,
          boxShadow: '0 4px 16px rgba(0,0,0,0.04)',
          position: 'sticky',
          top: 24,
        }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 14 }}>
            <h3 style={{ fontSize: '1.05rem', fontWeight: 700, margin: 0, color: 'var(--og-color-text-primary)', display: 'flex', alignItems: 'center', gap: 6 }}>
              <span>🧾</span>
              <span>Phiếu Thanh Toán (Bill)</span>
            </h3>
            <span style={{
              background: '#fef3c7',
              color: '#b45309',
              padding: '2px 8px',
              borderRadius: 4,
              fontSize: '0.75rem',
              fontWeight: 700,
            }}>
              #{orderId}
            </span>
          </div>

          {/* Delivery & Seller Info */}
          <div style={{ background: 'var(--og-color-bg-subtle)', borderRadius: 8, padding: 12, marginBottom: 14, fontSize: '0.82rem', lineHeight: 1.5 }}>
            <div style={{ marginBottom: 6 }}>
              <span style={{ color: 'var(--og-color-text-secondary)' }}>📍 Giao đến: </span>
              <strong>{paymentInfo.recipientName || 'Khách hàng'}</strong> {paymentInfo.phoneNumber && `(${paymentInfo.phoneNumber})`}
              <div style={{ color: 'var(--og-color-text-secondary)', marginTop: 2 }}>{paymentInfo.fullAddress || 'Địa chỉ nhận hàng đã chọn'}</div>
            </div>
            <div style={{ borderTop: '1px dashed var(--og-color-border)', paddingTop: 6, marginTop: 6 }}>
              <span style={{ color: 'var(--og-color-text-secondary)' }}>🏪 Người bán: </span>
              <strong>{paymentInfo.sellerId ? `Người bán #${paymentInfo.sellerId}` : 'O.G Shop Verified Seller'}</strong>
            </div>
          </div>

          {/* Product Mini Row */}
          <div style={{ display: 'flex', gap: 12, marginBottom: 16 }}>
            {paymentInfo.productThumbnail ? (
              <img
                src={paymentInfo.productThumbnail}
                alt={paymentInfo.productTitle}
                style={{ width: 60, height: 60, borderRadius: 8, objectFit: 'cover', border: '1px solid var(--og-color-border)' }}
              />
            ) : (
              <div style={{
                width: 60,
                height: 60,
                borderRadius: 8,
                background: '#f3f4f6',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: 22,
              }}>
                📦
              </div>
            )}
            <div style={{ flex: 1 }}>
              <div style={{
                fontSize: '0.86rem',
                fontWeight: 600,
                color: 'var(--og-color-text-primary)',
                display: '-webkit-box',
                WebkitLineClamp: 2,
                WebkitBoxOrient: 'vertical',
                overflow: 'hidden',
                lineHeight: 1.4,
              }}>
                {paymentInfo.productTitle}
              </div>
              <div style={{ fontSize: '0.78rem', color: 'var(--og-color-text-secondary)', marginTop: 4 }}>
                Phương thức: {selectedMethod === 'BANK_TRANSFER_MOCK' ? 'Chuyển khoản QR' : selectedMethod === 'E_WALLET_MOCK' ? 'Ví điện tử' : 'COD Đồng kiểm'}
              </div>
            </div>
          </div>

          {/* Bill Cost Breakdown */}
          <div style={{ borderTop: '1px dashed var(--og-color-border)', paddingTop: 12, marginBottom: 14, fontSize: '0.85rem' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8, color: 'var(--og-color-text-secondary)' }}>
              <span>Tiền hàng:</span>
              <span style={{ color: 'var(--og-color-text-primary)', fontWeight: 500 }}>
                {formatVnd(paymentInfo.subtotal || paymentInfo.totalAmount)}
              </span>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8, color: 'var(--og-color-text-secondary)' }}>
              <span>Phí vận chuyển tiêu chuẩn:</span>
              <span style={{ color: 'var(--og-color-text-primary)', fontWeight: 500 }}>
                {formatVnd(paymentInfo.shippingFee ?? 30000)}
              </span>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8, color: 'var(--og-color-text-secondary)' }}>
              <span>Phí bảo vệ người mua Escrow:</span>
              <span style={{ color: '#059669', fontWeight: 500 }}>0 đ (Miễn phí)</span>
            </div>
            {paymentInfo.voucherDiscount && paymentInfo.voucherDiscount > 0 ? (
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8, color: '#059669' }}>
                <span>Giảm giá Voucher:</span>
                <span style={{ fontWeight: 600 }}>-{formatVnd(paymentInfo.voucherDiscount)}</span>
              </div>
            ) : null}

            <div style={{ borderTop: '1px solid var(--og-color-border)', paddingTop: 10, marginTop: 8 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
                <span style={{ fontWeight: 700, color: 'var(--og-color-text-primary)', fontSize: '0.95rem' }}>Tổng thanh toán:</span>
                <strong style={{ color: '#e25b29', fontSize: '1.25rem' }}>
                  {formatVnd(paymentInfo.totalAmount)}
                </strong>
              </div>
            </div>
          </div>

          {/* Exit / Dismiss Buttons */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 14 }}>
            <button
              type="button"
              style={{
                width: '100%',
                padding: '10px 14px',
                background: '#f9fafb',
                color: '#374151',
                border: '1px solid #d1d5db',
                borderRadius: 8,
                fontWeight: 600,
                fontSize: '0.85rem',
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: 6,
              }}
              onClick={() => navigate(`/?pendingOrder=${orderId}`)}
            >
              <span>🏠</span>
              <span>Thoát về Trang chủ (Đơn lưu Chờ thanh toán)</span>
            </button>
            <button
              type="button"
              style={{
                width: '100%',
                padding: '8px 14px',
                background: 'transparent',
                color: '#6b7280',
                border: 'none',
                fontWeight: 500,
                fontSize: '0.82rem',
                cursor: 'pointer',
              }}
              onClick={() => navigate('/orders')}
            >
              Xem danh sách Đơn mua &rarr;
            </button>
          </div>

          <div style={{
            background: 'var(--og-color-bg-subtle)',
            borderRadius: 8,
            padding: 10,
            fontSize: '0.75rem',
            color: 'var(--og-color-text-secondary)',
            lineHeight: 1.5,
          }}>
            <div>🔒 <strong>Bảo đảm 100% Escrow:</strong></div>
            <div>Món đồ được giữ độc quyền cho bạn trong 1 giờ. Nếu quá hạn chưa thanh toán, đơn sẽ tự hủy và nhả hàng về Chợ.</div>
          </div>
        </div>
      </div>
    </div>
  );
};
