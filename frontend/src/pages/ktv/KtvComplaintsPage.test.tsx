import { render, screen, fireEvent } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { KtvComplaintsPage } from './KtvComplaintsPage';
import { managementStore } from '../../features/management/managementStore';

describe('KtvComplaintsPage Component', () => {
  it('renders complaint list and allows KTV to escalate system errors to Admin', () => {
    const escalateSpy = vi.spyOn(managementStore, 'escalateComplaintToAdmin');

    render(
      <MemoryRouter>
        <KtvComplaintsPage />
      </MemoryRouter>
    );

    // Header
    expect(screen.getByRole('heading', { level: 1, name: /Tiếp nhận & Xử lý khiếu nại/i })).toBeInTheDocument();

    // Check at least one escalate button exists
    const escalateButtons = screen.getAllByRole('button', { name: /Đẩy lên Admin \(Báo lỗi hệ thống\)/i });
    expect(escalateButtons.length).toBeGreaterThanOrEqual(1);

    // Click to open modal
    fireEvent.click(escalateButtons[0]);

    // Modal title
    expect(screen.getByRole('heading', { level: 2, name: /Đẩy sự vụ lên Quản Trị Viên \(Admin\)/i })).toBeInTheDocument();

    // Submit escalation
    const sendBtn = screen.getByRole('button', { name: /Gửi báo cáo khẩn cho Admin/i });
    fireEvent.click(sendBtn);

    expect(escalateSpy).toHaveBeenCalled();
  });
});
