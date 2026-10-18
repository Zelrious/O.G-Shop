# Danh mục 48 bảng nghiệp vụ — PostgreSQL V23

Cấu trúc gộp được tạo ở V22; V23 căn lại bộ đếm ID của bảng dùng chung. Xuất từ catalog PostgreSQL hiện hành, không có dữ liệu người dùng.

`public` có 48 bảng nghiệp vụ; `flyway_schema_history` là bảng kỹ thuật riêng. 7 view báo cáo và 104 view tương thích trong `og_compat` không được đếm là bảng hay thêm vào ERD nghiệp vụ.

Các bảng dùng chung có `record_type`. NULL ở cột vật lý có thể chỉ là cột không áp dụng cho loại dòng đó; yêu cầu NOT NULL/CHECK/FK theo từng loại vẫn được trigger và view nghiệp vụ bảo vệ. `source_*` giữ định danh cũ, không phải bản sao dữ liệu. Các mảng JSONB giữ metadata/lịch sử gắn với một chủ thể, có ràng buộc và quy trình ghi.

`users.reward_balance` và `users.email_2fa_enabled` là cột generated, đọc trực tiếp nhưng cập nhật qua nghiệp vụ điểm/bảo mật. Không sửa JSON lịch sử trực tiếp.

## Danh sách để đưa vào báo cáo

| Nhóm | Số bảng | Tên bảng |
|---|---:|---|
| Tài khoản, xác minh | 11 | `users`, `roles`, `user_roles`, `addresses`, `refresh_sessions`, `auth_challenges`, `ekyc_profiles`, `ekyc_private_assets`, `identity_document_registry`, `verification_attempts`, `seller_profiles` |
| Tin đăng, chính sách | 7 | `categories`, `products`, `product_categories`, `product_revisions`, `product_media`, `listing_fee_charges`, `business_policies` |
| Trao đổi, thương lượng | 3 | `conversations`, `messages`, `offers` |
| Mua hàng, ưu đãi | 9 | `cart_items`, `checkout_groups`, `orders`, `order_items`, `inventory_reservations`, `vouchers`, `voucher_products`, `voucher_grants`, `reward_ledger` |
| Thanh toán | 6 | `payments`, `payment_attempts`, `payment_events`, `payment_confirmations`, `order_fund_components`, `settlement_operations` |
| Giao nhận | 2 | `shipments`, `shipment_events` |
| Khiếu nại, tranh chấp | 3 | `cases`, `case_actions`, `case_evidence` |
| Quản trị, thông báo | 7 | `reviews`, `seller_buyer_blocks`, `penalty_ledger`, `notifications`, `audit_logs`, `outbox_events`, `interaction_events` |

## Cấu trúc từng bảng

### `users`

Tài khoản; gộp cấu hình bảo mật, liên kết đăng nhập, điểm hiện tại, hạn chế tài khoản và đầu giỏ hàng.

Nguồn dòng: `users`.
Metadata/lịch sử gộp: `user_security_settings` → `user_security_settings_history`; `reward_accounts` → `reward_accounts_history`; `external_identities` → `external_identities_history`; `account_restrictions` → `account_restrictions_history`; `carts` → `carts_history`.

| Thuộc tính | Kiểu PostgreSQL | Khóa/quan hệ | Bắt buộc vật lý | Mặc định / generated |
|---|---|---|---|---|
| `user_id` | bigint | PK | Có | nextval('og70_users_user_id_seq'::regclass) |
| `email` | character varying(320) | — | Theo loại dòng | — |
| `password_hash` | character varying(255) | — | Theo loại dòng | — |
| `full_name` | character varying(120) | — | Theo loại dòng | — |
| `phone_number` | character varying(20) | — | Theo loại dòng | — |
| `avatar_url` | text | — | Theo loại dòng | — |
| `status` | character varying(20) | — | Theo loại dòng | 'ACTIVE'::character varying |
| `created_at` | timestamp with time zone | — | Theo loại dòng | CURRENT_TIMESTAMP |
| `updated_at` | timestamp with time zone | — | Theo loại dòng | — |
| `deleted_at` | timestamp with time zone | — | Theo loại dòng | — |
| `bank_name` | character varying(100) | — | Theo loại dòng | — |
| `bank_account_number` | character varying(50) | — | Theo loại dòng | — |
| `bank_account_holder` | character varying(120) | — | Theo loại dòng | — |
| `email_verified_at` | timestamp with time zone | — | Theo loại dòng | — |
| `identity_workflow` | character varying(20) | — | Theo loại dòng | 'LEGACY_V14'::character varying |
| `record_type` | text | — | Có | 'users'::text |
| `user_security_settings_history` | jsonb | — | Theo loại dòng | '[]'::jsonb |
| `reward_accounts_history` | jsonb | — | Theo loại dòng | '[]'::jsonb |
| `external_identities_history` | jsonb | — | Theo loại dòng | '[]'::jsonb |
| `account_restrictions_history` | jsonb | — | Theo loại dòng | '[]'::jsonb |
| `carts_history` | jsonb | — | Theo loại dòng | '[]'::jsonb |
| `reward_balance` | bigint | — | Theo loại dòng | GENERATED: COALESCE((((reward_accounts_history -> 0) ->> 'balance'::text))::bigint, (0)::bigint) |
| `email_2fa_enabled` | boolean | — | Theo loại dòng | GENERATED: COALESCE((((user_security_settings_history -> 0) ->> 'email_2fa_enabled'::text))::boolean, false) |

### `roles`

Danh mục vai trò.

Nguồn dòng: `roles`.

| Thuộc tính | Kiểu PostgreSQL | Khóa/quan hệ | Bắt buộc vật lý | Mặc định / generated |
|---|---|---|---|---|
| `role_id` | smallint | PK | Có | nextval('og70_roles_role_id_seq'::regclass) |
| `role_name` | character varying(20) | — | Theo loại dòng | — |
| `description` | character varying(255) | — | Theo loại dòng | — |
| `record_type` | text | — | Có | 'roles'::text |

### `user_roles`

Vai trò được cấp cho tài khoản.

Nguồn dòng: `user_roles`.

| Thuộc tính | Kiểu PostgreSQL | Khóa/quan hệ | Bắt buộc vật lý | Mặc định / generated |
|---|---|---|---|---|
| `user_id` | bigint | PK; FK → users.user_id | Có | — |
| `role_id` | smallint | PK; FK → roles.role_id | Có | — |
| `granted_at` | timestamp with time zone | — | Theo loại dòng | CURRENT_TIMESTAMP |
| `granted_by` | bigint | FK → users.user_id | Theo loại dòng | — |
| `record_type` | text | — | Có | 'user_roles'::text |

### `addresses`

Địa chỉ của người dùng.

Nguồn dòng: `addresses`.

| Thuộc tính | Kiểu PostgreSQL | Khóa/quan hệ | Bắt buộc vật lý | Mặc định / generated |
|---|---|---|---|---|
| `address_id` | bigint | PK | Có | nextval('og70_addresses_address_id_seq'::regclass) |
| `user_id` | bigint | FK → users.user_id | Theo loại dòng | — |
| `recipient_name` | character varying(120) | — | Theo loại dòng | — |
| `phone_number` | character varying(20) | — | Theo loại dòng | — |
| `province` | character varying(100) | — | Theo loại dòng | — |
| `district` | character varying(100) | — | Theo loại dòng | — |
| `ward` | character varying(100) | — | Theo loại dòng | — |
| `detail_address` | character varying(255) | — | Theo loại dòng | — |
| `is_default` | boolean | — | Theo loại dòng | false |
| `created_at` | timestamp with time zone | — | Theo loại dòng | CURRENT_TIMESTAMP |
| `updated_at` | timestamp with time zone | — | Theo loại dòng | — |
| `deleted_at` | timestamp with time zone | — | Theo loại dòng | — |
| `record_type` | text | — | Có | 'addresses'::text |

### `refresh_sessions`

Phiên đăng nhập và digest token làm mới.

Nguồn dòng: `refresh_sessions`.

| Thuộc tính | Kiểu PostgreSQL | Khóa/quan hệ | Bắt buộc vật lý | Mặc định / generated |
|---|---|---|---|---|
| `session_id` | uuid | PK | Có | gen_random_uuid() |
| `user_id` | bigint | FK → users.user_id | Theo loại dòng | — |
| `family_id` | uuid | — | Theo loại dòng | — |
| `token_digest` | character varying(64) | — | Theo loại dòng | — |
| `issued_at` | timestamp with time zone | — | Theo loại dòng | — |
| `expires_at` | timestamp with time zone | — | Theo loại dòng | — |
| `consumed_at` | timestamp with time zone | — | Theo loại dòng | — |
| `revoked_at` | timestamp with time zone | — | Theo loại dòng | — |
| `replaced_by` | uuid | FK → refresh_sessions.session_id | Theo loại dòng | — |
| `created_by_ip` | character varying(45) | — | Theo loại dòng | — |
| `user_agent` | character varying(255) | — | Theo loại dòng | — |
| `record_type` | text | — | Có | 'refresh_sessions'::text |

### `auth_challenges`

OTP, đặt lại mật khẩu, phiên và kết quả xác thực nhanh; phân loại bằng record_type.

Nguồn dòng: `auth_challenges`, `password_reset_challenges`, `quick_auth_sessions`, `quick_auth_results`.

| Thuộc tính | Kiểu PostgreSQL | Khóa/quan hệ | Bắt buộc vật lý | Mặc định / generated |
|---|---|---|---|---|
| `challenge_id` | uuid | PK | Có | gen_random_uuid() |
| `user_id` | bigint | FK → auth_challenges.user_id; FK → ekyc_profiles.user_id; FK → users.user_id | Theo loại dòng | — |
| `subject_key` | character varying(360) | — | Theo loại dòng | — |
| `purpose` | character varying(30) | — | Theo loại dòng | — |
| `code_digest` | character varying(128) | — | Theo loại dòng | — |
| `state` | character varying(15) | — | Theo loại dòng | 'OPEN'::character varying |
| `issued_at` | timestamp with time zone | — | Theo loại dòng | CURRENT_TIMESTAMP |
| `expires_at` | timestamp with time zone | — | Theo loại dòng | — |
| `next_send_at` | timestamp with time zone | — | Theo loại dòng | — |
| `attempt_count` | integer | — | Theo loại dòng | 0 |
| `max_attempts` | integer | — | Theo loại dòng | 5 |
| `consumed_at` | timestamp with time zone | — | Theo loại dòng | — |
| `invalidated_at` | timestamp with time zone | — | Theo loại dòng | — |
| `command_key` | character varying(150) | — | Theo loại dòng | — |
| `source_password_reset_challenges_challenge_id` | uuid | — | Theo loại dòng | — |
| `otp_digest` | character(64) | — | Theo loại dòng | — |
| `attempts` | smallint | — | Theo loại dòng | — |
| `created_at` | timestamp with time zone | — | Theo loại dòng | — |
| `source_quick_auth_sessions_session_id` | uuid | — | Theo loại dòng | — |
| `profile_id` | bigint | FK → auth_challenges.profile_id; FK → ekyc_profiles.profile_id | Theo loại dòng | — |
| `reference_asset_id` | bigint | FK → auth_challenges.reference_asset_id; FK → ekyc_private_assets.asset_id | Theo loại dòng | — |
| `nonmatch_count` | integer | — | Theo loại dòng | — |
| `last_nonmatch_at` | timestamp with time zone | — | Theo loại dòng | — |
| `source_quick_auth_results_result_id` | uuid | — | Theo loại dòng | — |
| `matched_at` | timestamp with time zone | — | Theo loại dòng | — |
| `session_id` | uuid | FK → auth_challenges.source_quick_auth_sessions_session_id | Theo loại dòng | — |
| `record_type` | text | — | Có | 'auth_challenges'::text |

### `ekyc_profiles`

Hồ sơ định danh theo phiên bản, metadata xác minh cũ và lịch sử quyết định.

Nguồn dòng: `ekyc_profiles`, `seller_verifications`.
Metadata/lịch sử gộp: `ekyc_decisions` → `ekyc_decisions_history`.

