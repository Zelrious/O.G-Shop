import React, { useEffect, useState, useRef, useCallback } from 'react';
import { createPortal } from 'react-dom';

export interface MediaLightboxModalProps {
  isOpen: boolean;
  src: string | null;
  type?: 'IMAGE' | 'VIDEO' | string;
  alt?: string;
  caption?: string;
  onClose: () => void;
}

export const MediaLightboxModal: React.FC<MediaLightboxModalProps> = ({
  isOpen,
  src,
  type = 'IMAGE',
  alt = 'Tệp đa phương tiện',
  caption,
  onClose,
}) => {
  const [zoom, setZoom] = useState<number>(1);
  const [pan, setPan] = useState<{ x: number; y: number }>({ x: 0, y: 0 });
  const [isDragging, setIsDragging] = useState<boolean>(false);
  const dragStartRef = useRef<{ x: number; y: number }>({ x: 0, y: 0 });
  const hasMovedRef = useRef<boolean>(false);

  // Reset zoom and pan when modal opens or src changes
  useEffect(() => {
    if (isOpen) {
      setZoom(1);
      setPan({ x: 0, y: 0 });
      setIsDragging(false);
    }
  }, [isOpen, src]);

  // Handle ESC key to close modal & +, -, 0 keys to zoom
  useEffect(() => {
    if (!isOpen) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.preventDefault();
        onClose();
      } else if (e.key === '+' || e.key === '=') {
        e.preventDefault();
        setZoom((z) => Math.min(4, +(z + 0.25).toFixed(2)));
      } else if (e.key === '-' || e.key === '_') {
        e.preventDefault();
        setZoom((z) => Math.max(0.5, +(z - 0.25).toFixed(2)));
      } else if (e.key === '0') {
        e.preventDefault();
        setZoom(1);
        setPan({ x: 0, y: 0 });
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, onClose]);

  // Handle Zoom In / Out / Reset
  const handleZoomIn = () => setZoom((z) => Math.min(4, +(z + 0.25).toFixed(2)));
  const handleZoomOut = () => setZoom((z) => Math.max(0.5, +(z - 0.25).toFixed(2)));
  const handleResetZoom = () => {
    setZoom(1);
    setPan({ x: 0, y: 0 });
  };

  // Toggle Zoom on click (if not dragging)
  const handleToggleZoom = () => {
    if (hasMovedRef.current) return;
    if (zoom === 1) {
      setZoom(2);
    } else {
      setZoom(1);
      setPan({ x: 0, y: 0 });
    }
  };

  // Mouse wheel to zoom smoothly
  const handleWheel = (e: React.WheelEvent) => {
    e.stopPropagation();
    if (e.deltaY < 0) {
      handleZoomIn();
    } else {
      handleZoomOut();
    }
  };

  // Drag-to-pan handlers
  const handleMouseDown = (e: React.MouseEvent) => {
    if (zoom <= 1) return;
    setIsDragging(true);
    hasMovedRef.current = false;
    dragStartRef.current = { x: e.clientX - pan.x, y: e.clientY - pan.y };
  };

  const handleMouseMove = useCallback(
    (e: React.MouseEvent) => {
      if (!isDragging || zoom <= 1) return;
      hasMovedRef.current = true;
      setPan({
        x: e.clientX - dragStartRef.current.x,
        y: e.clientY - dragStartRef.current.y,
      });
    },
    [isDragging, zoom]
  );

  const handleMouseUp = () => {
    setIsDragging(false);
  };

  if (!isOpen || !src) return null;

  const isVideo = type === 'VIDEO' || src.endsWith('.mp4');

  return createPortal(
    <div
      role="dialog"
      aria-modal="true"
      aria-label="Xem tệp đa phương tiện"
      className="og-media-lightbox-overlay"
      style={{
        position: 'fixed',
        inset: 0,
        zIndex: 99999,
        backgroundColor: 'rgba(0, 0, 0, 0.88)',
        backdropFilter: 'blur(6px)',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        overflow: 'hidden',
        userSelect: 'none',
      }}
      onClick={(e) => {
        // Close on clicking backdrop
        if (e.target === e.currentTarget) {
          onClose();
        }
      }}
    >
      {/* Top Floating Control Bar */}
      <div
        className="og-media-lightbox-toolbar"
        style={{
          position: 'absolute',
          top: 16,
          left: '50%',
          transform: 'translateX(-50%)',
          zIndex: 100000,
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          padding: '6px 16px',
          backgroundColor: 'rgba(20, 20, 20, 0.85)',
          borderRadius: 24,
          border: '1px solid rgba(255, 255, 255, 0.15)',
          boxShadow: '0 4px 20px rgba(0, 0, 0, 0.4)',
          color: '#ffffff',
          fontSize: '0.85rem',
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Zoom Controls (Available for both image & video) */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          <button
            type="button"
            onClick={handleZoomOut}
            disabled={zoom <= 0.5}
            style={{
              background: 'transparent',
              border: 'none',
              color: zoom <= 0.5 ? 'rgba(255, 255, 255, 0.3)' : '#ffffff',
              cursor: zoom <= 0.5 ? 'not-allowed' : 'pointer',
              padding: '4px 8px',
              borderRadius: 4,
              display: 'flex',
              alignItems: 'center',
            }}
            title="Thu nhỏ (-)"
            aria-label="Thu nhỏ"
          >
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
              <line x1="5" y1="12" x2="19" y2="12" />
            </svg>
          </button>

          <button
            type="button"
            onClick={handleResetZoom}
            style={{
              background: 'rgba(255, 255, 255, 0.12)',
              border: '1px solid rgba(255, 255, 255, 0.2)',
              color: '#ffffff',
              cursor: 'pointer',
              padding: '2px 10px',
              borderRadius: 12,
              fontSize: '0.8rem',
              fontWeight: 600,
              minWidth: 54,
              textAlign: 'center',
            }}
            title="Đặt lại tỉ lệ 100% (Phím 0)"
            aria-label="Đặt lại kích thước"
          >
            {Math.round(zoom * 100)}%
          </button>

          <button
            type="button"
            onClick={handleZoomIn}
            disabled={zoom >= 4}
            style={{
              background: 'transparent',
              border: 'none',
              color: zoom >= 4 ? 'rgba(255, 255, 255, 0.3)' : '#ffffff',
              cursor: zoom >= 4 ? 'not-allowed' : 'pointer',
              padding: '4px 8px',
              borderRadius: 4,
              display: 'flex',
              alignItems: 'center',
            }}
            title="Phóng to (+)"
            aria-label="Phóng to"
          >
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
              <line x1="12" y1="5" x2="12" y2="19" />
              <line x1="5" y1="12" x2="19" y2="12" />
            </svg>
          </button>
        </div>

        <div style={{ width: 1, height: 16, backgroundColor: 'rgba(255, 255, 255, 0.2)' }} />

        {/* Close Button in toolbar */}
        <button
          type="button"
          onClick={onClose}
          style={{
            background: 'transparent',
            border: 'none',
            color: '#ffffff',
            cursor: 'pointer',
            padding: '4px 8px',
            borderRadius: 4,
            display: 'flex',
            alignItems: 'center',
            gap: 4,
            fontSize: '0.82rem',
          }}
          title="Đóng cửa sổ xem (Esc)"
          aria-label="Đóng cửa sổ xem"
        >
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
            <line x1="18" y1="6" x2="6" y2="18" />
            <line x1="6" y1="6" x2="18" y2="18" />
          </svg>
          <span>Đóng (Esc)</span>
        </button>
      </div>

      {/* Top Right Quick Close Button */}
      <button
        type="button"
        onClick={onClose}
        style={{
          position: 'absolute',
          top: 16,
          right: 20,
          zIndex: 100000,
          width: 38,
          height: 38,
          borderRadius: '50%',
          backgroundColor: 'rgba(30, 30, 30, 0.8)',
          border: '1px solid rgba(255, 255, 255, 0.2)',
          color: '#ffffff',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          cursor: 'pointer',
          boxShadow: '0 4px 12px rgba(0, 0, 0, 0.3)',
          transition: 'background-color 0.15s ease',
        }}
        title="Đóng (Esc)"
        aria-label="Đóng"
      >
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
          <line x1="18" y1="6" x2="6" y2="18" />
          <line x1="6" y1="6" x2="18" y2="18" />
        </svg>
      </button>

      {/* Media Canvas Area */}
      <div
        className="og-media-lightbox-canvas"
        style={{
          position: 'relative',
          width: '100vw',
          height: '100vh',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          cursor: isVideo ? 'default' : zoom > 1 ? (isDragging ? 'grabbing' : 'grab') : 'zoom-in',
        }}
        onWheel={handleWheel}
        onMouseDown={handleMouseDown}
        onMouseMove={handleMouseMove}
        onMouseUp={handleMouseUp}
        onClick={(e) => {
          if (e.target === e.currentTarget) {
            onClose();
          }
        }}
      >
        {isVideo ? (
          <div
            style={{
              transform: `translate(${pan.x}px, ${pan.y}px) scale(${zoom})`,
              transition: isDragging ? 'none' : 'transform 0.15s ease-out',
              maxWidth: '90vw',
              maxHeight: '85vh',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
            onClick={(e) => e.stopPropagation()}
          >
            <video
              src={src}
              controls
              autoPlay
              playsInline
              preload="metadata"
              style={{
                maxWidth: '90vw',
                maxHeight: '82vh',
                borderRadius: 8,
                boxShadow: '0 8px 32px rgba(0,0,0,0.6)',
              }}
              aria-label={alt}
            />
          </div>
        ) : (
          <img
            src={src}
            alt={alt}
            draggable={false}
            onClick={(e) => {
              e.stopPropagation();
              handleToggleZoom();
            }}
            style={{
              maxWidth: '90vw',
              maxHeight: '82vh',
              objectFit: 'contain',
              borderRadius: 6,
              boxShadow: '0 8px 32px rgba(0,0,0,0.6)',
              transform: `translate(${pan.x}px, ${pan.y}px) scale(${zoom})`,
              transition: isDragging ? 'none' : 'transform 0.15s ease-out',
              cursor: zoom > 1 ? (isDragging ? 'grabbing' : 'grab') : 'zoom-in',
            }}
          />
        )}
      </div>

      {/* Bottom Caption / Title Bar (if caption or alt provided) */}
      {(caption || alt) && (
        <div
          className="og-media-lightbox-caption"
          style={{
            position: 'absolute',
            bottom: 20,
            left: '50%',
            transform: 'translateX(-50%)',
            zIndex: 100000,
            maxWidth: '80vw',
            padding: '8px 18px',
            backgroundColor: 'rgba(20, 20, 20, 0.85)',
            borderRadius: 20,
            border: '1px solid rgba(255, 255, 255, 0.12)',
            color: '#e2e8f0',
            fontSize: '0.85rem',
            textAlign: 'center',
            pointerEvents: 'none',
            whiteSpace: 'nowrap',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
          }}
        >
          {caption || alt}
        </div>
      )}
    </div>,
    document.body
  );
};

