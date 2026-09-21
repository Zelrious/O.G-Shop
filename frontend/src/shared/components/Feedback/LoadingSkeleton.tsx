import React from 'react';

export interface LoadingSkeletonProps {
  type?: 'text' | 'card' | 'avatar' | 'rect' | 'product-grid';
  count?: number;
  height?: string | number;
  width?: string | number;
  className?: string;
}

export const LoadingSkeleton: React.FC<LoadingSkeletonProps> = ({
  type = 'rect',
  count = 1,
  height,
  width,
  className = '',
}) => {
  if (type === 'product-grid') {
    return (
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))',
          gap: '20px',
        }}
      >
        {Array.from({ length: count || 4 }).map((_, i) => (
          <div
            key={i}
            className="og-card"
            style={{ padding: '16px', display: 'flex', flexDirection: 'column', gap: '12px' }}
          >
            <div className="og-skeleton" style={{ width: '100%', height: '180px', borderRadius: '8px' }} />
            <div className="og-skeleton" style={{ width: '40%', height: '20px' }} />
            <div className="og-skeleton" style={{ width: '85%', height: '24px' }} />
            <div className="og-skeleton" style={{ width: '60%', height: '22px' }} />
          </div>
        ))}
      </div>
    );
  }

  const items = Array.from({ length: count });

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
      {items.map((_, idx) => (
        <div
          key={idx}
          className={`og-skeleton ${className}`}
          style={{
            height: height || (type === 'avatar' ? '48px' : type === 'text' ? '18px' : '60px'),
            width: width || (type === 'avatar' ? '48px' : '100%'),
            borderRadius: type === 'avatar' ? '50%' : 'var(--og-radius-sm)',
          }}
          aria-hidden="true"
        />
      ))}
    </div>
  );
};
