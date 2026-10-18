package com.oldbutgold.shop.modules.fulfillment.application;

import com.oldbutgold.shop.modules.commerce.application.FulfillmentCommerceFacade;
import com.oldbutgold.shop.modules.commerce.application.FulfillmentCommerceFacade.OrderFulfillmentSnapshot;
import com.oldbutgold.shop.modules.fulfillment.api.FulfillmentDtos.CreateShipmentRequest;
import com.oldbutgold.shop.modules.fulfillment.api.FulfillmentDtos.PrintWaybillResponse;
import com.oldbutgold.shop.modules.fulfillment.api.FulfillmentDtos.ShipmentResponse;
import com.oldbutgold.shop.modules.fulfillment.api.FulfillmentDtos.TrackingResponse;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.ClientOrderCodeGenerator;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.GhnApiException;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.GhnClient;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.GhnProperties;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnMasterDataDtos;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnShippingDtos.AvailableServiceDto;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnShippingDtos.CreateOrderResponseDto;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnShippingDtos.CreateShippingOrderRequest;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnShippingDtos.OrderDetailResponseDto;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnShippingDtos.OrderItemDto;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnShippingDtos.PrintTokenResponseDto;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.persistence.ShipmentEntity;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.persistence.ShipmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.Optional;

@Service
public class FulfillmentService {

    private static final Logger log = LoggerFactory.getLogger(FulfillmentService.class);
    private static final int COOLDOWN_SECONDS = 10;
    public static final int LEASE_DURATION_SECONDS = 30;
    private static final int MAX_LIGHT_WEIGHT_GRAMS = 20000;
    private static final int SERVICE_TYPE_LIGHT = 2; // Chuyển phát chuẩn cho hàng nhẹ (<20kg)
    private static final int SERVICE_TYPE_HEAVY = 5; // Hàng nặng

