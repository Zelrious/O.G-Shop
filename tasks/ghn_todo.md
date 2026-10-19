# Danh Sách Công Việc (Todo List) - Tích Hợp GHN Sandbox

## Phase 1: Cấu hình & GHN REST Client
- [x] **GHN-01**: Cập nhật `application.yml` thêm cấu hình `app.ghn` (`base-url`, `api-token`, `shop-id`, timeouts).
- [x] **GHN-02**: Tạo class `GhnProperties.java` để đọc cấu hình an toàn bằng `@ConfigurationProperties`.
- [x] **GHN-03**: Định nghĩa các record DTO giao tiếp với GHN (`GhnApiResponse`, `GhnMasterDataDtos`, `GhnShippingDtos` với `@JsonInclude(NON_NULL)`, `client_order_code`, `CHOTHUHANG`).
- [x] **GHN-04**: Viết class `GhnClient.java` sử dụng Spring `RestClient` kết nối GHN Dev Gateway (Master data, available services, tính phí, tạo vận đơn, in phiếu, tra cứu tracking, helper post/get an toàn).
- [x] **GHN-05**: Viết unit/integration test cho `GhnClient` kiểm tra việc gọi API GHN.

## Phase 2: Schema Database & Entity
- [x] **GHN-06**: Viết Flyway migration `V15__enhance_shipments_for_ghn.sql` mở rộng bảng `shipments` (chuẩn hóa `carrier VARCHAR(100)` DEFAULT 'GHN', thêm `client_order_code VARCHAR(50)` với partial `UNIQUE` index `WHERE client_order_code IS NOT NULL`, thêm `shipping_fee NUMERIC(19,2)`, `expected_delivery_at TIMESTAMPTZ`, `raw_ghn_status VARCHAR(50)`, `created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP` - không tạo `UNIQUE (order_id)`, không lưu `print_token`; lưu ý bảng `ghn_webhook_events` thuộc Phase 4, KHÔNG nằm trong V15).
- [x] **GHN-07**: Tạo `ShipmentEntity.java` và `ShipmentRepository.java` trong `modules/fulfillment/infrastructure/persistence/`.
- [x] **GHN-08**: Cập nhật `OrderEntity.java` bổ sung phương thức chuyển trạng thái `markShipped(Instant now)` và `markDelivered(Instant now)`.

## Phase 3: Nghiệp vụ Fulfillment & Controller
- [x] **GHN-09**: Tạo `FulfillmentService.java` xử lý nghiệp vụ tạo vận đơn GHN cho đơn hàng đã xác nhận (`SELLER_CONFIRMED`), lấy link in phiếu A5 và tra cứu tiến trình.
  * **Khóa an toàn Fail-Closed (A1)**: Khi phát hiện thiếu nguồn dữ liệu tin cậy (chờ ADDR-01/02/03), `createShipment` fail-closed ném `FulfillmentFeatureLockedException` (HTTP 409 Conflict). Tuyệt đối không gọi API GHN và không tạo bản ghi Shipment PENDING rác.
  * **Cơ chế Claim/Lease & Timeout Recovery (A2)**: Triển khai trong `V16__ghn_webhook_and_claim_support.sql` với `claim_token` và `claim_expires_at`. Tách transaction DB ngắn khỏi outbound HTTP call. Lease 30 giây, tranh chấp nguyên tử chống 2 worker gọi đồng thời. Khi timeout, giữ nguyên `client_order_code`, tự động đối soát GHN trước khi tái thử.
- [x] **GHN-10**: Tạo `FulfillmentController.java` cung cấp API cho Seller và Buyer.
- [x] **GHN-11**: Cập nhật `SecurityConfig.java` phân quyền cho các endpoint fulfillment.

## Phase 4: Webhook GHN & Đồng Bộ Trạng Thái
- [x] **GHN-12**: Tạo endpoint Webhook tiếp nhận callback từ GHN (`POST /api/v1/fulfillment/webhook`):
  * Xác thực custom header secret an toàn qua `MessageDigest.isEqual` (constant-time); fail-closed: trả `503 SERVICE_UNAVAILABLE` (cho phép GHN retry) nếu secret chưa được cấu hình, trả `403 FORBIDDEN` nếu header sai hoặc thiếu.
  * Parse DTO an toàn (`WebhookPayloadDto`) với các trường tùy chọn/nullable.
  * Lọc bỏ PII bưu tá (`ShipperName`, `ShipperPhone` được redact thành `[REDACTED]`).
  * Tạo `idempotency_key` chuẩn hóa: dùng `OrderCode:Type:Time` khi có thời gian hợp lệ, dùng SHA-256 fingerprint nội dung nghiệp vụ `OrderCode:Type:FP:<hex16>` khi thiếu thời gian (không dùng chung key `NO_TIME`, không lưu token/PII trong key).
  * Transaction boundary cô lập (`PROPAGATION_REQUIRES_NEW`) khi lưu sự kiện: ACK 200 OK ngay cả khi gặp `DataIntegrityViolationException` do duplicate concurrent insert mà không bị `UnexpectedRollbackException`. Trả 500 nếu lỗi database thực sự để GHN retry.
