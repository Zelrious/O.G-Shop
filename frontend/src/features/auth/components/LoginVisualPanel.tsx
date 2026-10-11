import React from 'react';

export const LoginVisualPanel: React.FC = () => {
  return (
    <div className="og-register-visual-panel og-login-visual-panel" aria-hidden="true">
      <div className="og-visual-state og-visual-state--login" style={{ opacity: 1, pointerEvents: 'auto' }}>
        {/* Full-bleed background hero photography */}
        <div className="og-visual-bg-media">
          <img
            src="/auth/buyer-visual.jpg"
            alt="Không gian đồ cũ độc đáo và ấm áp"
            className="og-visual-bg-img"
          />
          <div className="og-visual-bg-scrim" />
        </div>

        {/* Top & Bottom Content Container */}
        <div className="og-visual-content">
          <div className="og-visual-top">
            <div className="og-visual-badge">
              <span className="og-visual-badge-dot" />
              <span>Chào mừng trở lại</span>
            </div>

            <h2 className="og-visual-headline">
              Tiếp tục hành trình săn đồ độc bản.
            </h2>

            <p className="og-visual-sub">
              Khám phá và trao đổi những món đồ second-hand độc đáo, chất lượng cao trong cộng đồng văn minh, an toàn.
            </p>

            <ul className="og-visual-points">
              <li className="og-visual-point">
                <span className="og-visual-check">✓</span>
                <span>Tích lũy & đổi thưởng O.G Coin sau mỗi đơn hàng</span>
              </li>
              <li className="og-visual-point">
                <span className="og-visual-check">✓</span>
                <span>Quản lý đơn mua và gian hàng cá nhân tiện lợi</span>
              </li>
              <li className="og-visual-point">
                <span className="og-visual-check">✓</span>
                <span>Bảo vệ quyền lợi thanh toán & giao dịch 100%</span>
              </li>
            </ul>
          </div>

          {/* Frosted Bottom Overlay Card */}
          <div className="og-visual-card">
            <div className="og-visual-card-title">Cộng đồng đồ cũ O.G Shop</div>
            <p className="og-visual-card-text">
              Hơn 10.000+ thành viên đang kết nối, tái sử dụng và trao gửi giá trị mới cho đồ cũ mỗi ngày.
            </p>
            <div className="og-visual-indicator" aria-label="Trạng thái đăng nhập">
              <span className="og-indicator-bar og-indicator-bar--active" style={{ width: '28px' }} />
              <span className="og-indicator-bar" />
              <span className="og-indicator-bar" />
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
