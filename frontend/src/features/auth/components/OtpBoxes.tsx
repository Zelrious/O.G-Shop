import React, { useRef, useState, useEffect } from 'react';

export interface OtpBoxesProps {
  id?: string;
  label?: string;
  value: string;
  onChange: (value: string) => void;
  length?: number;
  disabled?: boolean;
  autoFocus?: boolean;
  error?: string;
}

export const OtpBoxes: React.FC<OtpBoxesProps> = ({
  id = 'otp-input',
  label = 'Mã OTP',
  value,
  onChange,
  length = 6,
  disabled = false,
  autoFocus = true,
  error,
}) => {
  const inputRef = useRef<HTMLInputElement>(null);
  const [isFocused, setIsFocused] = useState(false);

  useEffect(() => {
    if (autoFocus && !disabled) {
      inputRef.current?.focus();
    }
  }, [autoFocus, disabled]);

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const rawVal = e.target.value;
    const cleanedDigits = rawVal.replace(/\D/g, '').slice(0, length);
    onChange(cleanedDigits);
  };

  return (
    <div className={`og-otp-field ${error ? 'og-otp-field--error' : ''}`}>
      {label && (
        <label htmlFor={id} className="og-input-label og-otp-label">
          {label}
        </label>
      )}

      <div
        className="og-otp-container"
        onClick={() => inputRef.current?.focus()}
      >
        {/* Real accessible input backing the OTP slots */}
        <input
          ref={inputRef}
          id={id}
          type="text"
          inputMode="numeric"
          autoComplete="one-time-code"
          pattern="[0-9]*"
          maxLength={length}
          value={value}
          disabled={disabled}
          onChange={handleInputChange}
          onFocus={() => setIsFocused(true)}
          onBlur={() => setIsFocused(false)}
          className="og-otp-hidden-input"
          aria-label={label}
        />

        {/* Separated visual digit boxes */}
        <div className="og-otp-boxes-row" aria-hidden="true">
          {Array.from({ length }).map((_, index) => {
            const char = value[index] || '';
            const isCurrentSlot =
              isFocused &&
              !disabled &&
              (index === value.length || (index === length - 1 && value.length === length));

            return (
              <div
                key={index}
                className={`og-otp-box ${char ? 'og-otp-box--filled' : ''} ${
                  isCurrentSlot ? 'og-otp-box--active' : ''
                }`}
              >
                {char ? (
                  <span className="og-otp-char">{char}</span>
                ) : isCurrentSlot ? (
                  <span className="og-otp-caret" />
                ) : null}
              </div>
            );
          })}
        </div>
      </div>

      {error && <p className="og-field-error">{error}</p>}
    </div>
  );
};
