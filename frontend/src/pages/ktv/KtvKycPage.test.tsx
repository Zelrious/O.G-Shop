import { fireEvent, render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { KtvKycPage } from './KtvKycPage';
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
});
