import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { Select } from './Select';
import { Pagination } from '../Pagination/Pagination';

const options = [{ value: 'a', label: 'TEST50K', description: 'Giảm 50.000đ' }, { value: 'b', label: 'Dừng phát', disabled: true }, { value: 'c', label: 'TESTFUTURE', description: 'Chương trình tương lai' }];
describe('Themed Select', () => {
  it('anchors an upward menu by its bottom edge, without reserving 340px above the trigger', () => {
    render(<Select label="Số tin" value="a" options={options} onChange={vi.fn()} />);
    const trigger = screen.getByRole('combobox');
    vi.spyOn(trigger, 'getBoundingClientRect').mockReturnValue({ top: 700, bottom: 736, left: 100, right: 270, width: 170, height: 36, x: 100, y: 700, toJSON: () => ({}) });
    fireEvent.click(trigger);
    const popover = screen.getByRole('listbox').parentElement!;
    expect(popover.style.bottom).toBe(`${window.innerHeight - 700 + 6}px`);
    expect(popover.style.top).toBe('');
  });
  it('opens with keyboard, skips disabled options, selects and restores focus', () => {
    const change = vi.fn(); render(<Select label="Chương trình" value="a" options={options} onChange={change} />);
    const trigger = screen.getByRole('combobox'); trigger.focus(); fireEvent.keyDown(trigger, { key: 'ArrowDown' });
    const list = screen.getByRole('listbox'); expect(list).toHaveFocus();
    fireEvent.keyDown(list, { key: 'ArrowDown' }); fireEvent.keyDown(list, { key: 'Enter' });
    expect(change).toHaveBeenCalledWith('c'); expect(trigger).toHaveFocus(); expect(screen.queryByRole('listbox')).toBeNull();
  });
  it('searches titles/descriptions and Escape closes without changing selection', () => {
    const change = vi.fn(); render(<Select label="Chương trình" value="a" options={options} onChange={change} searchable />);
    fireEvent.click(screen.getByRole('combobox')); const search = screen.getByRole('combobox', { name: 'Tìm chương trình' });
    expect(search).toHaveFocus(); fireEvent.change(search, { target: { value: 'tương lai' } }); expect(screen.getAllByRole('option')).toHaveLength(1);
    fireEvent.keyDown(search, { key: 'Escape' }); expect(change).not.toHaveBeenCalled(); expect(screen.getByRole('combobox')).toHaveFocus();
  });
  it('closes on outside interaction and keeps disabled/error states accessible', () => {
    const { rerender } = render(<Select label="Trạng thái" value="a" options={options} onChange={vi.fn()} />);
    fireEvent.click(screen.getByRole('combobox')); fireEvent.pointerDown(document.body); expect(screen.queryByRole('listbox')).toBeNull();
    rerender(<Select label="Trạng thái" value="a" options={options} onChange={vi.fn()} disabled error="Chọn lại trạng thái" />);
    expect(screen.getByRole('combobox')).toBeDisabled(); expect(screen.getByRole('combobox')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByRole('combobox')).toHaveAccessibleDescription('Chọn lại trạng thái');
  });
  it('zero-result pagination never presents a fictitious first page', () => {
    const change = vi.fn(); render(<Pagination total={0} page={0} totalPages={0} onPage={change} />);
    expect(screen.getByText('0 kết quả')).toBeInTheDocument(); expect(screen.queryByText('1/1')).toBeNull(); expect(screen.queryByRole('button')).toBeNull();
  });
});
