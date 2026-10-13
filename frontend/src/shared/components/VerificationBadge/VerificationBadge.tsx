import { Icon } from '../Icon/Icon';
import React from 'react';

export type VerificationStatus = 'VERIFIED' | 'PENDING' | 'REJECTED' | 'NOT_SUBMITTED' | 'MVP_BYPASS';

export interface VerificationBadgeProps {
  status?: VerificationStatus | string;
  type?: 'SELLER' | 'BUYER';
  showLabel?: boolean;
  className?: string;
}

export const VerificationBadge: React.FC<VerificationBadgeProps> = ({
  status = 'VERIFIED',
  type = 'SELLER',
  showLabel = true,
  className = '',
}) => {
  const normStatus = (status || '').toUpperCase() as VerificationStatus;

  switch (normStatus) {
    case 'VERIFIED':
    case 'MVP_BYPASS':
      return (
        <span
          className={`og-badge og-badge--verified ${className}`}
          title={`${type === 'SELLER' ? 'Người bán' : 'Người mua'} đã xác minh danh tính eKYC`}
        >
          <span><Icon name="shield" /></span>
          {showLabel && <span>{type === 'SELLER' ? 'Người Bán Đã Xác Minh' : 'Đã Xác Minh'}</span>}
        </span>
      );
    case 'PENDING':
      return (
        <span
          className={`og-badge og-badge--pending ${className}`}
          title="Hồ sơ xác minh đang được xét duyệt"
        >
          <span><Icon name="clock" /></span>
          {showLabel && <span>Chờ Duyệt KYC</span>}
        </span>
      );
    case 'REJECTED':
      return (
        <span
          className={`og-badge og-badge--rejected ${className}`}
          title="Hồ sơ xác minh bị từ chối"
        >
          <span><Icon name="close" /></span>
          {showLabel && <span>Chưa Đạt KYC</span>}
        </span>
      );
    default:
      return null;
  }
};