    private final FulfillmentCommerceFacade commerceFacade;
    private final ShipmentRepository shipmentRepository;
    private final GhnClient ghnClient;
    private final GhnProperties ghnProperties;
    private final ClientOrderCodeGenerator clientOrderCodeGenerator;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public FulfillmentService(
            FulfillmentCommerceFacade commerceFacade,
            ShipmentRepository shipmentRepository,
            GhnClient ghnClient,
            GhnProperties ghnProperties,
            ClientOrderCodeGenerator clientOrderCodeGenerator,
            TransactionTemplate transactionTemplate,
            Clock clock
    ) {
        this.commerceFacade = commerceFacade;
        this.shipmentRepository = shipmentRepository;
        this.ghnClient = ghnClient;
        this.ghnProperties = ghnProperties;
        this.clientOrderCodeGenerator = clientOrderCodeGenerator;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    /**
     * Tạo vận đơn GHN cho đơn hàng đã xác nhận (SELLER_CONFIRMED).
     *
     * STAGE A1: Khóa an toàn tính năng tạo vận đơn thực tế ở phía server cho tới khi
     * các nguồn dữ liệu tin cậy (địa chỉ kho người bán ADDR-01, mã GHN người nhận,
     * cân nặng/kích thước chuẩn) được triển khai đầy đủ.
     *
     * - Kiểm tra quyền sở hữu của Seller và trạng thái Order trước tiên.
     * - Nếu đơn hàng đã có vận đơn GHN hợp lệ từ trước, trả về kết quả hiện hữu (idempotency).
     * - Nếu chưa có, ném FulfillmentFeatureLockedException (409 Conflict) để fail closed.
     * - Tuyệt đối không gọi GHN API, không tạo ShipmentEntity PENDING khi dữ liệu chưa xác minh.
     */
    public ShipmentResponse createShipment(long sellerId, long orderId, CreateShipmentRequest request) {
        // 1. Kiểm tra tồn tại đơn hàng & quyền sở hữu Seller trước tiên
        OrderFulfillmentSnapshot order = commerceFacade.getOrderSnapshot(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng #" + orderId));

        if (order.sellerId() != sellerId) {
            throw new AccessDeniedException("Bạn không có quyền tạo vận đơn cho đơn hàng này.");
        }

        if (!order.canShip()) {
            throw new IllegalStateException("Chỉ có thể tạo vận đơn khi đơn hàng ở trạng thái SELLER_CONFIRMED. Trạng thái hiện tại: " + order.status());
        }

        // 2. Kiểm tra nếu đơn hàng đã có vận đơn GHN hợp lệ từ trước thì trả về (idempotent)
        Optional<ShipmentEntity> existingOpt = shipmentRepository.findByOrderId(orderId);
        if (existingOpt.isPresent() && existingOpt.get().getTrackingNumber() != null && !existingOpt.get().getTrackingNumber().isBlank()) {
            log.info("Đơn hàng #{} đã có vận đơn GHN ({}), trả về kết quả hiện có.", orderId, existingOpt.get().getTrackingNumber());
            return mapToResponse(existingOpt.get());
        }

        // 3. ADDR-03 Legacy Guard: Fail-closed nếu đơn hàng thiếu địa chỉ lấy hàng hoặc thiếu mã GHN
        if (!order.hasValidGhnShippingCodes()
                || isBlank(order.fromName()) || isBlank(order.fromPhone()) || isBlank(order.fromAddress())
                || isBlank(order.fromWardName()) || isBlank(order.fromDistrictName()) || isBlank(order.fromProvinceName())) {
            throw new FulfillmentFeatureLockedException(
                    "Đơn hàng #" + orderId + " chưa có đầy đủ thông tin địa chỉ lấy hàng của người bán (ADDR-01) hoặc địa chỉ giao hàng chuẩn GHN. " +
                    "Vui lòng cập nhật sổ địa chỉ theo chuẩn GHN trước khi tạo vận đơn."
            );
        }

        // 4. Hợp nhất dữ liệu địa chỉ chuẩn từ database với thông số kiện hàng từ request
        int weight = (request != null && request.weight() != null && request.weight() > 0) ? request.weight() : 500;
        int length = (request != null && request.length() != null && request.length() > 0) ? request.length() : 20;
        int width = (request != null && request.width() != null && request.width() > 0) ? request.width() : 15;
        int height = (request != null && request.height() != null && request.height() > 0) ? request.height() : 10;
        String note = (request != null) ? request.note() : null;
        List<CreateShipmentRequest.ItemInfo> items = (request != null) ? request.items() : null;

        CreateShipmentRequest mergedRequest = new CreateShipmentRequest(
                order.fromName(),
                order.fromPhone(),
                order.fromAddress(),
                order.fromWardName(),
                order.fromDistrictName(),
                order.fromProvinceName(),
                order.fromDistrictId(),
                order.toDistrictId(),
                order.toWardCode(),
                weight,
                length,
                width,
                height,
                note,
                items
        );

        return executeShipmentCreationWithLease(sellerId, orderId, mergedRequest);
    }

    /**
     * Thực thi quy trình tạo vận đơn GHN dựa trên cơ chế claim/lease bền vững trong database.
     * Tách transaction ngắn khỏi HTTP outbound call, đảm bảo không có 2 worker nào cùng
     * thực hiện outbound request đồng thời cho cùng một đơn hàng.
     */
    public ShipmentResponse executeShipmentCreationWithLease(long sellerId, long orderId, CreateShipmentRequest request) {
        Instant now = clock.instant();

        // 1. Transaction 1: Khởi tạo/kiểm tra shipment PENDING và giành quyền claim/lease
        InitiationResult initiation = transactionTemplate.execute(status -> initiatePendingShipmentWithClaim(sellerId, orderId, request, now));

        if (initiation instanceof InitiationResult.AlreadyCompleted completed) {
            log.info("Đơn hàng #{} đã có vận đơn GHN hợp lệ ({}), tái sử dụng kết quả.", orderId, completed.response().trackingNumber());
            return completed.response();
        }

        InitiationResult.ReadyToCall ready = (InitiationResult.ReadyToCall) initiation;
        String clientOrderCode = ready.clientOrderCode();
        String claimToken = ready.claimToken();
        OrderFulfillmentSnapshot order = ready.order();

        CreateOrderResponseDto createResponse = null;

        // Nếu là lần retry/recovery (claim trước đó đã hết hạn):
        // Bắt buộc đối soát GHN trước bằng client_order_code, tránh tạo đơn thứ hai khi GHN đã tạo thành công
        if (ready.isReconciliationFirst()) {
            log.info("Thực hiện đối soát ưu tiên bằng clientOrderCode={} trước khi gọi tạo mới", clientOrderCode);
            createResponse = reconcileAfterTimeout(clientOrderCode);
            if (createResponse == null) {
                // GHN chưa trả về kết quả qua đối soát: TUYỆT ĐỐI KHÔNG tự động gọi lại API tạo vận đơn (tránh trùng đơn mù)!
                // Giải phóng claim và cập nhật thời điểm đối soát tiếp theo (backoff 30s) bằng query nguyên tử
                transactionTemplate.execute(status -> {
                    shipmentRepository.releaseClaimWithBackoff(
                            orderId,
                            claimToken,
                            now.plusSeconds(30),
                            0,
                            "Vận đơn đang chờ GHN xác nhận sau timeout đối soát.",
                            now
                    );
                    return null;
                });
                throw new GhnApiException("Vận đơn đang trong tiến trình chờ GHN xác nhận (mã đối soát: " + clientOrderCode + "). Hệ thống đang tiếp tục tự động đối soát, vui lòng không gửi lại yêu cầu tạo đơn mới để tránh trùng lặp.");
            }
        }

        if (createResponse == null) {
            // 2. HTTP Calls ra ngoài GHN (chỉ gọi khi là lần tạo đầu tiên !ready.isReconciliationFirst())
            int selectedServiceTypeId = resolveAvailableServiceType(request.fromDistrictId(), request.toDistrictId(), request.weight());
            CreateShippingOrderRequest ghnRequest = buildGhnCreateRequest(order, clientOrderCode, selectedServiceTypeId, request);

            try {
                createResponse = ghnClient.createShippingOrder(ghnRequest);
            } catch (GhnApiException ex) {
                log.warn("Lỗi khi gọi GHN createShippingOrder cho orderId={}: {}", orderId, ex.getMessage());

                if (isTimeoutOrNetworkError(ex)) {
                    log.info("GHN createShippingOrder bị timeout cho orderId={}, tiến hành đối soát bằng clientOrderCode={}", orderId, clientOrderCode);
                    createResponse = reconcileAfterTimeout(clientOrderCode);
                    if (createResponse == null) {
                        // Cập nhật backoff đối soát, ghi chú incident và giải phóng claim bằng query nguyên tử token-safe
                        transactionTemplate.execute(status -> {
                            shipmentRepository.releaseClaimWithBackoff(
                                    orderId,
                                    claimToken,
                                    now.plusSeconds(15),
                                    0,
                                    "Tạo vận đơn bị timeout, đang chờ GHN xác nhận qua đối soát.",
                                    now
                            );
                            return null;
                        });

                        // Kết quả chưa xác định: giữ nguyên PENDING, không ném FAILED
                        throw new GhnApiException("Kết quả tạo vận đơn chưa xác định do GHN timeout. Vui lòng kiểm tra lại sau giây lát để hệ thống tự động đối soát.", ex);
                    }
                } else {
                    // Lỗi validation: nhả claim để request sau có thể sửa input và gửi lại ngay
                    try {
                        transactionTemplate.execute(status -> {
                            shipmentRepository.releaseClaim(orderId, claimToken, clock.instant());
                            return null;
                        });
                    } catch (Exception e) {
                        log.warn("Không thể release claim cho orderId={} sau lỗi validation: {}", orderId, e.getMessage());
                    }
                    throw ex;
                }
            }
        }

        // 3. Transaction 2: Lưu kết quả phản hồi GHN vào Shipment trong transaction ngắn và giải phóng claim
        CreateOrderResponseDto finalResponse = createResponse;
        return transactionTemplate.execute(status -> finalizeGhnCreated(orderId, finalResponse, clock.instant()));
    }

    /**
     * Tra cứu thông tin tracking hành trình đơn hàng.
     * Dành cho Buyer hoặc Seller sở hữu Order. Không tự ý thay đổi trạng thái nghiệp vụ.
     */
    public TrackingResponse getTracking(long userId, long orderId) {
        OrderFulfillmentSnapshot order = commerceFacade.getOrderSnapshot(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng #" + orderId));

        if (order.buyerId() != userId && order.sellerId() != userId) {
            throw new AccessDeniedException("Bạn không có quyền xem thông tin vận chuyển của đơn hàng này.");
        }

        Optional<ShipmentEntity> shipmentOpt = shipmentRepository.findByOrderId(orderId);
        if (shipmentOpt.isEmpty()) {
            return new TrackingResponse(
                    orderId,
                    null,
                    "GHN",
                    "CHƯA_TẠO_VẬN_ĐƠN",
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }

        ShipmentEntity shipment = shipmentOpt.get();
        if (shipment.getTrackingNumber() == null || shipment.getTrackingNumber().isBlank()) {
            if (shipment.getClientOrderCode() != null && !shipment.getClientOrderCode().isBlank()
                    && !shipment.isAdminReviewRequired()) {
                Instant now = clock.instant();
                boolean cooldownPassed = shipment.getCreatedAt() != null
                        && shipment.getCreatedAt().isBefore(now.minusSeconds(5));
                boolean reconciliationDue = shipment.isReconciliationDue(now);
                boolean claimNotActive = !shipment.hasActiveClaim(now);

                if (cooldownPassed && reconciliationDue && claimNotActive) {
                    String onDemandToken = "ondemand-" + UUID.randomUUID();
                    // Lease 30s đảm bảo lớn hơn tổng connectTimeout (5s) + readTimeout (10s) của GHN
                    int acquired = shipmentRepository.acquireClaim(orderId, onDemandToken, now.plusSeconds(LEASE_DURATION_SECONDS), now);
                    if (acquired == 1) {
                        final int attemptsBeforeRecon = shipment.getReconciliationAttempts();
                        final String notesBeforeRecon = shipment.getIncidentNotes();
                        try {
                            OrderDetailResponseDto detail = ghnClient.getOrderDetailByClientCode(shipment.getClientOrderCode());
                            if (detail != null && detail.orderCode() != null && !detail.orderCode().isBlank()) {
                                log.info("On-demand reconciliation trong getTracking tìm thấy orderCode={} cho clientOrderCode={}",
                                        detail.orderCode(), shipment.getClientOrderCode());
                                String rawStatus = (detail.status() != null && !detail.status().isBlank())
                                        ? detail.status().trim().toLowerCase(Locale.ROOT)
                                        : "ready_to_pick";
                                int updatedRows = transactionTemplate.execute(status -> shipmentRepository.recordReconciledTracking(
                                        orderId,
                                        onDemandToken,
                                        detail.orderCode(),
                                        rawStatus,
                                        now
                                ));
                                if (updatedRows > 0) {
                                    shipment = shipmentRepository.findByOrderId(orderId).orElse(shipment);
                                } else {
                                    log.warn("On-demand worker với onDemandToken={} không còn sở hữu claim cho orderId={} (fenced write prevented overwriting).",
                                            onDemandToken, orderId);
                                    shipment = shipmentRepository.findByOrderId(orderId).orElse(shipment);
                                }
                            } else {
                                // GHN chưa có đơn: đặt backoff ngắn 15s và giải phóng claim một cách nguyên tử
                                transactionTemplate.execute(status -> {
                                    shipmentRepository.releaseClaimWithBackoff(
                                            orderId,
                                            onDemandToken,
                                            now.plusSeconds(15),
                                            attemptsBeforeRecon,
                                            notesBeforeRecon,
                                            now
                                    );
                                    return null;
                                });
                                shipment.setNextReconciliationAt(now.plusSeconds(15));
                            }
                        } catch (Exception ex) {
                            log.warn("On-demand reconciliation trong getTracking không thành công: {}", ex.getMessage());
                            transactionTemplate.execute(status -> {
                                shipmentRepository.releaseClaimWithBackoff(
                                        orderId,
                                        onDemandToken,
                                        now.plusSeconds(15),
                                        attemptsBeforeRecon,
                                        notesBeforeRecon,
                                        now
                                );
                                return null;
                            });
                            shipment.setNextReconciliationAt(now.plusSeconds(15));
                        } finally {
                            transactionTemplate.execute(status -> {
                                shipmentRepository.releaseClaim(orderId, onDemandToken, clock.instant());
                                return null;
                            });
                        }
                    }
                }
            }

            if (shipment.getTrackingNumber() == null || shipment.getTrackingNumber().isBlank()) {
                return new TrackingResponse(
                        orderId,
                        null,
                        shipment.getCarrier(),
                        shipment.getStatus(),
                        resolveDisplayGhnStatus(shipment),
                        shipment.getShippingFee(),
                        shipment.getExpectedDeliveryAt(),
                        null,
                        null
                );
            }
        }

        // Gọi GHN để lấy chi tiết mới nhất (read-only, không làm thay đổi trạng thái entity trong DB)
        OrderDetailResponseDto ghnDetail = null;
        try {
            ghnDetail = ghnClient.getOrderDetail(shipment.getTrackingNumber());
        } catch (Exception ex) {
            log.warn("Không thể lấy chi tiết vận đơn từ GHN cho trackingNumber={}: {}", shipment.getTrackingNumber(), ex.getMessage());
        }

        String rawStatus = ghnDetail != null ? ghnDetail.status() : shipment.getRawGhnStatus();
        String fromAddress = ghnDetail != null ? ghnDetail.fromAddress() : null;
        String toAddress = ghnDetail != null ? ghnDetail.toAddress() : null;

        return new TrackingResponse(
                orderId,
                shipment.getTrackingNumber(),
                shipment.getCarrier(),
                shipment.getStatus(),
                rawStatus,
                shipment.getShippingFee(),
                shipment.getExpectedDeliveryAt(),
                fromAddress,
                toAddress
        );
    }

    /**
     * Sinh link in phiếu giao hàng chuẩn A5 cho Người bán.
     * Gọi API gen-token động mỗi lần in, không lưu print token trong DB.
     */
    public PrintWaybillResponse printWaybill(long userId, long orderId) {
        OrderFulfillmentSnapshot order = commerceFacade.getOrderSnapshot(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng #" + orderId));

        if (order.sellerId() != userId) {
            throw new AccessDeniedException("Bạn không có quyền in vận đơn cho đơn hàng này.");
        }

        ShipmentEntity shipment = shipmentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalStateException("Đơn hàng chưa có thông tin vận chuyển."));

        if (shipment.getTrackingNumber() == null || shipment.getTrackingNumber().isBlank()) {
            throw new IllegalStateException("Đơn hàng chưa có mã vận đơn GHN hợp lệ để in phiếu.");
        }

        PrintTokenResponseDto tokenResp = ghnClient.generatePrintToken(List.of(shipment.getTrackingNumber()));
        String printUrl = buildPrintUrl(tokenResp.token());

        return new PrintWaybillResponse(orderId, shipment.getTrackingNumber(), printUrl);
    }

    // ========================================================================
    // Master Data APIs
    // ========================================================================

    public List<GhnMasterDataDtos.ProvinceDto> getProvinces() {
        return ghnClient.getProvinces();
    }

    public List<GhnMasterDataDtos.DistrictDto> getDistricts(int provinceId) {
        return ghnClient.getDistricts(provinceId);
    }

    public List<GhnMasterDataDtos.WardDto> getWards(int districtId) {
        return ghnClient.getWards(districtId);
    }

    // ========================================================================
    // Internal Helper Methods
    // ========================================================================

    private InitiationResult initiatePendingShipmentWithClaim(long sellerId, long orderId, CreateShipmentRequest request, Instant now) {
        // 1. Khóa Order theo thứ tự bất biến
        OrderFulfillmentSnapshot order = commerceFacade.lockOrderForFulfillment(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng #" + orderId));

        // 2. Xác minh quyền sở hữu của Seller
        if (order.sellerId() != sellerId) {
            throw new AccessDeniedException("Bạn không có quyền tạo vận đơn cho đơn hàng này.");
        }

        // 3. Xác minh trạng thái Order phải là SELLER_CONFIRMED
        if (!order.canShip()) {
            throw new IllegalStateException("Chỉ có thể tạo vận đơn khi đơn hàng ở trạng thái SELLER_CONFIRMED. Trạng thái hiện tại: " + order.status());
        }

        // 4. Kiểm tra tính hợp lệ của địa chỉ và thông tin kiện hàng
        validateRequiredShippingData(request);

        String claimToken = java.util.UUID.randomUUID().toString();
        Instant claimExpiry = now.plusSeconds(LEASE_DURATION_SECONDS);

        // 5. Kiểm tra shipment đã tồn tại hay chưa
        Optional<ShipmentEntity> existingOpt = shipmentRepository.findByOrderId(orderId);
        if (existingOpt.isPresent()) {
            ShipmentEntity existing = existingOpt.get();
            // Đã có mã vận đơn từ GHN
            if (existing.getTrackingNumber() != null && !existing.getTrackingNumber().isBlank()) {
                return new InitiationResult.AlreadyCompleted(mapToResponse(existing));
            }

            // Kiểm tra xem claim hiện tại còn hiệu lực hay không (cooldown / active lease)
            if (existing.hasActiveClaim(now)) {
                throw new IllegalStateException("Yêu cầu tạo vận đơn cho đơn hàng #" + orderId + " đang được xử lý, vui lòng chờ trong giây lát.");
            }

            // Claim đã hết hạn: cố gắng giành claim một cách nguyên tử
            int updated = shipmentRepository.acquireClaim(orderId, claimToken, claimExpiry, now);
            if (updated == 0) {
                throw new IllegalStateException("Yêu cầu tạo vận đơn cho đơn hàng #" + orderId + " đang được xử lý bởi một tiến trình khác, vui lòng chờ trong giây lát.");
            }

            // Tái sử dụng client_order_code đã lưu, đánh dấu reconciliationFirst = true
            return new InitiationResult.ReadyToCall(order, existing.getClientOrderCode(), claimToken, true);
        }

        // 6. Chưa có shipment: Sinh client_order_code DUY NHẤT một lần và lưu Shipment PENDING kèm claim
        String clientOrderCode = clientOrderCodeGenerator.generate(orderId);
        ShipmentEntity shipment = new ShipmentEntity(orderId, clientOrderCode, claimToken, claimExpiry, now);
        shipment.setRawGhnStatus("pending_carrier_confirmation");
        shipmentRepository.save(shipment);

        return new InitiationResult.ReadyToCall(order, clientOrderCode, claimToken, false);
    }

    private void validateRequiredShippingData(CreateShipmentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Thiếu dữ liệu yêu cầu tạo vận đơn.");
        }

        // Kiểm tra địa chỉ lấy hàng của Seller (chờ ADDR-01)
        if (isBlank(request.fromName()) || isBlank(request.fromPhone()) || isBlank(request.fromAddress())
                || request.fromDistrictId() == null || isBlank(request.fromWardName())
                || isBlank(request.fromDistrictName()) || isBlank(request.fromProvinceName())) {
            throw new IllegalArgumentException("Thiếu thông tin địa chỉ lấy hàng của người bán (đang chờ tính năng ADDR-01).");
        }

        // Kiểm tra mã quận/phường GHN của người nhận
        if (request.toDistrictId() == null || isBlank(request.toWardCode())) {
            throw new IllegalArgumentException("Thiếu thông tin mã quận/huyện hoặc phường/xã GHN của người nhận (to_district_id, to_ward_code).");
        }

        // Kiểm tra thông tin kiện hàng
        if (request.weight() == null || request.weight() <= 0
                || request.length() == null || request.length() <= 0
                || request.width() == null || request.width() <= 0
                || request.height() == null || request.height() <= 0) {
            throw new IllegalArgumentException("Thiếu thông tin kiện hàng bắt buộc (weight, length, width, height) để tạo vận đơn GHN.");
        }
    }

    private int resolveAvailableServiceType(int fromDistrictId, int toDistrictId, int weight) {
        List<AvailableServiceDto> services;
        try {
            services = ghnClient.getAvailableServices(fromDistrictId, toDistrictId);
        } catch (Exception ex) {
            throw new GhnApiException("Lỗi khi kiểm tra dịch vụ khả dụng từ GHN cho tuyến đường: " + ex.getMessage(), ex);
        }

        if (services == null || services.isEmpty()) {
            throw new IllegalArgumentException("Không tìm thấy dịch vụ giao hàng GHN khả dụng cho tuyến đường từ quận "
                    + fromDistrictId + " đến quận " + toDistrictId);
        }

        int targetServiceTypeId = (weight <= MAX_LIGHT_WEIGHT_GRAMS) ? SERVICE_TYPE_LIGHT : SERVICE_TYPE_HEAVY;

        boolean matched = services.stream()
                .anyMatch(s -> s.serviceTypeId() != null && s.serviceTypeId() == targetServiceTypeId);

        if (!matched) {
            throw new IllegalArgumentException("Không có gói dịch vụ GHN phù hợp (service_type_id=" + targetServiceTypeId
                    + ") cho tuyến đường và trọng lượng gói hàng này. Danh sách dịch vụ trả về: " + services);
        }

        return targetServiceTypeId;
    }

    private CreateShippingOrderRequest buildGhnCreateRequest(
            OrderFulfillmentSnapshot order,
            String clientOrderCode,
            int serviceTypeId,
            CreateShipmentRequest request
    ) {
        String recipientName = isNotBlank(request.note()) ? request.note() : order.recipientName();
        String toName = order.recipientName() != null ? order.recipientName() : "Người nhận";
        String toPhone = order.phoneNumber() != null ? order.phoneNumber() : "0900000000";
        String toAddress = order.detailAddress() != null ? order.detailAddress() : "Địa chỉ nhận";

        List<OrderItemDto> items = new ArrayList<>();
        if (request.items() != null && !request.items().isEmpty()) {
            for (CreateShipmentRequest.ItemInfo item : request.items()) {
                items.add(new OrderItemDto(
                        item.name(),
                        item.code(),
                        item.quantity() > 0 ? item.quantity() : 1,
                        item.price(),
                        item.length(),
                        item.width(),
                        item.height(),
                        item.weight()
                ));
            }
        } else {
            items.add(new OrderItemDto(
                    "Đơn hàng #" + order.orderId(),
                    clientOrderCode,
                    1,
                    0,
                    request.length(),
                    request.width(),
                    request.height(),
                    request.weight()
            ));
        }

        return new CreateShippingOrderRequest(
                CreateShippingOrderRequest.DEFAULT_PAYMENT_TYPE_SHOP_PAYS, // 1: Sàn/Shop trả cước
                request.note(),
                CreateShippingOrderRequest.REQUIRED_NOTE_CHO_THU_HANG,      // Bắt buộc: "CHOTHUHANG"
                clientOrderCode,
                request.fromName(),
                request.fromPhone(),
                request.fromAddress(),
                request.fromWardName(),
                request.fromDistrictName(),
                request.fromProvinceName(),
                request.fromPhone(),                                       // return_phone
                request.fromAddress(),                                     // return_address
                toName,
                toPhone,
                toAddress,
                request.toWardCode(),
                request.toDistrictId(),
                request.weight(),
                request.length(),
                request.width(),
                request.height(),
                serviceTypeId,
                null,
                CreateShippingOrderRequest.DEFAULT_COD_AMOUNT_ZERO,         // 0: đã thanh toán online qua VNPAY
                items
        );
    }

    private CreateOrderResponseDto reconcileAfterTimeout(String clientOrderCode) {
        try {
            OrderDetailResponseDto detail = ghnClient.getOrderDetailByClientCode(clientOrderCode);
            if (detail != null && detail.orderCode() != null && !detail.orderCode().isBlank()) {
                log.info("Đối soát thành công sau timeout cho clientOrderCode={}: tìm thấy orderCode={}", clientOrderCode, detail.orderCode());
                return new CreateOrderResponseDto(
                        detail.orderCode(),
                        null,
                        null,
                        null,
                        null
                );
            }
        } catch (Exception ex) {
            log.warn("Không tìm thấy vận đơn khi đối soát bằng clientOrderCode={}: {}", clientOrderCode, ex.getMessage());
        }
        return null;
    }

    private ShipmentResponse finalizeGhnCreated(long orderId, CreateOrderResponseDto ghnResponse, Instant now) {
        ShipmentEntity shipment = shipmentRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy vận đơn bị khóa cho đơn hàng #" + orderId));

        String orderCode = ghnResponse.orderCode();
        BigDecimal totalFee = ghnResponse.totalFee() != null ? BigDecimal.valueOf(ghnResponse.totalFee()) : null;
        Instant expectedDelivery = parseExpectedDelivery(ghnResponse.expectedDeliveryTime());

        // Kiểm tra xem đã có tracking_number chưa (idempotent / race condition)
        if (shipment.getTrackingNumber() != null && !shipment.getTrackingNumber().isBlank()) {
            if (!shipment.getTrackingNumber().equals(orderCode)) {
                log.error("Cảnh báo nghiêm trọng: Tracking number không khớp cho orderId={} (DB={}, GHN={})",
                        orderId, shipment.getTrackingNumber(), orderCode);
                throw new IllegalStateException("Mã tracking number không nhất quán với dữ liệu hiện có trong hệ thống.");
            }
            log.info("Vận đơn orderId={} đã có trackingNumber={} từ trước, bỏ qua ghi đè.", orderId, orderCode);
            return mapToResponse(shipment);
        }

        // Cập nhật thông tin vận đơn GHN thành công
        shipment.recordGhnCreated(orderCode, totalFee, expectedDelivery, now);
        shipmentRepository.save(shipment);

        log.info("Tạo vận đơn GHN thành công cho orderId={}, trackingNumber={}, fee={}", orderId, orderCode, totalFee);
        return mapToResponse(shipment);
    }

    private String buildPrintUrl(String token) {
        String baseUrl = ghnProperties.baseUrl();
        String origin;
        try {
            java.net.URI uri = java.net.URI.create(baseUrl);
            origin = uri.getScheme() + "://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
        } catch (Exception e) {
            origin = "https://dev-online-gateway.ghn.vn";
        }
        return origin + "/a5/public-api/printA5?token=" + token;
    }

    private Instant parseExpectedDelivery(String timeStr) {
        if (timeStr == null || timeStr.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(timeStr);
        } catch (Exception e) {
            try {
                return OffsetDateTime.parse(timeStr).toInstant();
            } catch (Exception e2) {
                log.warn("Không thể parse expected_delivery_time từ GHN: '{}'", timeStr);
                return null;
            }
        }
    }

    private boolean isTimeoutOrNetworkError(GhnApiException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof org.springframework.web.client.ResourceAccessException) {
            return true;
        }
        String msg = ex.getMessage();
        if (msg != null) {
            String lower = msg.toLowerCase();
            return lower.contains("timeout") || lower.contains("timed out") || lower.contains("connection refused");
        }
        return false;
    }

    public static String resolveDisplayGhnStatus(ShipmentEntity s) {
        if (s == null) {
            return null;
        }
        if (s.getRawGhnStatus() != null && !s.getRawGhnStatus().isBlank()) {
            return s.getRawGhnStatus();
        }
        if ((s.getTrackingNumber() == null || s.getTrackingNumber().isBlank())
                && s.getClientOrderCode() != null && !s.getClientOrderCode().isBlank()) {
            return "pending_carrier_confirmation";
        }
        return null;
    }

    private ShipmentResponse mapToResponse(ShipmentEntity s) {
        return new ShipmentResponse(
                s.getOrderId(),
                s.getTrackingNumber(),
                s.getClientOrderCode(),
                s.getCarrier(),
                s.getStatus(),
                s.getShippingFee(),
                s.getExpectedDeliveryAt(),
                resolveDisplayGhnStatus(s),
                s.getCreatedAt()
        );
    }

    private boolean isBlank(String str) {
        return str == null || str.isBlank();
    }

    private boolean isNotBlank(String str) {
        return str != null && !str.isBlank();
    }

    private sealed interface InitiationResult {
        record AlreadyCompleted(ShipmentResponse response) implements InitiationResult {}
        record ReadyToCall(OrderFulfillmentSnapshot order, String clientOrderCode, String claimToken, boolean isReconciliationFirst) implements InitiationResult {}
    }
}
