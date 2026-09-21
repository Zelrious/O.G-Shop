export interface MockProduct {
  id: string;
  title: { vi: string; en: string };
  price: number;
  originalPrice: number;
  category: string;
  categoryName: { vi: string; en: string };
  condition: 'like_new' | 'good' | 'fair' | 'vintage';
  conditionPercent: number;
  conditionDescription: { vi: string; en: string };
  defects: { vi: string; en: string }[];
  images: string[];
  coInspectionVideoAvailable: boolean;
  coInspectionVideoUrl?: string;
  location: string;
  postedTimeAgo: { vi: string; en: string };
  seller: {
    id: string;
    name: string;
    avatar: string;
    rating: number;
    reviewCount: number;
    joinDate: string;
    isKycVerified: boolean;
    trustScore: number;
    responseRate: string;
    responseTime: string;
  };
  attributes: { [key: string]: { vi: string; en: string } };
  viewCount: number;
  favoriteCount: number;
}

export interface MockCategory {
  id: string;
  slug: string;
  name: { vi: string; en: string };
  icon: string;
  count: number;
}

export interface MockVoucher {
  id: string;
  code: string;
  title: { vi: string; en: string };
  discountAmount: number;
  minOrder: number;
  expiry: string;
  type: 'shipping' | 'order';
}

export interface MockOrder {
  id: string;
  orderNumber: string;
  productId: string;
  productTitle: { vi: string; en: string };
  productImage: string;
  sellerName: string;
  buyerName: string;
  amount: number;
  shippingFee: number;
  voucherDiscount: number;
  totalPaid: number;
  escrowStatus: 'HOLDING' | 'RELEASED' | 'DISPUTED' | 'REFUNDED';
  shippingStatus: 'PENDING_PICKUP' | 'IN_TRANSIT' | 'OUT_FOR_DELIVERY' | 'DELIVERED';
  createdAt: string;
  coInspectionRequired: boolean;
  coInspectionPassed?: boolean;
  trackingNumber: string;
  carrier: string;
  timeline: {
    time: string;
    title: { vi: string; en: string };
    description: { vi: string; en: string };
    isDone: boolean;
  }[];
}

export interface MockChatMessage {
  id: string;
  senderId: string;
  senderName: string;
  text: string;
  time: string;
  isSeller: boolean;
  type: 'text' | 'offer' | 'warning' | 'product_card';
  offerDetails?: {
    originalPrice: number;
    offeredPrice: number;
    status: 'pending' | 'accepted' | 'rejected' | 'countered';
  };
}

export interface MockDispute {
  id: string;
  orderNumber: string;
  productTitle: { vi: string; en: string };
  productImage: string;
  buyerName: string;
  sellerName: string;
  reason: { vi: string; en: string };
  buyerStatement: { vi: string; en: string };
  sellerStatement?: { vi: string; en: string };
  disputedAmount: number;
  createdAt: string;
  status: 'AWAITING_SELLER_RESPONSE' | 'ADMIN_REVIEWING' | 'RESOLVED_REFUND' | 'RESOLVED_RELEASE';
  evidencePhotos: string[];
  coInspectionVideoAvailable: boolean;
  timeline: {
    time: string;
    title: { vi: string; en: string };
    by: string;
  }[];
}