- [x] **GHN-13**: Service xử lý sự kiện webhook bất đồng bộ & bền vững (`GhnWebhookProcessor`):
  * Cơ chế claim nguyên tử (`claimEventForProcessing` với `processing_started_at`) chống race condition giữa nhiều worker; trạng thái xử lý rõ ràng (`PENDING`, `PROCESSING`, `PROCESSED`, `FAILED`, `DEAD_LETTER`).
  * Scheduled recovery worker tự động quét và phục hồi các event `PENDING`, `PROCESSING` quá hạn (stale threshold 2 phút sau crash/restart) và `FAILED` (retry tối đa 5 lần).
  * Tra cứu shipment theo cả mã vận đơn GHN (`order_code`) lẫn mã đơn hàng client (`client_order_code`).
  * Ánh xạ trạng thái GHN nhất quán và xác định: `storing` và `sorting` chuyển vào nhóm `applyCarrierInTransit` (giúp Order tự động chuyển sang `SHIPPED` nếu lỡ webhook `picked` trung gian).
  * Không làm Order chuyển `SHIPPED` khi GHN mới ở bước tạo đơn/ready-to-pick/picking; chỉ chuyển `SHIPPED` khi bưu tá đã lấy hàng (`picked`) hoặc khi hàng đã ở kho/đang vận chuyển (`storing`, `sorting`, `transporting`, `delivering`).
  * Khi giao thành công (`delivered`), cập nhật shipment `DELIVERED`, lưu `delivered_at` và chuyển Order sang `DELIVERED`. Countdown 3 ngày đồng kiểm được tính động từ `delivered_at + 3 ngày` (không giải ngân escrow tức thì).
  * Các sự cố (`damage`, `lost`, `scrap`, `exception`) không làm thay đổi trạng thái shipment, mà ghi nhận cờ `admin_review_required = true` và `incident_notes` để chờ Admin xử lý (ADMIN-01), không tự động hoàn tiền/xử lý escrow.
- [x] **GHN-14**: Scheduled Job đối soát định kỳ & bù đắp vận đơn chưa có tracking number (`GhnReconciliationScheduler`):
  * Lịch chạy mặc định mỗi 30 phút (`0 */30 * * * *`), hỗ trợ bật/tắt và chỉnh tần suất/batch size qua `app.ghn.reconciliation`.
  * Nhánh 1: Quét các shipment chưa ở trạng thái cuối (`status IN ('PENDING', 'PICKING', 'IN_TRANSIT', 'RETURNING')`) có tracking number và `admin_review_required = false`. Tra cứu qua `/v2/shipping-order/detail`.
  * Nhánh 2: Quét các shipment `PENDING` có `client_order_code` nhưng chưa có `tracking_number` (do timeout lúc tạo đơn) và `admin_review_required = false`. Tra cứu qua `/v2/shipping-order/detail-by-client-code`. Nếu GHN chưa tìm thấy, giữ nguyên PENDING, không tạo lại mã mới hoặc gọi tạo đơn mù.
  * Loại trừ các đơn incident (`admin_review_required = true`) khỏi chu kỳ đối soát vô ích.
  * Xử lý lỗi cô lập từng shipment (lỗi một shipment không dừng batch); không ghi đè trạng thái khi gặp lỗi, timeout hoặc status lạ; log tổng kết chi tiết không chứa PII/secret.
- [x] **GHN-Reliability-Review**: Hoàn tất 6 Tasks rà soát độ tin cậy Phase 4:
  * 17/17 PostgreSQL integration tests trên DB cô lập (port 55432) passed 100%.
  * 110/110 Fulfillment module tests passed 100%.
  * 385/387 Full Maven suite tests passed (2 skipped theo baseline, 0 failures, 0 errors).
  * `git diff --check` sạch, bảo toàn thay đổi uncommitted, không commit/push.
