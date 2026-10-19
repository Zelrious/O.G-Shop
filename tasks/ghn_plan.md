# Kế hoạch Tích hợp GHN Sandbox (Giao Hàng Nhanh) vào O.G-Shop

> **Tài liệu tham chiếu chính thức**: `https://developer.ghn.vn/`  
> **Mục tiêu**: Tích hợp dịch vụ giao hàng GHN vào O.G-Shop (tính cước, tạo vận đơn, in phiếu giao hàng A5, tra cứu hành trình, webhook cập nhật trạng thái).

## 1. Mục tiêu và Nguyên tắc Tích hợp (Cập nhật sau Xác minh Sandbox)

### 1.1. Mục tiêu
- Tích hợp chuẩn hóa GHN API cho phân hệ Fulfillment của sàn TMĐT O.G-Shop.
- Cho phép Người bán (Seller) tạo vận đơn GHN cho đơn hàng đã xác nhận (`SELLER_CONFIRMED`).
- Cho phép Người bán in phiếu gửi hàng chuẩn A5 trực tiếp từ GHN Gateway (`printA5?token=...`).
- Cho phép Người mua (Buyer) và Người bán theo dõi hành trình đơn hàng (tracking).
- Tiếp nhận **Webhook từ GHN** khi bưu tá cập nhật trạng thái (đặc biệt khi giao thành công `delivered` -> chuyển đơn hàng sang `DELIVERED`, bắt đầu đếm ngược 3 ngày đồng kiểm; xử lý thêm hoàn/hủy/thất bại).

### 1.2. Nguyên tắc Thiết kế & Kiến trúc Đã Xác Minh Thực Tế
1. **Một Shop Chung Toàn Sàn (Marketplace Account Model)**:
   - Sàn O.G-Shop sử dụng một tài khoản GHN chung với `GHN_SHOP_ID = 229762` (đã xác minh trên Sandbox).
   - Địa chỉ kho mặc định của shop 229762 trên hệ thống GHN nằm ở đâu: **chưa xác minh** (GHN không trả về thông tin địa chỉ kho trong API tính phí hay tạo đơn; API tính cước khi bỏ `from_*` trả về 20.900đ bằng cước nội thành TP.HCM).
   - Shop cũ `226615` chưa thiết lập địa chỉ kho mặc định trên portal GHN, dẫn đến lỗi `SHOP_INFO_ERROR` khi không truyền địa chỉ lấy hàng.
   - **Quy tắc bắt buộc khi tạo đơn và tính phí**: Luôn luôn truyền đầy đủ thông tin địa chỉ lấy hàng của người bán (`from_*`) trong request payload:
     + `from_name`, `from_phone`, `from_address`
     + `from_ward_name`, `from_district_name`, `from_province_name`
     + `from_district_id` (nếu có hoặc cần thiết cho API lấy dịch vụ)
   - *Kết quả đo kiểm Sandbox thực tế*: Sandbox chỉ chứng minh GHN lưu đúng `from_*` vào đơn hàng (`return_address` và `from_address` trên vận đơn hiển thị chính xác địa chỉ của Seller); việc bưu tá thật đến lấy tại địa chỉ đó: **chưa xác minh**.
2. **Bảng Giá Cước Vận Chuyển Đã Đo Kiểm Thực Tế Trên Sandbox (Shop 229762)**:
   - *Điểm đến cố định trong bài đo*: Quận 3, TP.HCM (`to_district_id = 1444`, `to_ward_code = "20311"`).
   - *Hàng hóa mẫu*: Cân nặng 1000g, kích thước 20x15x10 cm, `service_type_id = 2` (Chuyển phát Chuẩn / E-commerce).
   - **Bảng số liệu đo thực tế**:
     | Tuyến đường (from -> to) | Địa chỉ lấy hàng (`from_*`) | HTTP Code | Cước phí GHN thực tế (`data.total`) | Ghi chú kỹ thuật |
     | :--- | :--- | :--- | :--- | :--- |
     | **Nội thành TP.HCM** | Quận 1 (`from_district_id = 1442`, `from_ward_code = "20101"`) | `200 OK` | **20.900 VNĐ** | Tuyến nội thành TP.HCM, `service_type_id = 2`. |
     | **Liên tỉnh (Đà Nẵng -> TP.HCM)** | Quận Hải Châu, Đà Nẵng (`from_district_id = 1526`, `from_ward_code = "910347"`) | `200 OK` | **53.900 VNĐ** | Tuyến liên vùng, `service_type_id = 2`. |
     | **Mặc định Shop 229762 (Bỏ `from_*`)** | *Không truyền `from_*`* | `200 OK` | **20.900 VNĐ** | GHN tự lấy kho mặc định của shop 229762. |
     | **Mặc định Shop cũ 226615 (Bỏ `from_*`)** | *Không truyền `from_*`* | `400 Bad Request` | **SHOP_INFO_ERROR** | Shop 226615 chưa cấu hình địa chỉ kho trên GHN. |
     | **Hà Nội -> TP.HCM** | Hà Nội | *Chưa đo* | *Chưa đo* | Chưa có log xác minh thực tế trên Sandbox. |
3. **Dịch Vụ Vận Chuyển Động (`available-services`)**:
   - Khi tạo đơn hoặc tính phí, hệ thống **bắt buộc gọi `POST /v2/shipping-order/available-services`** với `from_district` và `to_district` để lấy danh sách gói cước khả dụng.
   - Chọn dịch vụ theo `service_type_id`:
     + API `available-services` trả về các gói khả dụng với `service_type_id`: `1` (Nhanh), `2` (Chuẩn), `3` (Tiết kiệm).
     + Khi tạo đơn GHN: `service_type_id = 2` cho hàng nhẹ (<20kg, phù hợp với đồ công nghệ, thời trang của O.G-Shop), `service_type_id = 5` cho hàng nặng.
     + Nếu không có dịch vụ phù hợp từ `available-services`: Ném lỗi nghiệp vụ rõ ràng, không tự ý fallback âm thầm.
     + Giá trị `service_type_id` tìm được sẽ được truyền vào cả `FeeQueryRequest` (tính cước) và `CreateShippingOrderRequest` (tạo vận đơn). Hằng số mặc định `= 2` trong DTO chỉ dùng làm tiện ích phục vụ test.
     + Giữ cả `service_id` và `service_type_id` trong DTO response `AvailableServiceDto`.
     + Về `service_id`: Tài liệu GHN cho phép gửi `service_id` hoặc `service_type_id`. Khả năng bị lỗi `400 Invalid service_id` khi gửi sai tuyến là **chưa xác minh**; chuẩn ổn định nhất là sử dụng `service_type_id`.