| Thuộc tính | Kiểu PostgreSQL | Khóa/quan hệ | Bắt buộc vật lý | Mặc định / generated |
|---|---|---|---|---|
| `profile_id` | bigint | PK | Có | nextval('og70_ekyc_profiles_profile_id_seq'::regclass) |
| `user_id` | bigint | FK → identity_document_registry.user_id; FK → users.user_id | Theo loại dòng | — |
| `revision_no` | integer | — | Theo loại dòng | — |
| `state` | character varying(20) | — | Theo loại dòng | 'DRAFT'::character varying |
| `submitted_data` | jsonb | — | Theo loại dòng | '{}'::jsonb |
| `ai_summary` | jsonb | — | Theo loại dòng | '{}'::jsonb |
| `nonmatch_count` | integer | — | Theo loại dòng | 0 |
| `last_nonmatch_at` | timestamp with time zone | — | Theo loại dòng | — |
| `created_at` | timestamp with time zone | — | Theo loại dòng | CURRENT_TIMESTAMP |
| `submitted_at` | timestamp with time zone | — | Theo loại dòng | — |
| `verified_at` | timestamp with time zone | — | Theo loại dòng | — |
| `reference_expires_at` | timestamp with time zone | — | Theo loại dòng | — |
| `version` | bigint | — | Theo loại dòng | 0 |
| `document_digest` | character(64) | FK → identity_document_registry.document_digest | Theo loại dòng | — |
| `source_seller_verifications_verification_id` | bigint | — | Theo loại dòng | — |
| `verification_method` | character varying(30) | — | Theo loại dòng | — |
| `status` | character varying(20) | — | Theo loại dòng | — |
| `document_data` | jsonb | — | Theo loại dòng | — |
| `rejection_reason` | character varying(500) | — | Theo loại dòng | — |
| `reviewed_at` | timestamp with time zone | — | Theo loại dòng | — |
| `verified_by` | bigint | FK → users.user_id | Theo loại dòng | — |
| `record_type` | text | — | Có | 'ekyc_profiles'::text |
| `ekyc_decisions_history` | jsonb | — | Theo loại dòng | '[]'::jsonb |

### `ekyc_private_assets`

Metadata tài sản định danh riêng tư; không chứa sinh trắc học thô.

Nguồn dòng: `ekyc_private_assets`.

| Thuộc tính | Kiểu PostgreSQL | Khóa/quan hệ | Bắt buộc vật lý | Mặc định / generated |
|---|---|---|---|---|
| `asset_id` | bigint | PK | Có | nextval('og70_ekyc_private_assets_asset_id_seq'::regclass) |
| `profile_id` | bigint | FK → ekyc_profiles.profile_id | Theo loại dòng | — |
| `asset_type` | character varying(20) | — | Theo loại dòng | — |
| `object_key` | text | — | Theo loại dòng | — |
| `content_digest` | character(64) | — | Theo loại dòng | — |
| `captured_at` | timestamp with time zone | — | Theo loại dòng | CURRENT_TIMESTAMP |
| `expires_at` | timestamp with time zone | — | Theo loại dòng | — |
| `deleted_at` | timestamp with time zone | — | Theo loại dòng | — |
| `record_type` | text | — | Có | 'ekyc_private_assets'::text |

### `identity_document_registry`

Digest tài liệu và chủ sở hữu để chống đăng ký trùng.

Nguồn dòng: `identity_document_registry`.

| Thuộc tính | Kiểu PostgreSQL | Khóa/quan hệ | Bắt buộc vật lý | Mặc định / generated |
|---|---|---|---|---|
| `document_digest` | character(64) | PK | Có | — |
| `digest_key_version` | character varying(40) | — | Theo loại dòng | — |
| `user_id` | bigint | FK → ekyc_profiles.user_id; FK → users.user_id | Theo loại dòng | — |
| `first_profile_id` | bigint | FK → ekyc_profiles.profile_id | Theo loại dòng | — |
| `registered_at` | timestamp with time zone | — | Theo loại dòng | CURRENT_TIMESTAMP |
| `record_type` | text | — | Có | 'identity_document_registry'::text |

### `verification_attempts`

Các lần thử xác minh và metric cũ; phân biệt kết quả nghiệp vụ với lỗi dịch vụ.

Nguồn dòng: `ekyc_verification_attempts`, `quick_auth_attempts`, `seller_verification_metrics`.

| Thuộc tính | Kiểu PostgreSQL | Khóa/quan hệ | Bắt buộc vật lý | Mặc định / generated |
|---|---|---|---|---|
| `attempt_id` | uuid | PK | Có | gen_random_uuid() |
| `source_ekyc_verification_attempts_attempt_id` | bigint | — | Theo loại dòng | — |
| `profile_id` | bigint | FK → ekyc_profiles.profile_id | Theo loại dòng | — |
| `result` | character varying(20) | — | Theo loại dòng | — |
| `model_metadata` | jsonb | — | Theo loại dòng | — |
| `fallback_authorized_by` | bigint | FK → users.user_id | Theo loại dòng | — |
| `attempted_at` | timestamp with time zone | — | Theo loại dòng | — |
| `command_key` | character varying(150) | — | Theo loại dòng | — |
| `source_quick_auth_attempts_attempt_id` | uuid | — | Theo loại dòng | — |
| `session_id` | uuid | FK → auth_challenges.source_quick_auth_sessions_session_id | Theo loại dòng | — |
| `source_seller_verification_metrics_metric_id` | bigint | — | Theo loại dòng | — |
| `user_id` | bigint | FK → users.user_id | Theo loại dòng | — |
| `verification_id` | bigint | FK → ekyc_profiles.source_seller_verifications_verification_id | Theo loại dòng | — |
| `match_distance` | numeric(5,4) | — | Theo loại dòng | — |
| `processed_at` | timestamp with time zone | — | Theo loại dòng | — |
| `threshold_used` | numeric(5,4) | — | Theo loại dòng | — |
| `model_name` | character varying(80) | — | Theo loại dòng | — |
| `model_version` | character varying(80) | — | Theo loại dòng | — |
| `is_simulated` | boolean | — | Theo loại dòng | — |
| `record_type` | text | — | Có | 'ekyc_verification_attempts'::text |

### `seller_profiles`

Hồ sơ bán hàng, thông tin nhận tiền/nhận hàng và lịch sử xét duyệt.

Nguồn dòng: `seller_profiles`.
Metadata/lịch sử gộp: `seller_profile_decisions` → `seller_profile_decisions_history`.

| Thuộc tính | Kiểu PostgreSQL | Khóa/quan hệ | Bắt buộc vật lý | Mặc định / generated |
|---|---|---|---|---|
| `seller_profile_id` | bigint | PK | Có | nextval('og70_seller_profiles_seller_profile_id_seq'::regclass) |
| `user_id` | bigint | FK → addresses.user_id; FK → ekyc_profiles.user_id; FK → users.user_id | Theo loại dòng | — |
| `revision_no` | integer | — | Theo loại dòng | 1 |
| `state` | character varying(20) | — | Theo loại dòng | 'PENDING_EKYC'::character varying |
| `ekyc_profile_id` | bigint | FK → ekyc_profiles.profile_id | Theo loại dòng | — |
| `quick_auth_result_id` | uuid | FK → auth_challenges.source_quick_auth_results_result_id | Theo loại dòng | — |
| `pickup_address_id` | bigint | FK → addresses.address_id | Theo loại dòng | — |
| `bank_snapshot` | jsonb | — | Theo loại dòng | '{}'::jsonb |
| `updated_at` | timestamp with time zone | — | Theo loại dòng | CURRENT_TIMESTAMP |
| `record_type` | text | — | Có | 'seller_profiles'::text |
| `seller_profile_decisions_history` | jsonb | — | Theo loại dòng | '[]'::jsonb |

### `categories`

Danh mục sản phẩm.

Nguồn dòng: `categories`.

| Thuộc tính | Kiểu PostgreSQL | Khóa/quan hệ | Bắt buộc vật lý | Mặc định / generated |
|---|---|---|---|---|
| `category_id` | bigint | PK | Có | nextval('og70_categories_category_id_seq'::regclass) |
| `parent_category_id` | bigint | FK → categories.category_id | Theo loại dòng | — |
| `category_name` | character varying(100) | — | Theo loại dòng | — |
| `slug` | character varying(120) | — | Theo loại dòng | — |
| `description` | character varying(500) | — | Theo loại dòng | — |
| `is_active` | boolean | — | Theo loại dòng | true |
| `display_order` | integer | — | Theo loại dòng | 0 |
| `created_at` | timestamp with time zone | — | Theo loại dòng | CURRENT_TIMESTAMP |
| `updated_at` | timestamp with time zone | — | Theo loại dòng | — |
| `record_type` | text | — | Có | 'categories'::text |

### `products`

Tin đăng và tồn kho hiện tại.

Nguồn dòng: `products`.

| Thuộc tính | Kiểu PostgreSQL | Khóa/quan hệ | Bắt buộc vật lý | Mặc định / generated |
|---|---|---|---|---|
| `product_id` | bigint | PK; FK → product_categories.product_id; FK → product_revisions.product_id | Có | nextval('og70_products_product_id_seq'::regclass) |
| `seller_id` | bigint | FK → users.user_id | Theo loại dòng | — |
| `category_id` | bigint | FK → categories.category_id; FK → product_categories.category_id | Theo loại dòng | — |
| `title` | character varying(200) | — | Theo loại dòng | — |
| `description` | text | — | Theo loại dòng | — |
| `listed_price` | numeric(19,2) | — | Theo loại dòng | — |
| `currency` | character(3) | — | Theo loại dòng | 'VND'::bpchar |
| `condition` | character varying(20) | — | Theo loại dòng | — |
| `usage_duration` | character varying(100) | — | Theo loại dòng | — |
| `defects` | text | — | Theo loại dòng | — |
| `repair_history` | text | — | Theo loại dòng | — |
| `included_accessories` | text | — | Theo loại dòng | — |
| `location` | character varying(255) | — | Theo loại dòng | — |
| `status` | character varying(40) | — | Theo loại dòng | 'DRAFT'::character varying |
| `reserved_until` | timestamp with time zone | — | Theo loại dòng | — |
| `reserved_order_id` | bigint | — | Theo loại dòng | — |
| `version` | bigint | — | Theo loại dòng | 0 |
| `created_at` | timestamp with time zone | — | Theo loại dòng | CURRENT_TIMESTAMP |
| `updated_at` | timestamp with time zone | — | Theo loại dòng | — |
| `deleted_at` | timestamp with time zone | — | Theo loại dòng | — |
| `requires_buyer_ekyc` | boolean | — | Theo loại dòng | false |
| `content_revision` | bigint | — | Theo loại dòng | 1 |
| `workflow_model` | character varying(20) | — | Theo loại dòng | 'LEGACY_V14'::character varying |
| `quantity_total` | integer | — | Theo loại dòng | 1 |
| `quantity_held` | integer | — | Theo loại dòng | 0 |
| `quantity_sold` | integer | — | Theo loại dòng | 0 |
| `quantity_unavailable` | integer | — | Theo loại dòng | 0 |
| `quantity_available` | integer | — | Theo loại dòng | GENERATED: (((quantity_total - quantity_held) - quantity_sold) - quantity_unavailable) |
| `return_allowed` | boolean | — | Theo loại dòng | — |
| `delivery_options` | jsonb | — | Theo loại dòng | — |
| `current_revision_id` | bigint | FK → product_revisions.revision_id | Theo loại dòng | — |
| `public_revision_id` | bigint | FK → product_revisions.revision_id | Theo loại dòng | — |
| `record_type` | text | — | Có | 'products'::text |

### `product_categories`

Quan hệ sản phẩm với nhiều danh mục.

Nguồn dòng: `product_categories`.

| Thuộc tính | Kiểu PostgreSQL | Khóa/quan hệ | Bắt buộc vật lý | Mặc định / generated |
|---|---|---|---|---|
| `product_id` | bigint | PK; FK → products.product_id | Có | — |
| `category_id` | bigint | PK; FK → categories.category_id | Có | — |
| `record_type` | text | — | Có | 'product_categories'::text |

### `product_revisions`

Phiên bản tin, snapshot giá/số lượng và lịch sử kiểm tra/duyệt media.

Nguồn dòng: `product_revisions`.
Metadata/lịch sử gộp: `media_analysis_runs` → `media_analysis_runs_history`; `product_moderation_decisions` → `moderation_history`; `product_moderation_legacy_history` → `legacy_moderation_history`.

| Thuộc tính | Kiểu PostgreSQL | Khóa/quan hệ | Bắt buộc vật lý | Mặc định / generated |
|---|---|---|---|---|
| `revision_id` | bigint | PK | Có | nextval('og70_product_revisions_revision_id_seq'::regclass) |
| `product_id` | bigint | FK → products.product_id | Theo loại dòng | — |
| `revision_no` | bigint | — | Theo loại dòng | — |
| `provenance` | character varying(20) | — | Theo loại dòng | — |
| `state` | character varying(15) | — | Theo loại dòng | 'DRAFT'::character varying |
| `listed_unit_price` | numeric(19,2) | — | Theo loại dòng | — |
| `quantity` | integer | — | Theo loại dòng | — |
| `return_allowed` | boolean | — | Theo loại dòng | — |
| `delivery_options` | jsonb | — | Theo loại dòng | — |
| `content_snapshot` | jsonb | — | Theo loại dòng | — |
| `checklist_snapshot` | jsonb | — | Theo loại dòng | '[]'::jsonb |
| `created_at` | timestamp with time zone | — | Theo loại dòng | CURRENT_TIMESTAMP |
| `submitted_at` | timestamp with time zone | — | Theo loại dòng | — |
| `record_type` | text | — | Có | 'product_revisions'::text |
| `media_analysis_runs_history` | jsonb | — | Theo loại dòng | '[]'::jsonb |
| `moderation_history` | jsonb | — | Theo loại dòng | '[]'::jsonb |
| `legacy_moderation_history` | jsonb | — | Theo loại dòng | '[]'::jsonb |

### `product_media`

Ảnh/video cũ và media của từng phiên bản tin.

