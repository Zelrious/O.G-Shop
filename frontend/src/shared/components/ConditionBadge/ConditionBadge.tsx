import React from 'react';

export type ProductCondition = 'LIKE_NEW' | 'GOOD' | 'FAIR' | 'POOR' | 'FOR_PARTS';

export interface ConditionBadgeProps {
  condition: ProductCondition | string;
  showIcon?: boolean;
  className?: string;
}

const CONDITION_CONFIG: Record<
  ProductCondition,
  { label: { vi: string; en: string }; icon: string; className: string }
> = {
  LIKE_NEW: {
    label: { vi: 'Như Mới', en: 'Like New' },
    icon: '✨',
    className: 'og-condition-badge--like-new',
  },
  GOOD: {
    label: { vi: 'Khá Tốt', en: 'Good' },
    icon: '👌',
    className: 'og-condition-badge--good',
  },
  FAIR: {
    label: { vi: 'Chấp Nhận Được', en: 'Fair' },
    icon: '👍',
    className: 'og-condition-badge--fair',
  },
  POOR: {
    label: { vi: 'Đã Dùng Nhiều', en: 'Poor' },
    icon: '⚠️',
    className: 'og-condition-badge--poor',
  },
  FOR_PARTS: {
    label: { vi: 'Lấy Linh Kiện', en: 'For Parts' },
    icon: '🔧',
    className: 'og-condition-badge--for-parts',
  },
};

export const ConditionBadge: React.FC<ConditionBadgeProps> = ({
  condition,
  showIcon = true,
  className = '',
}) => {
  const normCondition = (condition || '').toUpperCase().replace(/\s+/g, '_') as ProductCondition;
  const config = CONDITION_CONFIG[normCondition] || {
    label: { vi: condition, en: condition },
    icon: '🏷️',
    className: 'og-condition-badge--good',
  };

  return (
    <span
      className={`og-condition-badge ${config.className} ${className}`}
      title={`Tình trạng: ${config.label.vi} (${config.label.en})`}
    >
      {showIcon && <span aria-hidden="true">{config.icon}</span>}
      <span>{config.label.vi}</span>
    </span>
  );
};
