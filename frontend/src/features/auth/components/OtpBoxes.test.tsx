import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { OtpBoxes } from './OtpBoxes';

describe('OtpBoxes Component', () => {
  it('renders separated visual slots and hidden input with accessibility label', () => {
    const onChange = vi.fn();
    render(<OtpBoxes label="Mã OTP" value="" onChange={onChange} length={6} />);

    expect(screen.getByLabelText('Mã OTP')).toBeInTheDocument();
    const boxes = document.querySelectorAll('.og-otp-box');
    expect(boxes).toHaveLength(6);
  });

  it('renders filled characters into respective slot boxes', () => {
    render(<OtpBoxes label="Mã OTP" value="123" onChange={vi.fn()} length={6} />);

    const chars = screen.getAllByText(/[123]/);
    expect(chars).toHaveLength(3);
    expect(chars[0]).toHaveTextContent('1');
    expect(chars[1]).toHaveTextContent('2');
    expect(chars[2]).toHaveTextContent('3');
  });

  it('sanitizes input to only digits and limits to length on change', () => {
    const onChange = vi.fn();
    render(<OtpBoxes label="Mã OTP" value="" onChange={onChange} length={6} />);

    const input = screen.getByLabelText('Mã OTP');
    fireEvent.change(input, { target: { value: 'abc123456789' } });

    expect(onChange).toHaveBeenCalledWith('123456');
  });
});