Nguồn dòng: `product_media`, `revision_media`.

| Thuộc tính | Kiểu PostgreSQL | Khóa/quan hệ | Bắt buộc vật lý | Mặc định / generated |
|---|---|---|---|---|
| `media_id` | bigint | PK | Có | nextval('og70_product_media_media_id_seq'::regclass) |
| `product_id` | bigint | FK → products.product_id | Theo loại dòng | — |
| `media_type` | character varying(10) | — | Theo loại dòng | — |
| `media_url` | text | — | Theo loại dòng | — |
| `display_order` | integer | — | Theo loại dòng | 0 |
| `created_at` | timestamp with time zone | — | Theo loại dòng | CURRENT_TIMESTAMP |
| `thumbnail_url` | text | — | Theo loại dòng | — |
| `duration_seconds` | integer | — | Theo loại dòng | — |
| `file_size_bytes` | bigint | — | Theo loại dòng | — |
| `cloudinary_public_id` | character varying(255) | — | Theo loại dòng | — |
| `mime_type` | character varying(50) | — | Theo loại dòng | — |
| `source_revision_media_revision_media_id` | bigint | — | Theo loại dòng | — |
| `revision_id` | bigint | FK → product_revisions.revision_id | Theo loại dòng | — |
| `object_key` | text | — | Theo loại dòng | — |
| `content_digest` | character(64) | — | Theo loại dòng | — |
| `checklist_items` | jsonb | — | Theo loại dòng | — |
| `record_type` | text | — | Có | 'product_media'::text |

### `listing_fee_charges`

Đánh giá phí và yêu cầu thu phí; record_type giữ riêng ý nghĩa của từng nghiệp vụ.

Nguồn dòng: `listing_fee_charges`, `listing_fee_assessments`.

| Thuộc tính | Kiểu PostgreSQL | Khóa/quan hệ | Bắt buộc vật lý | Mặc định / generated |
|---|---|---|---|---|
| `charge_id` | uuid | PK | Có | gen_random_uuid() |
| `assessment_id` | bigint | FK → listing_fee_charges.source_listing_fee_assessments_assessment_id | Theo loại dòng | — |
| `amount` | numeric(19,2) | — | Theo loại dòng | — |
| `state` | character varying(20) | — | Theo loại dòng | 'PENDING'::character varying |
| `created_at` | timestamp with time zone | — | Theo loại dòng | CURRENT_TIMESTAMP |
| `command_key` | character varying(150) | — | Theo loại dòng | — |
| `source_listing_fee_assessments_assessment_id` | bigint | — | Theo loại dòng | — |
| `product_id` | bigint | FK → product_revisions.product_id; FK → products.product_id | Theo loại dòng | — |
| `revision_id` | bigint | FK → product_revisions.revision_id | Theo loại dòng | — |
| `decision_id` | bigint | — | Theo loại dòng | — |
| `policy_id` | bigint | FK → business_policies.source_listing_fee_policies_policy_id | Theo loại dòng | — |
| `listed_total` | numeric(19,2) | — | Theo loại dòng | — |
| `previous_approved_total` | numeric(19,2) | — | Theo loại dòng | — |
| `computed_fee` | numeric(19,2) | — | Theo loại dòng | — |
| `cumulative_before` | numeric(19,2) | — | Theo loại dòng | — |
| `amount_due` | numeric(19,2) | — | Theo loại dòng | — |
| `assessed_at` | timestamp with time zone | — | Theo loại dòng | — |
| `record_type` | text | — | Có | 'listing_fee_charges'::text |

### `business_policies`

Các phiên bản chính sách phí hệ thống, phí tin, checklist, điểm và phạt.

Nguồn dòng: `system_fee_policies`, `listing_fee_policies`, `checklist_policies`, `reward_policies`, `penalty_policies`.

| Thuộc tính | Kiểu PostgreSQL | Khóa/quan hệ | Bắt buộc vật lý | Mặc định / generated |
|---|---|---|---|---|
| `policy_id` | bigint | PK | Có | nextval('og70_business_policies_policy_id_seq'::regclass) |
| `source_system_fee_policies_fee_policy_id` | bigint | — | Theo loại dòng | — |
| `policy_code` | character varying(60) | — | Theo loại dòng | — |
| `policy_name` | character varying(120) | — | Theo loại dòng | — |
| `buyer_fee_rate` | numeric(9,6) | — | Theo loại dòng | — |
| `buyer_fixed_fee` | numeric(19,2) | — | Theo loại dòng | — |
| `seller_fee_rate` | numeric(9,6) | — | Theo loại dòng | — |
| `seller_fixed_fee` | numeric(19,2) | — | Theo loại dòng | — |
| `minimum_buyer_fee` | numeric(19,2) | — | Theo loại dòng | — |
| `maximum_buyer_fee` | numeric(19,2) | — | Theo loại dòng | — |
| `minimum_seller_fee` | numeric(19,2) | — | Theo loại dòng | — |
| `maximum_seller_fee` | numeric(19,2) | — | Theo loại dòng | — |
| `rounding_scale` | smallint | — | Theo loại dòng | — |
| `rounding_mode` | character varying(20) | — | Theo loại dòng | — |
| `currency` | character(3) | — | Theo loại dòng | — |
| `status` | character varying(20) | — | Theo loại dòng | — |
| `version` | integer | — | Theo loại dòng | — |
| `effective_from` | timestamp with time zone | — | Theo loại dòng | — |
| `effective_to` | timestamp with time zone | — | Theo loại dòng | — |
| `created_at` | timestamp with time zone | — | Theo loại dòng | — |
| `created_by` | bigint | FK → users.user_id | Theo loại dòng | — |
| `source_listing_fee_policies_policy_id` | bigint | — | Theo loại dòng | — |
| `threshold_amount` | numeric(19,2) | — | Theo loại dòng | — |
| `fixed_below` | numeric(19,2) | — | Theo loại dòng | — |
| `rate_at_or_above` | numeric(8,6) | — | Theo loại dòng | — |
| `effective_until` | timestamp with time zone | — | Theo loại dòng | — |
| `source_checklist_policies_checklist_policy_id` | bigint | — | Theo loại dòng | — |
| `category_id` | bigint | FK → categories.category_id | Theo loại dòng | — |
| `policy_version` | integer | — | Theo loại dòng | — |
| `checklist` | jsonb | — | Theo loại dòng | — |
| `source_reward_policies_policy_id` | bigint | — | Theo loại dòng | — |
| `point_value` | numeric(19,2) | — | Theo loại dòng | — |
| `policy_snapshot` | jsonb | — | Theo loại dòng | — |
| `source_penalty_policies_policy_id` | bigint | — | Theo loại dòng | — |
| `thresholds` | jsonb | — | Theo loại dòng | — |
| `record_type` | text | — | Có | 'system_fee_policies'::text |

### `conversations`

Hội thoại cùng trạng thái người tham gia và mức sử dụng media.

Nguồn dòng: `conversations`.
Metadata/lịch sử gộp: `conversation_user_state` → `participant_state`; `chat_media_daily_quotas` → `media_quota_usage`.

| Thuộc tính | Kiểu PostgreSQL | Khóa/quan hệ | Bắt buộc vật lý | Mặc định / generated |
|---|---|---|---|---|
| `conversation_id` | bigint | PK; FK → messages.conversation_id | Có | nextval('og70_conversations_conversation_id_seq'::regclass) |
| `buyer_id` | bigint | FK → users.user_id | Theo loại dòng | — |
| `seller_id` | bigint | FK → products.seller_id; FK → users.user_id | Theo loại dòng | — |
| `product_id` | bigint | FK → products.product_id | Theo loại dòng | — |
| `created_at` | timestamp with time zone | — | Theo loại dòng | CURRENT_TIMESTAMP |
| `product_title_snapshot` | character varying(200) | — | Theo loại dòng | — |
| `product_price_at_start` | numeric(19,2) | — | Theo loại dòng | — |
| `product_thumbnail_snapshot` | text | — | Theo loại dòng | — |
| `currency` | character(3) | — | Theo loại dòng | — |
| `last_message_id` | bigint | FK → messages.message_id | Theo loại dòng | — |
| `last_activity_at` | timestamp with time zone | — | Theo loại dòng | — |
| `status` | character varying(20) | — | Theo loại dòng | 'ACTIVE'::character varying |
| `closed_at` | timestamp with time zone | — | Theo loại dòng | — |
| `record_type` | text | — | Có | 'conversations'::text |
| `participant_state` | jsonb | — | Theo loại dòng | '[]'::jsonb |
| `media_quota_usage` | jsonb | — | Theo loại dòng | '[]'::jsonb |

### `messages`

Tin nhắn.

Nguồn dòng: `messages`.

| Thuộc tính | Kiểu PostgreSQL | Khóa/quan hệ | Bắt buộc vật lý | Mặc định / generated |
|---|---|---|---|---|
| `message_id` | bigint | PK | Có | nextval('og70_messages_message_id_seq'::regclass) |
| `conversation_id` | bigint | FK → conversations.conversation_id; FK → messages.conversation_id; FK → offers.conversation_id | Theo loại dòng | — |
| `sender_id` | bigint | FK → users.user_id | Theo loại dòng | — |
| `content` | text | — | Theo loại dòng | — |
| `message_type` | character varying(20) | — | Theo loại dòng | 'TEXT'::character varying |
| `created_at` | timestamp with time zone | — | Theo loại dòng | CURRENT_TIMESTAMP |
| `client_message_id` | uuid | — | Theo loại dòng | — |
| `reply_to_message_id` | bigint | FK → messages.message_id | Theo loại dòng | — |
| `edited_at` | timestamp with time zone | — | Theo loại dòng | — |
| `deleted_at` | timestamp with time zone | — | Theo loại dòng | — |
| `offer_id` | bigint | FK → offers.offer_id | Theo loại dòng | — |
| `media_status` | character varying(20) | — | Theo loại dòng | 'ACTIVE'::character varying |
| `media_expires_at` | timestamp with time zone | — | Theo loại dòng | — |
| `cloudinary_public_id` | character varying(255) | — | Theo loại dòng | — |
| `media_metadata` | jsonb | — | Theo loại dòng | — |
| `record_type` | text | — | Có | 'messages'::text |

### `offers`

Đề nghị giá và trạng thái thương lượng.

Nguồn dòng: `offers`.

| Thuộc tính | Kiểu PostgreSQL | Khóa/quan hệ | Bắt buộc vật lý | Mặc định / generated |
|---|---|---|---|---|
| `offer_id` | bigint | PK | Có | nextval('og70_offers_offer_id_seq'::regclass) |
| `offered_item_price` | numeric(19,2) | — | Theo loại dòng | — |
| `status` | character varying(20) | — | Theo loại dòng | 'PENDING'::character varying |
| `expires_at` | timestamp with time zone | — | Theo loại dòng | — |
| `created_at` | timestamp with time zone | — | Theo loại dòng | CURRENT_TIMESTAMP |
| `responded_at` | timestamp with time zone | — | Theo loại dòng | — |
| `conversation_id` | bigint | FK → conversations.conversation_id; FK → offers.conversation_id | Theo loại dòng | — |
| `proposer_id` | bigint | — | Theo loại dòng | — |
| `responded_by` | bigint | — | Theo loại dòng | — |
| `parent_offer_id` | bigint | FK → offers.offer_id | Theo loại dòng | — |
| `fee_policy_id` | bigint | FK → business_policies.source_system_fee_policies_fee_policy_id | Theo loại dòng | — |
| `buyer_system_fee` | numeric(19,2) | — | Theo loại dòng | — |
| `seller_system_fee` | numeric(19,2) | — | Theo loại dòng | — |
| `buyer_subtotal` | numeric(19,2) | — | Theo loại dòng | — |
| `seller_proceeds` | numeric(19,2) | — | Theo loại dòng | — |
| `currency` | character(3) | — | Theo loại dòng | — |
| `version` | bigint | — | Theo loại dòng | 0 |
| `record_type` | text | — | Có | 'offers'::text |

### `cart_items`

Dòng sản phẩm trong giỏ, liên kết trực tiếp tới người dùng.

Nguồn dòng: `cart_items`.

| Thuộc tính | Kiểu PostgreSQL | Khóa/quan hệ | Bắt buộc vật lý | Mặc định / generated |
|---|---|---|---|---|
| `cart_id` | bigint | PK | Có | — |
| `product_id` | bigint | PK; FK → products.product_id | Có | — |
| `quantity` | integer | — | Theo loại dòng | 1 |
| `added_at` | timestamp with time zone | — | Theo loại dòng | CURRENT_TIMESTAMP |
| `record_type` | text | — | Có | 'cart_items'::text |
| `user_id` | bigint | FK → users.user_id | Theo loại dòng | — |

### `checkout_groups`

Nhóm thanh toán, snapshot địa chỉ, voucher đã dùng và xác nhận checkout 0đ.

Nguồn dòng: `checkout_groups`.
Metadata/lịch sử gộp: `checkout_redemptions` → `voucher_redemption`; `zero_checkout_confirmations` → `zero_confirmation`.

