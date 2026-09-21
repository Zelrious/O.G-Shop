-- ============================================================================
-- V5: Seed MVP standard categories idempotently by slug
-- ============================================================================

INSERT INTO categories (category_name, slug, description, is_active, display_order)
VALUES
    ('Điện tử', 'electronics', 'Thiết bị điện tử, điện thoại, máy tính, linh kiện và phụ kiện', TRUE, 1),
    ('Thời trang', 'fashion', 'Quần áo, giày dép, phụ kiện và túi xách đã qua sử dụng', TRUE, 2),
    ('Nhà cửa & đời sống', 'home-living', 'Đồ gia dụng, nội thất, thiết bị nhà bếp và trang trí', TRUE, 3),
    ('Sách & văn phòng phẩm', 'books-stationery', 'Sách, giáo trình, truyện và dụng cụ học tập', TRUE, 4),
    ('Thể thao & dã ngoại', 'sports-outdoors', 'Dụng cụ thể thao, xe đạp, phụ kiện thể hình và dã ngoại', TRUE, 5),
    ('Đồ sưu tầm', 'collectibles', 'Mô hình, đồng hồ cổ, đồ lưu niệm và hiện vật sưu tầm', TRUE, 6),
    ('Mẹ & bé', 'mother-baby', 'Đồ dùng cho mẹ và bé, đồ chơi an toàn, xe đẩy và nôi cũ', TRUE, 7),
    ('Khác', 'other', 'Các mặt hàng đồ cũ và sản phẩm khác chưa phân loại', TRUE, 8)
ON CONFLICT (slug) DO UPDATE
SET category_name = EXCLUDED.category_name,
    description = EXCLUDED.description,
    is_active = EXCLUDED.is_active,
    display_order = EXCLUDED.display_order,
    updated_at = CURRENT_TIMESTAMP;
