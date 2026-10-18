import React from 'react';

interface RegisterStepIndicatorProps {
  currentStep: number;
  accountType: 'BUYER' | 'SELLER';
  onStepClick?: (step: number) => void;
}

export const RegisterStepIndicator: React.FC<RegisterStepIndicatorProps> = ({
  currentStep,
  accountType,
  onStepClick,
}) => {
  const isSeller = accountType === 'SELLER';

  // Step 1: Tài khoản
  const step1Completed = currentStep > 1;
  const step1Active = currentStep === 1;

  // Step 2 (Seller): Hồ sơ người bán
  const step2Completed = isSeller && currentStep > 2;
  const step2Active = isSeller && currentStep === 2;

  // Final Step: Xác thực email (step 2 for buyer, step 3 for seller)
  const isFinalStepActive = (!isSeller && currentStep === 2) || (isSeller && currentStep === 3);

  return (
    <nav className="og-fluid-stepper" aria-label="Tiến trình đăng ký tài khoản">
      <div className={`og-fluid-stepper-track ${isSeller ? 'og-stepper--seller' : 'og-stepper--buyer'}`}>
        {/* STEP 1: Tài khoản */}
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
            aria-label={`Bước 1: Tài khoản ${step1Completed ? '(Đã hoàn thành)' : step1Active ? '(Đang thực hiện)' : ''}`}
          >
            <span className="og-step-circle">{step1Completed ? '✓' : '1'}</span>
            <span className="og-step-title">Tài khoản</span>
          </button>
        </div>

        {/* CONNECTOR 1: luôn nối từ Bước 1 sang bước tiếp theo */}
        <div
          className={`og-step-line og-step-line--1 ${step1Completed ? 'og-step-line--completed' : ''}`}
          aria-hidden="true"
        >
          <div className="og-step-line-fill" />
        </div>

        {/* MIDDLE SECTION (SELLER ONLY): Hồ sơ người bán + Connector 2 */}
        <div
          className={`og-step-middle-slot ${isSeller ? 'og-step-middle-slot--expanded' : ''}`}
          aria-hidden={!isSeller}
        >
          <div
            className={`og-step-node og-step-node--seller ${step2Active ? 'og-step-node--active' : ''} ${
              step2Completed ? 'og-step-node--completed og-step-node--clickable' : ''
            }`}
            aria-current={step2Active ? 'step' : undefined}
          >
            <button
              type="button"
              className="og-step-node-btn"
              onClick={() => {
                if (step2Completed) onStepClick?.(2);
              }}
              disabled={!step2Completed || !isSeller}
              tabIndex={isSeller ? 0 : -1}
              aria-label={`Bước 2: Hồ sơ người bán ${
                step2Completed ? '(Đã hoàn thành)' : step2Active ? '(Đang thực hiện)' : ''
              }`}
            >
              <span className="og-step-circle">{step2Completed ? '✓' : '2'}</span>
              <span className="og-step-title">Hồ sơ người bán</span>
            </button>
          </div>

          {/* CONNECTOR 2: nối từ Hồ sơ người bán sang Xác thực email */}
          <div
            className={`og-step-line og-step-line--2 ${step2Completed ? 'og-step-line--completed' : ''}`}
            aria-hidden="true"
          >
            <div className="og-step-line-fill" />
          </div>
        </div>

        {/* FINAL STEP: Xác thực email (trượt sang phải khi chọn Người bán, số chuyển 2 -> 3) */}
        <div
          className={`og-step-node og-step-node--final ${isFinalStepActive ? 'og-step-node--active' : ''}`}
          aria-current={isFinalStepActive ? 'step' : undefined}
        >
          <div className="og-step-node-btn" style={{ cursor: 'default' }}>
            <span className="og-step-circle">
              <span key={isSeller ? 'seller-step-3' : 'buyer-step-2'} className="og-step-digit-anim">
                {isSeller ? '3' : '2'}
              </span>
            </span>
            <span className="og-step-title">Xác thực email</span>
          </div>
        </div>
      </div>
    </nav>
  );
};
