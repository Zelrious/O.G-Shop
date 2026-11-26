import React, { useEffect, useState } from 'react';
import { authApi } from '../auth';
import { resolveReturnMediaUrl } from './mediaUrl';
import { extractAuthorizedFetchPath, isPrivateEvidenceKey } from './privateEvidenceUrl';

function useResolvedEvidenceSrc(objectKey?: string | null): { src: string | null; loading: boolean; error: boolean } {
  const isPrivate = isPrivateEvidenceKey(objectKey);
  const [blobUrl, setBlobUrl] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);

  useEffect(() => {
    if (!objectKey || !isPrivate) {
      setBlobUrl(null);
      setLoading(false);
      setError(false);
      return;
    }
    let active = true;
    let created: string | null = null;
    setLoading(true);
    setError(false);

    authApi.authorizedFetch(extractAuthorizedFetchPath(objectKey))
      .then(async (res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const blob = await res.blob();
        if (active) {
          created = URL.createObjectURL(blob);
          setBlobUrl(created);
        }
      })
      .catch(() => {
        if (active) setError(true);
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
      if (created) URL.revokeObjectURL(created);
    };
  }, [objectKey, isPrivate]);

  if (!objectKey) return { src: null, loading: false, error: false };
  if (!isPrivate) return { src: resolveReturnMediaUrl(objectKey), loading: false, error: false };
  return { src: blobUrl, loading, error };
}

export const PrivateEvidenceLink: React.FC<{ objectKey: string; label: string }> = ({ objectKey, label }) => {
  const { src, loading, error } = useResolvedEvidenceSrc(objectKey);
  if (error) return <span role="note">Không tải được tệp bằng chứng.</span>;
  if (loading || !src) return <span>Đang tải liên kết bằng chứng...</span>;
  return <a href={src} target="_blank" rel="noreferrer">{label}</a>;
};

export const PrivateEvidenceImage: React.FC<{
  objectKey: string;
  alt: string;
  style?: React.CSSProperties;
}> = ({ objectKey, alt, style }) => {
  const { src, loading, error } = useResolvedEvidenceSrc(objectKey);
  if (error || loading || !src) {
    return (
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '0.7rem', color: '#64748b', background: '#f1f5f9', ...style }}>
        {error ? 'Lỗi' : '...'}
      </div>
    );
  }
  return <img src={src} alt={alt} style={style} />;
};

export const PrivateEvidenceVideo: React.FC<{
  objectKey: string;
  style?: React.CSSProperties;
}> = ({ objectKey, style }) => {
  const { src, error } = useResolvedEvidenceSrc(objectKey);
  if (error || !src) {
    return (
      <div style={{ padding: 12, color: '#cbd5e1', fontSize: '0.82rem', textAlign: 'center' }}>
        {error ? 'Không thể phát video bằng chứng.' : 'Đang tải video bằng chứng...'}
      </div>
    );
  }
  return <video src={src} controls style={style} />;
};

