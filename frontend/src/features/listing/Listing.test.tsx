import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { ListingForm } from './components/ListingForm';
import { SellerListingItem } from './components/SellerListingItem';
import { SellerListingsPage } from '../../pages/SellerListingsPage';
import { listingApi } from './listingApi';
import { AuthContext } from '../auth/context';
import { AuthContextType, UserPrincipal } from '../auth/types';
import { Category } from '../marketplace/types';
import { SellerProductDetail, SellerProductSummary } from './types';

vi.mock('./listingApi', () => ({
  listingApi: {
    getSellerProducts: vi.fn(),
    getSellerProduct: vi.fn(),
    createProduct: vi.fn(),
    updateProduct: vi.fn(),
    publishProduct: vi.fn(),
    submitProduct: vi.fn(),
    uploadMedia: vi.fn(),
    deleteMedia: vi.fn(),
    hideProduct: vi.fn(),
  },
}));

const mockCategories: Category[] = [
  {
    categoryId: 1,
    categoryName: 'Điện tử',
    slug: 'electronics',
    description: 'Thiết bị điện tử',
    displayOrder: 1,
  },
];

const mockSellerProduct: SellerProductSummary = {
  productId: 201,
  title: 'Bàn phím cơ Custom Akko',
  listedPrice: 1200000,
  currency: 'VND',
  status: 'DRAFT',
  condition: 'GOOD',
  location: 'Hà Nội',
  thumbnailUrl: null,
  category: mockCategories[0],
  version: 0,
  createdAt: '2026-09-20T11:00:00Z',
  updatedAt: '2026-09-20T11:00:00Z',
};

const createMockAuth = (user: UserPrincipal | null): AuthContextType => ({
  user,
  isAuthenticated: !!user,
  isLoading: false,
  login: vi.fn(),
  register: vi.fn(),
  logout: vi.fn(),
  reloadCurrentUser: vi.fn().mockResolvedValue(undefined),
});

describe('ListingForm Component', () => {
  it('validates required fields and rejects invalid submissions', async () => {
    const handleSubmit = vi.fn();

    render(
      <ListingForm
        categories={mockCategories}
        onSubmit={handleSubmit}
        isSubmitting={false}
      />
    );

    const submitBtn = screen.getByRole('button', { name: /Lưu tin đăng/i });
    fireEvent.click(submitBtn);

    expect(await screen.findByText(/Vui lòng nhập tiêu đề tin đăng/i)).toBeInTheDocument();
    expect(screen.getByText(/Vui lòng chọn ít nhất một danh mục sản phẩm/i)).toBeInTheDocument();
    expect(screen.getByText(/Giá niêm yết phải lớn hơn 0/i)).toBeInTheDocument();
    expect(handleSubmit).not.toHaveBeenCalled();
  });

  it('submits valid data and prevents double submit when isSubmitting is true', async () => {
    const handleSubmit = vi.fn().mockResolvedValue(undefined);

    const { rerender } = render(
      <ListingForm
        categories={mockCategories}
        onSubmit={handleSubmit}
        isSubmitting={false}
      />
    );

    fireEvent.change(screen.getByLabelText(/Tiêu đề tin đăng/i), {
      target: { value: 'Sony WH-1000XM4 tai nghe chống ồn' },
    });
    fireEvent.click(screen.getByRole('checkbox', { name: mockCategories[0].categoryName }));
    fireEvent.change(screen.getByLabelText(/Tình trạng máy/i), {
      target: { value: 'LIKE_NEW' },
    });
    fireEvent.change(screen.getByLabelText(/Giá niêm yết/i), {
      target: { value: '4500000' },
    });
    fireEvent.change(screen.getByLabelText(/Nội dung bài đăng/i), {
      target: { value: 'Tai nghe ít dùng, còn đủ hộp và cáp sạc.' },
    });

    const submitBtn = screen.getByRole('button', { name: /Lưu tin đăng/i });
    fireEvent.click(submitBtn);

    expect(handleSubmit).toHaveBeenCalledWith(
      expect.objectContaining({
        title: 'Sony WH-1000XM4 tai nghe chống ồn',
        categoryIds: [1],
        condition: 'LIKE_NEW',
        listedPrice: 4500000,
        description: 'Tai nghe ít dùng, còn đủ hộp và cáp sạc.',
      })
    );

    // When isSubmitting is true, the button is disabled
    rerender(
      <ListingForm
        categories={mockCategories}
        onSubmit={handleSubmit}
        isSubmitting={true}
      />
    );

    expect(screen.getByRole('button', { name: /Đang lưu/i })).toBeDisabled();
  });
});

