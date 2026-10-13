import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ThemeProvider } from './ThemeProvider';
import { ThemeSwitch } from '../layout/ThemeSwitch';
import { useTheme } from './useTheme';

function SystemChoice() { const { setTheme } = useTheme(); return <button onClick={() => setTheme('system')}>Theo thiết bị</button>; }
beforeEach(() => { localStorage.clear(); document.documentElement.removeAttribute('data-theme'); });

describe('Approved light and dark mode', () => {
  it('applies a switch immediately, persists it, and restores it on remount', () => {
    const { unmount } = render(<ThemeProvider><ThemeSwitch /></ThemeProvider>);
    expect(document.documentElement).toHaveAttribute('data-theme', 'light');
    fireEvent.click(screen.getByRole('button', { name: 'Chuyển sang chế độ tối' }));
    expect(document.documentElement).toHaveAttribute('data-theme', 'dark');
    expect(localStorage.getItem('og_shop_theme')).toBe('dark');
    unmount(); render(<ThemeProvider><ThemeSwitch /></ThemeProvider>);
    expect(screen.getByRole('button', { name: 'Chuyển sang chế độ sáng' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Chuyển sang chế độ sáng' }));
    expect(document.documentElement).toHaveAttribute('data-theme', 'light');
  });
  it('ignores an invalid stored preference', () => {
    localStorage.setItem('og_shop_theme', 'invalid');
    render(<ThemeProvider><ThemeSwitch /></ThemeProvider>);
    expect(document.documentElement).toHaveAttribute('data-theme', 'light');
  });
  it('follows device changes only while system mode is selected', () => {
    let change: ((event: { matches: boolean }) => void) | undefined;
    vi.stubGlobal('matchMedia', () => ({ matches: false, addEventListener: (_: string, handler: typeof change) => { change = handler; }, removeEventListener: vi.fn() }));
    render(<ThemeProvider><ThemeSwitch /><SystemChoice /></ThemeProvider>);
    fireEvent.click(screen.getByRole('button', { name: 'Theo thiết bị' }));
    // Dispatch from a DOM event so React flushes updates as a user interaction.
    const trigger = document.createElement('button');
    trigger.addEventListener('click', () => change?.({ matches: true }));
    fireEvent.click(trigger);
    expect(document.documentElement).toHaveAttribute('data-theme', 'dark');
    fireEvent.click(screen.getByRole('button', { name: 'Chuyển sang chế độ sáng' }));
    fireEvent.click(trigger);
    expect(document.documentElement).toHaveAttribute('data-theme', 'light');
    vi.unstubAllGlobals();
  });
});
