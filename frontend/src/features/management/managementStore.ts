// Management Store for KTV and Admin Operations

export interface EscrowRecord {
  id: string;
  orderId: string;
  orderCode: string;
  buyerName: string;
  sellerName: string;
  productTitle: string;
  amount: number;
  status: 'HELD' | 'FROZEN' | 'RELEASED' | 'REFUNDED';
  createdAt: string;
  updatedAt: string;
  releaseDeadline?: string;
  notes?: string;
}

export interface NormalUserRecord {
  id: number;
  fullName: string;
  email: string;
  phoneNumber: string;
  roles: ('BUYER' | 'SELLER')[];
  status: 'ACTIVE' | 'LOCKED';
  lockReason?: string;
  joinedAt: string;
  completedOrdersCount: number;
  rating: number;
}

export interface DisputeRecord {
  id: string;
  orderCode: string;
  buyerName: string;
  sellerName: string;
  productTitle: string;
  disputeAmount: number;
  reason: string;
  description: string;
  buyerEvidenceUrls: string[];
  sellerResponse?: string;
  status: 'OPEN' | 'INVESTIGATING' | 'RETURN_APPROVED' | 'RESOLVED_REFUND' | 'RESOLVED_RELEASE';
  verdictAction?: 'FULL_REFUND' | 'RELEASE_SELLER' | 'PARTIAL_REFUND';
  verdictNotes?: string;
  createdAt: string;
  updatedAt: string;
}

export interface KycSubmissionRecord {
  id: string;
  userId: number;
  fullName: string;
  cccdNumber: string;
  dateOfBirth: string;
  address: string;
  idCardFrontUrl: string;
  idCardBackUrl: string;
  portraitUrl: string;
  submittedAt: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  reviewedBy?: string;
  rejectReason?: string;
}

export interface ComplaintRecord {
  id: string;
  title: string;
  complainantName: string;
  complainantEmail: string;
  complainantRole: 'BUYER' | 'SELLER';
  type: 'TRANSACTION' | 'SYSTEM_ERROR' | 'SELLER_BEHAVIOR';
  urgency: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  status: 'PENDING' | 'RESOLVED' | 'ESCALATED_TO_ADMIN';
  description: string;
  resolution?: string;
  escalatedAt?: string;
  escalationNote?: string;
  createdAt: string;
}

export interface EmergencyAlertRecord {
  id: string;
  complaintId?: string;
  ktvName: string;
  title: string;
  details: string;
  severity: 'HIGH' | 'CRITICAL';
  createdAt: string;
  status: 'UNREAD' | 'INVESTIGATING' | 'RESOLVED';
  adminNote?: string;
}

export interface KtvAccountRecord {
  id: number;
  fullName: string;
  email: string;
  phoneNumber: string;
  status: 'ACTIVE' | 'INACTIVE';
  assignedZone: string;
  casesResolvedCount: number;
  createdAt: string;
  lastActive: string;
}

export interface AuditLogRecord {
  id: string;
  actorName: string;
  actorRole: 'ADMIN' | 'KTV' | 'SYSTEM';
  action: string;
  target: string;
  ipAddress: string;
  timestamp: string;
  status: 'SUCCESS' | 'FAILED';
  details?: string;
}

export interface SystemFeeSettings {
  escrowFeePercent: number; // e.g. 2.5%
  minTransactionFee: number; // e.g. 10,000 VND
  withdrawalFeeVnd: number; // e.g. 5,000 VND
  buyerProtectionFixedFee: number; // e.g. 15,000 VND
  updatedAt: string;
  updatedBy: string;
}

export interface VoucherRecord {
  id: string;
  code: string;
  name: string;
  discountType: 'AMOUNT' | 'PERCENT';
  discountValue: number;
  minOrderValue: number;
  totalQuantity: number;
  usedQuantity: number;
  startDate: string;
  endDate: string;
  status: 'ACTIVE' | 'INACTIVE' | 'EXPIRED';
  createdBy: string;
}

export interface GrantedVoucherRecord {
  id: string;
  voucherId: string;
  voucherCode: string;
  voucherName: string;
  discountValueFormatted: string;
  userId: number;
  userEmail: string;
  grantedBy: string;
  grantedAt: string;
  reason: string;
  status: 'ACTIVE' | 'USED' | 'REVOKED';
  revokeReason?: string;
}

export interface BroadcastRecord {
  id: string;
  title: string;
  content: string;
  targetAudience: 'ALL' | 'KTV_ONLY' | 'SELLER_ONLY';
  sentAt: string;
  author: string;
  priority: 'NORMAL' | 'HIGH' | 'URGENT';
}

