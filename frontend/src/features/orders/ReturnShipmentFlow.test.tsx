import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { resolveOutboundGhnDisplayStatus, type TrackingResponse } from '../fulfillment/fulfillmentApi';
import { buildOrderTimelineSteps } from './orderTimeline';
import { ReturnShipmentCard } from '../disputes/ReturnShipmentCard';
import { SellerOrdersView } from './components/SellerOrdersView';
import { ordersApi } from './ordersApi';
import type { OrderDetail } from './types';
import type { DisputeDetail } from '../disputes/types';

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

const baseOrder: OrderDetail = {
  orderId: 501,
  checkoutGroupId: 'grp-501',
  productId: 10,
  productTitle: 'Tai nghe Sony WH-1000XM4',
  productThumbnail: null,
  unitPrice: 250000,
  quantity: 1,
  subtotal: 250000,
  shippingFee: 30000,
  buyerSystemFee: 0,
  sellerSystemFee: 0,
  sellerProceeds: 250000,
  voucherDiscountAmount: 0,
  shippingDiscountAmount: 0,
  appliedVoucherCode: null,
  totalAmount: 280000,
  currency: 'VND',
  paymentId: 77,
  paymentMethod: 'COD_MOCK',
  status: 'DISPUTED',
  paymentStatus: 'HELD',
  transactionCode: 'TXN-501',
  buyerId: 100,
  buyerName: 'Nguyễn Văn Mua',
  sellerId: 200,
  sellerName: 'Cửa hàng Độc Bản',
  shippingRecipientName: 'Nguyễn Văn Mua',
  shippingPhoneNumber: '0901234567',
  fullShippingAddress: '123 Lê Lợi, Q1, TP.HCM',
  createdAt: '2026-11-24T10:00:00Z',
  paidAt: '2026-11-24T10:01:00Z',
  heldAt: '2026-11-24T10:01:00Z',
  shippedAt: '2026-11-24T10:05:00Z',
  deliveredAt: '2026-11-24T10:10:00Z',
  completedAt: null,
  cancelledAt: null,
  paymentDueAt: '2026-11-24T11:00:00Z',
  cancellationReason: null,
};

const baseTracking: TrackingResponse = {
  orderId: 501,
  carrier: 'GHN',
  trackingNumber: 'LY9VTV',
  status: 'DELIVERED',
  rawGhnStatus: 'ready_to_pick',
  shippingFee: 30000,
  expectedDeliveryAt: '2026-11-26T23:59:59Z',
  fromAddress: '123 Lê Lợi, Q1',
  toAddress: '456 Nguyễn Huệ, Q1',
};

const baseDispute: DisputeDetail = {
  disputeId: 1,
  orderId: 501,
  orderCode: 'OG-ORD-501',
  buyerId: 100,
  buyerName: 'Nguyễn Văn Mua',
  buyerEmail: 'buyer@example.com',
  sellerId: 200,
  sellerName: 'Cửa hàng Độc Bản',
  sellerEmail: 'seller@example.com',
  productTitle: 'Tai nghe Sony WH-1000XM4',
  disputeAmount: 280000,
  reason: 'Hàng không đúng mô tả',
  description: 'Trầy xước nặng',
  buyerEvidenceUrls: [],
  videoUnboxingUrl: null,
  sellerResponse: null,
  sellerEvidenceUrls: [],
  sellerResponseDueAt: '2026-11-25T10:00:00Z',
  status: 'RETURN_APPROVED',
  verdictAction: 'RETURN_REFUND',
  verdictNotes: 'Chấp thuận hoàn trả',
  refundAmount: 280000,
  penaltyPoints: 0,
  resolvedBy: 1,
  resolvedAt: '2026-11-24T10:20:00Z',
  returnCarrier: 'GHN',
  returnTrackingCode: 'GHN-RET-501-TEST01',
  returnShipmentStatus: 'RETURN_CREATED',
  returnHandedOverAt: null,
  returnDeliveredAt: null,
  returnRecipientName: 'Kho Cửa hàng Độc Bản',
  returnRecipientPhone: '0909888777',
  returnRecipientAddress: '88 Nguyễn Trãi, Q5, TP.HCM',
  createdAt: '2026-11-24T10:15:00Z',
  updatedAt: '2026-11-24T10:20:00Z',
};

