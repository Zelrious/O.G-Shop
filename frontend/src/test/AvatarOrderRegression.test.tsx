import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { AppHeader } from '../shared/layout/AppHeader';
import { ProfileInfoTab } from '../features/profile/components/ProfileInfoTab';
import { BuyerOrdersView } from '../features/orders/components/BuyerOrdersView';
import { SellerOrdersView } from '../features/orders/components/SellerOrdersView';
import { ordersApi } from '../features/orders/ordersApi';
import { useAuth } from '../features/auth';
import { marketplaceApi } from '../features/marketplace';

vi.mock('../features/auth', () => ({ useAuth: vi.fn(), authApi: { authorizedFetch: vi.fn() } }));
vi.mock('../features/marketplace', () => ({ marketplaceApi: { getCategories: vi.fn() } }));
vi.mock('../features/orders/ordersApi', () => ({ ordersApi: {
  getBuyerOrders: vi.fn(), getSellerOrders: vi.fn(), confirmSellerOrder: vi.fn(), cancelBuyerOrder: vi.fn(),
} }));

const profile = {
  id: 7, email: 'probe@example.test', fullName: 'Probe User', phoneNumber: '0901234567',
  avatarUrl: 'https://example.test/old-broken.jpg', roles: ['BUYER'], bankName: null,
  bankAccountNumberMasked: null, bankAccountHolder: null, verifiedSeller: false, createdAt: '2026-10-04T00:00:00Z',
};

const order = (title: string, id = 71) => ({
  orderId: id, checkoutGroupId: 'probe', productId: 17, productTitle: title, productThumbnail: null,
  unitPrice: 100000, quantity: 1, totalAmount: 130000, currency: 'VND', status: 'PAID_HELD', paymentStatus: 'HELD',
  partnerId: 8, partnerName: 'Fixture Partner', createdAt: '2026-10-04T00:00:00Z',
  paymentDueAt: '2026-10-04T01:00:00Z', completedAt: null, cancelledAt: null,
});
const result = (title: string, page = 0) => ({ content: [order(title)], totalElements: 11, totalPages: 2, size: 10, number: page });
const deferred = <T,>() => {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => { resolve = done; });
  return { promise, resolve };
};

beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(marketplaceApi.getCategories).mockResolvedValue([]);
  vi.mocked(useAuth).mockReturnValue({
    user: { userId: 7, email: profile.email, fullName: profile.fullName, avatarUrl: profile.avatarUrl,
      roles: ['BUYER'], createdAt: profile.createdAt }, isAuthenticated: true, isLoading: false,
    logout: vi.fn(), login: vi.fn(), register: vi.fn(), refreshSession: vi.fn(), reloadCurrentUser: vi.fn(),
  });
  URL.createObjectURL = vi.fn(() => 'blob:new-avatar-preview');
  URL.revokeObjectURL = vi.fn();
  vi.spyOn(window, 'confirm').mockReturnValue(true);
  vi.spyOn(window, 'alert').mockImplementation(() => {});
});