// Initial Mock Datasets
const INITIAL_ESCROWS: EscrowRecord[] = [
  {
    id: 'ESC-001',
    orderId: 'ORD-9912',
    orderCode: '#OG-ORD-9912',
    buyerName: 'Trần Minh Quang',
    sellerName: 'Lê Hoàng Nam',
    productTitle: 'Máy ảnh Fujifilm X-T30 Body (95%)',
    amount: 14200000,
    status: 'HELD',
    createdAt: '2026-10-02 10:15',
    updatedAt: '2026-10-02 10:15',
    notes: 'Đang vận chuyển; hạn kiểm tra 3 ngày bắt đầu khi giao hàng thành công',
  },
  {
    id: 'ESC-002',
    orderId: 'ORD-9910',
    orderCode: '#OG-ORD-9910',
    buyerName: 'Phạm Thu Hằng',
    sellerName: 'Vũ Đức Trí',
    productTitle: 'Tai nghe Sony WH-1000XM4 đen',
    amount: 3200000,
    status: 'FROZEN',
    createdAt: '2026-10-01 14:20',
    updatedAt: '2026-10-03 09:30',
    notes: 'Đang tạm dừng theo đơn khiếu nại nứt khớp gập, chờ KTV phân xử',
  },
  {
    id: 'ESC-003',
    orderId: 'ORD-9884',
    orderCode: '#OG-ORD-9884',
    buyerName: 'Ngô Thanh Tùng',
    sellerName: 'Nguyễn Văn Minh',
    productTitle: 'Đồng hồ cơ cổ Seiko 5 Actus',
    amount: 1850000,
    status: 'RELEASED',
    createdAt: '2026-09-28 08:30',
    updatedAt: '2026-10-01 16:45',
    notes: 'Người mua đã xác nhận đồng kiểm thành công, tiền đã giải ngân về ví người bán',
  },
  {
    id: 'ESC-004',
    orderId: 'ORD-9870',
    orderCode: '#OG-ORD-9870',
    buyerName: 'Bùi Lan Anh',
    sellerName: 'Đặng Tuấn Anh',
    productTitle: 'Bàn phím cơ Filco Majestouch 2',
    amount: 1650000,
    status: 'REFUNDED',
    createdAt: '2026-09-25 11:00',
    updatedAt: '2026-09-27 15:20',
    notes: 'Người bán đồng ý hủy đơn do hàng bị cấn móp trong quá trình ship',
  },
  {
    id: 'ESC-005',
    orderId: 'ORD-9925',
    orderCode: '#OG-ORD-9925',
    buyerName: 'Đỗ Hải Đăng',
    sellerName: 'Trịnh Quốc Bảo',
    productTitle: 'Máy chơi game Nintendo Switch OLED',
    amount: 6100000,
    status: 'HELD',
    createdAt: '2026-10-03 16:00',
    updatedAt: '2026-10-03 16:00',
    notes: 'Đang giữ tiền bảo vệ Escrow',
  },
];

const INITIAL_NORMAL_USERS: NormalUserRecord[] = [
  {
    id: 101,
    fullName: 'Nguyễn Văn Minh',
    email: 'minh.nguyen@example.com',
    phoneNumber: '0912345678',
    roles: ['BUYER', 'SELLER'],
    status: 'ACTIVE',
    joinedAt: '2026-01-15',
    completedOrdersCount: 24,
    rating: 4.9,
  },
  {
    id: 102,
    fullName: 'Lê Hoàng Nam',
    email: 'nam.le@example.com',
    phoneNumber: '0987654321',
    roles: ['BUYER', 'SELLER'],
    status: 'ACTIVE',
    joinedAt: '2026-02-20',
    completedOrdersCount: 18,
    rating: 4.8,
  },
  {
    id: 103,
    fullName: 'Trần Văn Hùng (Báo cáo lừa cọc)',
    email: 'hung.tran88@example.com',
    phoneNumber: '0903332211',
    roles: ['BUYER', 'SELLER'],
    status: 'LOCKED',
    lockReason: 'Nhiều người mua phản ánh yêu cầu chuyển khoản ngoài sàn và không gửi hàng',
    joinedAt: '2026-08-10',
    completedOrdersCount: 2,
    rating: 1.5,
  },
  {
    id: 104,
    fullName: 'Phạm Thu Hằng',
    email: 'hang.pham@example.com',
    phoneNumber: '0977889900',
    roles: ['BUYER'],
    status: 'ACTIVE',
    joinedAt: '2026-03-05',
    completedOrdersCount: 9,
    rating: 5.0,
  },
  {
    id: 105,
    fullName: 'Đặng Tuấn Anh',
    email: 'anh.dang@example.com',
    phoneNumber: '0934112233',
    roles: ['BUYER', 'SELLER'],
    status: 'ACTIVE',
    joinedAt: '2026-04-12',
    completedOrdersCount: 31,
    rating: 4.7,
  },
];

const INITIAL_DISPUTES: DisputeRecord[] = [
  {
    id: 'DSP-001',
    orderCode: '#OG-ORD-9910',
    buyerName: 'Phạm Thu Hằng',
    sellerName: 'Vũ Đức Trí',
    productTitle: 'Tai nghe Sony WH-1000XM4 đen',
    disputeAmount: 3200000,
    reason: 'Sản phẩm có khuyết điểm nứt vỡ không được khai báo',
    description: 'Khớp gập tai nghe bên trái bị nứt 1.5cm, có nguy cơ gãy khi đeo. Bài đăng người bán chỉ ghi xước dăm 92% mà không đề cập vết nứt này.',
    buyerEvidenceUrls: [
      'https://images.unsplash.com/photo-1546435770-a3e426bf472b?w=600',
    ],
    sellerResponse: 'Lúc tôi gửi đi tai nghe còn nguyên, có thể do đơn vị vận chuyển đè ép làm nứt.',
    status: 'OPEN',
    createdAt: '2026-10-03 09:15',
    updatedAt: '2026-10-03 11:00',
  },
  {
    id: 'DSP-002',
    orderCode: '#OG-ORD-9844',
    buyerName: 'Hoàng Kim Long',
    sellerName: 'Trịnh Bảo Quân',
    productTitle: 'Máy ảnh Compact Ricoh GR III',
    disputeAmount: 18500000,
    reason: 'Gửi thiếu phụ kiện pin sạc zin theo cam kết',
    description: 'Người bán cam kết 2 pin zin + dock sạc kép, nhưng kiện hàng mở ra chỉ có 1 pin for và cáp sạc lô.',
    buyerEvidenceUrls: [
      'https://images.unsplash.com/photo-1516035069371-29a1b244cc32?w=600',
    ],
    sellerResponse: 'Tôi để quên dock sạc ở nhà, đã nhắn tin xin gửi bổ sung cho khách qua bưu điện.',
    status: 'INVESTIGATING',
    createdAt: '2026-10-01 14:00',
    updatedAt: '2026-10-02 17:30',
  },
];

