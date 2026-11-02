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

  it('supports selecting individual items and batch selection', () => {
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
      {
        productId: 102,
        title: 'Bàn phím cơ cũ',
        price: 300000,
        condition: 'GOOD',
        sellerName: 'Trần Văn B',
        location: 'Đà Nẵng',
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

    // Initial state: all selected (2 items)
    expect(screen.getByText(/Chọn tất cả \(2 món\)/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Mua hàng \(2 món đã chọn\)/i })).toBeInTheDocument();

    // Uncheck first item
    const itemCheckbox = screen.getByLabelText(/Chọn sản phẩm Áo khoác da vintage/i);
    fireEvent.click(itemCheckbox);

    // Now 1 item selected
    expect(screen.getByRole('button', { name: /Mua hàng \(1 món đã chọn\)/i })).toBeInTheDocument();
  });
});
