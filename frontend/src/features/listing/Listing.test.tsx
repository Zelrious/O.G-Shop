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
  refreshSession: vi.fn().mockResolvedValue(true),
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
    expect(screen.getByText(/Vui lòng chọn danh mục sản phẩm/i)).toBeInTheDocument();
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
    fireEvent.change(screen.getByLabelText(/Danh mục/i), {
      target: { value: '1' },
    });
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
        categoryId: 1,
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
  it('renders status badge and actions based on status', () => {
    const handlePublish = vi.fn();
    const handleHide = vi.fn();

    render(
      <MemoryRouter>
        <SellerListingItem
          product={mockSellerProduct}
          onPublish={handlePublish}
          onHide={handleHide}
          isActionLoading={false}
        />
      </MemoryRouter>
    );

    expect(screen.getByText('Bản nháp')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Đăng bán/i })).toBeInTheDocument();
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

  it('renders listing items and allows publishing a draft', async () => {
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
        items: [{ ...mockSellerProduct, status: 'ACTIVE' }],
        page: 0,
        size: 50,
        totalElements: 1,
        totalPages: 1,
        hasNext: false,
      });

    vi.mocked(listingApi.publishProduct).mockResolvedValue({
      ...mockSellerProduct,
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

    const publishBtn = screen.getByRole('button', { name: /Đăng bán/i });
    fireEvent.click(publishBtn);

    await waitFor(() => {
      expect(listingApi.publishProduct).toHaveBeenCalledWith(201);
    });

    expect(await screen.findByText(/Đăng bán sản phẩm thành công/i)).toBeInTheDocument();
  });
});
