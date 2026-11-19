import React, { useState, useEffect } from 'react';
import { authApi } from '../../auth';

export interface SecureAssetImageProps {
  assetId?: number | null;
  alt: string;
  className?: string;
  style?: React.CSSProperties;
  fallbackIcon?: React.ReactNode;
  fallbackText?: string;
  enableZoom?: boolean;
}

export const SecureAssetImage: React.FC<SecureAssetImageProps> = ({
  assetId,
  alt,
  className = '',
  style,
  fallbackIcon,
  fallbackText = 'Chưa có ảnh',
  enableZoom = true,
}) => {
  const [blobUrl, setBlobUrl] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [hasError, setHasError] = useState<boolean>(false);
  const [isZoomed, setIsZoomed] = useState<boolean>(false);

  useEffect(() => {
    if (!assetId) {
      setBlobUrl(null);
      setIsLoading(false);
      setHasError(false);
      return;
    }

    let isMounted = true;
    let createdUrl: string | null = null;

    const loadAsset = async () => {
      setIsLoading(true);
      setHasError(false);
      try {
        const response = await authApi.authorizedFetch(`/ekyc/assets/${assetId}`);
        if (!response.ok) {
          throw new Error(`Tải ảnh thất bại (${response.status})`);
        }
        const blob = await response.blob();
        if (isMounted) {
          createdUrl = URL.createObjectURL(blob);
          setBlobUrl(createdUrl);
        }
      } catch (err: unknown) {
        console.warn(`[SecureAssetImage] Không thể tải asset #${assetId}:`, err);
        if (isMounted) {
          setHasError(true);
        }
      } finally {
        if (isMounted) {
          setIsLoading(false);
        }
      }
    };

    loadAsset();

    return () => {
      isMounted = false;
      if (createdUrl) {
        URL.revokeObjectURL(createdUrl);
      }
    };
  }, [assetId]);

  if (!assetId || hasError) {
    return (
      <div
        className={className}
        style={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          padding: '16px',
          color: 'var(--og-color-text-muted)',
          backgroundColor: 'var(--og-color-surface-subtle)',
          borderRadius: '6px',
          border: '1px dashed var(--og-color-border)',
          minHeight: '120px',
          ...style,
        }}
      >
        {fallbackIcon && <div style={{ marginBottom: '6px' }}>{fallbackIcon}</div>}
        <small style={{ fontSize: '0.8rem' }}>{hasError ? 'Không thể tải ảnh' : fallbackText}</small>
      </div>
    );
  }

  if (isLoading) {
    return (
      <div
        className={className}
        style={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          padding: '16px',
          color: 'var(--og-color-text-muted)',
          backgroundColor: 'var(--og-color-surface-subtle)',
          borderRadius: '6px',
          minHeight: '120px',
          ...style,
        }}
      >
        <span className="og-spinner" style={{ marginBottom: '6px' }} />
        <small style={{ fontSize: '0.8rem' }}>Đang tải ảnh bảo mật...</small>
      </div>
    );
  }

  return (
    <>
      <div style={{ position: 'relative', display: 'inline-block', width: '100%', height: '100%' }}>
        <img
          src={blobUrl || undefined}
          alt={alt}
          className={className}
          style={{
            ...style,
            cursor: enableZoom ? 'zoom-in' : 'default',
            transition: 'transform 0.15s ease',
          }}
          onClick={() => {
            if (enableZoom && blobUrl) {
              setIsZoomed(true);
            }
          }}
          title={enableZoom ? 'Nhấn để phóng to ảnh' : undefined}
        />
      </div>

      {/* Lightbox / Zoom Modal for KTV Inspection */}
      {isZoomed && blobUrl && (
        <div
          role="dialog"
          aria-modal="true"
          style={{
            position: 'fixed',
            inset: 0,
            zIndex: 9999,
            backgroundColor: 'rgba(0, 0, 0, 0.85)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '24px',
            backdropFilter: 'blur(4px)',
          }}
          onClick={() => setIsZoomed(false)}
        >
          <div
            style={{
              position: 'relative',
              maxWidth: '90vw',
              maxHeight: '90vh',
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
            }}
            onClick={(e) => e.stopPropagation()}
          >
            <img
              src={blobUrl}
              alt={alt}
              style={{
                maxWidth: '100%',
                maxHeight: '85vh',
                objectFit: 'contain',
                borderRadius: '8px',
                boxShadow: '0 8px 32px rgba(0,0,0,0.5)',
              }}
            />
            <div
              style={{
                marginTop: '12px',
                color: '#fff',
                fontSize: '0.9rem',
                display: 'flex',
                alignItems: 'center',
                gap: '16px',
              }}
            >
              <span>{alt}</span>
              <button
                type="button"
                onClick={() => setIsZoomed(false)}
                style={{
                  padding: '4px 12px',
                  backgroundColor: 'rgba(255,255,255,0.2)',
                  color: '#fff',
                  border: 'none',
                  borderRadius: '4px',
                  cursor: 'pointer',
                  fontSize: '0.85rem',
                }}
              >
                ✕ Đóng (ESC)
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
};
