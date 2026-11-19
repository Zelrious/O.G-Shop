import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { beforeEach, afterEach, describe, it, expect, vi } from 'vitest';
import { OrderReviewModal } from './OrderReviewModal';
import { reviewApi } from './reviewApi';

vi.mock('./reviewApi', () => ({
  reviewApi: {
    createReview: vi.fn(),
  },
}));

describe('OrderReviewModal Component', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('renders rating choices and +500 coin reward notice', () => {
    render(
      <OrderReviewModal
        isOpen={true}
        orderId={1001}
        partnerName="Vintage Shop"
        isBuyer={true}
        onClose={vi.fn()}
        onSuccess={vi.fn()}
      />
    );

    expect(screen.getByText(/Đánh giá Người bán/)).toBeInTheDocument();
    expect(screen.getByText('+500 xu')).toBeInTheDocument();
    expect(screen.getByText(/Thưởng đánh giá:/)).toBeInTheDocument();
    expect(screen.getByText(/Đánh giá 3-5 sao giúp đối tác cải thiện điểm uy tín/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Gửi đánh giá & Nhận 500 xu' })).toBeInTheDocument();
  });

  it('submits review and displays success celebration', async () => {
    const onSuccess = vi.fn();
    vi.mocked(reviewApi.createReview).mockResolvedValue({
      reviewId: 1,
      orderId: 1001,
      reviewerId: 201,
      reviewerName: 'Buyer',
      revieweeId: 301,
      revieweeName: 'Seller',
      rating: 5,
      comment: 'Sản phẩm đóng gói rất cẩn thận, chuẩn auth!',
      createdAt: '2026-10-20T10:00:00Z',
      updatedAt: '2026-10-20T10:00:00Z',
    });

    render(
      <OrderReviewModal
        isOpen={true}
        orderId={1001}
        partnerName="Vintage Shop"
        isBuyer={true}
        onClose={vi.fn()}
        onSuccess={onSuccess}
      />
    );

    // Select 5 star (default is 5)
    const commentInput = screen.getByPlaceholderText(/Mô tả sự nhiệt tình/);
    fireEvent.change(commentInput, {
      target: { value: 'Sản phẩm đóng gói rất cẩn thận, chuẩn auth!' },
    });

    fireEvent.click(screen.getByRole('button', { name: 'Gửi đánh giá & Nhận 500 xu' }));

    await waitFor(() => {
      expect(reviewApi.createReview).toHaveBeenCalledWith(
        1001,
        5,
        'Sản phẩm đóng gói rất cẩn thận, chuẩn auth!'
      );
      expect(screen.getByText(/Gửi đánh giá thành công!/)).toBeInTheDocument();
      expect(screen.getByText(/\+500 xu thưởng/)).toBeInTheDocument();
    });
  });
});
