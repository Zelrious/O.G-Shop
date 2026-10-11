import React, { useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../features/auth';
import { verificationApi } from '../features/verification';
import { Card, Button, Alert, Badge } from '../shared/components';

export const SellerVerificationPage: React.FC = () => {
  const { user, reloadCurrentUser } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  if (!user) {
    navigate('/login');
    return null;
  }

  const isSeller = user.roles.includes('SELLER');

  if (user.sellerApprovalRequired && !isSeller) {
    return <div style={{ maxWidth: '680px', margin: '30px auto' }}>
      <Card title="Xác minh hồ sơ Người bán" subtitle="Quyền bán hàng cần được phê duyệt sau khi xác minh danh tính.">
        {location.state?.registrationCoins === 3000 && <Alert type="success">Đăng ký thành công. Bạn đã nhận 3.000 xu.</Alert>}
        <Alert type="info">
          Nếu bạn đã chọn Người bán khi đăng ký, thông tin cửa hàng, địa chỉ và ngân hàng đã được lưu.
          Tài khoản hiện có quyền Người mua. Quyền Người bán sẽ được cấp sau eKYC và kiểm duyệt.
        </Alert>
        <p>Bước nộp eKYC và theo dõi phê duyệt đang được hoàn thiện. Bạn có thể mua hàng trong thời gian này.</p>
        <Button variant="primary" onClick={() => navigate('/profile')}>Về Hồ sơ cá nhân</Button>
      </Card>
    </div>;
  }

  const handleActivate = async () => {
    if (isLoading || isSeller) return;

    setIsLoading(true);
    setError(null);
    setSuccessMessage(null);

    try {
      const result = await verificationApi.activateMvpSeller();
      await reloadCurrentUser();
      setSuccessMessage(
        `Kích hoạt quyền Người bán thành công! (Mã hồ sơ: #${result.verificationId}, Phương thức: ${result.verificationMethod})`
      );
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Kích hoạt quyền Người bán thất bại.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div style={{ maxWidth: '680px', margin: '30px auto' }}>
      <Card
        title="Kích hoạt tài khoản bán hàng (MVP)"
        subtitle="Thiết lập quyền Người bán cho tài khoản trên sàn giao dịch O.G Shop"
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
          {/* Cảnh báo rõ ràng về chế độ MVP */}
          <Alert type="info">
            <strong>Chế độ MVP:</strong> Bước xác minh eKYC thật đang tạm thời được bỏ qua. Đây không phải xác minh danh tính production.
          </Alert>

          {error && <Alert type="danger">{error}</Alert>}
          {successMessage && <Alert type="success">{successMessage}</Alert>}

          {isSeller ? (
            <div
              style={{
                backgroundColor: 'var(--og-color-surface-subtle)',
                padding: '20px',
                borderRadius: 'var(--og-radius-md)',
                border: '1px solid var(--og-color-border)',
                display: 'flex',
                flexDirection: 'column',
                gap: '12px',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <Badge variant="seller">Đã kích hoạt quyền Người bán</Badge>
              </div>
              <p style={{ margin: 0, color: 'var(--og-color-text-secondary)', fontSize: '0.95rem' }}>
                Tài khoản của bạn hiện đã có quyền <strong>SELLER</strong> từ hệ thống Backend. Bạn có thể sử dụng đầy đủ các tính năng đăng tin và quản lý sản phẩm ở bước tiếp theo.
              </p>
              <div style={{ marginTop: '8px' }}>
                <Button variant="primary" onClick={() => navigate('/profile')}>
                  Về trang Hồ sơ cá nhân
                </Button>
              </div>
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
              <p style={{ margin: 0, color: 'var(--og-color-text-secondary)', lineHeight: 1.6 }}>
                Khi nhấn kích hoạt, Backend sẽ cấp role <strong>SELLER</strong> cho tài khoản của bạn thông qua chế độ <code>MVP_BYPASS</code>. Quyền Người bán sẽ cho phép bạn đăng tin bán đồ cũ, quản lý đơn hàng và thương lượng ở task Catalog tiếp theo.
              </p>

              <div style={{ display: 'flex', gap: '12px', marginTop: '12px' }}>
                <Button
                  variant="gold"
                  onClick={handleActivate}
                  isLoading={isLoading}
                  disabled={isLoading}
                >
                  Kích hoạt quyền Người bán
                </Button>
                <Button
                  variant="outline"
                  onClick={() => navigate('/profile')}
                  disabled={isLoading}
                >
                  Quay lại Hồ sơ
                </Button>
              </div>
            </div>
          )}
        </div>
      </Card>
    </div>
  );
};
