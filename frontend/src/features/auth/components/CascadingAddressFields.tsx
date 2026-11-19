import React, { useState, useEffect, useMemo, useRef } from 'react';
import type { RegistrationAddress } from '../types';
import { Input } from '../../../shared/components';
import { profileApi, GhnProvince, GhnDistrict, GhnWard } from '../../profile';

interface CascadingAddressFieldsProps {
  title: string;
  subtitle?: string;
  prefix: string;
  value: RegistrationAddress;
  onChange: (value: RegistrationAddress) => void;
  disabled?: boolean;
}

// Built-in administrative data for instant offline/speedy response
const POPULAR_PROVINCES: GhnProvince[] = [
  { provinceId: 202, provinceName: 'TP. Hồ Chí Minh' },
  { provinceId: 201, provinceName: 'Hà Nội' },
  { provinceId: 203, provinceName: 'Đà Nẵng' },
  { provinceId: 204, provinceName: 'Hải Phòng' },
  { provinceId: 205, provinceName: 'Cần Thơ' },
  { provinceId: 206, provinceName: 'Bình Dương' },
  { provinceId: 207, provinceName: 'Đồng Nai' },
  { provinceId: 208, provinceName: 'Bà Rịa - Vũng Tàu' },
  { provinceId: 209, provinceName: 'Khánh Hòa' },
  { provinceId: 210, provinceName: 'Lâm Đồng' },
  { provinceId: 211, provinceName: 'Thừa Thiên Huế' },
  { provinceId: 212, provinceName: 'Quảng Nam' },
  { provinceId: 213, provinceName: 'Quảng Ninh' },
  { provinceId: 214, provinceName: 'Bắc Ninh' },
  { provinceId: 215, provinceName: 'Nghệ An' },
  { provinceId: 216, provinceName: 'Thanh Hóa' },
];

const FALLBACK_DISTRICTS: Record<number, GhnDistrict[]> = {
  202: [ // TP. HCM
    { districtId: 1442, provinceId: 202, districtName: 'Quận 1' },
    { districtId: 1443, provinceId: 202, districtName: 'Quận 3' },
    { districtId: 1444, provinceId: 202, districtName: 'Quận 4' },
    { districtId: 1445, provinceId: 202, districtName: 'Quận 5' },
    { districtId: 1446, provinceId: 202, districtName: 'Quận 7' },
    { districtId: 1447, provinceId: 202, districtName: 'Quận 10' },
    { districtId: 1448, provinceId: 202, districtName: 'Quận Bình Thạnh' },
    { districtId: 1449, provinceId: 202, districtName: 'Quận Phú Nhuận' },
    { districtId: 1450, provinceId: 202, districtName: 'Quận Tân Bình' },
    { districtId: 1451, provinceId: 202, districtName: 'TP. Thủ Đức' },
  ],
  201: [ // Hà Nội
    { districtId: 1501, provinceId: 201, districtName: 'Quận Hoàn Kiếm' },
    { districtId: 1502, provinceId: 201, districtName: 'Quận Ba Đình' },
    { districtId: 1503, provinceId: 201, districtName: 'Quận Đống Đa' },
    { districtId: 1504, provinceId: 201, districtName: 'Quận Cầu Giấy' },
    { districtId: 1505, provinceId: 201, districtName: 'Quận Hai Bà Trưng' },
    { districtId: 1506, provinceId: 201, districtName: 'Quận Tây Hồ' },
    { districtId: 1507, provinceId: 201, districtName: 'Quận Thanh Xuân' },
    { districtId: 1508, provinceId: 201, districtName: 'Quận Nam Từ Liêm' },
  ],
  203: [ // Đà Nẵng
    { districtId: 1520, provinceId: 203, districtName: 'Quận Hải Châu' },
    { districtId: 1521, provinceId: 203, districtName: 'Quận Thanh Khê' },
    { districtId: 1522, provinceId: 203, districtName: 'Quận Sơn Trà' },
    { districtId: 1523, provinceId: 203, districtName: 'Quận Ngũ Hành Sơn' },
    { districtId: 1524, provinceId: 203, districtName: 'Quận Cẩm Lệ' },
  ],
};

