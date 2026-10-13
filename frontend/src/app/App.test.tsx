import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from './App';

describe('App Root & Navigation', () => {
  beforeEach(() => { vi.spyOn(window, 'scrollTo').mockImplementation(() => {}); });
  it('renders brand title and navigation links', () => {
    render(<App />);

    expect(screen.getByRole('link', { name: /Old but Gold/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1, name: 'Old but Gold' })).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: 'Đăng nhập' }).length).toBeGreaterThanOrEqual(1);
    expect(screen.getByRole('button', { name: 'Đăng ký' })).toBeInTheDocument();
  });
});