export const MOCK_CATEGORIES: MockCategory[] = [
  { id: 'cat-1', slug: 'electronics', name: { vi: 'Máy Ảnh & Ống Kính', en: 'Cameras & Lenses' }, icon: '📷', count: 142 },
  { id: 'cat-2', slug: 'audio', name: { vi: 'Âm Thanh & Tai Nghe', en: 'Audio & Headphones' }, icon: '🎧', count: 98 },
  { id: 'cat-3', slug: 'keyboards', name: { vi: 'Bàn Phím Cơ & Gear PC', en: 'Keyboards & PC Gear' }, icon: '⌨️', count: 125 },
  { id: 'cat-4', slug: 'vintage-watches', name: { vi: 'Đồng Hồ Cổ & Trang Sức', en: 'Vintage Watches' }, icon: '⌚', count: 64 },
  { id: 'cat-5', slug: 'vintage-fashion', name: { vi: 'Thời Trang Second-hand', en: 'Vintage Apparel' }, icon: '🧥', count: 210 },
  { id: 'cat-6', slug: 'gaming', name: { vi: 'Máy Chơi Game & Đĩa', en: 'Gaming & Retro Consoles' }, icon: '🎮', count: 83 },
  { id: 'cat-7', slug: 'smartphones', name: { vi: 'Điện Thoại & Tablet', en: 'Phones & Tablets' }, icon: '📱', count: 176 },
  { id: 'cat-8', slug: 'collectibles', name: { vi: 'Đồ Sưu Tầm & Nghệ Thuật', en: 'Collectibles & Art' }, icon: '🏺', count: 52 },
];

