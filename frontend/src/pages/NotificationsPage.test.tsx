import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { NotificationsPage } from './NotificationsPage';
import { notificationApi, notificationTarget, type NotificationItem } from '../features/notifications/notificationApi';

vi.mock('../features/auth', () => ({ useAuth: () => ({ user: { userId: 1, roles: ['SELLER'] } }) }));
vi.mock('../features/notifications/notificationApi', async original => ({
  ...await original<typeof import('../features/notifications/notificationApi')>(),
  notificationApi: { list: vi.fn(), read: vi.fn(), readAll: vi.fn() },
}));

const item: NotificationItem = {
  id: 1,
  type: 'LISTING_REVIEWED',
  title: 'Tin đã được duyệt',
  content: 'Phí còn phải trả: 10.000 đồng.',
  read: false,
  createdAt: '2026-10-12T02:00:00Z',
  destination: 'SELLER_LISTING',
  productId: 10,
};

beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(notificationApi.list).mockResolvedValue({ items: [item], page: 0, size: 20, totalElements: 1, unreadCount: 1 });
  vi.mocked(notificationApi.readAll).mockResolvedValue();
});

describe('Private notifications', () => {
  it('opens only destinations supported by the current role', () => {
    expect(notificationTarget(item, ['SELLER'])).toBe('/seller/listings/10/review');
    expect(notificationTarget(item, ['BUYER'])).toBeNull();
    expect(notificationTarget({ ...item, destination: 'KTV_MODERATION' }, ['KTV'])).toBe('/ktv/moderation?productId=10');
  });

  it('marks notice as read when clicked directly', async () => {
    let finish!: () => void;
    vi.mocked(notificationApi.read).mockReturnValue(new Promise<void>(resolve => { finish = resolve; }));
    render(<MemoryRouter><NotificationsPage /></MemoryRouter>);
    const noticeCard = await screen.findByRole('button', { name: 'Mở thông báo: Tin đã được duyệt' });
    fireEvent.click(noticeCard);
    expect(notificationApi.read).toHaveBeenCalledWith(1);
    finish();
    await screen.findByText(/0 thông báo chưa đọc/);
  });

  it('marks all as read when button is clicked', async () => {
    render(<MemoryRouter><NotificationsPage /></MemoryRouter>);
    const markAllBtn = await screen.findByRole('button', { name: 'Đánh dấu đã đọc hết' });
    fireEvent.click(markAllBtn);
    expect(notificationApi.readAll).toHaveBeenCalledOnce();
    await screen.findByText(/0 thông báo chưa đọc/);
    expect(screen.queryByRole('button', { name: 'Đánh dấu đã đọc hết' })).not.toBeInTheDocument();
  });

  it('keeps an authorized destination available when marking read fails', async () => {
    vi.mocked(notificationApi.read).mockRejectedValue(new Error('Chưa cập nhật được trạng thái đã đọc.'));
    render(<MemoryRouter><NotificationsPage /></MemoryRouter>);
    fireEvent.click(await screen.findByRole('button', { name: 'Mở thông báo: Tin đã được duyệt' }));
    expect(await screen.findByText('Chưa cập nhật được trạng thái đã đọc.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Mở nội dung dù chưa ghi được đã đọc' })).toHaveAttribute('href', '/seller/listings/10/review');
    expect(screen.getByText(/1 thông báo chưa đọc/)).toBeInTheDocument();
  });

  it('has empty and retry states without pretending a failed load succeeded', async () => {
    vi.mocked(notificationApi.list).mockRejectedValueOnce(new Error('Không tải được thông báo')).mockResolvedValue({ items: [], page: 0, size: 20, totalElements: 0, unreadCount: 0 });
    render(<MemoryRouter><NotificationsPage /></MemoryRouter>);
    await screen.findByText('Không tải được thông báo');
    fireEvent.click(screen.getByRole('button', { name: 'Làm mới' }));
    await waitFor(() => expect(screen.getByText('Chưa có thông báo')).toBeInTheDocument());
  });
});
