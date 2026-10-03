import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ListingForm } from './components/ListingForm';
import { SellerProductDetail } from './types';
import { Category } from '../marketplace/types';

const categories: Category[] = [
  { categoryId: 1, categoryName: 'Điện tử', slug: 'electronics', description: '', displayOrder: 1 },
  { categoryId: 2, categoryName: 'Sách & văn phòng phẩm', slug: 'books', description: '', displayOrder: 2 },
  { categoryId: 3, categoryName: 'Khác', slug: 'other', description: '', displayOrder: 3 },
];

const initialData: SellerProductDetail = {
  productId: 12, title: 'Thiết bị học tập', description: 'Thiết bị đã qua sử dụng',
  listedPrice: 250000, currency: 'VND', condition: 'GOOD', usageDuration: null,
  defects: null, repairHistory: null, includedAccessories: null, location: null,
  status: 'DRAFT', thumbnailUrl: null, category: categories[0], categories: categories.slice(0, 2),
  media: [], requiresBuyerEkyc: false, version: 4, createdAt: '2026-10-03T00:00:00Z', updatedAt: null,
};

describe('Multiple product categories', () => {
  it('restores every saved category and submits the complete edited selection', () => {
    const submit = vi.fn().mockResolvedValue(undefined);
    render(<ListingForm categories={categories} initialData={initialData} onSubmit={submit} isSubmitting={false} />);
    expect(screen.getByRole('checkbox', { name: 'Điện tử' })).toBeChecked();
    expect(screen.getByRole('checkbox', { name: 'Sách & văn phòng phẩm' })).toBeChecked();
    fireEvent.click(screen.getByRole('checkbox', { name: 'Điện tử' }));
    fireEvent.click(screen.getByRole('checkbox', { name: 'Khác' }));
    fireEvent.click(screen.getByRole('button', { name: /Lưu tin đăng/ }));
    expect(submit).toHaveBeenCalledWith(expect.objectContaining({ categoryIds: [2, 3], version: 4 }));
  });

  it('blocks saving after removing all categories', () => {
    const submit = vi.fn();
    render(<ListingForm categories={categories} initialData={initialData} onSubmit={submit} isSubmitting={false} />);
    fireEvent.click(screen.getByRole('checkbox', { name: 'Điện tử' }));
    fireEvent.click(screen.getByRole('checkbox', { name: 'Sách & văn phòng phẩm' }));
    fireEvent.click(screen.getByRole('button', { name: /Lưu tin đăng/ }));
    expect(screen.getByRole('alert')).toHaveTextContent('Vui lòng chọn ít nhất một danh mục sản phẩm.');
    expect(submit).not.toHaveBeenCalled();
  });

  it('supports legacy single-category data and disables category controls while saving', () => {
    render(<ListingForm categories={categories} initialData={{ ...initialData, categories: undefined }}
      onSubmit={vi.fn()} isSubmitting />);
    expect(screen.getByRole('checkbox', { name: 'Điện tử' })).toBeChecked();
    expect(screen.getByRole('checkbox', { name: 'Sách & văn phòng phẩm' })).not.toBeChecked();
    for (const checkbox of screen.getAllByRole('checkbox')) expect(checkbox).toBeDisabled();
  });
});