- [x] **GHN-Task-1-Timeout-Recovery**: Cải thiện khả năng xử lý GHN timeout kéo dài & đối soát vận đơn (Bao gồm các Task 1.1, 1.2, 1.3, 1.4 - Fenced Reconciliation & Monotonic State Transitions):
  * Phân biệt rõ 5 tình huống GHN (chưa nhận, timeout response, indexing chậm, không có kết quả sau nhiều lần, lỗi tạm thời vs lỗi xác định).
  * Chống tạo trùng vận đơn: Khi `reconciliationFirst == true` mà tra cứu chưa thấy đơn, tuyệt đối không gọi tạo mới mù quáng; giải phóng claim, giữ nguyên `client_order_code`, cập nhật `rawGhnStatus = "pending_carrier_confirmation"`.
  * Cơ chế Bounded Backoff cho untracked reconciliation: tăng dần thời gian `claimExpiresAt` (30s -> 60s -> 120s -> 300s); dừng polling và gắn cờ `adminReviewRequired = true` khi vượt quá 30 phút.
  * Tách luồng scheduled `runUntrackedPendingReconciliation` chạy mỗi 60 giây độc lập với chu kỳ đối soát 30 phút.
  * Hỗ trợ On-Demand Fast Reconciliation trong `getTracking`: Seller kiểm tra trạng thái trong thời gian chờ sẽ được tra cứu ngay lập tức qua `client_order_code`, nhận tracking number ngay khi GHN index xong.
  * **Cơ chế Claim-Token Fencing (Task 1.2 & 1.3)**: Toàn bộ các thao tác `recordReconciledTracking`, `releaseClaim`, `releaseClaimWithBackoff`, `recordIncidentAndReleaseClaim` đều áp dụng conditional DML nguyên tử (`WHERE claimToken = :claimToken`). Nếu lease của Worker cũ hết hạn và Worker mới đã lấy claim mới, Worker cũ hoàn thành muộn sẽ bị chặn 100% ở cấp database (`updatedRows == 0`), không thể xóa claim, không thể ghi đè tracking number, backoff hay incident của Worker mới.
  * **Chuyển Trạng Thái Đơn Điệu (Monotonic Transitions) & Phòng Ngự TOCTOU (Task 1.4)**:
    - Loại bỏ hoàn toàn `shipmentRepository.save(shipment)` full-entity trong `GhnReconciliationScheduler.applyReconciliationStatus()`, thay thế bằng các câu query nguyên tử có điều kiện: `updateRawGhnStatusIfPending`, `updateRawGhnStatusIfNotTerminal`, `recordIncidentIfNotTerminal`. Triệt tiêu hoàn toàn rủi ro TOCTOU làm ghi đè lùi trạng thái database (Lost Update) do `ShipmentEntity` không có `@Version`.
    - Thêm cấu hình `@Modifying(clearAutomatically = true, flushAutomatically = true)` cho toàn bộ 8 phương thức modifying query trong `ShipmentRepository`, bảo đảm Persistence Context (1st-level cache) luôn được xóa sạch sau khi thực thi câu query native/JPQL, triệt tiêu rủi ro đọc trúng entity stale mang `trackingNumber = null` ở các lần gọi `findByOrderId()` ngay sau đó.
    - Bảo đảm nhánh on-demand `getTracking()` là read-only sau khi ghi nhận mã vận đơn, luôn phản ánh trạng thái mới nhất từ DB nếu Webhook xen ngang.
    - Bổ sung chốt chặn `shipment.isTerminal()` ở đầu `applyReconciliationStatus()`, từ chối mọi thao tác biến đổi đối với các đơn đã kết thúc (`DELIVERED`, `RETURNED`, `FAILED`).
  * **Số liệu Kiểm thử Thực tế Mới nhất**:
    - **65/65 Unit tests Fulfillment passed 100%**: `FulfillmentServiceUnitTest` (23 tests bao gồm A2.15 fenced write và A2.16 monotonic on-demand), `GhnReconciliationSchedulerTest` (15 tests bao gồm E12 fenced write, E13/E14 monotonic scheduler, E15 TOCTOU defense), `ShipmentEntityTest` (16 tests), `GhnClientUnitTest` (11 tests).
    - **Fulfillment Module Suite**: 101 tests (84 passed, 17 PostgreSQL skipped do thiếu môi trường cô lập).
    - **Toàn bộ Dự án Backend**: **405 tests passed 100%**, 0 failures, 0 errors, **46 tests PostgreSQL skipped** (ghi nhận trung thực, không giả lập PASS).

