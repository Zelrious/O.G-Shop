import { Icon, type IconName } from '../Icon/Icon';
import React from 'react';

export type StatusCategory = 'order' | 'payment' | 'shipment' | 'complaint';

export interface StatusBadgeProps {
  category: StatusCategory;
  status: string;
  className?: string;
}

const STATUS_DICTIONARY: Record<
  string,
  { label: string; variant: 'success' | 'warning' | 'danger' | 'info' | 'neutral'; icon?: IconName }
> = {
  // Order Status
  PENDING_PAYMENT: { label: 'Chờ thanh toán', variant: 'warning', icon: 'clock' },
  PAID_HELD: { label: 'Đã giữ tiền', variant: 'info', icon: 'lock' },
  SELLER_CONFIRMED: { label: 'Người bán đã nhận', variant: 'info', icon: 'package' },
  PROCESSING: { label: 'Đang chuẩn bị', variant: 'info', icon: 'sliders' },
  SHIPPED: { label: 'Đang giao hàng', variant: 'info', icon: 'truck' },
  DELIVERED: { label: 'Đã giao hàng', variant: 'info', icon: 'package' },
  COMPLETED: { label: 'Hoàn tất giao dịch', variant: 'success', icon: 'check' },
  CANCELLED: { label: 'Đã hủy', variant: 'neutral', icon: 'close' },
  DISPUTED: { label: 'Đang khiếu nại', variant: 'danger', icon: 'file' },
  REFUNDED: { label: 'Đã hoàn tiền', variant: 'neutral', icon: 'back' },

  // Payment Status
  PENDING: { label: 'Chờ thanh toán', variant: 'warning', icon: 'clock' },
  HELD: { label: 'Tiền được bảo vệ (Held)', variant: 'info', icon: 'shield' },
  RELEASED: { label: 'Đã giải ngân', variant: 'success', icon: 'credit' },
  REFUND_PENDING: { label: 'Đang xử lý hoàn tiền', variant: 'warning', icon: 'clock' },
  FAILED: { label: 'Thanh toán lỗi', variant: 'danger', icon: 'close' },

  // Shipment Status
  PICKED_UP: { label: 'Đã lấy hàng', variant: 'info', icon: 'package' },
  IN_TRANSIT: { label: 'Đang vận chuyển', variant: 'info', icon: 'truck' },
  OUT_FOR_DELIVERY: { label: 'Đang phát hàng', variant: 'info', icon: 'truck' },
  RETURN_TO_SELLER: { label: 'Đang trả về người bán', variant: 'warning', icon: 'back' },

  // Complaint Status
  OPEN: { label: 'Khiếu nại mới mở', variant: 'danger', icon: 'alert' },
  REVIEWING: { label: 'Trọng tài đang xử lý', variant: 'warning', icon: 'file' },
  RESOLVED: { label: 'Đã có phán quyết', variant: 'success', icon: 'file' },
  REJECTED: { label: 'Bác bỏ khiếu nại', variant: 'neutral', icon: 'close' },
};

export const StatusBadge: React.FC<StatusBadgeProps> = ({
  status,
  className = '',
}) => {
  const normKey = (status || '').toUpperCase().replace(/\s+/g, '_');
  const entry = STATUS_DICTIONARY[normKey] || {
    label: status,
    variant: 'neutral',
    icon: 'tag',
  };

  return (
    <span
      className={`og-status-badge og-status-badge--${entry.variant} ${className}`}
      data-status={normKey}
    >
      {entry.icon && <span aria-hidden="true"><Icon name={entry.icon as IconName} size={14} /></span>}
      <span>{entry.label}</span>
    </span>
  );
};