4. **Đồng kiểm & Xem Hàng ("Cho thử hàng")**:
   - `required_note`: Bỏ hoàn toàn `"CHOXEMHANGDONGKIEM"` (do GHN báo lỗi `USER_ERR_COMMON: Sai thông tin Required Note`).
   - Sử dụng bắt buộc: `required_note = "CHOTHUHANG"`.
   - *Đã xác minh thực tế trên Sandbox*: Phiếu in A5 hiển thị dòng chữ in đậm nổi bật: **"Cho thử hàng"**. Sửa tất cả các tài liệu và nhãn in từ *"CHO XEM HÀNG VÀ ĐỒNG KIỂM"* thành *"Cho thử hàng"*.
5. **Ký quỹ Escrow & COD**:
   - Đơn hàng O.G-Shop đã thanh toán trực tuyến qua VNPAY, do đó tiền thu hộ COD gửi sang GHN luôn bằng `0 VNĐ` (`cod_amount = 0`).
   - `payment_type_id = 1` (Người gửi/Sàn thanh toán cước phí với GHN).
6. **Chống Tạo Trùng Vận Đơn & Quy Tắc Sinh Mã `client_order_code`**:
   - **Công thức sinh mã**: `OG-{ENV}-{orderId}-{randomSuffix}` (ví dụ: `OG-DEV-105-A7X9K2` hoặc `OG-PROD-10023-F3B1C8`), tổng độ dài tối đa **34 ký tự $\le 50$ ký tự** theo giới hạn API GHN:
     + `OG-`: Tiền tố cố định của sàn O.G-Shop (3 ký tự).
     + `{ENV}`: Tiền tố môi trường (`DEV`, `STG`, `PROD`) cấu hình từ biến môi trường (3–4 ký tự). Tránh xung đột mã khi chạy trên môi trường khác nhau hoặc khi DB Dev bị reset.
     + `{orderId}`: ID đơn hàng kiểu `BIGINT` trong DB O.G-Shop (tối đa 19 chữ số, ứng với `Long.MAX_VALUE = 9223372036854775807`).
     + `{randomSuffix}`: Hậu tố ngẫu nhiên ngắn (6 ký tự alphanumeric). Tuyệt đối **không dùng `timestampHex`**.
     + *Độ dài tối đa*: $3 + 4 + 1 + 19 + 1 + 6 = 34 \le 50$ ký tự.
   - **Quy tắc sinh duy nhất MỘT LẦN**:
     + `client_order_code` được sinh DUY NHẤT MỘT LẦN khi tạo bản ghi `shipment` ở trạng thái `PENDING` và lưu vào DB.
     + **Không bao giờ sinh lại mã**: Mọi lần retry gọi sang GHN (do timeout, mất kết nối mạng, lỗi 5xx, hoặc Seller bấm gửi lại) bắt buộc phải **đọc lại mã đã lưu từ DB** để truyền vào `CreateShippingOrderRequest`.
     + Khi gửi lại cùng `client_order_code`, GHN sẽ trả về thông tin vận đơn cũ đã tạo trước đó. Sàn tái sử dụng `order_code` cũ này, không tạo shipment thứ hai.
   - **Phân biệt hai trạng thái của `PENDING`**:
     + `status = 'PENDING'` và `tracking_number IS NULL`: Hệ thống mới tạo bản ghi nội bộ, chưa gọi GHN thành công. Trường hợp này **được phép retry gọi `createShippingOrder`** bằng chính `client_order_code` đã lưu.
     + `status = 'PENDING'` và `tracking_number IS NOT NULL` (đã có mã vận đơn do GHN cấp): GHN đã nhận đơn và ghi nhận thành công. Trường hợp này **tuyệt đối không gọi tạo lại**, hệ thống chuyển sang bước theo dõi bưu tá lấy hàng qua webhook hoặc tracking.
   - **Xử lý hai request đồng thời cùng tạo shipment cho một đơn (Concurrency)**:
     + Ràng buộc duy nhất `UNIQUE (order_id)` trên bảng `shipments` sẽ đảm bảo chỉ một request tạo thành công bản ghi `PENDING`.
     + Request thứ hai sẽ bị chặn bởi `DataIntegrityViolationException`, sau đó đọc lại bản ghi `shipment` đã tồn tại thay vì tạo mới.
   - **Tách biệt Transaction**: Bản ghi `shipment` (`PENDING`) được commit trước khi gọi HTTP sang GHN. Tuyệt đối không giữ DB transaction mở trong lúc thực hiện cuộc gọi HTTP tới GHN.

---

## 2. Thông tin Kết nối GHN Sandbox Hiện Tại (Đã xác minh)

- **Base URL**: `https://dev-online-gateway.ghn.vn/shiip/public-api/`
- **API Token**: Đọc từ `.env` (`GHN_API_TOKEN`), không in ra log/output.
- **Shop ID**: Đọc từ `.env` (`GHN_SHOP_ID = 229762`).
- **Trạng thái kết nối**: Đã test handshake và tạo đơn thử nghiệm thành công trên Sandbox (`L8RL38`, `L8RL44`).

---

## 3. Lộ trình Triển khai Từng Bước (Roadmap 6 Bước)

### 🔹 Bước 1: Cấu hình Spring Boot & DTO Nền tảng cho GHN
- **Mục tiêu**: Đưa cấu hình `GHN_BASE_URL`, `GHN_API_TOKEN`, `GHN_SHOP_ID` vào Spring Boot `application.yml` và khai báo các DTO chuẩn xác theo API GHN.
- **Chi tiết DTOs**:
  1. `GhnProperties.java`: `@ConfigurationProperties(prefix = "app.ghn")` bất biến bằng Java record.
  2. `GhnApiResponse.java`: Generic response bọc `code`, `message`, `data`.
  3. `GhnMasterDataDtos.java`: DTO cho Tỉnh/Thành (`ProvinceDto`), Quận/Huyện (`DistrictDto`), Phường/Xã (`WardDto`).
  4. `GhnShippingDtos.java`:
     - Thêm `@JsonInclude(JsonInclude.Include.NON_NULL)` cho toàn bộ các request record.
     - `FeeQueryRequest`: Bổ sung `service_id` (Integer) và `from_ward_code` (String).
     - `CreateShippingOrderRequest`: `required_note = "CHOTHUHANG"`, thêm `service_id`, `client_order_code` (bắt buộc `<= 50 ký tự`, `MAX_CLIENT_ORDER_CODE_LENGTH = 50`), giữ `to_district_id` + `to_ward_code`, giữ `from_*` dạng tên (phường/quận/tỉnh lấy từ master-data).
     - `AvailableServiceDto` (giữ cả `service_id` và `service_type_id`), `FeeResponseDto`, `CreateOrderResponseDto`, `OrderDetailResponseDto`, `PrintTokenResponseDto`.