## Hạng Mục Mở Rộng Địa Chỉ, Checkout & Quản Trị (KHÔNG thực hiện trong Phase 2)
- [x] **ADDR-01 (Màn hình/API Chuẩn Hóa Sổ Địa Chỉ GHN & Địa Chỉ Lấy Hàng của Seller)**:
  * **Database Migration V18**: `V18__add_ghn_address_codes.sql` bổ sung `ghn_province_id`, `ghn_district_id`, `ghn_ward_code` vào `addresses` và `shipping_ghn_province_id`, `shipping_ghn_district_id`, `shipping_ghn_ward_code` vào `orders` (giữ nguyên NULL cho bản ghi cũ).
  * **Backend Entity & DTOs**: `AddressEntity` bổ sung 3 trường GHN và method `hasValidGhnCodes()`; `ProfileDtos` (`CreateAddressRequest`, `UpdateAddressRequest`, `AddressResponse`) và `IdentityCommerceFacade` (`AddressDto`, `CreateAddressRequest`) mở rộng nhận/trả các mã GHN; validation All-or-Nothing trong `ProfileService` & `DefaultIdentityCommerceFacade` chặn các payload truyền thiếu nhất quán mã GHN.
  * **Frontend Sổ Địa Chỉ (`AddressBookTab.tsx`)**:
    - Hiển thị banner hướng dẫn Seller: Địa chỉ mặc định sẽ tự động được sử dụng làm địa chỉ kho lấy hàng khi có người mua đặt đơn qua GHN.
    - Hiển thị Badge trực quan: "✓ Đã chuẩn hóa GHN" (xanh lá) vs "⚠️ Cần cập nhật chuẩn GHN" (cam).
    - Form Thêm mới / Sửa: Thay 3 ô input text bằng Cascading Select 3 cấp (Tỉnh/Thành -> Quận/Huyện -> Phường/Xã) kết nối trực tiếp với API Master-data GHN (`/api/v1/shipping/master-data/*`). Tự động nạp cấp con và reset khi thay đổi cấp cha.
    - Bắt buộc chọn đủ 3 cấp chuẩn GHN trước khi lưu.
  * **Kết quả Kiểm thử Thực tế**:
    - Backend Unit Tests: 12/12 passed (ProfileServiceTest 9/9, DefaultIdentityCommerceFacadeTest 3/3). Toàn bộ non-postgres suite: 368 passed, 0 failures, 8 skipped.
    - Frontend Unit Tests: 93/93 tests (22 test files) passed 100% (AddressBookTab 2/2, Profile 6/6). TypeScript typecheck `tsc --noEmit` passed 0 errors.
    - `git diff --check` sạch 100%, bảo toàn thay đổi uncommitted.
- [x] **ADDR-02 (Sửa gán địa chỉ nguồn Seller & Snapshot mã GHN vào OrderEntity)**:
  * **Sửa gán nhầm địa chỉ nguồn trong `CheckoutService.java`**: Truy vấn địa chỉ mặc định của Seller thông qua `identityCommerceFacade.getDefaultAddress(product.sellerId())` và gán `sellerAddress.id()` vào tham số `sourceAddressId` của `new OrderEntity(...)` (triệt tiêu hoàn toàn lỗi gán nhầm `address.id()` của Buyer từ trước).
  * **Snapshot mã GHN vào `OrderEntity`**: Ánh xạ 3 trường mới `shippingGhnProvinceId`, `shippingGhnDistrictId`, `shippingGhnWardCode` vào `OrderEntity` (tương ứng schema `V18`); bổ sung constructor overload, getters và helper `hasValidShippingGhnCodes()`; nạp snapshot từ địa chỉ nhận hàng của Buyer khi đặt hàng.
  * **Mở rộng DTOs**: Bổ sung `ghnProvinceId`, `ghnDistrictId`, `ghnWardCode` vào `CheckoutDtos.AddressSummaryResponse` và `CheckoutDtos.CreateAddressRequest` (kèm constructor overload tương thích ngược).
  * **Kiểm thử Thực tế**: `CheckoutServiceTest` (10/10 passed) xác nhận `order.getSourceAddressId()` bằng `sellerAddress.id()`, snapshot mã GHN đầy đủ, và kiểm tra các kịch bản đặt hàng mới.

