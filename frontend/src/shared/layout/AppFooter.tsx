import React, { useRef, useState } from 'react';
import { Link } from 'react-router-dom';

// 3D Tilt Card Component for the 4 Project Criteria (Requirement 1)
interface TiltFeatureCardProps {
  icon: string;
  title: string;
  desc: string;
}

const TiltFeatureCard: React.FC<TiltFeatureCardProps> = ({ icon, title, desc }) => {
  const cardRef = useRef<HTMLDivElement>(null);
  const [tiltStyle, setTiltStyle] = useState<React.CSSProperties>({});

  const handleMouseMove = (e: React.MouseEvent<HTMLDivElement>) => {
    if (!cardRef.current) return;
    const rect = cardRef.current.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;
    const width = rect.width > 0 ? rect.width : 260;
    const height = rect.height > 0 ? rect.height : 120;
    const centerX = width / 2;
    const centerY = height / 2;

    // Tilt calculations in 3D: rotateX tilts up/down, rotateY tilts left/right
    const rotateX = ((y - centerY) / centerY) * -12;
    const rotateY = ((x - centerX) / centerX) * 12;

    setTiltStyle({
      transform: `perspective(700px) rotateX(${rotateX.toFixed(2)}deg) rotateY(${rotateY.toFixed(2)}deg) translateY(-6px) scale(1.02)`,
      boxShadow: '0 16px 32px rgba(0, 0, 0, 0.45), 0 0 20px rgba(245, 158, 11, 0.18)',
      borderColor: 'rgba(245, 158, 11, 0.45)',
    });
  };

  const handleMouseLeave = () => {
    setTiltStyle({
      transform: 'perspective(700px) rotateX(0deg) rotateY(0deg) translateY(0) scale(1)',
      boxShadow: '',
      borderColor: '',
    });
  };

  return (
    <div
      ref={cardRef}
      className="og-footer__feature-card"
      onMouseMove={handleMouseMove}
      onMouseLeave={handleMouseLeave}
      style={tiltStyle}
    >
      <span className="og-footer__feature-icon" aria-hidden="true">
        {icon}
      </span>
      <div className="og-footer__feature-content">
        <h4 className="og-footer__feature-title">{title}</h4>
        <p className="og-footer__feature-desc">{desc}</p>
      </div>
    </div>
  );
};