const INITIAL_KYC: KycSubmissionRecord[] = [
  {
    id: 'KYC-001',
    userId: 101,
    fullName: 'Nguyễn Văn Minh',
    cccdNumber: '001095012345',
    dateOfBirth: '1995-08-14',
    address: 'Quận Ba Đình, Hà Nội',
    idCardFrontUrl: 'https://images.unsplash.com/photo-1589829545856-d10d557cf95f?w=600',
    idCardBackUrl: 'https://images.unsplash.com/photo-1589829545856-d10d557cf95f?w=600',
    portraitUrl: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=600',
    submittedAt: '2026-10-04 09:30',
    status: 'PENDING',
  },
  {
    id: 'KYC-002',
    userId: 106,
    fullName: 'Hoàng Thị Thảo',
    cccdNumber: '079198004567',
    dateOfBirth: '1998-11-22',
    address: 'Quận 1, TP. Hồ Chí Minh',
    idCardFrontUrl: 'https://images.unsplash.com/photo-1589829545856-d10d557cf95f?w=600',
    idCardBackUrl: 'https://images.unsplash.com/photo-1589829545856-d10d557cf95f?w=600',
    portraitUrl: 'https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=600',
    submittedAt: '2026-10-03 15:40',
    status: 'PENDING',
  },
  {
    id: 'KYC-003',
    userId: 107,
    fullName: 'Trần Đình Trọng',
    cccdNumber: '038093009988',
    dateOfBirth: '1993-04-10',
    address: 'Hải Châu, Đà Nẵng',
    idCardFrontUrl: 'https://images.unsplash.com/photo-1589829545856-d10d557cf95f?w=600',
    idCardBackUrl: 'https://images.unsplash.com/photo-1589829545856-d10d557cf95f?w=600',
    portraitUrl: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=600',
    submittedAt: '2026-10-02 11:20',
    status: 'APPROVED',
    reviewedBy: 'KTV Trần Anh Tuấn',
  },
];

const INITIAL_COMPLAINTS: ComplaintRecord[] = [
  {
    id: 'CMP-101',
    title: 'Lỗi trừ tiền hai lần qua cổng VNPay nhưng đơn hàng vẫn ở trạng thái chờ',
    complainantName: 'Đặng Tuấn Anh',
    complainantEmail: 'anh.dang@example.com',
    complainantRole: 'BUYER',
    type: 'SYSTEM_ERROR',
    urgency: 'CRITICAL',
    status: 'PENDING',
    description: 'Lúc 14:10 thanh toán đơn hàng #OG-ORD-9930, tài khoản ngân hàng báo trừ tiền 2 lần (2 x 4.500.000₫) nhưng giao diện hệ thống vẫn hiện Chờ thanh toán. KTV kiểm tra log giao dịch giúp.',
    createdAt: '2026-10-04 14:15',
  },
  {
    id: 'CMP-102',
    title: 'Người bán không chịu gửi hàng sau 3 ngày và chặn tin nhắn',
    complainantName: 'Ngô Thanh Tùng',
    complainantEmail: 'tung.ngo@example.com',
    complainantRole: 'BUYER',
    type: 'SELLER_BEHAVIOR',
    urgency: 'MEDIUM',
    status: 'PENDING',
    description: 'Đơn hàng #OG-ORD-9915 đã thanh toán Escrow từ ngày 01/10 nhưng người bán liên tục hẹn rồi hủy tin nhắn, không xuất mã vận đơn.',
    createdAt: '2026-10-04 10:00',
  },
  {
    id: 'CMP-103',
    title: 'Nút duyệt eKYC bị lỗi trắng trang khi upload file PDF dung lượng lớn',
    complainantName: 'Lê Hoàng Nam',
    complainantEmail: 'nam.le@example.com',
    complainantRole: 'SELLER',
    type: 'SYSTEM_ERROR',
    urgency: 'HIGH',
    status: 'PENDING',
    description: 'Khi gửi file hợp đồng định danh thương mại định dạng PDF 12MB thì server phản hồi mã 500 internal server error.',
    createdAt: '2026-10-03 16:30',
  },
];

const INITIAL_EMERGENCY_ALERTS: EmergencyAlertRecord[] = [
  {
    id: 'ALT-001',
    complaintId: 'CMP-099',
    ktvName: 'KTV Hoàng Hải',
    title: 'Tắc nghẽn Webhook thanh toán Escrow từ ngân hàng VietQR',
    details: 'Phát hiện 12 giao dịch nạp tiền ký quỹ của người mua từ 13:00 - 13:45 không nhận được tín hiệu callback webhook, dẫn đến trạng thái HELD bị treo tạm thời.',
    severity: 'CRITICAL',
    createdAt: '2026-10-04 13:50',
    status: 'UNREAD',
  },
];

const INITIAL_KTV_ACCOUNTS: KtvAccountRecord[] = [
  {
    id: 1,
    fullName: 'Hoàng Hải (Trưởng ca)',
    email: 'ktv.hai@ogshop.vn',
    phoneNumber: '0911002233',
    status: 'ACTIVE',
    assignedZone: 'Miền Bắc & Kiểm duyệt điện tử',
    casesResolvedCount: 342,
    createdAt: '2026-01-10',
    lastActive: '5 phút trước',
  },
  {
    id: 2,
    fullName: 'Trần Anh Tuấn',
    email: 'ktv.tuan@ogshop.vn',
    phoneNumber: '0922334455',
    status: 'ACTIVE',
    assignedZone: 'Miền Nam & Trọng tài tranh chấp',
    casesResolvedCount: 289,
    createdAt: '2026-02-15',
    lastActive: '12 phút trước',
  },
  {
    id: 3,
    fullName: 'Nguyễn Thị Phương Thảo',
    email: 'ktv.thao@ogshop.vn',
    phoneNumber: '0933445566',
    status: 'ACTIVE',
    assignedZone: 'Toàn quốc & Kiểm duyệt eKYC',
    casesResolvedCount: 415,
    createdAt: '2026-03-01',
    lastActive: 'Hôm nay, 08:30',
  },
  {
    id: 4,
    fullName: 'Vũ Đức Mạnh (Tạm ngừng)',
    email: 'ktv.manh@ogshop.vn',
    phoneNumber: '0944556677',
    status: 'INACTIVE',
    assignedZone: 'Nghỉ phép dài hạn',
    casesResolvedCount: 154,
    createdAt: '2026-04-10',
    lastActive: '2 tuần trước',
  },
];

