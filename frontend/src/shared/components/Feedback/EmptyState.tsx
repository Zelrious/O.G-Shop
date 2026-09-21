import React from 'react';
import { Button } from '../Button/Button';

export interface EmptyStateProps {
  icon?: string;
  title: string;
  description?: string;
  actionText?: string;
  onAction?: () => void;
  className?: string;
}

export const EmptyState: React.FC<EmptyStateProps> = ({
  icon = '📦',
  title,
  description,
  actionText,
  onAction,
  className = '',
}) => {
  return (
    <div className={`og-empty-state ${className}`} role="status">
      <div className="og-empty-state__icon" aria-hidden="true">
        {icon}
      </div>
      <h3 className="og-empty-state__title">{title}</h3>
      {description && <p className="og-empty-state__desc">{description}</p>}
      {actionText && onAction && (
        <Button variant="primary" size="md" onClick={onAction}>
          {actionText}
        </Button>
      )}
    </div>
  );
};
