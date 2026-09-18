import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { AuthProvider } from './AuthContext';
import { LoginForm } from './components/LoginForm';

describe('LoginForm Component', () => {
  it('renders form fields with accessible labels and buttons', () => {
    render(
      <AuthProvider>
        <LoginForm />
      </AuthProvider>
    );

    expect(screen.getByLabelText(/Địa chỉ Email/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/Mật khẩu/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Đăng nhập' })).toBeInTheDocument();
  });

  it('displays error message when submitted with empty fields', async () => {
    render(
      <AuthProvider>
        <LoginForm />
      </AuthProvider>
    );

    const submitBtn = screen.getByRole('button', { name: 'Đăng nhập' });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(screen.getByText('Vui lòng nhập đầy đủ email và mật khẩu.')).toBeInTheDocument();
    });
  });
});