export const MOCK_PRODUCTS: MockProduct[] = [
  {
    id: 'prod-001',
    title: {
      vi: 'Máy ảnh Sony Alpha A7 Mark III (Shutter 12k shot, Đẹp 97%)',
      en: 'Sony Alpha A7 III Mirrorless Camera (12k Shutter Count, 97% Clean)'
    },
    price: 24500000,
    originalPrice: 38000000,
    category: 'electronics',
    categoryName: { vi: 'Máy Ảnh & Ống Kính', en: 'Cameras & Lenses' },
    condition: 'good',
    conditionPercent: 97,
    conditionDescription: {
      vi: 'Máy hoạt động hoàn hảo, sensor sạch không trầy xước hay nấm mốc. Ngoại hình xước nhẹ ở góc đáy do gắn tripod.',
      en: 'Perfect working condition, pristine sensor free from scratches or fungus. Light cosmetic wear on baseplate from tripod mount.'
    },
    defects: [
      { vi: 'Vết xước sơn nhẹ 2mm ở góc đáy máy', en: '2mm minor paint scuff at bottom edge' },
      { vi: 'Mất nắp che cổng flash sync (đã bù nắp cao su ngoài)', en: 'Missing hot-shoe cap (replaced with third-party rubber cap)' }
    ],
    images: [
      'https://images.unsplash.com/photo-1516035069371-29a1b244cc32?auto=format&fit=crop&w=800&q=80',
      'https://images.unsplash.com/photo-1502920917128-1aa500764cbd?auto=format&fit=crop&w=800&q=80',
      'https://images.unsplash.com/photo-1512790182412-b19e6d62bc39?auto=format&fit=crop&w=800&q=80'
    ],
    coInspectionVideoAvailable: true,
    coInspectionVideoUrl: 'https://sample-videos.com/video123/mp4/720/big_buck_bunny_720p_1mb.mp4',
    location: 'Quận 1, TP. Hồ Chí Minh',
    postedTimeAgo: { vi: '2 giờ trước', en: '2 hours ago' },
    seller: {
      id: 'seller-minh',
      name: 'Minh Camera Saigon',
      avatar: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=200&q=80',
      rating: 4.9,
      reviewCount: 84,
      joinDate: '10/2023',
      isKycVerified: true,
      trustScore: 98,
      responseRate: '99%',
      responseTime: 'Trong 5 phút'
    },
    attributes: {
      'Shutter Count': { vi: '12.450 shot (Tuổi thọ 200k shot)', en: '12,450 shots (Rated 200k)' },
      'Phụ kiện': { vi: '1 pin zin, sạc đôi Wasabi, dây đeo zin, thẻ nhớ Sandisk 64GB', en: '1 OEM battery, Wasabi dual charger, strap, 64GB SD' },
      'Bảo hành': { vi: 'Bao test 15 ngày có Escrow bảo vệ', en: '15-day return with Escrow protection' }
    },
    viewCount: 342,
    favoriteCount: 29
  },
  {
    id: 'prod-002',
    title: {
      vi: 'Bàn phím cơ Custom Keychron Q1 V2 Knob (Gateron Baby Kangaroo, Keycap PBT retro)',
      en: 'Custom Keychron Q1 V2 Knob Mech Keyboard (Baby Kangaroo Switches, PBT Retro Caps)'
    },
    price: 2850000,
    originalPrice: 4500000,
    category: 'keyboards',
    categoryName: { vi: 'Bàn Phím Cơ & Gear PC', en: 'Keyboards & PC Gear' },
    condition: 'like_new',
    conditionPercent: 99,
    conditionDescription: {
      vi: 'Lắp chơi được 2 tuần rồi cất hộp. Đã tape mod 2 lớp, lube stab krytox 205g0 mượt mà không tick.',
      en: 'Used for 2 weeks then kept in box. Tape modded 2 layers, lubed stabs with Krytox 205g0, zero rattle.'
    },
    defects: [
      { vi: 'Hộp carton bên ngoài hơi móp 1 góc khi vận chuyển', en: 'Outer packaging slightly creased from initial shipping' }
    ],
    images: [
      'https://images.unsplash.com/photo-1587829741301-dc798b83add3?auto=format&fit=crop&w=800&q=80',
      'https://images.unsplash.com/photo-1618384887929-16ec33fab9ef?auto=format&fit=crop&w=800&q=80'
    ],
    coInspectionVideoAvailable: true,
    location: 'Cầu Giấy, Hà Nội',
    postedTimeAgo: { vi: '4 giờ trước', en: '4 hours ago' },
    seller: {
      id: 'seller-tuan',
      name: 'Tuấn Mech & Audio',
      avatar: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=200&q=80',
      rating: 5.0,
      reviewCount: 32,
      joinDate: '01/2024',
      isKycVerified: true,
      trustScore: 96,
      responseRate: '95%',
      responseTime: 'Trong 15 phút'
    },
    attributes: {
      'Layout': { vi: '75% có núm xoay nhôm', en: '75% exploded with aluminium knob' },
      'Switch': { vi: 'Gateron Baby Kangaroo Tactile (đã lube)', en: 'Lubed Gateron Baby Kangaroo Tactiles' },
      'Kết nối': { vi: 'Type-C rời (QMK/VIA tương thích)', en: 'Type-C wired (QMK/VIA ready)' }
    },
    viewCount: 189,
    favoriteCount: 45
  },
  {
    id: 'prod-003',
    title: {
      vi: 'Tai nghe chống ồn Sony WH-1000XM4 Màu Đen (Đệm tai mới thay, Pin 28h)',
      en: 'Sony WH-1000XM4 ANC Wireless Headphones Black (Fresh Earpads, 28h Battery)'
    },
    price: 3200000,
    originalPrice: 6990000,
    category: 'audio',
    categoryName: { vi: 'Âm Thanh & Tai Nghe', en: 'Audio & Headphones' },
    condition: 'good',
    conditionPercent: 92,
    conditionDescription: {
      vi: 'Âm thanh và chống ồn ANC hoạt động chuẩn mực. Đã thay đôi earpad da cừu êm ái hơn pad zin.',
      en: 'Noise cancelling and soundstage 100% functional. Upgraded to premium lambskin earpads.'
    },
    defects: [
      { vi: 'Xước dăm mờ trên bề mặt touchpad cảm ứng bên phải', en: 'Micro-scratches on right touch-sensitive surface' },
      { vi: 'Bao da zin có vết trầy nhẹ ở mặt sau', en: 'OEM travel case shows light exterior wear' }
    ],
    images: [
      'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=800&q=80',
      'https://images.unsplash.com/photo-1546435770-a3e426bf472b?auto=format&fit=crop&w=800&q=80'
    ],
    coInspectionVideoAvailable: true,
    location: 'Hải Châu, Đà Nẵng',
    postedTimeAgo: { vi: '1 ngày trước', en: '1 day ago' },
    seller: {
      id: 'seller-lan',
      name: 'Lan Nguyễn Vintage Sound',
      avatar: 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=200&q=80',
      rating: 4.8,
      reviewCount: 41,
      joinDate: '05/2023',
      isKycVerified: true,
      trustScore: 94,
      responseRate: '92%',
      responseTime: 'Trong 30 phút'
    },
    attributes: {
      'Pin': { vi: '28 giờ nghe liên tục (bật ANC)', en: '28 hours runtime with ANC on' },
      'Phụ kiện': { vi: 'Hộp đựng, cáp 3.5mm, adapter máy bay', en: 'Hard case, 3.5mm cable, flight adapter' }
    },
    viewCount: 412,
    favoriteCount: 67
  },
  {
    id: 'prod-004',
    title: {
      vi: 'Đồng hồ cơ Seiko 5 Automatic 7S26 Cổ điển Vintage (Dial xám chải tia)',
      en: 'Vintage Seiko 5 Automatic 7S26 Classic Wristwatch (Sunburst Grey Dial)'
    },
    price: 1350000,
    originalPrice: 3200000,
    category: 'vintage-watches',
    categoryName: { vi: 'Đồng Hồ Cổ & Trang Sức', en: 'Vintage Watches' },
    condition: 'vintage',
    conditionPercent: 88,
    conditionDescription: {
      vi: 'Đồng hồ nguyên bản sản xuất thập niên 90. Máy 7S26 vừa lau dầu tháng trước, sai số +/- 15s/ngày.',
      en: 'Authentic 1990s release. 7S26 movement professionally serviced last month, runs +/- 15s/day.'
    },
    defects: [
      { vi: 'Kính khoáng có vết xước tóc ở vị trí 2 giờ', en: 'Hairline scratch on mineral crystal at 2 o\'clock' },
      { vi: 'Dây kim loại theo máy vừa cổ tay dưới 17.5cm', en: 'Original steel bracelet fits wrists up to 17.5cm' }
    ],
    images: [
      'https://images.unsplash.com/photo-1522335789203-aabd1fc54bc9?auto=format&fit=crop&w=800&q=80',
      'https://images.unsplash.com/photo-1524805444758-089113d48a6d?auto=format&fit=crop&w=800&q=80'
    ],
    coInspectionVideoAvailable: false,
    location: 'Hoàn Kiếm, Hà Nội',
    postedTimeAgo: { vi: '3 ngày trước', en: '3 days ago' },
    seller: {
      id: 'seller-bac-tam',
      name: 'Bác Tám Cổ Ngoạn',
      avatar: 'https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?auto=format&fit=crop&w=200&q=80',
      rating: 4.9,
      reviewCount: 112,
      joinDate: '11/2022',
      isKycVerified: true,
      trustScore: 99,
      responseRate: '98%',
      responseTime: 'Trong 10 phút'
    },
    attributes: {
      'Bộ máy': { vi: 'Tự động Automatic Seiko 7S26 (21 chân kính)', en: 'Automatic Seiko 7S26 (21 Jewels)' },
      'Size mặt': { vi: '37mm (Dày 11mm, Lug 18mm)', en: '37mm diameter (11mm thick, 18mm lug)' }
    },
    viewCount: 520,
    favoriteCount: 89
  },
  {
    id: 'prod-005',
    title: {
      vi: 'Áo khoác Jean Levi\'s Vintage Trucker Jacket Type III (Made in USA, Size M)',
      en: 'Vintage Levi\'s Type III Denim Trucker Jacket (Made in USA, Size M, Faded Indigo)'
    },
    price: 1650000,
    originalPrice: 2800000,
    category: 'vintage-fashion',
    categoryName: { vi: 'Thời Trang Second-hand', en: 'Vintage Apparel' },
    condition: 'vintage',
    conditionPercent: 90,
    conditionDescription: {
      vi: 'Chất jean đanh dày chuẩn US, wash màu chàm bạc tự nhiên cực đẹp. Khuy đồng nguyên bản.',
      en: 'Heavyweight US cotton denim with authentic sun-faded indigo patina. Original copper buttons intact.'
    },
    defects: [
      { vi: 'Sờn nhẹ ở gấu tay áo bên trái do vintage wear', en: 'Light vintage fraying along left cuff rim' }
    ],
    images: [
      'https://images.unsplash.com/photo-1576995853123-5a10305d93c0?auto=format&fit=crop&w=800&q=80',
      'https://images.unsplash.com/photo-1542272604-780c96856592?auto=format&fit=crop&w=800&q=80'
    ],
    coInspectionVideoAvailable: true,
    location: 'Bình Thạnh, TP. Hồ Chí Minh',
    postedTimeAgo: { vi: '5 giờ trước', en: '5 hours ago' },
    seller: {
      id: 'seller-hoa',
      name: 'Tiệm Vintage Cũ Kỹ',
      avatar: 'https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&w=200&q=80',
      rating: 4.9,
      reviewCount: 78,
      joinDate: '03/2023',
      isKycVerified: true,
      trustScore: 97,
      responseRate: '96%',
      responseTime: 'Trong 8 phút'
    },
    attributes: {
      'Kích thước': { vi: 'Vai 46cm, Dài 62cm, Tay 61cm (phù hợp 1m68 - 1m75)', en: 'Shoulder 46cm, Length 62cm, Sleeve 61cm' },
      'Chất liệu': { vi: '100% Cotton Jean dệt thô', en: '100% Raw Heavy Cotton Denim' }
    },
    viewCount: 290,
    favoriteCount: 51
  },
  {
    id: 'prod-006',
    title: {
      vi: 'Máy chơi game Nintendo Switch OLED White (Fullbox 99%, Kèm thẻ nhớ 128GB)',
      en: 'Nintendo Switch OLED Model White (Full Box 99% Mint, Includes 128GB MicroSD)'
    },
    price: 5400000,
    originalPrice: 8200000,
    category: 'gaming',
    categoryName: { vi: 'Máy Chơi Game & Đĩa', en: 'Gaming & Retro Consoles' },
    condition: 'like_new',
    conditionPercent: 99,
    conditionDescription: {
      vi: 'Màn hình OLED không điểm chết, đã dán cường lực từ lúc đập hộp. Joycon không drift.',
      en: 'Flawless OLED screen, glass screen protector applied day one. Zero joy-con drift.'
    },
    defects: [],
    images: [
      'https://images.unsplash.com/photo-1578303512597-81e6cc155b3e?auto=format&fit=crop&w=800&q=80',
      'https://images.unsplash.com/photo-1612287233207-6c84c1f93f18?auto=format&fit=crop&w=800&q=80'
    ],
    coInspectionVideoAvailable: true,
    location: 'Thanh Xuân, Hà Nội',
    postedTimeAgo: { vi: '6 giờ trước', en: '6 hours ago' },
    seller: {
      id: 'seller-tuan',
      name: 'Tuấn Mech & Audio',
      avatar: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=200&q=80',
      rating: 5.0,
      reviewCount: 32,
      joinDate: '01/2024',
      isKycVerified: true,
      trustScore: 96,
      responseRate: '95%',
      responseTime: 'Trong 15 phút'
    },
    attributes: {
      'Tình trạng box': { vi: 'Đầy đủ dock, grip, strap, nguồn zin nguyên seal bọc', en: 'Complete with dock, grip, straps, OEM charger' },
      'Bảo hành': { vi: 'Bao test 1 tháng có Escrow bảo vệ', en: '1-month personal warranty + Escrow hold' }
    },
    viewCount: 640,
    favoriteCount: 110
  }
];

