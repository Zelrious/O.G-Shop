import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { BuyerOrdersView } from './components/BuyerOrdersView';
import { SellerOrdersView } from './components/SellerOrdersView';
import { BuyerOrderDetailView } from './components/BuyerOrderDetailView';
import { SellerOrderDetailView } from './components/SellerOrderDetailView';
import { ordersApi } from './ordersApi';
import { OrderDetail } from './types';

vi.mock('./ordersApi', () => ({
  ordersApi: {
    getBuyerOrders: vi.fn(),
    getBuyerOrderDetail: vi.fn(),
    cancelBuyerOrder: vi.fn(),
    getSellerOrders: vi.fn(),
    getSellerOrderDetail: vi.fn(),
    confirmSellerOrder: vi.fn(),
    createShipment: vi.fn(),
    printWaybill: vi.fn(),
    getTracking: vi.fn(),
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

  it('switches to Đang giao and Trả hàng tabs and requests SHIPPED and REFUNDED statuses', async () => {
    vi.mocked(ordersApi.getBuyerOrders).mockResolvedValue({
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
      expect(screen.getByText('Đồng hồ Seiko Vintage 1970')).toBeInTheDocument();
    });

    const shippingTab = screen.getByRole('button', { name: 'Đang giao' });
    fireEvent.click(shippingTab);
    await waitFor(() => {
      expect(ordersApi.getBuyerOrders).toHaveBeenCalledWith('SHIPPED', 0, 10);
    });

    const refundTab = screen.getByRole('button', { name: 'Trả hàng' });
    fireEvent.click(refundTab);
    await waitFor(() => {
      expect(ordersApi.getBuyerOrders).toHaveBeenCalledWith('REFUNDED', 0, 10);
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

const mockDetail: OrderDetail = {
  orderId: 201,
  checkoutGroupId: 'group-1',
  status: 'SELLER_CONFIRMED',
  buyerId: 10,
  buyerName: 'Trần Người Mua',
  sellerId: 88,
  sellerName: 'Lê Văn Bán',
  productId: 50,
  productTitle: 'Đồng hồ Seiko Vintage 1970',
  productThumbnail: null,
  unitPrice: 2500000,
  quantity: 1,
  shippingRecipientName: 'Trần Người Mua',
  shippingPhoneNumber: '0901234567',
  fullShippingAddress: 'Số 123 Đường Nguyễn Trãi, Phường Bến Thành, Quận 1, TP Hồ Chí Minh',
  subtotal: 2500000,
  shippingFee: 30000,
  buyerSystemFee: 0,
  sellerSystemFee: 50000,
  sellerProceeds: 2450000,
  voucherDiscountAmount: 0,
  shippingDiscountAmount: 0,
  appliedVoucherCode: null,
  totalAmount: 2530000,
  currency: 'VND',
  paymentId: 1,
  paymentMethod: 'ESCROW_MOCK',
  paymentStatus: 'HELD',
  transactionCode: 'ESCROW-999',
  paidAt: new Date().toISOString(),
  heldAt: new Date().toISOString(),
  paymentDueAt: new Date().toISOString(),
  createdAt: new Date().toISOString(),
  completedAt: null,
  cancelledAt: null,
  cancellationReason: null,
};

describe('SellerOrderDetailView Component with GHN', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders create shipment button when order is confirmed and has no tracking', async () => {
    vi.mocked(ordersApi.getSellerOrderDetail).mockResolvedValue(mockDetail);
    vi.mocked(ordersApi.getTracking).mockResolvedValue({
      orderId: 201,
      carrier: 'GHN',
      status: 'CHƯA_TẠO_VẬN_ĐƠN',
      trackingNumber: null,
    });

    render(
      <MemoryRouter>
        <SellerOrderDetailView orderId={201} />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Đơn bán #201 (SL-10)')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Tạo vận đơn GHN/i })).toBeInTheDocument();
    });
  });

  it('opens modal and submits shipment creation successfully', async () => {
    vi.spyOn(window, 'alert').mockImplementation(() => {});
    vi.mocked(ordersApi.getSellerOrderDetail).mockResolvedValue(mockDetail);
    vi.mocked(ordersApi.getTracking).mockResolvedValue({
      orderId: 201,
      carrier: 'GHN',
      status: 'CHƯA_TẠO_VẬN_ĐƠN',
      trackingNumber: null,
    });
    vi.mocked(ordersApi.createShipment).mockResolvedValue({
      orderId: 201,
      trackingNumber: 'GHN-TEST-12345',
      clientOrderCode: 'OG-ORD-201',
      carrier: 'GHN',
      status: 'PENDING',
      shippingFee: 32000,
      createdAt: new Date().toISOString(),
    });

    render(
      <MemoryRouter>
        <SellerOrderDetailView orderId={201} />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Tạo vận đơn GHN/i })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: /Tạo vận đơn GHN/i }));

    expect(screen.getByText('Khối lượng kiện hàng (gram) *')).toBeInTheDocument();


    const submitBtn = screen.getByRole('button', { name: /Xác nhận tạo vận đơn GHN/i });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(ordersApi.createShipment).toHaveBeenCalledWith(201, expect.objectContaining({
        weight: 500,
        length: 20,
        width: 15,
        height: 10,
      }));
    });
  });

  it('displays tracking card and triggers waybill printing when tracking exists', async () => {
    vi.spyOn(window, 'open').mockImplementation(() => null);
    vi.mocked(ordersApi.getSellerOrderDetail).mockResolvedValue(mockDetail);
    vi.mocked(ordersApi.getTracking).mockResolvedValue({
      orderId: 201,
      carrier: 'GHN',
      status: 'READY_TO_PICK',
      rawGhnStatus: 'ready_to_pick',
      trackingNumber: 'GHN-TEST-99999',
      shippingFee: 35000,
      expectedDeliveryAt: new Date().toISOString(),
    });
    vi.mocked(ordersApi.printWaybill).mockResolvedValue({
      orderId: 201,
      trackingNumber: 'GHN-TEST-99999',
      printUrl: 'https://dev-online-gateway.ghn.vn/a5/index.html?token=test-token',
    });

    render(
      <MemoryRouter>
        <SellerOrderDetailView orderId={201} />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('GHN-TEST-99999')).toBeInTheDocument();
      expect(screen.getByText('Chờ lấy hàng')).toBeInTheDocument();
    });

    const printBtn = screen.getByRole('button', { name: /In Phiếu Giao Hàng A5/i });
    fireEvent.click(printBtn);

    await waitFor(() => {
      expect(ordersApi.printWaybill).toHaveBeenCalledWith(201);
      expect(window.open).toHaveBeenCalledWith('https://dev-online-gateway.ghn.vn/a5/index.html?token=test-token', '_blank');
    });
  });
});

describe('BuyerOrderDetailView Component with GHN Tracking', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders GHN tracking details when tracking is present', async () => {
    vi.mocked(ordersApi.getBuyerOrderDetail).mockResolvedValue(mockDetail);
    vi.mocked(ordersApi.getTracking).mockResolvedValue({
      orderId: 201,
      carrier: 'Giao Hàng Nhanh',
      status: 'DELIVERING',
      rawGhnStatus: 'delivering',
      trackingNumber: 'GHN-BUYER-777',
      shippingFee: 30000,
      expectedDeliveryAt: new Date().toISOString(),
      fromAddress: 'Kho Hà Nội',
      toAddress: 'Số 123 Đường Nguyễn Trãi, Quận 1, TP Hồ Chí Minh',
    });

    render(
      <MemoryRouter>
        <BuyerOrderDetailView orderId={201} />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Đơn hàng #201')).toBeInTheDocument();
      expect(screen.getByText('GHN-BUYER-777')).toBeInTheDocument();
      expect(screen.getByText('Đang giao hàng')).toBeInTheDocument();
      expect(screen.getByText(/Gửi từ: Kho Hà Nội/i)).toBeInTheDocument();
    });
  });
});

