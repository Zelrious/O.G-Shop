import React, { useEffect, useState } from 'react';
import { chatModerationApi } from './chatModerationApi';

export interface KtvChatAttachmentProps {
  messageId: number;
  type: 'IMAGE' | 'VIDEO' | string;
  caption?: string;
  onOpenLightbox: (src: string, type: string, caption?: string) => void;
}

export const KtvChatAttachment: React.FC<KtvChatAttachmentProps> = ({
  messageId,
  type,
  caption,
  onOpenLightbox,
}) => {
  const [url, setUrl] = useState<string | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [retryCount, setRetryCount] = useState<number>(0);

  useEffect(() => {
    let ignore = false;
    let objectUrl: string | null = null;
    const controller = new AbortController();

    setLoading(true);
    setError(null);
    setUrl(null);

    chatModerationApi
      .getMessageMediaBlob(messageId, controller.signal)
      .then((blob) => {
        if (ignore) return;
        objectUrl = URL.createObjectURL(blob);
        setUrl(objectUrl);
        setLoading(false);
      })
      .catch((err: unknown) => {
        if (ignore) return;
        setLoading(false);
        setError(err instanceof Error ? err.message : 'Chưa tải được tệp.');
      });

    return () => {
      ignore = true;
      controller.abort();
      if (objectUrl) {
        URL.revokeObjectURL(objectUrl);
      }
    };
  }, [messageId, retryCount]);

  if (loading) {
    return (
      <div className="og-chat-media-loading" style={{
        display: 'flex',
        alignItems: 'center',
        gap: 8,
        padding: '12px 14px',
        backgroundColor: 'rgba(0, 0, 0, 0.15)',
        borderRadius: 8,
        fontSize: '0.82rem',
        color: 'var(--og-color-text-secondary)',
        minWidth: 160,
        marginBottom: caption ? 6 : 0,
      }}>
        <svg
          width="16"
          height="16"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
          style={{ animation: 'spin 1s linear infinite' }}
          aria-hidden="true"
        >
          <path d="M21 12a9 9 0 1 1-6.219-8.56" />
        </svg>
        <span>Đang tải {type === 'VIDEO' ? 'video' : 'hình ảnh'}...</span>
      </div>
    );
  }

  if (error || !url) {
    return (
      <div className="og-chat-media-error" style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 6,
        padding: '10px 12px',
        backgroundColor: 'rgba(239, 68, 68, 0.08)',
        border: '1px dashed var(--og-color-border, #e2e8f0)',
        borderRadius: 8,
        fontSize: '0.8rem',
        color: 'var(--og-color-text-secondary)',
        marginBottom: caption ? 6 : 0,
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, color: 'var(--og-color-danger, #ef4444)' }}>
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <circle cx="12" cy="12" r="10" />
            <line x1="12" y1="8" x2="12" y2="12" />
            <line x1="12" y1="16" x2="12.01" y2="16" />
          </svg>
          <span>Không thể tải {type === 'VIDEO' ? 'video' : 'ảnh'} #{messageId}</span>
        </div>
        <button
          type="button"
          onClick={() => setRetryCount((c) => c + 1)}
          style={{
            alignSelf: 'flex-start',
            padding: '2px 8px',
            fontSize: '0.75rem',
            backgroundColor: 'transparent',
            border: '1px solid var(--og-color-border)',
            borderRadius: 4,
            cursor: 'pointer',
            color: 'var(--og-color-text-primary)',
          }}
        >
          Thử lại
        </button>
      </div>
    );
  }

  return (
    <div className="og-chat-media-container" style={{ marginBottom: caption ? 6 : 0 }}>
      {type === 'VIDEO' ? (
        <div style={{ position: 'relative', display: 'inline-block' }}>
          <video
            src={url}
            controls
            preload="metadata"
            style={{
              maxWidth: '100%',
              maxHeight: 240,
              display: 'block',
              borderRadius: 8,
              backgroundColor: '#000',
            }}
            aria-label={`Video tin nhắn #${messageId}`}
          />
          <button
            type="button"
            onClick={() => onOpenLightbox(url, 'VIDEO', caption)}
            title="Xem video toàn màn hình / phóng to"
            aria-label="Xem video phóng to"
            style={{
              position: 'absolute',
              top: 8,
              right: 8,
              backgroundColor: 'rgba(0, 0, 0, 0.65)',
              border: '1px solid rgba(255, 255, 255, 0.2)',
              color: '#ffffff',
              borderRadius: 4,
              padding: '4px 6px',
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
            }}
          >
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
              <polyline points="15 3 21 3 21 9" />
              <polyline points="9 21 3 21 3 15" />
              <line x1="21" y1="3" x2="14" y2="10" />
              <line x1="3" y1="21" x2="10" y2="14" />
            </svg>
          </button>
        </div>
      ) : (
        <div
          role="button"
          tabIndex={0}
          className="og-chat-media-thumb-wrapper"
          onClick={() => onOpenLightbox(url, 'IMAGE', caption)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' || e.key === ' ') {
              e.preventDefault();
              onOpenLightbox(url, 'IMAGE', caption);
            }
          }}
          title="Nhấn để phóng to ảnh (không mở tab mới)"
          aria-label={`Xem ảnh #${messageId}`}
          style={{
            position: 'relative',
            display: 'inline-block',
            cursor: 'zoom-in',
            borderRadius: 8,
            overflow: 'hidden',
          }}
        >
          <img
            src={url}
            alt={caption || `Hình ảnh #${messageId}`}
            style={{
              maxWidth: '100%',
              maxHeight: 240,
              display: 'block',
              borderRadius: 8,
              objectFit: 'cover',
              transition: 'transform 0.15s ease',
            }}
          />
          {/* Subtle zoom hint overlay on hover */}
          <div
            className="og-chat-media-hover-hint"
            style={{
              position: 'absolute',
              inset: 0,
              backgroundColor: 'rgba(0, 0, 0, 0.25)',
              opacity: 0,
              transition: 'opacity 0.15s ease',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: '#ffffff',
            }}
          >
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="11" cy="11" r="8" />
              <line x1="21" y1="21" x2="16.65" y2="16.65" />
              <line x1="11" y1="8" x2="11" y2="14" />
              <line x1="8" y1="11" x2="14" y2="11" />
            </svg>
          </div>
        </div>
      )}
    </div>
  );
};