const INITIAL_AUDIT_LOGS: AuditLogRecord[] = [
  {
    id: 'AUD-001',
    actorName: 'Admin Nguyễn Văn Quản Trị',
    actorRole: 'ADMIN',
    action: 'Cập nhật phí bảo vệ Escrow sàn',
    target: 'Cấu hình biểu phí: 2.2% -> 2.5%',
    ipAddress: '113.190.234.12',
    timestamp: '2026-10-04 11:20:00',
    status: 'SUCCESS',
    details: 'Điều chỉnh tỷ lệ phí bảo vệ người mua theo phê duyệt Q3',
  },
  {
    id: 'AUD-002',
    actorName: 'KTV Hoàng Hải',
    actorRole: 'KTV',
    action: 'Duyệt tin đăng đồ cũ',
    target: 'Sản phẩm: Máy ảnh Sony A7 Mark III (#PRD-8820)',
    ipAddress: '14.161.45.89',
    timestamp: '2026-10-04 10:45:12',
    status: 'SUCCESS',
    details: 'Đã kiểm tra video cận cảnh cảm biến và ống kính không trầy xước',
  },
  {
    id: 'AUD-003',
    actorName: 'KTV Trần Anh Tuấn',
    actorRole: 'KTV',
    action: 'Khóa tài khoản vi phạm',
    target: 'User: Trần Văn Hùng (ID: 103)',
    ipAddress: '14.161.45.92',
    timestamp: '2026-10-04 09:30:45',
    status: 'SUCCESS',
    details: 'Khóa vĩnh viễn do hành vi lừa chuyển cọc qua Zalo',
  },
  {
    id: 'AUD-004',
    actorName: 'Admin Nguyễn Văn Quản Trị',
    actorRole: 'ADMIN',
    action: 'Tạo mã Voucher toàn sàn',
    target: 'Mã: OGWELCOME100K',
    ipAddress: '113.190.234.12',
    timestamp: '2026-10-03 16:15:30',
    status: 'SUCCESS',
    details: 'Phát hành 500 voucher giảm 100.000₫ cho đơn từ 1.000.000₫',
  },
  {
    id: 'AUD-005',
    actorName: 'Hệ thống tự động',
    actorRole: 'SYSTEM',
    action: 'Giải ngân Escrow định kỳ',
    target: 'Đơn hàng #OG-ORD-9884',
    ipAddress: '127.0.0.1',
    timestamp: '2026-10-03 14:00:00',
    status: 'SUCCESS',
    details: 'Sau 3 ngày từ giao hàng thành công, chỉ giải ngân khi không có khiếu nại hoặc yêu cầu trả hàng đang mở',
  },
];

const INITIAL_SYSTEM_FEES: SystemFeeSettings = {
  escrowFeePercent: 2.5,
  minTransactionFee: 10000,
  withdrawalFeeVnd: 5000,
  buyerProtectionFixedFee: 15000,
  updatedAt: '2026-10-04 11:20',
  updatedBy: 'Admin Cấp Cao',
};

const INITIAL_VOUCHERS: VoucherRecord[] = [
  {
    id: 'VOU-01',
    code: 'OGWELCOME100K',
    name: 'Ưu đãi chào đón thành viên mới',
    discountType: 'AMOUNT',
    discountValue: 100000,
    minOrderValue: 1000000,
    totalQuantity: 500,
    usedQuantity: 142,
    startDate: '2026-10-01',
    endDate: '2026-12-31',
    status: 'ACTIVE',
    createdBy: 'Admin Cấp Cao',
  },
  {
    id: 'VOU-02',
    code: 'OGPROTECT50K',
    name: 'Đền bù sự cố khiếu nại giao dịch',
    discountType: 'AMOUNT',
    discountValue: 50000,
    minOrderValue: 300000,
    totalQuantity: 200,
    usedQuantity: 38,
    startDate: '2026-10-01',
    endDate: '2026-11-30',
    status: 'ACTIVE',
    createdBy: 'Admin Cấp Cao',
  },
  {
    id: 'VOU-03',
    code: 'OGFREESHIP30K',
    name: 'Hỗ trợ cước vận chuyển an toàn',
    discountType: 'AMOUNT',
    discountValue: 30000,
    minOrderValue: 500000,
    totalQuantity: 1000,
    usedQuantity: 620,
    startDate: '2026-09-15',
    endDate: '2026-10-31',
    status: 'ACTIVE',
    createdBy: 'Admin Cấp Cao',
  },
  {
    id: 'VOU-04',
    code: 'OGVIPPERCENT10',
    name: 'Tri ân người mua thân thiết 10%',
    discountType: 'PERCENT',
    discountValue: 10,
    minOrderValue: 2000000,
    totalQuantity: 100,
    usedQuantity: 88,
    startDate: '2026-09-01',
    endDate: '2026-10-01',
    status: 'EXPIRED',
    createdBy: 'Admin Cấp Cao',
  },
];

const INITIAL_GRANTED_VOUCHERS: GrantedVoucherRecord[] = [
  {
    id: 'GV-01',
    voucherId: 'VOU-02',
    voucherCode: 'OGPROTECT50K',
    voucherName: 'Đền bù sự cố khiếu nại giao dịch',
    discountValueFormatted: '50.000₫',
    userId: 104,
    userEmail: 'hang.pham@example.com',
    grantedBy: 'KTV Hoàng Hải',
    grantedAt: '2026-10-03 14:30',
    reason: 'Hỗ trợ người mua trong vụ việc tranh chấp tai nghe Sony nứt khớp gập',
    status: 'ACTIVE',
  },
  {
    id: 'GV-02',
    voucherId: 'VOU-01',
    voucherCode: 'OGWELCOME100K',
    voucherName: 'Ưu đãi chào đón thành viên mới',
    discountValueFormatted: '100.000₫',
    userId: 101,
    userEmail: 'minh.nguyen@example.com',
    grantedBy: 'KTV Trần Anh Tuấn',
    grantedAt: '2026-10-02 09:15',
    reason: 'Thưởng người bán tích cực hoàn tất kiểm duyệt 5 tin đăng trong ngày',
    status: 'USED',
  },
];

