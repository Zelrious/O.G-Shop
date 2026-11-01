import React, { useEffect, useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, Badge, Button, Alert } from '../../../shared/components';
import { ekycApi, EkycStatus } from '../ekycApi';
import { SecureAssetImage } from './SecureAssetImage';
import { QuickFaceAuthModal } from './QuickFaceAuthModal';

export interface EkycProfileDetailsCardProps {
  user?: {
    fullName?: string;
    roles?: string[];
    verifiedSeller?: boolean;
    sellerApprovalRequired?: boolean;
  } | null;
  ekycStatus?: EkycStatus | null;
  onStatusUpdated?: () => void;
  title?: string;
  isSellerView?: boolean;
}

export const EkycProfileDetailsCard: React.FC<EkycProfileDetailsCardProps> = ({
  user,
  ekycStatus: propStatus,
  onStatusUpdated,
  title,
  isSellerView = false,
}) => {
  const navigate = useNavigate();
  const [internalStatus, setInternalStatus] = useState<EkycStatus | null>(propStatus || null);
  const [showReverifyModal, setShowReverifyModal] = useState(false);
  const [reverifyError, setReverifyError] = useState<string | null>(null);
  const [isStartingReverify, setIsStartingReverify] = useState(false);

  // Synchronize propStatus
  useEffect(() => {
    if (propStatus !== undefined) {
      setInternalStatus(propStatus);
    }
  }, [propStatus]);

  // Fetch status if not provided
  useEffect(() => {
    if (propStatus === undefined) {
      void (async () => {
        try {
          const data = await ekycApi.getStatus();
          setInternalStatus(data);
        } catch {
          // Fallback if status API not reachable
        }
      })();
    }
  }, [propStatus]);

  const maskCccd = (num?: string) => {
    if (!num) return 'Đã được mã hóa';
    const clean = num.trim();
    if (clean.length <= 4) return '****';
    return clean.slice(0, 3) + '******' + clean.slice(-3);
  };

  const isSeller = Boolean(user?.roles?.includes('SELLER'));
  const isVerified = Boolean(
    user?.verifiedSeller ||
    internalStatus?.hasVerifiedProfile ||
    internalStatus?.state === 'VERIFIED'
  );
  const isPending = internalStatus?.state === 'PENDING' || (internalStatus?.state as string) === 'NEEDS_REVIEW';
  const isRejected = internalStatus?.state === 'REJECTED';

  const handleStartReverification = () => {
    setReverifyError(null);
    setShowReverifyModal(true);
  };

  const handleReverifySuccess = useCallback(async (resultId: string) => {
    setShowReverifyModal(false);
    setIsStartingReverify(true);
    setReverifyError(null);
    try {
      await ekycApi.startOrResume(resultId);
      if (onStatusUpdated) onStatusUpdated();
      navigate('/ekyc/verification?reverify=true&step=2');
    } catch (err: unknown) {
      setReverifyError(err instanceof Error ? err.message : 'Không thể khởi tạo phiên cập nhật hồ sơ eKYC.');
    } finally {
      setIsStartingReverify(false);
    }
  }, [navigate, onStatusUpdated]);

  const cardTitle = title || (isSellerView ? 'Hồ sơ định danh eKYC Người bán' : 'Định danh tài khoản (eKYC) & Quyền lợi thành viên');

  return (
    <>
      <Card title={cardTitle}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          {reverifyError && <Alert type="danger">{reverifyError}</Alert>}

          {/* Badges bar */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px', flexWrap: 'wrap' }}>
            {isSeller ? (
              <Badge variant="seller">Đã kích hoạt Người bán</Badge>
            ) : (
              <Badge variant="buyer">Tài khoản Người mua</Badge>
            )}

            {isVerified ? (
              <Badge variant="seller">✓ Đã xác thực eKYC CCCD</Badge>
            ) : isPending ? (
              <Badge variant="warning">⏳ Hồ sơ đang chờ KTV duyệt</Badge>
            ) : isRejected ? (
              <Badge variant="danger">✗ Hồ sơ eKYC bị từ chối</Badge>
            ) : (
              <Badge variant="warning">Chưa định danh eKYC</Badge>
            )}
          </div>

          {/* Pending banner */}
          {isPending && !isVerified && (
            <Alert type="warning">
              ⏳ <strong>Hồ sơ đang chờ KTV duyệt:</strong> Kỹ thuật viên O.G Shop đang đối soát thông tin CCCD và hình ảnh sinh trắc học của bạn. Quá trình xử lý diễn ra trong tối đa 24 giờ làm việc.
            </Alert>
          )}

          {/* Rejected banner */}
          {isRejected && (
            <Alert type="danger">
              ✗ <strong>Hồ sơ eKYC bị từ chối:</strong> {internalStatus?.rejectionReason || 'Thông tin hoặc hình ảnh CCCD không hợp lệ.'}
              <div style={{ marginTop: '10px' }}>
                <Button variant="primary" size="sm" onClick={() => navigate('/ekyc/verification?reverify=true&step=2')}>
                  Chụp lại & Nộp lại hồ sơ eKYC
                </Button>
              </div>
            </Alert>
          )}

          {/* Chi tiết hồ sơ CCCD nếu đã xác thực */}
          {isVerified ? (
            <div
              style={{
                backgroundColor: 'var(--og-color-surface-subtle, #f8fafc)',
                border: '1px solid var(--og-color-border, #e2e8f0)',
                borderRadius: '12px',
                padding: '18px 20px',
              }}
            >
              <h4 style={{ margin: '0 0 14px', fontSize: '0.98rem', display: 'flex', alignItems: 'center', gap: '8px' }}>
                <span>🪪</span> Thông tin thẻ CCCD đã xác thực
              </h4>

              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '14px' }}>
                <div>
                  <span style={{ fontSize: '0.8rem', color: 'var(--og-color-text-secondary)', display: 'block' }}>Họ và tên</span>
                  <strong style={{ fontSize: '0.94rem' }}>
                    {internalStatus?.submittedData?.fullName || user?.fullName || '---'}
                  </strong>
                </div>
                <div>
                  <span style={{ fontSize: '0.8rem', color: 'var(--og-color-text-secondary)', display: 'block' }}>Số CCCD gắn chip</span>
                  <strong style={{ fontSize: '0.94rem', letterSpacing: '0.5px' }}>
                    {maskCccd(internalStatus?.submittedData?.cccdNumber)}
                  </strong>
                </div>
                {internalStatus?.submittedData?.dob && (
                  <div>
                    <span style={{ fontSize: '0.8rem', color: 'var(--og-color-text-secondary)', display: 'block' }}>Ngày sinh</span>
                    <strong style={{ fontSize: '0.94rem' }}>{internalStatus.submittedData.dob}</strong>
                  </div>
                )}
                {internalStatus?.submittedData?.gender && (
                  <div>
                    <span style={{ fontSize: '0.8rem', color: 'var(--og-color-text-secondary)', display: 'block' }}>Giới tính</span>
                    <strong style={{ fontSize: '0.94rem' }}>{internalStatus.submittedData.gender}</strong>
                  </div>
                )}
                {internalStatus?.submittedData?.hometown && (
                  <div>
                    <span style={{ fontSize: '0.8rem', color: 'var(--og-color-text-secondary)', display: 'block' }}>Quê quán</span>
                    <strong style={{ fontSize: '0.94rem' }}>{internalStatus.submittedData.hometown}</strong>
                  </div>
                )}
                {internalStatus?.submittedData?.address && (
                  <div>
                    <span style={{ fontSize: '0.8rem', color: 'var(--og-color-text-secondary)', display: 'block' }}>Nơi thường trú</span>
                    <strong style={{ fontSize: '0.94rem' }}>{internalStatus.submittedData.address}</strong>
                  </div>
                )}
                <div>
                  <span style={{ fontSize: '0.8rem', color: 'var(--og-color-text-secondary)', display: 'block' }}>Trạng thái đối soát</span>
                  <span style={{ fontSize: '0.9rem', color: 'var(--og-color-success)', fontWeight: 600 }}>
                    ✓ Đã đối soát KTV & AI
                  </span>
                </div>
              </div>

              {/* Lưới hình ảnh hồ sơ đã lưu */}
              {Boolean(
                internalStatus?.frontAssetId ||
                internalStatus?.backAssetId ||
                internalStatus?.portraitAssetId ||
                internalStatus?.liveAssetId
              ) && (
                <div style={{ marginTop: '16px', paddingTop: '14px', borderTop: '1px solid var(--og-color-border, #e2e8f0)' }}>
                  <span style={{ fontSize: '0.85rem', fontWeight: 600, color: 'var(--og-color-text-secondary)', display: 'block', marginBottom: '10px' }}>
                    🖼️ Hình ảnh tài liệu định danh đã xác thực:
                  </span>
                  <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(130px, 1fr))', gap: '10px' }}>
                    {internalStatus?.frontAssetId && (
                      <div style={{ textAlign: 'center', border: '1px solid var(--og-color-border, #e2e8f0)', borderRadius: '8px', padding: '8px', backgroundColor: 'var(--og-color-surface)' }}>
                        <span style={{ fontSize: '0.75rem', color: 'var(--og-color-text-secondary)', display: 'block', marginBottom: '4px' }}>CCCD Mặt trước</span>
                        <SecureAssetImage assetId={internalStatus.frontAssetId} alt="CCCD Mặt trước" style={{ width: '100%', height: '90px', objectFit: 'contain', borderRadius: '4px' }} fallbackText="Mặt trước" />
                      </div>
                    )}
                    {internalStatus?.backAssetId && (
                      <div style={{ textAlign: 'center', border: '1px solid var(--og-color-border, #e2e8f0)', borderRadius: '8px', padding: '8px', backgroundColor: 'var(--og-color-surface)' }}>
                        <span style={{ fontSize: '0.75rem', color: 'var(--og-color-text-secondary)', display: 'block', marginBottom: '4px' }}>CCCD Mặt sau</span>
                        <SecureAssetImage assetId={internalStatus.backAssetId} alt="CCCD Mặt sau" style={{ width: '100%', height: '90px', objectFit: 'contain', borderRadius: '4px' }} fallbackText="Mặt sau" />
                      </div>
                    )}
                    {internalStatus?.portraitAssetId && (
                      <div style={{ textAlign: 'center', border: '1px solid var(--og-color-border, #e2e8f0)', borderRadius: '8px', padding: '8px', backgroundColor: 'var(--og-color-surface)' }}>
                        <span style={{ fontSize: '0.75rem', color: 'var(--og-color-text-secondary)', display: 'block', marginBottom: '4px' }}>Ảnh thẻ chân dung</span>
                        <SecureAssetImage assetId={internalStatus.portraitAssetId} alt="Ảnh thẻ chân dung" style={{ width: '100%', height: '90px', objectFit: 'contain', borderRadius: '4px' }} fallbackText="Ảnh thẻ 3x4" />
                      </div>
                    )}
                    {internalStatus?.liveAssetId && (
                      <div style={{ textAlign: 'center', border: '1px solid var(--og-color-border, #e2e8f0)', borderRadius: '8px', padding: '8px', backgroundColor: 'var(--og-color-surface)' }}>
                        <span style={{ fontSize: '0.75rem', color: 'var(--og-color-text-secondary)', display: 'block', marginBottom: '4px' }}>Chân dung đối chiếu</span>
                        <SecureAssetImage assetId={internalStatus.liveAssetId} alt="Chân dung đối chiếu" style={{ width: '100%', height: '90px', objectFit: 'contain', borderRadius: '4px' }} fallbackText="Chân dung" />
                      </div>
                    )}
                  </div>
                </div>
              )}

              {/* Khu vực nút Xác thực lại / Đổi CCCD */}
              <div style={{ marginTop: '16px', paddingTop: '14px', borderTop: '1px dashed var(--og-color-border, #e2e8f0)', display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '10px' }}>
                <span style={{ fontSize: '0.84rem', color: 'var(--og-color-text-secondary)' }}>
                  Bạn vừa đổi thẻ CCCD mới hoặc cần cập nhật thông tin định danh?
                </span>
                <Button
                  variant="outline"
                  size="sm"
                  type="button"
                  onClick={handleStartReverification}
                  isLoading={isStartingReverify}
                >
                  🔄 Cập nhật lại hồ sơ eKYC mới
                </Button>
              </div>
            </div>
          ) : (
            !isPending && (
              <div style={{ marginTop: '4px', display: 'flex', alignItems: 'center', gap: '12px', flexWrap: 'wrap' }}>
                <Button variant="gold" size="sm" onClick={() => navigate('/ekyc/verification?step=2')}>
                  Bắt đầu xác thực danh tính (eKYC)
                </Button>
                <small style={{ color: 'var(--og-color-text-tertiary)' }}>
                  Quy trình gồm tải ảnh CCCD 2 mặt và quét khuôn mặt, hỗ trợ mở gian hàng Người bán.
                </small>
              </div>
            )
          )}

          {/* Quyền lợi định danh */}
          <div style={{ fontSize: '0.88rem', color: 'var(--og-color-text-secondary)', lineHeight: 1.6 }}>
            <p style={{ margin: '0 0 6px', fontWeight: 600, color: 'var(--og-color-text-primary)' }}>
              Quyền lợi khi định danh eKYC:
            </p>
            <ul style={{ margin: 0, paddingLeft: '20px', display: 'flex', flexDirection: 'column', gap: '4px' }}>
              <li>Bảo vệ tài khoản với công nghệ Face Auth khi thay đổi thông tin ngân hàng & SĐT.</li>
              <li>Mở khóa quyền đăng ký gian hàng bán đồ cũ trên nền tảng O.G Shop.</li>
              <li>Giao dịch an tâm 100% bảo vệ bởi hệ thống ký quỹ Escrow.</li>
            </ul>
          </div>
        </div>
      </Card>

      {/* Quick Face Auth Modal for Re-verification Guard */}
      {showReverifyModal && (
        <QuickFaceAuthModal
          isOpen={showReverifyModal}
          onClose={() => setShowReverifyModal(false)}
          onSuccess={handleReverifySuccess}
          onNeedEkyc={() => {
            setShowReverifyModal(false);
            navigate('/ekyc/verification?reverify=true&step=2');
          }}
          title="Xác thực khuôn mặt đối chiếu hồ sơ cũ"
          description="Vui lòng quét khuôn mặt để xác thực danh tính chủ sở hữu trước khi nộp hồ sơ CCCD mới."
        />
      )}
    </>
  );
};

