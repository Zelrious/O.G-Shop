import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { AdminEscalatedCasesPage } from './AdminEscalatedCasesPage';
import { returnCaseApi } from '../../features/disputes/returnCaseApi';

vi.mock('../../features/disputes/returnCaseApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../features/disputes/returnCaseApi')>();
  return {
    ...actual,
    returnCaseApi: {
      ...actual.returnCaseApi,
      getAdminEscalatedCases: vi.fn(),
      adminDecideRound2: vi.fn(),
      listOrderSettlements: vi.fn().mockResolvedValue([]),
    },
  };
});

describe('AdminEscalatedCasesPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('loads escalated cases and allows Admin to issue final Round 2 decision', async () => {
    const user = userEvent.setup();
    vi.mocked(returnCaseApi.getAdminEscalatedCases).mockResolvedValue({
      content: [
        {
          caseId: 901,
          orderId: 505,
          state: 'POST_RETURN',
          assignedKtvId: 4,
          slaDeadline: '2026-11-24T00:00:00Z',
        },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
    vi.mocked(returnCaseApi.adminDecideRound2).mockResolvedValue({
      caseId: 901,
      orderId: 505,
      state: 'CLOSED',
    });

    render(<AdminEscalatedCasesPage />);

    expect(await screen.findByText('#901')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /Xét duyệt Vòng 2/i }));
    await user.type(
      screen.getByPlaceholderText(/Nhập căn cứ bằng chứng/i),
      'Hàng trả về đầy đủ phụ kiện, duyệt hoàn tiền 100%.'
    );
    await user.click(screen.getByRole('button', { name: /Tiếp tục xác nhận phán quyết/i }));
    await user.click(screen.getByRole('button', { name: /Xác nhận ban hành phán quyết cuối cùng/i }));

    await waitFor(() => {
      expect(returnCaseApi.adminDecideRound2).toHaveBeenCalledWith(901, {
        outcome: 'FULL_GOODS_REFUND',
        reason: 'Hàng trả về đầy đủ phụ kiện, duyệt hoàn tiền 100%.',
      });
    });
  });
});
