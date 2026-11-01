import React, { useState, useRef, useEffect, useMemo } from 'react';
import { VIETNAMESE_BANKS, BankOption } from '../data/vietnameseBanks';

interface BankSelectProps {
  id?: string;
  label?: string;
  value: string;
  onChange: (bankName: string) => void;
  disabled?: boolean;
  required?: boolean;
  placeholder?: string;
}

export const BankSelect: React.FC<BankSelectProps> = ({
  id = 'seller-bank-select',
  label = 'Ngân hàng nhận thanh toán',
  value,
  onChange,
  disabled = false,
  required = true,
  placeholder = 'Chọn hoặc tìm kiếm ngân hàng...',
}) => {
  const [isOpen, setIsOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const wrapperRef = useRef<HTMLDivElement>(null);

  // Close dropdown on click outside
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (wrapperRef.current && !wrapperRef.current.contains(e.target as Node)) {
        setIsOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const selectedBank = useMemo(() => {
    if (!value) return null;
    return (
      VIETNAMESE_BANKS.find(
        (b) =>
          b.shortName.toLowerCase() === value.toLowerCase() ||
          b.code.toLowerCase() === value.toLowerCase() ||
          b.name.toLowerCase() === value.toLowerCase()
      ) || null
    );
  }, [value]);

  const filteredBanks = useMemo(() => {
    if (!searchQuery.trim()) return VIETNAMESE_BANKS;
    const q = searchQuery.toLowerCase().trim();
    return VIETNAMESE_BANKS.filter(
      (b) =>
        b.shortName.toLowerCase().includes(q) ||
        b.code.toLowerCase().includes(q) ||
        b.name.toLowerCase().includes(q)
    );
  }, [searchQuery]);

  const handleSelectBank = (bank: BankOption) => {
    onChange(bank.shortName);
    setSearchQuery('');
    setIsOpen(false);
  };

  return (
    <div className="og-bank-combobox-group" ref={wrapperRef} style={{ position: 'relative' }}>
      <label htmlFor={id} className="og-label" style={{ display: 'block', marginBottom: '6px' }}>
        {label} {required && <span style={{ color: 'var(--og-color-danger)' }}>*</span>}
      </label>

      {/* Main Trigger / Search Input */}
      <div
        className="og-bank-combobox-trigger"
        style={{
          position: 'relative',
          display: 'flex',
          alignItems: 'center',
          background: 'var(--og-color-surface, #ffffff)',
          border: '1.5px solid var(--og-color-border, #cbd5e1)',
          borderRadius: '10px',
          padding: '0 14px',
          minHeight: '46px',
          boxSizing: 'border-box',
          cursor: disabled ? 'not-allowed' : 'pointer',
          opacity: disabled ? 0.6 : 1,
          transition: 'border-color 0.2s ease, box-shadow 0.2s ease',
          boxShadow: isOpen ? '0 0 0 3px rgba(59, 130, 246, 0.12)' : 'none',
          borderColor: isOpen ? 'var(--og-color-primary, #3b82f6)' : 'var(--og-color-border, #cbd5e1)',
        }}
        onClick={() => !disabled && setIsOpen((prev) => !prev)}
      >
        <input
          id={id}
          type="text"
          value={isOpen ? searchQuery : value || ''}
          onChange={(e) => {
            setSearchQuery(e.target.value);
            onChange(e.target.value);
            if (!isOpen) setIsOpen(true);
          }}
          onFocus={() => {
            if (!disabled) {
              setSearchQuery('');
              setIsOpen(true);
            }
          }}
          disabled={disabled}
          required={required && !value}
          placeholder={selectedBank ? `${selectedBank.shortName} - ${selectedBank.name}` : placeholder}
          className="og-bank-search-input"
          style={{
            flex: 1,
            border: 'none',
            outline: 'none',
            background: 'transparent',
            font: 'inherit',
            fontSize: '0.94rem',
            color: 'var(--og-color-text-primary, #0f172a)',
            padding: '10px 0',
          }}
          aria-expanded={isOpen}
          aria-haspopup="listbox"
          autoComplete="off"
        />

        {/* Selected Logo Preview or Dropdown Chevron */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginLeft: '10px', flexShrink: 0 }}>
          {selectedBank && (
            <img
              src={selectedBank.logo}
              alt={selectedBank.shortName}
              style={{
                height: '22px',
                maxWidth: '75px',
                objectFit: 'contain',
                display: 'block',
              }}
              onError={(e) => {
                e.currentTarget.style.display = 'none';
              }}
            />
          )}
          <svg
            width="16"
            height="16"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
            style={{
              color: 'var(--og-color-text-secondary, #64748b)',
              transform: isOpen ? 'rotate(180deg)' : 'none',
              transition: 'transform 0.2s ease',
              flexShrink: 0,
              marginLeft: '2px',
            }}
          >
            <polyline points="6 9 12 15 18 9" />
          </svg>
        </div>
      </div>

      {/* Dropdown Options with Bank Name on Left & Logo on Right */}
      {isOpen && !disabled && (
        <div
          role="listbox"
          aria-label="Danh sách ngân hàng khả dụng"
          className="og-bank-combobox-dropdown"
          style={{
            position: 'absolute',
            top: 'calc(100% + 6px)',
            left: 0,
            right: 0,
            zIndex: 1050,
            maxHeight: '270px',
            overflowY: 'auto',
            background: 'var(--og-color-surface, #ffffff)',
            border: '1px solid var(--og-color-border, #e2e8f0)',
            borderRadius: '12px',
            boxShadow: '0 14px 30px -4px rgba(0, 0, 0, 0.2)',
            padding: '8px',
          }}
        >
          {filteredBanks.length === 0 ? (
            <div
              style={{
                padding: '14px 16px',
                fontSize: '0.88rem',
                color: 'var(--og-color-text-secondary, #64748b)',
                textAlign: 'center',
              }}
            >
              Không tìm thấy ngân hàng nào khớp &ldquo;{searchQuery}&rdquo;. Bạn vẫn có thể giữ tên đã nhập.
            </div>
          ) : (
            filteredBanks.map((bank) => {
              const isSelected = selectedBank?.code === bank.code || value === bank.shortName;
              return (
                <div
                  key={bank.code}
                  role="option"
                  aria-selected={isSelected}
                  onClick={(e) => {
                    e.stopPropagation();
                    handleSelectBank(bank);
                  }}
                  className="og-bank-option-row"
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    padding: '10px 14px',
                    borderRadius: '8px',
                    cursor: 'pointer',
                    gap: '14px',
                    background: isSelected ? 'rgba(59, 130, 246, 0.08)' : 'transparent',
                    transition: 'background 0.15s ease',
                  }}
                  onMouseEnter={(e) => {
                    if (!isSelected) {
                      (e.currentTarget as HTMLElement).style.background =
                        'var(--og-color-surface-hover, rgba(0,0,0,0.04))';
                    }
                  }}
                  onMouseLeave={(e) => {
                    if (!isSelected) {
                      (e.currentTarget as HTMLElement).style.background = 'transparent';
                    }
                  }}
                >
                  {/* Left: Tên ngân hàng */}
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '2px', minWidth: 0, flex: 1 }}>
                    <div style={{ fontWeight: 600, fontSize: '0.92rem', color: 'var(--og-color-text-primary, #0f172a)' }}>
                      {bank.shortName}
                    </div>
                    <div
                      style={{
                        fontSize: '0.78rem',
                        color: 'var(--og-color-text-secondary, #64748b)',
                        whiteSpace: 'nowrap',
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                      }}
                    >
                      {bank.name}
                    </div>
                  </div>

                  {/* Right: Logo ngân hàng (không viền bọc, phù hợp với màu nền sáng/tối) */}
                  <div
                    style={{
                      flexShrink: 0,
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      marginRight: '6px',
                    }}
                  >
                    <img
                      src={bank.logo}
                      alt={bank.shortName}
                      style={{
                        height: '22px',
                        maxWidth: '75px',
                        objectFit: 'contain',
                        display: 'block',
                      }}
                      onError={(e) => {
                        e.currentTarget.style.display = 'none';
                      }}
                    />
                  </div>
                </div>
              );
            })
          )}
        </div>
      )}
    </div>
  );
};
