import { createContext } from 'react';

export type Language = 'vi' | 'en';

export interface Translations {
  [key: string]: {
    vi: string;
    en: string;
  };
}

export const translations: Translations = {
  // Navigation & General
  'nav.home': { vi: 'Trang Chủ', en: 'Home' },
  'nav.marketplace': { vi: 'Chợ Đồ Cũ', en: 'Marketplace' },
  'nav.sell': { vi: 'Đăng Tin', en: 'Sell Item' },
  'nav.chat': { vi: 'Tin Nhắn', en: 'Messages' },
  'nav.cart': { vi: 'Giỏ Hàng', en: 'Cart' },
  'nav.account': { vi: 'Tài Khoản', en: 'Account' },
  'nav.orders': { vi: 'Đơn Hàng', en: 'Orders' },
  'nav.notifications': { vi: 'Thông Báo', en: 'Notifications' },
  'nav.wallet': { vi: 'Ví & Xu', en: 'Wallet & Coins' },
  'nav.admin': { vi: 'Quản Trị Admin', en: 'Admin Portal' },
  'nav.showcase': { vi: 'Bản Đồ 44 Màn Hình', en: '44 Screens Map' },

  // Roles
  'role.buyer': { vi: 'Người Mua (Buyer)', en: 'Buyer' },
  'role.seller': { vi: 'Người Bán (Seller)', en: 'Seller' },
  'role.admin': { vi: 'Quản Trị Viên (Admin)', en: 'Administrator' },
  'role.switch': { vi: 'Chuyển đổi vai trò', en: 'Switch Persona Role' },

  // Themes & Devices
  'theme.light': { vi: 'Giao diện Sáng', en: 'Light Mode' },
  'theme.dark': { vi: 'Giao diện Tối', en: 'Dark Mode' },
  'theme.system': { vi: 'Theo Hệ Thống', en: 'System Mode' },
  'device.desktop': { vi: 'Máy tính (Desktop)', en: 'Desktop View' },
  'device.mobile': { vi: 'Điện thoại (Mobile Frame)', en: 'Mobile Device Frame' },

  // Common Actions
  'action.search': { vi: 'Tìm kiếm sản phẩm, thương hiệu...', en: 'Search items, brands, models...' },
  'action.semanticSearch': { vi: 'Tìm kiếm mô tả thông minh AI', en: 'AI Natural Language Search' },
  'action.buyNow': { vi: 'Mua Ngay (Bảo Vệ Escrow)', en: 'Buy Now (Escrow Protected)' },
  'action.makeOffer': { vi: 'Thương Lượng / Trả Giá', en: 'Make an Offer' },
  'action.chatSeller': { vi: 'Chat Với Người Bán', en: 'Chat with Seller' },
  'action.addToCart': { vi: 'Thêm Vào Giỏ', en: 'Add to Cart' },
  'action.applyVoucher': { vi: 'Áp Dụng Mã Giảm Giá', en: 'Apply Voucher' },
  'action.confirmReceipt': { vi: 'Xác Nhận Đã Nhận & Hài Lòng', en: 'Confirm Received & Satisfied' },
  'action.requestDispute': { vi: 'Khiếu Nại / Yêu Cầu Trả Hàng', en: 'Open Dispute / Return' },
  'action.save': { vi: 'Lưu Thay Đổi', en: 'Save Changes' },
  'action.cancel': { vi: 'Hủy Bỏ', en: 'Cancel' },
  'action.close': { vi: 'Đóng', en: 'Close' },
  'action.back': { vi: 'Quay Lại', en: 'Back' },
  'action.upload': { vi: 'Tải Ảnh / Video Lên', en: 'Upload Photos / Video' },
  'action.printWaybill': { vi: 'In Phiếu Gửi Hàng', en: 'Print Shipping Waybill' },

  // Badges & Statuses
  'status.condition.like_new': { vi: 'Như Mới 99%', en: 'Like New 99%' },
  'status.condition.good': { vi: 'Khá Tốt 90-95%', en: 'Good 90-95%' },
  'status.condition.fair': { vi: 'Đã Qua Sử Dụng 80%', en: 'Fair 80%' },
  'status.condition.vintage': { vi: 'Đồ Cổ / Sưu Tầm', en: 'Vintage / Collectible' },
  'status.escrow_holding': { vi: 'Escrow Đang Giữ Tiền An Toàn', en: 'Escrow Holding Payment' },
  'status.escrow_released': { vi: 'Đã Giải Ngân Cho Người Bán', en: 'Escrow Released to Seller' },
  'status.dispute_in_review': { vi: 'Admin Đang Xử Lý Khiếu Nại', en: 'Admin Reviewing Dispute' },
  'status.co_inspection_passed': { vi: 'Đồng Kiểm Thành Công', en: 'Co-Inspection Verified' },

  // Warnings
  'warning.off_platform': {
    vi: 'CẢNH BÁO AN TOÀN: Hệ thống phát hiện tin nhắn chứa SĐT/Zalo/STK ngân hàng. Giao dịch ngoài sàn sẽ KHÔNG được O.G Escrow bảo vệ và bạn có thể bị lừa đảo!',
    en: 'SAFETY WARNING: System detected contact details/bank info. Off-platform transactions are NOT protected by O.G Escrow and expose you to fraud risk!'
  },
  'warning.kyc_required': {
    vi: 'Bạn cần hoàn tất định danh KYC người bán trước khi rút tiền hoặc đăng tin có giá trị cao.',
    en: 'Seller KYC verification is required before withdrawing escrow funds or posting high-value listings.'
  }
};

export interface I18nContextType {
  language: Language;
  setLanguage: (lang: Language) => void;
  toggleLanguage: () => void;
  t: (key: string) => string;
}

export const I18nContext = createContext<I18nContextType | undefined>(undefined);