export const MOCK_VOUCHERS: MockVoucher[] = [
  {
    id: 'vch-01',
    code: 'OGFREESHIP',
    title: { vi: 'Miễn phí vận chuyển đồng kiểm (tối đa 30.000đ)', en: 'Free Co-inspection Shipping (up to 30,000₫)' },
    discountAmount: 30000,
    minOrder: 500000,
    expiry: '30/11/2026',
    type: 'shipping'
  },
  {
    id: 'vch-02',
    code: 'WELCOMEOG',
    title: { vi: 'Giảm ngay 50.000đ cho đơn hàng đầu tiên', en: '50,000₫ OFF your first Escrow order' },
    discountAmount: 50000,
    minOrder: 1000000,
    expiry: '31/12/2026',
    type: 'order'
  },
  {
    id: 'vch-03',
    code: 'VINTAGELOVER',
    title: { vi: 'Ưu đãi đồ cũ sưu tầm 100.000đ cho đơn từ 2 triệu', en: '100,000₫ OFF Collectibles on orders > 2M' },
    discountAmount: 100000,
    minOrder: 2000000,
    expiry: '15/10/2026',
    type: 'order'
  }
];

export const MOCK_ORDERS: MockOrder[] = [
  {
    id: 'ord-88912',
    orderNumber: 'OG-2026-88912',
    productId: 'prod-001',
    productTitle: {
      vi: 'Máy ảnh Sony Alpha A7 Mark III (Shutter 12k shot)',
      en: 'Sony Alpha A7 III Mirrorless Camera'
    },
    productImage: 'https://images.unsplash.com/photo-1516035069371-29a1b244cc32?auto=format&fit=crop&w=400&q=80',
    sellerName: 'Minh Camera Saigon',
    buyerName: 'Bảo Khánh (Khách Mua)',
    amount: 24500000,
    shippingFee: 45000,
    voucherDiscount: 50000,
    totalPaid: 24495000,
    escrowStatus: 'HOLDING',
    shippingStatus: 'OUT_FOR_DELIVERY',
    createdAt: '18/09/2026 14:30',
    coInspectionRequired: true,
    trackingNumber: 'VNPOST-OG-88912-VN',
    carrier: 'Giao Hàng Tiết Kiệm (Đồng Kiểm O.G)',
    timeline: [
      {
        time: '18/09 14:30',
        title: { vi: 'Đặt hàng thành công & Escrow giữ tiền', en: 'Order placed & Escrow holding payment' },
        description: { vi: 'Số tiền 24.495.000₫ đã được giữ an toàn tại tài khoản O.G Escrow trung gian.', en: '24,495,000₫ held safely in O.G Escrow intermediary account.' },
        isDone: true
      },
      {
        time: '19/09 09:15',
        title: { vi: 'Người bán đóng gói & đính kèm video đồng kiểm', en: 'Seller packed item with co-inspection video' },
        description: { vi: 'Video kiểm tra cảm biến, nút bấm và ngoại hình đã được tải lên hệ thống.', en: 'Video verifying sensor, buttons, and exterior uploaded to platform.' },
        isDone: true
      },
      {
        time: '19/09 16:40',
        title: { vi: 'Đang vận chuyển liên tỉnh', en: 'In transit between hubs' },
        description: { vi: 'Bưu kiện rời kho trung chuyển TP.HCM hướng về Hà Nội.', en: 'Parcel departed HCMC sorting facility bound for Hanoi.' },
        isDone: true
      },
      {
        time: '20/09 08:20',
        title: { vi: 'Shipper đang giao hàng — Yêu cầu Đồng Kiểm', en: 'Out for delivery — Co-inspection required' },
        description: { vi: 'Shipper Nguyễn Văn H. đang giao. Hãy quay video mở hộp cùng shipper!', en: 'Courier is delivering. Please record unboxing together with courier!' },
        isDone: false
      }
    ]
  },
  {
    id: 'ord-88741',
    orderNumber: 'OG-2026-88741',
    productId: 'prod-002',
    productTitle: {
      vi: 'Bàn phím cơ Custom Keychron Q1 V2 Knob',
      en: 'Custom Keychron Q1 V2 Knob Mech Keyboard'
    },
    productImage: 'https://images.unsplash.com/photo-1587829741301-dc798b83add3?auto=format&fit=crop&w=400&q=80',
    sellerName: 'Tuấn Mech & Audio',
    buyerName: 'Bảo Khánh (Khách Mua)',
    amount: 2850000,
    shippingFee: 30000,
    voucherDiscount: 30000,
    totalPaid: 2850000,
    escrowStatus: 'RELEASED',
    shippingStatus: 'DELIVERED',
    createdAt: '12/09/2026 10:15',
    coInspectionRequired: true,
    coInspectionPassed: true,
    trackingNumber: 'GHN-OG-88741-HN',
    carrier: 'Giao Hàng Nhanh',
    timeline: [
      {
        time: '12/09 10:15',
        title: { vi: 'Đặt hàng & Thanh toán Escrow', en: 'Order placed & Escrow funded' },
        description: { vi: 'Đã thanh toán an toàn qua VNPAY Escrow.', en: 'Paid safely via VNPAY Escrow.' },
        isDone: true
      },
      {
        time: '13/09 15:20',
        title: { vi: 'Đã nhận hàng & Đồng kiểm thành công', en: 'Delivered & Co-inspection verified' },
        description: { vi: 'Người mua kiểm tra đúng tình trạng 99% và bấm Hài Lòng.', en: 'Buyer confirmed 99% condition match and clicked Satisfied.' },
        isDone: true
      },
      {
        time: '13/09 15:25',
        title: { vi: 'Escrow giải ngân tiền cho Người bán', en: 'Escrow released funds to seller' },
        description: { vi: '2.850.000₫ đã được cộng vào số dư khả dụng của Tuấn Mech.', en: '2,850,000₫ credited to seller available balance.' },
        isDone: true
      }
    ]
  }
];

