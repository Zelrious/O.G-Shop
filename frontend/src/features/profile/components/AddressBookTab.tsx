import React, { useState, useEffect } from 'react';
import { UserAddress, profileApi } from '../index';
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

  const openCreateForm = () => {
    setIsEditing(true);
    setEditingId(null);
    setRecipientName('');
    setPhoneNumber('');
    setProvince('');
    setDistrict('');
    setWard('');
    setDetailAddress('');
    setIsDefault(addresses.length === 0);
    setErrorMsg(null);
  };

  const openEditForm = (addr: UserAddress) => {
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
  };

  const closeForm = () => {
    setIsEditing(false);
    setEditingId(null);
  };

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!recipientName.trim() || !phoneNumber.trim() || !province.trim() || !district.trim() || !ward.trim() || !detailAddress.trim()) {
      setErrorMsg('Vui lòng điền đầy đủ các thông tin địa chỉ bắt buộc.');
      return;
    }

    setIsSaving(true);
    setErrorMsg(null);

    try {
      if (editingId) {
        await profileApi.updateAddress(editingId, {
          recipientName: recipientName.trim(),
          phoneNumber: phoneNumber.trim(),
          province: province.trim(),
          district: district.trim(),
          ward: ward.trim(),
          detailAddress: detailAddress.trim(),
          isDefault,
        });
        setSuccessMsg('Cập nhật địa chỉ thành công!');
      } else {
        await profileApi.createAddress({
          recipientName: recipientName.trim(),
          phoneNumber: phoneNumber.trim(),
          province: province.trim(),
          district: district.trim(),
          ward: ward.trim(),
          detailAddress: detailAddress.trim(),
          isDefault,
        });
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

  return (
    <div className="og-profile-tab">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
        <h3 style={{ margin: 0, fontSize: '1.2rem', color: 'var(--og-color-text-primary)' }}>
          Sổ địa chỉ nhận hàng
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
        <Card title={editingId ? 'Chỉnh sửa địa chỉ' : 'Thêm địa chỉ nhận hàng mới'} style={{ marginBottom: '24px' }}>
          <form onSubmit={handleSave} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: '14px' }}>
              <div>
                <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: '4px' }}>
                  Họ và tên người nhận *
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
                  Số điện thoại người nhận *
                </label>
                <Input
                  value={phoneNumber}
                  onChange={(e) => setPhoneNumber(e.target.value)}
                  placeholder="Ví dụ: 0987654321"
                  required
                />
              </div>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '14px' }}>
              <div>
                <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: '4px' }}>
                  Tỉnh / Thành phố *
                </label>
                <Input
                  value={province}
                  onChange={(e) => setProvince(e.target.value)}
                  placeholder="Ví dụ: TP. Hồ Chí Minh"
                  required
                />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: '4px' }}>
                  Quận / Huyện *
                </label>
                <Input
                  value={district}
                  onChange={(e) => setDistrict(e.target.value)}
                  placeholder="Ví dụ: Quận 1"
                  required
                />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: '4px' }}>
                  Phường / Xã *
                </label>
                <Input
                  value={ward}
                  onChange={(e) => setWard(e.target.value)}
                  placeholder="Ví dụ: Phường Bến Nghé"
                  required
                />
              </div>
            </div>

            <div>
              <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: '4px' }}>
                Địa chỉ chi tiết (Số nhà, tên đường, tòa nhà) *
              </label>
              <Input
                value={detailAddress}
                onChange={(e) => setDetailAddress(e.target.value)}
                placeholder="Ví dụ: 123 Lê Lợi, Chung cư ABC, P. 402"
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
                Đặt làm địa chỉ nhận hàng mặc định
              </label>
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '10px' }}>
              <Button variant="outline" type="button" onClick={closeForm}>
                Hủy bỏ
              </Button>
              <Button variant="primary" type="submit" disabled={isSaving}>
                {isSaving ? 'Đang lưu...' : 'Lưu địa chỉ'}
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
            Bạn chưa lưu địa chỉ nhận hàng nào.
          </p>
          <Button variant="primary" size="sm" onClick={openCreateForm}>
            + Thêm địa chỉ đầu tiên
          </Button>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
          {addresses.map((addr) => (
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
              <div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '6px' }}>
                  <strong style={{ fontSize: '1rem', color: 'var(--og-color-text-primary)' }}>
                    {addr.recipientName}
                  </strong>
                  <span style={{ color: 'var(--og-color-text-secondary)', fontSize: '0.9rem' }}>
                    ({addr.phoneNumber})
                  </span>
                  {addr.isDefault && <Badge variant="buyer">Mặc định</Badge>}
                </div>
                <p style={{ margin: '0 0 4px', fontSize: '0.92rem', color: 'var(--og-color-text-primary)' }}>
                  {addr.detailAddress}
                </p>
                <p style={{ margin: 0, fontSize: '0.85rem', color: 'var(--og-color-text-secondary)' }}>
                  {addr.ward}, {addr.district}, {addr.province}
                </p>
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
          ))}
        </div>
      )}
    </div>
  );
};
