export interface ReturnShipmentStatusMeta {
  label: string;
  color: string;
  bg: string;
}

export function getReturnShipmentStatusMeta(status?: string | null): ReturnShipmentStatusMeta {
  switch (status) {
    case 'RETURN_CREATED':
      return {
        label: 'Chờ bàn giao cho GHN',
        color: '#b45309',
        bg: '#fef3c7',
      };
    case 'RETURN_SHIPPING':
      return {
        label: 'Đang vận chuyển hoàn trả về Người bán',
        color: '#1d4ed8',
        bg: '#dbeafe',
      };
    case 'RETURN_DELIVERED':
      return {
        label: 'Đã giao trả thành công',
        color: '#15803d',
        bg: '#dcfce7',
      };
    default:
      return {
        label: 'Đang cập nhật hoàn trả',
        color: '#475569',
        bg: '#f1f5f9',
      };
  }
}

