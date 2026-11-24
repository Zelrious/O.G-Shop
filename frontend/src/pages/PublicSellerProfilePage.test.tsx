import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { PublicSellerProfilePage } from './PublicSellerProfilePage';
import { publicSellerApi } from '../features/seller/publicSellerApi';

vi.mock('../features/seller/publicSellerApi', () => ({
  publicSellerApi: {
    getPublicProfile: vi.fn(),
    getPublicProducts: vi.fn(),
    getPublicReviews: vi.fn(),
  },
}));

vi.mock('../features/auth', () => ({
  useAuth: () => ({ user: null, isAuthenticated: false }),
}));

const sampleProfile = {
  sellerId: 12,
  displayName: 'Tiệm Đồ Cổ Sài Gòn',
  avatarUrl: null,
  ekycVerified: true,
  joinedAt: '2026-09-01T08:00:00Z',
  stats: {
    averageRating: 4.8,
    totalReviews: 2,
    activeProductsCount: 1,
    completedTransactionsCount: 5,
    ratingDistribution: { '1': 0, '2': 0, '3': 0, '4': 1, '5': 1 },
  },
};

describe('PublicSellerProfilePage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders public seller profile for Guest with monogram when avatarUrl is null and switches between Products and Reviews tabs', async () => {
    vi.mocked(publicSellerApi.getPublicProfile).mockResolvedValue(sampleProfile);
    vi.mocked(publicSellerApi.getPublicProducts).mockResolvedValue({
      content: [
        {
          productId: 101,
          title: 'Máy ảnh Film Olympus OM-1',
          listedPrice: 3500000,
          currency: 'VND',
          condition: 'GOOD',
          location: 'TP.HCM',
          thumbnailUrl: null,
          categoryName: 'Máy ảnh',
          quantityAvailable: 1,
          requiresBuyerEkyc: false,
          createdAt: '2026-11-20T10:00:00Z',
        },
      ],
      page: 0,
      size: 12,
      totalElements: 1,
      totalPages: 1,
    });
    vi.mocked(publicSellerApi.getPublicReviews).mockResolvedValue({
      content: [
        {
          reviewId: 501,
          reviewerDisplayName: 'Người mua đã xác thực',
          rating: 5,
          comment: 'Hàng rất đẹp và đúng mô tả.',
          createdAt: '2026-11-21T10:00:00Z',
        },
      ],
      page: 0,
      size: 10,
      totalElements: 1,
      totalPages: 1,
    });

    render(
      <MemoryRouter initialEntries={['/sellers/12']}>
        <Routes>
          <Route path="/sellers/:sellerId" element={<PublicSellerProfilePage />} />
        </Routes>
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.getByText('Tiệm Đồ Cổ Sài Gòn')).toBeInTheDocument();
    });

    // Monogram shown when avatarUrl is null (consent OFF)
    expect(screen.getByTestId('public-seller-avatar-monogram')).toHaveTextContent('T');
    expect(screen.queryByTestId('public-seller-avatar-img')).not.toBeInTheDocument();
    expect(screen.getByTestId('seller-stat-rating')).toHaveTextContent('★ 4.8');
    expect(screen.getByTestId('seller-stat-active-products')).toHaveTextContent('1');
    expect(screen.getByTestId('seller-stat-completed-tx')).toHaveTextContent('5');

    // Tab 1: Active products
    await waitFor(() => {
      expect(screen.getByText('Máy ảnh Film Olympus OM-1')).toBeInTheDocument();
    });

    // Switch to Tab 2: Buyer reviews
    fireEvent.click(screen.getByRole('tab', { name: /Đánh giá từ người mua/i }));
    await waitFor(() => {
      expect(screen.getByText('Người mua đã xác thực')).toBeInTheDocument();
      expect(screen.getByText('Hàng rất đẹp và đúng mô tả.')).toBeInTheDocument();
    });
  });

  it('renders avatar image when allowPublicAvatar is ON (avatarUrl non-null)', async () => {
    vi.mocked(publicSellerApi.getPublicProfile).mockResolvedValue({
      ...sampleProfile,
      avatarUrl: 'https://cdn.example.com/avatar.jpg',
    });
    vi.mocked(publicSellerApi.getPublicProducts).mockResolvedValue({
      content: [],
      page: 0,
      size: 12,
      totalElements: 0,
      totalPages: 0,
    });

    render(
      <MemoryRouter initialEntries={['/sellers/12']}>
        <Routes>
          <Route path="/sellers/:sellerId" element={<PublicSellerProfilePage />} />
        </Routes>
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.getByTestId('public-seller-avatar-img')).toBeInTheDocument();
    });
    expect(screen.queryByTestId('public-seller-avatar-monogram')).not.toBeInTheDocument();
  });

  it('renders friendly unavailable state when seller returns 404', async () => {
    vi.mocked(publicSellerApi.getPublicProfile).mockRejectedValue(new Error('SELLER_PROFILE_NOT_FOUND'));

    render(
      <MemoryRouter initialEntries={['/sellers/999']}>
        <Routes>
          <Route path="/sellers/:sellerId" element={<PublicSellerProfilePage />} />
        </Routes>
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.getByText('Hồ sơ người bán không khả dụng')).toBeInTheDocument();
    });
  });
});