export const MOCK_CHATS: MockChatMessage[] = [
  {
    id: 'msg-1',
    senderId: 'buyer-01',
    senderName: 'Bảo Khánh',
    text: 'Chào bạn, máy A7 III này còn đủ hóa đơn mua hàng trước đây không bạn?',
    time: '10:15',
    isSeller: false,
    type: 'text'
  },
  {
    id: 'msg-2',
    senderId: 'seller-minh',
    senderName: 'Minh Camera Saigon',
    text: 'Dạ máy nguyên hộp và có phiếu mua hàng tại Sony Center năm ngoái nha anh. Em có quay video test sensor và các nút trên tin đăng rồi đó ạ.',
    time: '10:17',
    isSeller: true,
    type: 'text'
  },
  {
    id: 'msg-3',
    senderId: 'buyer-01',
    senderName: 'Bảo Khánh',
    text: 'Mình ở Hà Nội, mình muốn thương lượng bớt chút tiền vận chuyển và mua thêm 1 pin Wasabi được không?',
    time: '10:20',
    isSeller: false,
    type: 'offer',
    offerDetails: {
      originalPrice: 24500000,
      offeredPrice: 23800000,
      status: 'pending'
    }
  },
  {
    id: 'msg-4',
    senderId: 'seller-minh',
    senderName: 'Minh Camera Saigon',
    text: 'Có gì kết bạn Zalo số 0909123456 mình gửi thêm video chụp cận cảnh nha bạn!',
    time: '10:22',
    isSeller: true,
    type: 'warning'
  }
];

