import React from 'react';

interface RegisterVisualPanelProps {
  accountType: 'BUYER' | 'SELLER';
  step: number;
}

export const RegisterVisualPanel: React.FC<RegisterVisualPanelProps> = ({ accountType, step }) => {
  // Contextual step message for Seller onboarding
  const getSellerStepContext = () => {
    if (step === 2) {
      return 'Thiết lập địa chỉ lấy hàng và tài khoản ngân hàng để sẵn sàng nhận tiền bán hàng.';
    }
    if (step === 3) {
      return 'Hoàn tất xác thực email để kích hoạt quyền truy cập gian hàng và bảo vệ tài khoản.';
    }
    return 'Khám phá giải pháp thanh lý và mở gian hàng second-hand an toàn cùng O.G Shop.';
  };

  return (
    <div className="og-register-visual-panel" data-role={accountType} aria-hidden="true">
      {/* 1. BUYER VISUAL STATE */}
      <div className="og-visual-state og-visual-state--buyer">
        {/* Full-bleed background hero photography */}
        <div className="og-visual-bg-media">
          <img
            src="/auth/buyer-visual.jpg"
            alt="Món đồ second-hand độc đáo tuyển chọn"
            className="og-visual-bg-img"
          />
          <div className="og-visual-bg-scrim" />
        </div>

        {/* Top & Bottom Content Container */}
        <div className="og-visual-content">
          <div className="og-visual-top">
            <div className="og-visual-badge">
              <span className="og-visual-badge-dot" />
              <span>Mua sắm bền vững</span>
            </div>

            <h2 className="og-visual-headline">
              Tìm lại giá trị trong những món đồ cũ.
            </h2>

            <p className="og-visual-sub">
              Khám phá hàng ngàn sản phẩm độc đáo, đã qua kiểm định chất lượng, phù hợp với phong cách và ngân sách của bạn.
            </p>

            <ul className="og-visual-points">
              <li className="og-visual-point">
                <span className="og-visual-check">✓</span>
                <span>Nhận ngay 3.000 xu khi hoàn tất xác thực tài khoản</span>
              </li>
              <li className="og-visual-point">
                <span className="og-visual-check">✓</span>
                <span>Sản phẩm đa dạng & kiểm định minh bạch</span>
              </li>
              <li className="og-visual-point">
                <span className="og-visual-check">✓</span>
                <span>Bảo vệ quyền lợi người mua với chính sách đổi trả</span>
              </li>
              <li className="og-visual-point">
                <span className="og-visual-check">✓</span>
                <span>Đánh giá thực tế từ cộng đồng người mua</span>
              </li>
            </ul>
          </div>

          {/* Frosted Bottom Overlay Card */}
          <div className="og-visual-card">
            <div className="og-visual-card-title">Mua sắm thông minh cùng O.G Shop</div>
            <p className="og-visual-card-text">
              Hơn 10.000+ món đồ second-hand chất lượng cao đang sẵn sàng chuyển tới bạn.
            </p>
            <div className="og-visual-indicator" aria-label="Trạng thái chọn người mua">
              <span className="og-indicator-bar og-indicator-bar--active" />
              <span className="og-indicator-bar" />
            </div>
          </div>
        </div>
      </div>

      {/* 2. SELLER VISUAL STATE */}
      <div className="og-visual-state og-visual-state--seller">
        {/* Full-bleed background hero photography */}
        <div className="og-visual-bg-media">
          <img
            src="/auth/seller-visual.jpg"
            alt="Không gian đóng gói và kinh doanh của người bán"
            className="og-visual-bg-img"
          />
          <div className="og-visual-bg-scrim" />
        </div>

        {/* Top & Bottom Content Container */}
        <div className="og-visual-content">
          <div className="og-visual-top">
            <div className="og-visual-badge og-visual-badge--gold">
              <span className="og-visual-badge-dot og-visual-badge-dot--gold" />
              <span>Gian hàng cá nhân</span>
            </div>

            <h2 className="og-visual-headline">
              Biến món đồ cũ thành giá trị mới.
            </h2>

            <p className="og-visual-sub">
              Tạo gian hàng, đăng sản phẩm nhanh chóng và tiếp cận cộng đồng người mua văn minh, trân trọng đồ cũ.
            </p>

            <ul className="og-visual-points">
              <li className="og-visual-point">
                <span className="og-visual-check">✓</span>
                <span>Nhận ngay 3.000 xu khi hoàn tất xác thực tài khoản</span>
              </li>
              <li className="og-visual-point">
                <span className="og-visual-check">✓</span>
                <span>Tiếp cận hàng ngàn người mua tiềm năng mỗi ngày</span>
              </li>
              <li className="og-visual-point">
                <span className="og-visual-check">✓</span>
                <span>Đơn vị vận chuyển hỗ trợ lấy hàng tận nơi</span>
              </li>
              <li className="og-visual-point">
                <span className="og-visual-check">✓</span>
                <span>Thanh toán an toàn, đối soát rõ ràng và minh bạch</span>
              </li>
            </ul>
          </div>

          {/* Frosted Bottom Overlay Card */}
          <div className="og-visual-card">
            <div className="og-visual-card-title">Kinh doanh đồ cũ bền vững</div>
            <p className="og-visual-card-text">
              {getSellerStepContext()}
            </p>
            <div className="og-visual-indicator" aria-label="Trạng thái chọn người bán">
              <span className="og-indicator-bar" />
              <span className="og-indicator-bar og-indicator-bar--active" />
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
