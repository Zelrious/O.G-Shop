import React, { useState } from 'react';
import { reviewApi } from './reviewApi';

interface OrderReviewModalProps {
  isOpen: boolean;
  orderId: number;
  partnerName: string;
  isBuyer: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

export const OrderReviewModal: React.FC<OrderReviewModalProps> = ({
  isOpen,
  orderId,
  partnerName,
  isBuyer,
  onClose,
  onSuccess,
}) => {
  const [rating, setRating] = useState<number>(5);
  const [comment, setComment] = useState('');
  const [hoverRating, setHoverRating] = useState<number>(0);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [rewardClaimed, setRewardClaimed] = useState(false);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await reviewApi.createReview(orderId, rating, comment.trim());
      setRewardClaimed(true);
      setTimeout(() => {
        onSuccess();
        onClose();
      }, 1500);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không thể lưu đánh giá.');
      setSubmitting(false);
    }
  };

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        background: 'rgba(15, 23, 42, 0.6)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 1100,
        padding: '16px',
        backdropFilter: 'blur(2px)',
      }}
    >
      <div
        style={{
          background: 'var(--og-color-surface, #fff)',
          borderRadius: '16px',
          border: '1px solid var(--og-color-border, #e2e8f0)',
          padding: '24px',
          width: '100%',
          maxWidth: '480px',
          boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.2)',
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '14px' }}>
          <div>
            <h3 style={{ margin: '0 0 4px', fontSize: '1.25rem', fontWeight: 700, color: 'var(--og-color-text-primary, #0f172a)' }}>
              Đánh giá {isBuyer ? 'Người bán' : 'Người mua'} (UC61)
            </h3>
            <p style={{ margin: 0, fontSize: '0.84rem', color: 'var(--og-color-text-secondary, #64748b)' }}>
              Đơn hàng #{orderId} - Đối tác: <strong>{partnerName}</strong>
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            style={{ background: 'transparent', border: 'none', fontSize: '1.2rem', cursor: 'pointer', color: '#64748b' }}
          >
            ✕
          </button>
        </div>

        {rewardClaimed ? (
          <div
            style={{
              padding: '24px 16px',
              textAlign: 'center',
              background: '#f0fdf4',
              borderRadius: '12px',
              border: '1px solid #bbf7d0',
            }}
          >
            <div style={{ fontSize: '2.5rem', marginBottom: '8px' }}>🎉</div>
            <h4 style={{ margin: '0 0 6px', color: '#166534', fontSize: '1.1rem', fontWeight: 700 }}>
              Gửi đánh giá thành công!
            </h4>
            <p style={{ margin: 0, fontSize: '0.88rem', color: '#15803d' }}>
              Bạn nhận được <strong>+500 xu thưởng</strong> vào tài khoản tích lũy!
            </p>
          </div>
        ) : (
          <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
            {error && (
              <div
                style={{
                  padding: '10px 14px',
                  borderRadius: '8px',
                  background: '#fef2f2',
                  border: '1px solid #fca5a5',
                  color: '#b91c1c',
                  fontSize: '0.84rem',
                }}
              >
                {error}
              </div>
            )}

            {/* Reward Bonus Pill */}
            <div
              style={{
                background: 'linear-gradient(135deg, #fffbeb 0%, #fef3c7 100%)',
                border: '1px solid #fde68a',
                borderRadius: '10px',
                padding: '12px 14px',
                fontSize: '0.82rem',
                color: '#92400e',
                lineHeight: 1.5,
              }}
            >
              <div>🎁 <strong>Thưởng đánh giá:</strong> Nhận ngay <strong>+500 xu</strong> khi hoàn tất đánh giá.</div>
              <div style={{ marginTop: '4px', fontSize: '0.78rem' }}>
                🌟 Đánh giá 3-5 sao giúp đối tác cải thiện điểm uy tín (+3, +4, +5 điểm theo quy tắc phục hồi uy tín).
              </div>
            </div>

            {/* Interactive Stars Selector */}
            <div style={{ textAlign: 'center', padding: '10px 0' }}>
              <div style={{ fontSize: '0.88rem', fontWeight: 600, color: '#334155', marginBottom: '8px' }}>
                Chất lượng giao dịch & Hài lòng:
              </div>
              <div style={{ display: 'flex', justifyContent: 'center', gap: '8px' }}>
                {[1, 2, 3, 4, 5].map((star) => {
                  const active = (hoverRating || rating) >= star;
                  return (
                    <button
                      key={star}
                      type="button"
                      onMouseEnter={() => setHoverRating(star)}
                      onMouseLeave={() => setHoverRating(0)}
                      onClick={() => setRating(star)}
                      style={{
                        background: 'transparent',
                        border: 'none',
                        fontSize: '2rem',
                        cursor: 'pointer',
                        color: active ? '#f59e0b' : '#cbd5e1',
                        transition: 'transform 0.1s ease',
                        transform: active ? 'scale(1.15)' : 'scale(1)',
                        padding: '0 4px',
                      }}
                      aria-label={`${star} sao`}
                    >
                      ★
                    </button>
                  );
                })}
              </div>
              <div style={{ fontSize: '0.84rem', fontWeight: 700, color: '#f59e0b', marginTop: '6px' }}>
                {rating === 5 && '5 sao - Xuất sắc tuyệt vời (+5đ uy tín & +500 xu cho đối tác)'}
                {rating === 4 && '4 sao - Rất tốt (+4đ uy tín & +400 xu cho đối tác)'}
                {rating === 3 && '3 sao - Hài lòng (+3đ uy tín)'}
                {rating === 2 && '2 sao - Cần cải thiện'}
                {rating === 1 && '1 sao - Rất thất vọng'}
              </div>
            </div>

            {/* Comment Area */}
            <div>
              <label style={{ display: 'block', fontSize: '0.84rem', fontWeight: 600, marginBottom: '6px', color: '#334155' }}>
                Chia sẻ nhận xét của bạn
              </label>
              <textarea
                rows={3}
                placeholder="Mô tả sự nhiệt tình, tính trung thực trong mô tả hàng hoặc tốc độ giao hàng..."
                value={comment}
                onChange={(e) => setComment(e.target.value)}
                style={{
                  width: '100%',
                  padding: '9px 12px',
                  borderRadius: '8px',
                  border: '1px solid #cbd5e1',
                  fontSize: '0.88rem',
                  boxSizing: 'border-box',
                  fontFamily: 'inherit',
                }}
              />
            </div>

            {/* Actions */}
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '6px' }}>
              <button
                type="button"
                onClick={onClose}
                style={{
                  padding: '9px 16px',
                  background: '#f1f5f9',
                  border: '1px solid #cbd5e1',
                  borderRadius: '8px',
                  fontSize: '0.88rem',
                  fontWeight: 600,
                  color: '#475569',
                  cursor: 'pointer',
                }}
              >
                Để sau
              </button>
              <button
                type="submit"
                disabled={submitting}
                style={{
                  padding: '9px 20px',
                  background: 'var(--og-color-primary-fill, #059669)',
                  border: 'none',
                  borderRadius: '8px',
                  color: '#fff',
                  fontSize: '0.88rem',
                  fontWeight: 600,
                  cursor: submitting ? 'not-allowed' : 'pointer',
                  opacity: submitting ? 0.7 : 1,
                }}
              >
                {submitting ? 'Đang gửi...' : 'Gửi đánh giá & Nhận 500 xu'}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
};