describe('Scope D: resolveOutboundGhnDisplayStatus', () => {
  it('shows Đã giao hàng thành công when authoritative delivery is confirmed even if GHN Sandbox returns ready_to_pick', () => {
    const resolved = resolveOutboundGhnDisplayStatus(baseOrder, baseTracking);
    expect(resolved.label).toBe('Đã giao hàng thành công');
    expect(resolved.isDeliveredConfirmed).toBe(true);
    expect(resolved.rawDiagnosticStatus).toBe('ready_to_pick');
  });

  it('keeps raw GHN label when order is not delivered yet', () => {
    const undeliveredOrder: OrderDetail = {
      ...baseOrder,
      status: 'SHIPPED',
      deliveredAt: null,
    };
    const undeliveredTracking: TrackingResponse = {
      ...baseTracking,
      status: 'IN_TRANSIT',
      rawGhnStatus: 'ready_to_pick',
    };
    const resolved = resolveOutboundGhnDisplayStatus(undeliveredOrder, undeliveredTracking);
    expect(resolved.label).toBe('Chờ lấy hàng');
    expect(resolved.isDeliveredConfirmed).toBe(false);
    expect(resolved.rawDiagnosticStatus).toBe('ready_to_pick');
  });
});

describe('Scope C: buildOrderTimelineSteps', () => {
  it('preserves outbound shipping and delivered milestones when a delivered order enters DISPUTED and tracks return stages separately', () => {
    const stepsCreated = buildOrderTimelineSteps(baseOrder, baseTracking, baseDispute);
    expect(stepsCreated.find((s) => s.key === 'SHIPPED')?.done).toBe(true);
    expect(stepsCreated.find((s) => s.key === 'DELIVERED')?.done).toBe(true);
    expect(stepsCreated.find((s) => s.key === 'RETURN_HANDOVER')?.label).toBe('Chờ bàn giao hoàn trả');
    expect(stepsCreated.find((s) => s.key === 'RETURN_HANDOVER')?.done).toBe(false);
    expect(stepsCreated.find((s) => s.key === 'RETURN_DELIVERED')?.done).toBe(false);

    const shippingDispute: DisputeDetail = {
      ...baseDispute,
      returnShipmentStatus: 'RETURN_SHIPPING',
      returnHandedOverAt: '2026-11-24T11:00:00Z',
    };
    const stepsShipping = buildOrderTimelineSteps(baseOrder, baseTracking, shippingDispute);
    expect(stepsShipping.find((s) => s.key === 'RETURN_HANDOVER')?.label).toBe('Đang vận chuyển hoàn trả');
    expect(stepsShipping.find((s) => s.key === 'RETURN_HANDOVER')?.done).toBe(true);
    expect(stepsShipping.find((s) => s.key === 'RETURN_DELIVERED')?.done).toBe(false);

    const deliveredDispute: DisputeDetail = {
      ...shippingDispute,
      returnShipmentStatus: 'RETURN_DELIVERED',
      returnDeliveredAt: '2026-11-24T12:00:00Z',
    };
    const stepsReturnDelivered = buildOrderTimelineSteps(baseOrder, baseTracking, deliveredDispute);
    expect(stepsReturnDelivered.find((s) => s.key === 'RETURN_DELIVERED')?.label).toBe('Đã giao trả thành công');
    expect(stepsReturnDelivered.find((s) => s.key === 'RETURN_DELIVERED')?.done).toBe(true);
    expect(stepsReturnDelivered.find((s) => s.key === 'REFUNDED')?.done).toBe(false);
  });

  it('does NOT assume every DISPUTED order was delivered if deliveredAt and tracking DELIVERED are absent', () => {
    const undeliveredDisputedOrder: OrderDetail = {
      ...baseOrder,
      status: 'DISPUTED',
      deliveredAt: null,
    };
    const inTransitTracking: TrackingResponse = {
      ...baseTracking,
      status: 'IN_TRANSIT',
    };
    const steps = buildOrderTimelineSteps(undeliveredDisputedOrder, inTransitTracking, null);
    expect(steps.find((s) => s.key === 'SHIPPED')?.done).toBe(true);
    expect(steps.find((s) => s.key === 'DELIVERED')?.done).toBe(false);
  });

  it('marks final step Đã hoàn tiền as done when order status is REFUNDED', () => {
    const refundedOrder: OrderDetail = { ...baseOrder, status: 'REFUNDED' };
    const resolvedDispute: DisputeDetail = {
      ...baseDispute,
      status: 'RESOLVED_REFUND',
      returnShipmentStatus: 'RETURN_DELIVERED',
      returnDeliveredAt: '2026-11-24T12:00:00Z',
    };
    const steps = buildOrderTimelineSteps(refundedOrder, baseTracking, resolvedDispute);
    expect(steps.find((s) => s.key === 'DELIVERED')?.done).toBe(true);
    expect(steps.find((s) => s.key === 'REFUNDED')?.label).toBe('Đã hoàn tiền');
    expect(steps.find((s) => s.key === 'REFUNDED')?.done).toBe(true);
  });
});

