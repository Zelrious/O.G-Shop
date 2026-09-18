import { InputHTMLAttributes, forwardRef } from 'react';

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
  helperText?: string;
}

export const Input = forwardRef<HTMLInputElement, InputProps>(
  ({ label, error, helperText, id, className = '', ...props }, ref) => {
    const inputId = id || (label ? label.toLowerCase().replace(/\s+/g, '-') : undefined);

    return (
      <div className="og-form-group">
        {label && (
          <label htmlFor={inputId} className="og-label">
            {label}
            {props.required && <span style={{ color: 'var(--og-color-danger)' }}> *</span>}
          </label>
        )}
        <input
          ref={ref}
          id={inputId}
          className={`og-input ${error ? 'og-input--error' : ''} ${className}`}
          aria-invalid={Boolean(error)}
          aria-describedby={error ? `${inputId}-error` : helperText ? `${inputId}-helper` : undefined}
          {...props}
        />
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

Input.displayName = 'Input';
