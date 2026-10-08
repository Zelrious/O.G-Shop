import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { ProductCard } from './components/ProductCard';
import { MarketplaceFilters } from './components/MarketplaceFilters';
import { MarketplacePage, ProductDetailPage } from '../../pages';
import { marketplaceApi } from './marketplaceApi';
import { Category, ProductDetail, ProductSummary } from './types';

vi.mock('./marketplaceApi', () => ({
  marketplaceApi: {
    getCategories: vi.fn(),
    getProducts: vi.fn(),
    getProductDetail: vi.fn(),
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
  {
    categoryId: 2,
    categoryName: 'Thời trang',
    slug: 'fashion',
    description: 'Quần áo phụ kiện',
    displayOrder: 2,
  },
];

const mockProductSummary: ProductSummary = {
  productId: 101,
  title: 'iPhone 13 Pro Max 128GB',
  listedPrice: 15500000,
  currency: 'VND',
  condition: 'LIKE_NEW',
  location: 'Quận 1, TP.HCM',
  thumbnailUrl: null,
  category: mockCategories[0],
  seller: {
    sellerId: 5,
    displayName: 'Thanh Seller',
    trustLabel: 'Người bán MVP',
  },
  createdAt: '2026-09-20T10:00:00Z',
};

describe('ProductCard Component', () => {
  it('renders product summary data correctly with Vietnamese formatting', () => {
    render(
      <MemoryRouter>
        <ProductCard product={mockProductSummary} />
      </MemoryRouter>
    );

    expect(screen.getByText('iPhone 13 Pro Max 128GB')).toBeInTheDocument();
    expect(screen.getByText(/15\.500\.000/)).toBeInTheDocument();
    expect(screen.getByText('Điện tử')).toBeInTheDocument();
    expect(screen.getByText(/Như mới/)).toBeInTheDocument();
    expect(screen.getByText(/Thanh Seller/)).toBeInTheDocument();
    expect(screen.getByText(/Người bán MVP/)).toBeInTheDocument();
    expect(screen.getByText(/Chưa có ảnh/)).toBeInTheDocument();
  });
});

describe('MarketplaceFilters Component', () => {
  it('triggers onApplyFilters with entered values when submitted', () => {
    const handleApply = vi.fn();
    const handleReset = vi.fn();

    render(
      <MarketplaceFilters
        categories={mockCategories}
        filters={{}}
        onApplyFilters={handleApply}
        onResetFilters={handleReset}
      />
    );

    const searchInput = screen.getByPlaceholderText(/Nhập tên sản phẩm cần tìm/i);
    fireEvent.change(searchInput, { target: { value: 'MacBook' } });

    const applyButton = screen.getByRole('button', { name: /Áp dụng/i });
    fireEvent.click(applyButton);

    expect(handleApply).toHaveBeenCalledWith(
      expect.objectContaining({
        query: 'MacBook',
        page: 0,
      })
    );
  });

  it('triggers onResetFilters when reset button is clicked', () => {
    const handleApply = vi.fn();
    const handleReset = vi.fn();

    render(
      <MarketplaceFilters
        categories={mockCategories}
        filters={{ query: 'iPad' }}
        onApplyFilters={handleApply}
        onResetFilters={handleReset}
      />
    );

    const resetButton = screen.getByRole('button', { name: /Đặt lại/i });
    fireEvent.click(resetButton);

    expect(handleReset).toHaveBeenCalled();
  });

  it('toggles filter drawer and supports multi-category selection', () => {
    const handleApply = vi.fn();
    const handleReset = vi.fn();

    render(
      <MarketplaceFilters
        categories={mockCategories}
        filters={{}}
        onApplyFilters={handleApply}
        onResetFilters={handleReset}
      />
    );

    // Filter drawer is initially collapsed
    expect(screen.queryByText(/Tùy chọn lọc chi tiết/i)).not.toBeInTheDocument();

    // Click "Bộ lọc" toggle
    const toggleButton = screen.getByRole('button', { name: /Bộ lọc/i });
    fireEvent.click(toggleButton);

    // Filter drawer is now open
    expect(screen.getByText(/Tùy chọn lọc chi tiết/i)).toBeInTheDocument();

    // Click multiple categories: "Điện tử" and "Thời trang"
    const cat1 = screen.getByRole('button', { name: /Điện tử/i });
    const cat2 = screen.getByRole('button', { name: /Thời trang/i });
    fireEvent.click(cat1);
    fireEvent.click(cat2);

    // Click "Áp dụng bộ lọc" inside the drawer
    const applyDrawerBtn = screen.getByRole('button', { name: /Áp dụng bộ lọc/i });
    fireEvent.click(applyDrawerBtn);

    expect(handleApply).toHaveBeenCalledWith(
      expect.objectContaining({
        categoryIds: [1, 2],
        page: 0,
      })
    );
  });

  it('renders active filter tags with x button and removes criteria on click', () => {
    const handleApply = vi.fn();
    const handleReset = vi.fn();

    render(
      <MarketplaceFilters
        categories={mockCategories}
        filters={{
          categoryIds: [1, 2],
          conditions: ['LIKE_NEW'],
        }}
        onApplyFilters={handleApply}
        onResetFilters={handleReset}
      />
    );

    // Active tags list should display each criteria
    expect(screen.getByText('Danh mục: Điện tử')).toBeInTheDocument();
    expect(screen.getByText('Danh mục: Thời trang')).toBeInTheDocument();
    expect(screen.getByText(/Tình trạng: Như mới/i)).toBeInTheDocument();

    // Click 'x' button on "Danh mục: Điện tử" tag
    const removeCat1Btn = screen.getByRole('button', { name: /Xóa tiêu chí Danh mục: Điện tử/i });
    fireEvent.click(removeCat1Btn);

    expect(handleApply).toHaveBeenCalledWith(
      expect.objectContaining({
        categoryIds: [2],
        conditions: ['LIKE_NEW'],
      })
    );
  });

  it('clears all filters when Xóa tất cả button is clicked in active tags', () => {
    const handleApply = vi.fn();
    const handleReset = vi.fn();

    render(
      <MarketplaceFilters
        categories={mockCategories}
        filters={{
          query: 'ThinkPad',
          categoryIds: [1],
        }}
        onApplyFilters={handleApply}
        onResetFilters={handleReset}
      />
    );

    const clearAllBtn = screen.getByRole('button', { name: /Xóa tất cả/i });
    fireEvent.click(clearAllBtn);

    expect(handleReset).toHaveBeenCalled();
  });

  it('renders filter chips without checkmark icon ✓ and in scrollable containers', () => {
    render(
      <MarketplaceFilters
        categories={mockCategories}
        filters={{ categoryIds: [1] }}
        onApplyFilters={vi.fn()}
        onResetFilters={vi.fn()}
      />
    );

    // Open drawer
    fireEvent.click(screen.getByRole('button', { name: /^Bộ lọc/i }));

    // Verify category chip is selected and does NOT have a checkmark icon '✓'
    const cat1Btn = screen.getByRole('button', { name: /^Điện tử$/i });
    expect(cat1Btn).toHaveClass('og-filter-chip--active');
    expect(cat1Btn.textContent).toBe('Điện tử');
    expect(screen.queryByText('✓')).not.toBeInTheDocument();

    // Verify horizontal scroll container exists
    const scrollContainers = document.querySelectorAll('.og-filter-chips-scroll');
    expect(scrollContainers.length).toBeGreaterThanOrEqual(2);
  });

  it('supports dual range slider and manual price inputs with quick presets', () => {
    const handleApply = vi.fn();

    render(
      <MarketplaceFilters
        categories={mockCategories}
        filters={{}}
        onApplyFilters={handleApply}
        onResetFilters={vi.fn()}
      />
    );

    // Open drawer
    fireEvent.click(screen.getByRole('button', { name: /Bộ lọc/i }));

    // Verify min and max price inputs
    const minInput = screen.getByLabelText(/Giá từ/i) as HTMLInputElement;
    const maxInput = screen.getByLabelText(/Đến giá/i) as HTMLInputElement;
    expect(minInput).toBeInTheDocument();
    expect(maxInput).toBeInTheDocument();

    // Drag dual slider min thumb
    const minSlider = screen.getByLabelText(/Thanh kéo giá tối thiểu/i);
    fireEvent.change(minSlider, { target: { value: '2000000' } });
    expect(minInput.value).toBe('2000000');

    // Drag dual slider max thumb
    const maxSlider = screen.getByLabelText(/Thanh kéo giá tối đa/i);
    fireEvent.change(maxSlider, { target: { value: '10000000' } });
    expect(maxInput.value).toBe('10000000');

    // Click quick preset "1 - 5 triệu"
    const presetBtn = screen.getByRole('button', { name: /1 - 5 triệu/i });
    fireEvent.click(presetBtn);
    expect(minInput.value).toBe('1000000');
    expect(maxInput.value).toBe('5000000');

    // Apply filters
    const applyDrawerBtn = screen.getByRole('button', { name: /Áp dụng bộ lọc/i });
    fireEvent.click(applyDrawerBtn);

    expect(handleApply).toHaveBeenCalledWith(
      expect.objectContaining({
        minPrice: 1000000,
        maxPrice: 5000000,
      })
    );

    // Click "Xóa giá"
    const clearPriceBtn = screen.getByRole('button', { name: /Xóa giá/i });
    fireEvent.click(clearPriceBtn);
    expect(minInput.value).toBe('');
    expect(maxInput.value).toBe('');
  });
});

describe('MarketplacePage Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders loading, then products successfully', async () => {
    vi.mocked(marketplaceApi.getCategories).mockResolvedValue(mockCategories);
    vi.mocked(marketplaceApi.getProducts).mockResolvedValue({
      items: [mockProductSummary],
      page: 0,
      size: 12,
      totalElements: 1,
      totalPages: 1,
      hasNext: false,
    });

    render(
      <MemoryRouter initialEntries={['/marketplace']}>
        <MarketplacePage />
      </MemoryRouter>
    );

    expect(screen.getByLabelText(/Đang tải sản phẩm/i)).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText('iPhone 13 Pro Max 128GB')).toBeInTheDocument();
    });

    expect(screen.getByText(/Tìm thấy/i)).toBeInTheDocument();
  });

  it('renders zero search results empty state', async () => {
    vi.mocked(marketplaceApi.getCategories).mockResolvedValue(mockCategories);
    vi.mocked(marketplaceApi.getProducts).mockResolvedValue({
      items: [],
      page: 0,
      size: 12,
      totalElements: 0,
      totalPages: 0,
      hasNext: false,
    });

    render(
      <MemoryRouter initialEntries={['/marketplace?query=nonexistent']}>
        <MarketplacePage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText(/Không tìm thấy sản phẩm phù hợp/i)).toBeInTheDocument();
    });
  });

  it('handles API error and enables retry', async () => {
    vi.mocked(marketplaceApi.getCategories).mockResolvedValue(mockCategories);
    vi.mocked(marketplaceApi.getProducts)
      .mockRejectedValueOnce(new Error('Mạng không ổn định.'))
      .mockResolvedValueOnce({
        items: [mockProductSummary],
        page: 0,
        size: 12,
        totalElements: 1,
        totalPages: 1,
        hasNext: false,
      });

    render(
      <MemoryRouter initialEntries={['/marketplace']}>
        <MarketplacePage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText(/Mạng không ổn định/i)).toBeInTheDocument();
    });

    const retryButton = screen.getByRole('button', { name: /Thử lại/i });
    fireEvent.click(retryButton);

    await waitFor(() => {
      expect(screen.getByText('iPhone 13 Pro Max 128GB')).toBeInTheDocument();
    });
  });
});

