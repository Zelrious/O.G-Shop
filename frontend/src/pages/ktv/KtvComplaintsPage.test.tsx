import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import { KtvComplaintsPage } from './KtvComplaintsPage';
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
