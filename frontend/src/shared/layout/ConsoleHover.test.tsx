import { act, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { AdminLayoutShell } from './AdminLayoutShell';
import { KtvLayoutShell } from './KtvLayoutShell';
vi.mock('../../features/auth', () => ({ useAuth: () => ({ user: { roles: ['ADMIN', 'KTV'], fullName: 'UI test' }, logout: vi.fn() }) }));
vi.mock('../../features/notifications/NotificationBell', () => ({ NotificationBell: () => <button>Thông báo</button> }));
describe('Console sidebar hover', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());
  it.each([['Admin', AdminLayoutShell], ['KTV', KtvLayoutShell]] as const)('%s stays expanded after route focus leaves the sidebar until pointer exit', (_name, Shell) => {
    const { container } = render(<MemoryRouter><Shell /></MemoryRouter>);
    const sidebar = container.querySelector('aside')!;
    fireEvent.mouseEnter(sidebar); fireEvent.focus(sidebar);
    fireEvent.blur(sidebar, { relatedTarget: document.body });
    expect(sidebar).toHaveClass('og-admin-sidebar--expanded');
    fireEvent.mouseLeave(sidebar); expect(sidebar).toHaveClass('og-admin-sidebar--expanded'); act(() => vi.advanceTimersByTime(320)); expect(sidebar).toHaveClass('og-admin-sidebar--collapsed');
  });
  it('cancels a pending collapse when the pointer comes back', () => {
    const { container } = render(<MemoryRouter><AdminLayoutShell /></MemoryRouter>); const sidebar = container.querySelector('aside')!;
    fireEvent.mouseEnter(sidebar); fireEvent.mouseLeave(sidebar); act(() => vi.advanceTimersByTime(250)); fireEvent.mouseEnter(sidebar); act(() => vi.advanceTimersByTime(500));
    expect(sidebar).toHaveClass('og-admin-sidebar--expanded');
  });
  it('preserves the explicit pin after the pointer leaves', () => {
    const { container } = render(<MemoryRouter><AdminLayoutShell /></MemoryRouter>);
    const sidebar = container.querySelector('aside')!; fireEvent.mouseEnter(sidebar);
    fireEvent.click(screen.getByRole('button', { name: 'Ghim thanh điều hướng' })); fireEvent.mouseLeave(sidebar);
    expect(sidebar).toHaveClass('og-admin-sidebar--expanded');
  });
});