export const MOCK_DISPUTE: MockDispute = {
  id: 'dsp-2026-004',
  orderNumber: 'OG-2026-77192',
  productTitle: {
    vi: 'Tai nghe chống ồn Sony WH-1000XM4 Màu Đen',
    en: 'Sony WH-1000XM4 ANC Wireless Headphones'
  },
  productImage: 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=400&q=80',
  buyerName: 'Trần Văn Nam (Buyer)',
  sellerName: 'Lan Nguyễn Sound (Seller)',
  reason: {
    vi: 'Sản phẩm có vết nứt khớp gập bên trái không được mô tả trong bài đăng',
    en: 'Hinge crack on left ear cup not disclosed in listing description'
  },
  buyerStatement: {
    vi: 'Khi nhận hàng và mở hộp cùng shipper, mình phát hiện khớp gập bên tai trái có vết rạn sâu khoảng 1.5cm, có nguy cơ gãy khi đeo. Tin đăng mô tả 92% chỉ nói xước dăm nhưng không nhắc tới vết rạn này. Đã có video đồng kiểm làm bằng chứng.',
    en: 'During unboxing with courier, noticed deep 1.5cm fissure on left folding hinge. Poses breaking risk. Listing described 92% cosmetic wear but omitted this crack. Co-inspection video attached.'
  },
  sellerStatement: {
    vi: 'Lúc mình đóng gói gửi đi khớp gập vẫn chắc chắn, mình có quay video lúc bấm thử âm thanh và gập tai nghe vào bao da. Mong admin xem xét hỗ trợ giải pháp.',
    en: 'Before dispatch, folding hinge was firm. I recorded packaging video showing functional folding into case. Seeking fair admin mediation.'
  },
  disputedAmount: 3200000,
  createdAt: '19/09/2026 11:30',
  status: 'ADMIN_REVIEWING',
  evidencePhotos: [
    'https://images.unsplash.com/photo-1546435770-a3e426bf472b?auto=format&fit=crop&w=600&q=80',
    'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=600&q=80'
  ],
  coInspectionVideoAvailable: true,
  timeline: [
    {
      time: '19/09 11:30',
      title: { vi: 'Người mua mở yêu cầu khiếu nại & tạm dừng Escrow', en: 'Buyer opened dispute & froze Escrow payment' },
      by: 'Buyer (Trần Văn Nam)'
    },
    {
      time: '19/09 14:10',
      title: { vi: 'Người bán phản hồi và cung cấp video đóng hàng', en: 'Seller replied with dispatch packaging video' },
      by: 'Seller (Lan Nguyễn)'
    },
    {
      time: '20/09 09:00',
      title: { vi: 'Trọng tài viên O.G Escrow tiếp nhận hồ sơ phân xử', en: 'O.G Escrow arbitrator initiated dispute room review' },
      by: 'Admin Specialist (Minh Tuấn - Badge #04)'
    }
  ]
};

