import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import { KtvComplaintsPage } from './KtvComplaintsPage';
import { SystemComplaintsPage } from '../SystemComplaintsPage';
import { returnCaseApi } from '../../features/disputes/returnCaseApi';

it('receives an unassigned issue and refreshes the queue', async () => {
  const row = {caseId: 13, orderId: 2, state: 'OPEN'};
  vi.spyOn(returnCaseApi,'getKtvAdmins').mockResolvedValue([]);
  vi.spyOn(returnCaseApi,'getKtvSystemIssues').mockResolvedValueOnce([row]).mockResolvedValue([{...row,state:'REVIEWING'}]);
  const receive = vi.spyOn(returnCaseApi,'issueProgress').mockResolvedValue(row);
  render(<KtvComplaintsPage />);
  fireEvent.click(await screen.findByRole('button',{name:'Tiếp nhận'}));
  await waitFor(() => expect(receive).toHaveBeenCalledWith('/ktv/system-issues/13/receive',''));
  expect(await screen.findByLabelText('Nội dung xử lý')).toBeInTheDocument();
});

it('handles malformed SUPPLEMENTED event metadata safely on both KtvComplaintsPage and SystemComplaintsPage', async () => {
  const brokenIssue = {
    caseId: 88,
    orderId: 5,
    caseType: 'SYSTEM_ISSUE',
    state: 'REVIEWING',
    fullDescription: 'Mô tả sự cố kiểm thử JSON lỗi',
    events: [
      {
        eventId: 'ev-1',
        caseId: 88,
        eventType: 'SUPPLEMENTED',
        reason: 'Sự kiện có metadata hỏng',
        metadata: '{not-valid-json',
        occurredAt: '2026-11-26T00:00:00Z',
      },
    ],
  };
  vi.spyOn(returnCaseApi, 'getKtvAdmins').mockResolvedValue([]);
  vi.spyOn(returnCaseApi, 'getKtvSystemIssues').mockResolvedValue([brokenIssue]);
  vi.spyOn(returnCaseApi, 'mySystemIssues').mockResolvedValue([brokenIssue]);

  render(<KtvComplaintsPage />);
  expect(await screen.findByRole('article', { name: 'Hồ sơ #88' })).toBeInTheDocument();

  render(<SystemComplaintsPage />);
  expect(await screen.findByText(/Hồ sơ #88 · Đang xem xét/)).toBeInTheDocument();
});
