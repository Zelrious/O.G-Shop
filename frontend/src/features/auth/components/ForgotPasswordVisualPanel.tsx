import React from 'react';

interface ForgotPasswordVisualPanelProps {
  step?: 1 | 2 | 3;
}

export const ForgotPasswordVisualPanel: React.FC<ForgotPasswordVisualPanelProps> = ({ step = 1 }) => {
  return (
    <div className="og-register-visual-panel og-forgot-visual-panel" aria-hidden="true">
      <div className="og-visual-state og-visual-state--forgot" style={{ opacity: 1, pointerEvents: 'auto' }}>
        {/* Full-bleed background hero photography */}
        <div className="og-visual-bg-media">
          <img
            src="/auth/buyer-visual.jpg"
            alt="Bảo mật tài khoản O.G Shop"
            className="og-visual-bg-img"
          />
          <div className="og-visual-bg-scrim" />
        </div>

        {/* Top & Bottom Content Container */}
        <div className="og-visual-content">
          <div className="og-visual-top">
            <div className="og-visual-badge">
              <span className="og-visual-badge-dot" />
              <span>Bảo mật tài khoản</span>
            </div>

            <h2 className="og-visual-headline">
              Khôi phục quyền truy cập nhanh chóng.
            </h2>

            <p className="og-visual-sub">
              Bảo vệ tài khoản và giao dịch của bạn với quy trình xác minh OTP nhiều lớp an toàn và minh bạch.
            </p>

            <ul className="og-visual-points">
              <li className="og-visual-point">
                <span className="og-visual-check">✓</span>
                <span>Xác thực OTP tức thì qua email đăng ký chính chủ</span>
              </li>
              <li className="og-visual-point">
                <span className="og-visual-check">✓</span>
                <span>Mã xác thực có thời hạn và tự động hết hiệu lực</span>
              </li>
              <li className="og-visual-point">
                <span className="og-visual-check">✓</span>
                <span>Tự động thu hồi phiên đăng nhập cũ sau khi đổi mật khẩu</span>
              </li>
            </ul>
          </div>

          {/* Frosted Bottom Overlay Card */}
          <div className="og-visual-card">
            <div className="og-visual-card-title">An tâm tuyệt đối tại O.G Shop</div>
            <p className="og-visual-card-text">
              Mọi dữ liệu mua sắm và số dư xu thưởng tích lũy của bạn luôn được bảo vệ an toàn 100%.
            </p>
            <div className="og-visual-indicator" aria-label="Tiến trình khôi phục mật khẩu">
              <span
                className={`og-indicator-bar ${step >= 1 ? 'og-indicator-bar--active' : ''}`}
                style={{ width: step === 1 ? '32px' : '16px' }}
              />
              <span
                className={`og-indicator-bar ${step >= 2 ? 'og-indicator-bar--active' : ''}`}
                style={{ width: step === 2 ? '32px' : '16px' }}
              />
              <span
                className={`og-indicator-bar ${step >= 3 ? 'og-indicator-bar--active' : ''}`}
                style={{ width: step === 3 ? '32px' : '16px' }}
              />
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
