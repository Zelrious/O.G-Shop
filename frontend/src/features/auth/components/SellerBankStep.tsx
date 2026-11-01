import React from 'react';
import type { SellerRegistration } from '../types';
import { Input, InfoTooltip } from '../../../shared/components';
import { BankSelect } from './BankSelect';

interface SellerBankStepProps {
  value: SellerRegistration;
  onChange: (value: SellerRegistration) => void;
  disabled?: boolean;
}

export const SellerBankStep: React.FC<SellerBankStepProps> = ({
  value,
  onChange,
  disabled = false,
}) => {
  const change = (key: keyof SellerRegistration, text: string) => {
    onChange({ ...value, [key]: text });
  };

  return (
    <div className="og-step-enter">
      <div className="og-form-section-header" style={{ marginBottom: '16px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <h4 className="og-form-section-title" style={{ margin: 0, fontSize: '1rem', fontWeight: 700 }}>
            Tài khoản ngân hàng nhận thanh toán
          </h4>
          <InfoTooltip
            content="Tài khoản này dùng để nhận giải ngân tự động từ hệ thống ký quỹ O.G Shop Escrow khi người mua xác nhận đã nhận hàng thành công."
            ariaLabel="Chỉ dẫn thanh toán người bán"
          />
        </div>
        <p className="og-form-section-desc" style={{ marginTop: '4px', fontSize: '0.85rem' }}>
          Vui lòng chọn ngân hàng có hỗ trợ chuyển khoản nhanh 24/7 và nhập chính xác thông tin thụ hưởng.
        </p>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
        {/* SEARCHABLE BANK COMBOBOX (NAME ON LEFT, LOGO ON RIGHT) */}
        <BankSelect
          label="Ngân hàng thụ hưởng"
          value={value.bankName}
          onChange={(selectedBank) => change('bankName', selectedBank)}
          disabled={disabled}
          required
        />

        <div className="og-form-grid-2">
          <Input
            label="Số tài khoản ngân hàng"
            value={value.accountNumber}
            onChange={(event) => change('accountNumber', event.target.value)}
            inputMode="numeric"
            required
            pattern="[0-9]{6,30}"
            maxLength={30}
            disabled={disabled}
            placeholder="Ví dụ: 1029384756"
            tooltip="Nhập dãy số tài khoản in trên thẻ hoặc ứng dụng ngân hàng (từ 6 đến 30 chữ số)."
          />

          <Input
            label="Tên chủ tài khoản"
            value={value.accountHolder}
            onChange={(event) => change('accountHolder', event.target.value.toUpperCase())}
            required
            maxLength={120}
            disabled={disabled}
            placeholder="Ví dụ: NGUYEN VAN A"
            tooltip="Họ và tên chủ tài khoản viết hoa không dấu, phải trùng khớp với tên đã đăng ký tại ngân hàng."
          />
        </div>
      </div>
    </div>
  );
};