### 🔹 Bước 2: Xây dựng GHN REST Client Chuẩn Hóa (`GhnClient`)
- **Mục tiêu**: Gateway giao tiếp với GHN Sandbox bằng Spring `RestClient`, an toàn, gom xử lý lỗi tập trung.
- **Quy tắc kỹ thuật**:
  1. Toàn bộ `uri` gọi API luôn bắt đầu bằng dấu `"/"` (ví dụ: `"/master-data/district"`, `"/v2/shipping-order/fee"`).
  2. Gom logic gọi HTTP `POST` và `GET` vào helper method tập trung:
     - Lỗi HTTP: Dùng `.onStatus(HttpStatusCode::isError, ...)` để ném `GhnApiException` kèm nội dung body lỗi trả về từ GHN. **Tuyệt đối không đưa API token hay header nhạy cảm vào error message**.
     - Lỗi nghiệp vụ trong body: Sau khi HTTP thành công, kiểm tra trường `code` trong body response của GHN. Nếu `code != 200` thì ném `GhnApiException` kèm message của GHN (không coi HTTP 200 là thành công nghiệp vụ). Nếu `data` bắt buộc bị null/rỗng thì xử lý như lỗi có thông điệp rõ ràng.
     - Lỗi timeout/mạng: Chỉ bắt `ResourceAccessException` cho các lỗi mạng / connection timeout / read timeout để ném `GhnApiException`.
  3. Các phương thức nghiệp vụ:
     - `getProvinces()`, `getDistricts(provinceId)`, `getWards(districtId)`
     - `getAvailableServices(fromDistrictId, toDistrictId)`: Gọi `POST /v2/shipping-order/available-services`
     - `calculateShippingFee(FeeQueryRequest request)`: Gọi `POST /v2/shipping-order/fee`
     - `createShippingOrder(CreateShippingOrderRequest request)`: Gọi `POST /v2/shipping-order/create`
     - `getOrderDetail(String orderCode)`: Gọi `POST /v2/shipping-order/detail`
     - `generatePrintToken(String orderCode)`: Gọi `POST /v2/a5/gen-token`
- **Files cần làm**:
  1. `GhnProperties.java` (đã xong).
  2. `GhnClient.java` (đã xong).
  3. `GhnClientIntegrationTest.java` (đã xong).

### 🔹 Bước 3: Mở rộng Schema Database với Flyway Migration V15
- **Mục tiêu**: Lưu trữ an toàn thông tin vận đơn GHN vào bảng `shipments`, đảm bảo idempotent, truy vết trạng thái và toàn vẹn dữ liệu.
- **Thay đổi Schema (`V15__enhance_shipments_for_ghn.sql`)**:
  - Tái sử dụng và chuẩn hóa các cột hiện có: `tracking_number VARCHAR(100)` (lưu `OrderCode` của GHN), đặt giá trị mặc định cho `carrier` (đã là `VARCHAR(100) NOT NULL` từ V1): `ALTER TABLE shipments ALTER COLUMN carrier SET DEFAULT 'GHN';`.
  - Thêm cột mới:
    + `client_order_code VARCHAR(50)`: Mã đơn đối tác gửi GHN, cấu trúc `OG-{ENV}-{orderId}-{randomSuffix}` (tối đa 34 ký tự $\le 50$, xử lý `order_id` là `BIGINT` tối đa 19 chữ số), sinh 1 lần duy nhất khi tạo shipment PENDING, lưu vào DB và mọi lần retry dùng lại mã này. Cho phép `NULL` cho dữ liệu cũ. Bắt buộc có mã ở tầng ứng dụng khi tạo shipment mới.
    + `shipping_fee NUMERIC(19,2)` (Cước phí GHN thực tế)
    + `expected_delivery_at TIMESTAMPTZ` (Thời gian dự kiến giao hàng từ GHN)
    + `raw_ghn_status VARCHAR(50)` (Trạng thái thô phản hồi từ GHN: `ready_to_pick`, `picking`, `delivering`, ...)
    + `created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP` (Thời điểm tạo bản ghi shipment)
  - Thêm các ràng buộc an toàn:
    + `CREATE UNIQUE INDEX IF NOT EXISTS uq_shipments_client_order_code ON shipments (client_order_code) WHERE client_order_code IS NOT NULL;`
  - *Lưu ý quan trọng*: Bảng sự kiện webhook `ghn_webhook_events` là quyết định thiết kế cho Phase 4 (Webhook), **KHÔNG nằm trong Migration V15**.
- **Files cần làm**:
  1. `V15__enhance_shipments_for_ghn.sql`
  2. `ShipmentEntity.java` và `ShipmentRepository.java` trong `modules/fulfillment/infrastructure/persistence/`.
  3. `ClientOrderCodeGeneratorTest.java` (hoặc test tương ứng trong service):
     - `testFormatAndRegex`: Chuỗi sinh ra khớp regex `^OG-(DEV|STG|PROD)-[0-9]+-[A-Z0-9]{6}$`.
     - `testMaxLengthWithLongMaxValue`: Kiểm tra với `orderId = Long.MAX_VALUE` (9223372036854775807, 19 chữ số), tổng độ dài đạt 34 ký tự, đảm bảo luôn $\le 50$ ký tự.
     - `testEnvironmentPrefix`: Xác nhận tiền tố thay đổi theo cấu hình (`DEV` $\rightarrow$ `OG-DEV-`, `PROD` $\rightarrow$ `OG-PROD-`).
     - `testRetryReusesPersistedCode`: Xác nhận luồng retry đọc lại mã đã lưu từ entity, không sinh mã mới.

### 🔹 Bước 4: Mở rộng Trạng thái Đơn hàng trong `OrderEntity` & Facade
- **Mục tiêu**: Điều phối chuyển trạng thái đơn hàng đồng bộ với vận chuyển.
- **Quy tắc chuyển trạng thái**:
  - Khi Seller tạo vận đơn GHN thành công: Vận đơn ở GHN có trạng thái ban đầu là `ready_to_pick` (chờ lấy hàng). Trạng thái đơn hàng `OrderEntity` giữ `SELLER_CONFIRMED` hoặc chuyển sang `READY_TO_SHIP`, và **chỉ chuyển sang `SHIPPED` khi bưu tá đã đến lấy hàng thành công (qua Webhook GHN thông báo trạng thái `picked`)** (quyết định đã chốt, thống nhất với bảng map ở Bước 6).
  - Khi bưu tá giao hàng thành công: Chuyển `SHIPPED` -> `DELIVERED`, kích hoạt countdown 3 ngày kiểm tra hàng (co-inspection window).
