import React from 'react';

interface ForgotPasswordStepIndicatorProps {
  currentStep: 1 | 2 | 3;
  onStepClick?: (step: 1 | 2 | 3) => void;
}

export const ForgotPasswordStepIndicator: React.FC<ForgotPasswordStepIndicatorProps> = ({
  currentStep,
  onStepClick,
}) => {
  const step1Completed = currentStep > 1;
  const step1Active = currentStep === 1;

  const step2Completed = currentStep > 2;
  const step2Active = currentStep === 2;

  const step3Active = currentStep === 3;

  return (
    <nav className="og-fluid-stepper" aria-label="Tiến trình khôi phục mật khẩu">
      <div className="og-fluid-stepper-track og-stepper--forgot">
        {/* STEP 1: Email */}
        <div
          className={`og-step-node og-step-node--first ${step1Active ? 'og-step-node--active' : ''} ${
            step1Completed ? 'og-step-node--completed og-step-node--clickable' : ''
          }`}
          aria-current={step1Active ? 'step' : undefined}
        >
          <button
            type="button"
            className="og-step-node-btn"
            onClick={() => {
              if (step1Completed) onStepClick?.(1);
            }}
            disabled={!step1Completed}
            aria-label={`Bước 1: Nhập email ${
              step1Completed ? '(Đã hoàn thành, bấm để đổi)' : step1Active ? '(Đang thực hiện)' : ''
            }`}
          >
            <span className="og-step-circle">{step1Completed ? '✓' : '1'}</span>
            <span className="og-step-title">Email</span>
          </button>
        </div>

        {/* CONNECTOR 1: Nối từ Bước 1 sang Bước 2 */}
        <div
          className={`og-step-line og-step-line--1 ${step1Completed ? 'og-step-line--completed' : ''}`}
          aria-hidden="true"
        >
          <div className="og-step-line-fill" />
        </div>

        {/* STEP 2: Mã OTP */}
        <div
          className={`og-step-node ${step2Active ? 'og-step-node--active' : ''} ${
            step2Completed ? 'og-step-node--completed' : ''
          }`}
          aria-current={step2Active ? 'step' : undefined}
        >
          <div className="og-step-node-btn" style={{ cursor: 'default' }}>
            <span className="og-step-circle">
              <span key="forgot-step-2" className="og-step-digit-anim">
                {step2Completed ? '✓' : '2'}
              </span>
            </span>
            <span className="og-step-title">Mã OTP</span>
          </div>
        </div>

        {/* CONNECTOR 2: Nối từ Bước 2 sang Bước 3 */}
        <div
          className={`og-step-line og-step-line--2 ${step2Completed ? 'og-step-line--completed' : ''}`}
          aria-hidden="true"
        >
          <div className="og-step-line-fill" />
        </div>

        {/* STEP 3: Mật khẩu mới */}
        <div
          className={`og-step-node og-step-node--final ${step3Active ? 'og-step-node--active' : ''}`}
          aria-current={step3Active ? 'step' : undefined}
        >
          <div className="og-step-node-btn" style={{ cursor: 'default' }}>
            <span className="og-step-circle">
              <span key="forgot-step-3" className="og-step-digit-anim">
                3
              </span>
            </span>
            <span className="og-step-title">Mật khẩu mới</span>
          </div>
        </div>
      </div>
    </nav>
  );
};