describe('Scope A & B: ReturnShipmentCard', () => {
  it('displays Vietnamese labels for all 3 stages and real Seller warehouse address', () => {
    const { rerender } = render(<ReturnShipmentCard dispute={baseDispute} />);
    expect(screen.getByText(/Thông tin vận chuyển hoàn trả \(GHN — Chiều về\)/i)).toBeInTheDocument();
    expect(screen.getByText('GHN-RET-501-TEST01')).toBeInTheDocument();
    expect(screen.getByTestId('return-shipment-status-badge')).toHaveTextContent('Chờ bàn giao cho GHN');
    expect(screen.getByText(/Kho Cửa hàng Độc Bản/)).toBeInTheDocument();
    expect(screen.getByText(/88 Nguyễn Trãi, Q5, TP\.HCM/)).toBeInTheDocument();

    rerender(
      <ReturnShipmentCard
        dispute={{
          ...baseDispute,
          returnShipmentStatus: 'RETURN_SHIPPING',
          returnHandedOverAt: '2026-11-24T11:00:00Z',
        }}
      />
    );
    expect(screen.getByTestId('return-shipment-status-badge')).toHaveTextContent('Đang vận chuyển hoàn trả về Người bán');

    rerender(
      <ReturnShipmentCard
        dispute={{
          ...baseDispute,
          returnShipmentStatus: 'RETURN_DELIVERED',
          returnHandedOverAt: '2026-11-24T11:00:00Z',
          returnDeliveredAt: '2026-11-24T12:00:00Z',
        }}
      />
    );
    expect(screen.getByTestId('return-shipment-status-badge')).toHaveTextContent('Đã giao trả thành công');
  });

  it('renders nothing when no return tracking code exists', () => {
    const { container } = render(
      <ReturnShipmentCard dispute={{ ...baseDispute, returnTrackingCode: null, returnShipmentStatus: null }} />
    );
    expect(container.firstChild).toBeNull();
  });
});

describe('Scope E: SellerOrdersView DISPUTED and REFUNDED tabs', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders existing tabs plus DISPUTED and REFUNDED tabs and filters accurately', async () => {
    vi.mocked(ordersApi.getSellerOrders).mockResolvedValue({
      content: [
        {
          orderId: 601,
          checkoutGroupId: 'grp-601',
          productId: 11,
          productTitle: 'Máy ảnh Film Pentax',
          productThumbnail: null,
          unitPrice: 1200000,
          quantity: 1,
          totalAmount: 1230000,
          currency: 'VND',
          status: 'DISPUTED',
          paymentStatus: 'HELD',
          partnerId: 100,
          partnerName: 'Nguyễn Văn Mua',
          createdAt: '2026-11-24T10:00:00Z',
          paymentDueAt: '2026-11-24T11:00:00Z',
          completedAt: null,
          cancelledAt: null,
        },
      ],
      totalElements: 1,
      totalPages: 1,
      size: 10,
      number: 0,
    });

    render(
      <MemoryRouter>
        <SellerOrdersView />
      </MemoryRouter>
    );

    expect(screen.getByRole('button', { name: 'Tất cả' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Khiếu nại \/ Đang hoàn trả/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Đã hoàn tiền/i })).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText('Máy ảnh Film Pentax')).toBeInTheDocument();
    });
    expect(screen.getByText(/Khiếu nại \/ Tranh chấp/i)).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /Khiếu nại \/ Đang hoàn trả/i }));
    await waitFor(() => {
      expect(ordersApi.getSellerOrders).toHaveBeenCalledWith('DISPUTED', 0, 10);
    });

    fireEvent.click(screen.getByRole('button', { name: /Đã hoàn tiền/i }));
    await waitFor(() => {
      expect(ordersApi.getSellerOrders).toHaveBeenCalledWith('REFUNDED', 0, 10);
    });
  });
});
