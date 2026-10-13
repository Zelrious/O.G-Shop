import { useEffect, useRef, useState } from 'react';
import { accountApi, type AccountCase, type CaseResponse } from './accountApi';
import { Alert } from '../../shared/components';
export function CaseResponsePanel({ caseInfo, userId }: { caseInfo: AccountCase; userId: number }) {
  const [rows, setRows] = useState<CaseResponse[] | null>(null), [loading, setLoading] = useState(true), [refresh, setRefresh] = useState(0);
  const [text, setText] = useState(''), [busy, setBusy] = useState(false), [error, setError] = useState<string | null>(null), [success, setSuccess] = useState(false);
  const command = useRef<{ text: string; key: string } | null>(null), inFlight = useRef(false);
  useEffect(() => {
    let ignore = false; setLoading(true); setError(null);
    accountApi.responses(caseInfo.caseId).then(value => { if (!ignore) setRows(value); }).catch((e: unknown) => { if (!ignore) setError(e instanceof Error ? e.message : 'Chưa tải được phản hồi.'); }).finally(() => { if (!ignore) setLoading(false); });
    return () => { ignore = true; };
  }, [caseInfo.caseId, refresh]);
  const send = async () => {
    if (inFlight.current || !text.trim()) return;
    const value = text.trim(); if (command.current?.text !== value) command.current = { text: value, key: crypto.randomUUID() };
    inFlight.current = true; setBusy(true); setError(null); setSuccess(false);
    try { await accountApi.respond(caseInfo.caseId, value, command.current.key); setText(''); command.current = null; setSuccess(true); setRefresh(n => n + 1); }
    catch (e: unknown) { setError(e instanceof Error ? e.message : 'Chưa ghi nhận phản hồi, bạn có thể thử lại.'); }
    finally { inFlight.current = false; setBusy(false); }
  };
  return <section className="og-account-section og-account-response"><h3>Phản hồi hồ sơ #{caseInfo.caseId}</h3><p>{caseInfo.summary}</p>
    {loading ? <p role="status">Đang tải phản hồi...</p> : rows?.length ? <ol className="og-account-timeline">{rows.map(r => <li key={r.id}><strong>{r.actorId === userId ? 'Bạn' : `Bên liên quan #${r.actorId}`}</strong><span>{new Date(r.createdAt).toLocaleString('vi-VN')}</span><p>{r.text}</p></li>)}</ol> : rows && <p>Chưa có phản hồi được ghi nhận.</p>}
    {error && <div role="alert"><Alert type="danger">{error}</Alert><button className="og-button og-button--outline" disabled={loading || busy} onClick={() => setRefresh(n => n + 1)}>Tải lại phản hồi</button></div>}
    {success && <div role="status"><Alert type="success">Phản hồi đã được lưu vào hồ sơ.</Alert></div>}
    {caseInfo.canRespond ? <form onSubmit={e => { e.preventDefault(); void send(); }}><label>Phản hồi chính thức<textarea rows={4} maxLength={2000} value={text} onChange={e => setText(e.target.value)} disabled={busy} placeholder="Nêu thông tin liên quan đến hồ sơ này..." /></label><p>Phản hồi được lưu trong hồ sơ đơn cũ, không tự xác nhận hoàn tiền hoặc giải quyết tranh chấp.</p><button className="og-button og-button--primary" disabled={busy || loading || !text.trim()}>{busy ? 'Đang gửi...' : 'Gửi phản hồi'}</button></form> : <p>Hồ sơ đã kết thúc hoặc đã hết hạn phản hồi. Bạn vẫn xem được lịch sử.</p>}
  </section>;
}
