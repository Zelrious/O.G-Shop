import React from 'react';
import type { DisputeDetail } from './types';
import { getReturnShipmentStatusMeta } from './returnShipmentMeta';

interface ReturnShipmentCardProps {
  dispute: DisputeDetail | null;
  buyerSenderName?: string | null;
  buyerSenderAddress?: string | null;
}

const formatDateTime = (isoStr?: string | null) => {
  if (!isoStr) return null;
  return new Date(isoStr).toLocaleString('vi-VN');
};

export const ReturnShipmentCard: React.FC<ReturnShipmentCardProps> = ({
  dispute,
  buyerSenderName,
  buyerSenderAddress,
}) => {
  if (!dispute || !dispute.returnTrackingCode) {
    return null;
  }

  const statusMeta = getReturnShipmentStatusMeta(dispute.returnShipmentStatus);
  const handedOverLabel = formatDateTime(dispute.returnHandedOverAt);
  const deliveredLabel = formatDateTime(dispute.returnDeliveredAt);

  return (
    <div
      data-testid="return-shipment-info-card"
      style={{
        background: 'var(--og-color-surface, #fff)',
        border: '1px solid #93c5fd',
        borderRadius: 12,
        padding: 20,
      }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 10, flexWrap: 'wrap', marginBottom: 12 }}>
        <h3 style={{ margin: 0, fontSize: '1rem', fontWeight: 600, color: 'var(--og-color-text-primary, #0f172a)', display: 'flex', alignItems: 'center', gap: 8 }}>
          <span>🔄</span> Thông tin vận chuyển hoàn trả (GHN — Chiều về)
        </h3>
        <span
          data-testid="return-shipment-status-badge"
          style={{
            background: statusMeta.bg,
            color: statusMeta.color,
            padding: '4px 12px',
            borderRadius: 12,
            fontWeight: 700,
            fontSize: '0.82rem',
          }}
        >
          {statusMeta.label}
        </span>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 8, fontSize: '0.88rem' }}>
        <div>
          <span style={{ color: 'var(--og-color-text-secondary, #64748b)' }}>Mã vận đơn hoàn trả: </span>
          <strong style={{ color: '#2563eb', fontSize: '1rem' }}>{dispute.returnTrackingCode}</strong>
        </div>
        <div>
          <span style={{ color: 'var(--og-color-text-secondary, #64748b)' }}>Đơn vị vận chuyển: </span>
          <strong>{dispute.returnCarrier || 'GHN'}</strong>
        </div>
        <div>
          <span style={{ color: 'var(--og-color-text-secondary, #64748b)' }}>Người gửi hoàn trả: </span>
          <strong>{buyerSenderName || dispute.buyerName}</strong>
          {buyerSenderAddress && (
            <span style={{ color: 'var(--og-color-text-secondary, #64748b)' }}> — {buyerSenderAddress}</span>
          )}
        </div>
        <div>
          <span style={{ color: 'var(--og-color-text-secondary, #64748b)' }}>Kho nhận hoàn trả (Người bán): </span>
          <strong>{dispute.returnRecipientName || dispute.sellerName}</strong>
          {dispute.returnRecipientPhone && <span> ({dispute.returnRecipientPhone})</span>}
          {dispute.returnRecipientAddress && (
            <div style={{ color: 'var(--og-color-text-secondary, #64748b)', fontSize: '0.83rem', marginTop: 2 }}>
              Địa chỉ kho: {dispute.returnRecipientAddress}
            </div>
          )}
        </div>
        {handedOverLabel && (
          <div>
            <span style={{ color: 'var(--og-color-text-secondary, #64748b)' }}>Thời gian bàn giao cho GHN: </span>
            <strong>{handedOverLabel}</strong>
          </div>
        )}
        {deliveredLabel && (
          <div>
            <span style={{ color: 'var(--og-color-text-secondary, #64748b)' }}>Thời gian giao trả thành công: </span>
            <strong style={{ color: '#15803d' }}>{deliveredLabel}</strong>
          </div>
        )}
      </div>
    </div>
  );
};

