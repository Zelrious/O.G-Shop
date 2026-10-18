import { Icon, type IconName } from '../Icon/Icon';
import React, { HTMLAttributes } from 'react';

export type AlertType = 'info' | 'success' | 'warning' | 'danger';

export interface AlertProps extends HTMLAttributes<HTMLDivElement> {
  type?: AlertType;
  children: React.ReactNode;
}

const ICONS: Record<AlertType, IconName> = {
  info: 'info',
  success: 'check',
  warning: 'alert',
  danger: 'alert',
};

export const Alert: React.FC<AlertProps> = ({ type = 'info', children, className = '', ...props }) => {
  return (
    <div className={`og-alert og-alert--${type} ${className}`} role="alert" {...props}>
      <span aria-hidden="true"><Icon name={ICONS[type]} size={18} /></span>
      <div style={{ flex: 1 }}>{children}</div>
    </div>
  );
};
