import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { BuyerOrdersView } from './components/BuyerOrdersView';
import { SellerOrdersView } from './components/SellerOrdersView';
import { ordersApi } from './ordersApi';

vi.mock('./ordersApi', () => ({
  ordersApi: {
    getBuyerOrders: vi.fn(),
    getBuyerOrderDetail: vi.fn(),
    cancelBuyerOrder: vi.fn(),
    getSellerOrders: vi.fn(),
    getSellerOrderDetail: vi.fn(),
    confirmSellerOrder: vi.fn(),
  },
}));

const mockOrder1 = {
  orderId: 201,
  checkoutGroupId: 'group-1',
  productId: 50,
  productTitle: 'Đồng hồ Seiko Vintage 1970',
  productThumbnail: null,
  unitPrice: 2500000,
  quantity: 1,
  totalAmount: 2530000,
  currency: 'VND',
  status: 'PAID_HELD',
  paymentStatus: 'HELD',
  partnerId: 88,
  partnerName: 'Lê Văn Bán',
  createdAt: new Date().toISOString(),
  paymentDueAt: new Date().toISOString(),
  completedAt: null,
  cancelledAt: null,
};

const mockOrder2 = {
  ...mockOrder1,
  orderId: 202,
  productTitle: 'Máy ảnh Film Canon AE-1',
};

describe('BuyerOrdersView Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders order list with status tabs and pagination metadata', async () => {
    vi.mocked(ordersApi.getBuyerOrders).mockResolvedValue({
      content: [mockOrder1],
      totalElements: 15,
      totalPages: 2,
      size: 10,
      number: 0,
    });

    render(
      <MemoryRouter>
        <BuyerOrdersView />
      </MemoryRouter>
    );

    expect(screen.getByText(/Đơn Mua Của Bạn/i)).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText('Đồng hồ Seiko Vintage 1970')).toBeInTheDocument();
    });

    expect(screen.getByText(/Trang/i)).toHaveTextContent('Trang 1 / 2 (Tổng 15 đơn hàng)');
    const prevBtn = screen.getByRole('button', { name: 'Trước' });
    const nextBtn = screen.getByRole('button', { name: 'Sau' });

    expect(prevBtn).toBeDisabled();
    expect(nextBtn).toBeEnabled();
  });

  it('navigates to next page on click Sau and disables Sau on last page', async () => {
    vi.mocked(ordersApi.getBuyerOrders)
      .mockResolvedValueOnce({
        content: [mockOrder1],
        totalElements: 12,
        totalPages: 2,
        size: 10,
        number: 0,
      })
      .mockResolvedValueOnce({
        content: [mockOrder2],
        totalElements: 12,
        totalPages: 2,
        size: 10,
        number: 1,
      });

    render(
      <MemoryRouter>
        <BuyerOrdersView />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Đồng hồ Seiko Vintage 1970')).toBeInTheDocument();
    });

    const nextBtn = screen.getByRole('button', { name: 'Sau' });
    fireEvent.click(nextBtn);

    await waitFor(() => {
      expect(ordersApi.getBuyerOrders).toHaveBeenCalledWith('ALL', 1, 10);
      expect(screen.getByText('Máy ảnh Film Canon AE-1')).toBeInTheDocument();
    });

    expect(screen.getByText(/Trang/i)).toHaveTextContent('Trang 2 / 2 (Tổng 12 đơn hàng)');
    expect(screen.getByRole('button', { name: 'Trước' })).toBeEnabled();
    expect(screen.getByRole('button', { name: 'Sau' })).toBeDisabled();
  });

  it('switches status tab, resets page to 0, and requests new status', async () => {
    vi.mocked(ordersApi.getBuyerOrders).mockResolvedValue({
      content: [mockOrder1],
      totalElements: 5,
      totalPages: 1,
      size: 10,
      number: 0,
    });

    render(
      <MemoryRouter>
        <BuyerOrdersView />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Đồng hồ Seiko Vintage 1970')).toBeInTheDocument();
    });

    const pendingTab = screen.getByRole('button', { name: 'Chờ thanh toán' });
    fireEvent.click(pendingTab);

    await waitFor(() => {
      expect(ordersApi.getBuyerOrders).toHaveBeenCalledWith('PAYMENT_PENDING', 0, 10);
    });
  });

  it('displays empty state and suppresses pagination when totalPages is 0', async () => {
    vi.mocked(ordersApi.getBuyerOrders).mockResolvedValue({
      content: [],
      totalElements: 0,
      totalPages: 0,
      size: 10,
      number: 0,
    });

    render(
      <MemoryRouter>
        <BuyerOrdersView />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Chưa có đơn hàng nào')).toBeInTheDocument();
    });

    expect(screen.queryByText(/Trang 1 \/ 0/)).toBeNull();
    expect(screen.queryByRole('button', { name: 'Trước' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Sau' })).toBeNull();
  });

  it('shows error message with Thử lại button and retries successfully', async () => {
    vi.mocked(ordersApi.getBuyerOrders)
      .mockRejectedValueOnce(new Error('Mạng không ổn định'))
      .mockResolvedValueOnce({
        content: [mockOrder1],
        totalElements: 1,
        totalPages: 1,
        size: 10,
        number: 0,
      });

    render(
      <MemoryRouter>
        <BuyerOrdersView />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Mạng không ổn định')).toBeInTheDocument();
    });

    const retryBtn = screen.getByRole('button', { name: 'Thử lại' });
    fireEvent.click(retryBtn);

    await waitFor(() => {
      expect(screen.getByText('Đồng hồ Seiko Vintage 1970')).toBeInTheDocument();
    });
  });
});

