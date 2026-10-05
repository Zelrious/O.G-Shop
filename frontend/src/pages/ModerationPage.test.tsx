import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { ModerationPage } from './ModerationPage';
import { ApiError, listingApi, ModerationProduct } from '../features/listing';

vi.mock('../features/listing/listingApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../features/listing/listingApi')>();
  return {
    ...actual,
    listingApi: {
      getPendingProducts: vi.fn(),
      approveProduct: vi.fn(),
      rejectProduct: vi.fn(),
    },
  };
});

const mockModerationProduct: ModerationProduct = {
  productId: 101,
  sellerId: 10,
  title: 'Máy ảnh cơ Canon AE-1',
  description: 'Máy chụp tốt, cơ êm, kèm lens 50mm f/1.8',
  listedPrice: 3500000,
  currency: 'VND',
  condition: 'GOOD',
  usageDuration: '6 tháng',
  defects: 'Xước nhẹ ở đáy máy',
  repairHistory: 'Chưa từng sửa chữa',
  includedAccessories: 'Dây đeo da, nắp lens',
  location: 'TP. Hồ Chí Minh',
  status: 'PENDING',
  category: { categoryId: 1, categoryName: 'Máy ảnh', slug: 'cameras', description: 'Máy ảnh cơ và số', displayOrder: 1 },
  categories: [
    { categoryId: 1, categoryName: 'Máy ảnh', slug: 'cameras', description: 'Máy ảnh cơ và số', displayOrder: 1 },
    { categoryId: 2, categoryName: 'Đồ cổ điển', slug: 'vintage', description: 'Đồ cổ điển và sưu tầm', displayOrder: 2 },
  ],
  media: [
    {
      mediaId: 1,
      mediaType: 'IMAGE',
      mediaUrl: 'https://example.com/canon-ae1.jpg',
      displayOrder: 0,
    },
    {
      mediaId: 2,
      mediaType: 'VIDEO',
      mediaUrl: 'https://example.com/canon-ae1-close-up.mp4',
      displayOrder: 1,
    },
  ],
  requiresBuyerEkyc: true,
  version: 3,
  createdAt: '2026-10-01T10:00:00Z',
  updatedAt: '2026-10-01T10:05:00Z',
};

