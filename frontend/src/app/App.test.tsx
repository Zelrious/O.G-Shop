import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { App } from './App';

describe('App Root & Navigation', () => {
  it('renders brand title and navigation links', () => {
    render(<App />);

    expect(screen.getByRole('link', { name: /Old but Gold/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1, name: 'Old but Gold' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Đăng nhập' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Đăng ký' })).toBeInTheDocument();
  });
});
