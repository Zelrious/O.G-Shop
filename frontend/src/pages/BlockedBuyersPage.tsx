import { useEffect, useState, useLayoutEffect, useRef } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../features/auth';
import { Alert } from '../shared/components';
import { ConfirmDialog } from '../shared/components/Dialog/ConfirmDialog';
import { blockScopeLabels, chatApi, type BlockScope, type BlockedBuyer } from '../features/chat-offer/chatApi';
import '../features/chat-offer/chat.css';

const SCOPE_OPTIONS: { value: BlockScope; title: string; description: string }[] = [
  { value: 'CHAT', title: 'Chặn tin nhắn', description: 'Ngăn nhắn tin mới; vẫn cho phép xem/mua hàng và xử lý nghĩa vụ đơn cũ.' },
  { value: 'PURCHASE', title: 'Chặn mua hàng', description: 'Ẩn toàn bộ sản phẩm của shop khỏi người mua này, ngăn trả giá & đặt mua mới; vẫn giữ đơn cũ.' },
  { value: 'ALL', title: 'Chặn cả tin nhắn & mua hàng', description: 'Ẩn sản phẩm của shop và chặn toàn bộ chat, trả giá, đặt mua mới; vẫn giữ nghĩa vụ đơn cũ.' },
];

export function BlockedBuyersPage() {
  const { user } = useAuth();
  const owner = user?.userId ?? 0;
  const [data, setData] = useState<{ owner: number; items: BlockedBuyer[] } | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<{ userId: number; name: string }[]>([]);
  const [searching, setSearching] = useState(false);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const actor = useRef(owner);
  useLayoutEffect(() => { actor.current = owner; }, [owner]);
  const [searched, setSearched] = useState(false);
  const [decision, setDecision] = useState<{ id: number; name: string; blocked: boolean; editing?: boolean } | null>(null);
  const [scope, setScope] = useState<BlockScope>('ALL');
  const [reason, setReason] = useState('');

  useEffect(() => {
    let ignore = false;
    setLoading(true);
    setData(null);
    setResults([]);
    setError(null);
    setDecision(null);
    setSearched(false);
    setSearching(false);
    setBusy(false);
    setNotice(null);
    setQuery('');
    chatApi.blocks()
      .then(items => { if (!ignore) setData({ owner, items }); })
      .catch((e: unknown) => { if (!ignore) setError(e instanceof Error ? e.message : 'Chưa tải được danh sách chặn.'); })
      .finally(() => { if (!ignore) setLoading(false); });
    return () => { ignore = true; };
  }, [owner]);

  const search = async () => {
    if (searching || query.trim().length < 2) return;
    setSearching(true);
    setError(null);
    try {
      const found = await chatApi.searchBuyers(query.trim());
      if (actor.current !== owner) return;
      setResults(found);
      setSearched(true);
    } catch (e: unknown) {
      if (actor.current !== owner) return;
      setError(e instanceof Error ? e.message : 'Chưa tìm được người mua.');
    } finally {
      if (actor.current === owner) setSearching(false);
    }
  };

  const commit = async () => {
    if (!decision || busy) return;
    setBusy(true);
    setError(null);
    try {
      const items = await chatApi.block(decision.id, decision.blocked, reason.trim() || undefined, scope);
      if (actor.current !== owner) return;
      setData({ owner, items });
      setNotice(
        decision.blocked
          ? `Đã cập nhật mức chặn (${blockScopeLabels[scope]}). Hồ sơ và nghĩa vụ đơn cũ vẫn được giữ.`
          : 'Đã bỏ chặn người mua.'
      );
      setDecision(null);
      setReason('');
    } catch (e: unknown) {
      if (actor.current !== owner) return;
      setError(e instanceof Error ? e.message : 'Chưa cập nhật được quan hệ chặn.');
    } finally {
      if (actor.current === owner) setBusy(false);
    }
  };

  const items = data?.owner === owner ? data.items : [];
  if (!user?.roles.includes('SELLER')) {
    return (
      <section className="og-blocks-page">
        <Alert type="warning">Chỉ người bán có quyền quản lý danh sách chặn.</Alert>
        <Link to="/messages">Về tin nhắn</Link>
      </section>
    );
  }

  return (
    <section className="og-blocks-page">
      <header className="og-chat-heading">
        <div>
          <span className="og-eyebrow">QUẢN LÝ TƯƠNG TÁC</span>
          <h1>Danh sách người mua bị chặn</h1>
          <p>Chọn mức chặn tin nhắn, chặn mua hàng hoặc chặn cả hai với từng người mua; lịch sử và nghĩa vụ đơn cũ luôn được giữ.</p>
        </div>
        <Link to="/messages" className="og-button og-button--outline">Về tin nhắn</Link>
      </header>

      {notice && <div role="status"><Alert type="success">{notice}</Alert></div>}
      {error && <div role="alert"><Alert type="danger">{error}</Alert></div>}

      <div className="og-blocks-search">
        <form onSubmit={e => { e.preventDefault(); void search(); }}>
          <input
            aria-label="Tìm người mua theo tên hoặc mã"
            placeholder="Tên hoặc mã người mua (ít nhất 2 ký tự)"
            value={query}
            maxLength={100}
            onChange={e => setQuery(e.target.value)}
          />
          <button className="og-button og-button--outline" disabled={searching || query.trim().length < 2}>
            {searching ? 'Đang tìm...' : 'Tìm người mua'}
          </button>
        </form>
        {searched && !searching && !results.length && <p role="status">Không có người mua phù hợp.</p>}
        {results.length > 0 && (
          <ul className="og-blocks-list">
            {results.map(person => {
              const existing = items.find(item => item.buyerId === person.userId);
              return (
                <li key={person.userId}>
                  <div>
                    <strong>{person.name}</strong>
                    <small>Người mua #{person.userId}</small>
                  </div>
                  <button
                    className="og-button og-button--outline"
                    disabled={busy}
                    onClick={() => {
                      setDecision({ id: person.userId, name: person.name, blocked: true, editing: Boolean(existing) });
                      setScope(existing?.blockScope ?? 'ALL');
                      setReason(existing?.reason ?? '');
                    }}
                  >
                    {existing ? 'Đổi mức chặn' : 'Chặn'}
                  </button>
                </li>
              );
            })}
          </ul>
        )}
      </div>

      {loading ? (
        <p role="status">Đang tải danh sách...</p>
      ) : !items.length ? (
        <div className="og-blocks-search">
          <h2>Chưa có người mua bị chặn</h2>
          <p>Tìm người mua ở trên khi cần quản lý tương tác.</p>
        </div>
      ) : (
        <div className="og-blocks-search">
          <h2>{items.length} người mua đang bị chặn</h2>
          <ul className="og-blocks-list">
            {items.map(item => {
              const currentScope: BlockScope = item.blockScope ?? 'ALL';
              return (
                <li key={item.buyerId}>
                  <div>
                    <div className="og-blocks-item-head">
                      <strong>{item.name}</strong>
                      <span className={`og-block-scope-badge og-block-scope-badge--${currentScope.toLowerCase()}`}>
                        {blockScopeLabels[currentScope]}
                      </span>
                      <span className="og-blocks-item-meta">
                        #{item.buyerId} · {new Date(item.blockedAt).toLocaleString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' })}
                      </span>
                    </div>
                    {item.reason && <p className="og-blocks-item-reason">{item.reason}</p>}
                  </div>
                  <div className="og-blocks-actions">
                    <button
                      className="og-button og-button--outline"
                      disabled={busy}
                      onClick={() => {
                        setDecision({ id: item.buyerId, name: item.name, blocked: true, editing: true });
                        setScope(currentScope);
                        setReason(item.reason ?? '');
                      }}
                    >
                      Đổi mức chặn
                    </button>
                    <button
                      className="og-button og-button--ghost"
                      disabled={busy}
                      onClick={() => {
                        setDecision({ id: item.buyerId, name: item.name, blocked: false });
                        setReason('');
                      }}
                    >
                      Bỏ chặn
                    </button>
                  </div>
                </li>
              );
            })}
          </ul>
        </div>
      )}

      <ConfirmDialog
        isOpen={decision !== null}
        title={decision?.blocked ? (decision.editing ? 'Cập nhật mức chặn người mua' : 'Xác nhận chặn người mua') : 'Xác nhận bỏ chặn'}
        message={
          <>
            <p><strong>{decision?.name}</strong></p>
            {decision?.blocked ? (
              <>
                <div className="og-block-scope-options" role="radiogroup" aria-label="Chọn mức chặn">
                  {SCOPE_OPTIONS.map(opt => (
                    <label key={opt.value} className={'og-block-scope-option' + (scope === opt.value ? ' og-block-scope-option--active' : '')}>
                      <input
                        type="radio"
                        name="block-scope"
                        value={opt.value}
                        checked={scope === opt.value}
                        disabled={busy}
                        onChange={() => setScope(opt.value)}
                      />
                      <span>
                        <strong>{opt.title}</strong>
                        <small>{opt.description}</small>
                      </span>
                    </label>
                  ))}
                </div>
                <label className="og-block-reason-label">
                  Lý do (tùy chọn)
                  <textarea
                    className="og-input"
                    maxLength={500}
                    rows={2}
                    value={reason}
                    onChange={e => setReason(e.target.value)}
                    disabled={busy}
                  />
                </label>
              </>
            ) : (
              <p>Cho phép người mua tiếp tục nhắn tin và mua hàng mới theo điều kiện hiện tại.</p>
            )}
            {error && <Alert type="danger">{error}</Alert>}
          </>
        }
        isLoading={busy}
        confirmText={decision?.blocked ? (decision.editing ? 'Lưu mức chặn' : 'Xác nhận chặn') : 'Bỏ chặn'}
        onConfirm={() => void commit()}
        onCancel={() => { if (!busy) setDecision(null); }}
      />
    </section>
  );
}
