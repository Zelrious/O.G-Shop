import { Icon } from '../../../shared/components/Icon/Icon';
import { InfoTooltip } from '../../../shared/components';
import React from 'react';

interface RoleSelectorCardsProps {
  value: 'BUYER' | 'SELLER';
  onChange: (value: 'BUYER' | 'SELLER') => void;
  disabled?: boolean;
}

export const RoleSelectorCards: React.FC<RoleSelectorCardsProps> = ({
  value,
  onChange,
  disabled = false,
}) => {
  const isSeller = value === 'SELLER';

  return (
    <div className="og-seller-optin-container" style={{ margin: '14px 0 6px' }}>
      <label
        htmlFor="account-type-seller"
        className={`og-seller-optin-card ${isSeller ? 'og-seller-optin-card--active' : ''}`}
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: '12px',
          padding: '12px 14px',
          background: isSeller ? 'var(--og-color-primary-light, #eaf1ed)' : 'var(--og-color-surface)',
          border: `1.5px solid ${isSeller ? 'var(--og-color-primary, #276347)' : 'var(--og-color-border, #d2d8d5)'}`,
          borderRadius: '10px',
          cursor: disabled ? 'not-allowed' : 'pointer',
          transition: 'all 0.2s ease',
          userSelect: 'none',
        }}
      >
        <input
          id="account-type-seller"
          aria-label="Mua và bán hàng"
          type="checkbox"
          checked={isSeller}
          onChange={(e) => onChange(e.target.checked ? 'SELLER' : 'BUYER')}
          disabled={disabled}
          style={{
            width: '18px',
            height: '18px',
            cursor: disabled ? 'not-allowed' : 'pointer',
            accentColor: 'var(--og-color-primary, #276347)',
            flexShrink: 0,
          }}
        />
        <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '8px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontWeight: 600, fontSize: '0.92rem', color: 'var(--og-color-text-primary)' }}>
            <span style={{ color: 'var(--og-color-primary, #276347)', display: 'inline-flex' }}>
              <Icon name="store" size={17} />
            </span>
            <span>Đăng ký làm Người bán (Mở gian hàng)</span>
            <InfoTooltip
              content={
                <span>
                  Mặc định mọi tài khoản đều có sẵn quyền <strong>Mua hàng</strong>. Đánh dấu vào đây nếu bạn muốn kích hoạt thêm quyền <strong>Đăng bán đồ cũ</strong> và nhận thanh toán an toàn.
                </span>
              }
              ariaLabel="Chỉ dẫn về quyền người bán"
            />
          </div>
        </div>
      </label>
    </div>
  );
};