const INITIAL_BROADCASTS: BroadcastRecord[] = [
  {
    id: 'BRD-001',
    title: 'Thông báo nâng cấp hạ tầng thanh toán Escrow định kỳ',
    content: 'Hệ thống sẽ bảo trì kết nối cổng thanh toán từ 01:00 đến 03:00 sáng ngày 08/10. Các giao dịch trong thời gian này sẽ được hoãn xử lý an toàn.',
    targetAudience: 'ALL',
    sentAt: '2026-10-04 09:00',
    author: 'Quản trị viên Cấp cao',
    priority: 'HIGH',
  },
  {
    id: 'BRD-002',
    title: 'Chỉ thị nghiệp vụ: Siết chặt kiểm định video quay cận cảnh đồ điện tử',
    content: 'Yêu cầu toàn bộ KTV trực ca kiểm tra kỹ góc quay tem niêm phong và khay SIM / số IMEI đối với điện thoại iPhone và máy ảnh.',
    targetAudience: 'KTV_ONLY',
    sentAt: '2026-10-03 14:00',
    author: 'Giám đốc Vận hành',
    priority: 'URGENT',
  },
];

// In-Memory & LocalStorage backed Singleton Store
class ManagementStore {
  private nextId(prefix: string): string {
    return `${prefix}-${crypto.randomUUID()}`;
  }
  private escrows: EscrowRecord[];
  private users: NormalUserRecord[];
  private disputes: DisputeRecord[];
  private kycList: KycSubmissionRecord[];
  private complaints: ComplaintRecord[];
  private emergencyAlerts: EmergencyAlertRecord[];
  private ktvAccounts: KtvAccountRecord[];
  private auditLogs: AuditLogRecord[];
  private systemFees: SystemFeeSettings;
  private vouchers: VoucherRecord[];
  private grantedVouchers: GrantedVoucherRecord[];
  private broadcasts: BroadcastRecord[];

  private listeners: Set<() => void> = new Set();

  constructor() {
    this.escrows = this.loadFromStorage('og_escrows', INITIAL_ESCROWS);
    this.users = this.loadFromStorage('og_users', INITIAL_NORMAL_USERS);
    this.disputes = this.loadFromStorage('og_disputes', INITIAL_DISPUTES);
    this.kycList = this.loadFromStorage('og_kyc', INITIAL_KYC);
    this.complaints = this.loadFromStorage('og_complaints', INITIAL_COMPLAINTS);
    this.emergencyAlerts = this.loadFromStorage('og_alerts', INITIAL_EMERGENCY_ALERTS);
    this.ktvAccounts = this.loadFromStorage('og_ktv_accounts', INITIAL_KTV_ACCOUNTS);
    this.auditLogs = this.loadFromStorage('og_audit_logs', INITIAL_AUDIT_LOGS);
    this.systemFees = this.loadFromStorage('og_system_fees', INITIAL_SYSTEM_FEES);
    this.vouchers = this.loadFromStorage('og_vouchers', INITIAL_VOUCHERS);
    this.grantedVouchers = this.loadFromStorage('og_granted_vouchers', INITIAL_GRANTED_VOUCHERS);
    this.broadcasts = this.loadFromStorage('og_broadcasts', INITIAL_BROADCASTS);
  }

  private loadFromStorage<T>(key: string, fallback: T): T {
    try {
      const item = localStorage.getItem(key);
      return item ? JSON.parse(item) : fallback;
    } catch {
      return fallback;
    }
  }

  private saveToStorage(key: string, data: unknown) {
    try {
      localStorage.setItem(key, JSON.stringify(data));
    } catch (e) {
      console.warn('Storage save failed:', e);
    }
  }

  public subscribe(listener: () => void) {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  }

  private notify() {
    this.listeners.forEach((listener) => listener());
  }

  private addAuditLog(actorName: string, actorRole: 'ADMIN' | 'KTV' | 'SYSTEM', action: string, target: string, details?: string) {
    const newLog: AuditLogRecord = {
      id: this.nextId('AUD'),
      actorName,
      actorRole,
      action,
      target,
      ipAddress: '113.190.234.' + Math.floor(Math.random() * 200 + 10),
      timestamp: new Date().toISOString().replace('T', ' ').slice(0, 19),
      status: 'SUCCESS',
      details,
    };
    this.auditLogs = [newLog, ...this.auditLogs];
    this.saveToStorage('og_audit_logs', this.auditLogs);
  }

  // --- KTV GETTERS ---
  public getEscrows(): EscrowRecord[] {
    return [...this.escrows];
  }

  public getNormalUsers(): NormalUserRecord[] {
    return [...this.users];
  }

  public getDisputes(): DisputeRecord[] {
    return [...this.disputes];
  }

  public getKycList(): KycSubmissionRecord[] {
    return [...this.kycList];
  }

  public getComplaints(): ComplaintRecord[] {
    return [...this.complaints];
  }

  public getGrantedVouchers(): GrantedVoucherRecord[] {
    return [...this.grantedVouchers];
  }

  // --- ADMIN GETTERS ---
  public getEmergencyAlerts(): EmergencyAlertRecord[] {
    return [...this.emergencyAlerts];
  }

  public getUnreadAlertsCount(): number {
    return this.emergencyAlerts.filter((a) => a.status === 'UNREAD').length;
  }

  public getKtvAccounts(): KtvAccountRecord[] {
    return [...this.ktvAccounts];
  }

  public getAuditLogs(): AuditLogRecord[] {
    return [...this.auditLogs];
  }

  public getSystemFees(): SystemFeeSettings {
    return { ...this.systemFees };
  }

  public getVouchers(): VoucherRecord[] {
    return [...this.vouchers];
  }

  public getBroadcasts(): BroadcastRecord[] {
    return [...this.broadcasts];
  }

  // --- KTV ACTIONS ---
  public lockUser(userId: number, reason: string, ktvName = 'KTV Trực ca') {
    this.users = this.users.map((u) => {
      if (u.id === userId) {
        return { ...u, status: 'LOCKED', lockReason: reason };
      }
      return u;
    });
    this.saveToStorage('og_users', this.users);
    this.addAuditLog(ktvName, 'KTV', 'Khóa tài khoản người dùng', `User ID: ${userId}`, `Lý do: ${reason}`);
    this.notify();
  }

