import { useEffect, useState } from 'react';
import { returnCaseApi, type AdminAssigneeOption, type ReturnCaseDetail } from '../../features/disputes/returnCaseApi';
import { parseCaseEventAction } from '../../features/disputes/caseEventMeta';
import { Button } from '../../shared/components';

const labels: Record<string, string> = {
  OPEN: 'Đã gửi',
  REVIEWING: 'Đang xem xét',
  CLOSED: 'Đã chấp nhận',
  REJECTED: 'Đã từ chối',
};

function CaseReview({ issue, admins, reload }: { issue: ReturnCaseDetail; admins: AdminAssigneeOption[]; reload: () => Promise<void> }) {
  const [reason, setReason] = useState('');
  const [admin, setAdmin] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const latest = issue.events?.filter(e => e.eventType === 'SUPPLEMENTED').at(-1);
  const pending = parseCaseEventAction(latest?.metadata) === 'INFORMATION_REQUEST';
  async function act(action: string) {
    setBusy(true); setError('');
    try {
      if (action === 'receive' || action === 'information') {
        await returnCaseApi.issueProgress('/ktv/system-issues/' + issue.caseId + '/' + action, reason.trim());
      } else if (action === 'escalate') {
        await returnCaseApi.ktvEscalateCase(issue.caseId, Number(admin), reason.trim());
      } else {
        await returnCaseApi.ktvDecideSystemIssue(issue.caseId, action as 'CONFIRM_ISSUE' | 'REJECT_ISSUE', reason.trim());
      }
      setReason(''); await reload();
    } catch (ex) { setError((ex as Error).message); } finally { setBusy(false); }
  }
  return <article className="og-account-detail" aria-label={'Hồ sơ #' + issue.caseId}>
    <h2>Hồ sơ #{issue.caseId} · {labels[issue.state] || issue.state}</h2><p>{issue.fullDescription}</p>
    {issue.evidence?.map(e => <p key={e.evidenceId}>{e.textContent}</p>)}
    {issue.events?.filter(e => e.eventType === 'SUPPLEMENTED').map(e => <p key={e.eventId}>{e.reason}</p>)}
    {issue.decisions?.map((d, i) => <p key={i}>{issue.state === 'REJECTED' ? 'Đã từ chối' : 'Đã chấp nhận'} · {d.reason}</p>)}
    {issue.state === 'OPEN' && <Button disabled={busy} onClick={() => void act('receive')}>Tiếp nhận</Button>}
    {issue.state === 'REVIEWING' && <>
      <label>Nội dung xử lý<textarea value={reason} maxLength={2000} onChange={e => setReason(e.target.value)} /></label>
      {pending && <p>Đang chờ người gửi bổ sung</p>}
      <Button disabled={busy || !!pending || !reason.trim()} onClick={() => void act('information')}>Yêu cầu bổ sung</Button>
      <Button disabled={busy || !!pending || reason.trim().length < 5} onClick={() => void act('CONFIRM_ISSUE')}>Xác nhận sự cố</Button>
      <Button disabled={busy || !!pending || reason.trim().length < 5} onClick={() => void act('REJECT_ISSUE')}>Từ chối</Button>
      <label>Admin tiếp nhận<select value={admin} onChange={e => setAdmin(e.target.value)}><option value="">Chọn Admin</option>
        {admins.map(a => <option key={a.userId} value={a.userId}>{a.fullName}</option>)}</select></label>
      <Button disabled={busy || !admin || reason.trim().length < 5} onClick={() => void act('escalate')}>Chuyển Admin</Button>
    </>}
    {error && <p role="alert">{error}</p>}
  </article>;
}

export function KtvComplaintsPage() {
  const [rows, setRows] = useState<ReturnCaseDetail[]>([]);
  const [admins, setAdmins] = useState<AdminAssigneeOption[]>([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  async function load() {
    setError(''); setLoading(true);
    try { setRows(await returnCaseApi.getKtvSystemIssues()); }
    catch (ex) { setError((ex as Error).message); } finally { setLoading(false); }
  }
  useEffect(() => {
    void load();
    returnCaseApi.getKtvAdmins().then(setAdmins).catch(ex => setError(ex.message));
  }, []);
  return <main><h1>Tiếp nhận &amp; Xử lý khiếu nại</h1>
    <Button disabled={loading} onClick={() => void load()}>Cập nhật danh sách</Button>
    {error && <p role="alert">{error}</p>}
    {loading && <p role="status">Đang tải hồ sơ…</p>}
    {!loading && !error && rows.length === 0 && <p>Chưa có hồ sơ cần xử lý.</p>}
    {rows.map(row => <CaseReview key={row.caseId} issue={row} admins={admins} reload={load} />)}
  </main>;
}
