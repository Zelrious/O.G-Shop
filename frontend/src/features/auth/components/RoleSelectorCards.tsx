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
  return (
    <fieldset className="og-role-fieldset">
      <legend className="og-label" style={{ marginBottom: '8px' }}>
        Bạn muốn sử dụng tài khoản để <span style={{ color: 'var(--og-color-danger)' }}>*</span>
      </legend>

      <div className="og-role-grid" role="radiogroup" aria-label="Loại tài khoản">
        {/* BUYER CARD */}
        <div
          className={`og-role-card ${value === 'BUYER' ? 'og-role-card--selected' : ''}`}
          onClick={() => !disabled && onChange('BUYER')}
          tabIndex={disabled ? -1 : 0}
          onKeyDown={(e) => {
            if (!disabled && (e.key === ' ' || e.key === 'Enter')) {
              e.preventDefault();
              onChange('BUYER');
            }
          }}
        >
          <div className="og-role-card-header">
            <span className="og-role-card-icon" role="img" aria-label="Giỏ hàng">
              🛒
            </span>
            <div className="og-role-card-check" aria-hidden="true">
              {value === 'BUYER' && (
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
                  <polyline points="20 6 9 17 4 12" />
                </svg>
              )}
            </div>
          </div>
          <label
            htmlFor="account-type-buyer"
            className="og-role-card-title"
            style={{ cursor: 'pointer', display: 'block' }}
          >
            <input
              id="account-type-buyer"
              type="radio"
              name="accountType"
              value="BUYER"
              checked={value === 'BUYER'}
              onChange={() => onChange('BUYER')}
              disabled={disabled}
              style={{ position: 'absolute', opacity: 0, width: 0, height: 0, pointerEvents: 'none' }}
            />
            Mua hàng
          </label>
          <p className="og-role-card-desc">Tìm kiếm và mua sản phẩm second-hand chất lượng cao.</p>
        </div>

        {/* SELLER CARD */}
        <div
          className={`og-role-card ${value === 'SELLER' ? 'og-role-card--selected' : ''}`}
          onClick={() => !disabled && onChange('SELLER')}
          tabIndex={disabled ? -1 : 0}
          onKeyDown={(e) => {
            if (!disabled && (e.key === ' ' || e.key === 'Enter')) {
              e.preventDefault();
              onChange('SELLER');
            }
          }}
        >
          <div className="og-role-card-header">
            <span className="og-role-card-icon" role="img" aria-label="Cửa hàng">
              🏪
            </span>
            <div className="og-role-card-check" aria-hidden="true">
              {value === 'SELLER' && (
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
                  <polyline points="20 6 9 17 4 12" />
                </svg>
              )}
            </div>
          </div>
          <label
            htmlFor="account-type-seller"
            className="og-role-card-title"
            style={{ cursor: 'pointer', display: 'block' }}
          >
            <input
              id="account-type-seller"
              type="radio"
              name="accountType"
              value="SELLER"
              checked={value === 'SELLER'}
              onChange={() => onChange('SELLER')}
              disabled={disabled}
              style={{ position: 'absolute', opacity: 0, width: 0, height: 0, pointerEvents: 'none' }}
            />
            Mua và bán hàng
          </label>
          <p className="og-role-card-desc">Mua sản phẩm và mở gian hàng riêng để thanh lý, kinh doanh.</p>
        </div>
      </div>
    </fieldset>
  );
};
