import React, { useState, useEffect } from 'react';
import { UserAddress, GhnProvince, GhnDistrict, GhnWard, profileApi } from '../index';
import { Alert, Badge, Button, Card, Input } from '../../../shared/components';

export const AddressBookTab: React.FC = () => {
  const [addresses, setAddresses] = useState<UserAddress[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  // Form state
  const [isEditing, setIsEditing] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [recipientName, setRecipientName] = useState('');
  const [phoneNumber, setPhoneNumber] = useState('');
  const [province, setProvince] = useState('');
  const [district, setDistrict] = useState('');
  const [ward, setWard] = useState('');
  const [detailAddress, setDetailAddress] = useState('');
  const [isDefault, setIsDefault] = useState(false);
  const [isSaving, setIsSaving] = useState(false);

  // GHN Master Data Cascading Select state
  const [provinces, setProvinces] = useState<GhnProvince[]>([]);
  const [districts, setDistricts] = useState<GhnDistrict[]>([]);
  const [wards, setWards] = useState<GhnWard[]>([]);
  const [selectedProvinceId, setSelectedProvinceId] = useState<number | ''>('');
  const [selectedDistrictId, setSelectedDistrictId] = useState<number | ''>('');
  const [selectedWardCode, setSelectedWardCode] = useState<string>('');
  const [isLoadingProvinces, setIsLoadingProvinces] = useState(false);
  const [isLoadingDistricts, setIsLoadingDistricts] = useState(false);
  const [isLoadingWards, setIsLoadingWards] = useState(false);

  const fetchAddresses = async () => {
    setIsLoading(true);
    try {
      const list = await profileApi.getAddresses();
      setAddresses(list);
      setErrorMsg(null);
    } catch (err: unknown) {
      setErrorMsg(err instanceof Error ? err.message : 'Không thể tải danh sách địa chỉ.');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchAddresses();
  }, []);

  const loadProvinces = async () => {
    if (provinces.length > 0) return provinces;
    setIsLoadingProvinces(true);
    try {
      const list = await profileApi.getProvinces();
      setProvinces(list);
      return list;
    } catch (err: unknown) {
      setErrorMsg('Không thể tải danh mục Tỉnh/Thành phố từ GHN. Vui lòng thử lại.');
      return [];
    } finally {
      setIsLoadingProvinces(false);
    }
  };

  const handleProvinceChange = async (provId: number | '') => {
    setSelectedProvinceId(provId);
    setSelectedDistrictId('');
    setSelectedWardCode('');
    setDistricts([]);
    setWards([]);

    if (provId === '') {
      setProvince('');
      setDistrict('');
      setWard('');
      return;
    }

    const p = provinces.find((item) => item.provinceId === provId);
    setProvince(p ? p.provinceName : '');
    setDistrict('');
    setWard('');

    setIsLoadingDistricts(true);
    try {
      const distList = await profileApi.getDistricts(provId);
      setDistricts(distList);
    } catch {
      setErrorMsg('Không thể tải danh mục Quận/Huyện từ GHN.');
    } finally {
      setIsLoadingDistricts(false);
    }
  };

  const handleDistrictChange = async (distId: number | '') => {
    setSelectedDistrictId(distId);
    setSelectedWardCode('');
    setWards([]);

    if (distId === '') {
      setDistrict('');
      setWard('');
      return;
    }

    const d = districts.find((item) => item.districtId === distId);
    setDistrict(d ? d.districtName : '');
    setWard('');

    setIsLoadingWards(true);
    try {
      const wardList = await profileApi.getWards(distId);
      setWards(wardList);
    } catch {
      setErrorMsg('Không thể tải danh mục Phường/Xã từ GHN.');
    } finally {
      setIsLoadingWards(false);
    }
  };

  const handleWardChange = (wCode: string) => {
    setSelectedWardCode(wCode);
    const w = wards.find((item) => item.wardCode === wCode);
    setWard(w ? w.wardName : '');
  };

  const openCreateForm = async () => {
    setIsEditing(true);
    setEditingId(null);
    setRecipientName('');
    setPhoneNumber('');
    setProvince('');
    setDistrict('');
    setWard('');
    setSelectedProvinceId('');
    setSelectedDistrictId('');
    setSelectedWardCode('');
    setDistricts([]);
    setWards([]);
    setDetailAddress('');
    setIsDefault(addresses.length === 0);
    setErrorMsg(null);

    await loadProvinces();
  };

  const openEditForm = async (addr: UserAddress) => {
    setIsEditing(true);
    setEditingId(addr.id);
    setRecipientName(addr.recipientName);
    setPhoneNumber(addr.phoneNumber);
    setProvince(addr.province);
    setDistrict(addr.district);
    setWard(addr.ward);
    setDetailAddress(addr.detailAddress);
    setIsDefault(addr.isDefault);
    setErrorMsg(null);

    await loadProvinces();

    if (addr.ghnProvinceId) {
      setSelectedProvinceId(addr.ghnProvinceId);
      setIsLoadingDistricts(true);
      try {
        const distList = await profileApi.getDistricts(addr.ghnProvinceId);
        setDistricts(distList);

        if (addr.ghnDistrictId) {
          setSelectedDistrictId(addr.ghnDistrictId);
          setIsLoadingWards(true);
          try {
            const wardList = await profileApi.getWards(addr.ghnDistrictId);
            setWards(wardList);
            if (addr.ghnWardCode) {
              setSelectedWardCode(addr.ghnWardCode);
            }
          } catch {
            // bỏ qua
          } finally {
            setIsLoadingWards(false);
          }
        }
      } catch {
        // bỏ qua
      } finally {
        setIsLoadingDistricts(false);
      }
    } else {
      setSelectedProvinceId('');
      setSelectedDistrictId('');
      setSelectedWardCode('');
      setDistricts([]);
      setWards([]);
    }
  };

  const closeForm = () => {
    setIsEditing(false);
    setEditingId(null);
    setErrorMsg(null);
  };

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!recipientName.trim() || !phoneNumber.trim() || !detailAddress.trim()) {
      setErrorMsg('Vui lòng điền đầy đủ họ tên, số điện thoại và địa chỉ chi tiết.');
      return;
    }

    if (!selectedProvinceId || !selectedDistrictId || !selectedWardCode) {
      setErrorMsg('Vui lòng chọn đầy đủ Tỉnh/Thành phố, Quận/Huyện và Phường/Xã từ danh mục chuẩn GHN.');
      return;
    }

    setIsSaving(true);
    setErrorMsg(null);

    try {
      const payload = {
        recipientName: recipientName.trim(),
        phoneNumber: phoneNumber.trim(),
        province: province.trim(),
        district: district.trim(),
        ward: ward.trim(),
        detailAddress: detailAddress.trim(),
        ghnProvinceId: Number(selectedProvinceId),
        ghnDistrictId: Number(selectedDistrictId),
        ghnWardCode: selectedWardCode.trim(),
        isDefault,
      };

      if (editingId) {
        await profileApi.updateAddress(editingId, payload);
        setSuccessMsg('Cập nhật địa chỉ thành công!');
      } else {
        await profileApi.createAddress(payload);
        setSuccessMsg('Thêm địa chỉ mới thành công!');
      }
      closeForm();
      await fetchAddresses();
      setTimeout(() => setSuccessMsg(null), 4000);
    } catch (err: unknown) {
      setErrorMsg(err instanceof Error ? err.message : 'Lưu địa chỉ thất bại.');
    } finally {
      setIsSaving(false);
    }
  };

  const handleDelete = async (id: number) => {
    if (!window.confirm('Bạn có chắc chắn muốn xóa địa chỉ này?')) return;
    try {
      await profileApi.deleteAddress(id);
      setSuccessMsg('Đã xóa địa chỉ thành công!');
      await fetchAddresses();
      setTimeout(() => setSuccessMsg(null), 4000);
    } catch (err: unknown) {
      setErrorMsg(err instanceof Error ? err.message : 'Xóa địa chỉ thất bại.');
    }
  };

  const handleSetDefault = async (id: number) => {
    try {
      await profileApi.setDefaultAddress(id);
      setSuccessMsg('Đã đặt làm địa chỉ mặc định thành công!');
      await fetchAddresses();
      setTimeout(() => setSuccessMsg(null), 4000);
    } catch (err: unknown) {
      setErrorMsg(err instanceof Error ? err.message : 'Không thể đặt làm mặc định.');
    }
  };

  const isNormalized = (addr: UserAddress) => {
    return Boolean(addr.ghnDistrictId && addr.ghnWardCode);
  };

  return (
    <div className="og-profile-tab">
      {/* Banner hướng dẫn Seller */}
      <div style={{ marginBottom: '16px' }}>
        <Alert type="info">
          📦 <strong>Địa chỉ lấy hàng cho Người bán:</strong> Địa chỉ được đánh dấu <strong>Mặc định</strong> trong sổ địa chỉ này sẽ được hệ thống Giao Hàng Nhanh (GHN) tự động sử dụng làm địa chỉ kho lấy hàng khi có người mua đặt đơn hàng của bạn.
        </Alert>
      </div>

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
        <h3 style={{ margin: 0, fontSize: '1.2rem', color: 'var(--og-color-text-primary)' }}>
          Sổ địa chỉ nhận & gửi hàng
        </h3>
        {!isEditing && (
          <Button variant="primary" size="sm" onClick={openCreateForm}>
            + Thêm địa chỉ mới
          </Button>
        )}
      </div>

      {successMsg && <Alert type="success">{successMsg}</Alert>}
      {errorMsg && <Alert type="danger">{errorMsg}</Alert>}

      {/* Form Thêm/Sửa */}
      {isEditing && (
        <Card title={editingId ? 'Chỉnh sửa địa chỉ' : 'Thêm địa chỉ mới'} style={{ marginBottom: '24px' }}>
          <form onSubmit={handleSave} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: '14px' }}>
              <div>
                <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: '4px' }}>
                  Họ và tên người nhận / người gửi *
                </label>
                <Input
                  value={recipientName}
                  onChange={(e) => setRecipientName(e.target.value)}
                  placeholder="Ví dụ: Nguyễn Văn A"
                  required
                />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: '4px' }}>
                  Số điện thoại liên hệ *
                </label>
                <Input
                  value={phoneNumber}
                  onChange={(e) => setPhoneNumber(e.target.value)}
                  placeholder="Ví dụ: 0987654321"
                  required
                />
              </div>
            </div>

            {/* Cascading Selects cho Tỉnh/Thành -> Quận/Huyện -> Phường/Xã */}
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '14px' }}>
              <div>
                <label htmlFor="select-province" style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: '4px' }}>
                  Tỉnh / Thành phố (GHN) *
                </label>
                <select
                  id="select-province"
                  className="og-input"
                  style={{ width: '100%', height: '42px', padding: '0 10px', fontSize: '0.9rem', backgroundColor: '#fff' }}
                  value={selectedProvinceId}
                  onChange={(e) => handleProvinceChange(e.target.value ? Number(e.target.value) : '')}
                  disabled={isLoadingProvinces}
                  required
                >
                  <option value="">{isLoadingProvinces ? 'Đang tải danh mục...' : '-- Chọn Tỉnh / Thành phố --'}</option>
                  {provinces.map((p) => (
                    <option key={p.provinceId} value={p.provinceId}>
                      {p.provinceName}
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label htmlFor="select-district" style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: '4px' }}>
                  Quận / Huyện (GHN) *
                </label>
                <select
                  id="select-district"
                  className="og-input"
                  style={{ width: '100%', height: '42px', padding: '0 10px', fontSize: '0.9rem', backgroundColor: '#fff' }}
                  value={selectedDistrictId}
                  onChange={(e) => handleDistrictChange(e.target.value ? Number(e.target.value) : '')}
                  disabled={!selectedProvinceId || isLoadingDistricts}
                  required
                >
                  <option value="">
                    {isLoadingDistricts
                      ? 'Đang tải Quận/Huyện...'
                      : !selectedProvinceId
                      ? '-- Vui lòng chọn Tỉnh trước --'
                      : '-- Chọn Quận / Huyện --'}
                  </option>
                  {districts.map((d) => (
                    <option key={d.districtId} value={d.districtId}>
                      {d.districtName}
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label htmlFor="select-ward" style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: '4px' }}>
                  Phường / Xã (GHN) *
                </label>
                <select
                  id="select-ward"
                  className="og-input"
                  style={{ width: '100%', height: '42px', padding: '0 10px', fontSize: '0.9rem', backgroundColor: '#fff' }}
                  value={selectedWardCode}
                  onChange={(e) => handleWardChange(e.target.value)}
                  disabled={!selectedDistrictId || isLoadingWards}
                  required
                >
                  <option value="">
                    {isLoadingWards
                      ? 'Đang tải Phường/Xã...'
                      : !selectedDistrictId
                      ? '-- Vui lòng chọn Huyện trước --'
                      : '-- Chọn Phường / Xã --'}
                  </option>
                  {wards.map((w) => (
                    <option key={w.wardCode} value={w.wardCode}>
                      {w.wardName}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            <div>
              <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: '4px' }}>
                Địa chỉ chi tiết (Số nhà, tên đường, tòa nhà) *
              </label>
              <Input
                value={detailAddress}
                onChange={(e) => setDetailAddress(e.target.value)}
                placeholder="Ví dụ: 123 Lê Lợi, Tòa nhà ABC, Phòng 402"
                required
              />
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <input
                type="checkbox"
                id="isDefaultAddr"
                checked={isDefault}
                onChange={(e) => setIsDefault(e.target.checked)}
                style={{ width: '16px', height: '16px', accentColor: 'var(--og-color-primary)' }}
              />
              <label htmlFor="isDefaultAddr" style={{ fontSize: '0.9rem', cursor: 'pointer' }}>
                Đặt làm địa chỉ mặc định (ưu tiên nhận hàng và lấy hàng)
              </label>
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '10px' }}>
              <Button variant="outline" type="button" onClick={closeForm}>
                Hủy bỏ
              </Button>
              <Button variant="primary" type="submit" disabled={isSaving}>
                {isSaving ? 'Đang lưu...' : 'Lưu địa chỉ chuẩn GHN'}
              </Button>
            </div>
          </form>
        </Card>
      )}

      {/* Danh sách địa chỉ */}
      {isLoading ? (
        <p style={{ color: 'var(--og-color-text-secondary)', textAlign: 'center', padding: '20px 0' }}>
          Đang tải sổ địa chỉ...
        </p>
      ) : addresses.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '36px 0', border: '1px dashed var(--og-color-border-subtle)', borderRadius: '8px' }}>
          <p style={{ margin: '0 0 12px', color: 'var(--og-color-text-secondary)' }}>
            Bạn chưa lưu địa chỉ nào.
          </p>
          <Button variant="primary" size="sm" onClick={openCreateForm}>
            + Thêm địa chỉ đầu tiên
          </Button>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
          {addresses.map((addr) => {
            const normalized = isNormalized(addr);
            return (
              <div
                key={addr.id}
                style={{
                  padding: '16px',
                  border: `1.5px solid ${addr.isDefault ? 'var(--og-color-primary)' : 'var(--og-color-border-subtle)'}`,
                  borderRadius: '8px',
                  backgroundColor: addr.isDefault ? 'rgba(29, 73, 57, 0.03)' : 'var(--og-color-surface-elevated)',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'flex-start',
                  flexWrap: 'wrap',
                  gap: '12px',
                }}
              >
                <div style={{ flex: '1 1 300px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: '8px', marginBottom: '6px' }}>
                    <strong style={{ fontSize: '1rem', color: 'var(--og-color-text-primary)' }}>
                      {addr.recipientName}
                    </strong>
                    <span style={{ color: 'var(--og-color-text-secondary)', fontSize: '0.9rem' }}>
                      ({addr.phoneNumber})
                    </span>
                    {addr.isDefault && <Badge variant="buyer">Mặc định</Badge>}
                    {normalized ? (
                      <span
                        style={{
                          fontSize: '0.75rem',
                          fontWeight: 600,
                          color: '#065f46',
                          backgroundColor: '#d1fae5',
                          padding: '2px 8px',
                          borderRadius: '4px',
                        }}
                      >
                        ✓ Đã chuẩn hóa GHN
                      </span>
                    ) : (
                      <span
                        style={{
                          fontSize: '0.75rem',
                          fontWeight: 600,
                          color: '#9a3412',
                          backgroundColor: '#ffedd5',
                          padding: '2px 8px',
                          borderRadius: '4px',
                        }}
                      >
                        ⚠️ Cần cập nhật chuẩn GHN
                      </span>
                    )}
                  </div>
                  <p style={{ margin: '0 0 4px', fontSize: '0.92rem', color: 'var(--og-color-text-primary)' }}>
                    {addr.detailAddress}
                  </p>
                  <p style={{ margin: 0, fontSize: '0.85rem', color: 'var(--og-color-text-secondary)' }}>
                    {addr.ward}, {addr.district}, {addr.province}
                  </p>
                  {!normalized && (
                    <p style={{ margin: '6px 0 0', fontSize: '0.8rem', color: '#b45309' }}>
                      Địa chỉ này chưa có mã bưu chính GHN. Vui lòng bấm &quot;Sửa&quot; và chọn lại Tỉnh/Huyện/Xã để có thể giao/nhận hàng.
                    </p>
                  )}
                </div>

                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  {!addr.isDefault && (
                    <Button variant="outline" size="sm" onClick={() => handleSetDefault(addr.id)}>
                      Đặt mặc định
                    </Button>
                  )}
                  <Button variant="outline" size="sm" onClick={() => openEditForm(addr)}>
                    Sửa
                  </Button>
                  <Button variant="outline" size="sm" onClick={() => handleDelete(addr.id)}>
                    Xóa
                  </Button>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};
