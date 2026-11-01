import { fireEvent, render, screen, within, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { KtvKycPage } from './KtvKycPage';
import { ekycKtvApi } from '../../features/verification';

describe('eKYC presentation', () => {
  it('masks list identifiers and retains the full identifier in the review view', () => {
    render(<KtvKycPage />);
    const queue = within(screen.getByRole('complementary', { name: 'Danh sách hồ sơ eKYC' }));
    expect(queue.queryByText('001095012345')).toBeNull(); expect(queue.getByText('CCCD: 0010••••2345')).toBeInTheDocument();
    expect(within(screen.getByRole('article', { name: 'Hồ sơ eKYC được chọn' })).getByText('001095012345')).toBeInTheDocument();
  });
  it('completed cards retain document access without exposing a second approval action', () => {
    render(<KtvKycPage />); fireEvent.click(screen.getByRole('button', { name: 'Xem hồ sơ Trần Đình Trọng' }));
    expect(screen.getByRole('heading', { name: /Hồ sơ định danh eKYC: Trần Đình Trọng/ })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Phê duyệt eKYC' })).toBeNull();
  });
  it('renders EmptyState when server returns an empty queue instead of falling back to mock data', async () => {
    vi.spyOn(ekycKtvApi, 'getQueue').mockResolvedValue({
      items: [],
      page: 0,
      size: 50,
      totalElements: 0,
      totalPages: 0,
    });
    render(<KtvKycPage />);
    await waitFor(() => {
      expect(screen.getByText('Hiện không có hồ sơ eKYC nào đang chờ duyệt.')).toBeInTheDocument();
      expect(screen.queryByText('Trần Đình Trọng')).toBeNull();
    });
  });
});