| Thuộc tính | Kiểu PostgreSQL | Khóa/quan hệ | Bắt buộc vật lý | Mặc định / generated |
|---|---|---|---|---|
| `group_id` | uuid | PK | Có | gen_random_uuid() |
| `buyer_id` | bigint | FK → users.user_id | Theo loại dòng | — |
| `command_key` | character varying(150) | — | Theo loại dòng | — |
| `request_digest` | character(64) | — | Theo loại dòng | — |
| `address_snapshot` | jsonb | — | Theo loại dòng | — |
| `delivery_method` | character varying(20) | — | Theo loại dòng | — |
| `carrier` | character varying(100) | — | Theo loại dòng | — |
| `payment_method` | character varying(30) | — | Theo loại dòng | — |
| `expected_total` | numeric(19,2) | — | Theo loại dòng | — |
| `state` | character varying(20) | — | Theo loại dòng | 'READY'::character varying |
| `created_at` | timestamp with time zone | — | Theo loại dòng | CURRENT_TIMESTAMP |
| `payment_due_at` | timestamp with time zone | — | Theo loại dòng | — |
| `record_type` | text | — | Có | 'checkout_groups'::text |
| `voucher_redemption` | jsonb | — | Theo loại dòng | '[]'::jsonb |
| `zero_confirmation` | jsonb | — | Theo loại dòng | '[]'::jsonb |

### `orders`

Đơn hàng; snapshot phí, địa chỉ, voucher cũ và liên kết hội thoại.

Nguồn dòng: `orders`.
Metadata/lịch sử gộp: `order_vouchers` → `legacy_voucher_history`; `conversation_order_links` → `conversation_links`.

| Thuộc tính | Kiểu PostgreSQL | Khóa/quan hệ | Bắt buộc vật lý | Mặc định / generated |
|---|---|---|---|---|
| `order_id` | bigint | PK | Có | nextval('og70_orders_order_id_seq'::regclass) |
| `checkout_group_id` | uuid | — | Theo loại dòng | — |
| `buyer_id` | bigint | FK → checkout_groups.buyer_id; FK → users.user_id | Theo loại dòng | — |
| `seller_id` | bigint | FK → users.user_id | Theo loại dòng | — |
| `source_address_id` | bigint | FK → addresses.address_id | Theo loại dòng | — |
| `shipping_recipient_name` | character varying(120) | — | Theo loại dòng | — |
| `shipping_phone_number` | character varying(20) | — | Theo loại dòng | — |
| `shipping_province` | character varying(100) | — | Theo loại dòng | — |
| `shipping_district` | character varying(100) | — | Theo loại dòng | — |
| `shipping_ward` | character varying(100) | — | Theo loại dòng | — |
| `shipping_detail_address` | character varying(255) | — | Theo loại dòng | — |
| `subtotal` | numeric(19,2) | — | Theo loại dòng | — |
| `shipping_fee` | numeric(19,2) | — | Theo loại dòng | 0 |
| `total_amount` | numeric(19,2) | — | Theo loại dòng | — |
| `currency` | character(3) | — | Theo loại dòng | 'VND'::bpchar |
| `status` | character varying(30) | — | Theo loại dòng | 'PAYMENT_PENDING'::character varying |
| `payment_due_at` | timestamp with time zone | — | Theo loại dòng | — |
| `version` | bigint | — | Theo loại dòng | 0 |
| `created_at` | timestamp with time zone | — | Theo loại dòng | CURRENT_TIMESTAMP |
| `updated_at` | timestamp with time zone | — | Theo loại dòng | — |
| `completed_at` | timestamp with time zone | — | Theo loại dòng | — |
| `cancelled_at` | timestamp with time zone | — | Theo loại dòng | — |
| `cancellation_reason` | character varying(500) | — | Theo loại dòng | — |
| `buyer_system_fee` | numeric(19,2) | — | Theo loại dòng | — |
| `seller_system_fee` | numeric(19,2) | — | Theo loại dòng | — |
| `seller_proceeds` | numeric(19,2) | — | Theo loại dòng | — |
| `voucher_discount_amount` | numeric(19,2) | — | Theo loại dòng | 0 |
| `shipping_discount_amount` | numeric(19,2) | — | Theo loại dòng | 0 |
| `sponsor_type` | character varying(20) | — | Theo loại dòng | 'PLATFORM'::character varying |
| `workflow_model` | character varying(20) | — | Theo loại dòng | 'LEGACY_V14'::character varying |
| `group_id` | uuid | FK → checkout_groups.group_id | Theo loại dòng | — |
| `return_allowed` | boolean | — | Theo loại dòng | — |
| `delivery_method` | character varying(20) | — | Theo loại dòng | — |
| `carrier_snapshot` | character varying(100) | — | Theo loại dòng | — |
| `handover_day` | date | — | Theo loại dòng | — |
| `points_discount_amount` | numeric(19,2) | — | Theo loại dòng | 0 |
| `paid_confirmed_at` | timestamp with time zone | — | Theo loại dòng | — |
| `seller_accept_due_at` | timestamp with time zone | — | Theo loại dòng | — |
| `valid_delivered_at` | timestamp with time zone | — | Theo loại dòng | — |
| `received_at` | timestamp with time zone | — | Theo loại dòng | — |
| `return_due_at` | timestamp with time zone | — | Theo loại dòng | — |
| `early_completed_at` | timestamp with time zone | — | Theo loại dòng | — |
| `early_completion_warning_version` | character varying(50) | — | Theo loại dòng | — |
| `purchase_shipping_used_at` | timestamp with time zone | — | Theo loại dòng | — |
| `record_type` | text | — | Có | 'orders'::text |
| `legacy_voucher_history` | jsonb | — | Theo loại dòng | '[]'::jsonb |
| `conversation_links` | jsonb | — | Theo loại dòng | '[]'::jsonb |

### `order_items`

Dòng hàng và snapshot giá/số lượng; lưu lịch sử phân bổ giảm giá.

Nguồn dòng: `order_items`.
Metadata/lịch sử gộp: `discount_allocations` → `discount_history`.

| Thuộc tính | Kiểu PostgreSQL | Khóa/quan hệ | Bắt buộc vật lý | Mặc định / generated |
|---|---|---|---|---|
| `order_item_id` | bigint | PK | Có | nextval('og70_order_items_order_item_id_seq'::regclass) |
| `order_id` | bigint | FK → orders.order_id | Theo loại dòng | — |
| `product_id` | bigint | FK → product_revisions.product_id; FK → products.product_id | Theo loại dòng | — |
| `product_title` | character varying(200) | — | Theo loại dòng | — |
| `quantity` | integer | — | Theo loại dòng | 1 |
| `agreed_price` | numeric(19,2) | — | Theo loại dòng | — |
| `buyer_line_total` | numeric(19,2) | — | Theo loại dòng | — |
| `listed_price` | numeric(19,2) | — | Theo loại dòng | — |
| `buyer_system_fee` | numeric(19,2) | — | Theo loại dòng | — |
| `seller_system_fee` | numeric(19,2) | — | Theo loại dòng | — |
| `seller_line_proceeds` | numeric(19,2) | — | Theo loại dòng | — |
| `fee_policy_id` | bigint | FK → business_policies.source_system_fee_policies_fee_policy_id | Theo loại dòng | — |
| `accepted_offer_id` | bigint | FK → offers.offer_id | Theo loại dòng | — |
| `pricing_source` | character varying(20) | — | Theo loại dòng | 'LIST_PRICE'::character varying |
| `workflow_model` | character varying(20) | — | Theo loại dòng | 'LEGACY_V14'::character varying |
| `product_revision_id` | bigint | FK → product_revisions.revision_id | Theo loại dòng | — |
| `return_allowed` | boolean | — | Theo loại dòng | — |
| `voucher_allocation` | numeric(19,2) | — | Theo loại dòng | 0 |
| `points_allocation` | numeric(19,2) | — | Theo loại dòng | 0 |
| `record_type` | text | — | Có | 'order_items'::text |
| `discount_history` | jsonb | — | Theo loại dòng | '[]'::jsonb |
| `conversation_id` | bigint | FK → conversations.conversation_id | Theo loại dòng | — |

### `inventory_reservations`

Giữ/tiêu thụ/giải phóng tồn kho, có khóa lệnh chống lặp.

Nguồn dòng: `inventory_reservations`.

| Thuộc tính | Kiểu PostgreSQL | Khóa/quan hệ | Bắt buộc vật lý | Mặc định / generated |
|---|---|---|---|---|
| `reservation_id` | uuid | PK | Có | gen_random_uuid() |
| `product_id` | bigint | FK → order_items.product_id; FK → products.product_id | Theo loại dòng | — |
| `order_item_id` | bigint | FK → order_items.order_item_id | Theo loại dòng | — |
| `quantity` | integer | — | Theo loại dòng | — |
| `state` | character varying(15) | — | Theo loại dòng | 'HELD'::character varying |
| `created_at` | timestamp with time zone | — | Theo loại dòng | CURRENT_TIMESTAMP |
| `expires_at` | timestamp with time zone | — | Theo loại dòng | — |
| `changed_at` | timestamp with time zone | — | Theo loại dòng | CURRENT_TIMESTAMP |
| `command_key` | character varying(150) | — | Theo loại dòng | — |
| `record_type` | text | — | Có | 'inventory_reservations'::text |

### `vouchers`

Voucher và các phiên bản chính sách; lịch sử thu hồi gắn với phiên bản.

Nguồn dòng: `vouchers`, `voucher_revisions`.
Metadata/lịch sử gộp: `voucher_revision_revocations` → `revocation_history`.

| Thuộc tính | Kiểu PostgreSQL | Khóa/quan hệ | Bắt buộc vật lý | Mặc định / generated |
|---|---|---|---|---|
| `voucher_id` | bigint | PK | Có | nextval('og70_vouchers_voucher_id_seq'::regclass) |
| `code` | character varying(50) | — | Theo loại dòng | — |
| `title` | character varying(200) | — | Theo loại dòng | — |
| `description` | text | — | Theo loại dòng | — |
| `voucher_type` | character varying(30) | — | Theo loại dòng | — |
| `discount_type` | character varying(20) | — | Theo loại dòng | — |
| `discount_value` | numeric(19,2) | — | Theo loại dòng | — |
| `max_discount_amount` | numeric(19,2) | — | Theo loại dòng | — |
| `min_order_amount` | numeric(19,2) | — | Theo loại dòng | 0 |
| `sponsor_type` | character varying(20) | — | Theo loại dòng | 'PLATFORM'::character varying |
| `seller_id` | bigint | FK → users.user_id | Theo loại dòng | — |
| `total_usage_limit` | integer | — | Theo loại dòng | — |
| `current_usage_count` | integer | — | Theo loại dòng | 0 |
| `max_usage_per_user` | integer | — | Theo loại dòng | 1 |
| `start_time` | timestamp with time zone | — | Theo loại dòng | — |
| `end_time` | timestamp with time zone | — | Theo loại dòng | — |
| `is_active` | boolean | — | Theo loại dòng | true |
| `version` | bigint | — | Theo loại dòng | 0 |
| `created_at` | timestamp with time zone | — | Theo loại dòng | CURRENT_TIMESTAMP |
| `updated_at` | timestamp with time zone | — | Theo loại dòng | — |
| `source_voucher_revisions_revision_id` | bigint | — | Theo loại dòng | — |
| `parent_voucher_id` | bigint | FK → vouchers.voucher_id | Theo loại dòng | — |
| `revision_no` | integer | — | Theo loại dòng | — |
| `policy_snapshot` | jsonb | — | Theo loại dòng | — |
| `scope` | character varying(20) | — | Theo loại dòng | — |
| `claim_limit` | integer | — | Theo loại dòng | — |
| `per_user_claim_limit` | integer | — | Theo loại dòng | — |
| `valid_from` | timestamp with time zone | — | Theo loại dòng | — |
| `valid_until` | timestamp with time zone | — | Theo loại dòng | — |
| `created_by` | bigint | FK → users.user_id | Theo loại dòng | — |
| `benefit_component` | character varying(10) | — | Theo loại dòng | — |
| `min_eligible_amount` | numeric(19,2) | — | Theo loại dòng | — |
| `record_type` | text | — | Có | 'vouchers'::text |
| `revocation_history` | jsonb | — | Theo loại dòng | '[]'::jsonb |

### `voucher_products`

Phạm vi sản phẩm áp dụng cho phiên bản voucher.

Nguồn dòng: `voucher_revision_products`.

| Thuộc tính | Kiểu PostgreSQL | Khóa/quan hệ | Bắt buộc vật lý | Mặc định / generated |
|---|---|---|---|---|
| `revision_id` | bigint | PK; FK → vouchers.source_voucher_revisions_revision_id | Có | — |
| `product_id` | bigint | PK; FK → products.product_id | Có | — |
| `record_type` | text | — | Có | 'voucher_revision_products'::text |

### `voucher_grants`

Voucher đã cấp cho người dùng và lịch sử sử dụng.

Nguồn dòng: `voucher_grants`.
Metadata/lịch sử gộp: `voucher_grant_events` → `grant_history`.