const FALLBACK_WARDS: Record<number, GhnWard[]> = {
  1442: [ // Quận 1
    { wardCode: '20101', districtId: 1442, wardName: 'Phường Bến Nghé' },
    { wardCode: '20102', districtId: 1442, wardName: 'Phường Bến Thành' },
    { wardCode: '20103', districtId: 1442, wardName: 'Phường Đa Kao' },
    { wardCode: '20104', districtId: 1442, wardName: 'Phường Tân Định' },
    { wardCode: '20105', districtId: 1442, wardName: 'Phường Phạm Ngũ Lão' },
  ],
  1443: [ // Quận 3
    { wardCode: '20201', districtId: 1443, wardName: 'Phường Võ Thị Sáu' },
    { wardCode: '20202', districtId: 1443, wardName: 'Phường 1' },
    { wardCode: '20203', districtId: 1443, wardName: 'Phường 2' },
  ],
  1501: [ // Quận Hoàn Kiếm
    { wardCode: '10101', districtId: 1501, wardName: 'Phường Tràng Tiền' },
    { wardCode: '10102', districtId: 1501, wardName: 'Phường Hàng Bạc' },
    { wardCode: '10103', districtId: 1501, wardName: 'Phường Cửa Đông' },
  ],
  1504: [ // Quận Cầu Giấy
    { wardCode: '10401', districtId: 1504, wardName: 'Phường Dịch Vọng' },
    { wardCode: '10402', districtId: 1504, wardName: 'Phường Quan Hoa' },
    { wardCode: '10403', districtId: 1504, wardName: 'Phường Nghĩa Đô' },
  ],
  1520: [ // Quận Hải Châu
    { wardCode: '30101', districtId: 1520, wardName: 'Phường Hải Châu 1' },
    { wardCode: '30102', districtId: 1520, wardName: 'Phường Thạch Thang' },
    { wardCode: '30103', districtId: 1520, wardName: 'Phường Bình Thuận' },
  ],
};

