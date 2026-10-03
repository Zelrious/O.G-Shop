import React, { useState } from 'react';
import { Category, CONDITION_LABELS, ProductFilterParams } from '../types';

interface MarketplaceFiltersProps {
  categories: Category[];
  filters: ProductFilterParams;
  onApplyFilters: (newFilters: ProductFilterParams) => void;
  onResetFilters: () => void;
  isLoading?: boolean;
}

export const MarketplaceFilters: React.FC<MarketplaceFiltersProps> = ({
  categories,
  filters,
  onApplyFilters,
  onResetFilters,
  isLoading = false,
}) => {
  const [query, setQuery] = useState(filters.query || '');
  const [categoryId, setCategoryId] = useState<string>(filters.categoryId ? String(filters.categoryId) : '');
  const [condition, setCondition] = useState(filters.condition || '');
  const [minPrice, setMinPrice] = useState<string>(filters.minPrice !== undefined ? String(filters.minPrice) : '');
  const [maxPrice, setMaxPrice] = useState<string>(filters.maxPrice !== undefined ? String(filters.maxPrice) : '');
  const [sort, setSort] = useState(filters.sort || 'newest');



  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onApplyFilters({
      query: query.trim() || undefined,
      categoryId: categoryId ? Number(categoryId) : undefined,
      condition: condition || undefined,
      minPrice: minPrice ? Number(minPrice) : undefined,
      maxPrice: maxPrice ? Number(maxPrice) : undefined,
      sort,
      page: 0,
    });
  };

  const handleReset = () => {
    setQuery('');
    setCategoryId('');
    setCondition('');
    setMinPrice('');
    setMaxPrice('');
    setSort('newest');
    onResetFilters();
  };

  return (
    <form className="og-marketplace-filters" onSubmit={handleSubmit} role="search" aria-label="Bộ lọc tìm kiếm sản phẩm">
      <div className="og-marketplace-filters__row">
        {/* Search Input */}
        <div className="og-marketplace-filters__group og-marketplace-filters__group--search">
          <label htmlFor="filter-query" className="og-marketplace-filters__label">
            Tìm kiếm sản phẩm
          </label>
          <input
            id="filter-query"
            type="search"
            className="og-input"
            placeholder="Nhập tên sản phẩm cần tìm..."
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </div>

        {/* Category Filter */}
        <div className="og-marketplace-filters__group">
          <label htmlFor="filter-category" className="og-marketplace-filters__label">
            Danh mục
          </label>
          <select
            id="filter-category"
            className="og-input"
            value={categoryId}
            onChange={(e) => setCategoryId(e.target.value)}
          >
            <option value="">Tất cả danh mục</option>
            {categories.map((cat) => (
              <option key={cat.categoryId} value={cat.categoryId}>
                {cat.categoryName}
              </option>
            ))}
          </select>
        </div>

        {/* Condition Filter */}
        <div className="og-marketplace-filters__group">
          <label htmlFor="filter-condition" className="og-marketplace-filters__label">
            Tình trạng
          </label>
          <select
            id="filter-condition"
            className="og-input"
            value={condition}
            onChange={(e) => setCondition(e.target.value)}
          >
            <option value="">Tất cả tình trạng</option>
            {Object.entries(CONDITION_LABELS).map(([val, label]) => (
              <option key={val} value={val}>
                {label}
              </option>
            ))}
          </select>
        </div>

        {/* Sort */}
        <div className="og-marketplace-filters__group">
          <label htmlFor="filter-sort" className="og-marketplace-filters__label">
            Sắp xếp
          </label>
          <select
            id="filter-sort"
            className="og-input"
            value={sort}
            onChange={(e) => setSort(e.target.value)}
          >
            <option value="newest">Mới nhất</option>
            <option value="price_asc">Giá: Thấp đến cao</option>
            <option value="price_desc">Giá: Cao đến thấp</option>
          </select>
        </div>
      </div>

      <div className="og-marketplace-filters__row og-marketplace-filters__row--price">
        {/* Min Price */}
        <div className="og-marketplace-filters__group og-marketplace-filters__group--price">
          <label htmlFor="filter-min-price" className="og-marketplace-filters__label">
            Giá từ (₫)
          </label>
          <input
            id="filter-min-price"
            type="number"
            min="0"
            step="10000"
            className="og-input"
            placeholder="Tối thiểu"
            value={minPrice}
            onChange={(e) => setMinPrice(e.target.value)}
          />
        </div>

        {/* Max Price */}
        <div className="og-marketplace-filters__group og-marketplace-filters__group--price">
          <label htmlFor="filter-max-price" className="og-marketplace-filters__label">
            Đến giá (₫)
          </label>
          <input
            id="filter-max-price"
            type="number"
            min="0"
            step="10000"
            className="og-input"
            placeholder="Tối đa"
            value={maxPrice}
            onChange={(e) => setMaxPrice(e.target.value)}
          />
        </div>

        {/* Action Buttons */}
        <div className="og-marketplace-filters__actions">
          <button
            type="submit"
            className="og-button og-button--primary"
            disabled={isLoading}
          >
            🔍 Áp dụng
          </button>
          <button
            type="button"
            className="og-button og-button--ghost"
            onClick={handleReset}
            disabled={isLoading}
          >
            Đặt lại
          </button>
        </div>
      </div>
    </form>
  );
};
