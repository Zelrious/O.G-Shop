import { Icon } from '../shared/components/Icon/Icon';
import React, { useState } from 'react';
import { useNavigate, useLocation, Link } from 'react-router-dom';
import type { GoogleRegistration } from '../features/auth/types';
import { RegisterForm, RegisterVisualPanel } from '../features/auth';

export const RegisterPage: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const googleRegistration = (location.state as { googleRegistration?: GoogleRegistration } | null)?.googleRegistration;
  const [currentStep, setCurrentStep] = useState(1);
  const [role, setRole] = useState<'BUYER' | 'SELLER'>('BUYER');

  const isSellerStep = role === 'SELLER' && currentStep === 2;
  const isOtpStep = (role === 'BUYER' && currentStep === 2) || (role === 'SELLER' && currentStep === 3);

  const getPageTitle = () => {
    if (isOtpStep) return 'Xác thực địa chỉ email';
    if (isSellerStep) return 'Thiết lập thông tin người bán';
    return 'Tạo tài khoản O.G Shop';
  };

  const getPageSubtitle = () => {
    if (isOtpStep) return 'Nhập mã xác thực 6 chữ số để kích hoạt tài khoản của bạn.';
    if (isSellerStep) return 'Hoàn thiện thông tin để O.G Shop có thể hỗ trợ giao hàng và thanh toán cho gian hàng của bạn.';
    return 'Tham gia cộng đồng mua bán đồ cũ an toàn và minh bạch.';
  };

  return (
    <div className="og-register-split-container">
      {/* LEFT VISUAL STORYTELLING PANEL */}
      <RegisterVisualPanel accountType={role} step={currentStep} />

      {/* RIGHT FUNCTIONAL FORM PANEL */}
      <div className="og-register-form-panel">
        <div className="og-form-panel-header">
          <div className="og-form-panel-top-row">
            <div className="og-form-panel-title-group">
              <h1 className="og-form-panel-title">{getPageTitle()}</h1>
              <p className="og-form-panel-sub">{getPageSubtitle()}</p>
            </div>

            {/* COMPACT LOGO AT TOP RIGHT */}
            <Link to="/" className="og-form-compact-logo" title="Old but Gold (O.G Shop) — Về trang chủ">
              <span className="og-compact-logo-coin"><Icon name="credit" style={{ width: '1em', height: '1em' }} /></span>
              <span className="og-compact-logo-text">O.G</span>
              <span className="og-compact-logo-dot" />
            </Link>
          </div>
        </div>

        <RegisterForm
          googleRegistration={googleRegistration}
          onStepChange={(step, currentRole) => {
            setCurrentStep(step);
            setRole(currentRole);
          }}
          onSuccess={(result) =>
            navigate(
              result.nextStep === 'SELLER_VERIFICATION' ? '/seller-verification' : '/profile',
              { state: { registrationCoins: result.awardedCoins } }
            )
          }
          onNavigateToLogin={() => navigate('/login')}
        />
      </div>
    </div>
  );
};