export const CascadingAddressFields: React.FC<CascadingAddressFieldsProps> = ({
  title,
  subtitle,
  prefix,
  value,
  onChange,
  disabled = false,
}) => {
  const [provinces, setProvinces] = useState<GhnProvince[]>(POPULAR_PROVINCES);
  const [districts, setDistricts] = useState<GhnDistrict[]>([]);
  const [wards, setWards] = useState<GhnWard[]>([]);

  const [activeDropdown, setActiveDropdown] = useState<'province' | 'district' | 'ward' | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  // Close dropdown on click outside
  useEffect(() => {
    const handleOutside = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setActiveDropdown(null);
      }
    };
    document.addEventListener('mousedown', handleOutside);
    return () => document.removeEventListener('mousedown', handleOutside);
  }, []);

  // Load provinces from master-data API
  useEffect(() => {
    let ignore = false;
    profileApi
      .getProvinces()
      .then((data) => {
        if (!ignore && data && data.length > 0) {
          setProvinces(data);
        }
      })
      .catch(() => {
        // Fallback already in state
      });
    return () => {
      ignore = true;
    };
  }, []);

  // When value.province changes, load districts
  useEffect(() => {
    if (!value.province) {
      setDistricts([]);
      return;
    }
    const match = provinces.find(
      (p) => p.provinceName.toLowerCase() === value.province.toLowerCase()
    );
    const provId = match ? match.provinceId : null;
    if (provId) {
      profileApi
        .getDistricts(provId)
        .then((list) => {
          if (list && list.length > 0) setDistricts(list);
          else if (FALLBACK_DISTRICTS[provId]) setDistricts(FALLBACK_DISTRICTS[provId]);
        })
        .catch(() => {
          if (FALLBACK_DISTRICTS[provId]) setDistricts(FALLBACK_DISTRICTS[provId]);
        });
    } else {
      // Check if fallback has match
      const fallbackEntry = Object.entries(FALLBACK_DISTRICTS).find(([pid]) => {
        const pObj = POPULAR_PROVINCES.find((p) => p.provinceId === Number(pid));
        return pObj && pObj.provinceName.toLowerCase().includes(value.province.toLowerCase());
      });
      if (fallbackEntry) setDistricts(fallbackEntry[1]);
    }
  }, [value.province, provinces]);

  // When value.district changes, load wards
  useEffect(() => {
    if (!value.district || districts.length === 0) {
      setWards([]);
      return;
    }
    const match = districts.find(
      (d) => d.districtName.toLowerCase() === value.district.toLowerCase()
    );
    const distId = match ? match.districtId : null;
    if (distId) {
      profileApi
        .getWards(distId)
        .then((list) => {
          if (list && list.length > 0) setWards(list);
          else if (FALLBACK_WARDS[distId]) setWards(FALLBACK_WARDS[distId]);
        })
        .catch(() => {
          if (FALLBACK_WARDS[distId]) setWards(FALLBACK_WARDS[distId]);
        });
    } else if (FALLBACK_WARDS[1442]) {
      setWards(FALLBACK_WARDS[1442]);
    }
  }, [value.district, districts]);

  const filteredProvinces = useMemo(() => {
    if (!value.province) return provinces;
    const q = value.province.toLowerCase();
    const matches = provinces.filter((p) => p.provinceName.toLowerCase().includes(q));
    return matches.length > 0 ? matches : provinces;
  }, [provinces, value.province]);

  const filteredDistricts = useMemo(() => {
    if (!value.district) return districts;
    const q = value.district.toLowerCase();
    const matches = districts.filter((d) => d.districtName.toLowerCase().includes(q));
    return matches.length > 0 ? matches : districts;
  }, [districts, value.district]);

  const filteredWards = useMemo(() => {
    if (!value.ward) return wards;
    const q = value.ward.toLowerCase();
    const matches = wards.filter((w) => w.wardName.toLowerCase().includes(q));
    return matches.length > 0 ? matches : wards;
  }, [wards, value.ward]);

  return (
    <div role="group" aria-label={title} className="og-form-section" ref={containerRef}>
      <div className="og-form-section-header">
        <h4 className="og-form-section-title"> {title}</h4>
        {subtitle && <p className="og-form-section-desc">{subtitle}</p>}
      </div>

      <div className="og-form-grid-2">
        {/* 1. TỈNH / THÀNH PHỐ COMBOBOX */}
        <div style={{ position: 'relative' }}>
          <Input
            id={`${prefix}-province`}
            label="Tỉnh / Thành phố"
            value={value.province}
            onChange={(e) => {
              onChange({ ...value, province: e.target.value, district: '', ward: '' });
              setActiveDropdown('province');
            }}
            onFocus={() => !disabled && setActiveDropdown('province')}
            required
            maxLength={100}
            disabled={disabled}
            placeholder="Chọn hoặc nhập Tỉnh / TP (VD: TP. Hồ Chí Minh)"
            autoComplete="off"
          />
          {activeDropdown === 'province' && !disabled && filteredProvinces.length > 0 && (
            <div
              className="og-combobox-dropdown"
              style={{
                position: 'absolute',
                top: 'calc(100% - 10px)',
                left: 0,
                right: 0,
                zIndex: 1040,
                maxHeight: '200px',
                overflowY: 'auto',
                background: 'var(--og-color-surface-elevated, var(--og-color-surface))',
                border: '1px solid var(--og-color-border-strong)',
                borderRadius: '8px',
                boxShadow: '0 6px 20px rgba(0,0,0,0.12)',
                padding: '4px',
              }}
            >
              {filteredProvinces.map((p) => (
                <div
                  key={p.provinceId}
                  onClick={() => {
                    onChange({ ...value, province: p.provinceName, district: '', ward: '' });
                    setActiveDropdown(null);
                  }}
                  style={{
                    padding: '8px 12px',
                    borderRadius: '6px',
                    cursor: 'pointer',
                    fontSize: '0.88rem',
                    color: 'var(--og-color-text-primary)',
                  }}
                  onMouseEnter={(e) => {
                    (e.currentTarget as HTMLElement).style.background = 'var(--og-color-surface-hover, rgba(0,0,0,0.05))';
                  }}
                  onMouseLeave={(e) => {
                    (e.currentTarget as HTMLElement).style.background = 'transparent';
                  }}
                >
                  {p.provinceName}
                </div>
              ))}
            </div>
          )}
        </div>

        {/* 2. QUẬN / HUYỆN COMBOBOX (Lọc theo Tỉnh/TP) */}
        <div style={{ position: 'relative' }}>
          <Input
            id={`${prefix}-district`}
            label="Quận / Huyện"
            value={value.district}
            onChange={(e) => {
              onChange({ ...value, district: e.target.value, ward: '' });
              setActiveDropdown('district');
            }}
            onFocus={() => !disabled && setActiveDropdown('district')}
            required
            maxLength={100}
            disabled={disabled}
            placeholder={value.province ? 'Chọn hoặc nhập Quận / Huyện (VD: Quận 1)' : 'Vui lòng chọn Tỉnh/Thành trước'}
            autoComplete="off"
          />
          {activeDropdown === 'district' && !disabled && districts.length > 0 && (
            <div
              className="og-combobox-dropdown"
              style={{
                position: 'absolute',
                top: 'calc(100% - 10px)',
                left: 0,
                right: 0,
                zIndex: 1040,
                maxHeight: '200px',
                overflowY: 'auto',
                background: 'var(--og-color-surface-elevated, var(--og-color-surface))',
                border: '1px solid var(--og-color-border-strong)',
                borderRadius: '8px',
                boxShadow: '0 6px 20px rgba(0,0,0,0.12)',
                padding: '4px',
              }}
            >
              {filteredDistricts.map((d) => (
                <div
                  key={d.districtId}
                  onClick={() => {
                    onChange({ ...value, district: d.districtName, ward: '' });
                    setActiveDropdown(null);
                  }}
                  style={{
                    padding: '8px 12px',
                    borderRadius: '6px',
                    cursor: 'pointer',
                    fontSize: '0.88rem',
                    color: 'var(--og-color-text-primary)',
                  }}
                  onMouseEnter={(e) => {
                    (e.currentTarget as HTMLElement).style.background = 'var(--og-color-surface-hover, rgba(0,0,0,0.05))';
                  }}
                  onMouseLeave={(e) => {
                    (e.currentTarget as HTMLElement).style.background = 'transparent';
                  }}
                >
                  {d.districtName}
                </div>
              ))}
            </div>
          )}
        </div>

        {/* 3. PHƯỜNG / XÃ COMBOBOX (Lọc theo Quận/Huyện) */}
        <div style={{ position: 'relative' }}>
          <Input
            id={`${prefix}-ward`}
            label="Phường / Xã"
            value={value.ward}
            onChange={(e) => {
              onChange({ ...value, ward: e.target.value });
              setActiveDropdown('ward');
            }}
            onFocus={() => !disabled && setActiveDropdown('ward')}
            required
            maxLength={100}
            disabled={disabled}
            placeholder={value.district ? 'Chọn hoặc nhập Phường / Xã (VD: Phường Bến Nghé)' : 'Vui lòng chọn Quận/Huyện trước'}
            autoComplete="off"
          />
          {activeDropdown === 'ward' && !disabled && wards.length > 0 && (
            <div
              className="og-combobox-dropdown"
              style={{
                position: 'absolute',
                top: 'calc(100% - 10px)',
                left: 0,
                right: 0,
                zIndex: 1040,
                maxHeight: '200px',
                overflowY: 'auto',
                background: 'var(--og-color-surface-elevated, var(--og-color-surface))',
                border: '1px solid var(--og-color-border-strong)',
                borderRadius: '8px',
                boxShadow: '0 6px 20px rgba(0,0,0,0.12)',
                padding: '4px',
              }}
            >
              {filteredWards.map((w) => (
                <div
                  key={w.wardCode}
                  onClick={() => {
                    onChange({ ...value, ward: w.wardName });
                    setActiveDropdown(null);
                  }}
                  style={{
                    padding: '8px 12px',
                    borderRadius: '6px',
                    cursor: 'pointer',
                    fontSize: '0.88rem',
                    color: 'var(--og-color-text-primary)',
                  }}
                  onMouseEnter={(e) => {
                    (e.currentTarget as HTMLElement).style.background = 'var(--og-color-surface-hover, rgba(0,0,0,0.05))';
                  }}
                  onMouseLeave={(e) => {
                    (e.currentTarget as HTMLElement).style.background = 'transparent';
                  }}
                >
                  {w.wardName}
                </div>
              ))}
            </div>
          )}
        </div>

        {/* 4. ĐỊA CHỈ CHI TIẾT */}
        <div>
          <Input
            id={`${prefix}-detail`}
            label="Địa chỉ chi tiết"
            value={value.detailAddress}
            onChange={(event) => onChange({ ...value, detailAddress: event.target.value })}
            required
            maxLength={255}
            disabled={disabled}
            placeholder="Số nhà, tên ngõ/đường, tòa nhà..."
          />
        </div>
      </div>
    </div>
  );
};
