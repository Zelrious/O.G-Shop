import { authApi } from '../auth/authApi';

export interface CreateShipmentRequest {
  fromName?: string;
  fromPhone?: string;
  fromAddress?: string;
  fromWardName?: string;
  fromDistrictName?: string;
  fromProvinceName?: string;
  fromDistrictId?: number;
  toDistrictId?: number;
  toWardCode?: string;
  weight?: number;
  length?: number;
  width?: number;
  height?: number;
  note?: string;
}

export interface ShipmentResponse {
  orderId: number;
  trackingNumber: string;
  clientOrderCode: string;
  carrier: string;
  status: string;
  shippingFee: number;
  expectedDeliveryAt?: string | null;
  rawGhnStatus?: string | null;
  createdAt: string;
}

export interface PrintWaybillResponse {
  orderId: number;
  trackingNumber: string;
  printUrl: string;
}

export interface TrackingResponse {
  orderId: number;
  trackingNumber?: string | null;
  carrier: string;
  status: string;
  rawGhnStatus?: string | null;
  shippingFee?: number | null;
  expectedDeliveryAt?: string | null;
  fromAddress?: string | null;
  toAddress?: string | null;
}

export interface GhnStatusMeta {
  label: string;
  color: string;
  bg: string;
}

export function formatGhnStatus(rawStatus?: string | null): GhnStatusMeta {
  if (!rawStatus) {
    return { label: 'Đang xử lý', color: '#475569', bg: '#f1f5f9' };
  }
  const s = rawStatus.toLowerCase().trim();
  switch (s) {
    case 'ready_to_pick':
      return { label: 'Chờ lấy hàng', color: '#b45309', bg: '#fef3c7' };
    case 'picking':
      return { label: 'Đang lấy hàng', color: '#b45309', bg: '#fef3c7' };
    case 'picked':
      return { label: 'Đã lấy hàng', color: '#2563eb', bg: '#eff6ff' };
    case 'storing':
    case 'transporting':
    case 'sorting':
      return { label: 'Đang luân chuyển / Lưu kho', color: '#4f46e5', bg: '#eef2ff' };
    case 'delivering':
      return { label: 'Đang giao hàng', color: '#0284c7', bg: '#e0f2fe' };
    case 'delivered':
      return { label: 'Giao hàng thành công', color: '#16a34a', bg: '#f0fdf4' };
    case 'delivery_fail':
      return { label: 'Giao không thành công', color: '#dc2626', bg: '#fef2f2' };
    case 'waiting_to_return':
    case 'return':
      return { label: 'Đang chuyển hoàn', color: '#ea580c', bg: '#fff7ed' };
    case 'returned':
      return { label: 'Đã chuyển hoàn', color: '#64748b', bg: '#f8fafc' };
    case 'cancel':
      return { label: 'Đã hủy', color: '#dc2626', bg: '#fef2f2' };
    case 'exception':
    case 'damage':
    case 'lost':
      return { label: 'Sự cố vận chuyển', color: '#991b1b', bg: '#fee2e2' };
    default:
      return { label: rawStatus, color: '#334155', bg: '#f1f5f9' };
  }
}

export const fulfillmentApi = {
  createShipment: async (orderId: number, req?: CreateShipmentRequest): Promise<ShipmentResponse> => {
    const res = await authApi.authorizedFetch(`/fulfillment/orders/${orderId}/create-shipment`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(req || {}),
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.message || 'Không thể tạo vận đơn GHN');
    }
    return res.json();
  },

  printWaybill: async (orderId: number): Promise<PrintWaybillResponse> => {
    const res = await authApi.authorizedFetch(`/fulfillment/orders/${orderId}/print-waybill`);
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.message || 'Không thể lấy thông tin in phiếu giao hàng');
    }
    return res.json();
  },

  getTracking: async (orderId: number): Promise<TrackingResponse> => {
    const res = await authApi.authorizedFetch(`/fulfillment/orders/${orderId}/tracking`);
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.message || 'Không thể lấy thông tin vận đơn GHN');
    }
    return res.json();
  },
};
