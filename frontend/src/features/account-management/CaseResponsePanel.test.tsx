import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { beforeEach, describe, it, expect, vi } from 'vitest';
import { CaseResponsePanel } from './CaseResponsePanel';
import { accountApi, type AccountCase } from './accountApi';
vi.mock('./accountApi', async original => { const actual = await original<typeof import('./accountApi')>(); return { ...actual, accountApi: { ...actual.accountApi, responses: vi.fn(), respond: vi.fn() } }; });
const caseInfo: AccountCase = { caseId: 3, orderId: 8, type: 'RETURN', state: 'POST_RETURN', summary: 'Hồ sơ cũ', createdAt: '2026-10-01T10:00:00Z', responseDeadline: null, canRespond: true, canCompensate: false };
beforeEach(() => { vi.resetAllMocks();vi.mocked(accountApi.responses).mockResolvedValue([]); });
describe('Old official case responses', () => {
  it('keeps text/key on retry and reports success only after acknowledgement', async () => {
    vi.mocked(accountApi.respond).mockRejectedValueOnce(new Error('Chưa ghi được')).mockResolvedValue({ id: 'test-id' });
    render(<CaseResponsePanel caseInfo={caseInfo} userId={10} />);
    await screen.findByText('Chưa có phản hồi được ghi nhận.');
    fireEvent.change(screen.getByLabelText('Phản hồi chính thức'), { target: { value: 'Tôi phản hồi về đơn cũ' } });
    fireEvent.click(screen.getByRole('button', { name: 'Gửi phản hồi' }));
    await screen.findByText('Chưa ghi được');expect(screen.queryByText('Phản hồi đã được lưu vào hồ sơ.')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Gửi phản hồi' }));
    await screen.findByText('Phản hồi đã được lưu vào hồ sơ.');
    expect(vi.mocked(accountApi.respond).mock.calls[1]).toEqual(vi.mocked(accountApi.respond).mock.calls[0]);
    await waitFor(() => expect(screen.getByLabelText('Phản hồi chính thức')).toHaveValue(''));
  });
  it('keeps closed case history readable and removes the new response action', async () => {
    render(<CaseResponsePanel caseInfo={{ ...caseInfo, state: 'CLOSED', canRespond: false }} userId={10} />);
    await screen.findByText('Chưa có phản hồi được ghi nhận.');expect(screen.queryByRole('button', { name: 'Gửi phản hồi' })).not.toBeInTheDocument();
  });
});
