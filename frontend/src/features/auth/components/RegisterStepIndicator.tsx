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

  // Step 2 (Seller): Địa chỉ lấy hàng
  const step2Completed = isSeller && currentStep > 2;
  const step2Active = isSeller && currentStep === 2;

  // Step 3 (Seller): Tài khoản ngân hàng
  const step3Completed = isSeller && currentStep > 3;
  const step3Active = isSeller && currentStep === 3;

  // Final Step: Xác thực email (step 2 for buyer, step 4 for seller)
  const isFinalStepActive = (!isSeller && currentStep === 2) || (isSeller && currentStep === 4);

  return (
    <nav className="og-fluid-stepper" aria-label="Tiến trình đăng ký tài khoản">
      <div
        key={isSeller ? 'stepper-seller' : 'stepper-buyer'}
        className={`og-fluid-stepper-track ${isSeller ? 'og-stepper--seller' : 'og-stepper--buyer'}`}
      >
        {/* STEP 1: Tài khoản */}
        <div
          className={`og-step-node ${step1Active ? 'og-step-node--active' : ''} ${
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

        {/* CONNECTOR 1: Nối Bước 1 sang Bước tiếp theo */}
        <div
          className={`og-step-line ${step1Completed ? 'og-step-line--completed' : ''}`}
          aria-hidden="true"
        >
          <div className="og-step-line-fill" />
        </div>

        {/* CÁC BƯỚC DÀNH CHO NGƯỜI BÁN (ĐỊA CHỈ & NGÂN HÀNG) */}
        {isSeller && (
          <>
            {/* STEP 2 (Seller): Địa chỉ lấy hàng */}
            <div
              className={`og-step-node ${step2Active ? 'og-step-node--active' : ''} ${
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
                disabled={!step2Completed}
                tabIndex={0}
                aria-label={`Bước 2: Địa chỉ lấy hàng ${
                  step2Completed ? '(Đã hoàn thành)' : step2Active ? '(Đang thực hiện)' : ''
                }`}
              >
                <span className="og-step-circle">{step2Completed ? '✓' : '2'}</span>
                <span className="og-step-title">Địa chỉ</span>
              </button>
            </div>

            {/* CONNECTOR 2: Nối Địa chỉ sang Ngân hàng */}
            <div
              className={`og-step-line ${step2Completed ? 'og-step-line--completed' : ''}`}
              aria-hidden="true"
            >
              <div className="og-step-line-fill" />
            </div>

            {/* STEP 3 (Seller): Tài khoản ngân hàng */}
            <div
              className={`og-step-node ${step3Active ? 'og-step-node--active' : ''} ${
                step3Completed ? 'og-step-node--completed og-step-node--clickable' : ''
              }`}
              aria-current={step3Active ? 'step' : undefined}
            >
              <button
                type="button"
                className="og-step-node-btn"
                onClick={() => {
                  if (step3Completed) onStepClick?.(3);
                }}
                disabled={!step3Completed}
                tabIndex={0}
                aria-label={`Bước 3: Tài khoản ngân hàng ${
                  step3Completed ? '(Đã hoàn thành)' : step3Active ? '(Đang thực hiện)' : ''
                }`}
              >
                <span className="og-step-circle">{step3Completed ? '✓' : '3'}</span>
                <span className="og-step-title">Ngân hàng</span>
              </button>
            </div>

            {/* CONNECTOR 3: Nối Ngân hàng sang Xác thực */}
            <div
              className={`og-step-line ${step3Completed ? 'og-step-line--completed' : ''}`}
              aria-hidden="true"
            >
              <div className="og-step-line-fill" />
            </div>
          </>
        )}

        {/* FINAL STEP: Xác thực */}
        <div
          className={`og-step-node og-step-node--final ${isFinalStepActive ? 'og-step-node--active' : ''}`}
          aria-current={isFinalStepActive ? 'step' : undefined}
        >
          <div className="og-step-node-btn" style={{ cursor: 'default' }}>
            <span className="og-step-circle">
              {isSeller ? '4' : '2'}
            </span>
            <span className="og-step-title">
              {isSeller ? 'Xác thực' : 'Xác thực email'}
            </span>
          </div>
        </div>
      </div>
    </nav>
  );
};
