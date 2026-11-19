import { Icon } from '../Icon/Icon';
import { InfoTooltip } from '../InfoTooltip/InfoTooltip';
import { InputHTMLAttributes, forwardRef } from 'react';

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
  helperText?: string;
  tooltip?: string;
}

export const Input = forwardRef<HTMLInputElement, InputProps>(
  ({ label, error, helperText, tooltip, id, className = '', ...props }, ref) => {
    const inputId = id || (label ? label.toLowerCase().replace(/\s+/g, '-') : undefined);

    return (
      <div className="og-form-group">
        {label && (
          <div className="og-label-row" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '6px' }}>
            <label htmlFor={inputId} className="og-label" style={{ marginBottom: 0 }}>
              {label}
              {props.required && <span style={{ color: 'var(--og-color-danger)' }}> *</span>}
            </label>
            {tooltip && <InfoTooltip content={tooltip} ariaLabel="Xem chỉ dẫn" />}
          </div>
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
            <span><Icon name="alert" style={{ width: '1em', height: '1em' }} /></span> {error}
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
