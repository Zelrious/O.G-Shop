import React, { useState, useEffect, useMemo } from 'react';
import { Category, CONDITION_LABELS, ProductFilterParams } from '../types';

export interface MarketplaceFiltersProps {
  categories: Category[];
  filters: ProductFilterParams;
  onApplyFilters: (newFilters: ProductFilterParams) => void;
  onResetFilters: () => void;
  isLoading?: boolean;
}

interface PricePreset {
  label: string;
  min?: number;
  max?: number;
}

const MAX_SLIDER_LIMIT = 50000000;
const SLIDER_STEP = 500000;

const PRICE_PRESETS: PricePreset[] = [
  { label: '< 1 triệu', max: 1000000 },
  { label: '1 - 5 triệu', min: 1000000, max: 5000000 },
  { label: '5 - 15 triệu', min: 5000000, max: 15000000 },
  { label: '> 15 triệu', min: 15000000 },
];

const formatShortVnd = (val: number): string => {
  if (val >= 1000000000) {
    const b = val / 1000000000;
    return `${Number(b.toFixed(1))} tỷ ₫`;
  }
  if (val >= 1000000) {
    const m = val / 1000000;
    return `${Number(m.toFixed(1))} triệu ₫`;
  }
  if (val >= 1000) {
    const k = val / 1000;
    return `${Number(k.toFixed(0))}k ₫`;
  }
  return `${val} ₫`;
};