- **Files cần làm**:
  1. `OrderEntity.java`: Thêm method `markShipped(Instant now)` và `markDelivered(Instant now)`.
  2. Facade/Service: `FulfillmentCommerceFacade.java` phục vụ giao tiếp liên module.

### 🔹 Bước 5: Xây dựng Service Nghiệp vụ & Controller Fulfillment
- **Mục tiêu**: Cung cấp API cho Seller tạo vận đơn, lấy link in phiếu A5 và tra cứu tracking.
- **Quy trình tạo vận đơn chống trùng (Anti-duplication Flow)**:
  1. Kiểm tra đơn hàng có trạng thái `SELLER_CONFIRMED`.
  2. Kiểm tra chưa tồn tại shipment thành công cho `orderId`.
  3. Lấy địa chỉ lấy hàng của seller: chờ ADDR-01, chưa có nguồn dữ liệu.
  4. Lấy địa chỉ giao hàng của buyer (`to_district_id`, `to_ward_code`).
  5. Gọi GHN lấy danh sách dịch vụ qua `getAvailableServices`, chọn `service_type_id` phù hợp theo tuyến đường và loại hàng (hàng nhẹ = 2). **Nếu không có dịch vụ phù hợp trả về -> ném lỗi nghiệp vụ rõ ràng**.
  6. Lưu bản ghi `shipment` ở trạng thái `PENDING` với `carrier = 'GHN'`, kèm `client_order_code` (sinh duy nhất 1 lần dạng `OG-{ENV}-{orderId}-{randomSuffix}`, lưu DB, mọi lần retry đọc lại mã đã lưu này) và commit transaction.
  7. Thực hiện cuộc gọi HTTP `createShippingOrder` ra ngoài GHN (không giữ DB lock/connection).
  8. Xử lý phản hồi từ GHN:
     - **Quy tắc bảo vệ trạng thái khi cập nhật**:
       + Thread tạo đơn ban đầu thực thi câu lệnh: `UPDATE shipments SET tracking_number = ?, ... WHERE shipment_id = ? AND status = 'PENDING'`.
       + **Nếu số dòng cập nhật = 0 (Webhook đã cập nhật trước)**: Không coi là lỗi (do webhook đã chạy trước thành công); đọc lại bản ghi `shipment` từ DB và xác nhận `tracking_number` có trùng với mã `order_code` mà GHN vừa trả về hay không. Nếu trùng khớp, tiếp tục luồng bình thường. Nếu khác nhau, ghi log cảnh báo nghiêm trọng (`WARN/ERROR: Tracking number mismatch`) và tuyệt đối không ghi đè dữ liệu mà webhook đã cập nhật.
       + **Nếu số dòng cập nhật = 1**: Cập nhật các trường `tracking_number = order_code`, `status = 'PENDING'` (status giữ `PENDING`, không đặt `PICKED_UP` vì `shipped_at` còn `NULL`), `raw_ghn_status = 'ready_to_pick'`, `shipping_fee`, `expected_delivery_at`.
       + **Nếu GHN trả về đơn cũ** (trường hợp gửi lại `client_order_code` đã dùng): Coi là thành công, tái sử dụng `order_code` cũ được GHN trả về, tuyệt đối không tạo thêm shipment thứ hai trong DB.
- **Endpoints Controller**:
  - `POST /api/v1/fulfillment/orders/{orderId}/create-shipment`: Seller tạo vận đơn GHN.
  - `GET /api/v1/fulfillment/orders/{orderId}/print-waybill`: Sinh token A5 động từ GHN mỗi lần in và trả về URL in phiếu (`https://dev-online-gateway.ghn.vn/a5/public-api/printA5?token=...`).
  - `GET /api/v1/fulfillment/orders/{orderId}/tracking`: Tra cứu hành trình vận đơn.
  - `GET /api/v1/shipping/master-data/provinces`, `/districts`, `/wards`: Tra cứu danh mục địa chỉ GHN.

### 🔹 Bước 6: Xử lý Webhook GHN (Bảo mật & Tự động cập nhật)
- **Cơ chế Đăng ký & Bảo mật Webhook (Theo tài liệu chính thức `developer.ghn.vn`)**:
  - *Cấu hình trên Developer Portal*: Chủ dự án tự cấu hình trực tiếp tại cổng GHN Developer Portal (mục **Webhook configuration**, tab **Order**).
  - *Môi trường độc lập*: Staging (`developer.ghn.dev`) và Production (`developer.ghn.vn`) là hai tài khoản riêng biệt, phải cấu hình độc lập cho từng môi trường.
  - *Thời gian hiệu lực*: Cấu hình sau khi lưu trên portal sẽ có hiệu lực sau khoảng **15 phút**.
  - *Xác thực*: Hỗ trợ thiết lập **Custom Header** (ví dụ: `X-Webhook-Secret`) để gửi secret key xác thực request. Tài liệu GHN chính thức **hoàn toàn không có chữ ký HMAC** được mô tả.
  - *Sự kiện kích hoạt*: Bắt buộc chọn bật đầy đủ các sự kiện `switch_status` (và `create`).
- **Quy tắc Phản hồi & Cơ chế Retry từ GHN (`developer.ghn.vn/en/docs/webhook/callback-order-status`)**:
  - `2xx`: GHN ghi nhận callback thành công.
  - `4xx` (ngoại trừ `408 Request Timeout` và `429 Too Many Requests`): GHN coi là lỗi vĩnh viễn (client error), **loại bỏ sự kiện ngay lập tức khỏi hàng đợi và KHÔNG retry**.
  - `5xx` / `408` / `429` / Timeout: GHN coi là lỗi tạm thời, sẽ thực hiện retry theo cơ chế **exponential backoff** với số lần retry cấu hình từ **1 đến 5 lần** trên portal; khi hết số lần retry cho phép (hết cap) mà không thành công thì sự kiện bị loại bỏ (dropped).