| Thuộc tính | Kiểu PostgreSQL | Khóa/quan hệ | Bắt buộc vật lý | Mặc định / generated |
|---|---|---|---|---|
| `grant_id` | uuid | PK | Có | gen_random_uuid() |
| `revision_id` | bigint | FK → vouchers.source_voucher_revisions_revision_id | Theo loại dòng | — |
| `user_id` | bigint | FK → users.user_id | Theo loại dòng | — |
| `source` | character varying(20) | — | Theo loại dòng | — |
| `issued_by` | bigint | FK → users.user_id | Theo loại dòng | — |
| `reason` | text | — | Theo loại dòng | — |
| `state` | character varying(15) | — | Theo loại dòng | 'AVAILABLE'::character varying |
| `granted_at` | timestamp with time zone | — | Theo loại dòng | CURRENT_TIMESTAMP |
| `expires_at` | timestamp with time zone | — | Theo loại dòng | — |
| `command_key` | character varying(150) | — | Theo loại dòng | — |
| `record_type` | text | — | Có | 'voucher_grants'::text |
| `grant_history` | jsonb | — | Theo loại dòng | '[]'::jsonb |

### `reward_ledger`

Sổ biến động điểm; số dư trên users được suy ra từ tài khoản điểm được bảo vệ.

Nguồn dòng: `reward_ledger`.

| Thuộc tính | Kiểu PostgreSQL | Khóa/quan hệ | Bắt buộc vật lý | Mặc định / generated |
|---|---|---|---|---|
| `entry_id` | uuid | PK | Có | gen_random_uuid() |
| `user_id` | bigint | FK → checkout_groups.buyer_id | Theo loại dòng | — |
| `policy_id` | bigint | FK → business_policies.source_reward_policies_policy_id | Theo loại dòng | — |
| `entry_type` | character varying(20) | — | Theo loại dòng | — |
| `points_delta` | bigint | — | Theo loại dòng | — |
| `order_id` | bigint | FK → orders.order_id | Theo loại dòng | — |
| `group_id` | uuid | FK → checkout_groups.group_id | Theo loại dòng | — |
| `actor_id` | bigint | FK → users.user_id | Theo loại dòng | — |
| `reason` | text | — | Theo loại dòng | — |
| `created_at` | timestamp with time zone | — | Theo loại dòng | CURRENT_TIMESTAMP |
| `command_key` | character varying(150) | — | Theo loại dòng | — |
| `record_type` | text | — | Có | 'reward_ledger'::text |

### `payments`

Thanh toán cũ và intent theo mục đích; không coi intent là tiền đã nhận.

Nguồn dòng: `payments`, `payment_intents`.

| Thuộc tính | Kiểu PostgreSQL | Khóa/quan hệ | Bắt buộc vật lý | Mặc định / generated |
|---|---|---|---|---|
| `payment_id` | bigint | PK | Có | nextval('og70_payments_payment_id_seq'::regclass) |
| `order_id` | bigint | FK → orders.order_id | Theo loại dòng | — |
| `amount` | numeric(19,2) | — | Theo loại dòng | — |
| `currency` | character(3) | — | Theo loại dòng | 'VND'::bpchar |
| `payment_method` | character varying(30) | — | Theo loại dòng | — |
| `transaction_code` | character varying(100) | — | Theo loại dòng | — |
| `status` | character varying(20) | — | Theo loại dòng | 'PENDING'::character varying |
| `paid_at` | timestamp with time zone | — | Theo loại dòng | — |
| `version` | bigint | — | Theo loại dòng | 0 |
| `held_at` | timestamp with time zone | — | Theo loại dòng | — |
| `released_at` | timestamp with time zone | — | Theo loại dòng | — |
| `refund_amount` | numeric(19,2) | — | Theo loại dòng | — |
| `refund_reason` | character varying(500) | — | Theo loại dòng | — |
| `refunded_at` | timestamp with time zone | — | Theo loại dòng | — |
| `failure_reason` | character varying(500) | — | Theo loại dòng | — |
| `created_at` | timestamp with time zone | — | Theo loại dòng | CURRENT_TIMESTAMP |
| `updated_at` | timestamp with time zone | — | Theo loại dòng | — |
| `source_payment_intents_intent_id` | uuid | — | Theo loại dòng | — |
| `purpose` | character varying(20) | — | Theo loại dòng | — |
| `group_id` | uuid | FK → checkout_groups.group_id | Theo loại dòng | — |
| `listing_charge_id` | uuid | FK → listing_fee_charges.charge_id | Theo loại dòng | — |
| `expected_amount` | numeric(19,2) | — | Theo loại dòng | — |
| `state` | character varying(20) | — | Theo loại dòng | — |
| `deadline` | timestamp with time zone | — | Theo loại dòng | — |
| `command_key` | character varying(150) | — | Theo loại dòng | — |
| `record_type` | text | — | Có | 'payments'::text |

### `payment_attempts`

Lần thử thanh toán tổng quát và VNPay.

Nguồn dòng: `payment_attempts`, `vnpay_payment_attempts`.

| Thuộc tính | Kiểu PostgreSQL | Khóa/quan hệ | Bắt buộc vật lý | Mặc định / generated |
|---|---|---|---|---|
| `attempt_id` | bigint | PK | Có | nextval('og70_payment_attempts_attempt_id_seq'::regclass) |
| `order_id` | bigint | FK → orders.order_id; FK → payments.order_id | Theo loại dòng | — |
| `payment_id` | bigint | FK → payments.payment_id | Theo loại dòng | — |
| `txn_ref` | character varying(100) | — | Theo loại dòng | — |
| `amount` | numeric(19,2) | — | Theo loại dòng | — |
| `currency` | character(3) | — | Theo loại dòng | 'VND'::bpchar |
| `provider` | character varying(30) | — | Theo loại dòng | 'VNPAY'::character varying |
| `status` | character varying(35) | — | Theo loại dòng | 'PENDING'::character varying |
| `vnp_amount_raw` | bigint | — | Theo loại dòng | — |
| `vnp_amount` | numeric(19,2) | — | Theo loại dòng | — |
| `vnp_transaction_no` | character varying(100) | — | Theo loại dòng | — |
| `vnp_response_code` | character varying(10) | — | Theo loại dòng | — |
| `vnp_transaction_status` | character varying(10) | — | Theo loại dòng | — |
| `vnp_bank_code` | character varying(50) | — | Theo loại dòng | — |
| `vnp_pay_date` | character varying(20) | — | Theo loại dòng | — |
| `failure_reason` | character varying(500) | — | Theo loại dòng | — |
| `ipn_count` | integer | — | Theo loại dòng | 0 |
| `first_ipn_received_at` | timestamp with time zone | — | Theo loại dòng | — |
| `last_ipn_received_at` | timestamp with time zone | — | Theo loại dòng | — |
| `created_at` | timestamp with time zone | — | Theo loại dòng | CURRENT_TIMESTAMP |
| `updated_at` | timestamp with time zone | — | Theo loại dòng | — |
| `intent_id` | uuid | FK → payments.source_payment_intents_intent_id | Theo loại dòng | — |
| `source_vnpay_payment_attempts_transaction_ref` | character varying(100) | — | Theo loại dòng | — |
| `expires_at` | timestamp with time zone | — | Theo loại dòng | — |
| `processed_at` | timestamp with time zone | — | Theo loại dòng | — |
| `provider_transaction_no` | character varying(100) | — | Theo loại dòng | — |
| `outcome` | character varying(20) | — | Theo loại dòng | — |
| `response_code` | character varying(2) | — | Theo loại dòng | — |
| `transaction_status` | character varying(2) | — | Theo loại dòng | — |
| `record_type` | text | — | Có | 'payment_attempts'::text |

### `payment_events`

Receipt callback thô và sự kiện đã phân tích, phân loại riêng trong cùng sổ.

Nguồn dòng: `payment_ipn_raw_receipts`, `payment_ipn_events`.

| Thuộc tính | Kiểu PostgreSQL | Khóa/quan hệ | Bắt buộc vật lý | Mặc định / generated |
|---|---|---|---|---|
| `event_id` | bigint | PK | Có | nextval('og70_payment_events_event_id_seq'::regclass) |
| `source_payment_ipn_raw_receipts_receipt_id` | bigint | — | Theo loại dòng | — |
| `raw_query_params` | text | — | Theo loại dòng | — |
| `raw_query_original_length` | integer | — | Theo loại dòng | — |
| `raw_query_truncated` | boolean | — | Theo loại dòng | — |
| `raw_query_sanitized` | boolean | — | Theo loại dòng | — |
| `raw_query_sha256` | character(64) | — | Theo loại dòng | — |
| `ip_address` | character varying(50) | — | Theo loại dòng | — |
| `received_at` | timestamp with time zone | — | Theo loại dòng | — |
| `vnp_txn_ref` | character varying(100) | — | Theo loại dòng | — |
| `vnp_transaction_no` | character varying(100) | — | Theo loại dòng | — |
| `vnp_amount_text` | text | — | Theo loại dòng | — |
| `verification_status` | character varying(30) | — | Theo loại dòng | — |
| `processing_status` | character varying(30) | — | Theo loại dòng | — |
| `processing_error` | character varying(200) | — | Theo loại dòng | — |
| `source_payment_ipn_events_event_id` | bigint | — | Theo loại dòng | — |
| `receipt_id` | bigint | FK → payment_events.source_payment_ipn_raw_receipts_receipt_id | Theo loại dòng | — |
| `attempt_id` | bigint | FK → payment_attempts.attempt_id | Theo loại dòng | — |
| `order_id` | bigint | FK → payment_attempts.order_id | Theo loại dòng | — |
| `payment_id` | bigint | FK → payment_attempts.payment_id | Theo loại dòng | — |
| `txn_ref` | character varying(100) | — | Theo loại dòng | — |
| `vnp_response_code` | character varying(10) | — | Theo loại dòng | — |
| `vnp_transaction_status` | character varying(10) | — | Theo loại dòng | — |
| `vnp_curr_code` | character varying(10) | — | Theo loại dòng | — |
| `vnp_amount_raw` | bigint | — | Theo loại dòng | — |
| `vnp_amount` | numeric(19,2) | — | Theo loại dòng | — |
| `vnp_bank_code` | character varying(50) | — | Theo loại dòng | — |
| `vnp_bank_tran_no` | character varying(100) | — | Theo loại dòng | — |
| `vnp_card_type` | character varying(20) | — | Theo loại dòng | — |
| `vnp_pay_date` | character varying(20) | — | Theo loại dòng | — |
| `processing_outcome` | character varying(50) | — | Theo loại dòng | — |
| `intent_id` | uuid | FK → payment_attempts.intent_id; FK → payments.source_payment_intents_intent_id | Theo loại dòng | — |
| `record_type` | text | — | Có | 'payment_ipn_raw_receipts'::text |

### `payment_confirmations`

Xác nhận tiền có kiểm chứng và receipt phí tin.

Nguồn dòng: `payment_confirmations`, `listing_fee_receipts`.

| Thuộc tính | Kiểu PostgreSQL | Khóa/quan hệ | Bắt buộc vật lý | Mặc định / generated |
|---|---|---|---|---|
| `confirmation_id` | uuid | PK | Có | gen_random_uuid() |
| `intent_id` | uuid | FK → payment_attempts.intent_id; FK → payments.source_payment_intents_intent_id | Theo loại dòng | — |
| `attempt_id` | bigint | FK → payment_attempts.attempt_id | Theo loại dòng | — |
| `ipn_event_id` | bigint | FK → payment_events.source_payment_ipn_events_event_id | Theo loại dòng | — |
| `provider` | character varying(30) | — | Theo loại dòng | — |
| `provider_transaction_id` | character varying(150) | — | Theo loại dòng | — |
| `amount` | numeric(19,2) | — | Theo loại dòng | — |
| `disposition` | character varying(20) | — | Theo loại dòng | 'ALLOCATABLE'::character varying |
| `confirmed_at` | timestamp with time zone | — | Theo loại dòng | CURRENT_TIMESTAMP |
| `source_listing_fee_receipts_receipt_id` | uuid | — | Theo loại dòng | — |
| `charge_id` | uuid | FK → listing_fee_charges.charge_id | Theo loại dòng | — |
| `record_type` | text | — | Có | 'payment_confirmations'::text |

### `order_fund_components`

Phân bổ tiền vào đơn và các phần tiền hàng/ship/phí/trợ giá.

Nguồn dòng: `order_fund_components`, `payment_allocations`.

