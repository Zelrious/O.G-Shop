import { render, screen, fireEvent } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { AppFooter } from './AppFooter';

describe('AppFooter Component (Requirement 1, 2, 3)', () => {
  it('renders footer highlights, brand info, policy links, and payment & shipping logos', () => {
    render(
      <MemoryRouter>
        <AppFooter />
      </MemoryRouter>
    );

    // 4 Project criteria cards
    expect(screen.getByText('Ký Quỹ An Toàn Escrow')).toBeInTheDocument();
    expect(screen.getByText('Kiểm Định Minh Bạch')).toBeInTheDocument();
    expect(screen.getByText('Trả Giá Linh Hoạt')).toBeInTheDocument();
    expect(screen.getByText('Thanh Toán Tiện Lợi')).toBeInTheDocument();

    // Verify tilt card mouse interaction does not error and applies transform
    const escrowTitle = screen.getByText('Ký Quỹ An Toàn Escrow');
    const featureCard = escrowTitle.closest('.og-footer__feature-card');
    expect(featureCard).not.toBeNull();
    if (featureCard) {
      fireEvent.mouseMove(featureCard, { clientX: 100, clientY: 80 });
      expect((featureCard as HTMLElement).style.transform).toContain('perspective');
      fireEvent.mouseLeave(featureCard);
      expect((featureCard as HTMLElement).style.transform).toContain('rotateX(0deg)');
    }

    // Navigation links
    expect(screen.getByRole('link', { name: /Giỏ hàng của bạn/i })).toHaveAttribute('href', '/cart');
    expect(screen.getByRole('link', { name: /Đơn mua của tôi/i })).toHaveAttribute('href', '/orders');
    expect(screen.getByRole('link', { name: /Kênh Người Bán/i })).toHaveAttribute('href', '/seller');

    // Payment brand logos
    expect(screen.getByText(/VNPay QR/i)).toBeInTheDocument();
    expect(screen.getAllByText(/VietQR/i).length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText(/Visa/i).length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText(/Mastercard/i)).toBeInTheDocument();
    expect(screen.getByText(/Thẻ ATM/i)).toBeInTheDocument();

    // Shipping brand logos
    expect(screen.getByText(/Giao Hàng Nhanh/i)).toBeInTheDocument();
    expect(screen.getByText(/Viettel Post/i)).toBeInTheDocument();
    expect(screen.getByText(/GHTK/i)).toBeInTheDocument();
  });
});

