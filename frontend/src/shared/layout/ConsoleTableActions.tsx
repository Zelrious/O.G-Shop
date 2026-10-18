import { Button } from '../components/Button/Button';
import React, { useState, useRef, useEffect } from 'react';
import { ConsoleIcons } from './ConsoleIcons';

export interface ActionMenuItem {
  label: string;
  icon?: React.ReactNode;
  onClick: () => void;
  variant?: 'normal' | 'danger';
  disabled?: boolean;
}

export interface TableActionDropdownProps {
  items: ActionMenuItem[];
  align?: 'right' | 'left';
  ariaLabel?: string;
}

export const TableActionDropdown: React.FC<TableActionDropdownProps> = ({
  items,
  align = 'right',
  ariaLabel = 'Tùy chọn thao tác',
}) => {
  const [isOpen, setIsOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    };

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setIsOpen(false);
      }
    };

    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside);
      document.addEventListener('keydown', handleKeyDown);
    }
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [isOpen]);

  return (
    <div ref={containerRef} style={{ position: 'relative', display: 'inline-block' }}>
      <button
        type="button"
        aria-label={ariaLabel}
        aria-haspopup="true"
        aria-expanded={isOpen}
        onClick={(e) => {
          e.stopPropagation();
          setIsOpen(!isOpen);
        }}
        style={{
          width: '32px',
          height: '32px',
          borderRadius: '6px',
          border: '1px solid var(--og-color-border-strong)',
          background: isOpen ? 'var(--og-color-surface-subtle)' : 'var(--og-color-surface)',
          color: 'var(--og-color-text-secondary)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          cursor: 'pointer',
          transition: 'all 0.15s ease',
          padding: 0,
        }}
        onMouseEnter={(e) => {
          if (!isOpen) e.currentTarget.style.background = 'var(--og-color-surface-hover)';
        }}
        onMouseLeave={(e) => {
          if (!isOpen) e.currentTarget.style.background = 'var(--og-color-surface)';
        }}
      >
        <ConsoleIcons.MoreVertical size={16} />
      </button>

      {isOpen && (
        <div
          role="menu"
          style={{
            position: 'absolute',
            top: 'calc(100% + 4px)',
            [align === 'right' ? 'right' : 'left']: 0,
            zIndex: 100,
            minWidth: '180px',
            background: 'var(--og-color-surface)',
            border: '1px solid var(--og-color-border)',
            borderRadius: '8px',
            boxShadow: '0 10px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1)',
            padding: '6px',
            display: 'flex',
            flexDirection: 'column',
            gap: '2px',
          }}
          onClick={(e) => e.stopPropagation()}
        >
          {items.map((item, idx) => (
            <Button key={idx}
              type="button"
              role="menuitem"
              disabled={item.disabled}
              onClick={() => {
                setIsOpen(false);
                item.onClick();
              }}
              variant={item.variant === 'danger' ? 'danger-outline' : 'ghost'}>
              {item.icon && (
                <span
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    color: item.variant === 'danger' ? 'var(--og-color-danger)' : 'var(--og-color-text-secondary)',
                  }}
                >
                  {item.icon}
                </span>
              )}
              <span>{item.label}</span>
            </Button>
          ))}
        </div>
      )}
    </div>
  );
};

export interface BulkActionItem {
  label: string;
  icon?: React.ReactNode;
  onClick: () => void;
  variant?: 'primary' | 'danger' | 'warning' | 'default';
  disabled?: boolean;
}

export interface BulkActionBarProps {
  selectedCount: number;
  totalCount?: number;
  onClearSelection: () => void;
  actions: BulkActionItem[];
}

export const BulkActionBar: React.FC<BulkActionBarProps> = ({
  selectedCount,
  onClearSelection,
  actions,
}) => {
  if (selectedCount === 0) return null;

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        flexWrap: 'wrap',
        gap: '12px',
        padding: '12px 18px',
        background: 'var(--og-color-primary-light)',
        border: '1px solid var(--og-color-border)',
        borderRadius: '10px',
        marginBottom: '16px',
        boxShadow: '0 2px 4px rgba(37, 99, 235, 0.05)',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
        <span
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            minWidth: '24px',
            height: '24px',
            borderRadius: '12px',
            background: 'var(--og-color-primary)',
            color: 'var(--og-color-surface)',
            fontSize: '0.78rem',
            fontWeight: 700,
            padding: '0 6px',
          }}
        >
          {selectedCount}
        </span>
        <span style={{ fontSize: '0.9rem', color: 'var(--og-color-text-primary)', fontWeight: 600 }}>
          Đã chọn <strong>{selectedCount}</strong> hàng
        </span>
        <button
          type="button"
          onClick={onClearSelection}
          style={{
            background: 'none',
            border: 'none',
            color: 'var(--og-color-primary)',
            fontSize: '0.84rem',
            fontWeight: 600,
            cursor: 'pointer',
            textDecoration: 'underline',
            padding: '2px 6px',
          }}
        >
          Bỏ chọn tất cả
        </button>
      </div>

      <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
        {actions.map((action, idx) => {
          return (
            <Button key={idx}
              type="button"
              disabled={action.disabled}
              onClick={action.onClick}
              variant={action.variant === 'primary' ? 'primary' : action.variant === 'danger' ? 'danger-outline' : 'secondary'}>
              {action.icon}
              <span>{action.label}</span>
            </Button>
          );
        })}
      </div>
    </div>
  );
};

export interface TableCheckboxProps {
  checked: boolean;
  indeterminate?: boolean;
  onChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
  ariaLabel?: string;
}

export const TableCheckbox: React.FC<TableCheckboxProps> = ({
  checked,
  indeterminate,
  onChange,
  ariaLabel = 'Chọn hàng',
}) => {
  const checkboxRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (checkboxRef.current) {
      checkboxRef.current.indeterminate = Boolean(indeterminate && !checked);
    }
  }, [indeterminate, checked]);

  return (
    <input
      ref={checkboxRef}
      type="checkbox"
      checked={checked}
      onChange={onChange}
      aria-label={ariaLabel}
      style={{
        width: '16px',
        height: '16px',
        cursor: 'pointer',
        accentColor: 'var(--og-color-primary-fill)',
      }}
    />
  );
};
