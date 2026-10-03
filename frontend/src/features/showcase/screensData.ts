import { UserRole } from '../../shared/context';

export interface ScreenDefinition {
  id: string;
  code: string;
  name: { vi: string; en: string };
  module: 1 | 2 | 3 | 4;
  moduleName: { vi: string; en: string };
  role: UserRole;
  description: { vi: string; en: string };
}

export const ALL_SCREENS: ScreenDefinition[] = [
  // Module 1: Account Management & Authorization (14)
  {
    id: 'SCR-AUTH-01',
    code: 'SCR-AUTH-01',
    name: { vi: 'Đăng Ký Tài Khoản', en: 'Register Account' },
    module: 1,
    moduleName: { vi: '1. Quản Lý Tài Khoản & Phân Quyền', en: '1. Account & Authorization' },
    role: 'BUYER',
    description: { vi: 'Form đăng ký Buyer/Seller với kiểm tra mật khẩu mạnh và điều khoản Escrow', en: 'Registration form with strong password validation and Escrow terms' }
  },
  {
    id: 'SCR-AUTH-02',
    code: 'SCR-AUTH-02',
    name: { vi: 'Đăng Nhập', en: 'Login' },
    module: 1,
    moduleName: { vi: '1. Quản Lý Tài Khoản & Phân Quyền', en: '1. Account & Authorization' },
    role: 'BUYER',
    description: { vi: 'Đăng nhập email/mật khẩu, ghi nhớ đăng nhập và hỗ trợ SSO', en: 'Email/password authentication with remember me and SSO options' }
  },
  {
    id: 'SCR-AUTH-03',
    code: 'SCR-AUTH-03',
    name: { vi: 'Quên Mật Khẩu & Khôi Phục', en: 'Forgot Password & Reset' },
    module: 1,
    moduleName: { vi: '1. Quản Lý Tài Khoản & Phân Quyền', en: '1. Account & Authorization' },
    role: 'BUYER',
    description: { vi: 'Gửi mã xác minh OTP qua email để đặt lại mật khẩu an toàn', en: 'Send OTP recovery code to reset account credentials' }
  },
  {
    id: 'SCR-USER-01',
    code: 'SCR-USER-01',
    name: { vi: 'Hồ Sơ Cá Nhân & Địa Chỉ', en: 'Profile & Saved Addresses' },
    module: 1,
    moduleName: { vi: '1. Quản Lý Tài Khoản & Phân Quyền', en: '1. Account & Authorization' },
    role: 'BUYER',
    description: { vi: 'Quản lý thông tin cá nhân, sổ địa chỉ nhận hàng và số điện thoại xác thực', en: 'Manage user identity, shipping address book, and verified contact' }
  },
  {
    id: 'SCR-SELLER-BANK',
    code: 'SCR-SELLER-BANK',
    name: { vi: 'Tài Khoản Ngân Hàng Nhận Tiền', en: 'Seller Payout Bank Account' },
    module: 1,
    moduleName: { vi: '1. Quản Lý Tài Khoản & Phân Quyền', en: '1. Account & Authorization' },
    role: 'SELLER',
    description: { vi: 'Cấu hình STK ngân hàng thụ hưởng giải ngân từ Escrow', en: 'Configure beneficiary bank account for automated Escrow payouts' }
  },
  {
    id: 'SCR-KYC-01',
    code: 'SCR-KYC-01',
    name: { vi: 'Nộp Hồ Sơ Định Danh KYC Người Bán', en: 'Seller KYC Submission' },
    module: 1,
    moduleName: { vi: '1. Quản Lý Tài Khoản & Phân Quyền', en: '1. Account & Authorization' },
    role: 'SELLER',
    description: { vi: 'Tải ảnh CCCD/Hộ chiếu mặt trước, mặt sau và chân dung khuôn mặt', en: 'Upload citizen ID card front/back and face verification selfie' }
  },
  {
    id: 'SCR-KYC-02',
    code: 'SCR-KYC-02',
    name: { vi: 'Trạng Thái & Huy Hiệu Xác Thực KYC', en: 'KYC Status & Trust Badge' },
    module: 1,
    moduleName: { vi: '1. Quản Lý Tài Khoản & Phân Quyền', en: '1. Account & Authorization' },
    role: 'SELLER',
    description: { vi: 'Xem tiến độ phê duyệt, điểm uy tín và huy hiệu Đã Xác Thực', en: 'Check verification approval progress, trust score, and verified badge' }
  },
  {
    id: 'SCR-AUTH-2FA',
    code: 'SCR-AUTH-2FA',
    name: { vi: 'Bảo Mật Hai Lớp 2FA & Đổi Mật Khẩu', en: 'Two-Factor Auth (2FA) & Security' },
    module: 1,
    moduleName: { vi: '1. Quản Lý Tài Khoản & Phân Quyền', en: '1. Account & Authorization' },
    role: 'BUYER',
    description: { vi: 'Kích hoạt Google Authenticator TOTP và đổi mật khẩu định kỳ', en: 'Set up Google Authenticator TOTP app and update password' }
  },
  {
    id: 'SCR-SELLER-BLOCK',
    code: 'SCR-SELLER-BLOCK',
    name: { vi: 'Cảnh Báo Tài Khoản Bị Giới Hạn/Khóa', en: 'Account Restriction Notice' },
    module: 1,
    moduleName: { vi: '1. Quản Lý Tài Khoản & Phân Quyền', en: '1. Account & Authorization' },
    role: 'SELLER',
    description: { vi: 'Thông báo lý do tạm khóa do vi phạm chính sách giao dịch ngoài sàn', en: 'Notice detailing restriction due to off-platform transaction attempts' }
  },
  {
    id: 'SCR-ADMIN-USERS',
    code: 'SCR-ADMIN-USERS',
    name: { vi: 'Danh Sách Quản Lý Người Dùng', en: 'Admin User Directory' },
    module: 1,
    moduleName: { vi: '1. Quản Lý Tài Khoản & Phân Quyền', en: '1. Account & Authorization' },
    role: 'ADMIN',
    description: { vi: 'Tra cứu tài khoản, phân loại Buyer/Seller/KycVerified và lọc trạng thái', en: 'Lookup accounts, inspect roles, and filter account status' }
  },
  {
    id: 'SCR-ADMIN-PENALTY',
    code: 'SCR-ADMIN-PENALTY',
    name: { vi: 'Áp Dụng Chế Tài & Xử Phạt', en: 'User Penalty & Enforcement' },
    module: 1,
    moduleName: { vi: '1. Quản Lý Tài Khoản & Phân Quyền', en: '1. Account & Authorization' },
    role: 'ADMIN',
    description: { vi: 'Ghi nhận vi phạm, trừ điểm uy tín, cấm đăng tin hoặc khóa vĩnh viễn', en: 'Record violations, penalize trust score, freeze listings or ban account' }
  },
  {
    id: 'SCR-ADMIN-VOUCHER',
    code: 'SCR-ADMIN-VOUCHER',
    name: { vi: 'Cấu Hình & Phát Hành Mã Giảm Giá', en: 'Voucher & Promotion Setup' },
    module: 1,
    moduleName: { vi: '1. Quản Lý Tài Khoản & Phân Quyền', en: '1. Account & Authorization' },
    role: 'ADMIN',
    description: { vi: 'Thiết lập mã freeship, giảm giá đơn hàng và giới hạn ngân sách', en: 'Create freeship vouchers, order discounts, and set redemption budgets' }
  },
  {
    id: 'SCR-ADMIN-POPUP',
    code: 'SCR-ADMIN-POPUP',
    name: { vi: 'Quản Lý Banner & Thông Báo Khẩn', en: 'System Banner & Announcements' },
    module: 1,
    moduleName: { vi: '1. Quản Lý Tài Khoản & Phân Quyền', en: '1. Account & Authorization' },
    role: 'ADMIN',
    description: { vi: 'Cấu hình popup cảnh báo lừa đảo ngoài sàn và banner khuyến mãi trang chủ', en: 'Configure anti-fraud warning popups and homepage promotional banners' }
  },
  {
    id: 'SCR-ADMIN-STATS',
    code: 'SCR-ADMIN-STATS',
    name: { vi: 'Báo Cáo Tổng Quan Hoạt Động & GMV', en: 'Admin Analytics & Metrics' },
    module: 1,
    moduleName: { vi: '1. Quản Lý Tài Khoản & Phân Quyền', en: '1. Account & Authorization' },
    role: 'ADMIN',
    description: { vi: 'Biểu đồ tăng trưởng GMV, số dư Escrow, tỷ lệ tranh chấp và đơn hoàn tất', en: 'GMV growth, Escrow liquidity, dispute resolution rate, and orders' }
  },

  // Module 2: Commerce & Transactions (19)
  {
    id: 'SCR-BUYER-HOME',
    code: 'SCR-BUYER-HOME',
    name: { vi: 'Trang Chủ Khám Phá Đồ Cũ', en: 'Buyer Marketplace Home' },
    module: 2,
    moduleName: { vi: '2. Mua Bán & Giao Dịch', en: '2. Commerce & Transactions' },
    role: 'BUYER',
    description: { vi: 'Banner cam kết Escrow, danh mục ngành hàng, đồ sưu tầm chọn lọc', en: 'Escrow pledge banner, category cards, and curated vintage picks' }
  },
  {
    id: 'SCR-BUYER-SEARCH',
    code: 'SCR-BUYER-SEARCH',
    name: { vi: 'Bộ Lọc & Tìm Kiếm Theo Độ Hao Mòn', en: 'Search & Condition Filters' },
    module: 2,
    moduleName: { vi: '2. Mua Bán & Giao Dịch', en: '2. Commerce & Transactions' },
    role: 'BUYER',
    description: { vi: 'Lọc theo Like new/Good/Fair/Vintage, khoảng giá, địa điểm và có video đồng kiểm', en: 'Filter by wear grade, price range, city, and co-inspection video' }
  },
  {
    id: 'SCR-BUYER-SEMANTIC',
    code: 'SCR-BUYER-SEMANTIC',
    name: { vi: 'Tìm Kiếm AI Ngôn Ngữ Tự Nhiên', en: 'AI Natural Language Search' },
    module: 2,
    moduleName: { vi: '2. Mua Bán & Giao Dịch', en: '2. Commerce & Transactions' },
    role: 'BUYER',
    description: { vi: 'Tìm theo mô tả ngữ cảnh (ví dụ: "máy ảnh chụp phong cảnh dưới 25 triệu còn đẹp")', en: 'Find items by semantic context (e.g., "landscape camera under 25M clean")' }
  },
  {
    id: 'SCR-BUYER-DETAIL',
    code: 'SCR-BUYER-DETAIL',
    name: { vi: 'Chi Tiết Sản Phẩm & Bảng Thẩm Định', en: 'Product Detail & Appraisal' },
    module: 2,
    moduleName: { vi: '2. Mua Bán & Giao Dịch', en: '2. Commerce & Transactions' },
    role: 'BUYER',
    description: { vi: 'Ảnh chi tiết khuyết điểm, thanh đo độ hao mòn, video đồng kiểm và huy hiệu người bán', en: 'Defect photos, wear rating bar, co-inspection clip, and seller trust metrics' }
  },
  {
    id: 'SCR-BUYER-CONDITION',
    code: 'SCR-BUYER-CONDITION',
    name: { vi: 'Quy Chuẩn Thẩm Định Tình Trạng', en: 'Condition Standards Guide' },
    module: 2,
    moduleName: { vi: '2. Mua Bán & Giao Dịch', en: '2. Commerce & Transactions' },
    role: 'BUYER',
    description: { vi: 'Modal giải thích 4 thang độ hao mòn: 99% Như mới, 90-95% Khá tốt, 80% Đã dùng, Cổ/Sưu tầm', en: 'Modal explaining the 4 wear grades and defect declaration rules' }
  },
  {
    id: 'SCR-BUYER-CART',
    code: 'SCR-BUYER-CART',
    name: { vi: 'Giỏ Hàng Đồ Cũ', en: 'Second-hand Cart' },
    module: 2,
    moduleName: { vi: '2. Mua Bán & Giao Dịch', en: '2. Commerce & Transactions' },
    role: 'BUYER',
    description: { vi: 'Danh sách món đồ đã chọn, phân loại theo từng người bán và hiển thị phí giao hàng', en: 'Cart partitioned per unique seller with live shipping calculation' }
  },
  {
    id: 'SCR-BUYER-VOUCHER-MODAL',
    code: 'SCR-BUYER-VOUCHER-MODAL',
    name: { vi: 'Chọn Mã Giảm Giá Escrow', en: 'Select Escrow Voucher' },
    module: 2,
    moduleName: { vi: '2. Mua Bán & Giao Dịch', en: '2. Commerce & Transactions' },
    role: 'BUYER',
    description: { vi: 'Modal áp dụng voucher giảm phí giao dịch và mã miễn phí đồng kiểm', en: 'Modal to pick platform escrow coupons and freeship discounts' }
  },
  {
    id: 'SCR-BUYER-CHECKOUT',
    code: 'SCR-BUYER-CHECKOUT',
    name: { vi: 'Thanh Toán Ký Quỹ Escrow', en: 'Escrow Protected Checkout' },
    module: 2,
    moduleName: { vi: '2. Mua Bán & Giao Dịch', en: '2. Commerce & Transactions' },
    role: 'BUYER',
    description: { vi: 'Xác nhận địa chỉ, chọn phương thức VNPAY/Chuyển khoản ký quỹ, cam kết bảo vệ tiền', en: 'Confirm shipping, select Escrow payment method, and review safety guarantee' }
  },
  {
    id: 'SCR-BUYER-PAYMENT-SUCCESS',
    code: 'SCR-BUYER-PAYMENT-SUCCESS',
    name: { vi: 'Thanh Toán Thành Công — Tiền Vào Escrow', en: 'Payment Success & Escrow Hold' },
    module: 2,
    moduleName: { vi: '2. Mua Bán & Giao Dịch', en: '2. Commerce & Transactions' },
    role: 'BUYER',
    description: { vi: 'Chứng từ thanh toán an toàn, số tiền đang tạm giữ và các bước tiếp theo của người bán', en: 'Receipt showing funds safely locked in escrow and upcoming seller packaging' }
  },
  {
    id: 'SCR-CHAT-PRODUCT',
    code: 'SCR-CHAT-PRODUCT',
    name: { vi: 'Chat Đính Kèm Thẻ Sản Phẩm', en: 'Product-Linked Chat' },
    module: 2,
    moduleName: { vi: '2. Mua Bán & Giao Dịch', en: '2. Commerce & Transactions' },
    role: 'BUYER',
    description: { vi: 'Khung chat thời gian thực hiển thị ngay sản phẩm đang trao đổi ở đầu cuộc hội thoại', en: 'Live messaging with product status card pinned to chat header' }
  },
  {
    id: 'SCR-CHAT-OFFER',
    code: 'SCR-CHAT-OFFER',
    name: { vi: 'Trả Giá & Thương Lượng Tương Tác', en: 'Interactive Offer Bargaining' },
    module: 2,
    moduleName: { vi: '2. Mua Bán & Giao Dịch', en: '2. Commerce & Transactions' },
    role: 'BUYER',
    description: { vi: 'Thẻ trả giá tương tác trong tin nhắn: Chấp nhận, Từ chối, hoặc Đưa ra giá phản hồi', en: 'In-chat offer card supporting Accept, Decline, and Counter-offer actions' }
  },
  {
    id: 'SCR-CHAT-SAFETY-WARNING',
    code: 'SCR-CHAT-SAFETY-WARNING',
    name: { vi: 'Cảnh Báo Chống Giao Dịch Ngoài Sàn', en: 'Anti-Off-Platform Alert' },
    module: 2,
    moduleName: { vi: '2. Mua Bán & Giao Dịch', en: '2. Commerce & Transactions' },
    role: 'BUYER',
    description: { vi: 'Hệ thống tự động phát hiện số Zalo/SĐT/STK và bật cảnh báo rủi ro mất tiền', en: 'Automated detection of phone/Zalo/bank info with clear fraud risk banner' }
  },
  {
    id: 'SCR-SELLER-CREATE',
    code: 'SCR-SELLER-CREATE',
    name: { vi: 'Tạo Tin Đăng Bán Đồ Cũ', en: 'Create Second-Hand Listing' },
    module: 2,
    moduleName: { vi: '2. Mua Bán & Giao Dịch', en: '2. Commerce & Transactions' },
    role: 'SELLER',
    description: { vi: 'Form khai báo thông số, độ hao mòn, danh sách khuyết điểm và tải video đồng kiểm', en: 'Step-by-step form to declare condition, defects, and co-inspection video' }
  },
  {
    id: 'SCR-SELLER-AI-CHECK',
    code: 'SCR-SELLER-AI-CHECK',
    name: { vi: 'AI Kiểm Tra Ảnh & Gợi Ý Giá', en: 'AI Photo & Price Assistant' },
    module: 2,
    moduleName: { vi: '2. Mua Bán & Giao Dịch', en: '2. Commerce & Transactions' },
    role: 'SELLER',
    description: { vi: 'Trợ lý AI phân tích góc chụp, cảnh báo ảnh mờ và gợi ý mức giá bán hợp lý', en: 'AI scans upload quality, flags blurry angles, and recommends market price' }
  },
  {
    id: 'SCR-SELLER-KYC-LOCK',
    code: 'SCR-SELLER-KYC-LOCK',
    name: { vi: 'Khóa Tin Giá Trị Cao Chờ KYC', en: 'High-Value KYC Gate' },
    module: 2,
    moduleName: { vi: '2. Mua Bán & Giao Dịch', en: '2. Commerce & Transactions' },
    role: 'SELLER',
    description: { vi: 'Yêu cầu định danh danh tính khi người bán đăng tin trên 5 triệu đồng', en: 'Gate requiring seller identity verification for items priced over 5,000,000₫' }
  },
  {
    id: 'SCR-SELLER-LISTINGS',
    code: 'SCR-SELLER-LISTINGS',
    name: { vi: 'Quản Lý Danh Sách Tin Đăng', en: 'Seller Listings Dashboard' },
    module: 2,
    moduleName: { vi: '2. Mua Bán & Giao Dịch', en: '2. Commerce & Transactions' },
    role: 'SELLER',
    description: { vi: 'Danh sách tin đang bán, chờ duyệt, đã bán, số lượt xem và nút chỉnh sửa nhanh', en: 'Manage active, pending, and sold items with view stats and quick edit' }
  },
  {
    id: 'SCR-SELLER-ORDERS',
    code: 'SCR-SELLER-ORDERS',
    name: { vi: 'Quản Lý Đơn Hàng Cần Đóng Gói', en: 'Seller Order Fulfillment' },
    module: 2,
    moduleName: { vi: '2. Mua Bán & Giao Dịch', en: '2. Commerce & Transactions' },
    role: 'SELLER',
    description: { vi: 'Xử lý đơn mới, tải video đóng gói trước khi giao cho shipper, xem mã vận đơn', en: 'Process paid orders, attach pre-shipping pack video, and view waybill' }
  },
  {
    id: 'SCR-ADMIN-ESCROW',
    code: 'SCR-ADMIN-ESCROW',
    name: { vi: 'Quản Trị Dòng Tiền Ký Quỹ Escrow', en: 'Escrow Funds Operations' },
    module: 2,
    moduleName: { vi: '2. Mua Bán & Giao Dịch', en: '2. Commerce & Transactions' },
    role: 'ADMIN',
    description: { vi: 'Theo dõi tổng số tiền đang giữ an toàn, các lệnh chờ giải ngân và xử lý đóng băng', en: 'Monitor safe escrow pool, pending payout batches, and frozen disputes' }
  },
  {
    id: 'SCR-ADMIN-MODERATION',
    code: 'SCR-ADMIN-MODERATION',
    name: { vi: 'Duyệt & Kiểm Duyệt Tin Đăng', en: 'Listing Moderation Queue' },
    module: 2,
    moduleName: { vi: '2. Mua Bán & Giao Dịch', en: '2. Commerce & Transactions' },
    role: 'ADMIN',
    description: { vi: 'Hàng chờ duyệt sản phẩm mới, kiểm tra hình ảnh vi phạm hoặc thông tin thiếu minh bạch', en: 'Queue to approve new listings, flag suspicious photos, or reject violations' }
  },

  // Module 3: Shipping & Experience (6)
  {
    id: 'SCR-SHIP-TRACKING',
    code: 'SCR-SHIP-TRACKING',
    name: { vi: 'Theo Dõi Đơn Hàng & Đồng Kiểm', en: 'Co-Inspection Order Tracking' },
    module: 3,
    moduleName: { vi: '3. Vận Chuyển & Trải Nghiệm', en: '3. Shipping & Experience' },
    role: 'BUYER',
    description: { vi: 'Dòng thời gian vận chuyển chi tiết, thông tin shipper và nhắc nhở quay video mở hộp', en: 'Step-by-step courier progress, courier contact, and co-inspection reminders' }
  },
  {
    id: 'SCR-SHIP-CO-INSPECT',
    code: 'SCR-SHIP-CO-INSPECT',
    name: { vi: 'Quy Trình & Biên Bản Đồng Kiểm', en: 'Co-Inspection Protocol & Sign-off' },
    module: 3,
    moduleName: { vi: '3. Vận Chuyển & Trải Nghiệm', en: '3. Shipping & Experience' },
    role: 'BUYER',
    description: { vi: 'Quy định mở hàng trước mặt bưu tá, danh mục kiểm tra và nút xác nhận nhận hàng', en: 'Checklist for unboxing with courier and button to verify condition' }
  },
  {
    id: 'SCR-SELLER-PRINT-WAYBILL',
    code: 'SCR-SELLER-PRINT-WAYBILL',
    name: { vi: 'In Phiếu Gửi Hàng Đồng Kiểm', en: 'Print Shipping Waybill' },
    module: 3,
    moduleName: { vi: '3. Vận Chuyển & Trải Nghiệm', en: '3. Shipping & Experience' },
    role: 'SELLER',
    description: { vi: 'Mẫu phiếu gửi hàng tiêu chuẩn dán lên kiện hàng có mã vạch và ghi chú "Cho xem hàng"', en: 'Standard parcel waybill label with barcode and "Co-inspection Allowed" badge' }
  },
  {
    id: 'SCR-SHIP-RETURNS',
    code: 'SCR-SHIP-RETURNS',
    name: { vi: 'Theo Dõi Kiện Hàng Trả Về', en: 'Return Shipment Tracking' },
    module: 3,
    moduleName: { vi: '3. Vận Chuyển & Trải Nghiệm', en: '3. Shipping & Experience' },
    role: 'BUYER',
    description: { vi: 'Hành trình kiện hàng gửi trả lại cho người bán sau khi được duyệt trả hàng', en: 'Tracking return parcel journey back to seller after dispute approval' }
  },
  {
    id: 'SCR-REVIEW-PARTNER',
    code: 'SCR-REVIEW-PARTNER',
    name: { vi: 'Đánh Giá 2 Chiều & Chấm Sao Uy Tín', en: 'Bilateral Trust Rating' },
    module: 3,
    moduleName: { vi: '3. Vận Chuyển & Trải Nghiệm', en: '3. Shipping & Experience' },
    role: 'BUYER',
    description: { vi: 'Chấm điểm độ đúng mô tả, thái độ giao tiếp, đóng gói và tặng kèm O.G Xu', en: 'Rate accuracy, communication, packaging, and earn reward coins' }
  },
  {
    id: 'SCR-REWARDS-WALLET',
    code: 'SCR-REWARDS-WALLET',
    name: { vi: 'Ví O.G Xu & Điểm Thưởng', en: 'O.G Coins & Rewards Wallet' },
    module: 3,
    moduleName: { vi: '3. Vận Chuyển & Trải Nghiệm', en: '3. Shipping & Experience' },
    role: 'BUYER',
    description: { vi: 'Số dư xu tích lũy khi đồng kiểm/đánh giá và lịch sử đổi xu trừ tiền cước vận chuyển', en: 'Track coin balance earned from reviews and redeem for shipping discounts' }
  },

  // Module 4: Security & Dispute Resolution (5)
  {
    id: 'SCR-DISPUTE-CREATE',
    code: 'SCR-DISPUTE-CREATE',
    name: { vi: 'Mở Hồ Sơ Khiếu Nại & Trả Hàng', en: 'Open Dispute & Return Claim' },
    module: 4,
    moduleName: { vi: '4. Bảo Mật & Tranh Chấp', en: '4. Security & Disputes' },
    role: 'BUYER',
    description: { vi: 'Chọn lý do: Hàng không đúng mô tả, có khuyết điểm ẩn, hoặc bể vỡ do vận chuyển', en: 'State dispute reason: undisclosed defect, condition mismatch, or transit damage' }
  },
  {
    id: 'SCR-DISPUTE-EVIDENCE',
    code: 'SCR-DISPUTE-EVIDENCE',
    name: { vi: 'Tải Lên Bằng Chứng Ảnh & Video', en: 'Upload Inspection Evidence' },
    module: 4,
    moduleName: { vi: '4. Bảo Mật & Tranh Chấp', en: '4. Security & Disputes' },
    role: 'BUYER',
    description: { vi: 'Đính kèm video mở hộp đồng kiểm cùng shipper và ảnh chụp cận cảnh vết lỗi', en: 'Attach unboxing video recorded with courier and macro photos of defect' }
  },
  {
    id: 'SCR-DISPUTE-TIMELINE',
    code: 'SCR-DISPUTE-TIMELINE',
    name: { vi: 'Tiến Trình Phân Xử Khiếu Nại', en: 'Dispute Resolution Progress' },
    module: 4,
    moduleName: { vi: '4. Bảo Mật & Tranh Chấp', en: '4. Security & Disputes' },
    role: 'BUYER',
    description: { vi: 'Dòng thời gian phản hồi giữa Người mua - Người bán - Trọng tài viên O.G', en: 'Chronological timeline of Buyer, Seller, and O.G Arbitrator interactions' }
  },
  {
    id: 'SCR-ADMIN-DISPUTE-ROOM',
    code: 'SCR-ADMIN-DISPUTE-ROOM',
    name: { vi: 'Phòng Trọng Tài Xử Lý Tranh Chấp', en: 'Arbitration Dispute Room' },
    module: 4,
    moduleName: { vi: '4. Bảo Mật & Tranh Chấp', en: '4. Security & Disputes' },
    role: 'ADMIN',
    description: { vi: 'Giao diện admin so sánh video đóng gói của seller và video mở hộp của buyer', en: 'Specialist interface comparing seller pack video vs buyer unbox footage' }
  },
  {
    id: 'SCR-ADMIN-DISPUTE-DECIDE',
    code: 'SCR-ADMIN-DISPUTE-DECIDE',
    name: { vi: 'Phán Quyết & Xử Lý Tiền Escrow', en: 'Final Verdict & Escrow Settlement' },
    module: 4,
    moduleName: { vi: '4. Bảo Mật & Tranh Chấp', en: '4. Security & Disputes' },
    role: 'ADMIN',
    description: { vi: 'Ra phán quyết: Hoàn tiền 100%, Hoàn tiền một phần (bù trừ khuyết điểm), hoặc Giải ngân', en: 'Execute verdict: 100% refund to buyer, partial compensation, or release to seller' }
  }
];
