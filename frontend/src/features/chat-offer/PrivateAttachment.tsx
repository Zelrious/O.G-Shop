import { useEffect, useState } from 'react';
import { chatApi, type ChatMessage } from './chatApi';
import { MediaLightboxModal } from '../../shared/components';

interface Props { message: ChatMessage; userId: number }

export function PrivateAttachment({ message, userId }: Props) {
  const [url, setUrl] = useState<string | null>(null), [error, setError] = useState<string | null>(null), [retry, setRetry] = useState(0);
  const [showLightbox, setShowLightbox] = useState<boolean>(false);

  useEffect(() => {
    let ignore = false, objectUrl: string | null = null; const controller = new AbortController(); setUrl(null); setError(null);
    if (message.media?.available) chatApi.mediaBlob(message.id, controller.signal).then(blob => { if (ignore) return; objectUrl = URL.createObjectURL(blob); setUrl(objectUrl); }).catch((e: unknown) => { if (!ignore) setError(e instanceof Error ? e.message : 'Chưa tải được tệp.'); });
    return () => { ignore = true; controller.abort(); if (objectUrl) URL.revokeObjectURL(objectUrl); };
  }, [message.id, message.media?.available, userId, retry]);

  if (!message.media?.available) return <div className="og-chat-media-expired"><span aria-hidden="true">▷</span><strong>Tệp không còn khả dụng</strong><small>Lịch sử tin nhắn vẫn được giữ.</small></div>;
  if (error) return <div className="og-chat-media-expired" role="alert"><p>{error}</p><button className="og-button og-button--outline" onClick={() => setRetry(n => n + 1)}>Tải lại tệp</button></div>;
  if (!url) return <div className="og-chat-media-expired" role="status">Đang tải tệp riêng tư...</div>;

  return (
    <>
      <div className="og-chat-attachment">
        {message.type === 'VIDEO' ? (
          <div style={{ position: 'relative', display: 'inline-block' }}>
            <video controls preload="metadata" src={url} aria-label="Video trong hội thoại" onError={() => setError('Chưa phát được video. Tải lại hoặc hỏi đối tác.')} />
            <button
              type="button"
              className="og-chat-media-expand-btn"
              onClick={() => setShowLightbox(true)}
              title="Xem video phóng to"
              aria-label="Xem video phóng to"
              style={{
                position: 'absolute',
                top: 8,
                right: 8,
                background: 'rgba(0,0,0,0.65)',
                color: '#fff',
                border: 'none',
                borderRadius: '4px',
                padding: '4px 6px',
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
              }}
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
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
            onClick={() => setShowLightbox(true)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                setShowLightbox(true);
              }
            }}
            aria-label="Mở hình ảnh trong hội thoại"
            style={{ cursor: 'pointer', display: 'inline-block', position: 'relative' }}
          >
            <img src={url} alt="Hình ảnh được gửi trong hội thoại" onError={() => setError('Chưa hiển thị được hình ảnh.')} />
          </div>
        )}
        <small>{(message.media.bytes / 1048576).toFixed(2)} MB{message.media.duration ? ' · ' + message.media.duration.toFixed(1) + ' giây' : ''}</small>
      </div>

      <MediaLightboxModal
        isOpen={showLightbox}
        src={url}
        type={message.type}
        alt="Hình ảnh trong hội thoại"
        caption={message.content}
        onClose={() => setShowLightbox(false)}
      />
    </>
  );
}
