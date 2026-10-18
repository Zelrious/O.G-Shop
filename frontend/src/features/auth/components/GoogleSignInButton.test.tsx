import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { authApi } from '../authApi';
import { GoogleSignInButton } from './GoogleSignInButton';
beforeEach(() => { vi.restoreAllMocks(); });
afterEach(() => { delete window.google; });
it('does not initialize Google when the backend has no Client ID', async () => {
  vi.spyOn(authApi, 'googleConfig').mockResolvedValue({ enabled: false, clientId: '' });
  const nonce = vi.spyOn(authApi, 'googleNonce');
  render(<GoogleSignInButton onResult={vi.fn()} />);
  await screen.findByText('Đăng nhập Google chưa được cấu hình.');expect(nonce).not.toHaveBeenCalled();
});
it('binds the SDK credential to the server nonce and forwards a registration ticket', async () => {
  let callback!: (r: { credential: string }) => void;
  const initialize = vi.fn(options => { callback = options.callback; });
  window.google = { accounts: { id: { initialize, renderButton: (element) => {
    const button = document.createElement('button');button.textContent = 'Google fixture';
    button.onclick = () => callback({ credential: 'signed-id-token-fixture' });element.append(button);
  } } } };
  vi.spyOn(authApi, 'googleConfig').mockResolvedValue({ enabled: true, clientId: 'public-client-id' });
  vi.spyOn(authApi, 'googleNonce').mockResolvedValue({ nonceId: 'nonce' });
  const result = { status: 'REGISTRATION_REQUIRED' as const, registrationTicket: 'ticket', email: 'fixture@example.invalid', fullName: 'Fixture' };
  const login = vi.spyOn(authApi, 'googleLogin').mockResolvedValue(result);const onResult = vi.fn().mockResolvedValue(undefined);
  render(<GoogleSignInButton onResult={onResult} />);await screen.findByText('Google fixture');
  expect(initialize).toHaveBeenCalledWith(expect.objectContaining({ client_id: 'public-client-id', nonce: 'nonce', ux_mode: 'popup' }));
  fireEvent.click(screen.getByText('Google fixture'));
  await waitFor(() => expect(onResult).toHaveBeenCalledWith(result));expect(login).toHaveBeenCalledWith('signed-id-token-fixture', 'nonce');
});