describe('SellerListingItem Component', () => {
  it('renders status badge and actions based on status: draft shows submit for review and edit', () => {
    const handlePublish = vi.fn();
    const handleHide = vi.fn();
    const handleSubmitForReview = vi.fn();

    render(
      <MemoryRouter>
        <SellerListingItem
          product={mockSellerProduct}
          onPublish={handlePublish}
          onHide={handleHide}
          onSubmitForReview={handleSubmitForReview}
          isActionLoading={false}
        />
      </MemoryRouter>
    );

    expect(screen.getByText('Bản nháp')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Gửi duyệt/i })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Đăng bán/i })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Sửa/i })).toBeInTheDocument();
  });
});

describe('SellerListingsPage Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders upgrade explanation and CTA when user is not a SELLER', () => {
    const buyerAuth = createMockAuth({
      userId: 1,
      email: 'buyer@ogshop.com',
      fullName: 'Buyer Only',
      roles: ['BUYER'],
      createdAt: '2026-09-20T10:00:00Z',
    });

    render(
      <AuthContext.Provider value={buyerAuth}>
        <MemoryRouter>
          <SellerListingsPage />
        </MemoryRouter>
      </AuthContext.Provider>
    );

    expect(screen.getByRole('heading', { level: 1, name: /Kích hoạt quyền Người bán/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Kích hoạt quyền Người bán ngay/i })).toBeInTheDocument();
  });

  it('renders empty listings state for seller with no products', async () => {
    const sellerAuth = createMockAuth({
      userId: 2,
      email: 'seller@ogshop.com',
      fullName: 'Valid Seller',
      roles: ['BUYER', 'SELLER'],
      createdAt: '2026-09-20T10:00:00Z',
    });

    vi.mocked(listingApi.getSellerProducts).mockResolvedValue({
      items: [],
      page: 0,
      size: 50,
      totalElements: 0,
      totalPages: 0,
      hasNext: false,
    });

    render(
      <AuthContext.Provider value={sellerAuth}>
        <MemoryRouter>
          <SellerListingsPage />
        </MemoryRouter>
      </AuthContext.Provider>
    );

    await waitFor(() => {
      expect(screen.getByText(/Bạn chưa có tin đăng nào/i)).toBeInTheDocument();
    });

    expect(screen.getByRole('button', { name: /Đăng tin đầu tiên/i })).toBeInTheDocument();
  });

  it('enforces manual review: DRAFT has submit for review CTA instead of direct publish', async () => {
    const sellerAuth = createMockAuth({
      userId: 2,
      email: 'seller@ogshop.com',
      fullName: 'Valid Seller',
      roles: ['BUYER', 'SELLER'],
      createdAt: '2026-09-20T10:00:00Z',
    });

    vi.mocked(listingApi.getSellerProducts)
      .mockResolvedValueOnce({
        items: [mockSellerProduct],
        page: 0,
        size: 50,
        totalElements: 1,
        totalPages: 1,
        hasNext: false,
      })
      .mockResolvedValueOnce({
        items: [{ ...mockSellerProduct, status: 'PENDING' }],
        page: 0,
        size: 50,
        totalElements: 1,
        totalPages: 1,
        hasNext: false,
      });

    vi.mocked(listingApi.submitProduct).mockResolvedValue({
      ...mockSellerProduct,
      status: 'PENDING',
    } as SellerProductDetail);

    render(
      <AuthContext.Provider value={sellerAuth}>
        <MemoryRouter>
          <SellerListingsPage />
        </MemoryRouter>
      </AuthContext.Provider>
    );

    await waitFor(() => {
      expect(screen.getByText('Bàn phím cơ Custom Akko')).toBeInTheDocument();
    });

    // Should NOT have "Đăng bán" or "Đăng lại" button for DRAFT
    expect(screen.queryByRole('button', { name: /Đăng bán/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Đăng lại/i })).not.toBeInTheDocument();

    // Primary CTA is "Gửi duyệt"
    const submitBtn = screen.getByRole('button', { name: /Gửi duyệt/i });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(listingApi.submitProduct).toHaveBeenCalledWith(201);
    });

    expect(await screen.findByText(/Đã gửi tin đăng cho KTV kiểm duyệt thành công/i)).toBeInTheDocument();
  });

  it('allows republishing a HIDDEN listing directly to ACTIVE', async () => {
    const sellerAuth = createMockAuth({
      userId: 2,
      email: 'seller@ogshop.com',
      fullName: 'Valid Seller',
      roles: ['BUYER', 'SELLER'],
      createdAt: '2026-09-20T10:00:00Z',
    });

    const hiddenProduct: SellerProductSummary = {
      ...mockSellerProduct,
      status: 'HIDDEN',
    };

    vi.mocked(listingApi.getSellerProducts)
      .mockResolvedValueOnce({
        items: [hiddenProduct],
        page: 0,
        size: 50,
        totalElements: 1,
        totalPages: 1,
        hasNext: false,
      })
      .mockResolvedValueOnce({
        items: [{ ...hiddenProduct, status: 'ACTIVE' }],
        page: 0,
        size: 50,
        totalElements: 1,
        totalPages: 1,
        hasNext: false,
      });

    vi.mocked(listingApi.publishProduct).mockResolvedValue({
      ...hiddenProduct,
      status: 'ACTIVE',
    } as SellerProductDetail);

    render(
      <AuthContext.Provider value={sellerAuth}>
        <MemoryRouter>
          <SellerListingsPage />
        </MemoryRouter>
      </AuthContext.Provider>
    );

    await waitFor(() => {
      expect(screen.getByText('Bàn phím cơ Custom Akko')).toBeInTheDocument();
    });

    const republishBtn = screen.getByRole('button', { name: /Đăng lại/i });
    fireEvent.click(republishBtn);

    await waitFor(() => {
      expect(listingApi.publishProduct).toHaveBeenCalledWith(201);
    });

    expect(await screen.findByText(/Đăng bán sản phẩm thành công/i)).toBeInTheDocument();
  });

  it('displays real rejection reason and reviewedAt for REJECTED listing', () => {
    const rejectedProduct: SellerProductSummary = {
      ...mockSellerProduct,
      status: 'REJECTED',
      rejectionReason: 'Video không quay cận cảnh góc máy có vết nứt',
      reviewedAt: '2026-10-03T10:30:00Z',
    };

    render(
      <MemoryRouter>
        <SellerListingItem
          product={rejectedProduct}
          onPublish={vi.fn()}
          onHide={vi.fn()}
          onSubmitForReview={vi.fn()}
          isActionLoading={false}
        />
      </MemoryRouter>
    );

    expect(screen.getByText(/Lý do từ chối kiểm duyệt/i)).toBeInTheDocument();
    expect(screen.getByText(/Video không quay cận cảnh góc máy có vết nứt/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Gửi duyệt/i })).toBeInTheDocument();
  });

  it('displays fallback when REJECTED listing has no rejection reason (legacy fixture)', () => {
    const legacyRejectedProduct: SellerProductSummary = {
      ...mockSellerProduct,
      status: 'REJECTED',
      rejectionReason: null,
      reviewedAt: null,
    };

    render(
      <MemoryRouter>
        <SellerListingItem
          product={legacyRejectedProduct}
          onPublish={vi.fn()}
          onHide={vi.fn()}
          onSubmitForReview={vi.fn()}
          isActionLoading={false}
        />
      </MemoryRouter>
    );

    expect(screen.getByText(/chưa có chi tiết lý do từ KTV/i)).toBeInTheDocument();
  });

  it('renders eKYC badge and PENDING state on SellerListingItem', () => {
    const ekycProduct: SellerProductSummary = {
      ...mockSellerProduct,
      status: 'PENDING',
      requiresBuyerEkyc: true,
    };

    render(
      <MemoryRouter>
        <SellerListingItem
          product={ekycProduct}
          onPublish={vi.fn()}
          onHide={vi.fn()}
          isActionLoading={false}
        />
      </MemoryRouter>
    );

    expect(screen.getByText('Chờ kiểm duyệt')).toBeInTheDocument();
    expect(screen.getByText('🛡️ Yêu cầu eKYC')).toBeInTheDocument();
    expect(screen.getByText(/Tin đang được KTV kiểm duyệt nội dung/i)).toBeInTheDocument();
  });

  it('allows checking requiresBuyerEkyc in ListingForm and submitting', async () => {
    const handleSubmit = vi.fn().mockResolvedValue(undefined);

    render(
      <ListingForm
        categories={mockCategories}
        onSubmit={handleSubmit}
        isSubmitting={false}
      />
    );

    fireEvent.change(screen.getByLabelText(/Tiêu đề tin đăng/i), {
      target: { value: 'iPhone 15 Pro Max 256GB' },
    });
    fireEvent.click(screen.getByRole('checkbox', { name: mockCategories[0].categoryName }));
    fireEvent.change(screen.getByLabelText(/Tình trạng máy/i), {
      target: { value: 'LIKE_NEW' },
    });
    fireEvent.change(screen.getByLabelText(/Giá niêm yết/i), {
      target: { value: '25000000' },
    });
    fireEvent.change(screen.getByLabelText(/Nội dung bài đăng/i), {
      target: { value: 'Hàng chính hãng VN/A, pin 100%' },
    });

    const ekycCheckbox = screen.getByLabelText(/Yêu cầu Người mua phải hoàn tất xác thực CCCD/i);
    expect(ekycCheckbox).not.toBeChecked();
    fireEvent.click(ekycCheckbox);
    expect(ekycCheckbox).toBeChecked();

    const submitBtn = screen.getByRole('button', { name: /Lưu tin đăng/i });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(handleSubmit).toHaveBeenCalledWith(
        expect.objectContaining({
          title: 'iPhone 15 Pro Max 256GB',
          requiresBuyerEkyc: true,
        })
      );
    });
  });
});
