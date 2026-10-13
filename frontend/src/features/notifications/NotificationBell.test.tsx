import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { NotificationBell } from './NotificationBell';
import { notificationApi, type NotificationPage } from './notificationApi';

const actor = vi.hoisted(() => ({ userId: 1, roles: ['SELLER'] }));
vi.mock('../auth', () => ({ useAuth: () => ({ user: actor }) }));
vi.mock('./notificationApi', async original => ({ ...await original<typeof import('./notificationApi')>(), notificationApi: { list: vi.fn(), read: vi.fn() } }));
const page: NotificationPage = { page: 0, size: 6, totalElements: 1, unreadCount: 1, items: [{ id: 9, type: 'LISTING', title: 'Tin đăng được duyệt', content: 'Xem kết quả kiểm duyệt', read: false, createdAt: '2026-10-13T01:00:00Z', destination: 'SELLER_LISTING', productId: 10 }] };
function App() { return <MemoryRouter><NotificationBell /><Routes><Route path="/seller/listings/10/review" element={<h1>Kết quả kiểm duyệt</h1>} /><Route path="*" element={<h1>Trang đang xem</h1>} /></Routes></MemoryRouter>; }
beforeEach(() => { vi.resetAllMocks(); actor.userId = 1; actor.roles = ['SELLER']; vi.mocked(notificationApi.list).mockResolvedValue(page); vi.mocked(notificationApi.read).mockResolvedValue(); });

describe('Notification quick menu', () => {
  it('reads the notice and opens its authorized destination', async () => {
    render(<App />); fireEvent.click(screen.getByRole('button', { name: /^Thông báo/ }));
    fireEvent.click(await screen.findByRole('button', { name: 'Mở thông báo: Tin đăng được duyệt' }));
    expect(await screen.findByRole('heading', { name: 'Kết quả kiểm duyệt' })).toBeInTheDocument();
    expect(notificationApi.read).toHaveBeenCalledWith(9);
    expect(screen.queryByRole('dialog', { name: 'Thông báo' })).not.toBeInTheDocument();
  });
  it('keeps the menu and badge on read failure and offers an explicit fallback', async () => {
    vi.mocked(notificationApi.read).mockRejectedValue(new Error('Không ghi được đã đọc'));
    render(<App />); fireEvent.click(screen.getByRole('button', { name: /^Thông báo/ }));
    fireEvent.click(await screen.findByRole('button', { name: 'Mở thông báo: Tin đăng được duyệt' }));
    expect(await screen.findByText('Không ghi được đã đọc')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Trang đang xem' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Thông báo, 1 chưa đọc' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Mở nội dung dù chưa ghi được đã đọc' }));
    expect(await screen.findByRole('heading', { name: 'Kết quả kiểm duyệt' })).toBeInTheDocument();
  });
  it('closes with Escape and restores focus to the bell', () => {
    render(<App />); const bell = screen.getByRole('button', { name: /^Thông báo/ });
    fireEvent.click(bell); fireEvent.keyDown(screen.getByRole('dialog', { name: 'Thông báo' }), { key: 'Escape' });
    expect(screen.queryByRole('dialog', { name: 'Thông báo' })).not.toBeInTheDocument();
    expect(bell).toHaveFocus();
  });
  it('ignores a late inbox result from the previous account', async () => {
    let oldResult!: (result: NotificationPage) => void;
    vi.mocked(notificationApi.list).mockReturnValueOnce(new Promise(resolve => { oldResult = resolve; }));
    const { rerender } = render(<App />);
    actor.userId = 8; vi.mocked(notificationApi.list).mockResolvedValue({ ...page, items: [], unreadCount: 0 }); rerender(<App />);
    oldResult(page);
    fireEvent.click(screen.getByRole('button', { name: /^Thông báo/ }));
    await waitFor(() => expect(screen.getByText('Chưa có thông báo')).toBeInTheDocument());
    expect(screen.queryByText('Tin đăng được duyệt')).not.toBeInTheDocument();
  });
});
