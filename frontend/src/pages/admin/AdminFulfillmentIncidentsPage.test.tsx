import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { AdminFulfillmentIncidentsPage } from './AdminFulfillmentIncidentsPage';
import { fulfillmentAdminApi, ShipmentIncident } from '../../features/fulfillment/fulfillmentAdminApi';

vi.mock('../../features/fulfillment/fulfillmentAdminApi', () => ({
  fulfillmentAdminApi: {
    getIncidents: vi.fn(),
    resolveIncident: vi.fn(),
  },
}));

describe('AdminFulfillmentIncidentsPage', () => {
  const mockIncidents: ShipmentIncident[] = [
    {
      id: 1,
      orderId: 100,
      trackingNumber: 'GHN_TEST_100',
      clientOrderCode: 'OG-CLIENT-100',
      carrier: 'GHN',
      status: 'PENDING',
      rawGhnStatus: 'damage',
      incidentNotes: 'Bưu phẩm bị rách bao bì khi phân loại',
      adminReviewRequired: true,
      reconciliationAttempts: 3,
      createdAt: '2026-10-18T10:00:00Z',
      updatedAt: '2026-10-18T10:30:00Z',
    },
    {
      id: 2,
      orderId: 200,
      trackingNumber: 'GHN_TEST_200',
      clientOrderCode: 'OG-CLIENT-200',
      carrier: 'GHN',
      status: 'IN_TRANSIT',
      rawGhnStatus: 'delivery_fail',
      incidentNotes: 'Khách hàng không nghe máy quá 3 lần',
      adminReviewRequired: true,
      reconciliationAttempts: 1,
      createdAt: '2026-10-18T09:00:00Z',
      updatedAt: '2026-10-18T11:00:00Z',
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders incidents queue list and summary cards successfully', async () => {
    (fulfillmentAdminApi.getIncidents as unknown as ReturnType<typeof vi.fn>).mockResolvedValue(mockIncidents);

    render(<AdminFulfillmentIncidentsPage />);

    expect(screen.getByText(/Đang tải danh sách sự cố/i)).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText('Sự Cố Vận Đơn (GHN Incidents)')).toBeInTheDocument();
      expect(screen.getByText('#100')).toBeInTheDocument();
      expect(screen.getByText('#200')).toBeInTheDocument();
      expect(screen.getByText('GHN_TEST_100')).toBeInTheDocument();
      expect(screen.getByText('GHN_TEST_200')).toBeInTheDocument();
      expect(screen.getByText('damage')).toBeInTheDocument();
      expect(screen.getByText('delivery_fail')).toBeInTheDocument();
    });
  });

  it('opens resolve modal and submits DISMISS_AND_RESUME decision', async () => {
    (fulfillmentAdminApi.getIncidents as unknown as ReturnType<typeof vi.fn>).mockResolvedValue(mockIncidents);
    (fulfillmentAdminApi.resolveIncident as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
      orderId: 100,
      trackingNumber: 'GHN_TEST_100',
      status: 'PENDING',
      rawGhnStatus: 'damage',
      adminReviewRequired: false,
      incidentNotes: 'DISMISS_AND_RESUME by admin',
      message: 'Đã gỡ cờ sự cố và khôi phục đối soát tự động cho đơn hàng #100',
    });

    render(<AdminFulfillmentIncidentsPage />);

    await waitFor(() => {
      expect(screen.getByText('#100')).toBeInTheDocument();
    });

    // Click on the first "Xử lý" button
    const actionButtons = screen.getAllByRole('button', { name: /^Xử lý$/i });
    fireEvent.click(actionButtons[0]);

    // Verify modal is open
    expect(screen.getByText('Xử Lý Sự Cố Vận Đơn #100')).toBeInTheDocument();
    expect(screen.getByText(/Bỏ qua sự cố & Khôi phục đối soát tự động/i)).toBeInTheDocument();

    // Type admin notes
    const textarea = screen.getByPlaceholderText(/Nhập căn cứ xử lý/i);
    fireEvent.change(textarea, { target: { value: 'Đã làm việc với kho GHN' } });

    // Click confirm
    const confirmButton = screen.getByRole('button', { name: /Xác nhận xử lý/i });
    fireEvent.click(confirmButton);

    await waitFor(() => {
      expect(fulfillmentAdminApi.resolveIncident).toHaveBeenCalledWith(100, {
        action: 'DISMISS_AND_RESUME',
        notes: 'Đã làm việc với kho GHN',
      });
      expect(screen.getByText(/Đã gỡ cờ sự cố và khôi phục đối soát tự động/i)).toBeInTheDocument();
    });
  });

  it('allows selecting MARK_DELIVERED action in modal', async () => {
    (fulfillmentAdminApi.getIncidents as unknown as ReturnType<typeof vi.fn>).mockResolvedValue(mockIncidents);
    (fulfillmentAdminApi.resolveIncident as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
      orderId: 200,
      trackingNumber: 'GHN_TEST_200',
      status: 'DELIVERED',
      rawGhnStatus: 'delivered',
      adminReviewRequired: false,
      incidentNotes: 'MARK_DELIVERED',
      message: 'Đã xác nhận giao hàng thành công (DELIVERED) cho đơn hàng #200',
    });

    render(<AdminFulfillmentIncidentsPage />);

    await waitFor(() => {
      expect(screen.getByText('#200')).toBeInTheDocument();
    });

    const actionButtons = screen.getAllByRole('button', { name: /^Xử lý$/i });
    fireEvent.click(actionButtons[1]); // open modal for order #200

    // Select MARK_DELIVERED radio
    const radioDelivered = screen.getByDisplayValue('MARK_DELIVERED');
    fireEvent.click(radioDelivered);

    const confirmButton = screen.getByRole('button', { name: /Xác nhận xử lý/i });
    fireEvent.click(confirmButton);

    await waitFor(() => {
      expect(fulfillmentAdminApi.resolveIncident).toHaveBeenCalledWith(200, {
        action: 'MARK_DELIVERED',
        notes: '',
      });
    });
  });
});