- **Kiến trúc Endpoint Xử Lý Nhanh & Quyết Định Thiết Kế Bảng `ghn_webhook_events` (Chỉ ghi vào Plan, KHÔNG nằm trong V15 - thực hiện ở Phase 4)**:
  - *Nguyên tắc phản hồi nhanh*: Endpoint chỉ làm nhiệm vụ kiểm tra custom header secret, parse DTO an toàn, lọc bỏ trường thông tin cá nhân, lưu sự kiện thô bền vững vào database rồi **trả ngay mã `2xx`** (đảm bảo hoàn tất nhanh, an toàn trước ngưỡng timeout mặc định 5 giây của GHN). Mọi xử lý nặng (đồng bộ trạng thái shipment, cập nhật Order, gửi thông báo, kích hoạt countdown...) được đưa ra xử lý sau (asynchronous worker / background job / event listener).
  - *Bảng lưu trữ sự kiện `ghn_webhook_events` (Phase 4)*:
    + Khử trùng lặp sự kiện theo tổ hợp khóa duy nhất: `(order_code, type, event_time)`.
    + **Lọc bỏ dữ liệu cá nhân (PII)**: Lọc bớt các trường thông tin cá nhân của bưu tá (`ShipperName`, `ShipperPhone`) trước khi lưu payload JSON nhằm bảo vệ quyền riêng tư và tuân thủ an toàn thông tin.
  - *Quy tắc phản hồi HTTP của Endpoint*:
    + Sự kiện trùng lặp đã xử lý $\rightarrow$ Trả `2xx`.
    + Trạng thái lạ nhưng đã ghi nhận bền vững vào DB $\rightarrow$ Trả `2xx`.
    + Sự kiện không khớp shipment trong hệ thống $\rightarrow$ **Ghi nhận bền vững vào bảng sự kiện đối soát rồi trả `2xx`**. Tuyệt đối **KHÔNG BAO GIỜ trả 4xx** cho sự kiện không khớp (vì trả 4xx sẽ khiến GHN loại bỏ vĩnh viễn sự kiện callback, không thể phục hồi); và **không chỉ ghi log đơn thuần** mà bắt buộc phải lưu trữ bền vững vào DB để phục vụ đối soát.
    + Database / Hệ thống nội bộ tạm thời gặp sự cố không lưu được bản ghi $\rightarrow$ Trả `5xx` (để kích hoạt cơ chế retry backoff từ GHN).
- **Đặc tả DTO Webhook & Xử Lý Null/Empty**:
  - Tài liệu GHN quy định: Mọi trường trong payload JSON luôn luôn có mặt đầy đủ. Những trường chưa có giá trị được trả về dưới dạng chuỗi rỗng `""`, số `0` hoặc `null`.
  - Record/Class DTO webhook trong Java và logic parsing phải được thiết kế an toàn, chủ động xử lý `null`/chuỗi rỗng/số 0 để không gây lỗi `NullPointerException` hoặc lỗi parse JSON.
- **Quy tắc Lọc `Type` và Bảng Map Trạng Thái**:
  - **Chỉ `Type = 'create'` và `Type = 'switch_status'` mới được phép cập nhật `status` của shipment và đơn hàng**. Danh sách `Type` chuẩn theo tài liệu GHN: `create`, `switch_status`, `update_weight`, `update_cod`, `update_fee`, `update_payment_type`, `cod`, `update_partial_return`. Các `Type` khác ngoài `create` và `switch_status` chỉ ghi nhận vào bảng sự kiện để kiểm toán/cập nhật trường phụ, tuyệt đối không thay đổi trạng thái shipment.
  - *Ngữ nghĩa trường `Time` của GHN*:
    + Trường `Time` trong payload Webhook là **thời điểm cập nhật trạng thái gần nhất của đơn hàng trên hệ thống GHN (chuỗi ISO-8601)**, **KHÔNG PHẢI thời điểm bưu tá lấy hàng**. Tuyệt đối không dùng `NOW()` của server backend để thay thế.
  - *Quy tắc gán `shipped_at` thống nhất cho mọi đường chuyển*:
    + Mọi luồng chuyển sang `PICKED_UP`, `IN_TRANSIT` hoặc `RETURNED` (bao gồm `delivery_fail` và các trạng thái trả hàng `return`, `return_transporting`, `return_sorting`, `returning`, `returned`) đều bắt buộc qua quy tắc gán `shipped_at`: dùng `Time` của sự kiện `picked` nếu nhận được; nếu sự kiện `picked` bị bỏ lỡ hoặc đến muộn, dùng `Time` của sự kiện đầu tiên đưa shipment vào một trong các trạng thái đó và ghi nhận đây là **giá trị xấp xỉ** (để thỏa mãn ràng buộc database `ck_shipments_times`).
  - *Mapping các trạng thái đã xác định*:
    + `ready_to_pick`, `picking`, `money_collect_picking`: Map sang `PENDING` (`shipped_at = NULL`, `delivered_at = NULL`).
    + `picked`, `storing` *(kho GHN sau khi lấy)*, `sorting` *(đang phân loại)*: Map sang **`PICKED_UP`**. Set `shipped_at` theo quy tắc thời gian nêu trên. Đơn hàng `OrderEntity` chuyển sang `SHIPPED`.
    + `transporting` *(vận chuyển liên kho)*, `delivering` *(đang giao)*, `money_collect_delivering`: Map sang **`IN_TRANSIT`**. Set `shipped_at` (xấp xỉ nếu chưa có).
    + `delivery_fail`: Giao hàng không thành công một lần (GHN sẽ tự động phát lại). Map sang **`IN_TRANSIT`** (giữ nguyên trạng thái vận chuyển), chỉ cập nhật `raw_ghn_status = 'delivery_fail'`, áp dụng quy tắc gán `shipped_at` nếu chưa có.
    + `delivered`: Map sang `DELIVERED`, set `shipped_at = COALESCE(shipped_at, GHN Time)`, set `delivered_at = GHN Time`. Đơn hàng chuyển sang `DELIVERED`, kích hoạt countdown 3 ngày đồng kiểm.
    + `return`, `return_transporting`, `return_sorting`, `returning`: Hàng đang trong quá trình chuyển hoàn về người gửi. Map sang **`IN_TRANSIT`**, cập nhật `raw_ghn_status`, áp dụng quy tắc gán `shipped_at` nếu chưa có.
    + `returned`: Hàng đã hoàn tất trả về người gửi. Nếu `shipped_at != null` $\rightarrow$ Map sang `RETURNED` (set `delivered_at = NULL`); nếu `shipped_at == null` $\rightarrow$ Map sang `FAILED` (đảm bảo tuân thủ `ck_shipments_times`).
    + `cancel`: Map sang `FAILED`, thông báo cho seller.
  - *Hành vi mặc định cho trạng thái lạ / chưa biết*:
    + Nếu nhận trạng thái không nằm trong danh mục: **Giữ nguyên `status` hiện tại của shipment**, chỉ cập nhật `raw_ghn_status = payload.Status` và ghi log cảnh báo (`WARN: Received unknown GHN status`).
  - *Quy tắc không lùi trạng thái (State Machine Guard)*:
    + Cấp bậc trạng thái: `PENDING` (1) < `PICKED_UP` (2) < `IN_TRANSIT` (3) < `DELIVERED` / `RETURNED` / `FAILED` (4 - kết thúc).
    + Nếu trạng thái hiện tại trong DB có cấp bậc lớn hơn hoặc bằng cấp bậc của sự kiện webhook đến (do webhook đến trễ hoặc gửi lại) $\rightarrow$ **tuyệt đối không cập nhật lùi `status`**, chỉ ghi nhận `raw_ghn_status` và log kiểm toán. Không bao giờ đảo ngược từ trạng thái kết thúc về trạng thái đang chạy.
  - *Quy tắc xử lý các trạng thái đặc thù (Đã chốt quyết định thiết kế)*:
    + `waiting_to_return` (Chờ chuyển hoàn sau khi giao thất bại nhiều lần): **Giữ `status = IN_TRANSIT`**, chỉ cập nhật `raw_ghn_status = 'waiting_to_return'`.
    + `return_fail` (Trả hàng về cho Seller thất bại): **Giữ `status = IN_TRANSIT`**, cập nhật `raw_ghn_status = 'return_fail'` và **gắn cờ cảnh báo Seller** (thông báo bưu tá không thể hoàn trả hàng).
    + `damage` (Hàng hư hỏng), `lost` (Hàng thất lạc), `scrap` (Tiêu hủy), `exception` (Sự cố bưu tá): **Giữ nguyên `status` hiện tại của shipment**, cập nhật `raw_ghn_status`, **đưa shipment vào hàng đợi duyệt thủ công của Admin** (các đơn này sẽ nằm ở trạng thái cũ cho tới khi Admin can thiệp xử lý). Hệ thống **tuyệt đối KHÔNG tự khóa escrow, KHÔNG tự hoàn tiền, KHÔNG tự mở khiếu nại**.