  public unlockUser(userId: number, ktvName = 'KTV Trực ca') {
    this.users = this.users.map((u) => {
      if (u.id === userId) {
        return { ...u, status: 'ACTIVE', lockReason: undefined };
      }
      return u;
    });
    this.saveToStorage('og_users', this.users);
    this.addAuditLog(ktvName, 'KTV', 'Kích hoạt lại tài khoản người dùng', `User ID: ${userId}`);
    this.notify();
  }

  public resolveDispute(
    disputeId: string,
    verdict: 'FULL_REFUND' | 'RELEASE_SELLER',
    verdictNotes: string,
    ktvName = 'KTV Trọng tài'
  ) {
    if (verdict !== 'FULL_REFUND' && verdict !== 'RELEASE_SELLER') {
      throw new Error('Hoàn một phần chưa được hỗ trợ. Vui lòng chọn quyết định trả hàng hoặc giải ngân.');
    }
    const dispute = this.disputes.find(d => d.id === disputeId);
    if (!dispute || !['OPEN', 'INVESTIGATING'].includes(dispute.status)) {
      throw new Error('Vụ việc không tồn tại hoặc đã có quyết định.');
    }
    this.disputes = this.disputes.map((d) => {
      if (d.id === disputeId) {
        const nextStatus = verdict === 'FULL_REFUND' ? 'RETURN_APPROVED' : 'RESOLVED_RELEASE';
        return {
          ...d,
          status: nextStatus as DisputeRecord['status'],
          verdictAction: verdict,
          verdictNotes,
          updatedAt: new Date().toISOString().replace('T', ' ').slice(0, 16),
        };
      }
      return d;
    });
    this.saveToStorage('og_disputes', this.disputes);
    this.addAuditLog(ktvName, 'KTV', `Phán quyết tranh chấp (${verdict})`, `Mã vụ việc: ${disputeId}`, verdictNotes);
    this.notify();
  }

  public approveKyc(kycId: string, ktvName = 'KTV Kiểm duyệt eKYC') {
    this.kycList = this.kycList.map((k) => {
      if (k.id === kycId) {
        return { ...k, status: 'APPROVED', reviewedBy: ktvName };
      }
      return k;
    });
    this.saveToStorage('og_kyc', this.kycList);
    this.addAuditLog(ktvName, 'KTV', 'Phê duyệt eKYC người bán', `Hồ sơ ID: ${kycId}`);
    this.notify();
  }

  public rejectKyc(kycId: string, reason: string, ktvName = 'KTV Kiểm duyệt eKYC') {
    this.kycList = this.kycList.map((k) => {
      if (k.id === kycId) {
        return { ...k, status: 'REJECTED', reviewedBy: ktvName, rejectReason: reason };
      }
      return k;
    });
    this.saveToStorage('og_kyc', this.kycList);
    this.addAuditLog(ktvName, 'KTV', 'Từ chối eKYC người bán', `Hồ sơ ID: ${kycId}`, `Lý do: ${reason}`);
    this.notify();
  }

  public resolveComplaint(complaintId: string, resolution: string, ktvName = 'KTV Xử lý') {
    this.complaints = this.complaints.map((c) => {
      if (c.id === complaintId) {
        return { ...c, status: 'RESOLVED', resolution };
      }
      return c;
    });
    this.saveToStorage('og_complaints', this.complaints);
    this.addAuditLog(ktvName, 'KTV', 'Giải quyết khiếu nại', `Mã khiếu nại: ${complaintId}`, resolution);
    this.notify();
  }

  // KEY REQUIREMENT: KTV pushes complaint up to Admin as Emergency Alert!
  public escalateComplaintToAdmin(
    complaintId: string,
    escalationNote: string,
    severity: 'HIGH' | 'CRITICAL' = 'CRITICAL',
    ktvName = 'KTV Trực ban'
  ) {
    let complaintTitle = 'Lỗi hệ thống cần giải quyết';
    this.complaints = this.complaints.map((c) => {
      if (c.id === complaintId) {
        complaintTitle = c.title;
        return {
          ...c,
          status: 'ESCALATED_TO_ADMIN',
          escalatedAt: new Date().toISOString().replace('T', ' ').slice(0, 16),
          escalationNote,
        };
      }
      return c;
    });
    this.saveToStorage('og_complaints', this.complaints);

    // Push into Admin's Emergency Alerts
    const newAlert: EmergencyAlertRecord = {
      id: this.nextId('ALT'),
      complaintId,
      ktvName,
      title: `[KTV ĐẨY LÊN] ${complaintTitle}`,
      details: escalationNote,
      severity,
      createdAt: new Date().toISOString().replace('T', ' ').slice(0, 16),
      status: 'UNREAD',
    };
    this.emergencyAlerts = [newAlert, ...this.emergencyAlerts];
    this.saveToStorage('og_alerts', this.emergencyAlerts);

    this.addAuditLog(
      ktvName,
      'KTV',
      'Đẩy sự vụ lên Admin (Thông báo khẩn)',
      `Khiếu nại ID: ${complaintId}`,
      `Mức độ: ${severity}. Ghi chú: ${escalationNote}`
    );
    this.notify();
  }