export const MarketplaceFilters: React.FC<MarketplaceFiltersProps> = ({
  categories,
  filters,
  onApplyFilters,
  onResetFilters,
  isLoading = false,
}) => {
  const [isExpanded, setIsExpanded] = useState(false);
  const [query, setQuery] = useState(filters.query || '');

  const [selectedCategoryIds, setSelectedCategoryIds] = useState<number[]>(() => {
    if (filters.categoryIds && filters.categoryIds.length > 0) return filters.categoryIds;
    if (filters.categoryId) return [filters.categoryId];
    return [];
  });

  const [selectedConditions, setSelectedConditions] = useState<string[]>(() => {
    if (filters.conditions && filters.conditions.length > 0) return filters.conditions;
    if (filters.condition) return [filters.condition];
    return [];
  });

  const [minPrice, setMinPrice] = useState<string>(
    filters.minPrice !== undefined && filters.minPrice !== null ? String(filters.minPrice) : ''
  );
  const [maxPrice, setMaxPrice] = useState<string>(
    filters.maxPrice !== undefined && filters.maxPrice !== null ? String(filters.maxPrice) : ''
  );
  const [sort, setSort] = useState(filters.sort || 'newest');
  const [activeThumb, setActiveThumb] = useState<'min' | 'max' | null>(null);

  // Synchronize local filter state whenever props filters change
  useEffect(() => {
    setQuery(filters.query || '');

    if (filters.categoryIds && filters.categoryIds.length > 0) {
      setSelectedCategoryIds(filters.categoryIds);
    } else if (filters.categoryId) {
      setSelectedCategoryIds([filters.categoryId]);
    } else {
      setSelectedCategoryIds([]);
    }

    if (filters.conditions && filters.conditions.length > 0) {
      setSelectedConditions(filters.conditions);
    } else if (filters.condition) {
      setSelectedConditions([filters.condition]);
    } else {
      setSelectedConditions([]);
    }

    setMinPrice(filters.minPrice !== undefined && filters.minPrice !== null ? String(filters.minPrice) : '');
    setMaxPrice(filters.maxPrice !== undefined && filters.maxPrice !== null ? String(filters.maxPrice) : '');
    setSort(filters.sort || 'newest');
  }, [filters]);

  const toggleCategory = (catId: number) => {
    setSelectedCategoryIds((prev) =>
      prev.includes(catId) ? prev.filter((id) => id !== catId) : [...prev, catId]
    );
  };

  const toggleCondition = (cond: string) => {
    setSelectedConditions((prev) =>
      prev.includes(cond) ? prev.filter((c) => c !== cond) : [...prev, cond]
    );
  };

  const handlePricePreset = (preset: PricePreset) => {
    setMinPrice(preset.min !== undefined ? String(preset.min) : '');
    setMaxPrice(preset.max !== undefined ? String(preset.max) : '');
  };

  const handleSubmit = (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    onApplyFilters({
      query: query.trim() || undefined,
      categoryIds: selectedCategoryIds.length > 0 ? selectedCategoryIds : undefined,
      categoryId: selectedCategoryIds.length === 1 ? selectedCategoryIds[0] : undefined,
      conditions: selectedConditions.length > 0 ? selectedConditions : undefined,
      condition: selectedConditions.length === 1 ? selectedConditions[0] : undefined,
      minPrice: minPrice ? Number(minPrice) : undefined,
      maxPrice: maxPrice ? Number(maxPrice) : undefined,
      sort,
      page: 0,
    });
  };

  const handleReset = () => {
    setQuery('');
    setSelectedCategoryIds([]);
    setSelectedConditions([]);
    setMinPrice('');
    setMaxPrice('');
    setSort('newest');
    onResetFilters();
  };

  const handleSortChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const nextSort = e.target.value;
    setSort(nextSort);
    onApplyFilters({
      query: query.trim() || undefined,
      categoryIds: selectedCategoryIds.length > 0 ? selectedCategoryIds : undefined,
      categoryId: selectedCategoryIds.length === 1 ? selectedCategoryIds[0] : undefined,
      conditions: selectedConditions.length > 0 ? selectedConditions : undefined,
      condition: selectedConditions.length === 1 ? selectedConditions[0] : undefined,
      minPrice: minPrice ? Number(minPrice) : undefined,
      maxPrice: maxPrice ? Number(maxPrice) : undefined,
      sort: nextSort,
      page: 0,
    });
  };

  // Format currency in VND
  const formatVnd = (val: number) => {
    return new Intl.NumberFormat('vi-VN').format(val) + '₫';
  };

  // Numeric values and positions for the dual range slider
  const numericMin =
    minPrice !== '' && !isNaN(Number(minPrice))
      ? Math.min(Math.max(0, Number(minPrice)), MAX_SLIDER_LIMIT)
      : 0;

  const numericMax =
    maxPrice !== '' && !isNaN(Number(maxPrice))
      ? Math.min(Math.max(0, Number(maxPrice)), MAX_SLIDER_LIMIT)
      : MAX_SLIDER_LIMIT;

  const minPercent = Math.min(100, Math.max(0, (numericMin / MAX_SLIDER_LIMIT) * 100));
  const maxPercent = Math.min(100, Math.max(0, (numericMax / MAX_SLIDER_LIMIT) * 100));

  const handleMinSliderChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const val = Number(e.target.value);
    if (val <= numericMax) {
      setMinPrice(val === 0 ? '' : String(val));
    }
  };

  const handleMaxSliderChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const val = Number(e.target.value);
    if (val >= numericMin) {
      setMaxPrice(val >= MAX_SLIDER_LIMIT ? '' : String(val));
    }
  };

  const handleHorizontalWheel = (e: React.WheelEvent<HTMLDivElement>) => {
    if (e.deltaY !== 0 && !e.shiftKey) {
      const container = e.currentTarget;
      if (container.scrollWidth > container.clientWidth) {
        container.scrollLeft += e.deltaY;
      }
    }
  };

  // Derive active filter tags from current active filters props
  const activeTags = useMemo(() => {
    const tags: Array<{
      id: string;
      label: string;
      onRemove: () => void;
    }> = [];

    // Search query tag
    if (filters.query && filters.query.trim()) {
      tags.push({
        id: 'query',
        label: `Từ khóa: "${filters.query.trim()}"`,
        onRemove: () => {
          setQuery('');
          onApplyFilters({
            ...filters,
            query: undefined,
            page: 0,
          });
        },
      });
    }

    // Category tags (each selected category becomes its own chip)
    const activeCatIds = filters.categoryIds && filters.categoryIds.length > 0
      ? filters.categoryIds
      : filters.categoryId ? [filters.categoryId] : [];

    activeCatIds.forEach((catId) => {
      const cat = categories.find((c) => c.categoryId === catId);
      const catName = cat ? cat.categoryName : `Danh mục #${catId}`;
      tags.push({
        id: `cat-${catId}`,
        label: `Danh mục: ${catName}`,
        onRemove: () => {
          const next = activeCatIds.filter((id) => id !== catId);
          setSelectedCategoryIds(next);
          onApplyFilters({
            ...filters,
            categoryIds: next.length > 0 ? next : undefined,
            categoryId: next.length === 1 ? next[0] : undefined,
            page: 0,
          });
        },
      });
    });

    // Condition tags (each selected condition becomes its own chip)
    const activeConds = filters.conditions && filters.conditions.length > 0
      ? filters.conditions
      : filters.condition ? [filters.condition] : [];

    activeConds.forEach((cond) => {
      const condLabel = CONDITION_LABELS[cond] || cond;
      tags.push({
        id: `cond-${cond}`,
        label: `Tình trạng: ${condLabel}`,
        onRemove: () => {
          const next = activeConds.filter((c) => c !== cond);
          setSelectedConditions(next);
          onApplyFilters({
            ...filters,
            conditions: next.length > 0 ? next : undefined,
            condition: next.length === 1 ? next[0] : undefined,
            page: 0,
          });
        },
      });
    });

    // Price range tag
    if (filters.minPrice !== undefined || filters.maxPrice !== undefined) {
      let priceLabel = 'Khoảng giá';
      if (filters.minPrice !== undefined && filters.maxPrice !== undefined) {
        priceLabel = `Giá: ${formatVnd(filters.minPrice)} - ${formatVnd(filters.maxPrice)}`;
      } else if (filters.minPrice !== undefined) {
        priceLabel = `Giá từ: ${formatVnd(filters.minPrice)}`;
      } else if (filters.maxPrice !== undefined) {
        priceLabel = `Giá đến: ${formatVnd(filters.maxPrice)}`;
      }

      tags.push({
        id: 'price',
        label: priceLabel,
        onRemove: () => {
          setMinPrice('');
          setMaxPrice('');
          onApplyFilters({
            ...filters,
            minPrice: undefined,
            maxPrice: undefined,
            page: 0,
          });
        },
      });
    }

    return tags;
  }, [filters, categories, onApplyFilters]);

  // Count active criteria (categories + conditions + price)
  const filterCriteriaCount =
    (selectedCategoryIds.length > 0 ? selectedCategoryIds.length : 0) +
    (selectedConditions.length > 0 ? selectedConditions.length : 0) +
    (minPrice || maxPrice ? 1 : 0);

  return (
    <div className="og-marketplace-filters-wrapper">
      {/* 1. Sleek Compact Main Filter Bar */}
      <form
        className="og-marketplace-filters og-filterbar"
        onSubmit={handleSubmit}
        role="search"
        aria-label="Bộ lọc tìm kiếm sản phẩm"
      >
        {/* Search input with integrated icon */}
        <div className="og-filterbar__search-group">
          <span className="og-filterbar__search-icon" aria-hidden="true">
            🔍
          </span>
          <input
            id="filter-query"
            type="search"
            className="og-input og-filterbar__search-input"
            placeholder="Nhập tên sản phẩm cần tìm..."
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
          {query && (
            <button
              type="button"
              className="og-filterbar__search-clear"
              onClick={() => setQuery('')}
              aria-label="Xóa từ khóa tìm kiếm"
            >
              ✕
            </button>
          )}
        </div>

        {/* Filter Toggle Button */}
        <button
          type="button"
          className={`og-button og-button--secondary og-filterbar__toggle-btn ${
            isExpanded ? 'og-filterbar__toggle-btn--active' : ''
          }`}
          onClick={() => setIsExpanded(!isExpanded)}
          aria-expanded={isExpanded}
          aria-controls="og-filter-drawer"
        >
          <svg
            className="og-filter-icon"
            viewBox="0 0 20 20"
            fill="currentColor"
            width="16"
            height="16"
            aria-hidden="true"
          >
            <path
              fillRule="evenodd"
              d="M3 5a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1zm2 5a1 1 0 011-1h8a1 1 0 110 2H6a1 1 0 01-1-1zm3 5a1 1 0 011-1h2a1 1 0 110 2H9a1 1 0 01-1-1z"
              clipRule="evenodd"
            />
          </svg>
          <span className="og-filterbar__toggle-text">Bộ lọc</span>
          {filterCriteriaCount > 0 && (
            <span className="og-filter-badge" aria-label={`${filterCriteriaCount} tiêu chí đang chọn`}>
              {filterCriteriaCount}
            </span>
          )}
          <span className="og-filter-chevron" aria-hidden="true">
            {isExpanded ? '▲' : '▼'}
          </span>
        </button>

        {/* Sort Select */}
        <div className="og-filterbar__sort-group">
          <label htmlFor="filter-sort" className="og-filterbar__sort-label">
            Sắp xếp:
          </label>
          <select
            id="filter-sort"
            className="og-input og-filterbar__sort-select"
            value={sort}
            onChange={handleSortChange}
          >
            <option value="newest">Mới nhất</option>
            <option value="price_asc">Giá: Thấp đến cao</option>
            <option value="price_desc">Giá: Cao đến thấp</option>
          </select>
        </div>

        {/* Action Buttons in Main Bar */}
        <div className="og-filterbar__actions">
          <button
            type="submit"
            className="og-button og-button--primary og-filterbar__submit-btn"
            disabled={isLoading}
          >
            🔍 Áp dụng
          </button>
          <button
            type="button"
            className="og-button og-button--ghost og-filterbar__reset-btn"
            onClick={handleReset}
            disabled={isLoading}
          >
            Đặt lại
          </button>
        </div>
      </form>

      {/* 2. Expandable Collapsible Filter Panel */}
      {isExpanded && (
        <div
          id="og-filter-drawer"
          className="og-filter-drawer"
          role="region"
          aria-label="Tùy chọn lọc chi tiết"
        >
          <div className="og-filter-drawer__header">
            <span className="og-filter-drawer__title">Tùy chọn lọc chi tiết</span>
            <button
              type="button"
              className="og-filter-drawer__close-btn"
              onClick={() => setIsExpanded(false)}
              aria-label="Đóng bảng lọc"
            >
              Thu gọn ▲
            </button>
          </div>

          <div className="og-filter-drawer__grid">
            {/* 1. Category Section (Multi-Select, Horizontal Scroll) */}
            <div className="og-filter-group">
              <div className="og-filter-group__header">
                <span className="og-filter-group__title">🏷️ Loại sản phẩm / Danh mục</span>
                <span className="og-filter-group__sub">
                  {selectedCategoryIds.length > 0
                    ? `(Đã chọn ${selectedCategoryIds.length})`
                    : '(Chọn nhiều)'}
                </span>
              </div>
              <div
                className="og-filter-chips-scroll"
                role="group"
                aria-label="Danh mục sản phẩm"
                onWheel={handleHorizontalWheel}
              >
                <button
                  type="button"
                  className={`og-filter-chip ${
                    selectedCategoryIds.length === 0 ? 'og-filter-chip--active' : ''
                  }`}
                  onClick={() => setSelectedCategoryIds([])}
                >
                  Tất cả
                </button>
                {categories.map((cat) => {
                  const isSelected = selectedCategoryIds.includes(cat.categoryId);
                  return (
                    <button
                      key={cat.categoryId}
                      type="button"
                      className={`og-filter-chip ${isSelected ? 'og-filter-chip--active' : ''}`}
                      onClick={() => toggleCategory(cat.categoryId)}
                      aria-pressed={isSelected}
                    >
                      {cat.categoryName}
                    </button>
                  );
                })}
              </div>
            </div>

            {/* 2. Condition Section (Multi-Select, Horizontal Scroll) */}
            <div className="og-filter-group">
              <div className="og-filter-group__header">
                <span className="og-filter-group__title">⭐ Tình trạng sản phẩm</span>
                <span className="og-filter-group__sub">
                  {selectedConditions.length > 0
                    ? `(Đã chọn ${selectedConditions.length})`
                    : '(Chọn nhiều)'}
                </span>
              </div>
              <div
                className="og-filter-chips-scroll"
                role="group"
                aria-label="Tình trạng sản phẩm"
                onWheel={handleHorizontalWheel}
              >
                <button
                  type="button"
                  className={`og-filter-chip ${
                    selectedConditions.length === 0 ? 'og-filter-chip--active' : ''
                  }`}
                  onClick={() => setSelectedConditions([])}
                >
                  Tất cả
                </button>
                {Object.entries(CONDITION_LABELS).map(([val, label]) => {
                  const isSelected = selectedConditions.includes(val);
                  return (
                    <button
                      key={val}
                      type="button"
                      className={`og-filter-chip ${isSelected ? 'og-filter-chip--active' : ''}`}
                      onClick={() => toggleCondition(val)}
                      aria-pressed={isSelected}
                    >
                      {label}
                    </button>
                  );
                })}
              </div>
            </div>

            {/* 3. Price Range Section (Tầm giá: Text Boxes + Thanh kéo 2 đầu + Lọc nhanh) */}
            <div className="og-filter-group og-filter-group--price">
              <div className="og-filter-group__header">
                <span className="og-filter-group__title">💰 Tầm giá</span>
              </div>

              <div className="og-filter-price-card">
                {/* Top Row: Inputs on Left, Dual Range Slider on Right */}
                <div className="og-filter-price-top-row">
                  {/* Left Column: Text inputs for quick entry with ample spacing */}
                  <div className="og-filter-price-inputs-col">
                    <div className="og-filter-price-inputs-group">
                      <div className="og-filter-price-field">
                        <label htmlFor="filter-min-price" className="og-filter-price-label">
                          Giá từ (₫)
                        </label>
                        <input
                          id="filter-min-price"
                          type="number"
                          min="0"
                          step="100000"
                          className="og-input og-filter-price-input"
                          placeholder="Tối thiểu"
                          value={minPrice}
                          onChange={(e) => setMinPrice(e.target.value)}
                        />
                        {minPrice && Number(minPrice) > 0 ? (
                          <span className="og-filter-price-hint">
                            ≈ {formatShortVnd(Number(minPrice))}
                          </span>
                        ) : (
                          <span className="og-filter-price-hint og-filter-price-hint--placeholder">
                            0 ₫
                          </span>
                        )}
                      </div>

                      <span className="og-filter-price-dash" aria-hidden="true">
                        —
                      </span>

                      <div className="og-filter-price-field">
                        <label htmlFor="filter-max-price" className="og-filter-price-label">
                          Đến giá (₫)
                        </label>
                        <input
                          id="filter-max-price"
                          type="number"
                          min="0"
                          step="100000"
                          className="og-input og-filter-price-input"
                          placeholder="Tối đa"
                          value={maxPrice}
                          onChange={(e) => setMaxPrice(e.target.value)}
                        />
                        {maxPrice && Number(maxPrice) > 0 ? (
                          <span className="og-filter-price-hint">
                            ≈ {formatShortVnd(Number(maxPrice))}
                          </span>
                        ) : (
                          <span className="og-filter-price-hint og-filter-price-hint--placeholder">
                            50.000.000+ ₫
                          </span>
                        )}
                      </div>

                      {(minPrice || maxPrice) && (
                        <button
                          type="button"
                          className="og-button og-button--ghost og-button--sm og-filter-price-clear-btn"
                          onClick={() => {
                            setMinPrice('');
                            setMaxPrice('');
                          }}
                          title="Xóa giá đã nhập"
                        >
                          ✕ Xóa giá
                        </button>
                      )}
                    </div>
                  </div>

                  {/* Right Column: Thanh kéo giá 2 đầu */}
                  <div className="og-filter-price-slider-col">
                    <div className="og-filter-slider-header">
                      <span className="og-filter-slider-label">Thanh kéo giá:</span>
                    </div>
                    <div className="og-dual-slider-box">
                      <div className="og-dual-slider">
                        <div className="og-dual-slider__track" />
                        <div
                          className="og-dual-slider__range"
                          style={{
                            left: `${minPercent}%`,
                            width: `${Math.max(0, maxPercent - minPercent)}%`,
                          }}
                        />
                        <input
                          type="range"
                          min={0}
                          max={MAX_SLIDER_LIMIT}
                          step={SLIDER_STEP}
                          value={numericMin}
                          aria-label="Thanh kéo giá tối thiểu"
                          className="og-dual-slider__input og-dual-slider__input--min"
                          style={{ zIndex: activeThumb === 'min' ? 5 : 3 }}
                          onMouseDown={() => setActiveThumb('min')}
                          onTouchStart={() => setActiveThumb('min')}
                          onChange={handleMinSliderChange}
                        />
                        <input
                          type="range"
                          min={0}
                          max={MAX_SLIDER_LIMIT}
                          step={SLIDER_STEP}
                          value={numericMax}
                          aria-label="Thanh kéo giá tối đa"
                          className="og-dual-slider__input og-dual-slider__input--max"
                          style={{ zIndex: activeThumb === 'max' ? 5 : 4 }}
                          onMouseDown={() => setActiveThumb('max')}
                          onTouchStart={() => setActiveThumb('max')}
                          onChange={handleMaxSliderChange}
                        />
                      </div>
                      <div className="og-dual-slider-markers" aria-hidden="true">
                        <span>0 ₫</span>
                        <span>25 triệu ₫</span>
                        <span>50+ triệu ₫</span>
                      </div>
                    </div>
                  </div>
                </div>

                {/* Các nút lọc nhanh như hiện tại */}
                <div className="og-filter-presets-section">
                  <span className="og-filter-presets-title">Lọc nhanh:</span>
                  <div
                    className="og-filter-chips-scroll og-filter-chips-scroll--presets"
                    role="group"
                    aria-label="Chọn nhanh mức giá"
                    onWheel={handleHorizontalWheel}
                  >
                    {PRICE_PRESETS.map((preset, idx) => {
                      const isPresetActive =
                        (preset.min !== undefined ? minPrice === String(preset.min) : !minPrice) &&
                        (preset.max !== undefined ? maxPrice === String(preset.max) : !maxPrice);
                      return (
                        <button
                          key={idx}
                          type="button"
                          className={`og-filter-chip og-filter-chip--preset ${
                            isPresetActive ? 'og-filter-chip--active' : ''
                          }`}
                          onClick={() => handlePricePreset(preset)}
                        >
                          {preset.label}
                        </button>
                      );
                    })}
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* Drawer Footer Actions */}
          <div className="og-filter-drawer__footer">
            <span className="og-filter-drawer__status">
              {filterCriteriaCount > 0
                ? `Đã cấu hình ${filterCriteriaCount} tiêu chí lọc`
                : 'Chưa chọn tiêu chí nào'}
            </span>
            <div className="og-filter-drawer__actions">
              <button
                type="button"
                className="og-button og-button--ghost"
                onClick={handleReset}
                disabled={isLoading}
              >
                Đặt lại
              </button>
              <button
                type="button"
                className="og-button og-button--primary"
                onClick={() => handleSubmit()}
                disabled={isLoading}
              >
                Áp dụng bộ lọc
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 3. Active Filter Chips / Labels List */}
      {activeTags.length > 0 && (
        <div
          className="og-marketplace-active-filters"
          role="region"
          aria-label="Danh sách tiêu chí đang lọc"
        >
          <span className="og-marketplace-active-filters__lead">
            Đang lọc:
          </span>
          <div className="og-marketplace-active-filters__tags">
            {activeTags.map((tag) => (
              <span key={tag.id} className="og-active-filter-tag">
                <span className="og-active-filter-tag__text">{tag.label}</span>
                <button
                  type="button"
                  className="og-active-filter-tag__remove"
                  onClick={tag.onRemove}
                  aria-label={`Xóa tiêu chí ${tag.label}`}
                  title="Xóa tiêu chí này"
                >
                  ✕
                </button>
              </span>
            ))}
            <button
              type="button"
              className="og-active-filters-clear-all"
              onClick={handleReset}
              aria-label="Xóa tất cả bộ lọc"
            >
              Xóa tất cả
            </button>
          </div>
        </div>
      )}
    </div>
  );
};