| Thuộc tính | Kiểu PostgreSQL | Khóa/quan hệ | Bắt buộc vật lý | Mặc định / generated |
|---|---|---|---|---|
| `component_id` | uuid | PK | Có | gen_random_uuid() |
| `order_id` | bigint | FK → order_fund_components.order_id; FK → orders.order_id | Theo loại dòng | — |
| `allocation_id` | uuid | FK → order_fund_components.source_payment_allocations_allocation_id | Theo loại dòng | — |
| `late_confirmation_id` | uuid | FK → payment_confirmations.confirmation_id | Theo loại dòng | — |
| `component_type` | character varying(30) | — | Theo loại dòng | — |
| `confirmed_amount` | numeric(19,2) | — | Theo loại dòng | — |
| `reserved_amount` | numeric(19,2) | — | Theo loại dòng | 0 |
| `refunded_amount` | numeric(19,2) | — | Theo loại dòng | 0 |
| `released_amount` | numeric(19,2) | — | Theo loại dòng | 0 |
| `funding_reference` | character varying(150) | — | Theo loại dòng | — |
| `confirmed_at` | timestamp with time zone | — | Theo loại dòng | CURRENT_TIMESTAMP |
| `version` | bigint | — | Theo loại dòng | 0 |
| `source_payment_allocations_allocation_id` | uuid | — | Theo loại dòng | — |
| `intent_id` | uuid | FK → payment_confirmations.intent_id; FK → payments.source_payment_intents_intent_id | Theo loại dòng | — |
| `confirmation_id` | uuid | FK → payment_confirmations.confirmation_id | Theo loại dòng | — |
| `amount` | numeric(19,2) | — | Theo loại dòng | — |
| `record_type` | text | — | Có | 'order_fund_components'::text |

### `settlement_operations`

Lệnh hoàn tiền/giải ngân cùng trạng thái thực hiện và chống lặp.

Nguồn dòng: `settlement_operations`.

| Thuộc tính | Kiểu PostgreSQL | Khóa/quan hệ | Bắt buộc vật lý | Mặc định / generated |
|---|---|---|---|---|
| `operation_id` | uuid | PK | Có | gen_random_uuid() |
| `component_id` | uuid | FK → order_fund_components.component_id | Theo loại dòng | — |
| `operation_type` | character varying(25) | — | Theo loại dòng | — |
| `amount` | numeric(19,2) | — | Theo loại dòng | — |
| `cause_type` | character varying(20) | — | Theo loại dòng | — |
| `case_decision_id` | bigint | FK → case_actions.source_case_decisions_decision_id | Theo loại dòng | — |
| `recipient_user_id` | bigint | FK → users.user_id | Theo loại dòng | — |
| `state` | character varying(15) | — | Theo loại dòng | 'REQUESTED'::character varying |
| `requested_at` | timestamp with time zone | — | Theo loại dòng | CURRENT_TIMESTAMP |
| `confirmed_at` | timestamp with time zone | — | Theo loại dòng | — |
| `provider` | character varying(30) | — | Theo loại dòng | — |
| `provider_operation_id` | character varying(150) | — | Theo loại dòng | — |
| `failure_reason` | text | — | Theo loại dòng | — |
| `command_key` | character varying(150) | — | Theo loại dòng | — |
| `record_type` | text | — | Có | 'settlement_operations'::text |

### `shipments`

Chặng giao đi/trả về, phí và hạn giao.

Nguồn dòng: `shipments`.

| Thuộc tính | Kiểu PostgreSQL | Khóa/quan hệ | Bắt buộc vật lý | Mặc định / generated |
|---|---|---|---|---|
| `shipment_id` | bigint | PK | Có | nextval('og70_shipments_shipment_id_seq'::regclass) |
| `order_id` | bigint | FK → orders.order_id | Theo loại dòng | — |
| `carrier` | character varying(100) | — | Theo loại dòng | — |
| `tracking_number` | character varying(100) | — | Theo loại dòng | — |
| `status` | character varying(20) | — | Theo loại dòng | 'PENDING'::character varying |
| `shipped_at` | timestamp with time zone | — | Theo loại dòng | — |
| `delivered_at` | timestamp with time zone | — | Theo loại dòng | — |
| `updated_at` | timestamp with time zone | — | Theo loại dòng | — |
| `leg` | character varying(15) | — | Theo loại dòng | 'OUTBOUND'::character varying |
| `workflow_model` | character varying(20) | — | Theo loại dòng | 'LEGACY_V14'::character varying |
| `return_case_id` | bigint | FK → cases.case_id | Theo loại dòng | — |
| `delivery_method` | character varying(20) | — | Theo loại dòng | — |
| `fee_snapshot` | numeric(19,2) | — | Theo loại dòng | — |
| `fee_payer` | character varying(10) | — | Theo loại dòng | — |
| `initiated_at` | timestamp with time zone | — | Theo loại dòng | CURRENT_TIMESTAMP |
| `pickup_expected_at` | timestamp with time zone | — | Theo loại dòng | — |
| `pickup_deadline` | timestamp with time zone | — | Theo loại dòng | — |
| `first_failure_at` | timestamp with time zone | — | Theo loại dòng | — |
| `delivery_retry_due_at` | timestamp with time zone | — | Theo loại dòng | — |
| `valid_delivery_at` | timestamp with time zone | — | Theo loại dòng | — |
| `otp_digest` | character varying(128) | — | Theo loại dòng | — |
| `otp_expires_at` | timestamp with time zone | — | Theo loại dòng | — |
| `otp_attempt_count` | integer | — | Theo loại dòng | 0 |
| `otp_consumed_at` | timestamp with time zone | — | Theo loại dòng | — |
| `record_type` | text | — | Có | 'shipments'::text |

### `shipment_events`

Sự kiện vận chuyển, lần lấy hàng và xác nhận giao trực tiếp.

Nguồn dòng: `shipment_events`, `pickup_attempts`, `handover_confirmations`.

| Thuộc tính | Kiểu PostgreSQL | Khóa/quan hệ | Bắt buộc vật lý | Mặc định / generated |
|---|---|---|---|---|
| `event_id` | uuid | PK | Có | gen_random_uuid() |
| `shipment_id` | bigint | FK → shipments.shipment_id | Theo loại dòng | — |
| `source` | character varying(30) | — | Theo loại dòng | — |
| `source_event_id` | character varying(150) | — | Theo loại dòng | — |
| `event_type` | character varying(30) | — | Theo loại dòng | — |
| `occurred_at` | timestamp with time zone | — | Theo loại dòng | — |
| `received_at` | timestamp with time zone | — | Theo loại dòng | CURRENT_TIMESTAMP |
| `metadata` | jsonb | — | Theo loại dòng | '{}'::jsonb |
| `source_pickup_attempts_attempt_id` | uuid | — | Theo loại dòng | — |
| `attempt_no` | integer | — | Theo loại dòng | — |
| `attempted_at` | timestamp with time zone | — | Theo loại dòng | — |
| `attempt_day` | date | — | Theo loại dòng | GENERATED: ((attempted_at AT TIME ZONE 'Asia/Saigon'::text))::date |
| `outcome` | character varying(15) | — | Theo loại dòng | — |
| `responsibility` | character varying(15) | — | Theo loại dòng | — |
| `reason` | text | — | Theo loại dòng | — |
| `pickup_attempts_source_event_id` | uuid | FK → shipment_events.event_id | Theo loại dòng | — |
| `source_handover_confirmations_confirmation_id` | uuid | — | Theo loại dòng | — |
| `party` | character varying(10) | — | Theo loại dòng | — |
| `user_id` | bigint | FK → users.user_id | Theo loại dòng | — |
| `confirmed_at` | timestamp with time zone | — | Theo loại dòng | — |
| `command_key` | character varying(150) | — | Theo loại dòng | — |
| `record_type` | text | — | Có | 'shipment_events'::text |

### `cases`

Hồ sơ trả hàng, khiếu nại, báo cáo và đối soát thanh toán.

Nguồn dòng: `cases`, `complaints`, `reports`, `payment_reconciliation_cases`.

| Thuộc tính | Kiểu PostgreSQL | Khóa/quan hệ | Bắt buộc vật lý | Mặc định / generated |
|---|---|---|---|---|
| `case_id` | bigint | PK | Có | nextval('og70_cases_case_id_seq'::regclass) |
| `case_type` | character varying(40) | — | Theo loại dòng | — |
| `order_id` | bigint | FK → orders.order_id | Theo loại dòng | — |
| `product_id` | bigint | FK → products.product_id | Theo loại dòng | — |
| `listing_charge_id` | uuid | FK → listing_fee_charges.charge_id | Theo loại dòng | — |
| `payment_intent_id` | uuid | FK → payments.source_payment_intents_intent_id | Theo loại dòng | — |
| `target_user_id` | bigint | FK → users.user_id | Theo loại dòng | — |
| `created_by` | bigint | FK → users.user_id | Theo loại dòng | — |
| `assigned_to` | bigint | FK → users.user_id | Theo loại dòng | — |
| `description` | text | — | Theo loại dòng | — |
| `state` | character varying(20) | — | Theo loại dòng | 'OPEN'::character varying |
| `created_at` | timestamp with time zone | — | Theo loại dòng | CURRENT_TIMESTAMP |
| `approved_at` | timestamp with time zone | — | Theo loại dòng | — |
| `valid_returned_at` | timestamp with time zone | — | Theo loại dòng | — |
| `seller_response_due_at` | timestamp with time zone | — | Theo loại dòng | — |
| `closed_at` | timestamp with time zone | — | Theo loại dòng | — |
| `last_related_completion_at` | timestamp with time zone | — | Theo loại dòng | — |
| `command_key` | character varying(150) | — | Theo loại dòng | — |
| `source_complaints_complaint_id` | bigint | — | Theo loại dòng | — |
| `reason` | character varying(50) | — | Theo loại dòng | — |
| `evidence` | jsonb | — | Theo loại dòng | — |
| `status` | character varying(20) | — | Theo loại dòng | — |
| `resolution` | character varying(30) | — | Theo loại dòng | — |
| `resolution_note` | text | — | Theo loại dòng | — |
| `resolved_by` | bigint | FK → users.user_id | Theo loại dòng | — |
| `refund_amount` | numeric(19,2) | — | Theo loại dòng | — |
| `resolved_at` | timestamp with time zone | — | Theo loại dòng | — |
| `evidence_cleanup_due_at` | timestamp with time zone | — | Theo loại dòng | — |
| `evidence_cleaned_at` | timestamp with time zone | — | Theo loại dòng | — |
| `source_reports_report_id` | bigint | — | Theo loại dòng | — |
| `reporter_id` | bigint | FK → users.user_id | Theo loại dòng | — |
| `reported_user_id` | bigint | FK → users.user_id | Theo loại dòng | — |
| `message_id` | bigint | FK → messages.message_id | Theo loại dòng | — |
| `incident_key` | character varying(150) | — | Theo loại dòng | — |
| `evidence_snapshot` | jsonb | — | Theo loại dòng | — |
| `source_payment_reconciliation_cases_case_id` | bigint | — | Theo loại dòng | — |
| `payment_id` | bigint | FK → payments.payment_id | Theo loại dòng | — |
| `attempt_id` | bigint | FK → payment_attempts.attempt_id | Theo loại dòng | — |
| `source_type` | character varying(30) | — | Theo loại dòng | — |
| `event_id` | bigint | FK → payment_events.source_payment_ipn_events_event_id | Theo loại dòng | — |
| `review_audit_id` | bigint | FK → case_actions.source_payment_review_audits_review_audit_id | Theo loại dòng | — |
| `vnp_transaction_no` | character varying(100) | — | Theo loại dòng | — |
| `dedup_key` | character varying(150) | — | Theo loại dòng | — |
| `payment_reconciliation_cases_assigned_to` | character varying(50) | — | Theo loại dòng | — |
| `intent_id` | uuid | FK → payment_attempts.intent_id; FK → payments.source_payment_intents_intent_id | Theo loại dòng | — |
| `assigned_user_id` | bigint | FK → users.user_id | Theo loại dòng | — |
| `record_type` | text | — | Có | 'cases'::text |

### `case_actions`

Vòng xử lý, quyết định, sự kiện, giữ tiền và nhật ký kiểm tra đối soát.

Nguồn dòng: `case_rounds`, `case_decisions`, `case_events`, `money_holds`, `payment_review_audits`.

