import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { beforeEach, afterEach, describe, it, expect, vi } from 'vitest';
import { AccountCommandDialog } from './AccountCommandDialog';
import { accountApi } from './accountApi';
import { accountFixture } from './accountFixtures.testdata';
vi.mock('./accountApi', async original => { const actual = await original<typeof import('./accountApi')>(); return { ...actual, accountApi: { ...actual.accountApi, command: vi.fn() } }; });
beforeEach(() => { vi.resetAllMocks(); });
afterEach(() => { vi.restoreAllMocks(); });
describe('Account decisions', () => {
  it('sends server version and chosen scope, never an actor or point deduction', async () => {
    const done = vi.fn(); vi.mocked(accountApi.command).mockResolvedValue({ userId: 10, version: 3, action: 'RESTRICT', message: 'Đã lưu' });
    render(<AccountCommandDialog detail={accountFixture} initialAction="RESTRICT" onClose={vi.fn()} onDone={done} />);
    expect(screen.getByRole('button', { name: 'Xác nhận quyết định' })).toBeDisabled();
    fireEvent.click(screen.getByRole('combobox', { name: 'Phạm vi hạn chế' })); fireEvent.click(screen.getByRole('option', { name: 'Mua tin yêu cầu eKYC' }));
    fireEvent.change(screen.getByLabelText('Lý do quyết định'), { target: { value: 'Đã xác minh sự việc' } });
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận quyết định' }));
    await waitFor(() => expect(done).toHaveBeenCalledWith('Đã lưu'));
    const data = vi.mocked(accountApi.command).mock.calls[0][1];
    expect(data).toMatchObject({ expectedVersion: 2, action: 'RESTRICT', scope: 'BUY_EKYC', durationWeeks: 2, reason: 'Đã xác minh sự việc' });
    expect(data).not.toHaveProperty('actorId'); expect(data).not.toHaveProperty('pointsDelta');
  });
  it('keeps command and lock expiry unchanged after an uncertain network result', async () => {
    vi.spyOn(Date, 'now').mockReturnValue(Date.parse('2026-10-12T10:00:00Z'));
    vi.mocked(accountApi.command).mockRejectedValueOnce(new Error('Kết quả chưa xác nhận')).mockResolvedValue({ userId: 10, version: 3, action: 'LOCK', message: 'Đã lưu' });
    render(<AccountCommandDialog detail={accountFixture} initialAction="LOCK" onClose={vi.fn()} onDone={vi.fn()} />);
    fireEvent.change(screen.getByLabelText('Lý do quyết định'), { target: { value: 'Kiểm tra tài khoản' } });
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận quyết định' }));
    await screen.findByText('Kết quả chưa xác nhận');
    const first = vi.mocked(accountApi.command).mock.calls[0];
    vi.mocked(Date.now).mockReturnValue(Date.parse('2026-10-12T10:01:00Z'));
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận quyết định' }));
    await waitFor(() => expect(accountApi.command).toHaveBeenCalledTimes(2));
    expect(vi.mocked(accountApi.command).mock.calls[1]).toEqual(first);
  });
  it('blocks double submission and traps keyboard focus', async () => {
    let finish!: (value: { userId: number; version: number; action: string; message: string }) => void;
    vi.mocked(accountApi.command).mockReturnValue(new Promise(resolve => { finish = resolve; }));
    const close = vi.fn(); render(<AccountCommandDialog detail={accountFixture} initialAction="RESTRICT" onClose={close} onDone={vi.fn()} />);
    const dialog = screen.getByRole('dialog'); dialog.focus(); fireEvent.keyDown(dialog, { key: 'Tab' });
    expect(screen.getByRole('button', { name: 'Đóng quyết định' })).toHaveFocus();
    fireEvent.change(screen.getByLabelText('Lý do quyết định'), { target: { value: 'Đã kiểm tra' } });
    const button = screen.getByRole('button', { name: 'Xác nhận quyết định' }); fireEvent.click(button); fireEvent.click(button);
    expect(accountApi.command).toHaveBeenCalledTimes(1);
    fireEvent.keyDown(dialog, { key: 'Escape' }); expect(close).not.toHaveBeenCalled();
    finish({ userId: 10, version: 3, action: 'RESTRICT', message: 'Đã lưu' });
    await waitFor(() => expect(screen.getByRole('button', { name: 'Xác nhận quyết định' })).toBeEnabled());
  });
  it('binds compensation to the confirmed outage and old case', async () => {
    const detail = { ...accountFixture, cases: [{ caseId: 30, orderId: 9, type: 'RETURN', state: 'POST_RETURN', summary: 'Old case', createdAt: '2026-10-01T10:00:00Z', responseDeadline: '2026-10-14T10:00:00Z', canRespond: true, canCompensate: true }], outages: [{ systemCaseId: 21, affectedFrom: '2026-10-12T08:00:00Z', affectedUntil: '2026-10-12T09:00:00Z' }] };
    vi.mocked(accountApi.command).mockResolvedValue({ userId: 10, version: 3, action: 'COMPENSATE', message: 'Đã lưu' });
    render(<AccountCommandDialog detail={detail} initialAction="COMPENSATE" onClose={vi.fn()} onDone={vi.fn()} />);
    fireEvent.change(screen.getByLabelText('Lý do quyết định'), { target: { value: 'Bù đúng thời gian' } });
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận quyết định' }));
    await waitFor(() => expect(accountApi.command).toHaveBeenCalledTimes(1));
    expect(vi.mocked(accountApi.command).mock.calls[0][1]).toMatchObject({ targetCaseId: 30, systemCaseId: 21, affectedFrom: detail.outages[0].affectedFrom, affectedUntil: detail.outages[0].affectedUntil });
    expect(vi.mocked(accountApi.command).mock.calls[0][1]).not.toHaveProperty('expiresAt');
  });
});
