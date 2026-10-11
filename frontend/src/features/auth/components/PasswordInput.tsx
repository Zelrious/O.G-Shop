import { useState, forwardRef, InputHTMLAttributes } from 'react';

export interface PasswordInputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
  helperText?: string;
  toggleAriaLabel?: string;
}

export const PasswordInput = forwardRef<HTMLInputElement, PasswordInputProps>(
  ({ label, error, helperText, toggleAriaLabel, id, className = '', ...props }, ref) => {
    const [showPassword, setShowPassword] = useState(false);
    const inputId = id || (label ? label.toLowerCase().replace(/\s+/g, '-') : undefined);

    return (
      <div className="og-form-group">
        {label && (
          <label htmlFor={inputId} className="og-label">
            {label}
            {props.required && <span style={{ color: 'var(--og-color-danger)' }}> *</span>}
          </label>
        )}
        <div className="og-password-container">
          <input
            ref={ref}
            id={inputId}
            type={showPassword ? 'text' : 'password'}
            className={`og-input ${error ? 'og-input--error' : ''} ${className}`}
            aria-invalid={Boolean(error)}
            aria-describedby={error ? `${inputId}-error` : helperText ? `${inputId}-helper` : undefined}
            {...props}
          />
          <button
            type="button"
            className="og-password-toggle-btn"
            onClick={() => setShowPassword((prev) => !prev)}
            aria-label={toggleAriaLabel || (showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu')}
            tabIndex={0}
          >
            {showPassword ? (
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24" />
                <line x1="1" y1="1" x2="23" y2="23" />
              </svg>
            ) : (
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
                <circle cx="12" cy="12" r="3" />
              </svg>
            )}
          </button>
        </div>
        {error && (
          <div id={`${inputId}-error`} className="og-error-msg" role="alert">
            <span>⚠️</span> {error}
          </div>
        )}
        {!error && helperText && (
          <div id={`${inputId}-helper`} className="og-helper-text">
            {helperText}
          </div>
        )}
      </div>
    );
  }
);

PasswordInput.displayName = 'PasswordInput';
