import React, { useMemo } from 'react';
import './DriftWall.css';

interface DriftItem {
  id: string;
  title: string;
  category: string;
  tag: string;
  image: string;
  price: string;
}

const VINTAGE_COLLECTION: DriftItem[] = [
  {
    id: 'camera',
    title: 'Máy ảnh cơ Leica M3 Classic',
    category: 'Nhiếp ảnh',
    tag: 'Độ mới 95%',
    image: 'https://images.unsplash.com/photo-1526170375885-4d8ecf77b99f?auto=format&fit=crop&w=600&q=80',
    price: '14.500.000đ',
  },
  {
    id: 'vinyl',
    title: 'Đĩa than Vinyl Jazz The Trio 1968',
    category: 'Âm nhạc',
    tag: 'Bản hiếm',
    image: 'https://images.unsplash.com/photo-1539185441755-769473a23570?auto=format&fit=crop&w=600&q=80',
    price: '950.000đ',
  },
  {
    id: 'watch',
    title: 'Đồng hồ cơ Omega Seamaster Vintage',
    category: 'Phụ kiện',
    tag: 'Sưu tầm',
    image: 'https://images.unsplash.com/photo-1522335789203-aabd1fc54bc9?auto=format&fit=crop&w=600&q=80',
    price: '28.000.000đ',
  },
  {
    id: 'typewriter',
    title: 'Máy đánh chữ cơ Olivetti Lettera 32',
    category: 'Đồ xưa',
    tag: 'Hoạt động tốt',
    image: 'https://images.unsplash.com/photo-1516962215378-7fa2e137ae93?auto=format&fit=crop&w=600&q=80',
    price: '2.400.000đ',
  },
  {
    id: 'bag',
    title: 'Túi da bò thật Retro Satchel 1990',
    category: 'Thời trang',
    tag: 'Da sáp mộc',
    image: 'https://images.unsplash.com/photo-1548036328-c9fa89d128fa?auto=format&fit=crop&w=600&q=80',
    price: '1.650.000đ',
  },
  {
    id: 'keyboard',
    title: 'Bàn phím cơ IBM Model M Buckling Spring',
    category: 'Công nghệ cũ',
    tag: 'Nguyên bản',
    image: 'https://images.unsplash.com/photo-1587829741301-dc798b83add3?auto=format&fit=crop&w=600&q=80',
    price: '3.200.000đ',
  },
  {
    id: 'radio',
    title: 'Radio đèn cổ điển Telefunken Opus',
    category: 'Âm thanh',
    tag: 'Độ mới 90%',
    image: 'https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?auto=format&fit=crop&w=600&q=80',
    price: '5.800.000đ',
  },
  {
    id: 'coffee',
    title: 'Cối xay cà phê tay Zassenhaus Antique',
    category: 'Gia dụng xưa',
    tag: 'Gỗ & gang đúc',
    image: 'https://images.unsplash.com/photo-1514432324607-a09d9b4aefdd?auto=format&fit=crop&w=600&q=80',
    price: '1.200.000đ',
  },
  {
    id: 'sunglasses',
    title: 'Kính râm Ray-Ban Aviator B&L USA',
    category: 'Phụ kiện',
    tag: 'Thập niên 80',
    image: 'https://images.unsplash.com/photo-1511499767150-a48a237f0083?auto=format&fit=crop&w=600&q=80',
    price: '2.850.000đ',
  },
  {
    id: 'turntable',
    title: 'Đầu đĩa than Technics SL-1200MK2',
    category: 'Âm thanh',
    tag: 'Huyền thoại',
    image: 'https://images.unsplash.com/photo-1603048588665-791ca8aea617?auto=format&fit=crop&w=600&q=80',
    price: '12.000.000đ',
  },
  {
    id: 'boots',
    title: 'Giày bốt da Red Wing Iron Ranger 8111',
    category: 'Thời trang',
    tag: 'Chính hãng',
    image: 'https://images.unsplash.com/photo-1549298916-b41d501d3772?auto=format&fit=crop&w=600&q=80',
    price: '4.500.000đ',
  },
  {
    id: 'fountainpen',
    title: 'Bút máy Montblanc Meisterstück 149',
    category: 'Sưu tầm',
    tag: 'Ngòi vàng 18K',
    image: 'https://images.unsplash.com/photo-1583485088034-697b5bc54ccd?auto=format&fit=crop&w=600&q=80',
    price: '9.200.000đ',
  },
];

export interface DriftWallProps {
  columnsCount?: number;
  speed?: number;
  className?: string;
}

export const DriftWall: React.FC<DriftWallProps> = ({
  columnsCount = 7,
  speed = 45,
  className = '',
}) => {
  // Pre-generate columns with repeated items so that each column loops infinitely without gaps
  const columns = useMemo(() => {
    const cols: DriftItem[][] = [];
    const totalItems = VINTAGE_COLLECTION.length;

    for (let c = 0; c < columnsCount; c++) {
      const colItems: DriftItem[] = [];
      // Offset starting item per column for a natural, varied look
      const offset = (c * 3) % totalItems;
      for (let i = 0; i < totalItems; i++) {
        colItems.push(VINTAGE_COLLECTION[(i + offset) % totalItems]);
      }
      // Duplicate items to ensure smooth seamless looping
      cols.push([...colItems, ...colItems]);
    }
    return cols;
  }, [columnsCount]);

  return (
    <div className={`og-drift-wall-container ${className}`} aria-hidden="true">
      <div className="og-drift-wall-grid">
        {columns.map((colItems, colIndex) => {
          const isOdd = colIndex % 2 === 1;
          const colSpeed = isOdd ? speed * 1.15 : speed * 0.9;
          const animationClass = isOdd ? 'og-drift-col--up' : 'og-drift-col--down';

          return (
            <div
              key={`col-${colIndex}`}
              className={`og-drift-col ${animationClass}`}
              style={{
                animationDuration: `${colSpeed}s`,
                animationDelay: `${(colIndex * -4) % 15}s`,
              }}
            >
              {colItems.map((item, itemIdx) => (
                <div key={`${item.id}-${colIndex}-${itemIdx}`} className="og-drift-tile">
                  <div className="og-drift-tile-img-box">
                    <img
                      src={item.image}
                      alt={item.title}
                      className="og-drift-tile-img"
                      loading="lazy"
                    />
                    <span className="og-drift-tile-tag">{item.tag}</span>
                  </div>
                  <div className="og-drift-tile-info">
                    <span className="og-drift-tile-category">{item.category}</span>
                    <h5 className="og-drift-tile-title">{item.title}</h5>
                    <div className="og-drift-tile-price">{item.price}</div>
                  </div>
                </div>
              ))}
            </div>
          );
        })}
      </div>

      {/* Atmospheric Ambient Scrim for Depth & Readable Form Contrast */}
      <div className="og-drift-wall-overlay" />
    </div>
  );
};