- [x] **ADDR-03 (Bật Chặn Checkout thiếu mã GHN & Mở khóa FulfillmentService.createShipment)**:
  * **Chặn Checkout thiếu mã GHN tại Backend (`CheckoutService.java`)**:
    - Kiểm tra địa chỉ nhận hàng của Buyer: Nếu `!address.hasValidGhnCodes()` -> ném `IllegalArgumentException` yêu cầu cập nhật hoặc thêm địa chỉ chuẩn GHN.
    - Kiểm tra địa chỉ kho của Seller: Nếu Seller chưa thiết lập địa chỉ mặc định hoặc `!sellerAddress.hasValidGhnCodes()` -> ném `IllegalStateException` thông báo người bán chưa thiết lập địa chỉ lấy hàng chuẩn hóa GHN.
  * **Chặn Checkout thiếu mã GHN tại Frontend (`CheckoutView.tsx`)**:
    - Hiển thị badge trạng thái chuẩn hóa GHN ("✓ Chuẩn GHN" xanh lá vs "⚠️ Chưa chuẩn GHN" đỏ) trên từng địa chỉ trong danh sách chọn.
    - Hiển thị banner cảnh báo nổi bật khi địa chỉ đang chọn thiếu mã GHN hợp lệ.
    - Vô hiệu hóa nút xác nhận đặt mua khi `!isCheckoutAddressValid` và đổi nhãn nút thành `"⚠️ Cần chọn địa chỉ chuẩn hóa GHN"`.
    - Nâng cấp form thêm địa chỉ mới ngay tại Checkout sang Cascading Select 3 cấp (Tỉnh/Thành -> Quận/Huyện -> Phường/Xã) từ `profileApi` (`getProvinces`, `getDistricts`, `getWards`), kèm badge phản hồi trực tiếp khi đã chọn đủ 3 cấp GHN.
  * **Mở khóa `FulfillmentService.createShipment` & Kết nối dữ liệu DB thật**:
    - Mở rộng `OrderFulfillmentSnapshot` và `DefaultFulfillmentCommerceFacade`: Inject `AddressRepository`, nạp đầy đủ thông tin địa chỉ lấy hàng của Seller (`fromName`, `fromPhone`, `fromAddress`, `fromProvinceName`, `fromDistrictName`, `fromWardName`, `fromDistrictId`, `fromWardCode`) và địa chỉ nhận hàng của Buyer (`toName`, `toPhone`, `toAddress`, `toDistrictId`, `toWardCode`).
    - Gỡ bỏ lệnh ném `FulfillmentFeatureLockedException` mặc định trong `createShipment`.
    - **Chốt chặn Legacy Guard**: Kiểm tra `order.hasValidGhnShippingCodes()` và tính đầy đủ của địa chỉ kho; nếu thiếu, chặn fail-closed với `FulfillmentFeatureLockedException` trước khi tạo bản ghi `ShipmentEntity` PENDING hoặc gọi HTTP ra ngoài.
    - Hợp nhất dữ liệu địa chỉ chuẩn từ DB snapshot với thông số kiện hàng từ request (`weight`, `length`, `width`, `height`, `note`, `items`) thành `mergedRequest` và chuyển thẳng vào `executeShipmentCreationWithLease(...)` để thực thi quy trình tạo vận đơn có lease.
  * **Số liệu Kiểm thử Thực tế**:
    - **Backend Suite**: `.\mvnw.cmd test -Dtest="!GhnClientIntegrationTest"`: **419 passed 100%**, 0 failures, 0 errors, 46 skipped.
    - `CheckoutServiceTest`: 10/10 passed.
    - `FulfillmentServiceUnitTest`: 24/24 passed (bao gồm A1.4 Legacy Guard fail-closed và A1.5 tạo vận đơn từ DB snapshot thành công).
    - `DefaultFulfillmentCommerceFacadeTest`: 2/2 passed (kiểm tra mapping snapshot Seller/Buyer và xử lý legacy order).
    - **Frontend Suite**: `npx vitest run`: **23/23 test files passed, 96/96 tests passed 100%** (bao gồm `CheckoutView.test.tsx` 3/3 passed).
    - `npx tsc --noEmit`: 0 errors.
    - `git diff --check`: sạch 100%.

