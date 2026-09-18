import React, { HTMLAttributes } from 'react';

export interface CardProps extends HTMLAttributes<HTMLDivElement> {
  title?: string;
  subtitle?: string;
  children: React.ReactNode;
}

export const Card: React.FC<CardProps> = ({ title, subtitle, children, className = '', ...props }) => {
  return (
    <div className={`og-card ${className}`} {...props}>
      {(title || subtitle) && (
        <div className="og-card-header">
          {title && <h2 className="og-card-title">{title}</h2>}
          {subtitle && <p className="og-card-subtitle">{subtitle}</p>}
        </div>
      )}
      {children}
    </div>
  );
};
