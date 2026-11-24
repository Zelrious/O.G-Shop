import type { DisputeDetail } from '../disputes/types';
import type { TrackingResponse } from '../fulfillment/fulfillmentApi';
import type { OrderDetail } from './types';

export interface OrderTimelineStep {
  key: string;
  label: string;
  done: boolean;
  time?: string | null;
}

/**
 * Build an authoritative order timeline shared by both Buyer and Seller views.
 * - Preserves completed outbound delivery milestones when an order enters DISPUTED or REFUNDED.
 * - Does NOT assume every DISPUTED order was delivered unless authoritative delivery data exists.
 * - Separates return shipment lifecycle from outbound shipment progress and refund settlement.
 */
export function buildOrderTimelineSteps(
  order: Pick<
    OrderDetail,
    'status' | 'createdAt' | 'paidAt' | 'heldAt' | 'shippedAt' | 'deliveredAt' | 'completedAt'
  >,
  tracking?: TrackingResponse | null,
  dispute?: DisputeDetail | null,
): OrderTimelineStep[] {
  const isPaid = order.status !== 'PAYMENT_PENDING' && order.status !== 'CANCELLED';
  const outboundDelivered =
    Boolean(order.deliveredAt) ||
    tracking?.status === 'DELIVERED' ||
    ['DELIVERED', 'COMPLETED'].includes(order.status);
  const outboundShipped =
    outboundDelivered ||
    Boolean(order.shippedAt) ||
    ['SHIPPED'].includes(order.status) ||
    (Boolean(tracking?.rawGhnStatus) && tracking?.rawGhnStatus !== 'ready_to_pick');
  const sellerConfirmed =
    outboundShipped ||
    order.status === 'SELLER_CONFIRMED' ||
    Boolean(tracking?.trackingNumber);

  const steps: OrderTimelineStep[] = [
    { key: 'CREATED', label: 'Đặt hàng', done: true, time: order.createdAt },
    { key: 'PAID', label: 'Ký quỹ Escrow', done: isPaid, time: order.heldAt || order.paidAt },
    { key: 'CONFIRMED', label: 'Người bán xác nhận', done: sellerConfirmed },
    { key: 'SHIPPED', label: 'Đang vận chuyển', done: outboundShipped, time: order.shippedAt },
    { key: 'DELIVERED', label: 'Đã giao hàng', done: outboundDelivered, time: order.deliveredAt },
  ];

  const hasReturnFlow =
    order.status === 'REFUNDED' ||
    dispute?.status === 'RETURN_APPROVED' ||
    dispute?.status === 'RESOLVED_REFUND' ||
    Boolean(dispute?.returnTrackingCode);

  if (hasReturnFlow) {
    const returnStatus = dispute?.returnShipmentStatus;
    const isReturnHandedOver =
      returnStatus === 'RETURN_SHIPPING' ||
      returnStatus === 'RETURN_DELIVERED' ||
      Boolean(dispute?.returnHandedOverAt);
    const isReturnDelivered =
      returnStatus === 'RETURN_DELIVERED' ||
      Boolean(dispute?.returnDeliveredAt);

    steps.push({
      key: 'RETURN_HANDOVER',
      label: isReturnHandedOver ? 'Đang vận chuyển hoàn trả' : 'Chờ bàn giao hoàn trả',
      done: isReturnHandedOver,
      time: dispute?.returnHandedOverAt,
    });

    steps.push({
      key: 'RETURN_DELIVERED',
      label: 'Đã giao trả thành công',
      done: isReturnDelivered,
      time: dispute?.returnDeliveredAt,
    });

    steps.push({
      key: 'REFUNDED',
      label: order.status === 'REFUNDED' ? 'Đã hoàn tiền' : 'Chờ hoàn tất hoàn tiền',
      done: order.status === 'REFUNDED',
      time: order.status === 'REFUNDED' ? dispute?.resolvedAt : null,
    });
  } else {
    steps.push({
      key: 'COMPLETED',
      label: order.status === 'DISPUTED' ? 'Đang xử lý tranh chấp' : 'Hoàn tất giao dịch',
      done: order.status === 'COMPLETED',
      time: order.completedAt,
    });
  }

  return steps;
}

