import React, { useState } from 'react';
import type { SellerRegistration } from '../types';
import { Input } from '../../../shared/components';
import { emptyAddress } from '../registrationDraft';
import { CascadingAddressFields } from './CascadingAddressFields';
import { BankSelect } from './BankSelect';

interface SellerRegistrationFieldsProps {
  value: SellerRegistration;
  onChange: (value: SellerRegistration) => void;
  disabled?: boolean;
  defaultFullName?: string;
}

export const SellerRegistrationFields: React.FC<SellerRegistrationFieldsProps> = ({
  value,
  onChange,
  disabled = false,
  defaultFullName = '',
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
          <div className="og-info-banner-title">Về tài khoản người bán đồ cũ</div>
          <div>
            Thông tin dưới đây được sử dụng để tạo hồ sơ người bán và thiết lập kho lấy hàng. Bạn có thể bán đồ cá nhân với tên của mình hoặc đặt tên gian hàng riêng.
          </div>
        </div>
      </div>

      {/* 2. STORE / SELLER INFORMATION SECTION */}
      <div className="og-form-section" style={{ marginTop: 0, paddingTop: 0, borderTop: 0 }}>
        <div className="og-form-section-header">
          <h4 className="og-form-section-title"> Thông tin người bán</h4>
          <p className="og-form-section-desc">
            Tên hiển thị gian hàng (có thể dùng chính họ tên bạn) và số điện thoại liên hệ khi bàn giao hàng.
          </p>
        </div>

        <div className="og-form-grid-2">
          <Input
            label="Tên cửa hàng"
            value={value.storeName}
            onChange={(event) => change('storeName', event.target.value)}
            required
            maxLength={120}
            disabled={disabled}
            placeholder={defaultFullName ? `Mặc định: ${defaultFullName}` : 'Tên gian hàng hoặc Họ tên của bạn'}
            helperText="Là sàn bán đồ cũ C2C, bạn có thể để tên cá nhân hoặc tên gian hàng tùy thích."
          />
          <Input
            label="Số điện thoại liên hệ"
            type="tel"
            value={value.contactPhone}
            onChange={(event) => change('contactPhone', event.target.value)}
            required
            pattern="(0|\+84)[35789][0-9]{8}"
            disabled={disabled}
            placeholder="0912345678"
            helperText="Số điện thoại bưu tá liên hệ khi đến nhận hàng (có thể khác SĐT mua hàng)."
          />
        </div>
      </div>

      {/* 3. PICKUP ADDRESS SECTION (CASCADING COMBOBOXES) */}
      <CascadingAddressFields
        title="Địa chỉ lấy hàng"
        subtitle="Chọn Tỉnh/Thành, Quận/Huyện, Phường/Xã và địa chỉ chi tiết để đơn vị vận chuyển (GHN) đến nhận hàng."
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
        <CascadingAddressFields
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
          <h4 className="og-form-section-title"> Thông tin tài khoản ngân hàng</h4>
          <p className="og-form-section-desc">
            Chọn ngân hàng khả dụng trong hệ thống thanh toán để nhận tiền giải ngân từ Escrow khi đơn hàng thành công.
          </p>
        </div>

        <div className="og-form-grid-2">
          {/* SEARCHABLE BANK COMBOBOX (NAME ON LEFT, LOGO ON RIGHT) */}
          <BankSelect
            label="Ngân hàng"
            value={value.bankName}
            onChange={(selectedBank) => change('bankName', selectedBank)}
            disabled={disabled}
            required
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
