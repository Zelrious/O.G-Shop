import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { ForgotPasswordForm, ForgotPasswordVisualPanel } from '../features/auth';

export const ForgotPasswordPage: React.FC = () => {
  const navigate = useNavigate();
  const [step, setStep] = useState<1 | 2 | 3>(1);

  const getPageTitle = () => {
    if (step === 2) return 'Xác thực mã OTP';
    if (step === 3) return 'Thiết lập mật khẩu mới';
    return 'Khôi phục mật khẩu O.G Shop';
  };

  const getPageSubtitle = () => {
    if (step === 2) return 'Nhập mã gồm 6 chữ số đã được gửi tới email của bạn.';
    if (step === 3) return 'Tạo mật khẩu mới an toàn cho tài khoản của bạn.';
    return 'Nhập email để nhận mã xác minh OTP đặt lại mật khẩu.';
  };

  return (
    <div className="og-register-split-container og-login-split-container og-forgot-split-container">
      {/* LEFT VISUAL STORYTELLING PANEL */}
      <ForgotPasswordVisualPanel step={step} />

      {/* RIGHT FUNCTIONAL FORM PANEL */}
      <div className="og-register-form-panel og-login-form-panel">
        <div className="og-form-panel-header">
          <div className="og-form-panel-top-row">
            <div className="og-form-panel-title-group">
              <h1 className="og-form-panel-title">{getPageTitle()}</h1>
              <p className="og-form-panel-sub">{getPageSubtitle()}</p>
            </div>

            {/* COMPACT LOGO AT TOP RIGHT */}
            <Link to="/" className="og-form-compact-logo" title="Old but Gold (O.G Shop) — Về trang chủ">
              <span className="og-compact-logo-coin">🪙</span>
              <span className="og-compact-logo-text">O.G</span>
              <span className="og-compact-logo-dot" />
            </Link>
          </div>
        </div>

        <ForgotPasswordForm
          onStepChange={setStep}
          onSuccess={() =>
            navigate('/login', {
              state: { notice: 'Đặt lại mật khẩu thành công. Hãy đăng nhập bằng mật khẩu mới.' },
            })
          }
          onNavigateToLogin={() => navigate('/login')}
        />
      </div>
    </div>
  );
};
