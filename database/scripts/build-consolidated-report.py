"""Generate a data-free table dictionary and standalone 48-table ERD DDL.

Input must be structural catalog JSON from export-consolidated-metadata.sql.
It never opens private backups or credential files.
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
catalog = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8-sig"))
layout = json.loads((ROOT / "output/database-consolidation-2026-10-09/consolidation-layout.json").read_text(encoding="utf-8"))
tables = catalog["tables"]
assert len(tables) == 48
assert set(tables) == set(layout["plan"])
GROUPS = {
    "Tài khoản, xác minh": "users roles user_roles addresses refresh_sessions auth_challenges ekyc_profiles ekyc_private_assets identity_document_registry verification_attempts seller_profiles",
    "Tin đăng, chính sách": "categories products product_categories product_revisions product_media listing_fee_charges business_policies",
    "Trao đổi, thương lượng": "conversations messages offers",
    "Mua hàng, ưu đãi": "cart_items checkout_groups orders order_items inventory_reservations vouchers voucher_products voucher_grants reward_ledger",
    "Thanh toán": "payments payment_attempts payment_events payment_confirmations order_fund_components settlement_operations",
    "Giao nhận": "shipments shipment_events",
    "Khiếu nại, tranh chấp": "cases case_actions case_evidence",
    "Quản trị, thông báo": "reviews seller_buyer_blocks penalty_ledger notifications audit_logs outbox_events interaction_events",
}
DESCRIPTIONS = {
    "users": "Tài khoản; gộp cấu hình bảo mật, liên kết đăng nhập, điểm hiện tại, hạn chế tài khoản và đầu giỏ hàng.",
    "roles": "Danh mục vai trò.", "user_roles": "Vai trò được cấp cho tài khoản.",
    "addresses": "Địa chỉ của người dùng.", "refresh_sessions": "Phiên đăng nhập và digest token làm mới.",
    "auth_challenges": "OTP, đặt lại mật khẩu, phiên và kết quả xác thực nhanh; phân loại bằng record_type.",
    "ekyc_profiles": "Hồ sơ định danh theo phiên bản, metadata xác minh cũ và lịch sử quyết định.",
    "ekyc_private_assets": "Metadata tài sản định danh riêng tư; không chứa sinh trắc học thô.",
    "identity_document_registry": "Digest tài liệu và chủ sở hữu để chống đăng ký trùng.",
    "verification_attempts": "Các lần thử xác minh và metric cũ; phân biệt kết quả nghiệp vụ với lỗi dịch vụ.",
    "seller_profiles": "Hồ sơ bán hàng, thông tin nhận tiền/nhận hàng và lịch sử xét duyệt.",
    "categories": "Danh mục sản phẩm.", "products": "Tin đăng và tồn kho hiện tại.",
    "product_categories": "Quan hệ sản phẩm với nhiều danh mục.",
    "product_revisions": "Phiên bản tin, snapshot giá/số lượng và lịch sử kiểm tra/duyệt media.",
    "product_media": "Ảnh/video cũ và media của từng phiên bản tin.",
    "listing_fee_charges": "Đánh giá phí và yêu cầu thu phí; record_type giữ riêng ý nghĩa của từng nghiệp vụ.",
    "business_policies": "Các phiên bản chính sách phí hệ thống, phí tin, checklist, điểm và phạt.",
    "conversations": "Hội thoại cùng trạng thái người tham gia và mức sử dụng media.",
    "messages": "Tin nhắn.", "offers": "Đề nghị giá và trạng thái thương lượng.",
    "cart_items": "Dòng sản phẩm trong giỏ, liên kết trực tiếp tới người dùng.",
    "checkout_groups": "Nhóm thanh toán, snapshot địa chỉ, voucher đã dùng và xác nhận checkout 0đ.",
    "orders": "Đơn hàng; snapshot phí, địa chỉ, voucher cũ và liên kết hội thoại.",
    "order_items": "Dòng hàng và snapshot giá/số lượng; lưu lịch sử phân bổ giảm giá.",
    "inventory_reservations": "Giữ/tiêu thụ/giải phóng tồn kho, có khóa lệnh chống lặp.",
    "vouchers": "Voucher và các phiên bản chính sách; lịch sử thu hồi gắn với phiên bản.",
    "voucher_products": "Phạm vi sản phẩm áp dụng cho phiên bản voucher.",
    "voucher_grants": "Voucher đã cấp cho người dùng và lịch sử sử dụng.",
    "reward_ledger": "Sổ biến động điểm; số dư trên users được suy ra từ tài khoản điểm được bảo vệ.",
    "payments": "Thanh toán cũ và intent theo mục đích; không coi intent là tiền đã nhận.",
    "payment_attempts": "Lần thử thanh toán tổng quát và VNPay.",
    "payment_events": "Receipt callback thô và sự kiện đã phân tích, phân loại riêng trong cùng sổ.",
    "payment_confirmations": "Xác nhận tiền có kiểm chứng và receipt phí tin.",
    "order_fund_components": "Phân bổ tiền vào đơn và các phần tiền hàng/ship/phí/trợ giá.",
    "settlement_operations": "Lệnh hoàn tiền/giải ngân cùng trạng thái thực hiện và chống lặp.",
    "shipments": "Chặng giao đi/trả về, phí và hạn giao.",
    "shipment_events": "Sự kiện vận chuyển, lần lấy hàng và xác nhận giao trực tiếp.",
    "cases": "Hồ sơ trả hàng, khiếu nại, báo cáo và đối soát thanh toán.",
    "case_actions": "Vòng xử lý, quyết định, sự kiện, giữ tiền và nhật ký kiểm tra đối soát.",
    "case_evidence": "Bằng chứng theo vòng xử lý và metadata video mở hàng cũ.",
    "reviews": "Đánh giá và lịch sử chỉnh sửa.", "seller_buyer_blocks": "Chặn giao dịch theo cặp người bán/người mua.",
    "penalty_ledger": "Sổ điểm phạt có nguồn và người thực hiện.",
    "notifications": "Thông báo, lệnh gửi, nhắc hạn, cảnh báo thanh toán và lịch sử giao thông báo.",
    "audit_logs": "Nhật ký kiểm toán, trạng thái tài khoản/đơn hàng và lượt xuất báo cáo.",
    "outbox_events": "Sự kiện chờ xử lý cùng tiến độ consumer.",
    "interaction_events": "Sự kiện tương tác để thống kê.",
}

def cell(value):
    return str(value or "—").replace("|", "\\|").replace("\n", " ")

lines = [f"# Danh mục 48 bảng nghiệp vụ — PostgreSQL V{catalog['version']}", "",
    "Cấu trúc gộp được tạo ở V22; V23 căn lại bộ đếm ID của bảng dùng chung. Xuất từ catalog PostgreSQL hiện hành, không có dữ liệu người dùng.", "",
    "`public` có 48 bảng nghiệp vụ; `flyway_schema_history` là bảng kỹ thuật riêng. 7 view báo cáo và 104 view tương thích trong `og_compat` không được đếm là bảng hay thêm vào ERD nghiệp vụ.", "",
    "Các bảng dùng chung có `record_type`. NULL ở cột vật lý có thể chỉ là cột không áp dụng cho loại dòng đó; yêu cầu NOT NULL/CHECK/FK theo từng loại vẫn được trigger và view nghiệp vụ bảo vệ. `source_*` giữ định danh cũ, không phải bản sao dữ liệu. Các mảng JSONB giữ metadata/lịch sử gắn với một chủ thể, có ràng buộc và quy trình ghi.", "",
    "`users.reward_balance` và `users.email_2fa_enabled` là cột generated, đọc trực tiếp nhưng cập nhật qua nghiệp vụ điểm/bảo mật. Không sửa JSON lịch sử trực tiếp.", "",
    "## Danh sách để đưa vào báo cáo", "", "| Nhóm | Số bảng | Tên bảng |", "|---|---:|---|"]
for group, names in GROUPS.items():
    lines.append(f"| {group} | {len(names.split())} | " + ", ".join(f"`{n}`" for n in names.split()) + " |")
lines += ["", "## Cấu trúc từng bảng", ""]
for group, names in GROUPS.items():
    for table in names.split():
        entry = tables[table]
        keys = {col for c in entry["constraints"] if c["kind"] == "p" for col in c["columns"]}
        foreign = {}
        for c in entry["constraints"]:
            if c["kind"] == "f":
                for a,b in zip(c["columns"],c["ref_columns"]):
                    foreign.setdefault(a,set()).add(c["ref_table"] + "." + b)
        plan = layout["plan"][table]
        lines += [f"### `{table}`", "", DESCRIPTIONS[table], "",
            "Nguồn dòng: " + ", ".join(f"`{s}`" for s in plan["members"]) + "."]
        if plan["folds"]:
            lines += ["Metadata/lịch sử gộp: " + "; ".join(f"`{s}` → `{layout['sources'][s]['column']}`" for s in plan["folds"]) + "."]
        lines += ["", "| Thuộc tính | Kiểu PostgreSQL | Khóa/quan hệ | Bắt buộc vật lý | Mặc định / generated |",
            "|---|---|---|---|---|"]
        for c in entry["columns"]:
            relation = (["PK"] if c["name"] in keys else []) + ["FK → " + p for p in sorted(foreign.get(c["name"],[]))]
            default = ("GENERATED: " if c["generated"] else "") + (c["default"] or "")
            lines.append("| " + " | ".join([f"`{c['name']}`",cell(c["type"]),cell("; ".join(relation)),"Có" if c["notnull"] else "Theo loại dòng",cell(default)]) + " |")
        lines.append("")
lines += ["## Ánh xạ 104 nguồn cũ → 48 bảng", "", "| Nguồn V21 | Bảng V22 | Cách lưu |", "|---|---|---|"]
for s,e in sorted(layout["sources"].items()):
    mode = f"Dòng có `record_type={s}`" if e["mode"]=="ROWS" else f"JSONB `{e['column']}` trên chủ thể"
    lines.append(f"| `{s}` | `{e['target']}` | {mode} |")
lines += ["", "## Dùng cho ERD", "",
    "Chọn schema `public`, chọn 48 bảng ở danh sách trên, bỏ `flyway_schema_history`. Khóa ngoại tới `source_*` là quan hệ tới định danh của một loại dòng trong bảng dùng chung; không tạo thêm bảng cho mỗi loại. Quan hệ metadata JSONB được mô tả trong ánh xạ nguồn, không tự hiện thành FK trên ERD.", "",
    f"Catalog có {sum(c['kind']=='f' for t in tables.values() for c in t['constraints'])} ràng buộc FK vật lý. Một cặp bảng có thể có nhiều FK theo loại dòng hoặc khóa ghép; chỉ hiển thị quan hệ cần đọc trên sơ đồ tổng quan, giữ đầy đủ trong phụ lục cấu trúc.", "",
    "[DDL chỉ gồm 48 bảng và quan hệ](../schema/og_shop_v22_tables_for_erd.sql). DDL này để dựng ERD trong database trống; runtime dùng Flyway V1–V22 cùng view/trigger, không chạy snapshot đè database đang dùng.", ""]
(ROOT / "database/docs/DATABASE_TABLE_DICTIONARY_V22.md").write_text("\n".join(lines),encoding="utf-8")

ddl = ["-- Structure-only ERD snapshot. Exactly 48 business tables; no data/views/workflow triggers.",
       "-- Import only into an empty disposable database for diagramming. Runtime uses Flyway.", "CREATE SCHEMA IF NOT EXISTS public;"]
for seq,s in sorted(catalog["sequences"].items()):
    ddl.append(f"CREATE SEQUENCE public.{seq} AS {s['type']} INCREMENT BY {s['increment']} MINVALUE {s['min']} MAXVALUE {s['max']} START WITH {s['start']} CACHE {s['cache']} " + ("CYCLE" if s["cycle"] else "NO CYCLE") + ";")
for table,e in sorted(tables.items()):
    columns = []
    for c in e["columns"]:
        column = f"{c['name']} {c['type']}"
        if c["generated"]: column += f" GENERATED ALWAYS AS ({c['default']}) STORED"
        elif c["default"]: column += " DEFAULT " + c["default"]
        if c["notnull"]: column += " NOT NULL"
        columns.append(column)
    ddl.append(f"CREATE TABLE public.{table} (\n  " + ",\n  ".join(columns) + "\n);")
for kind in ["p","u","c","f"]:
    for table,e in sorted(tables.items()):
        for c in e["constraints"]:
            if c["kind"] == kind: ddl.append(f"ALTER TABLE public.{table} ADD CONSTRAINT {c['name']} {c['definition']};")
for table,e in sorted(tables.items()):
    ddl.extend(index + ";" for index in e["indexes"])
out = ROOT / "database/schema/og_shop_v22_tables_for_erd.sql"
out.parent.mkdir(parents=True,exist_ok=True)
out.write_text("\n\n".join(ddl)+"\n",encoding="utf-8")
print(f"Exported dictionary and ERD DDL: {len(tables)} tables, {sum(len(t['columns']) for t in tables.values())} physical attributes.")
