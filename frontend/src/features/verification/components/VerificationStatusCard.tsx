import React from 'react';
import { VerificationRecord } from '../types';
import { Card, Badge, Button } from '../../../shared/components';

interface VerificationStatusCardProps {
  record: VerificationRecord | null;
  onStartVerification?: () => void;
}

export const VerificationStatusCard: React.FC<VerificationStatusCardProps> = ({
  record,
  onStartVerification,
}) => {
  if (!record || record.status === 'NONE') {
    return (
      <Card title="Xác minh Người bán (Seller Verification)">
        <p style={{ color: 'var(--og-color-text-secondary)', margin: '0 0 16px' }}>
          Bạn chưa thực hiện xác thực danh tính eKYC. Để đảm bảo tính tin cậy trên O.G Shop và bắt đầu đăng bán sản phẩm, bạn cần hoàn thành xác minh thẻ CCCD và khuôn mặt.
        </p>
        <Button variant="primary" onClick={onStartVerification}>
          Bắt đầu xác minh eKYC ngay
        </Button>
      </Card>
    );
  }

  const getStatusBadge = () => {
    switch (record.status) {
      case 'VERIFIED':
        return <Badge variant="verified">Đã xác minh (Verified Seller)</Badge>;
      case 'PENDING':
        return <Badge variant="pending">Đang chờ phê duyệt (Pending)</Badge>;
      case 'REJECTED':
        return <Badge variant="rejected">Bị từ chối (Rejected)</Badge>;
      default:
        return <Badge variant="neutral">Chưa xác minh</Badge>;
    }
  };

  return (
    <Card title="Hồ sơ Xác minh Người bán (eKYC)">
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '16px' }}>
        <span style={{ fontWeight: 600 }}>Tình trạng hồ sơ:</span>
        {getStatusBadge()}
      </div>

      <div
        style={{
          backgroundColor: 'var(--og-color-surface-subtle)',
          padding: '16px',
          borderRadius: 'var(--og-radius-md)',
          marginBottom: '16px',
          fontSize: '0.9rem',
        }}
      >
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '8px' }}>
          <div>
            <span style={{ color: 'var(--og-color-text-muted)' }}>Họ và tên: </span>
            <strong>{record.ocrData.fullName}</strong>
          </div>
          <div>
            <span style={{ color: 'var(--og-color-text-muted)' }}>Số CCCD: </span>
            <strong>{record.ocrData.cccdNumber}</strong>
          </div>
          <div>
            <span style={{ color: 'var(--og-color-text-muted)' }}>Ngày sinh: </span>
            <span>{record.ocrData.dob}</span>
          </div>
          <div>
            <span style={{ color: 'var(--og-color-text-muted)' }}>Phương thức: </span>
            <code>{record.verificationMethod}</code>
          </div>
        </div>

        {record.similarityScore !== undefined && (
          <div style={{ marginTop: '10px', paddingTop: '8px', borderTop: '1px solid var(--og-color-border)' }}>
            <span style={{ color: 'var(--og-color-text-muted)' }}>Độ sai lệch sinh trắc học: </span>
            <code style={{ color: 'var(--og-color-success)', fontWeight: 700 }}>
              {record.similarityScore} (Cosine Distance)
            </code>
          </div>
        )}
      </div>

      {record.status === 'REJECTED' && record.rejectionReason && (
        <div style={{ color: 'var(--og-color-danger)', marginBottom: '16px', fontSize: '0.9rem' }}>
          <strong>Lý do từ chối:</strong> {record.rejectionReason}
        </div>
      )}

      {record.status === 'REJECTED' && onStartVerification && (
        <Button variant="danger" onClick={onStartVerification}>
          Thực hiện xác minh lại
        </Button>
      )}

      {record.status === 'VERIFIED' && (
        <p style={{ margin: 0, fontSize: '0.85rem', color: 'var(--og-color-success)' }}>
          ✓ Danh tính của bạn đã được đối chiếu an toàn. Bạn có toàn quyền đăng bán và quản lý sản phẩm.
        </p>
      )}
    </Card>
  );
};
