import { useEffect, useState } from 'react';
import { chatApi, type ChatMessage } from './chatApi';
interface Props { message: ChatMessage; userId: number }
export function PrivateAttachment({ message, userId }: Props) {
  const [url, setUrl] = useState<string | null>(null), [error, setError] = useState<string | null>(null), [retry, setRetry] = useState(0);
  useEffect(() => {
    let ignore = false, objectUrl: string | null = null; const controller = new AbortController(); setUrl(null); setError(null);
    if (message.media?.available) chatApi.mediaBlob(message.id, controller.signal).then(blob => { if (ignore) return; objectUrl = URL.createObjectURL(blob); setUrl(objectUrl); }).catch((e: unknown) => { if (!ignore) setError(e instanceof Error ? e.message : 'Chưa tải được tệp.'); });
    return () => { ignore = true; controller.abort(); if (objectUrl) URL.revokeObjectURL(objectUrl); };
  }, [message.id, message.media?.available, userId, retry]);
  if (!message.media?.available) return <div className="og-chat-media-expired"><span aria-hidden="true">◷</span><strong>Tệp không còn khả dụng</strong><small>Lịch sử tin nhắn vẫn được giữ.</small></div>;
  if (error) return <div className="og-chat-media-expired" role="alert"><p>{error}</p><button className="og-button og-button--outline" onClick={() => setRetry(n => n + 1)}>Tải lại tệp</button></div>;
  if (!url) return <div className="og-chat-media-expired" role="status">Đang tải tệp riêng tư...</div>;
  return <div className="og-chat-attachment">{message.type === 'VIDEO' ? <video controls preload="metadata" src={url} aria-label="Video trong hội thoại" onError={() => setError('Chưa phát được video. Tải lại hoặc hỏi đối tác.')} /> : <a href={url} target="_blank" rel="noopener noreferrer" aria-label="Mở hình ảnh trong hội thoại"><img src={url} alt="Hình ảnh được gửi trong hội thoại" onError={() => setError('Chưa hiển thị được hình ảnh.')} /></a>}<small>{(message.media.bytes / 1048576).toFixed(2)} MB{message.media.duration ? ' · ' + message.media.duration.toFixed(1) + ' giây' : ''}</small></div>;
}
