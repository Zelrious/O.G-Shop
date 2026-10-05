import { beforeEach, describe, expect, it, vi } from 'vitest';

beforeEach(() => { localStorage.clear(); vi.resetModules(); });
const store = async () => (await import('./managementStore')).managementStore;

describe('Management operation guards', () => {
  it('rejects partial refunds without changing a dispute or creating an audit', async () => {
    const s = await store();
    const before = s.getDisputes();
    const auditCount = s.getAuditLogs().length;
    expect(() => s.resolveDispute(before[0].id, 'PARTIAL_REFUND' as never, 'test')).toThrow();
    expect(s.getDisputes()).toEqual(before);
    expect(s.getAuditLogs()).toHaveLength(auditCount);
  });
  it('records return approval separately from an actual refund', async () => {
    const s = await store();
    const dispute = s.getDisputes().find(d => d.status === 'OPEN')!;
    const escrow = s.getEscrows();
    s.resolveDispute(dispute.id, 'FULL_REFUND', 'Goods must be returned');
    expect(s.getDisputes().find(d => d.id === dispute.id)?.status).toBe('RETURN_APPROVED');
    expect(s.getEscrows()).toEqual(escrow);
    expect(() => s.resolveDispute(dispute.id, 'RELEASE_SELLER', 'Repeated')).toThrow();
  });
  it('creates unique operation and audit IDs even at the same timestamp', async () => {
    const s = await store();
    vi.spyOn(Date, 'now').mockReturnValue(1777777777777);
    for (let i = 0; i < 101; i++) s.sendBroadcast({ title: `Test ${i}`, content: 'test', targetAudience: 'ALL', priority: 'NORMAL' });
    const ids = s.getBroadcasts().map(v => v.id);
    const auditIds = s.getAuditLogs().map(v => v.id);
    expect(new Set(ids).size).toBe(ids.length);
    expect(new Set(auditIds).size).toBe(auditIds.length);
    vi.restoreAllMocks();
  });
  it('rejects expired and missing vouchers without success audits', async () => {
    const s = await store();
    const audits = s.getAuditLogs().length;
    const invalid = s.getVouchers().filter(v => v.status !== 'ACTIVE');
    expect(invalid.length).toBeGreaterThan(0);
    for (const voucher of invalid) expect(() => s.grantVoucher(s.getNormalUsers()[0].id, voucher.id, 'test')).toThrow();
    expect(() => s.grantVoucher(s.getNormalUsers()[0].id, 'missing', 'test')).toThrow();
    expect(s.getAuditLogs()).toHaveLength(audits);
  });
  it('cannot revoke a used or missing grant', async () => {
    const s = await store();
    const before = s.getGrantedVouchers();
    const audits = s.getAuditLogs().length;
    const used = before.find(g => g.status === 'USED')!;
    expect(() => s.revokeVoucher(used.id, 'test')).toThrow();
    expect(() => s.revokeVoucher('missing', 'test')).toThrow();
    expect(s.getGrantedVouchers()).toEqual(before);
    expect(s.getAuditLogs()).toHaveLength(audits);
  });
});
