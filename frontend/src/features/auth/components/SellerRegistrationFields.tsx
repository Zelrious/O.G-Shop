import React, { useState } from 'react';
import type { RegistrationAddress, SellerRegistration } from '../types';
import { Input } from '../../../shared/components';
import { emptyAddress } from '../registrationDraft';

interface AddressFieldsProps {
  title: string;
  subtitle?: string;
  prefix: string;
  value: RegistrationAddress;
  onChange: (value: RegistrationAddress) => void;
  disabled?: boolean;
}

function AddressFields({
  title,
  subtitle,
  prefix,
  value,
  onChange,
  disabled = false,
}: AddressFieldsProps) {
  return (
    <div role="group" aria-label={title} className="og-form-section">
      <div className="og-form-section-header">
        <h4 className="og-form-section-title">📍 {title}</h4>
        {subtitle && <p className="og-form-section-desc">{subtitle}</p>}
      </div>

      <div className="og-form-grid-2">
        <Input
          id={prefix + '-province'}
          label="Tỉnh / Thành phố"
          value={value.province}
          onChange={(event) => onChange({ ...value, province: event.target.value })}
          required
          maxLength={100}
          disabled={disabled}
          placeholder="Ví dụ: TP. Hồ Chí Minh"
        />
        <Input
          id={prefix + '-district'}
          label="Quận / Huyện"
          value={value.district}
          onChange={(event) => onChange({ ...value, district: event.target.value })}
          required
          maxLength={100}
          disabled={disabled}
          placeholder="Ví dụ: Quận 1"
        />
        <Input
          id={prefix + '-ward'}
          label="Phường / Xã"
          value={value.ward}
          onChange={(event) => onChange({ ...value, ward: event.target.value })}
          required
          maxLength={100}
          disabled={disabled}
          placeholder="Ví dụ: Phường Bến Nghé"
        />
        <Input
          id={prefix + '-detail'}
          label="Địa chỉ chi tiết"
          value={value.detailAddress}
          onChange={(event) => onChange({ ...value, detailAddress: event.target.value })}
          required
          maxLength={255}
          disabled={disabled}
          placeholder="Số nhà, tên đường, tòa nhà..."
        />
      </div>
    </div>
  );
}

interface SellerRegistrationFieldsProps {
  value: SellerRegistration;
  onChange: (value: SellerRegistration) => void;
  disabled?: boolean;
}

export const SellerRegistrationFields: React.FC<SellerRegistrationFieldsProps> = ({
  value,
  onChange,
  disabled = false,
}) => {
  const [sameAddress, setSameAddress] = useState(
    () => JSON.stringify(value.pickupAddress) === JSON.stringify(value.returnAddress)
  );

  const change = (key: keyof SellerRegistration, text: string) => {
    onChange({ ...value, [key]: text });
  };

  return (
    <div className="og-step-enter">
      {/* 1. SELLER INFORMATION MESSAGE */}
      <div className="og-info-banner" role="status">
        <span className="og-info-banner-icon" aria-hidden="true">ℹ️</span>
        <div className="og-info-banner-body">
          <div className="og-info-banner-title">Về tài khoản người bán</div>
          <div>
            Thông tin dưới đây được sử dụng để tạo hồ sơ người bán. Bạn cần hoàn tất xác minh eKYC và quy trình xét duyệt trước khi có thể đăng bán sản phẩm.
          </div>
        </div>
      </div>

      {/* 2. STORE INFORMATION SECTION */}
      <div className="og-form-section" style={{ marginTop: 0, paddingTop: 0, borderTop: 0 }}>
        <div className="og-form-section-header">
          <h4 className="og-form-section-title">🏪 Thông tin cửa hàng</h4>
          <p className="og-form-section-desc">Tên gian hàng và số điện thoại hỗ trợ khách hàng của bạn.</p>
        </div>

        <div className="og-form-grid-2">
          <Input
            label="Tên cửa hàng"
            value={value.storeName}
            onChange={(event) => change('storeName', event.target.value)}
            required
            maxLength={120}
            disabled={disabled}
            placeholder="Tên shop hoặc thương hiệu của bạn"
          />
          <Input
            label="Số điện thoại liên hệ cửa hàng"
            type="tel"
            value={value.contactPhone}
            onChange={(event) => change('contactPhone', event.target.value)}
            required
            pattern="(0|\+84)[35789][0-9]{8}"
            disabled={disabled}
            placeholder="0912345678"
          />
        </div>
      </div>

      {/* 3. PICKUP ADDRESS SECTION */}
      <AddressFields
        title="Địa chỉ lấy hàng"
        subtitle="Địa chỉ được sử dụng để đơn vị vận chuyển đến nhận hàng khi có đơn."
        prefix="pickup"
        value={value.pickupAddress}
        disabled={disabled}
        onChange={(address) =>
          onChange({
            ...value,
            pickupAddress: address,
            returnAddress: sameAddress ? address : value.returnAddress,
          })
        }
      />

      {/* RETURN ADDRESS SAME AS PICKUP CHECKBOX */}
      <div style={{ marginTop: '16px', marginBottom: '8px' }}>
        <label
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '8px',
            fontSize: '0.9rem',
            cursor: disabled ? 'not-allowed' : 'pointer',
            userSelect: 'none',
          }}
        >
          <input
            type="checkbox"
            checked={sameAddress}
            disabled={disabled}
            onChange={(event) => {
              const checked = event.target.checked;
              setSameAddress(checked);
              onChange({
                ...value,
                returnAddress: checked ? value.pickupAddress : emptyAddress(),
              });
            }}
          />
          <span>Địa chỉ trả hàng giống địa chỉ lấy hàng</span>
        </label>
      </div>

      {/* 4. RETURN ADDRESS (OPTIONAL IF DIFFERENT) */}
      {!sameAddress && (
        <AddressFields
          title="Địa chỉ trả hàng"
          subtitle="Địa chỉ nhận lại hàng khi người mua trả hàng hoặc giao không thành công."
          prefix="return"
          value={value.returnAddress}
          disabled={disabled}
          onChange={(address) => onChange({ ...value, returnAddress: address })}
        />
      )}

      {/* 5. PAYMENT INFORMATION SECTION */}
      <div className="og-form-section">
        <div className="og-form-section-header">
          <h4 className="og-form-section-title">💳 Thông tin nhận thanh toán</h4>
          <p className="og-form-section-desc">
            Thông tin này được sử dụng để nhận thanh toán từ các giao dịch hoàn tất trên O.G Shop.
          </p>
        </div>

        <div className="og-form-grid-2">
          <Input
            label="Ngân hàng"
            value={value.bankName}
            onChange={(event) => change('bankName', event.target.value)}
            required
            maxLength={100}
            disabled={disabled}
            placeholder="Ví dụ: Vietcombank, MB Bank, Techcombank..."
          />
          <Input
            label="Số tài khoản ngân hàng"
            value={value.accountNumber}
            onChange={(event) => change('accountNumber', event.target.value)}
            inputMode="numeric"
            required
            pattern="[0-9]{6,30}"
            maxLength={30}
            disabled={disabled}
            placeholder="Số tài khoản (6 - 30 chữ số)"
          />
          <div className="og-form-col-full">
            <Input
              label="Tên chủ tài khoản"
              value={value.accountHolder}
              onChange={(event) => change('accountHolder', event.target.value)}
              required
              maxLength={120}
              disabled={disabled}
              placeholder="Họ và tên in hoa không dấu (ví dụ: NGUYEN VAN A)"
            />
          </div>
        </div>
      </div>
    </div>
  );
};