describe('ProductDetailPage Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders product detail successfully when found', async () => {
    const mockDetail: ProductDetail = {
      ...mockProductSummary,
      description: 'Máy dùng lướt, pin 98%, nguyên zin áp suất.',
      usageDuration: '6 tháng',
      defects: 'Xước nhẹ viền',
      repairHistory: 'Chưa sửa chữa',
      includedAccessories: 'Hộp, cáp zin',
      media: [],
    };

    vi.mocked(marketplaceApi.getProductDetail).mockResolvedValue(mockDetail);

    render(
      <MemoryRouter initialEntries={['/products/101']}>
        <Routes>
          <Route path="/products/:productId" element={<ProductDetailPage />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByRole('heading', { level: 1, name: 'iPhone 13 Pro Max 128GB' })).toBeInTheDocument();
    });

    expect(screen.getByText(/Máy dùng lướt, pin 98%/i)).toBeInTheDocument();
    expect(screen.getByText('Xước nhẹ viền')).toBeInTheDocument();
    expect(screen.getByText('Hộp, cáp zin')).toBeInTheDocument();
    expect(screen.getByText(/Giá niêm yết do Người bán đưa ra/i)).toBeInTheDocument();
  });

  it('renders 404 state when product does not exist or is not active', async () => {
    vi.mocked(marketplaceApi.getProductDetail).mockRejectedValue(new Error('PRODUCT_NOT_FOUND'));

    render(
      <MemoryRouter initialEntries={['/products/999']}>
        <Routes>
          <Route path="/products/:productId" element={<ProductDetailPage />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText(/Sản phẩm không tồn tại/i)).toBeInTheDocument();
    });

    expect(screen.getByRole('link', { name: /Khám phá sản phẩm khác/i })).toBeInTheDocument();
  });
});
