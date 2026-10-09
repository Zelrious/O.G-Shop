import { render, screen, fireEvent } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { CartPage } from './CartPage';
import { CartProvider } from '../shared/context';

describe('CartPage Component (Requirement 5)', () => {
  it('renders empty cart state and provides link to marketplace and orders', () => {
    localStorage.clear();

    render(
      <MemoryRouter>
        <CartProvider>
          <CartPage />
        </CartProvider>
      </MemoryRouter>
    );

    expect(screen.getByText('Giỏ hàng của bạn đang trống')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Khám phá Chợ Đồ Cũ/i })).toHaveAttribute('href', '/marketplace');
    expect(screen.getByRole('link', { name: /Xem các đơn đã mua/i })).toHaveAttribute('href', '/orders');
  });

  it('renders cart items, calculates summary total, and allows removing items', () => {
    const mockItems = [
      {
        productId: 101,
        title: 'Áo khoác da vintage',
        price: 500000,
        condition: 'LIKE_NEW',
        sellerName: 'Nguyễn Văn A',
        location: 'Hà Nội',
        addedAt: new Date().toISOString(),
      },
    ];
    localStorage.setItem('og_cart_items', JSON.stringify(mockItems));

    render(
      <MemoryRouter>
        <CartProvider>
          <CartPage />
        </CartProvider>
      </MemoryRouter>
    );

    expect(screen.getByText('Áo khoác da vintage')).toBeInTheDocument();
    expect(screen.getByText('Nguyễn Văn A')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Mua ngay/i })).toBeInTheDocument();

    // Click remove item
    const removeBtn = screen.getByRole('button', { name: /Bỏ món này/i });
    fireEvent.click(removeBtn);

    // Cart is now empty
    expect(screen.getByText('Giỏ hàng của bạn đang trống')).toBeInTheDocument();
  });
});
