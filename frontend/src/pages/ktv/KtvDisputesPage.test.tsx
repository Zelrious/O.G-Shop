import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { beforeEach, afterEach, describe, it, expect, vi } from 'vitest';
import { KtvDisputesPage } from './KtvDisputesPage';
import { disputeApi } from '../../features/disputes/disputeApi';

vi.mock('../../features/disputes/disputeApi', () => ({
  disputeApi: {
    getDisputes: vi.fn(),
    getDisputeDetail: vi.fn(),
    submitDecision: vi.fn(),
  },
}));

describe('KtvDisputesPage Component', () => {
  const sampleDisputes = [
    {
      disputeId: 101,
      orderId: 1001,
      orderCode: 'ORD-1001',
      buyerName: 'Nguyễn Văn Mua',
      sellerName: 'Trần Văn Bán',
      productTitle: 'Đồng hồ cơ Thụy Sĩ cổ',
      disputeAmount: 1500000,
      reason: 'Mặt kính bị vỡ khi nhận hàng',
      status: 'WAITING_KTV_REVIEW',
      createdAt: '2026-10-20T10:00:00Z',
    },
  ];

  const sampleDetail = {
    disputeId: 101,
    orderId: 1001,
    orderCode: 'ORD-1001',
    buyerId: 201,
    buyerName: 'Nguyễn Văn Mua',
    buyerEmail: 'mua@example.com',
    sellerId: 301,
    sellerName: 'Trần Văn Bán',
    sellerEmail: 'ban@example.com',
    productTitle: 'Đồng hồ cơ Thụy Sĩ cổ',
    disputeAmount: 1500000,
    reason: 'Mặt kính bị vỡ khi nhận hàng',
    description: 'Khi bóc hàng thấy mặt kính nứt toác',
    buyerEvidenceUrls: ['https://example.com/unboxing1.jpg'],
    videoUnboxingUrl: 'https://example.com/unboxing.mp4',
    sellerResponse: 'Shop đã bọc xốp cẩn thận 3 lớp',
    sellerEvidenceUrls: ['https://example.com/pack1.jpg'],
    sellerResponseDueAt: '2026-10-22T10:00:00Z',
    status: 'WAITING_KTV_REVIEW',
    verdictAction: null,
    verdictNotes: null,
    refundAmount: null,
    penaltyPoints: null,
    resolvedBy: null,
    resolvedAt: null,
    createdAt: '2026-10-20T10:00:00Z',
    updatedAt: '2026-10-20T12:00:00Z',
  };

  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(disputeApi.getDisputes).mockResolvedValue({
      items: sampleDisputes,
      page: 0,
      size: 50,
      totalElements: 1,
      totalPages: 1,
    });
    vi.mocked(disputeApi.getDisputeDetail).mockResolvedValue(sampleDetail);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('renders dispute table with data from real API', async () => {
    render(<KtvDisputesPage />);

    expect(screen.getByText('Phân xử tranh chấp & Khiếu nại giao dịch')).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByText('ORD-1001')).toBeInTheDocument();
      expect(screen.getByText('Đồng hồ cơ Thụy Sĩ cổ')).toBeInTheDocument();
      expect(screen.getByText('Nguyễn Văn Mua')).toBeInTheDocument();
      expect(screen.getByText('Trần Văn Bán')).toBeInTheDocument();
    });
  });

  it('opens 2-way visual evidence inspection modal and displays buyer & seller evidence', async () => {
    render(<KtvDisputesPage />);

    await waitFor(() => expect(screen.getByText('ORD-1001')).toBeInTheDocument());

    const actionDropdownBtn = screen.getByLabelText('Thao tác phân xử đơn ORD-1001');
    fireEvent.click(actionDropdownBtn);

    const arbitrateBtn = screen.getByText('Đối soát & Phán quyết');
    fireEvent.click(arbitrateBtn);

    await waitFor(() => {
      expect(screen.getByText('Đối soát & Phán quyết: ORD-1001')).toBeInTheDocument();
      expect(screen.getByText(/Khi bóc hàng thấy mặt kính nứt toác/)).toBeInTheDocument();
      expect(screen.getByText(/Shop đã bọc xốp cẩn thận 3 lớp/)).toBeInTheDocument();
      expect(screen.getByText(/Phóng to video trong Lightbox/)).toBeInTheDocument();
    });
  });

  it('submits verdict with penalty points and notes', async () => {
    vi.mocked(disputeApi.submitDecision).mockResolvedValue({
      disputeId: 101,
      verdictAction: 'RETURN_APPROVED',
      status: 'RETURN_APPROVED',
      message: 'Đã chấp thuận',
    });

    render(<KtvDisputesPage />);

    await waitFor(() => expect(screen.getByText('ORD-1001')).toBeInTheDocument());

    fireEvent.click(screen.getByLabelText('Thao tác phân xử đơn ORD-1001'));
    fireEvent.click(screen.getByText('Đối soát & Phán quyết'));

    await waitFor(() => expect(screen.getByText('Đối soát & Phán quyết: ORD-1001')).toBeInTheDocument());

    const confirmBtn = screen.getByText('Xác nhận ban hành phán quyết');
    expect(confirmBtn).toBeDisabled();

    // Fill verdict notes
    const textarea = screen.getByPlaceholderText(/Ghi rõ kết quả đối soát video unboxing/);
    fireEvent.change(textarea, {
      target: { value: 'Video unboxing rõ nét mặt vỡ, chấp thuận cho trả hàng.' },
    });

    // Choose penalty -10đ
    fireEvent.click(screen.getByText('-10đ (Gian dối tình trạng hàng)'));

    expect(confirmBtn).toBeEnabled();
    fireEvent.click(confirmBtn);

    await waitFor(() => {
      expect(disputeApi.submitDecision).toHaveBeenCalledWith(101, {
        verdictAction: 'RETURN_APPROVED',
        verdictNotes: 'Video unboxing rõ nét mặt vỡ, chấp thuận cho trả hàng.',
        penaltyPoints: 10,
      });
      expect(screen.getByText(/Đã ghi nhận phán quyết/)).toBeInTheDocument();
    });
  });

  it('filters disputes and shows dynamic badge count on status tabs', async () => {
    render(<KtvDisputesPage />);

    await waitFor(() => expect(screen.getByText('ORD-1001')).toBeInTheDocument());

    // Check status tab with badge count
    const allTab = screen.getByRole('button', { name: /Tất cả/ });
    expect(allTab).toBeInTheDocument();
    expect(allTab).toHaveTextContent('1');

    const waitingReviewTab = screen.getByRole('button', { name: /Chờ KTV phán quyết/ });
    expect(waitingReviewTab).toBeInTheDocument();
    expect(waitingReviewTab).toHaveTextContent('1');

    const waitingSellerTab = screen.getByRole('button', { name: /Chờ Seller/ });
    expect(waitingSellerTab).toBeInTheDocument();
    expect(waitingSellerTab).toHaveTextContent('0');
  });

  it('supports search input with debouncing, clear button and left-aligned menu', async () => {
    render(<KtvDisputesPage />);

    await waitFor(() => expect(screen.getByText('ORD-1001')).toBeInTheDocument());

    const searchInput = screen.getByPlaceholderText('Tìm theo mã đơn hoặc người dùng...');
    expect(searchInput).toBeInTheDocument();

    // Type query
    fireEvent.change(searchInput, { target: { value: 'Thụy Sĩ' } });
    expect(searchInput).toHaveValue('Thụy Sĩ');

    // Clear button should be visible
    const clearBtn = screen.getByLabelText('Xóa tìm kiếm');
    expect(clearBtn).toBeInTheDocument();

    // Click clear button
    fireEvent.click(clearBtn);
    expect(searchInput).toHaveValue('');

    // Open action dropdown and verify menu item left-alignment styling
    const actionBtn = screen.getByLabelText('Thao tác phân xử đơn ORD-1001');
    fireEvent.click(actionBtn);

    const arbitrateMenuItem = screen.getByRole('menuitem', { name: /Đối soát & Phán quyết/ });
    expect(arbitrateMenuItem).toHaveStyle({ textAlign: 'left', justifyContent: 'flex-start' });
  });
});