- **Thứ tự Tra cứu Shipment & Xử Lý Đồng Bộ**:
  - *Thứ tự tra cứu 3 bước*:
    1. **(a) Tìm theo `tracking_number = webhook.OrderCode`**.
    2. **(b) Tìm theo `client_order_code = webhook.ClientOrderCode`** (xử lý race condition khi webhook đến trước khi thread HTTP kịp ghi `tracking_number` vào DB). Khi tìm thấy shipment ở trạng thái `PENDING`, tự động cập nhật `tracking_number = webhook.OrderCode`.
    3. **(c) Không khớp hoặc trả về nhiều kết quả**: Ghi nhận bền vững vào bảng sự kiện đối soát (`ghn_webhook_events`) kèm log cảnh báo (`WARN: Webhook event unmatched or multiple shipments found`), **tuyệt đối KHÔNG tự tạo shipment**.
  - *Quy tắc không ghi đè từ thread HTTP*: Thread HTTP tạo đơn ban đầu chỉ cập nhật shipment khi trạng thái còn là `PENDING`. Nếu webhook đã cập nhật trước (UPDATE trả về 0 dòng), thread đọc lại DB để xác nhận `tracking_number` trùng khớp với `order_code` GHN trả về, không ghi đè lùi trạng thái.
  - *Idempotency*: Kiểm tra trạng thái hiện tại của `ShipmentEntity` trước khi update; nếu đã ở trạng thái tương đương hoặc cao hơn thì bỏ qua, trả về HTTP 200 OK ngay lập tức.
- **Job Đối Soát Định Kỳ (Reconciliation Scheduled Job)**:
  - Xây dựng một tác vụ định kỳ (mỗi 30–60 phút) tự động quét các shipment chưa về trạng thái cuối (`status NOT IN ('DELIVERED', 'RETURNED', 'FAILED')`), gọi API tra cứu Order Info (`POST /v2/shipping-order/detail`) của GHN để đối soát và cập nhật bù nếu webhook bị bỏ lỡ (phòng ngừa trường hợp GHN loại bỏ callback sau khi hết cap retry hoặc sự cố mạng diện rộng).

---

## 4. Các Hạng Mục "Chưa Xác Minh" & "Câu Hỏi Còn Mở" (Mở cho Người Dùng Quyết Định)

### 4.1. Hạng mục "Chưa xác minh" (Không suy đoán)
1. **Thời hạn tồn tại của Token In A5 (`gen-token`)**: Payload trả về từ GHN chỉ chứa trường `token` (chuỗi UUID), tài liệu GHN không ghi thời hạn của print token -> **Chưa xác minh**. *(Giải pháp thiết kế: không lưu token trong DB, sinh token mới mỗi lần in)*.
2. **Khả năng bưu tá thật đến lấy hàng tại địa chỉ `from_address` tự chọn**: Sandbox đã chứng minh GHN lưu chính xác và tính cước theo `from_*` của seller (Đà Nẵng; các tỉnh/thành khác như Hà Nội: chưa xác minh tuyến cụ thể), tuy nhiên việc bưu tá ngoài đời thực có đến lấy tận nơi theo địa chỉ seller khi dùng tài khoản shop chung của sàn hay không -> **Chưa xác minh** (cần xác nhận với hỗ trợ kỹ thuật / kinh doanh của GHN khi ký hợp đồng doanh nghiệp).
3. **Cấu trúc địa chỉ 3 cấp trên môi trường Production**: Cấu trúc địa chỉ 3 cấp (`to_district_id` + `to_ward_code`, và `from_*` dạng tên) mới chỉ được xác minh trên Sandbox -> **Production: chưa xác minh**. Cần kiểm tra lại với tài liệu GHN mới nhất trước khi go-live vì GHN có thể đã đổi hệ địa chỉ.
4. **Cơ chế chữ ký số (Webhook Signature) của GHN**: Tài liệu GHN public không đề cập đến chữ ký HMAC trong webhook -> **Chưa xác minh**.
5. **Kho của shop 229762 nằm ở đâu**: API tính phí khi bỏ `from_*` trả về thành công cước 20.900đ (bằng cước nội thành TP.HCM), nhưng response của GHN không trả về trường dữ liệu địa chỉ kho -> **Chưa xác minh** (trừ khi có tài liệu quản trị tài khoản Sandbox chứng minh).
6. **Địa chỉ lấy hàng của Seller & Quản lý địa chỉ cũ**:
   - *Đã xác minh trong code*:
     + Màn hình đăng bán (`ListingForm.tsx`): Seller chỉ nhập một trường chuỗi tự do `location` (ví dụ "Hà Nội"), lưu vào `products.location VARCHAR(255)`. Hoàn toàn không có thao tác chọn địa chỉ 3 cấp hay liên kết sổ địa chỉ.
     + Quản lý địa chỉ (`profileApi.ts`, `AddressEntity.java`): Bảng `addresses` chỉ đóng vai trò sổ địa chỉ nhận hàng cá nhân của người dùng (`user_id`, `recipient_name`, `phone_number`, `province`, `district`, `ward`, `detail_address`, `is_default`).
     + Hiện trạng Checkout (`CheckoutService.java:214`): Khi Buyer đặt mua `buyNow(...)`, mã nguồn hiện tại đang lấy `address.id()` của Buyer (địa chỉ nhận hàng) và truyền vào tham số `sourceAddressId` của `new OrderEntity(...)`.
   - *Phần là giả định (Chưa xác minh trong code)*:
     + "Seller chọn địa chỉ lấy hàng ở đâu?": **Chưa xác minh**. Hiện tại trong codebase **hoàn toàn chưa có màn hình/API nào để Seller chọn, cấu hình hoặc lưu địa chỉ lấy hàng riêng biệt**. Mọi suy luận rằng Seller lấy từ `addresses WHERE is_default = true` chỉ là giả định kiến trúc cho tương lai.
   - *Kế hoạch cho dữ liệu địa chỉ cũ & Cơ chế chặn checkout*:
     + Khi thêm các cột `ghn_province_id`, `ghn_district_id`, `ghn_ward_code` vào `addresses` (Migration `V16`), các bản ghi cũ sẽ nhận giá trị `NULL`. **Tuyệt đối không tự gán mã GHN dựa trên tên địa chỉ cũ** để tránh sai sót hành chính.
     + **Chặn tính phí và checkout khi thiếu mã GHN**: Cả Backend (`CheckoutService`) và Giao diện (Frontend Checkout/Address modal) bắt buộc phải kiểm tra `ghn_district_id` và `ghn_ward_code`. Nếu địa chỉ người mua hoặc người bán thiếu mã chuẩn GHN, hệ thống sẽ chặn không cho tính cước vận chuyển GHN / không cho bấm đặt hàng, đồng thời hiển thị thông báo yêu cầu người dùng cập nhật lại địa chỉ qua dropdown chuẩn của GHN.

