import React from 'react';

export type StatusCategory = 'order' | 'payment' | 'shipment' | 'complaint';

export interface StatusBadgeProps {
  category: StatusCategory;
  status: string;
  className?: string;
}

const STATUS_DICTIONARY: Record<
  string,
  { label: string; variant: 'success' | 'warning' | 'danger' | 'info' | 'neutral'; icon?: string }
> = {
  // Order Status
  PENDING_PAYMENT: { label: 'Chờ thanh toán', variant: 'warning', icon: '⏱️' },
  PAID_HELD: { label: 'Đã giữ tiền', variant: 'info', icon: '🔒' },
  SELLER_CONFIRMED: { label: 'Người bán đã nhận', variant: 'info', icon: '📦' },
  PROCESSING: { label: 'Đang chuẩn bị', variant: 'info', icon: '⚙️' },
  SHIPPED: { label: 'Đang giao hàng', variant: 'info', icon: '🚚' },
  DELIVERED: { label: 'Đã giao hàng', variant: 'info', icon: '📬' },
  COMPLETED: { label: 'Hoàn tất giao dịch', variant: 'success', icon: '✅' },
  CANCELLED: { label: 'Đã hủy', variant: 'neutral', icon: '🚫' },
  DISPUTED: { label: 'Đang khiếu nại', variant: 'danger', icon: '⚖️' },
  REFUNDED: { label: 'Đã hoàn tiền', variant: 'neutral', icon: '↩️' },

  // Payment Status
  PENDING: { label: 'Chờ thanh toán', variant: 'warning', icon: '⏳' },
  HELD: { label: 'Tiền được bảo vệ (Held)', variant: 'info', icon: '🛡️' },
  RELEASED: { label: 'Đã giải ngân', variant: 'success', icon: '💰' },
  REFUND_PENDING: { label: 'Đang xử lý hoàn tiền', variant: 'warning', icon: '⏳' },
  FAILED: { label: 'Thanh toán lỗi', variant: 'danger', icon: '❌' },

  // Shipment Status
  PICKED_UP: { label: 'Đã lấy hàng', variant: 'info', icon: '📦' },
  IN_TRANSIT: { label: 'Đang vận chuyển', variant: 'info', icon: '🚛' },
  OUT_FOR_DELIVERY: { label: 'Đang phát hàng', variant: 'info', icon: '🛵' },
  RETURN_TO_SELLER: { label: 'Đang trả về người bán', variant: 'warning', icon: '↩️' },

  // Complaint Status
  OPEN: { label: 'Khiếu nại mới mở', variant: 'danger', icon: '⚠️' },
  REVIEWING: { label: 'Trọng tài đang xử lý', variant: 'warning', icon: '⚖️' },
  RESOLVED: { label: 'Đã có phán quyết', variant: 'success', icon: '📋' },
  REJECTED: { label: 'Bác bỏ khiếu nại', variant: 'neutral', icon: '❌' },
};

export const StatusBadge: React.FC<StatusBadgeProps> = ({
  status,
  className = '',
}) => {
  const normKey = (status || '').toUpperCase().replace(/\s+/g, '_');
  const entry = STATUS_DICTIONARY[normKey] || {
    label: status,
    variant: 'neutral',
    icon: '🏷️',
  };

  return (
    <span
      className={`og-status-badge og-status-badge--${entry.variant} ${className}`}
      data-status={normKey}
    >
      {entry.icon && <span aria-hidden="true">{entry.icon}</span>}
      <span>{entry.label}</span>
    </span>
  );
};
