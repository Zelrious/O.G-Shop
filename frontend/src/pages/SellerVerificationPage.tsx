import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../features/auth';
import {
  CccdOcrData,
  BiometricScanResult,
  CccdUploader,
  CameraCapture,
} from '../features/verification';
import { Card, Button, Alert, Badge } from '../shared/components';

export const SellerVerificationPage: React.FC = () => {
  const { user, reloadCurrentUser } = useAuth();
  const navigate = useNavigate();

  const [currentStep, setCurrentStep] = useState<1 | 2 | 3 | 4>(1);
  const [ocrData, setOcrData] = useState<CccdOcrData | null>(null);
  const [cardFile, setCardFile] = useState<File | null>(null);
  const [isSimulated, setIsSimulated] = useState<boolean>(true);
  const [biometricResult, setBiometricResult] = useState<BiometricScanResult | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!user) {
    navigate('/login');
    return null;
  }

  const handleOcrComplete = (
    data: CccdOcrData,
    extra?: { cardFile: File; isSimulated: boolean }
  ) => {
    setOcrData(data);
    if (extra) {
      setCardFile(extra.cardFile);
      setIsSimulated(extra.isSimulated);
    }
  };

  const handleScanComplete = (result: BiometricScanResult) => {
    setBiometricResult(result);
    setIsSimulated(result.isSimulated);
    if (result.ocrData) setOcrData(result.ocrData);
  };

  const handleSubmitVerification = async () => {
    if (!ocrData || !biometricResult) {
      setError('Vui lòng hoàn thành cả 2 bước: quét thẻ CCCD và quét khuôn mặt.');
      return;
    }

    setIsSubmitting(true);
    setError(null);

    try {
      await reloadCurrentUser();
      setCurrentStep(4);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Xác minh không thành công.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div style={{ maxWidth: '780px', margin: '30px auto' }}>
      <Card
        title="Xác thực Danh tính Người bán (eKYC)"
        subtitle="Hệ thống trí tuệ nhân tạo nhận diện CCCD và so khớp sinh trắc học khuôn mặt."
      >
        {/* Thanh tiến trình các bước (Step Wizard Bar) */}
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            marginBottom: '28px',
            paddingBottom: '16px',
            borderBottom: '1px solid var(--og-color-border)',
            gap: '8px',
          }}
        >
          {[
            { step: 1, label: '1. Thẻ CCCD' },
            { step: 2, label: '2. Khuôn mặt' },
            { step: 3, label: '3. Đối soát' },
            { step: 4, label: '4. Hoàn tất' },
          ].map((item) => (
            <div
              key={item.step}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '6px',
                fontWeight: currentStep === item.step ? 700 : 500,
                color:
                  currentStep === item.step
                    ? 'var(--og-color-primary)'
                    : currentStep > item.step
                    ? 'var(--og-color-success)'
                    : 'var(--og-color-text-muted)',
                fontSize: '0.88rem',
              }}
            >
              <span
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  width: '24px',
                  height: '24px',
                  borderRadius: '50%',
                  backgroundColor:
                    currentStep === item.step
                      ? 'var(--og-color-primary)'
                      : currentStep > item.step
                      ? 'var(--og-color-success)'
                      : 'var(--og-color-surface-subtle)',
                  color:
                    currentStep >= item.step ? '#ffffff' : 'var(--og-color-text-muted)',
                  fontSize: '0.75rem',
                  fontWeight: 700,
                }}
              >
                {currentStep > item.step ? '✓' : item.step}
              </span>
              <span>{item.label}</span>
            </div>
          ))}
        </div>

        {error && <Alert type="danger">{error}</Alert>}

        {/* BƯỚC 1: Tải thẻ CCCD */}
        {currentStep === 1 && (
          <div>
            <h3 style={{ margin: '0 0 16px', fontSize: '1.1rem' }}>Bước 1: Quét thẻ Căn cước công dân</h3>
            <CccdUploader onOcrComplete={handleOcrComplete} initialData={ocrData} />

            <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '24px' }}>
              <Button
                variant="primary"
                disabled={!ocrData || !ocrData.cccdNumber || !cardFile}
                onClick={() => setCurrentStep(2)}
              >
                Tiếp tục sang Quét khuôn mặt →
              </Button>
            </div>
          </div>
        )}

        {/* BƯỚC 2: Quét khuôn mặt qua Camera */}
        {currentStep === 2 && (
          <div>
            <h3 style={{ margin: '0 0 16px', fontSize: '1.1rem' }}>Bước 2: So khớp sinh trắc học khuôn mặt</h3>
            <p style={{ margin: '0 0 20px', color: 'var(--og-color-text-secondary)', fontSize: '0.9rem' }}>
              Hệ thống sẽ mở camera để đối chiếu trực tiếp khuôn mặt của bạn với ảnh chân dung trên thẻ CCCD vừa quét.
            </p>

            <CameraCapture
              onScanComplete={handleScanComplete}
              cardFile={cardFile!}
            />

            <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: '24px' }}>
              <Button variant="outline" onClick={() => setCurrentStep(1)}>
                ← Quay lại Bước 1
              </Button>
              <Button
                variant="primary"
                disabled={!biometricResult || !biometricResult.isMatch}
                onClick={() => setCurrentStep(3)}
              >
                Tiếp tục xem lại thông tin →
              </Button>
            </div>
          </div>
        )}

        {/* BƯỚC 3: Đối soát thông tin & Gửi hồ sơ */}
        {currentStep === 3 && ocrData && biometricResult && (
          <div>
            <h3 style={{ margin: '0 0 16px', fontSize: '1.1rem' }}>Bước 3: Xác nhận thông tin hồ sơ eKYC</h3>

            <div
              style={{
                backgroundColor: 'var(--og-color-surface-subtle)',
                padding: '20px',
                borderRadius: 'var(--og-radius-md)',
                marginBottom: '20px',
                border: '1px solid var(--og-color-border)',
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
                <h4 style={{ margin: 0, color: 'var(--og-color-primary)', fontSize: '1rem' }}>
                  Thông tin người bán đã xác thực:
                </h4>
                <Badge variant="neutral">
                  {isSimulated ? 'eKYC mô phỏng (VietOCR + ArcFace)' : 'eKYC sandbox'}
                </Badge>
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '10px' }}>
                <div>Số CCCD: <strong>{ocrData.cccdNumber}</strong></div>
                <div>Họ và tên: <strong>{ocrData.fullName}</strong></div>
                <div>Ngày sinh: <span>{ocrData.dob}</span></div>
                {ocrData.gender && <div>Giới tính: <span>{ocrData.gender}</span></div>}
                <div>Quê quán: <span>{ocrData.hometown}</span></div>
                <div style={{ gridColumn: '1 / -1' }}>Địa chỉ: <span>{ocrData.address}</span></div>
              </div>

              <div style={{ marginTop: '16px', paddingTop: '12px', borderTop: '1px solid var(--og-color-border)', display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: '16px' }}>
                <div>
                  <span style={{ fontSize: '0.85rem', color: 'var(--og-color-text-muted)' }}>Sinh trắc học: </span>
                  <Badge variant="verified">Khớp danh tính</Badge>
                </div>
                <div>
                  <span style={{ fontSize: '0.85rem', color: 'var(--og-color-text-muted)' }}>Khoảng cách Cosine: </span>
                  <code>{biometricResult.distance} (&le; 0.50)</code>
                </div>
                {biometricResult.confidenceScore !== undefined && (
                  <div>
                    <span style={{ fontSize: '0.85rem', color: 'var(--og-color-text-muted)' }}>Độ tin cậy: </span>
                    <strong>{(biometricResult.confidenceScore * 100).toFixed(1)}%</strong>
                  </div>
                )}
              </div>
            </div>

            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <Button variant="outline" onClick={() => setCurrentStep(2)}>
                ← Quay lại
              </Button>
              <Button
                variant="gold"
                onClick={handleSubmitVerification}
                isLoading={isSubmitting}
              >
                Hoàn tất và Tải lại Quyền từ Backend
              </Button>
            </div>
          </div>
        )}

        {/* BƯỚC 4: Hoàn tất */}
        {currentStep === 4 && (
          <div style={{ textAlign: 'center', padding: '20px 0' }}>
            <div style={{ fontSize: '3.5rem', marginBottom: '12px' }}>🎉</div>
            <h3 style={{ margin: '0 0 8px', fontSize: '1.4rem', color: 'var(--og-color-success)' }}>
              Chúc mừng! Bạn đã hoàn thành xác thực eKYC
            </h3>
            <p style={{ margin: '0 0 24px', color: 'var(--og-color-text-secondary)', fontSize: '0.95rem' }}>
              Hồ sơ xác minh người bán của bạn đã được đối chiếu thành công. Tài khoản của bạn đã được nâng cấp thành <strong>SELLER</strong> trên nền tảng O.G Shop.
            </p>

            <div style={{ display: 'flex', justifyContent: 'center', gap: '12px' }}>
              <Button variant="primary" onClick={() => navigate('/profile')}>
                Về trang Hồ sơ cá nhân
              </Button>
            </div>
          </div>
        )}
      </Card>
    </div>
  );
};