### 4.2. Câu hỏi Còn Mở (Design Decisions Cần Chốt)
1. **Chính sách Trả Cước Vận Chuyển (Who Pays Shipping Fee?)**:
   - Khi tạo đơn với `payment_type_id = 1`, GHN sẽ khấu trừ toàn bộ cước vận chuyển vào tài khoản GHN của Sàn O.G-Shop.
   - *Lựa chọn cần quyết định*:
     - (A) Sàn thu tiền cước từ Người mua (Buyer) lúc thanh toán VNPAY, sau đó sàn dùng khoản thu này trả cước cho GHN.
     - (B) Người bán (Seller) chịu phí vận chuyển: Sàn tự động trừ cước vận chuyển thực tế khỏi số tiền thanh toán giải ngân (Seller proceeds) khi đơn hàng hoàn tất.
     - (C) Sàn tài trợ / miễn phí vận chuyển cho người dùng (Chính sách Freeship marketing).
2. **Nguồn dữ liệu Cân nặng và Kích thước Kiện hàng**:
   - Khi tính phí và tạo đơn, GHN bắt buộc có `weight`, `length`, `width`, `height`.
   - *Lựa chọn cần quyết định*:
     - Lấy từ trường dữ liệu cân nặng/kích thước trong `ProductEntity` khi seller đăng bán đồ cũ?
     - Hay áp dụng cân nặng/kích thước mặc định chuẩn (ví dụ: máy ảnh/đồ công nghệ: 1000g, 20x15x10 cm)?
3. **Thời điểm Chuyển Trạng thái `SHIPPED` của Đơn hàng (ĐÃ CHỐT)**:
   - **Quyết định đã chốt**: Không chuyển `SHIPPED` ngay khi tạo vận đơn. Đơn hàng `OrderEntity` chỉ chuyển sang `SHIPPED` khi Webhook GHN gửi callback báo bưu tá đã lấy hàng thành công với trạng thái `picked` (thống nhất với bảng map ở Bước 6).
4. **Kiểm tra Hệ thống Địa chỉ trước khi Go-live**:
   - Cần rà soát đối chiếu tài liệu Production mới nhất của GHN để xác nhận xem Production có yêu cầu mã hóa địa chỉ mới (như chuẩn địa chỉ mới của GHN) hay vẫn sử dụng hoàn toàn cấu trúc 3 cấp như Sandbox.
5. **Đăng ký Callback URL & Cấu hình Xác thực Webhook với GHN**:
   - Việc đăng ký Endpoint Callback URL và cấu hình phương thức xác thực (Basic Authentication username/password hoặc Custom Header mang API-Key bí mật) trên cổng GHN Developer Portal (riêng biệt cho môi trường Sandbox tại `developer.ghn.dev` và Production tại `developer.ghn.vn`) **là công việc cấu hình trực tiếp của Chủ dự án**.
   - Tài liệu GHN chính thức **hoàn toàn không mô tả bất kỳ cơ chế chữ ký HMAC nào** trên payload Webhook. Do đó, hệ thống phụ thuộc vào bí mật cấu hình trên portal GHN và cơ chế xác minh chủ động (gọi `/detail` đối chiếu khi cần).

---

## 5. Kiến trúc Đối Soát & Phục Hồi Timeout Vận Đơn (Task 1 Architecture Review)

### 5.1. Cơ chế Claim-Token Fencing
- Tách bạch trường `claim_expires_at` (lease ngắn hạn 30s tranh chấp giữa các worker) khỏi `next_reconciliation_at` (khoảng cách exponential backoff khi chưa tìm thấy đơn trên GHN).
- Toàn bộ các thao tác giải phóng claim (`releaseClaim`, `releaseClaimWithBackoff`, `recordIncidentAndReleaseClaim`) và ghi nhận kết quả đối soát thành công (`recordReconciledTracking`) đều áp dụng mệnh đề điều kiện nguyên tử:
  `WHERE s.orderId = :orderId AND s.claimToken = :claimToken`.
- Bảo đảm worker cũ khi bị quá hạn lease (mất quyền sở hữu) không thể ghi đè claim, không thể xóa claim của worker mới, không thể rollback trạng thái shipment hay ghi đè dữ liệu incident/backoff.