// Brand Logos for Payment & Shipping (Requirement 2)
const BrandLogos = {
  VnPay: () => (
    <span className="og-brand-badge" title="VNPay QR">
      <svg width="22" height="22" viewBox="0 0 40 40" fill="none" aria-hidden="true">
        <rect width="40" height="40" rx="8" fill="#005BAA" />
        <path d="M12 10L20 28L28 10H22L20 18L18 10H12Z" fill="#ED1C24" />
        <path d="M20 28L28 10H23L19 21L20 28Z" fill="#FFFFFF" />
      </svg>
      <span className="og-brand-badge__name">VNPay QR</span>
    </span>
  ),
  VietQr: () => (
    <span className="og-brand-badge" title="VietQR">
      <svg width="22" height="22" viewBox="0 0 40 40" fill="none" aria-hidden="true">
        <rect width="40" height="40" rx="8" fill="#0B4892" />
        <circle cx="15" cy="20" r="5" fill="#ED1C24" />
        <circle cx="25" cy="20" r="5" fill="#FFC20E" />
        <path d="M20 15L25 20L20 25" stroke="#FFFFFF" strokeWidth="2.5" strokeLinecap="round" />
      </svg>
      <span className="og-brand-badge__name">VietQR</span>
    </span>
  ),
  Visa: () => (
    <span className="og-brand-badge" title="Visa">
      <svg width="28" height="18" viewBox="0 0 60 20" fill="none" aria-hidden="true">
        <text x="2" y="16" fill="#1A1F71" fontFamily="sans-serif" fontWeight="900" fontSize="18" fontStyle="italic">
          VISA
        </text>
      </svg>
      <span className="og-brand-badge__name">Visa</span>
    </span>
  ),
  Mastercard: () => (
    <span className="og-brand-badge" title="Mastercard">
      <svg width="26" height="18" viewBox="0 0 36 24" fill="none" aria-hidden="true">
        <circle cx="13" cy="12" r="10" fill="#EB001B" />
        <circle cx="23" cy="12" r="10" fill="#F79E1B" fillOpacity="0.88" />
      </svg>
      <span className="og-brand-badge__name">Mastercard</span>
    </span>
  ),
  AtmNapas: () => (
    <span className="og-brand-badge" title="Thẻ ATM / Napas">
      <svg width="22" height="22" viewBox="0 0 40 40" fill="none" aria-hidden="true">
        <rect width="40" height="40" rx="8" fill="#1E3A8A" />
        <rect x="8" y="14" width="24" height="15" rx="3" fill="#3B82F6" stroke="#93C5FD" strokeWidth="1.5" />
        <rect x="8" y="18" width="24" height="4" fill="#1E293B" />
        <circle cx="14" cy="25" r="1.5" fill="#FBBF24" />
      </svg>
      <span className="og-brand-badge__name">Thẻ ATM</span>
    </span>
  ),
  Ghn: () => (
    <span className="og-brand-badge" title="Giao Hàng Nhanh (GHN)">
      <svg width="22" height="22" viewBox="0 0 40 40" fill="none" aria-hidden="true">
        <rect width="40" height="40" rx="8" fill="#FF6600" />
        <path d="M10 22L17 14H30L23 26H10Z" fill="#FFFFFF" />
        <circle cx="16" cy="27" r="3" fill="#1E293B" />
        <circle cx="25" cy="27" r="3" fill="#1E293B" />
      </svg>
      <span className="og-brand-badge__name">Giao Hàng Nhanh</span>
    </span>
  ),
  ViettelPost: () => (
    <span className="og-brand-badge" title="Viettel Post">
      <svg width="22" height="22" viewBox="0 0 40 40" fill="none" aria-hidden="true">
        <rect width="40" height="40" rx="8" fill="#E60000" />
        <path d="M12 25L20 12L28 25L20 21L12 25Z" fill="#FFFFFF" />
        <circle cx="20" cy="18" r="2.5" fill="#FFD700" />
      </svg>
      <span className="og-brand-badge__name">Viettel Post</span>
    </span>
  ),
  Ghtk: () => (
    <span className="og-brand-badge" title="Giao Hàng Tiết Kiệm (GHTK)">
      <svg width="22" height="22" viewBox="0 0 40 40" fill="none" aria-hidden="true">
        <rect width="40" height="40" rx="8" fill="#006B38" />
        <path d="M11 23L16 15H25L21 23H11Z" fill="#FBBF24" />
        <circle cx="15" cy="26" r="3" fill="#FFFFFF" />
        <circle cx="24" cy="26" r="3" fill="#FFFFFF" />
      </svg>
      <span className="og-brand-badge__name">GHTK</span>
    </span>
  ),
};