- [x] **ADMIN-01 (Màn hình/Danh sách Hàng Đợi Duyệt Thủ Công Sự Cố Shipment)**:
  * **Backend Implementation**:
    - `ShipmentRepository.java`: Bổ sung `findByAdminReviewRequiredTrueOrderByUpdatedAtDesc()` truy vấn danh sách shipment có cờ `adminReviewRequired = true`.
    - `FulfillmentDtos.java`: Mở rộng các DTOs `ShipmentIncidentResponse`, `IncidentResolutionAction` (`DISMISS_AND_RESUME`, `MARK_DELIVERED`, `MARK_RETURNED`, `MARK_FAILED`), `ResolveIncidentRequest`, `ResolveIncidentResponse`.
    - `FulfillmentStateService.java`: Triển khai `getIncidentQueue()` và `resolveIncident(orderId, action, adminNotes, adminUsername)` dưới khóa bi quan bất biến (Order TRƯỚC, Shipment SAU). Xử lý an toàn:
      + `DISMISS_AND_RESUME`: Tắt cờ `adminReviewRequired = false`, reset `reconciliationAttempts = 0`, khôi phục `nextReconciliationAt = now`, ghi nhận lịch sử xử lý vào `incidentNotes`.
      + `MARK_DELIVERED`: Kiểm tra guard xung đột terminal, chuyển shipment sang `DELIVERED`, đồng bộ Order sang `DELIVERED` qua facade, tắt cờ review.
      + `MARK_RETURNED`: Chuyển shipment sang `RETURNED` (hoặc `FAILED` nếu chưa lấy hàng), bảo toàn trạng thái monotonic, tắt cờ review.
      + `MARK_FAILED`: Chuyển shipment sang `FAILED` nếu chưa ở trạng thái terminal, tắt cờ review.
    - `AdminFulfillmentController.java`: Tạo REST Controller chuẩn RESTful tại `/api/v1/admin/fulfillment` với phân quyền nghiêm ngặt `@PreAuthorize("hasRole('ADMIN')")`, cung cấp endpoints:
      + `GET /api/v1/admin/fulfillment/incidents`: Lấy danh sách hàng đợi sự cố.
      + `POST /api/v1/admin/fulfillment/orders/{orderId}/resolve-incident`: Xử lý sự cố theo `orderId`.
      + `POST /api/v1/admin/fulfillment/incidents/{shipmentId}/resolve`: Xử lý sự cố theo `shipmentId`.
    - `SecurityConfig.java`: Cấu hình security matcher `.requestMatchers("/api/v1/admin/fulfillment/**").hasRole("ADMIN")`.
  * **Frontend Implementation**:
    - `frontend/src/features/fulfillment/fulfillmentAdminApi.ts`: Client API gọi endpoints `/admin/fulfillment/incidents` và `/orders/{orderId}/resolve-incident` thông qua `authApi.authorizedFetch`.
    - `AdminFulfillmentIncidentsPage.tsx`: Màn hình giao diện quản trị Admin chuyên biệt với các thẻ thống kê phân loại sự cố (Tổng sự cố, Hư hỏng/Mất hàng, Giao thất bại, Timeout quá hạn), thanh tìm kiếm & bộ lọc, bảng danh sách chi tiết kèm badge trạng thái, modal xử lý sự cố hỗ trợ 4 quyết định nghiệp vụ an toàn và trường nhập ghi chú căn cứ xử lý của Admin.
    - `AdminLayoutShell.tsx`: Bổ sung mục điều hướng "Sự cố vận đơn GHN" (`/admin/fulfillment-incidents`) vào thanh sidebar của Quản trị viên.
    - `App.tsx`: Đăng ký route bảo vệ `/admin/fulfillment-incidents` dành riêng cho role `ADMIN`.
  * **Số liệu Kiểm thử Thực tế**:
    - **Backend Suite**: `.\mvnw.cmd test -Dtest="!GhnClientIntegrationTest"`: **430 passed 100%**, 0 failures, 0 errors, 46 skipped.
    - `AdminFulfillmentControllerSecurityTest`: **5/5 passed** (xác nhận chặn 401 unauthenticated, chặn 403 non-admin/KTV, cho phép 200 ADMIN, xử lý resolve incident thành công).
    - `FulfillmentStateServiceTest`: **11/11 passed** (xác nhận `getIncidentQueue`, `DISMISS_AND_RESUME`, `MARK_DELIVERED`, chặn terminal conflict, `MARK_RETURNED`, `MARK_FAILED`).
    - **Frontend Suite**: `npx vitest run`: **24/24 test files passed, 99/99 tests passed 100%** (bao gồm `AdminFulfillmentIncidentsPage.test.tsx` 3/3 passed và `AdminLayoutShell.test.tsx` 1/1 passed).
    - `npx tsc --noEmit`: 0 errors.
    - `git diff --check`: sạch 100%.