describe('SellerOrdersView Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders seller orders and supports pagination navigation', async () => {
    vi.mocked(ordersApi.getSellerOrders)
      .mockResolvedValueOnce({
        content: [mockOrder1],
        totalElements: 15,
        totalPages: 2,
        size: 10,
        number: 0,
      })
      .mockResolvedValueOnce({
        content: [mockOrder2],
        totalElements: 15,
        totalPages: 2,
        size: 10,
        number: 1,
      });

    render(
      <MemoryRouter>
        <SellerOrdersView />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Đồng hồ Seiko Vintage 1970')).toBeInTheDocument();
    });

    expect(screen.getByText(/Trang/i)).toHaveTextContent('Trang 1 / 2 (Tổng 15 đơn bán)');
    const nextBtn = screen.getByRole('button', { name: 'Sau' });
    fireEvent.click(nextBtn);

    await waitFor(() => {
      expect(ordersApi.getSellerOrders).toHaveBeenCalledWith('PAID_HELD', 1, 10);
      expect(screen.getByText('Máy ảnh Film Canon AE-1')).toBeInTheDocument();
    });
  });

  it('confirms order and reloads current page', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    vi.spyOn(window, 'alert').mockImplementation(() => {});

    vi.mocked(ordersApi.getSellerOrders).mockResolvedValue({
      content: [mockOrder1],
      totalElements: 1,
      totalPages: 1,
      size: 10,
      number: 0,
    });
    vi.mocked(ordersApi.confirmSellerOrder).mockResolvedValue({
      orderId: 201,
      status: 'SELLER_CONFIRMED',
      message: 'Xác nhận đơn thành công',
    });

    render(
      <MemoryRouter>
        <SellerOrdersView />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Đồng hồ Seiko Vintage 1970')).toBeInTheDocument();
    });

    const confirmBtn = screen.getByRole('button', { name: /Xác nhận đơn hàng/i });
    fireEvent.click(confirmBtn);

    await waitFor(() => {
      expect(ordersApi.confirmSellerOrder).toHaveBeenCalledWith(201);
      // Reloaded
      expect(ordersApi.getSellerOrders).toHaveBeenCalledTimes(2);
    });
  });
});
