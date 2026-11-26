import { useEffect, useState } from 'react';
import { returnCaseApi, type ReturnCaseDetail } from '../features/disputes/returnCaseApi';
import { parseCaseEventAction } from '../features/disputes/caseEventMeta';
import { Button } from '../shared/components';

const labels: Record<string, string> = { OPEN: 'Đã gửi', REVIEWING: 'Đang xem xét', CLOSED: 'Đã chấp nhận', REJECTED: 'Đã từ chối' };

function Complaint({ issue, onChange }: { issue: ReturnCaseDetail; onChange: (row: ReturnCaseDetail) => void }) {
  const [note, setNote] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const progress = issue.events?.filter(e => e.eventType === 'SUPPLEMENTED').at(-1);
  const requested = parseCaseEventAction(progress?.metadata) === 'INFORMATION_REQUEST';
  return <article className="og-account-detail" aria-label={'Hồ sơ #' + issue.caseId}><h2>Hồ sơ #{issue.caseId} · {labels[issue.state] || issue.state}</h2>
    <p>{issue.fullDescription}</p>
    {issue.evidence?.map(e => <p key={e.evidenceId}>{e.textContent}</p>)}
    {issue.events?.filter(e => e.eventType === 'SUPPLEMENTED').map(e => <p key={e.eventId}>{e.reason}</p>)}
    {issue.decisions?.map((d, i) => <p key={i}>Kết quả xử lý · {d.reason}</p>)}
    {requested && issue.state === 'REVIEWING' && <form onSubmit={async e => {
      e.preventDefault(); setBusy(true); setError('');
      try {
        onChange(await returnCaseApi.issueProgress('/trustsafety/system-issues/' + issue.caseId + '/supplements', note.trim(), progress?.eventId));
        setNote('');
      } catch (err) { setError((err as Error).message); } finally { setBusy(false); }
    }}><label>Nội dung bổ sung<textarea required maxLength={2000} value={note} onChange={e => setNote(e.target.value)} /></label>
      <Button type="submit" disabled={busy}>Gửi bổ sung</Button></form>}
    {error && <p role="alert">{error}</p>}
  </article>;
}

export function SystemComplaintsPage() {
  const [rows, setRows] = useState<ReturnCaseDetail[]>([]);
  const [description, setDescription] = useState('');
  const [source, setSource] = useState('account');
  const [reference, setReference] = useState('');
  const numeric = source === 'orderId' || source === 'productId';
  const [evidence, setEvidence] = useState('');
  const [key, setKey] = useState(() => crypto.randomUUID());
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  async function refresh() {
    setBusy(true); setError('');
    try { setRows(await returnCaseApi.mySystemIssues()); }
    catch (err) { setError((err as Error).message); } finally { setBusy(false); }
  }
  useEffect(() => { void refresh(); }, []);
  function update(row: ReturnCaseDetail) { setRows(old => [row, ...old.filter(c => c.caseId !== row.caseId)]); }
  return <main className="og-account-self"><h1>Khiếu nại hệ thống</h1>
    <form className="og-account-detail" onChange={() => setKey(crypto.randomUUID())} onSubmit={async e => {
      e.preventDefault(); setBusy(true); setError('');
      try { update(await returnCaseApi.createSystemIssue(description.trim(), source === 'account' ? {} : { [source]: numeric ? Number(reference) : reference.trim() }, evidence.trim(), key));
        setDescription(''); setReference(''); setSource('account'); setEvidence(''); setKey(crypto.randomUUID());
      } catch (err) { setError((err as Error).message); } finally { setBusy(false); }
    }}>
      <p>Bạn có thể báo thu trùng phí, đã trả phí nhưng tin chưa mở, hoặc thanh toán đến sau khi hủy đơn. Vui lòng giữ riêng mật khẩu và mã OTP.</p>
      <label>Mô tả sự việc<textarea required minLength={10} maxLength={2000} value={description} onChange={e => setDescription(e.target.value)} /></label>
      <label>Sự việc liên quan đến<select value={source} onChange={e => { setSource(e.target.value); setReference(''); }}>
        <option value="account">Tài khoản</option><option value="orderId">Đơn hàng</option><option value="productId">Tin đăng</option>
        <option value="listingChargeId">Khoản phí đăng tin</option><option value="paymentIntentId">Giao dịch thanh toán</option>
      </select></label>
      {source !== 'account' && <label>Mã tham chiếu<input required type={numeric ? 'number' : 'text'} min={1}
        pattern={numeric ? undefined : '[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}'}
        value={reference} onChange={e => setReference(e.target.value)} /></label>}
      <label>Bằng chứng bằng văn bản<textarea maxLength={2000} value={evidence} onChange={e => setEvidence(e.target.value)} /></label>
      <Button type="submit" disabled={busy}>Gửi khiếu nại</Button>
    </form>
    {error && <p role="alert">{error}</p>}
    <h2>Hồ sơ của bạn</h2><Button disabled={busy} onClick={() => void refresh()}>Cập nhật hồ sơ</Button>
    {rows.map(issue => <Complaint key={issue.caseId} issue={issue} onChange={update} />)}
  </main>;
}