describe('ModerationPage Component (UC70)', () => {
  it('submits a selected row once during a pending bulk request and reloads totals', async () => {
    let finish!: (value: { productId: number; status: string; message: string }) => void;
    const pending = new Promise<{ productId: number; status: string; message: string }>(resolve => { finish = resolve; });
    const another = { ...mockModerationProduct, productId: 102, title: 'Another product' };
    vi.mocked(listingApi.getPendingProducts).mockResolvedValueOnce({ items: [mockModerationProduct, another], page: 0, size: 20, totalElements: 3, totalPages: 1, hasNext: false })
      .mockResolvedValue({ items: [another], page: 0, size: 20, totalElements: 2, totalPages: 1, hasNext: false });
    vi.mocked(listingApi.approveProduct).mockReturnValue(pending);
    render(<ModerationPage />);
    fireEvent.click(await screen.findByRole('checkbox', { name: 'Chọn tin #101' }));
    const button = screen.getByRole('button', { name: /Duyệt nhanh các tin đã chọn/ });
    fireEvent.click(button);
    fireEvent.click(button);
    expect(button).toBeDisabled();
    expect(listingApi.approveProduct).toHaveBeenCalledTimes(1);
    finish({ productId: 101, status: 'ACTIVE', message: 'Success' });
    await screen.findByText(/Đã duyệt 1\/1 tin/);
    expect(screen.getByText(/Tổng/)).toHaveTextContent('Tổng 2 tin chờ duyệt');
    expect(listingApi.getPendingProducts).toHaveBeenCalledTimes(2);
    expect(screen.queryByText(mockModerationProduct.title)).not.toBeInTheDocument();
    expect(listingApi.approveProduct).toHaveBeenCalledTimes(1);
  });

  it('reports a partial bulk outcome and preserves failed rows', async () => {
    const another = { ...mockModerationProduct, productId: 102, title: 'Another product' };
    vi.mocked(listingApi.getPendingProducts).mockResolvedValueOnce({ items: [mockModerationProduct, another], page: 0, size: 20, totalElements: 2, totalPages: 1, hasNext: false })
      .mockResolvedValue({ items: [another], page: 0, size: 20, totalElements: 1, totalPages: 1, hasNext: false });
    vi.mocked(listingApi.approveProduct).mockResolvedValueOnce({ productId: 101, status: 'ACTIVE', message: 'Success' })
      .mockRejectedValueOnce(new Error('Network failure'));
    render(<ModerationPage />);
    fireEvent.click(await screen.findByRole('checkbox', { name: 'Chọn tất cả tin' }));
    fireEvent.click(screen.getByRole('button', { name: /Duyệt nhanh các tin đã chọn/ }));
    await screen.findByText(/Đã duyệt 1\/2 tin/);
    expect(screen.getByText('Another product')).toBeInTheDocument();
    expect(listingApi.approveProduct).toHaveBeenCalledTimes(2);
  });

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('returns to a valid page after bulk approval empties the last page', async () => {
    const last = { ...mockModerationProduct, productId:103, title:'Last page product' };
    const response = (items: ModerationProduct[], page:number, totalElements:number, totalPages:number) =>
      ({items,page,size:20,totalElements,totalPages,hasNext:page < totalPages-1});
    vi.mocked(listingApi.getPendingProducts).mockResolvedValueOnce(response([mockModerationProduct],0,21,2))
      .mockResolvedValueOnce(response([last],1,21,2))
      .mockResolvedValueOnce(response([],1,20,1))
      .mockResolvedValue(response([mockModerationProduct],0,20,1));
    vi.mocked(listingApi.approveProduct).mockResolvedValue({productId:103,status:'ACTIVE',message:'Success'});
    render(<ModerationPage />);
    await screen.findByText(mockModerationProduct.title);
    fireEvent.click(screen.getByRole('button',{name:/Trang sau/}));
    await screen.findByText(last.title);
    fireEvent.click(screen.getByRole('checkbox',{name:'Chọn tin #103'}));
    fireEvent.click(screen.getByRole('button',{name:/Duyệt nhanh các tin đã chọn/}));
    await screen.findByText(mockModerationProduct.title);
    expect(listingApi.getPendingProducts).toHaveBeenLastCalledWith(0,20);
    expect(screen.getByText(/Tổng/)).toHaveTextContent('Tổng 20 tin chờ duyệt');
  });

  it('renders empty state when there are no pending listings', async () => {
    vi.mocked(listingApi.getPendingProducts).mockResolvedValue({
      items: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
      hasNext: false,
    });

    render(<ModerationPage />);

    await waitFor(() => {
      expect(screen.getByText(/Không có tin nào đang chờ duyệt/i)).toBeInTheDocument();
    });
  });

  it('renders pending listing with full inspection details, categories, eKYC, and AI badge', async () => {
    vi.mocked(listingApi.getPendingProducts).mockResolvedValue({
      items: [mockModerationProduct],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      hasNext: false,
    });

    render(<ModerationPage />);

    await waitFor(() => {
      expect(screen.getByText('Máy ảnh cơ Canon AE-1')).toBeInTheDocument();
    });

    // Check multiple categories
    expect(screen.getByText(/Máy ảnh · Đồ cổ điển/i)).toBeInTheDocument();

    // Check badges
    expect(screen.getByText(/PENDING/i)).toBeInTheDocument();
    expect(screen.getByText(/Yêu cầu eKYC Người mua/i)).toBeInTheDocument();
    expect(screen.getByText(/Chưa kiểm tra AI/i)).toBeInTheDocument();
    expect(screen.getByText(/Mã tin: #101 \| Người bán: #10 \| Bản: v3/i)).toBeInTheDocument();

    // Check inspection checklist
    expect(screen.getByText('6 tháng')).toBeInTheDocument();
    expect(screen.getByText('Xước nhẹ ở đáy máy')).toBeInTheDocument();
    expect(screen.getByText('Chưa từng sửa chữa')).toBeInTheDocument();
    expect(screen.getByText('Dây đeo da, nắp lens')).toBeInTheDocument();

    // Check Rule V6 media
    expect(screen.getByText(/Media kiểm định theo Rule V6 \(1 ảnh, 1 video\)/i)).toBeInTheDocument();
  });

  it('approves a pending product with expectedVersion and commandKey', async () => {
    vi.mocked(listingApi.getPendingProducts).mockResolvedValue({
      items: [mockModerationProduct],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      hasNext: false,
    });

    vi.mocked(listingApi.approveProduct).mockResolvedValue({
      productId: 101,
      status: 'ACTIVE',
      message: 'Phê duyệt thành công',
    });

    render(<ModerationPage />);

    await waitFor(() => {
      expect(screen.getByText('Máy ảnh cơ Canon AE-1')).toBeInTheDocument();
    });

    const approveBtn = screen.getByRole('button', { name: /Phê duyệt \(ACTIVE\)/i });
    expect(approveBtn).not.toBeDisabled();
    fireEvent.click(approveBtn);

    await waitFor(() => {
      expect(listingApi.approveProduct).toHaveBeenCalledWith(
        101,
        expect.objectContaining({
          expectedVersion: 3,
          commandKey: expect.stringMatching(/^ck-app-101-3-/),
        })
      );
    });

    expect(await screen.findByText(/Phê duyệt thành công/i)).toBeInTheDocument();
    expect(screen.queryByText('Máy ảnh cơ Canon AE-1')).not.toBeInTheDocument();
  });

  it('validates rejection reason and submits rejection with expectedVersion and commandKey', async () => {
    vi.mocked(listingApi.getPendingProducts).mockResolvedValue({
      items: [mockModerationProduct],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      hasNext: false,
    });

    vi.mocked(listingApi.rejectProduct).mockResolvedValue({
      productId: 101,
      status: 'REJECTED',
      message: 'Đã từ chối tin',
    });

    render(<ModerationPage />);

    await waitFor(() => {
      expect(screen.getByText('Máy ảnh cơ Canon AE-1')).toBeInTheDocument();
    });

    // Click "Từ chối tin" to open dialog
    const openRejectBtn = screen.getByRole('button', { name: /Từ chối tin/i });
    fireEvent.click(openRejectBtn);

    expect(screen.getByLabelText(/Lý do từ chối duyệt/i)).toBeInTheDocument();

    // Confirm button should be disabled when input is empty
    const confirmRejectBtn = screen.getByRole('button', { name: /Xác nhận từ chối/i });
    expect(confirmRejectBtn).toBeDisabled();

    // Enter reason
    const reasonInput = screen.getByLabelText(/Lý do từ chối duyệt/i);
    fireEvent.change(reasonInput, {
      target: { value: 'Video chưa quay cận cảnh vết xước ở đáy máy' },
    });

    expect(confirmRejectBtn).not.toBeDisabled();
    fireEvent.click(confirmRejectBtn);

    await waitFor(() => {
      expect(listingApi.rejectProduct).toHaveBeenCalledWith(
        101,
        expect.objectContaining({
          reason: 'Video chưa quay cận cảnh vết xước ở đáy máy',
          expectedVersion: 3,
          commandKey: expect.stringMatching(/^ck-rej-101-3-/),
        })
      );
    });

    expect(await screen.findByText(/Đã từ chối tin/i)).toBeInTheDocument();
    expect(screen.queryByText('Máy ảnh cơ Canon AE-1')).not.toBeInTheDocument();
  });

  it('handles 409 conflict error and displays reload button', async () => {
    vi.mocked(listingApi.getPendingProducts).mockResolvedValue({
      items: [mockModerationProduct],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      hasNext: false,
    });

    vi.mocked(listingApi.approveProduct).mockRejectedValue(
      new ApiError('Yêu cầu thất bại (409): Tin đăng đã được kiểm duyệt hoặc sửa đổi.', 409, 'PRODUCT_VERSION_CONFLICT')
    );

    render(<ModerationPage />);

    await waitFor(() => {
      expect(screen.getByText('Máy ảnh cơ Canon AE-1')).toBeInTheDocument();
    });

    const approveBtn = screen.getByRole('button', { name: /Phê duyệt \(ACTIVE\)/i });
    fireEvent.click(approveBtn);

    await waitFor(() => {
      expect(screen.getByText(/Tải lại dữ liệu mới nhất \(HTTP 409\)/i)).toBeInTheDocument();
    });
  });

  it('disables approve button when video is missing (Rule V6 violation)', async () => {
    const productWithoutVideo: ModerationProduct = {
      ...mockModerationProduct,
      media: [
        {
          mediaId: 1,
          mediaType: 'IMAGE',
          mediaUrl: 'https://example.com/photo.jpg',
          displayOrder: 0,
        },
      ],
    };

    vi.mocked(listingApi.getPendingProducts).mockResolvedValue({
      items: [productWithoutVideo],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      hasNext: false,
    });

    render(<ModerationPage />);

    await waitFor(() => {
      expect(screen.getByText('Máy ảnh cơ Canon AE-1')).toBeInTheDocument();
    });

    const approveBtn = screen.getByRole('button', { name: /Phê duyệt \(ACTIVE\)/i });
    expect(approveBtn).toBeDisabled();
    expect(screen.getByText(/Tin này thiếu video cận cảnh sản phẩm!/i)).toBeInTheDocument();
  });

  it('handles 409 conflict error via typed ApiError without keyword matching and displays reload button', async () => {
    vi.mocked(listingApi.getPendingProducts).mockResolvedValue({
      items: [mockModerationProduct],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      hasNext: false,
    });

    // Error message contains no keywords like "409", "xung đột", or "phiên bản"
    vi.mocked(listingApi.approveProduct).mockRejectedValue(
      new ApiError('Resource state mismatch occurred', 409, 'PRODUCT_VERSION_CONFLICT')
    );

    render(<ModerationPage />);

    await waitFor(() => {
      expect(screen.getByText('Máy ảnh cơ Canon AE-1')).toBeInTheDocument();
    });

    const approveBtn = screen.getByRole('button', { name: /Phê duyệt \(ACTIVE\)/i });
    fireEvent.click(approveBtn);

    await waitFor(() => {
      expect(screen.getByText(/Tải lại dữ liệu mới nhất \(HTTP 409\)/i)).toBeInTheDocument();
    });
  });

  it('reuses the exact same commandKey on retry when network or server call fails', async () => {
    vi.mocked(listingApi.getPendingProducts).mockResolvedValue({
      items: [mockModerationProduct],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      hasNext: false,
    });

    let callCount = 0;
    let firstCommandKey = '';
    vi.mocked(listingApi.approveProduct).mockImplementation(async (_id, payload) => {
      callCount++;
      if (callCount === 1) {
        firstCommandKey = payload.commandKey;
        throw new Error('Network timeout during moderation');
      }
      return {
        productId: 101,
        status: 'ACTIVE',
        message: 'Phê duyệt thành công sau khi thử lại',
      };
    });

    render(<ModerationPage />);

    await waitFor(() => {
      expect(screen.getByText('Máy ảnh cơ Canon AE-1')).toBeInTheDocument();
    });

    const approveBtn = screen.getByRole('button', { name: /Phê duyệt \(ACTIVE\)/i });
    fireEvent.click(approveBtn);

    await waitFor(() => {
      expect(screen.getByText(/Network timeout during moderation/i)).toBeInTheDocument();
    });
    expect(callCount).toBe(1);
    expect(firstCommandKey).toBeTruthy();

    // Click retry with same parameters
    fireEvent.click(approveBtn);

    await waitFor(() => {
      expect(callCount).toBe(2);
    });

    expect(listingApi.approveProduct).toHaveBeenLastCalledWith(
      101,
      expect.objectContaining({
        expectedVersion: 3,
        commandKey: firstCommandKey,
      })
    );
  });
});
