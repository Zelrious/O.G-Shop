import React, { useEffect, useLayoutEffect, useRef, useId } from 'react';
import { Button } from '../Button/Button';

export interface ConfirmDialogProps {
  isOpen: boolean;
  title: string;
  message: string | React.ReactNode;
  confirmText?: string;
  cancelText?: string;
  variant?: 'danger' | 'primary' | 'warning';
  isLoading?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

export const ConfirmDialog: React.FC<ConfirmDialogProps> = ({
  isOpen,
  title,
  message,
  confirmText = 'Xác nhận',
  cancelText = 'Hủy bỏ',
  variant = 'primary',
  isLoading = false,
  onConfirm,
  onCancel,
}) => {
  const dialog = useRef<HTMLDivElement>(null);
  const cancel = useRef(onCancel), loading = useRef(isLoading);
  const titleId = useId();
  useLayoutEffect(() => { cancel.current = onCancel; loading.current = isLoading; }, [onCancel, isLoading]);
  useEffect(() => {
    if (!isOpen) return;
    const previous = document.activeElement as HTMLElement | null;
    const overflow = document.body.style.overflow;
    const focusable = () => Array.from(dialog.current?.querySelectorAll<HTMLElement>('button:not(:disabled), textarea:not(:disabled), input:not(:disabled), a[href], [tabindex="0"]') ?? []);
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !loading.current) {
        cancel.current();
      }
      if (e.key === 'Tab') {
        const items = focusable(), first = items[0], last = items.at(-1);
        if (!first) { e.preventDefault(); dialog.current?.focus(); }
        else if (e.shiftKey && (document.activeElement === first || document.activeElement === dialog.current)) { e.preventDefault(); last?.focus(); }
        else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
      }
    };
    document.body.style.overflow = 'hidden';
    window.addEventListener('keydown', handleKeyDown);
    (focusable()[0] ?? dialog.current)?.focus();
    return () => {
      document.body.style.overflow = overflow;
      window.removeEventListener('keydown', handleKeyDown);
      previous?.focus();
    };
  }, [isOpen]);

  if (!isOpen) return null;

  return (
    <div
      className="og-modal-backdrop"
      role="dialog"
      aria-modal="true"
      aria-labelledby={titleId}
      ref={dialog}
      tabIndex={-1}
      onClick={(e) => {
        if (e.target === e.currentTarget && !isLoading) onCancel();
      }}
    >
      <div className="og-modal-dialog">
        <div className="og-modal-dialog__header">
          <h3 id={titleId} className="og-modal-dialog__title">
            {variant === 'danger' ? '⚠️ ' : ''}
            {title}
          </h3>
        </div>
        <div className="og-modal-dialog__body">
          {typeof message === 'string' ? <p style={{ margin: 0 }}>{message}</p> : message}
        </div>
        <div className="og-modal-dialog__footer">
          <Button variant="ghost" size="sm" onClick={onCancel} disabled={isLoading}>
            {cancelText}
          </Button>
          <Button
            variant={variant === 'danger' ? 'danger' : 'primary'}
            size="sm"
            onClick={onConfirm}
            isLoading={isLoading}
          >
            {confirmText}
          </Button>
        </div>
      </div>
    </div>
  );
};