| Thuộc tính | Kiểu PostgreSQL | Khóa/quan hệ | Bắt buộc vật lý | Mặc định / generated |
|---|---|---|---|---|
| `action_id` | bigint | PK | Có | nextval('og70_case_actions_action_id_seq'::regclass) |
| `source_case_rounds_round_id` | bigint | — | Theo loại dòng | — |
| `case_id` | bigint | FK → case_actions.case_id; FK → cases.case_id | Theo loại dòng | — |
| `round_no` | integer | — | Theo loại dòng | — |
| `stage` | character varying(25) | — | Theo loại dòng | — |
| `state` | character varying(15) | — | Theo loại dòng | — |
| `opened_at` | timestamp with time zone | — | Theo loại dòng | — |
| `complete_submission_at` | timestamp with time zone | — | Theo loại dòng | — |
| `ktv_due_at` | timestamp with time zone | — | Theo loại dòng | — |
| `valid_damage_request_at` | timestamp with time zone | — | Theo loại dòng | — |
| `source_case_decisions_decision_id` | bigint | — | Theo loại dòng | — |
| `round_id` | bigint | FK → case_actions.source_case_rounds_round_id | Theo loại dòng | — |
| `reviewed_by` | bigint | FK → users.user_id | Theo loại dòng | — |
| `actor_type` | character varying(20) | — | Theo loại dòng | — |
| `outcome` | character varying(25) | — | Theo loại dòng | — |
| `is_final` | boolean | — | Theo loại dòng | — |
| `goods_refund_amount` | numeric(19,2) | — | Theo loại dòng | — |
| `reason` | text | — | Theo loại dòng | — |
| `decided_at` | timestamp with time zone | — | Theo loại dòng | — |
| `supersedes_decision_id` | bigint | FK → case_actions.source_case_decisions_decision_id | Theo loại dòng | — |
| `command_key` | character varying(150) | — | Theo loại dòng | — |
| `source_case_events_event_id` | uuid | — | Theo loại dòng | — |
| `actor_id` | bigint | FK → users.user_id | Theo loại dòng | — |
| `recipient_id` | bigint | FK → users.user_id | Theo loại dòng | — |
| `event_type` | character varying(30) | — | Theo loại dòng | — |
| `metadata` | jsonb | — | Theo loại dòng | — |
| `occurred_at` | timestamp with time zone | — | Theo loại dòng | — |
| `source_money_holds_hold_id` | uuid | — | Theo loại dòng | — |
| `component_id` | uuid | FK → order_fund_components.component_id | Theo loại dòng | — |
| `source_key` | character varying(150) | — | Theo loại dòng | — |
| `closed_at` | timestamp with time zone | — | Theo loại dòng | — |
| `source_payment_review_audits_review_audit_id` | bigint | — | Theo loại dòng | — |
| `order_id` | bigint | FK → orders.order_id | Theo loại dòng | — |
| `payment_id` | bigint | FK → payments.payment_id | Theo loại dòng | — |
| `attempt_id` | bigint | FK → payment_attempts.attempt_id | Theo loại dòng | — |
| `decided_by` | character varying(50) | — | Theo loại dòng | — |
| `decision` | character varying(30) | — | Theo loại dòng | — |
| `reason_note` | text | — | Theo loại dòng | — |
| `vnp_verification_ref` | character varying(100) | — | Theo loại dòng | — |
| `created_at` | timestamp with time zone | — | Theo loại dòng | — |
| `intent_id` | uuid | FK → payment_attempts.intent_id; FK → payments.source_payment_intents_intent_id | Theo loại dòng | — |
| `record_type` | text | — | Có | 'case_rounds'::text |

### `case_evidence`

Bằng chứng theo vòng xử lý và metadata video mở hàng cũ.

Nguồn dòng: `case_evidence`, `order_unboxing_evidences`.

| Thuộc tính | Kiểu PostgreSQL | Khóa/quan hệ | Bắt buộc vật lý | Mặc định / generated |
|---|---|---|---|---|
| `evidence_id` | uuid | PK | Có | gen_random_uuid() |
| `round_id` | bigint | FK → case_actions.source_case_rounds_round_id | Theo loại dòng | — |
| `submitted_by` | bigint | FK → users.user_id | Theo loại dòng | — |
| `submission_no` | integer | — | Theo loại dòng | — |
| `media_type` | character varying(10) | — | Theo loại dòng | — |
| `object_key` | text | — | Theo loại dòng | — |
| `text_content` | text | — | Theo loại dòng | — |
| `content_digest` | character(64) | — | Theo loại dòng | — |
| `file_size_bytes` | bigint | — | Theo loại dòng | — |
| `duration_seconds` | integer | — | Theo loại dòng | — |
| `submitted_at` | timestamp with time zone | — | Theo loại dòng | CURRENT_TIMESTAMP |
| `deleted_at` | timestamp with time zone | — | Theo loại dòng | — |
| `source_order_unboxing_evidences_evidence_id` | bigint | — | Theo loại dòng | — |
| `order_id` | bigint | FK → orders.order_id | Theo loại dòng | — |
| `buyer_id` | bigint | FK → orders.buyer_id; FK → users.user_id | Theo loại dòng | — |
| `status` | character varying(30) | — | Theo loại dòng | — |
| `video_url` | text | — | Theo loại dòng | — |
| `thumbnail_url` | text | — | Theo loại dòng | — |
| `cloudinary_public_id` | character varying(255) | — | Theo loại dòng | — |
| `video_duration_sec` | integer | — | Theo loại dòng | — |
| `recorded_at` | timestamp with time zone | — | Theo loại dòng | — |
| `skipped_at` | timestamp with time zone | — | Theo loại dòng | — |
| `created_at` | timestamp with time zone | — | Theo loại dòng | — |
| `recorded_acknowledged_at` | timestamp with time zone | — | Theo loại dòng | — |
| `skip_warning_accepted_at` | timestamp with time zone | — | Theo loại dòng | — |
| `warning_version` | character varying(50) | — | Theo loại dòng | — |
| `record_type` | text | — | Có | 'case_evidence'::text |

### `reviews`

Đánh giá và lịch sử chỉnh sửa.

Nguồn dòng: `reviews`.
Metadata/lịch sử gộp: `review_revisions` → `edit_history`.

| Thuộc tính | Kiểu PostgreSQL | Khóa/quan hệ | Bắt buộc vật lý | Mặc định / generated |
|---|---|---|---|---|
| `review_id` | bigint | PK | Có | nextval('og70_reviews_review_id_seq'::regclass) |
| `order_id` | bigint | FK → orders.order_id | Theo loại dòng | — |
| `reviewer_id` | bigint | FK → users.user_id | Theo loại dòng | — |
| `reviewee_id` | bigint | FK → users.user_id | Theo loại dòng | — |
| `rating` | smallint | — | Theo loại dòng | — |
| `comment` | character varying(2000) | — | Theo loại dòng | — |
| `created_at` | timestamp with time zone | — | Theo loại dòng | CURRENT_TIMESTAMP |
| `updated_at` | timestamp with time zone | — | Theo loại dòng | — |
| `deleted_at` | timestamp with time zone | — | Theo loại dòng | — |
| `record_type` | text | — | Có | 'reviews'::text |
| `edit_history` | jsonb | — | Theo loại dòng | '[]'::jsonb |

### `seller_buyer_blocks`

Chặn giao dịch theo cặp người bán/người mua.

Nguồn dòng: `seller_buyer_blocks`.

| Thuộc tính | Kiểu PostgreSQL | Khóa/quan hệ | Bắt buộc vật lý | Mặc định / generated |
|---|---|---|---|---|
| `seller_id` | bigint | PK; FK → users.user_id | Có | — |
| `buyer_id` | bigint | PK; FK → users.user_id | Có | — |
| `blocked_at` | timestamp with time zone | — | Theo loại dòng | CURRENT_TIMESTAMP |
| `unblocked_at` | timestamp with time zone | — | Theo loại dòng | — |
| `reason` | text | — | Theo loại dòng | — |
| `record_type` | text | — | Có | 'seller_buyer_blocks'::text |

### `penalty_ledger`

Sổ điểm phạt có nguồn và người thực hiện.

Nguồn dòng: `penalty_ledger`.

| Thuộc tính | Kiểu PostgreSQL | Khóa/quan hệ | Bắt buộc vật lý | Mặc định / generated |
|---|---|---|---|---|
| `entry_id` | uuid | PK | Có | gen_random_uuid() |
| `user_id` | bigint | FK → users.user_id | Theo loại dòng | — |
| `report_id` | bigint | FK → cases.source_reports_report_id | Theo loại dòng | — |
| `policy_id` | bigint | FK → business_policies.source_penalty_policies_policy_id | Theo loại dòng | — |
| `points_delta` | integer | — | Theo loại dòng | — |
| `adjustment_of` | uuid | FK → penalty_ledger.entry_id | Theo loại dòng | — |
| `decided_by` | bigint | FK → users.user_id | Theo loại dòng | — |
| `reason` | text | — | Theo loại dòng | — |
| `created_at` | timestamp with time zone | — | Theo loại dòng | CURRENT_TIMESTAMP |
| `command_key` | character varying(150) | — | Theo loại dòng | — |
| `record_type` | text | — | Có | 'penalty_ledger'::text |

### `notifications`

Thông báo, lệnh gửi, nhắc hạn, cảnh báo thanh toán và lịch sử giao thông báo.

Nguồn dòng: `notifications`, `notification_dispatches`, `notification_reminders`, `payment_alert_requests`.
Metadata/lịch sử gộp: `notification_deliveries` → `delivery_history`.

| Thuộc tính | Kiểu PostgreSQL | Khóa/quan hệ | Bắt buộc vật lý | Mặc định / generated |
|---|---|---|---|---|
| `notification_id` | bigint | PK | Có | nextval('og70_notifications_notification_id_seq'::regclass) |
| `user_id` | bigint | FK → users.user_id | Theo loại dòng | — |
| `type` | character varying(50) | — | Theo loại dòng | — |
| `title` | character varying(200) | — | Theo loại dòng | — |
| `content` | text | — | Theo loại dòng | — |
| `reference_type` | character varying(50) | — | Theo loại dòng | — |
| `reference_id` | bigint | — | Theo loại dòng | — |
| `is_read` | boolean | — | Theo loại dòng | false |
| `created_at` | timestamp with time zone | — | Theo loại dòng | CURRENT_TIMESTAMP |
| `read_at` | timestamp with time zone | — | Theo loại dòng | — |
| `event_id` | uuid | FK → outbox_events.event_id | Theo loại dòng | — |
| `dispatch_id` | uuid | FK → notifications.source_notification_dispatches_dispatch_id | Theo loại dòng | — |
| `sender_id` | bigint | FK → users.user_id | Theo loại dòng | — |
| `reference_uuid` | uuid | — | Theo loại dòng | — |
| `reference_revision` | bigint | — | Theo loại dòng | — |
| `target_data` | jsonb | — | Theo loại dòng | '{}'::jsonb |
| `source_notification_dispatches_dispatch_id` | uuid | — | Theo loại dòng | — |
| `command_key` | character varying(150) | — | Theo loại dòng | — |
| `source_notification_reminders_reminder_id` | uuid | — | Theo loại dòng | — |
| `source_key` | character varying(150) | — | Theo loại dòng | — |
| `reminder_type` | character varying(30) | — | Theo loại dòng | — |
| `scheduled_at` | timestamp with time zone | — | Theo loại dòng | — |
| `expires_at` | timestamp with time zone | — | Theo loại dòng | — |
| `state` | character varying(15) | — | Theo loại dòng | — |
| `source_snapshot` | jsonb | — | Theo loại dòng | — |
| `source_payment_alert_requests_alert_id` | bigint | — | Theo loại dòng | — |
| `case_id` | bigint | FK → cases.source_payment_reconciliation_cases_case_id | Theo loại dòng | — |
| `alert_type` | character varying(50) | — | Theo loại dòng | — |
| `severity` | character varying(20) | — | Theo loại dòng | — |
| `status` | character varying(20) | — | Theo loại dòng | — |
| `attempt_count` | integer | — | Theo loại dòng | — |
| `last_error` | text | — | Theo loại dòng | — |
| `sent_at` | timestamp with time zone | — | Theo loại dòng | — |
| `record_type` | text | — | Có | 'notifications'::text |
| `delivery_history` | jsonb | — | Theo loại dòng | '[]'::jsonb |

### `audit_logs`

Nhật ký kiểm toán, trạng thái tài khoản/đơn hàng và lượt xuất báo cáo.

Nguồn dòng: `audit_logs`, `account_status_events`, `order_events`, `report_export_runs`.

| Thuộc tính | Kiểu PostgreSQL | Khóa/quan hệ | Bắt buộc vật lý | Mặc định / generated |
|---|---|---|---|---|
| `log_id` | bigint | PK | Có | nextval('og70_audit_logs_log_id_seq'::regclass) |
| `user_id` | bigint | FK → users.user_id | Theo loại dòng | — |
| `action` | character varying(50) | — | Theo loại dòng | — |
| `entity_type` | character varying(50) | — | Theo loại dòng | — |
| `entity_id` | bigint | — | Theo loại dòng | — |
| `old_values` | jsonb | — | Theo loại dòng | — |
| `new_values` | jsonb | — | Theo loại dòng | — |
| `ip_address` | inet | — | Theo loại dòng | — |
| `request_id` | character varying(100) | — | Theo loại dòng | — |
| `created_at` | timestamp with time zone | — | Theo loại dòng | CURRENT_TIMESTAMP |
| `source_account_status_events_event_id` | bigint | — | Theo loại dòng | — |
| `old_status` | character varying(20) | — | Theo loại dòng | — |
| `new_status` | character varying(20) | — | Theo loại dòng | — |
| `occurred_at` | timestamp with time zone | — | Theo loại dòng | — |
| `source_order_events_event_id` | uuid | — | Theo loại dòng | — |
| `order_id` | bigint | FK → orders.order_id | Theo loại dòng | — |
| `actor_id` | bigint | FK → users.user_id | Theo loại dòng | — |
| `event_type` | character varying(60) | — | Theo loại dòng | — |
| `reason` | text | — | Theo loại dòng | — |
| `metadata` | jsonb | — | Theo loại dòng | — |
| `command_key` | character varying(150) | — | Theo loại dòng | — |
| `source_report_export_runs_export_id` | uuid | — | Theo loại dòng | — |
| `requested_by` | bigint | FK → users.user_id | Theo loại dòng | — |
| `metric` | character varying(30) | — | Theo loại dòng | — |
| `filters` | jsonb | — | Theo loại dòng | — |
| `timezone` | character varying(60) | — | Theo loại dòng | — |
| `cutoff_at` | timestamp with time zone | — | Theo loại dòng | — |
| `object_key` | text | — | Theo loại dòng | — |
| `record_type` | text | — | Có | 'audit_logs'::text |