export const AppFooter: React.FC = () => {
  const currentYear = new Date().getFullYear();

  return (
    <footer className="og-footer" role="contentinfo" aria-label="Chân trang Old but Gold">
      {/* Top 4 Project Criteria: 3D Tilt Cards (Requirement 1) */}
      <div className="og-footer__highlights">
        <div className="og-footer__container">
          <div className="og-footer__highlights-grid">
            <TiltFeatureCard
              icon="🛡️"
              title="Ký Quỹ An Toàn Escrow"
              desc="Sàn giữ tiền 3 ngày, chỉ chuyển cho người bán khi bạn hoàn toàn hài lòng với món đồ."
            />
            <TiltFeatureCard
              icon="🔍"
              title="Kiểm Định Minh Bạch"
              desc="Đội ngũ KTV kiểm duyệt tin đăng, đối chiếu khuyết điểm và hỗ trợ giải quyết tranh chấp."
            />
            <TiltFeatureCard
              icon="💬"
              title="Trả Giá Linh Hoạt"
              desc="Thương lượng giá mua trực tiếp với người bán nhanh chóng, chốt deal tiện lợi."
            />
            <TiltFeatureCard
              icon="⚡"
              title="Thanh Toán Tiện Lợi"
              desc="Hỗ trợ quét mã VNPay, VietQR, ví điện tử và các loại thẻ ngân hàng an toàn tuyệt đối."
            />
          </div>
        </div>
      </div>

      {/* Main Footer Links - Balanced Multi-Column Layout (Requirement 2 & 3) */}
      <div className="og-footer__main">
        <div className="og-footer__container">
          <div className="og-footer__grid">
            {/* Brand Column */}
            <div className="og-footer__col og-footer__col--brand">
              <Link to="/" className="og-footer__brand" aria-label="Logo O.G Shop" style={{ textDecoration: 'none' }}>
                <span className="og-footer__brand-icon" aria-hidden="true">🪙</span>
                <span className="og-footer__brand-name">Old but Gold</span>
                <span className="og-footer__brand-dot" aria-hidden="true" />
              </Link>
              <p className="og-footer__brand-desc">
                Nền tảng mua bán đồ cũ C2C đáng tin cậy. Bảo vệ tối đa quyền lợi người mua và người bán thông qua quy trình ký quỹ và kiểm duyệt độc lập.
              </p>
              <div className="og-footer__contact-info">
                <div className="og-footer__contact-row">
                  <span aria-hidden="true">📞</span>
                  <span>Tổng đài hỗ trợ: <strong>1900 8888</strong> (8:00 - 21:00)</span>
                </div>
                <div className="og-footer__contact-row">
                  <span aria-hidden="true">✉️</span>
                  <span>Email: <strong>hotro@oldbutgold.vn</strong></span>
                </div>
                <div className="og-footer__contact-row">
                  <span aria-hidden="true">📍</span>
                  <span>Địa chỉ: Khu Đô thị ĐHQG, TP. Hồ Chí Minh</span>
                </div>
              </div>
            </div>

            {/* Column: Về Old but Gold */}
            <div className="og-footer__col">
              <h3 className="og-footer__title">Về Old but Gold</h3>
              <ul className="og-footer__links">
                <li><Link to="/about">Giới thiệu về O.G Shop</Link></li>
                <li><Link to="/terms">Quy chế hoạt động sàn</Link></li>
                <li><Link to="/privacy">Chính sách bảo mật</Link></li>
                <li><Link to="/escrow-policy">Cơ chế bảo vệ Ký quỹ Escrow</Link></li>
                <li><Link to="/dispute-policy">Quy trình xử lý tranh chấp</Link></li>
              </ul>
            </div>

            {/* Column: Dành cho Người Mua */}
            <div className="og-footer__col">
              <h3 className="og-footer__title">Dành cho Người Mua</h3>
              <ul className="og-footer__links">
                <li><Link to="/marketplace">Khám phá Chợ Đồ Cũ</Link></li>
                <li><Link to="/cart">Giỏ hàng của bạn</Link></li>
                <li><Link to="/orders">Đơn mua của tôi</Link></li>
                <li><Link to="/buyer-guide">Hướng dẫn mua hàng an toàn</Link></li>
                <li><Link to="/inspect-guide">Kinh nghiệm kiểm tra đồ cũ</Link></li>
              </ul>
            </div>

            {/* Column: Dành cho Người Bán */}
            <div className="og-footer__col">
              <h3 className="og-footer__title">Dành cho Người Bán</h3>
              <ul className="og-footer__links">
                <li><Link to="/seller">Kênh Người Bán (Seller Center)</Link></li>
                <li><Link to="/seller/products/new">Đăng bán món đồ cũ</Link></li>
                <li><Link to="/seller-verification">Xác thực danh tính eKYC</Link></li>
                <li><Link to="/fee-policy">Biểu phí sàn minh bạch</Link></li>
                <li><Link to="/seller-rules">Quy định mô tả khuyết điểm</Link></li>
              </ul>
            </div>

            {/* Column: Thanh toán & Vận chuyển với Logo thương hiệu (Requirement 2) */}
            <div className="og-footer__col og-footer__col--partners">
              <h3 className="og-footer__title">Thanh Toán & Vận Chuyển</h3>
              <p className="og-footer__payment-lead">
                Cổng thanh toán ký quỹ bảo mật:
              </p>
              <div className="og-footer__brand-logos-grid">
                <BrandLogos.VnPay />
                <BrandLogos.VietQr />
                <BrandLogos.Visa />
                <BrandLogos.Mastercard />
                <BrandLogos.AtmNapas />
              </div>

              <div style={{ marginTop: '20px' }}>
                <p className="og-footer__payment-lead">Đơn vị vận chuyển đối tác:</p>
                <div className="og-footer__brand-logos-grid" style={{ marginTop: '8px' }}>
                  <BrandLogos.Ghn />
                  <BrandLogos.ViettelPost />
                  <BrandLogos.Ghtk />
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Bottom Copyright Strip */}
      <div className="og-footer__bottom">
        <div className="og-footer__container">
          <div className="og-footer__bottom-inner">
            <div className="og-footer__copyright">
              © {currentYear} Old but Gold (O.G Shop) - Bản quyền thuộc về Đồ Cũ Chất Lượng Cao.
            </div>
            <div className="og-footer__security-text">
              🔒 Giao dịch ký quỹ O.G Escrow | Giữ tiền 3 ngày an tâm mua sắm
            </div>
          </div>
        </div>
      </div>
    </footer>
  );
};