### 5.2. Chuyển Trạng Thái Đơn Điệu (Monotonic Transitions) & Phòng Ngự TOCTOU
- Loại bỏ hoàn toàn các lời gọi `shipmentRepository.save(shipment)` full-entity trong `GhnReconciliationScheduler.applyReconciliationStatus()`.
- Thay thế bằng các câu lệnh UPDATE nguyên tử có điều kiện cấp database: `updateRawGhnStatusIfPending` (chỉ cập nhật khi `status = 'PENDING'`), `updateRawGhnStatusIfNotTerminal`, `recordIncidentIfNotTerminal` (bảo vệ các đơn terminal `DELIVERED`, `RETURNED`, `FAILED`).
- Triệt tiêu hoàn toàn lỗi Lost Update / TOCTOU do `ShipmentEntity` không có `@Version` khi có Webhook xen ngang giữa lúc đọc và cập nhật.
- Bổ sung cấu hình `@Modifying(clearAutomatically = true, flushAutomatically = true)` cho toàn bộ các truy vấn modifying trong `ShipmentRepository`, bảo đảm Persistence Context (1st-level cache) luôn được xóa sạch sau khi thực thi câu query, triệt tiêu rủi ro đọc dữ liệu stale mang `trackingNumber = null` từ bộ nhớ đệm JPA.
- Bảo đảm nhánh on-demand `FulfillmentService.getTracking()` là hoàn toàn read-only sau khi ghi nhận mã vận đơn, không can thiệp trạng thái DB và luôn phản ánh trạng thái mới nhất từ Webhook.

### 5.3. Chuẩn Hóa Địa Chỉ GHN, Sửa Gán Nguồn Kho & Mở Khóa Tạo Vận Đơn (ADDR-01 -> ADDR-03)
1. **Migration Schema V18 (`V18__add_ghn_address_codes.sql`)**:
   - Thêm `ghn_province_id`, `ghn_district_id`, `ghn_ward_code` vào bảng `addresses`.
   - Thêm `shipping_ghn_province_id`, `shipping_ghn_district_id`, `shipping_ghn_ward_code` vào bảng `orders` (snapshot cố định khi đặt hàng).
2. **Sổ địa chỉ GHN (ADDR-01)**:
   - Cascading Select 3 cấp (Tỉnh/Thành -> Quận/Huyện -> Phường/Xã) kết nối trực tiếp API Master Data GHN.
   - Bắt buộc kiểm tra tính toàn vẹn All-or-Nothing của mã GHN khi thêm/sửa địa chỉ.
   - Hiển thị badge trạng thái chuẩn GHN và thông báo địa chỉ mặc định được dùng làm kho lấy hàng của Seller.
3. **Sửa gán địa chỉ nguồn Seller & Snapshot GHN (ADDR-02)**:
   - Sửa `CheckoutService.buyNow`: thay thế gán nhầm `address.id()` của Buyer bằng `sellerAddress.id()` của Seller vào `OrderEntity.sourceAddressId`.
   - Lưu snapshot đầy đủ 3 mã GHN từ địa chỉ nhận hàng của Buyer vào `OrderEntity`.
4. **Chặn Checkout thiếu mã GHN & Mở khóa tạo vận đơn (ADDR-03)**:
   - **Backend Guard**: Chặn checkout nếu Buyer thiếu mã GHN hợp lệ hoặc Seller chưa có địa chỉ lấy hàng mặc định chuẩn GHN.
   - **Frontend Guard**: Hiển thị badge GHN, cảnh báo khi chọn địa chỉ cũ thiếu mã GHN, vô hiệu hóa nút đặt mua, và hỗ trợ tạo địa chỉ mới với cascading dropdown 3 cấp ngay tại màn hình Checkout.
   - **Mở khóa `FulfillmentService.createShipment`**: Kết nối `DefaultFulfillmentCommerceFacade` đọc địa chỉ kho Seller từ bảng `addresses` và địa chỉ nhận hàng Buyer từ `OrderEntity`. Áp dụng **Legacy Guard** fail-closed đối với các đơn hàng cũ thiếu mã GHN, và chuyển thẳng các đơn chuẩn hóa vào `executeShipmentCreationWithLease` để tạo vận đơn GHN thật.

### 5.4. Hàng Đợi Duyệt Thủ Công Sự Cố Vận Đơn (ADMIN-01)
1. **Mục tiêu Nghiệp vụ**:
   - Xử lý các sự cố vận chuyển ngoại lệ (`damage`, `lost`, `scrap`, `exception`, hoặc đối soát quá số lần quy định) mà hệ thống tự động không thể tự quyết định.
   - Vận đơn được gắn cờ `admin_review_required = true`, dừng tự động polling để tránh xung đột dữ liệu, chuyển vào hàng đợi kiểm tra thủ công của Admin.
2. **Kiến trúc Backend & Bảo đảm Khóa Bi quan**:
   - Toàn bộ thao tác xử lý sự cố trong `FulfillmentStateService.resolveIncident(...)` đều tuân thủ nguyên tắc khóa bất biến: Khóa Order TRƯỚC (`lockOrderForFulfillment`), Khóa Shipment SAU (`findByOrderIdForUpdate`).
   - Hỗ trợ 4 hành động giải quyết an toàn (`IncidentResolutionAction`):
     + `DISMISS_AND_RESUME`: Tắt cờ sự cố, xóa số lần thử lại (`reconciliationAttempts = 0`), đặt `nextReconciliationAt = now` để scheduler/webhook tiếp tục chạy tự động, lưu ghi chú của Admin.
     + `MARK_DELIVERED`: Kiểm tra guard chống xung đột trạng thái terminal (`hasTerminalConflictWithDelivered`), chuyển shipment và đồng bộ Order sang `DELIVERED`, tắt cờ sự cố.
     + `MARK_RETURNED`: Kiểm tra tính hợp lệ monotonic, chuyển shipment sang `RETURNED` (hoặc `FAILED` nếu chưa bưu tá lấy hàng), tắt cờ sự cố.
     + `MARK_FAILED`: Chuyển shipment sang `FAILED` nếu chưa ở trạng thái terminal, tắt cờ sự cố.
   - Endpoint RESTful `/api/v1/admin/fulfillment/incidents` và `/api/v1/admin/fulfillment/orders/{orderId}/resolve-incident` được bảo vệ nghiêm ngặt bằng `@PreAuthorize("hasRole('ADMIN')")` và cấu hình `SecurityConfig`.
3. **Giao diện Quản trị Frontend**:
   - Trang quản trị chuyên biệt `AdminFulfillmentIncidentsPage.tsx` tích hợp thanh thống kê thẻ phân loại sự cố, bộ lọc trạng thái, tìm kiếm tức thì, bảng dữ liệu chi tiết và Modal xử lý sự cố trực quan kèm ghi chú lý do của Admin.
   - Điều hướng tích hợp trên thanh sidebar của `AdminLayoutShell.tsx` và route bảo vệ `/admin/fulfillment-incidents`.