  public grantVoucher(
    userId: number,
    voucherId: string,
    reason: string,
    ktvName = 'KTV Chăm sóc'
  ) {
    const user = this.users.find((u) => u.id === userId);
    const voucher = this.vouchers.find((v) => v.id === voucherId);
    if (!user || !voucher) throw new Error('Người dùng hoặc voucher không tồn tại.');
    if (voucher.status !== 'ACTIVE') throw new Error('Chỉ được cấp voucher đang hoạt động.');

    const formattedValue =
      voucher.discountType === 'AMOUNT'
        ? `${voucher.discountValue.toLocaleString('vi-VN')}₫`
        : `${voucher.discountValue}%`;

    const newGrant: GrantedVoucherRecord = {
      id: this.nextId('GV'),
      voucherId: voucher.id,
      voucherCode: voucher.code,
      voucherName: voucher.name,
      discountValueFormatted: formattedValue,
      userId: user.id,
      userEmail: user.email,
      grantedBy: ktvName,
      grantedAt: new Date().toISOString().replace('T', ' ').slice(0, 16),
      reason,
      status: 'ACTIVE',
    };

    this.grantedVouchers = [newGrant, ...this.grantedVouchers];
    this.saveToStorage('og_granted_vouchers', this.grantedVouchers);

    this.addAuditLog(
      ktvName,
      'KTV',
      'Tặng Voucher cho người dùng',
      `User: ${user.fullName} (${user.email})`,
      `Mã: ${voucher.code} - Lý do: ${reason}`
    );
    this.notify();
  }

  public revokeVoucher(grantId: string, reason: string, ktvName = 'KTV Chăm sóc') {
    const grant = this.grantedVouchers.find(g => g.id === grantId);
    if (!grant) throw new Error('Không tìm thấy lượt cấp voucher.');
    if (grant.status !== 'ACTIVE') throw new Error('Chỉ được thu hồi voucher chưa sử dụng và đang hoạt động.');
    this.grantedVouchers = this.grantedVouchers.map((g) => {
      if (g.id === grantId) {
        return { ...g, status: 'REVOKED', revokeReason: reason };
      }
      return g;
    });
    this.saveToStorage('og_granted_vouchers', this.grantedVouchers);
    this.addAuditLog(ktvName, 'KTV', 'Thu hồi Voucher người dùng', `Mã lượt tặng: ${grantId}`, `Lý do: ${reason}`);
    this.notify();
  }

  // --- ADMIN ACTIONS ---
  public acknowledgeAlert(alertId: string, adminNote?: string, adminName = 'Quản trị viên') {
    this.emergencyAlerts = this.emergencyAlerts.map((a) => {
      if (a.id === alertId) {
        return {
          ...a,
          status: 'INVESTIGATING',
          adminNote: adminNote || a.adminNote,
        };
      }
      return a;
    });
    this.saveToStorage('og_alerts', this.emergencyAlerts);
    this.addAuditLog(adminName, 'ADMIN', 'Tiếp nhận thông báo khẩn', `Mã cảnh báo: ${alertId}`, adminNote);
    this.notify();
  }

  public resolveAlert(alertId: string, adminNote: string, adminName = 'Quản trị viên') {
    this.emergencyAlerts = this.emergencyAlerts.map((a) => {
      if (a.id === alertId) {
        return {
          ...a,
          status: 'RESOLVED',
          adminNote,
        };
      }
      return a;
    });
    this.saveToStorage('og_alerts', this.emergencyAlerts);
    this.addAuditLog(adminName, 'ADMIN', 'Đã xử lý thông báo khẩn', `Mã cảnh báo: ${alertId}`, adminNote);
    this.notify();
  }

  public createKtvAccount(
    data: { fullName: string; email: string; phoneNumber: string; assignedZone: string },
    adminName = 'Quản trị viên'
  ) {
    const newKtv: KtvAccountRecord = {
      id: Date.now(),
      fullName: data.fullName,
      email: data.email,
      phoneNumber: data.phoneNumber,
      assignedZone: data.assignedZone,
      status: 'ACTIVE',
      casesResolvedCount: 0,
      createdAt: new Date().toISOString().replace('T', ' ').slice(0, 10),
      lastActive: 'Vừa tạo',
    };
    this.ktvAccounts = [newKtv, ...this.ktvAccounts];
    this.saveToStorage('og_ktv_accounts', this.ktvAccounts);
    this.addAuditLog(adminName, 'ADMIN', 'Tạo tài khoản KTV mới', `KTV: ${data.fullName} (${data.email})`);
    this.notify();
  }

  public updateKtvAccount(
    id: number,
    data: Partial<KtvAccountRecord>,
    adminName = 'Quản trị viên'
  ) {
    this.ktvAccounts = this.ktvAccounts.map((k) => {
      if (k.id === id) {
        return { ...k, ...data };
      }
      return k;
    });
    this.saveToStorage('og_ktv_accounts', this.ktvAccounts);
    this.addAuditLog(adminName, 'ADMIN', 'Cập nhật tài khoản KTV', `KTV ID: ${id}`);
    this.notify();
  }

  public toggleKtvStatus(id: number, adminName = 'Quản trị viên') {
    let nextStatus: 'ACTIVE' | 'INACTIVE' = 'ACTIVE';
    this.ktvAccounts = this.ktvAccounts.map((k) => {
      if (k.id === id) {
        nextStatus = k.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
        return { ...k, status: nextStatus };
      }
      return k;
    });
    this.saveToStorage('og_ktv_accounts', this.ktvAccounts);
    this.addAuditLog(adminName, 'ADMIN', `Thay đổi trạng thái KTV (${nextStatus})`, `KTV ID: ${id}`);
    this.notify();
  }

  public updateSystemFees(fees: Partial<SystemFeeSettings>, adminName = 'Quản trị viên') {
    this.systemFees = {
      ...this.systemFees,
      ...fees,
      updatedAt: new Date().toISOString().replace('T', ' ').slice(0, 16),
      updatedBy: adminName,
    };
    this.saveToStorage('og_system_fees', this.systemFees);
    this.addAuditLog(
      adminName,
      'ADMIN',
      'Cập nhật biểu phí hệ thống',
      `Phí Escrow: ${this.systemFees.escrowFeePercent}%, Phí tối thiểu: ${this.systemFees.minTransactionFee.toLocaleString()}₫`
    );
    this.notify();
  }

  public createVoucher(
    data: Omit<VoucherRecord, 'id' | 'usedQuantity' | 'createdBy'>,
    adminName = 'Quản trị viên'
  ) {
    const newVoucher: VoucherRecord = {
      ...data,
      id: this.nextId('VOU'),
      usedQuantity: 0,
      createdBy: adminName,
    };
    this.vouchers = [newVoucher, ...this.vouchers];
    this.saveToStorage('og_vouchers', this.vouchers);
    this.addAuditLog(adminName, 'ADMIN', 'Tạo Voucher mới', `Mã: ${newVoucher.code} - ${newVoucher.name}`);
    this.notify();
  }