export const MOCK_WALLET = {
  coinBalance: 12500,
  vndEquivalent: '12.500₫',
  pendingCoins: 3500,
  history: [
    { id: 'w-1', date: '18/09/2026', desc: { vi: 'Thưởng đồng kiểm đơn OG-88912', en: 'Reward for co-inspection OG-88912' }, amount: '+5.000 O.G Xu', type: 'in' },
    { id: 'w-2', date: '13/09/2026', desc: { vi: 'Đánh giá kèm ảnh máy Keychron Q1', en: 'Review with photo for Keychron Q1' }, amount: '+2.500 O.G Xu', type: 'in' },
    { id: 'w-3', date: '10/09/2026', desc: { vi: 'Đổi xu giảm giá phí vận chuyển', en: 'Redeemed coins for shipping fee discount' }, amount: '-10.000 O.G Xu', type: 'out' },
    { id: 'w-4', date: '01/09/2026', desc: { vi: 'Thưởng đăng ký thành viên O.G Club', en: 'Welcome bonus O.G Club membership' }, amount: '+15.000 O.G Xu', type: 'in' }
  ]
};

export const MOCK_ADMIN_METRICS = {
  totalGmv: '1.428.500.000₫',
  escrowHoldingBalance: '385.200.000₫',
  activeDisputesCount: 6,
  pendingKycCount: 14,
  flaggedListingsCount: 8,
  dailyCompletedOrders: 92
};
