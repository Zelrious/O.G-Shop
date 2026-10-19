import { authApi } from '../auth/authApi';

export interface ShipmentIncident {
  id: number;
  orderId: number;
  trackingNumber?: string | null;
  clientOrderCode?: string | null;
  carrier: string;
  status: string;
  rawGhnStatus?: string | null;
  incidentNotes?: string | null;
  adminReviewRequired: boolean;
  reconciliationAttempts: number;
  createdAt: string;
  updatedAt?: string | null;
}

export type IncidentResolutionAction =
  | 'DISMISS_AND_RESUME'
  | 'MARK_DELIVERED'
  | 'MARK_RETURNED'
  | 'MARK_FAILED';

export interface ResolveIncidentRequest {
  action: IncidentResolutionAction;
  notes?: string;
}

export interface ResolveIncidentResponse {
  orderId: number;
  trackingNumber?: string | null;
  status: string;
  rawGhnStatus?: string | null;
  adminReviewRequired: boolean;
  incidentNotes?: string | null;
  message: string;
}

export const fulfillmentAdminApi = {
  getIncidents: async (): Promise<ShipmentIncident[]> => {
    const res = await authApi.authorizedFetch('/admin/fulfillment/incidents');
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.message || 'Không thể tải danh sách sự cố vận đơn');
    }
    return res.json();
  },

  resolveIncident: async (
    orderId: number,
    req: ResolveIncidentRequest
  ): Promise<ResolveIncidentResponse> => {
    const res = await authApi.authorizedFetch(
      `/admin/fulfillment/orders/${orderId}/resolve-incident`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(req),
      }
    );
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.message || 'Không thể xử lý sự cố vận đơn');
    }
    return res.json();
  },
};