  public toggleVoucherStatus(id: string, adminName = 'Quản trị viên') {
    this.vouchers = this.vouchers.map((v) => {
      if (v.id === id) {
        const nextStatus = v.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
        return { ...v, status: nextStatus };
      }
      return v;
    });
    this.saveToStorage('og_vouchers', this.vouchers);
    this.addAuditLog(adminName, 'ADMIN', 'Thay đổi trạng thái Voucher', `Voucher ID: ${id}`);
    this.notify();
  }

  public sendBroadcast(
    data: Omit<BroadcastRecord, 'id' | 'sentAt' | 'author'>,
    adminName = 'Quản trị viên'
  ) {
    const newBroadcast: BroadcastRecord = {
      ...data,
      id: this.nextId('BRD'),
      sentAt: new Date().toISOString().replace('T', ' ').slice(0, 16),
      author: adminName,
    };
    this.broadcasts = [newBroadcast, ...this.broadcasts];
    this.saveToStorage('og_broadcasts', this.broadcasts);
    this.addAuditLog(adminName, 'ADMIN', 'Phát thông báo điều hướng', `Tiêu đề: ${newBroadcast.title} (Đối tượng: ${newBroadcast.targetAudience})`);
    this.notify();
  }

  // --- REPORT CSV EXPORTERS ---
  public exportUserDataCsv() {
    const header = 'Tháng,Người dùng mới,Người dùng hoạt động,Tổng người dùng tích lũy\n';
    const rows = [
      'Tháng 1/2026,120,450,1200',
      'Tháng 2/2026,150,520,1350',
      'Tháng 3/2026,190,610,1540',
      'Tháng 4/2026,220,700,1760',
      'Tháng 5/2026,260,820,2020',
      'Tháng 6/2026,310,950,2330',
      'Tháng 7/2026,380,1100,2710',
      'Tháng 8/2026,450,1320,3160',
      'Tháng 9/2026,520,1580,3680',
      'Tháng 10/2026 (Hiện tại),210,1820,3890',
    ].join('\n');
    this.triggerDownloadCsv('bao-cao-so-luong-nguoi-dung.csv', header + rows);
  }

  public exportRevenueDataCsv() {
    const header = 'Tháng,Tổng GMV (₫),Doanh thu phí sàn Escrow (₫),Số đơn hoàn tất\n';
    const rows = [
      'Tháng 1/2026,450000000,11250000,140',
      'Tháng 2/2026,520000000,13000000,165',
      'Tháng 3/2026,680000000,17000000,210',
      'Tháng 4/2026,790000000,19750000,245',
      'Tháng 5/2026,920000000,23000000,290',
      'Tháng 6/2026,1150000000,28750000,350',
      'Tháng 7/2026,1340000000,33500000,410',
      'Tháng 8/2026,1620000000,40500000,490',
      'Tháng 9/2026,1890000000,47250000,560',
      'Tháng 10/2026,820000000,20500000,240',
    ].join('\n');
    this.triggerDownloadCsv('bao-cao-doanh-thu-phi-san.csv', header + rows);
  }

  public exportTopSearchCsv() {
    const header = 'Phân loại,Tên mặt hàng / Từ khóa,Lượt tìm kiếm trong tháng,Lượt mua hoàn tất,Tỷ lệ chuyển đổi\n';
    const rows = [
      'TOP NHIỀU,iPhone 13 / 14 cũ 99%,14850,312,2.1%',
      'TOP NHIỀU,Máy ảnh Fujifilm X-T series,11200,185,1.6%',
      'TOP NHIỀU,Đồng hồ cơ vintage Nhật Bản,8900,142,1.6%',
      'TOP NHIỀU,Tai nghe chống ồn Sony / Bose,7400,160,2.1%',
      'TOP NHIỀU,Đồ da thủ công cao cấp,6100,118,1.9%',
      'TOP ÍT,Máy tính bảng Windows đời cũ,120,4,3.3%',
      'TOP ÍT,Máy ghi âm cầm tay cassette,95,2,2.1%',
      'TOP ÍT,Màn hình CRT văn phòng cũ,60,1,1.6%',
      'TOP ÍT,Điện thoại bàn cố định kéo dây,45,0,0.0%',
      'TOP ÍT,Bao da điện thoại phím bấm cổ,32,1,3.1%',
    ].join('\n');
    this.triggerDownloadCsv('thong-ke-mat-hang-tim-mua-nhieu-it.csv', header + rows);
  }

  public exportReturnComplaintCsv() {
    const header = 'Danh mục,Số đơn bán,Số đơn yêu cầu trả hàng,Số vụ khiếu nại tranh chấp,Tỷ lệ hoàn hàng (%),Lý do chính\n';
    const rows = [
      'Điện thoại & Máy tính bảng,420,14,3,3.3%,Pin chai hơn mô tả',
      'Máy ảnh & Thiết bị quang học,290,11,4,3.7%,Nấm mốc kính ngắm / xước ống kính',
      'Âm thanh & Tai nghe,310,8,2,2.5%,Nứt khớp gập / pin sạc lỗi',
      'Đồng hồ & Phụ kiện cổ,180,3,1,1.6%,Sai số chạy chậm hàng ngày',
      'Thời trang & Túi vintage,250,5,1,2.0%,Vết ố vải lót bên trong',
      'Gia dụng & Đồ sưu tầm,150,2,0,1.3%,Móp hộp trong lúc vận chuyển',
    ].join('\n');
    this.triggerDownloadCsv('thong-ke-hang-bi-tra-va-khieu-nai.csv', header + rows);
  }

  private triggerDownloadCsv(filename: string, content: string) {
    const bom = '\uFEFF';
    const blob = new Blob([bom + content], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', filename);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  }
}

export const managementStore = new ManagementStore();