// Regression tests for recovered avatars and current order filters.
describe('Avatar and order regressions', () => {
  it('A1: header shows the new avatar after the old URL failed', () => {
    const tree = <MemoryRouter><AppHeader /></MemoryRouter>;
    const view = render(tree);
    const oldImage = screen.getByRole('img', { hidden: true });
    fireEvent.error(oldImage);
    const auth = vi.mocked(useAuth).mock.results[0].value;
    vi.mocked(useAuth).mockReturnValue({ ...auth, user: { ...auth.user, avatarUrl: 'https://example.test/new-valid.jpg' } });
    view.rerender(<MemoryRouter><AppHeader /></MemoryRouter>);
    const newImage = screen.getByRole('img', { hidden: true });
    expect(newImage).toHaveAttribute('src', 'https://example.test/new-valid.jpg');
    expect(newImage).toBeVisible();
  });

  it('A1: Profile preview remains visible after the saved avatar failed', () => {
    render(<ProfileInfoTab profile={profile} onProfileUpdated={vi.fn()} />);
    fireEvent.error(screen.getByAltText('Ảnh đại diện'));
    fireEvent.change(screen.getByTestId('avatar-file-input'), {
      target: { files: [new File(['fixture'], 'new.png', { type: 'image/png' })] },
    });
    const preview = screen.getByAltText('Xem trước ảnh đại diện');
    expect(preview).toHaveAttribute('src', 'blob:new-avatar-preview');
    expect(preview).toBeVisible();
  });

  it('A2: a delayed seller confirmation reloads the current filter/page', async () => {
    const action = deferred<{ orderId: number; status: string; message: string }>();
    vi.mocked(ordersApi.confirmSellerOrder).mockReturnValue(action.promise);
    let actionResolved = false;
    vi.mocked(ordersApi.getSellerOrders).mockImplementation(async (status, page) => (
      result(status === 'ALL' ? 'LATEST_ALL_RESULTS' : actionResolved ? 'STALE_PAID_RESULTS' : `INITIAL_PAID_${page}`, page)
    ));
    render(<MemoryRouter><SellerOrdersView /></MemoryRouter>);
    await screen.findByText('INITIAL_PAID_0');
    fireEvent.click(screen.getByRole('button', { name: 'Sau' }));
    await screen.findByText('INITIAL_PAID_1');
    fireEvent.click(screen.getByRole('button', { name: /Xác nhận đơn hàng/ }));
    expect(ordersApi.confirmSellerOrder).toHaveBeenCalledWith(71);
    fireEvent.click(screen.getByRole('button', { name: 'Tất cả' }));
    await screen.findByText('LATEST_ALL_RESULTS');
    actionResolved = true;
    await act(async () => { action.resolve({ orderId: 71, status: 'SELLER_CONFIRMED', message: 'Confirmed fixture' }); });
    await waitFor(() => expect(ordersApi.getSellerOrders).toHaveBeenLastCalledWith('ALL', 0, 10));
    expect(screen.getByRole('button', { name: 'Tất cả' })).toHaveStyle({ fontWeight: '700' });
    expect(ordersApi.getSellerOrders).toHaveBeenLastCalledWith('ALL', 0, 10);
    expect(screen.getByText('LATEST_ALL_RESULTS')).toBeInTheDocument();
  });
});

describe('TASK-0048 positive pagination probes', () => {
  it.each(['buyer', 'seller'] as const)('ignores an old GET after a tab switch: %s', async (actor) => {
    const oldRequest = deferred<ReturnType<typeof result>>();
    const get = actor === 'buyer' ? ordersApi.getBuyerOrders : ordersApi.getSellerOrders;
    vi.mocked(get).mockReturnValueOnce(oldRequest.promise).mockResolvedValueOnce(result('CURRENT_CANCELLED_RESULTS'));
    render(<MemoryRouter>{actor === 'buyer' ? <BuyerOrdersView /> : <SellerOrdersView />}</MemoryRouter>);
    fireEvent.click(screen.getByRole('button', { name: 'Đã hủy' }));
    await screen.findByText('CURRENT_CANCELLED_RESULTS');
    await act(async () => { oldRequest.resolve(result('OLD_GET_RESULTS')); });
    expect(screen.queryByText('OLD_GET_RESULTS')).toBeNull();
    expect(screen.getByText('CURRENT_CANCELLED_RESULTS')).toBeInTheDocument();
  });

  it('clamps an out-of-range buyer page when totalPages stays positive', async () => {
    vi.mocked(ordersApi.getBuyerOrders).mockResolvedValueOnce(result('PAGE_ZERO'))
      .mockResolvedValueOnce({ content: [], totalElements: 1, totalPages: 1, size: 10, number: 1 })
      .mockResolvedValueOnce({ ...result('CLAMPED_PAGE_ZERO'), totalPages: 1, totalElements: 1 });
    render(<MemoryRouter><BuyerOrdersView /></MemoryRouter>);
    await screen.findByText('PAGE_ZERO');
    fireEvent.click(screen.getByRole('button', { name: 'Sau' }));
    await screen.findByText('CLAMPED_PAGE_ZERO');
    await waitFor(() => expect(ordersApi.getBuyerOrders).toHaveBeenLastCalledWith('ALL', 0, 10));
  });
});
