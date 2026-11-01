import { Icon } from '../shared/components/Icon/Icon';
import React, { useState } from 'react';
import { useNavigate, useLocation, Link } from 'react-router-dom';
import type { GoogleRegistration } from '../features/auth/types';
import { RegisterForm } from '../features/auth';

export const RegisterPage: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const googleRegistration = (location.state as { googleRegistration?: GoogleRegistration } | null)?.googleRegistration;
  const [currentStep, setCurrentStep] = useState(1);
  const [role, setRole] = useState<'BUYER' | 'SELLER'>('BUYER');

  const isAddressStep = role === 'SELLER' && currentStep === 2;
  const isBankStep = role === 'SELLER' && currentStep === 3;
  const isOtpStep = (role === 'BUYER' && currentStep === 2) || (role === 'SELLER' && currentStep === 4);

  const getPageTitle = () => {
    if (isOtpStep) return 'Xác thực địa chỉ email';
    if (isBankStep) return 'Thiết lập tài khoản ngân hàng';
    if (isAddressStep) return 'Thiết lập địa chỉ lấy hàng';
    return 'Tạo tài khoản O.G Shop';
  };

  const getPageSubtitle = () => {
    if (isOtpStep) return 'Nhập mã xác thực 6 chữ số để kích hoạt tài khoản của bạn.';
    if (isBankStep) return 'Nhập tài khoản ngân hàng để nhận tiền bán hàng an toàn từ O.G Shop Escrow.';
    if (isAddressStep) return 'Cung cấp địa chỉ lấy hàng để bưu tá (GHN) đến nhận món đồ cần giao.';
    return 'Tham gia cộng đồng mua bán đồ cũ an toàn và minh bạch.';
  };

  return (
    <div className="og-register-page-container">
      {/* CENTERED CARD */}
      <div className="og-register-glass-card">
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
