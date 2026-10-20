import { Select } from '../../shared/components/Select/Select';
import { useEffect, useRef, useState } from 'react';
import { Alert } from '../../shared/components';
import { accountApi, actionLabels, scopeLabels, type AccountAction, type AccountCommand, type AccountDetail, type AccountScope } from './accountApi';
import '../listing/review.css';
import './accounts.css';
interface Props { detail: AccountDetail; initialAction: AccountAction; onClose: () => void; onDone: (message: string) => void }
export function AccountCommandDialog({ detail, initialAction, onClose, onDone }: Props) {
  const [action, setAction] = useState(initialAction);
  const [reason, setReason] = useState('');
  const [scope, setScope] = useState<AccountScope>(detail.allowedScopes[0]);
  const [weeks, setWeeks] = useState(detail.allowedWeeks.includes(2) ? 2 : detail.allowedWeeks[0]);
  const activeRestrictions = detail.restrictions.filter(r => !r.closedAt && (!r.expiresAt || Date.parse(r.expiresAt) > Date.parse(detail.serverTime)));
  const [restrictionId, setRestrictionId] = useState(activeRestrictions[0]?.id ?? '');
  const [temporary, setTemporary] = useState(true);
  const [caseId, setCaseId] = useState(detail.cases.find(c => c.canCompensate)?.caseId ?? 0);
  const [outageIndex, setOutageIndex] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const dialog = useRef<HTMLDivElement>(null);
  const inFlight = useRef(false);
  const command = useRef<{ signature: string; key: string } | null>(null);
  useEffect(() => {
    const prior = document.activeElement as HTMLElement | null;
    const overflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden'; dialog.current?.focus();
    return () => { document.body.style.overflow = overflow; prior?.focus(); };
  }, []);
  const submit = async () => {
    if (inFlight.current || !reason.trim()) return;
    const data: Omit<AccountCommand, 'commandKey'> = { action, reason: reason.trim(), expectedVersion: detail.user.version };
    if (action === 'RESTRICT') { data.scope = scope; data.durationWeeks = weeks; }
    if (action === 'LIFT') data.restrictionId = restrictionId;
    // The expiry chosen for a lock remains stable across an uncertain retry and is based on serverTime.
    const signature = JSON.stringify({ ...data, expiresAt: action === 'LOCK' ? { temporary, weeks } : undefined });
    if (action === 'LOCK' && temporary) {
      const stored = retryPayload.current;
      if (stored?.signature === signature && stored.data.expiresAt) {
        data.expiresAt = stored.data.expiresAt;
      } else {
        const baseTime = Date.parse(detail.serverTime) || Date.now();
        data.expiresAt = new Date(baseTime + weeks * 7 * 86400000).toISOString();
      }
    }
    if (action === 'COMPENSATE') {
      const outage = detail.outages[outageIndex];
      if (!outage) return;
      data.targetCaseId = caseId; data.systemCaseId = outage.systemCaseId; data.affectedFrom = outage.affectedFrom; data.affectedUntil = outage.affectedUntil;
    }
    if (command.current?.signature !== signature) command.current = { signature, key: crypto.randomUUID() };
    const stored = retryPayload.current;
    if (stored?.signature === signature) Object.assign(data, stored.data);
    else retryPayload.current = { signature, data };
    inFlight.current = true; setBusy(true); setError(null);
    try { const result = await accountApi.command(detail.user.userId, { ...data, commandKey: command.current.key }); onDone(result.message); }
    catch (e: unknown) { setError(e instanceof Error ? e.message : 'Chưa ghi nhận được quyết định. Thử lại với cùng mã.'); }
    finally { inFlight.current = false; setBusy(false); }
  };
  const retryPayload = useRef<{ signature: string; data: Omit<AccountCommand, 'commandKey'> } | null>(null);

  const getActionExplanation = (act: AccountAction) => {
    switch (act) {
      case 'RESTRICT':
        return 'Hạn chế chức năng (RESTRICT): Chỉ giới hạn phạm vi tính năng được chọn (Đăng tin, Mua hàng, Nhắn tin, v.v.). Tài khoản vẫn hoạt động ở các phần khác, vẫn đăng nhập và tiếp tục xử lý các đơn hàng/nghĩa vụ cũ.';
      case 'LOCK':
        return 'Khóa hoạt động mới (LOCK): Chặn toàn bộ giao dịch hoặc hoạt động tạo mới. Tài khoản bị khóa vẫn đăng nhập vào hệ thống để tra cứu và hoàn tất các đơn hàng, khiếu nại hoặc nghĩa vụ cũ còn dang dở.';
      case 'DEACTIVATE':
        return 'Ngừng mềm (DEACTIVATE): Ngừng toàn bộ hoạt động mới. Dữ liệu tài khoản và lịch sử giao dịch được bảo toàn nguyên vẹn để phục vụ nghĩa vụ cũ và đối soát.';
      case 'ACTIVATE':
        return 'Kích hoạt lại (ACTIVATE): Khôi phục trạng thái hoạt động bình thường của tài khoản. Các hạn chế tính năng riêng biệt (nếu có) vẫn được giữ cho đến khi được gỡ bỏ hoặc hết hạn.';
      case 'LIFT':
        return 'Gỡ bỏ hạn chế (LIFT): Hủy bỏ hạn chế chức năng cụ thể đã áp dụng trước đó.';
      case 'COMPENSATE':
        return 'Bù hạn thời gian (COMPENSATE): Chỉ bù đúng khoảng thời gian hệ thống gián đoạn đã xác nhận cho hạn phản hồi hồ sơ khiếu nại. Không thay đổi thời hạn hạn chế tài khoản.';
      default:
        return 'Biện pháp chỉ chặn chức năng tương ứng. Không tự hủy đơn, hoàn tiền hoặc trừ điểm uy tín.';
    }
  };

  return <div className="og-modal-backdrop" onClick={() => { if (!busy) onClose(); }}><div className="og-fee-dialog og-account-dialog" role="dialog" aria-modal="true" aria-labelledby="account-command-title" ref={dialog} tabIndex={-1}
    onClick={e => e.stopPropagation()} onKeyDown={e => {
      if (e.key === 'Escape' && !busy) onClose();
      if (e.key === 'Tab') {
        const nodes = dialog.current?.querySelectorAll<HTMLElement>('button:not(:disabled),input:not(:disabled),textarea:not(:disabled),select:not(:disabled)');
        if (!nodes?.length) { e.preventDefault(); return; }
        const first = nodes[0], last = nodes[nodes.length - 1];
        if (e.shiftKey && (document.activeElement === first || document.activeElement === dialog.current)) { e.preventDefault(); last.focus(); }
        else if (!e.shiftKey && (document.activeElement === last || document.activeElement === dialog.current)) { e.preventDefault(); first.focus(); }
      }
    }}>
    <header><div><span className="og-eyebrow">QUYẾT ĐỊNH TÀI KHOẢN</span><h2 id="account-command-title">Xác nhận biện pháp</h2></div><button className="og-button og-button--ghost" onClick={onClose} disabled={busy} aria-label="Đóng quyết định">×</button></header>
    <p><strong>{detail.user.fullName}</strong> · #{detail.user.userId}</p>
    <form onSubmit={e => { e.preventDefault(); void submit(); }}>
      <label>Biện pháp<Select hideLabel label="Biện pháp" value={String(action)} onChange={value => setAction(value as AccountAction)} options={[...detail.allowedActions.map(a => ({ value: String(a), label: actionLabels[a] }))]} disabled={busy} /></label>
      {action === 'RESTRICT' && <label>Phạm vi hạn chế<Select hideLabel label="Phạm vi hạn chế" value={String(scope)} onChange={value => setScope(value as AccountScope)} options={[...detail.allowedScopes.map(s => ({ value: String(s), label: scopeLabels[s] }))]} disabled={busy} /></label>}
      {action === 'LIFT' && <label>Hạn chế cần gỡ<Select hideLabel label="Hạn chế cần gỡ" value={String(restrictionId)} onChange={value => setRestrictionId(value)} options={[...activeRestrictions.map(r => ({ value: String(r.id), label: `${scopeLabels[r.scope] || r.scope} · ${r.reason}` }))]} disabled={busy} /></label>}
      {action === 'LOCK' && <label className="og-account-check"><input type="checkbox" checked={temporary} onChange={e => setTemporary(e.target.checked)} disabled={busy} />Khóa tạm thời</label>}
      {(action === 'RESTRICT' || action === 'LOCK' && temporary) && <label>Thời hạn<Select hideLabel label="Thời hạn" value={String(weeks)} onChange={value => setWeeks(Number(value))} options={[...detail.allowedWeeks.map(w => ({ value: String(w), label: `${w} tuần` }))]} disabled={busy} /></label>}
      {action === 'COMPENSATE' && <><label>Hồ sơ đơn cũ cần bù hạn<Select hideLabel label="Hồ sơ đơn cũ cần bù hạn" value={String(caseId)} onChange={value => setCaseId(Number(value))} options={[...detail.cases.filter(c => c.canCompensate).map(c => ({ value: String(c.caseId), label: `Hồ sơ #${c.caseId} · Đơn #${c.orderId}` }))]} disabled={busy} /></label>
        <label>Khoảng gián đoạn đã được xác nhận<Select hideLabel label="Khoảng gián đoạn đã được xác nhận" value={String(outageIndex)} onChange={value => setOutageIndex(Number(value))} options={[...detail.outages.map((o, i) => ({ value: String(i), label: `Lỗi #${o.systemCaseId} · ${new Date(o.affectedFrom).toLocaleString('vi-VN')} → ${new Date(o.affectedUntil).toLocaleString('vi-VN')}` }))]} disabled={busy} /></label></>}
      <label>Lý do quyết định<textarea rows={4} maxLength={500} value={reason} onChange={e => setReason(e.target.value)} disabled={busy} placeholder="Nêu căn cứ, phần đã kiểm tra và lý do áp dụng..." required /></label>
      <Alert type="warning">
        <p style={{ margin: 0 }}>{getActionExplanation(action)}</p>
        <p style={{ margin: '6px 0 0', fontSize: '0.82rem' }}>
          Lưu ý: Biện pháp không tự hủy đơn, hoàn tiền hoặc trừ điểm uy tín. Tài khoản bị hạn chế hoặc khóa vẫn đăng nhập và xử lý nghĩa vụ cũ được.
        </p>
      </Alert>
      {error && <div role="alert"><Alert type="danger">{error}</Alert><p>Chưa xác nhận hoàn tất. Nếu dữ liệu đã thay đổi, đóng hộp này và làm mới hồ sơ.</p></div>}
      <footer><button type="button" className="og-button og-button--outline" onClick={onClose} disabled={busy}>Hủy</button><button className="og-button og-button--primary" disabled={busy || !reason.trim()}>{busy ? 'Đang ghi nhận...' : 'Xác nhận quyết định'}</button></footer>
    </form>
  </div></div>;
}
