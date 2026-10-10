import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './AuthContext';
import { useAuth } from './useAuth';
import { authApi } from './authApi';
import { ProtectedRoute } from '../../app/ProtectedRoute';
import type { AuthResponse, UserPrincipal } from './types';

vi.mock('./authApi', () => ({ SESSION_EXPIRED_EVENT: 'og:session-expired', authApi: { restoreSession: vi.fn(), login: vi.fn(), logout: vi.fn(), clearAccessToken: vi.fn() } }));
const admin: UserPrincipal = { userId: 1, fullName: 'Fixture Admin', email: 'fixture@example.test', roles: ['ADMIN'], createdAt: '2026-10-05T00:00:00Z' };
const session = (user: UserPrincipal): AuthResponse => ({ user, accessToken: 'fixture', expiresIn: 900 });

function SessionProbe({ email = 'admin@example.test' }: { email?: string }) {
  const { user, isLoading, login } = useAuth();
  return <>
    <output aria-label="session">{isLoading ? 'loading' : user?.roles.join(',') || 'anonymous'}</output>
    <button onClick={() => { void login({ email, password: 'wrong' }).catch(() => {}); }}>Login probe</button>
  </>;
}
beforeEach(() => { vi.resetAllMocks(); localStorage.clear(); vi.mocked(authApi.restoreSession).mockRejectedValue(new Error('Unauthorized')); });

describe('server authenticated sessions', () => {
  it('clears the principal when the fixed deadline is reached', async () => {
    vi.useFakeTimers();
    try {
      vi.mocked(authApi.restoreSession).mockResolvedValue({ ...session(admin), expiresIn: 2 });
      render(<AuthProvider><SessionProbe /></AuthProvider>);
      await act(async () => { await Promise.resolve(); });
      expect(screen.getByLabelText('session')).toHaveTextContent('ADMIN');
      await act(async () => { vi.advanceTimersByTime(2000); });
      expect(screen.getByLabelText('session')).toHaveTextContent('anonymous');
      expect(authApi.restoreSession).toHaveBeenCalledTimes(1);
    } finally { vi.useRealTimers(); }
  });

  it('clears the principal after the server reports expiry', async () => {
    vi.mocked(authApi.restoreSession).mockResolvedValue(session(admin));
    render(<AuthProvider><SessionProbe /></AuthProvider>);
    await waitFor(() => expect(screen.getByLabelText('session')).toHaveTextContent('ADMIN'));
    act(() => window.dispatchEvent(new Event('og:session-expired')));
    expect(screen.getByLabelText('session')).toHaveTextContent('anonymous');
  });
  it.each(['admin@example.test', 'ktv@example.test'])('does not create a role on offline login for %s', async email => {
    vi.mocked(authApi.login).mockRejectedValue(new TypeError('Failed to fetch'));
    render(<AuthProvider><SessionProbe email={email} /></AuthProvider>);
    await waitFor(() => expect(screen.getByLabelText('session')).toHaveTextContent('anonymous'));
    fireEvent.click(screen.getByText('Login probe'));
    await waitFor(() => expect(authApi.login).toHaveBeenCalled());
    await waitFor(() => expect(screen.getByLabelText('session')).toHaveTextContent('anonymous'));
    expect(localStorage.getItem('og_dev_user')).toBeNull();
  });
  it.each([new Error('Unauthorized'), new TypeError('Failed to fetch')])('never restores an injected principal after session restoration failure', async error => {
    localStorage.setItem('og_dev_user', JSON.stringify(admin));
    vi.mocked(authApi.restoreSession).mockRejectedValue(error);
    render(<AuthProvider><SessionProbe /></AuthProvider>);
    await waitFor(() => expect(screen.getByLabelText('session')).toHaveTextContent('anonymous'));
    expect(localStorage.getItem('og_dev_user')).toBeNull();
    expect(authApi.clearAccessToken).toHaveBeenCalled();
  });
  it('does not accept incorrect credentials', async () => {
    vi.mocked(authApi.login).mockRejectedValue(new Error('Invalid credentials'));
    render(<AuthProvider><SessionProbe /></AuthProvider>);
    await waitFor(() => expect(screen.getByLabelText('session')).toHaveTextContent('anonymous'));
    fireEvent.click(screen.getByText('Login probe'));
    await waitFor(() => expect(authApi.login).toHaveBeenCalled());
    expect(screen.getByLabelText('session')).toHaveTextContent('anonymous');
  });
  it('uses the complete principal returned by the server', async () => {
    vi.mocked(authApi.login).mockResolvedValue(session(admin));
    render(<AuthProvider><SessionProbe /></AuthProvider>);
    await waitFor(() => expect(screen.getByLabelText('session')).toHaveTextContent('anonymous'));
    fireEvent.click(screen.getByText('Login probe'));
    await waitFor(() => expect(screen.getByLabelText('session')).toHaveTextContent('ADMIN'));
    expect(localStorage.getItem('og_dev_user')).toBeNull();
  });
  it('denies the admin route to a server authenticated buyer', async () => {
    vi.mocked(authApi.restoreSession).mockResolvedValue(session({ ...admin, roles: ['BUYER'] }));
    render(<MemoryRouter initialEntries={['/admin']}><AuthProvider><Routes>
      <Route path="/admin" element={<ProtectedRoute allowedRoles={['ADMIN']}><div>Private admin</div></ProtectedRoute>} />
      <Route path="/" element={<div>Marketplace</div>} />
    </Routes></AuthProvider></MemoryRouter>);
    expect(await screen.findByText('Marketplace')).toBeInTheDocument();
    expect(screen.queryByText('Private admin')).not.toBeInTheDocument();
  });
});
