import React, { useState, useRef, useEffect, useId } from 'react';
import './InfoTooltip.css';

export interface InfoTooltipProps {
  content: React.ReactNode;
  ariaLabel?: string;
  position?: 'top' | 'bottom' | 'right' | 'left';
  className?: string;
}

export const InfoTooltip: React.FC<InfoTooltipProps> = ({
  content,
  ariaLabel = 'Xem chỉ dẫn',
  position = 'top',
  className = '',
}) => {
  const [isOpen, setIsOpen] = useState(false);
  const tooltipId = useId();
  const triggerRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && isOpen) {
        setIsOpen(false);
        triggerRef.current?.focus();
      }
    };
    if (isOpen) {
      window.addEventListener('keydown', handleKeyDown);
    }
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen]);

  return (
    <span className={`og-info-tooltip-wrapper ${className}`}>
      <button
        ref={triggerRef}
        type="button"
        className="og-info-tooltip-trigger"
        aria-label={ariaLabel}
        aria-describedby={isOpen ? tooltipId : undefined}
        onMouseEnter={() => setIsOpen(true)}
        onMouseLeave={() => setIsOpen(false)}
        onFocus={() => setIsOpen(true)}
        onBlur={() => setIsOpen(false)}
        onClick={(e) => {
          e.preventDefault();
          e.stopPropagation();
          setIsOpen((prev) => !prev);
        }}
      >
        <svg
          width="14"
          height="14"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2.2"
          strokeLinecap="round"
          strokeLinejoin="round"
          aria-hidden="true"
        >
          <circle cx="12" cy="12" r="10" />
          <line x1="12" y1="16" x2="12" y2="12" />
          <line x1="12" y1="8" x2="12.01" y2="8" />
        </svg>
      </button>

      {isOpen && (
        <span
          id={tooltipId}
          role="tooltip"
          className={`og-info-tooltip-bubble og-info-tooltip-bubble--${position}`}
          onMouseEnter={() => setIsOpen(true)}
          onMouseLeave={() => setIsOpen(false)}
        >
          <span className="og-info-tooltip-content">{content}</span>
          <span className="og-info-tooltip-arrow" aria-hidden="true" />
        </span>
      )}
    </span>
  );
};
