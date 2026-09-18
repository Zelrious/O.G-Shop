import React, { HTMLAttributes } from 'react';

export type BadgeVariant = 'pending' | 'verified' | 'rejected' | 'seller' | 'buyer' | 'neutral';

export interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  variant?: BadgeVariant;
  children: React.ReactNode;
}

export const Badge: React.FC<BadgeProps> = ({ variant = 'neutral', children, className = '', ...props }) => {
  return (
    <span className={`og-badge og-badge--${variant} ${className}`} {...props}>
      {variant === 'verified' || variant === 'seller' ? '✓ ' : ''}
      {children}
    </span>
  );
};
