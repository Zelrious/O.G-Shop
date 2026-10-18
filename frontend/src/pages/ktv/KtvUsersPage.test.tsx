import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, it, expect, vi } from 'vitest';
import { KtvUsersPage } from './KtvUsersPage';
import { accountApi } from '../../features/account-management/accountApi';
import { accountFixture } from '../../features/account-management/accountFixtures.testdata';
vi.mock('../../features/account-management/accountApi', async original => { const actual = await original<typeof import('../../features/account-management/accountApi')>(); return { ...actual, accountApi: { ...actual.accountApi, list: vi.fn(), detail: vi.fn(), history: vi.fn(), command: vi.fn() } }; });
beforeEach(() => {
  vi.resetAllMocks();vi.mocked(accountApi.list).mockResolvedValue({ items: [accountFixture.user], page: 0, size: 20, totalElements: 1 });
  vi.mocked(accountApi.detail).mockResolvedValue(accountFixture);vi.mocked(accountApi.history).mockResolvedValue({ items: [], page: 0, size: 20, totalElements: 0 });
});
describe('KTV account management', () => {
  it('renders actual profile and asks for a decision without applying one implicitly', async () => {
    render(<MemoryRouter><KtvUsersPage /></MemoryRouter>);
    await screen.findByRole('heading', { name: 'Mai Anh' });
    expect(screen.getByText(/điểm bắt đầu 100/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Quyết định biện pháp' }));
    expect(screen.getByRole('dialog')).toBeInTheDocument(); expect(accountApi.command).not.toHaveBeenCalled();
  });
  it('applies search/status/role to the server and resets page', async () => {
    render(<MemoryRouter><KtvUsersPage /></MemoryRouter>);
    await screen.findByRole('heading', { name: 'Mai Anh' });
    fireEvent.change(screen.getByLabelText('Tìm tài khoản'), { target: { value: 'Mai' } });
    fireEvent.click(screen.getByRole('combobox', { name: 'Trạng thái' })); fireEvent.click(screen.getByRole('option', { name: 'Khóa hoạt động mới' }));
    fireEvent.click(screen.getByRole('combobox', { name: 'Vai trò' })); fireEvent.click(screen.getByRole('option', { name: 'Người bán' }));
    fireEvent.click(screen.getByRole('button', { name: 'Áp dụng bộ lọc' }));
    await waitFor(() => expect(accountApi.list).toHaveBeenLastCalledWith('Mai', 'LOCKED', 'SELLER', 0));
  });
  it('shows failed authorization/loading separately from a genuinely empty list', async () => {
    vi.mocked(accountApi.list).mockRejectedValueOnce(new Error('Chỉ KTV đang hoạt động được xem')).mockResolvedValue({ items: [], page: 0, size: 20, totalElements: 0 });
    render(<MemoryRouter><KtvUsersPage /></MemoryRouter>);
    await screen.findByText('Chỉ KTV đang hoạt động được xem');
    expect(screen.queryByText('Không có tài khoản phù hợp')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Thử lại danh sách' }));
    await screen.findByText('Không có tài khoản phù hợp');
  });
});
