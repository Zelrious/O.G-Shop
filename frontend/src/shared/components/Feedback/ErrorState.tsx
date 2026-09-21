import React from 'react';
import { Button } from '../Button/Button';

export interface ErrorStateProps {
  icon?: string;
  title?: string;
  message: string;
  retryText?: string;
  onRetry?: () => void;
  className?: string;
}

export const ErrorState: React.FC<ErrorStateProps> = ({
  icon = '⚠️',
  title = 'Đã có lỗi xảy ra',
  message,
  retryText = 'Thử lại',
  onRetry,
  className = '',
}) => {
  return (
    <div className={`og-error-state ${className}`} role="alert">
      <div className="og-error-state__icon" aria-hidden="true">
        {icon}
      </div>
      <h3 className="og-error-state__title">{title}</h3>
      <p className="og-error-state__desc">{message}</p>
      {onRetry && (
        <Button variant="outline" size="md" onClick={onRetry}>
          🔄 {retryText}
        </Button>
      )}
    </div>
  );
};