### `outbox_events`

Sự kiện chờ xử lý cùng tiến độ consumer.

Nguồn dòng: `outbox_events`.
Metadata/lịch sử gộp: `outbox_consumer_checkpoints` → `consumer_progress`.

| Thuộc tính | Kiểu PostgreSQL | Khóa/quan hệ | Bắt buộc vật lý | Mặc định / generated |
|---|---|---|---|---|
| `event_id` | uuid | PK | Có | gen_random_uuid() |
| `aggregate_type` | character varying(80) | — | Theo loại dòng | — |
| `aggregate_id` | character varying(100) | — | Theo loại dòng | — |
| `event_type` | character varying(120) | — | Theo loại dòng | — |
| `payload` | jsonb | — | Theo loại dòng | — |
| `occurred_at` | timestamp with time zone | — | Theo loại dòng | CURRENT_TIMESTAMP |
| `available_at` | timestamp with time zone | — | Theo loại dòng | CURRENT_TIMESTAMP |
| `published_at` | timestamp with time zone | — | Theo loại dòng | — |
| `attempt_count` | integer | — | Theo loại dòng | 0 |
| `last_error` | character varying(1000) | — | Theo loại dòng | — |
| `record_type` | text | — | Có | 'outbox_events'::text |
| `consumer_progress` | jsonb | — | Theo loại dòng | '[]'::jsonb |

### `interaction_events`

Sự kiện tương tác để thống kê.

Nguồn dòng: `interaction_events`.

| Thuộc tính | Kiểu PostgreSQL | Khóa/quan hệ | Bắt buộc vật lý | Mặc định / generated |
|---|---|---|---|---|
| `event_id` | uuid | PK | Có | gen_random_uuid() |
| `user_id` | bigint | FK → users.user_id | Theo loại dòng | — |
| `guest_session_digest` | character(64) | — | Theo loại dòng | — |
| `event_type` | character varying(15) | — | Theo loại dòng | — |
| `product_id` | bigint | FK → products.product_id | Theo loại dòng | — |
| `query_text` | character varying(200) | — | Theo loại dòng | — |
| `occurred_at` | timestamp with time zone | — | Theo loại dòng | CURRENT_TIMESTAMP |
| `source_key` | character varying(150) | — | Theo loại dòng | — |
| `record_type` | text | — | Có | 'interaction_events'::text |

## Ánh xạ 104 nguồn cũ → 48 bảng

| Nguồn V21 | Bảng V22 | Cách lưu |
|---|---|---|
| `account_restrictions` | `users` | JSONB `account_restrictions_history` trên chủ thể |
| `account_status_events` | `audit_logs` | Dòng có `record_type=account_status_events` |
| `addresses` | `addresses` | Dòng có `record_type=addresses` |
| `audit_logs` | `audit_logs` | Dòng có `record_type=audit_logs` |
| `auth_challenges` | `auth_challenges` | Dòng có `record_type=auth_challenges` |
| `cart_items` | `cart_items` | Dòng có `record_type=cart_items` |
| `carts` | `users` | JSONB `carts_history` trên chủ thể |
| `case_decisions` | `case_actions` | Dòng có `record_type=case_decisions` |
| `case_events` | `case_actions` | Dòng có `record_type=case_events` |
| `case_evidence` | `case_evidence` | Dòng có `record_type=case_evidence` |
| `case_rounds` | `case_actions` | Dòng có `record_type=case_rounds` |
| `cases` | `cases` | Dòng có `record_type=cases` |
| `categories` | `categories` | Dòng có `record_type=categories` |
| `chat_media_daily_quotas` | `conversations` | JSONB `media_quota_usage` trên chủ thể |
| `checklist_policies` | `business_policies` | Dòng có `record_type=checklist_policies` |
| `checkout_groups` | `checkout_groups` | Dòng có `record_type=checkout_groups` |
| `checkout_redemptions` | `checkout_groups` | JSONB `voucher_redemption` trên chủ thể |
| `complaints` | `cases` | Dòng có `record_type=complaints` |
| `conversation_order_links` | `orders` | JSONB `conversation_links` trên chủ thể |
| `conversation_user_state` | `conversations` | JSONB `participant_state` trên chủ thể |
| `conversations` | `conversations` | Dòng có `record_type=conversations` |
| `discount_allocations` | `order_items` | JSONB `discount_history` trên chủ thể |
| `ekyc_decisions` | `ekyc_profiles` | JSONB `ekyc_decisions_history` trên chủ thể |
| `ekyc_private_assets` | `ekyc_private_assets` | Dòng có `record_type=ekyc_private_assets` |
| `ekyc_profiles` | `ekyc_profiles` | Dòng có `record_type=ekyc_profiles` |
| `ekyc_verification_attempts` | `verification_attempts` | Dòng có `record_type=ekyc_verification_attempts` |
| `external_identities` | `users` | JSONB `external_identities_history` trên chủ thể |
| `handover_confirmations` | `shipment_events` | Dòng có `record_type=handover_confirmations` |
| `identity_document_registry` | `identity_document_registry` | Dòng có `record_type=identity_document_registry` |
| `interaction_events` | `interaction_events` | Dòng có `record_type=interaction_events` |
| `inventory_reservations` | `inventory_reservations` | Dòng có `record_type=inventory_reservations` |
| `listing_fee_assessments` | `listing_fee_charges` | Dòng có `record_type=listing_fee_assessments` |
| `listing_fee_charges` | `listing_fee_charges` | Dòng có `record_type=listing_fee_charges` |
| `listing_fee_policies` | `business_policies` | Dòng có `record_type=listing_fee_policies` |
| `listing_fee_receipts` | `payment_confirmations` | Dòng có `record_type=listing_fee_receipts` |
| `media_analysis_runs` | `product_revisions` | JSONB `media_analysis_runs_history` trên chủ thể |
| `messages` | `messages` | Dòng có `record_type=messages` |
| `money_holds` | `case_actions` | Dòng có `record_type=money_holds` |
| `notification_deliveries` | `notifications` | JSONB `delivery_history` trên chủ thể |
| `notification_dispatches` | `notifications` | Dòng có `record_type=notification_dispatches` |
| `notification_reminders` | `notifications` | Dòng có `record_type=notification_reminders` |
| `notifications` | `notifications` | Dòng có `record_type=notifications` |
| `offers` | `offers` | Dòng có `record_type=offers` |
| `order_events` | `audit_logs` | Dòng có `record_type=order_events` |
| `order_fund_components` | `order_fund_components` | Dòng có `record_type=order_fund_components` |
| `order_items` | `order_items` | Dòng có `record_type=order_items` |
| `order_unboxing_evidences` | `case_evidence` | Dòng có `record_type=order_unboxing_evidences` |
| `order_vouchers` | `orders` | JSONB `legacy_voucher_history` trên chủ thể |
| `orders` | `orders` | Dòng có `record_type=orders` |
| `outbox_consumer_checkpoints` | `outbox_events` | JSONB `consumer_progress` trên chủ thể |
| `outbox_events` | `outbox_events` | Dòng có `record_type=outbox_events` |
| `password_reset_challenges` | `auth_challenges` | Dòng có `record_type=password_reset_challenges` |
| `payment_alert_requests` | `notifications` | Dòng có `record_type=payment_alert_requests` |
| `payment_allocations` | `order_fund_components` | Dòng có `record_type=payment_allocations` |
| `payment_attempts` | `payment_attempts` | Dòng có `record_type=payment_attempts` |
| `payment_confirmations` | `payment_confirmations` | Dòng có `record_type=payment_confirmations` |
| `payment_intents` | `payments` | Dòng có `record_type=payment_intents` |
| `payment_ipn_events` | `payment_events` | Dòng có `record_type=payment_ipn_events` |
| `payment_ipn_raw_receipts` | `payment_events` | Dòng có `record_type=payment_ipn_raw_receipts` |
| `payment_reconciliation_cases` | `cases` | Dòng có `record_type=payment_reconciliation_cases` |
| `payment_review_audits` | `case_actions` | Dòng có `record_type=payment_review_audits` |
| `payments` | `payments` | Dòng có `record_type=payments` |
| `penalty_ledger` | `penalty_ledger` | Dòng có `record_type=penalty_ledger` |
| `penalty_policies` | `business_policies` | Dòng có `record_type=penalty_policies` |
| `pickup_attempts` | `shipment_events` | Dòng có `record_type=pickup_attempts` |
| `product_categories` | `product_categories` | Dòng có `record_type=product_categories` |
| `product_media` | `product_media` | Dòng có `record_type=product_media` |
| `product_moderation_decisions` | `product_revisions` | JSONB `moderation_history` trên chủ thể |
| `product_moderation_legacy_history` | `product_revisions` | JSONB `legacy_moderation_history` trên chủ thể |
| `product_revisions` | `product_revisions` | Dòng có `record_type=product_revisions` |
| `products` | `products` | Dòng có `record_type=products` |
| `quick_auth_attempts` | `verification_attempts` | Dòng có `record_type=quick_auth_attempts` |
| `quick_auth_results` | `auth_challenges` | Dòng có `record_type=quick_auth_results` |
| `quick_auth_sessions` | `auth_challenges` | Dòng có `record_type=quick_auth_sessions` |
| `refresh_sessions` | `refresh_sessions` | Dòng có `record_type=refresh_sessions` |
| `report_export_runs` | `audit_logs` | Dòng có `record_type=report_export_runs` |
| `reports` | `cases` | Dòng có `record_type=reports` |
| `review_revisions` | `reviews` | JSONB `edit_history` trên chủ thể |
| `reviews` | `reviews` | Dòng có `record_type=reviews` |
| `revision_media` | `product_media` | Dòng có `record_type=revision_media` |
| `reward_accounts` | `users` | JSONB `reward_accounts_history` trên chủ thể |
| `reward_ledger` | `reward_ledger` | Dòng có `record_type=reward_ledger` |
| `reward_policies` | `business_policies` | Dòng có `record_type=reward_policies` |
| `roles` | `roles` | Dòng có `record_type=roles` |
| `seller_buyer_blocks` | `seller_buyer_blocks` | Dòng có `record_type=seller_buyer_blocks` |
| `seller_profile_decisions` | `seller_profiles` | JSONB `seller_profile_decisions_history` trên chủ thể |
| `seller_profiles` | `seller_profiles` | Dòng có `record_type=seller_profiles` |
| `seller_verification_metrics` | `verification_attempts` | Dòng có `record_type=seller_verification_metrics` |
| `seller_verifications` | `ekyc_profiles` | Dòng có `record_type=seller_verifications` |
| `settlement_operations` | `settlement_operations` | Dòng có `record_type=settlement_operations` |
| `shipment_events` | `shipment_events` | Dòng có `record_type=shipment_events` |
| `shipments` | `shipments` | Dòng có `record_type=shipments` |
| `system_fee_policies` | `business_policies` | Dòng có `record_type=system_fee_policies` |
| `user_roles` | `user_roles` | Dòng có `record_type=user_roles` |
| `user_security_settings` | `users` | JSONB `user_security_settings_history` trên chủ thể |
| `users` | `users` | Dòng có `record_type=users` |
| `vnpay_payment_attempts` | `payment_attempts` | Dòng có `record_type=vnpay_payment_attempts` |
| `voucher_grant_events` | `voucher_grants` | JSONB `grant_history` trên chủ thể |
| `voucher_grants` | `voucher_grants` | Dòng có `record_type=voucher_grants` |
| `voucher_revision_products` | `voucher_products` | Dòng có `record_type=voucher_revision_products` |
| `voucher_revision_revocations` | `vouchers` | JSONB `revocation_history` trên chủ thể |
| `voucher_revisions` | `vouchers` | Dòng có `record_type=voucher_revisions` |
| `vouchers` | `vouchers` | Dòng có `record_type=vouchers` |
| `zero_checkout_confirmations` | `checkout_groups` | JSONB `zero_confirmation` trên chủ thể |

## Dùng cho ERD

Chọn schema `public`, chọn 48 bảng ở danh sách trên, bỏ `flyway_schema_history`. Khóa ngoại tới `source_*` là quan hệ tới định danh của một loại dòng trong bảng dùng chung; không tạo thêm bảng cho mỗi loại. Quan hệ metadata JSONB được mô tả trong ánh xạ nguồn, không tự hiện thành FK trên ERD.

Catalog có 191 ràng buộc FK vật lý. Một cặp bảng có thể có nhiều FK theo loại dòng hoặc khóa ghép; chỉ hiển thị quan hệ cần đọc trên sơ đồ tổng quan, giữ đầy đủ trong phụ lục cấu trúc.

[DDL chỉ gồm 48 bảng và quan hệ](../schema/og_shop_v22_tables_for_erd.sql). DDL này để dựng ERD trong database trống; runtime dùng Flyway V1–V22 cùng view/trigger, không chạy snapshot đè database đang dùng.
