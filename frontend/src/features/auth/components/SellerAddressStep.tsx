import React, { useState } from 'react';
import type { SellerRegistration } from '../types';
import { Input } from '../../../shared/components';
import { emptyAddress } from '../registrationDraft';
import { CascadingAddressFields } from './CascadingAddressFields';

interface SellerAddressStepProps {
  value: SellerRegistration;
  onChange: (value: SellerRegistration) => void;
  disabled?: boolean;
  defaultFullName?: string;
}

export const SellerAddressStep: React.FC<SellerAddressStepProps> = ({
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
      {/* STORE & CONTACT PHONE ROW */}
      <div className="og-form-grid-2" style={{ marginBottom: '14px' }}>
        <Input
          label="Tên cửa hàng"
          value={value.storeName}
          onChange={(event) => change('storeName', event.target.value)}
          required
          maxLength={120}
          disabled={disabled}
          placeholder={defaultFullName ? `Mặc định: ${defaultFullName}` : 'Tên gian hàng hoặc Họ tên của bạn'}
          tooltip="Đối với bán đồ cũ cá nhân, bạn có thể để nguyên Họ tên của mình hoặc đặt biệt hiệu gian hàng."
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
          tooltip="Số điện thoại bưu tá liên hệ khi đến nhận hàng (có thể khác với SĐT tài khoản mua hàng)."
        />
      </div>

      {/* PICKUP ADDRESS SECTION (CASCADING COMBOBOXES) */}
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
      <div style={{ marginTop: '12px', marginBottom: '8px' }}>
        <label
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '8px',
            fontSize: '0.88rem',
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

      {/* OPTIONAL RETURN ADDRESS IF DIFFERENT */}
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
    </div>
  );
};
