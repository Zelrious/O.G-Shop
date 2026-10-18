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
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnShippingDtos.AvailableServiceDto;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnShippingDtos.CreateOrderResponseDto;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnShippingDtos.CreateShippingOrderRequest;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnShippingDtos.OrderDetailResponseDto;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnShippingDtos.PrintTokenResponseDto;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.persistence.ShipmentEntity;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.persistence.ShipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class FulfillmentServiceUnitTest {

    private FulfillmentCommerceFacade commerceFacade;
    private ShipmentRepository shipmentRepository;
    private GhnClient ghnClient;
    private GhnProperties ghnProperties;
    private ClientOrderCodeGenerator clientOrderCodeGenerator;
    private TransactionTemplate transactionTemplate;
    private Clock clock;
    private FulfillmentService fulfillmentService;

    private static final Instant NOW = Instant.parse("2026-10-18T10:00:00Z");
    private static final long SELLER_ID = 100L;
    private static final long OTHER_SELLER_ID = 999L;
    private static final long BUYER_ID = 200L;
    private static final long ORDER_ID = 500L;

    @BeforeEach
    void setUp() {
        commerceFacade = mock(FulfillmentCommerceFacade.class);
        shipmentRepository = mock(ShipmentRepository.class);
        ghnClient = mock(GhnClient.class);
        ghnProperties = new GhnProperties(
                "https://dev-online-gateway.ghn.vn/shiip/public-api/",
                "secret-ghn-token-never-expose",
                229762,
                Duration.ofSeconds(5),
                Duration.ofSeconds(10)
        );
        clientOrderCodeGenerator = new ClientOrderCodeGenerator("DEV");
        transactionTemplate = mock(TransactionTemplate.class);
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> callback = inv.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });

        clock = Clock.fixed(NOW, ZoneOffset.UTC);

        when(shipmentRepository.acquireClaim(any(), any(), any(), any())).thenReturn(1);

        fulfillmentService = new FulfillmentService(
                commerceFacade,
                shipmentRepository,
                ghnClient,
                ghnProperties,
                clientOrderCodeGenerator,
                transactionTemplate,
                clock
        );
    }

    private OrderFulfillmentSnapshot createConfirmedOrderSnapshot() {
        return new OrderFulfillmentSnapshot(
                ORDER_ID,
                BUYER_ID,
                SELLER_ID,
                "SELLER_CONFIRMED",
                null,
                null,
                "Nguyen Van A",
                "0987654321",
                "Ho Chi Minh",
                "Quan 1",
                "Ben Nghe",
                "123 Le Duan"
        );
    }

    private CreateShipmentRequest createValidShipmentRequest() {
        return new CreateShipmentRequest(
                "Shop Vintage",
                "0912345678",
                "456 Tran Hung Dao",
                "Phuong Pham Ngu Lao",
                "Quan 1",
                "TP. Ho Chi Minh",
                1442,
                1444,
                "20311",
                1000,
                20,
                15,
                10,
                "Giao gio hanh chinh",
                null
        );
    }

    // ========================================================================
    // STAGE A1: Fail-closed createShipment tests
    // ========================================================================

    @Test
    @DisplayName("A1.1 Order không ở SELLER_CONFIRMED bị từ chối với IllegalStateException")
    void testOrderNotSellerConfirmedRejected() {
        OrderFulfillmentSnapshot snapshot = new OrderFulfillmentSnapshot(
                ORDER_ID, BUYER_ID, SELLER_ID, "PAYMENT_PENDING", null, null,
                "Recipient", "0912345678", "HN", "BD", "Ward", "Detail"
        );
        when(commerceFacade.getOrderSnapshot(ORDER_ID)).thenReturn(Optional.of(snapshot));

        assertThatThrownBy(() -> fulfillmentService.createShipment(SELLER_ID, ORDER_ID, createValidShipmentRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SELLER_CONFIRMED");

        verify(ghnClient, never()).createShippingOrder(any());
        verify(shipmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("A1.2 Seller không sở hữu Order bị từ chối với AccessDeniedException")
    void testSellerNotOwnerRejected() {
        OrderFulfillmentSnapshot snapshot = createConfirmedOrderSnapshot();
        when(commerceFacade.getOrderSnapshot(ORDER_ID)).thenReturn(Optional.of(snapshot));

        assertThatThrownBy(() -> fulfillmentService.createShipment(OTHER_SELLER_ID, ORDER_ID, createValidShipmentRequest()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("không có quyền");

        verify(ghnClient, never()).createShippingOrder(any());
        verify(shipmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("A1.3 Shipment đã có vận đơn không bị tạo lại (idempotency)")
    void testShipmentAlreadyHasTrackingNumberNotRecreated() {
        OrderFulfillmentSnapshot snapshot = createConfirmedOrderSnapshot();
        when(commerceFacade.getOrderSnapshot(ORDER_ID)).thenReturn(Optional.of(snapshot));

        ShipmentEntity existingShipment = new ShipmentEntity(ORDER_ID, "OG-DEV-500-ABCDEF", NOW.minusSeconds(60));
        existingShipment.recordGhnCreated("GHN-ALREADY-CREATED-123", BigDecimal.valueOf(20900), NOW.plus(Duration.ofDays(2)), NOW);

        when(shipmentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(existingShipment));

        ShipmentResponse response = fulfillmentService.createShipment(SELLER_ID, ORDER_ID, createValidShipmentRequest());

        assertThat(response).isNotNull();
        assertThat(response.trackingNumber()).isEqualTo("GHN-ALREADY-CREATED-123");
        verify(ghnClient, never()).createShippingOrder(any());
        verify(shipmentRepository, never()).save(any());
    }

    private OrderFulfillmentSnapshot createValidGhnOrderSnapshot() {
        return new OrderFulfillmentSnapshot(
                ORDER_ID,
                BUYER_ID,
                SELLER_ID,
                "SELLER_CONFIRMED",
                null,
                null,
                100L,
                "Shop Vintage",
                "0912345678",
                "456 Tran Hung Dao",
                "TP. Ho Chi Minh",
                "Quan 1",
                "Phuong Pham Ngu Lao",
                1442,
                "20101",
                "Nguyen Van A",
                "0987654321",
                "Ho Chi Minh",
                "Quan 1",
                "Ben Nghe",
                "123 Le Duan",
                1444,
                "20311"
        );
    }

    @Test
    @DisplayName("A1.4 Đơn hàng thiếu thông tin chuẩn GHN bị chặn bằng FulfillmentFeatureLockedException (Legacy Guard Fail Closed)")
    void testClientDeclaredDataBlockedFailClosed() {
        OrderFulfillmentSnapshot snapshot = createConfirmedOrderSnapshot();
        when(commerceFacade.getOrderSnapshot(ORDER_ID)).thenReturn(Optional.of(snapshot));
        when(shipmentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fulfillmentService.createShipment(SELLER_ID, ORDER_ID, createValidShipmentRequest()))
                .isInstanceOf(FulfillmentFeatureLockedException.class)
                .hasMessageContaining("chưa có đầy đủ thông tin địa chỉ");

        // Xác nhận không có HTTP outbound call nào tới GHN
        verify(ghnClient, never()).createShippingOrder(any());
        verify(ghnClient, never()).getAvailableServices(anyInt(), anyInt());
        // Xác nhận không tạo bản ghi shipment PENDING mới
        verify(shipmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("A1.5 Đơn hàng có đầy đủ thông tin chuẩn GHN từ DB tạo vận đơn thành công qua executeShipmentCreationWithLease")
    void testValidGhnOrderSucceedsAndCallsExecuteShipmentCreation() {
        OrderFulfillmentSnapshot snapshot = createValidGhnOrderSnapshot();
        when(commerceFacade.getOrderSnapshot(ORDER_ID)).thenReturn(Optional.of(snapshot));
        when(commerceFacade.lockOrderForFulfillment(ORDER_ID)).thenReturn(Optional.of(snapshot));
        when(shipmentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());

        ShipmentEntity createdShipment = new ShipmentEntity(ORDER_ID, "OG-DEV-500-ABCDEF", NOW);
        when(shipmentRepository.save(any(ShipmentEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(shipmentRepository.findByOrderIdForUpdate(ORDER_ID)).thenReturn(Optional.of(createdShipment));

        when(ghnClient.getAvailableServices(1442, 1444)).thenReturn(List.of(
                new AvailableServiceDto(2, "Chuan", 2)
        ));
        when(ghnClient.createShippingOrder(any())).thenReturn(new CreateOrderResponseDto(
                "GHN-SUCCESS-999", null, null, null, null
        ));

        ShipmentResponse response = fulfillmentService.createShipment(SELLER_ID, ORDER_ID, createValidShipmentRequest());

        assertThat(response).isNotNull();
        assertThat(response.trackingNumber()).isEqualTo("GHN-SUCCESS-999");
        verify(ghnClient).createShippingOrder(any());
    }

    // ========================================================================
    // STAGE A2: Claim/Lease & Concurrency tests (executeShipmentCreationWithLease)
    // ========================================================================

    @Test
    @DisplayName("A2.1 Request B đến khi A còn giữ active lease (vd: tại 2s hoặc 11s) bị chặn, không gọi GHN")
    void testConcurrentRequestWithinActiveLeaseRejected() {
        OrderFulfillmentSnapshot snapshot = createConfirmedOrderSnapshot();
        when(commerceFacade.lockOrderForFulfillment(ORDER_ID)).thenReturn(Optional.of(snapshot));

        // Shipment PENDING đang giữ lease còn hiệu lực tới NOW + 20s
        ShipmentEntity existingPending = new ShipmentEntity(ORDER_ID, "OG-DEV-500-ABCDEF", "claim-token-1", NOW.plusSeconds(20), NOW.minusSeconds(10));
        when(shipmentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(existingPending));

        assertThatThrownBy(() -> fulfillmentService.executeShipmentCreationWithLease(SELLER_ID, ORDER_ID, createValidShipmentRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("đang được xử lý");

        verify(ghnClient, never()).createShippingOrder(any());
    }

    @Test
    @DisplayName("A2.2 Hai worker cùng tranh claim: worker thứ 2 không update được dòng nào -> bị từ chối")
    void testAtomicClaimConflictRejectsSecondWorker() {
        OrderFulfillmentSnapshot snapshot = createConfirmedOrderSnapshot();
        when(commerceFacade.lockOrderForFulfillment(ORDER_ID)).thenReturn(Optional.of(snapshot));

        // Shipment PENDING có lease đã hết hạn
        ShipmentEntity existingPending = new ShipmentEntity(ORDER_ID, "OG-DEV-500-ABCDEF", "old-claim", NOW.minusSeconds(1), NOW.minusSeconds(40));
        when(shipmentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(existingPending));

        // Giả lập worker khác vừa giành claim trước (acquireClaim trả về 0 rows updated)
        when(shipmentRepository.acquireClaim(eq(ORDER_ID), any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> fulfillmentService.executeShipmentCreationWithLease(SELLER_ID, ORDER_ID, createValidShipmentRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("đang được xử lý");

        verify(ghnClient, never()).createShippingOrder(any());
    }

    @Test
    @DisplayName("A2.3 Retry sau khi lease hết hạn: dùng lại cùng client_order_code và đối soát trước")
    void testRetryReusesExistingClientOrderCodeAndReconcilesFirst() {
        OrderFulfillmentSnapshot snapshot = createConfirmedOrderSnapshot();
        when(commerceFacade.lockOrderForFulfillment(ORDER_ID)).thenReturn(Optional.of(snapshot));

        String storedClientCode = "OG-DEV-500-RETRY123";
        ShipmentEntity existingPending = new ShipmentEntity(ORDER_ID, storedClientCode, "old-claim", NOW.minusSeconds(1), NOW.minusSeconds(40));
        when(shipmentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(existingPending));
        when(shipmentRepository.findByOrderIdForUpdate(ORDER_ID)).thenReturn(Optional.of(existingPending));

        // Giành được claim mới
        when(shipmentRepository.acquireClaim(eq(ORDER_ID), any(), any(), any())).thenReturn(1);

        // Đối soát trước: phát hiện GHN ĐÃ tạo đơn từ trước!
        OrderDetailResponseDto reconciledDetail = new OrderDetailResponseDto(
                "GHN-ALREADY-CREATED-RECON", "ready_to_pick", "CHOTHUHANG",
                "Shop", "0912345678", "Address", "20101", 1442,
                "Buyer", "0987654321", "To Address", "20311", 1444
        );
        when(ghnClient.getOrderDetailByClientCode(storedClientCode)).thenReturn(reconciledDetail);

        ShipmentResponse response = fulfillmentService.executeShipmentCreationWithLease(SELLER_ID, ORDER_ID, createValidShipmentRequest());

        assertThat(response.trackingNumber()).isEqualTo("GHN-ALREADY-CREATED-RECON");
        assertThat(response.clientOrderCode()).isEqualTo(storedClientCode);

        // Vì đối soát đã tìm thấy đơn, tuyệt đối KHÔNG gọi createShippingOrder lần hai!
        verify(ghnClient, never()).createShippingOrder(any());
    }

    @Test
    @DisplayName("A2.4 GHN trả lỗi validation: ném GhnApiException, nhả claim, không gán Shipment thành FAILED")
    void testGhnValidationErrorReleasesClaimWithoutFailingShipment() {
        OrderFulfillmentSnapshot snapshot = createConfirmedOrderSnapshot();
        when(commerceFacade.lockOrderForFulfillment(ORDER_ID)).thenReturn(Optional.of(snapshot));
        when(shipmentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());

        when(ghnClient.getAvailableServices(1442, 1444)).thenReturn(List.of(
                new AvailableServiceDto(53320, "Chuẩn", 2)
        ));

        when(ghnClient.createShippingOrder(any()))
                .thenThrow(new GhnApiException("GHN API error (400): SHOP_INFO_ERROR"));

        assertThatThrownBy(() -> fulfillmentService.executeShipmentCreationWithLease(SELLER_ID, ORDER_ID, createValidShipmentRequest()))
                .isInstanceOf(GhnApiException.class)
                .hasMessageContaining("SHOP_INFO_ERROR");

        // Xác nhận có nhả claim để request sau có thể gửi lại
        verify(shipmentRepository).releaseClaim(eq(ORDER_ID), any(), any());
        // Xác nhận không bao giờ gọi finalize/markFailed trên Shipment
        verify(shipmentRepository, never()).findByOrderIdForUpdate(ORDER_ID);
    }

    @Test
    @DisplayName("A2.5 GHN timeout và kết quả chưa xác định: ném exception, giữ PENDING, không sinh mã mới")
    void testGhnTimeoutThrowsUnconfirmedExceptionWithoutFailingShipment() {
        OrderFulfillmentSnapshot snapshot = createConfirmedOrderSnapshot();
        when(commerceFacade.lockOrderForFulfillment(ORDER_ID)).thenReturn(Optional.of(snapshot));
        when(shipmentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());

        when(ghnClient.getAvailableServices(1442, 1444)).thenReturn(List.of(
                new AvailableServiceDto(53320, "Chuẩn", 2)
        ));

        // Mô phỏng read timeout
        when(ghnClient.createShippingOrder(any()))
                .thenThrow(new GhnApiException("Lỗi kết nối/timeout khi gọi GHN API", new ResourceAccessException("Read timed out")));

        // Tra cứu sau timeout cũng không tìm thấy
        when(ghnClient.getOrderDetailByClientCode(any())).thenReturn(null);

        assertThatThrownBy(() -> fulfillmentService.executeShipmentCreationWithLease(SELLER_ID, ORDER_ID, createValidShipmentRequest()))
                .isInstanceOf(GhnApiException.class)
                .hasMessageContaining("chưa xác định do GHN timeout");

        verify(shipmentRepository, never()).findByOrderIdForUpdate(ORDER_ID);
    }

    @Test
    @DisplayName("A2.6 Tra cứu theo client_order_code tìm thấy vận đơn ngay sau timeout")
    void testReconciliationFindsOrderAfterTimeout() {
        OrderFulfillmentSnapshot snapshot = createConfirmedOrderSnapshot();
        when(commerceFacade.lockOrderForFulfillment(ORDER_ID)).thenReturn(Optional.of(snapshot));

        ShipmentEntity pendingShipment = new ShipmentEntity(ORDER_ID, "OG-DEV-500-RECON", "claim-1", NOW.plusSeconds(30), NOW);
        when(shipmentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());
        when(shipmentRepository.findByOrderIdForUpdate(ORDER_ID)).thenReturn(Optional.of(pendingShipment));

        when(ghnClient.getAvailableServices(1442, 1444)).thenReturn(List.of(
                new AvailableServiceDto(53320, "Chuẩn", 2)
        ));

        // Giả lập GHN create bị timeout
        when(ghnClient.createShippingOrder(any()))
                .thenThrow(new GhnApiException("Read timed out", new ResourceAccessException("Timeout")));

        // Đối soát theo client_order_code tìm thấy đơn hàng đã được tạo trên GHN!
        OrderDetailResponseDto reconciledDetail = new OrderDetailResponseDto(
                "GHN-RECONCILED-999", "ready_to_pick", "CHOTHUHANG",
                "Shop", "0912345678", "Address", "20101", 1442,
                "Buyer", "0987654321", "To Address", "20311", 1444
        );
        when(ghnClient.getOrderDetailByClientCode(any())).thenReturn(reconciledDetail);

        ShipmentResponse response = fulfillmentService.executeShipmentCreationWithLease(SELLER_ID, ORDER_ID, createValidShipmentRequest());

        assertThat(response).isNotNull();
        assertThat(response.trackingNumber()).isEqualTo("GHN-RECONCILED-999");
        assertThat(pendingShipment.getTrackingNumber()).isEqualTo("GHN-RECONCILED-999");
        assertThat(pendingShipment.getClaimToken()).isNull(); // claim cleared on finalize
    }

    @Test
    @DisplayName("A2.7 available-services không trả dịch vụ phù hợp bị từ chối rõ ràng")
    void testNoSuitableServiceThrowsClearBusinessError() {
        OrderFulfillmentSnapshot snapshot = createConfirmedOrderSnapshot();
        when(commerceFacade.lockOrderForFulfillment(ORDER_ID)).thenReturn(Optional.of(snapshot));
        when(shipmentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());

        when(ghnClient.getAvailableServices(1442, 1444)).thenReturn(List.of(
                new AvailableServiceDto(10001, "Hỏa tốc", 1)
        ));

        assertThatThrownBy(() -> fulfillmentService.executeShipmentCreationWithLease(SELLER_ID, ORDER_ID, createValidShipmentRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Không có gói dịch vụ GHN phù hợp");

        verify(ghnClient, never()).createShippingOrder(any());
    }

    @Test
    @DisplayName("A2.8 Thiếu địa chỉ Seller hoặc thông tin kiện hàng bắt buộc bị từ chối")
    void testMissingSellerAddressOrParcelDataRejected() {
        OrderFulfillmentSnapshot snapshot = createConfirmedOrderSnapshot();
        when(commerceFacade.lockOrderForFulfillment(ORDER_ID)).thenReturn(Optional.of(snapshot));
        when(shipmentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());

        // Case A: Thiếu địa chỉ Seller (chờ ADDR-01)
        CreateShipmentRequest missingSellerAddress = new CreateShipmentRequest(
                null, null, null, null, null, null, null,
                1444, "20311", 1000, 20, 15, 10, null, null
        );
        assertThatThrownBy(() -> fulfillmentService.executeShipmentCreationWithLease(SELLER_ID, ORDER_ID, missingSellerAddress))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ADDR-01");

        // Case B: Thiếu thông tin kiện hàng (weight = 0 hoặc null)
        CreateShipmentRequest missingParcelInfo = new CreateShipmentRequest(
                "Shop", "0912345678", "Addr", "Ward", "District", "Province", 1442,
                1444, "20311", null, 20, 15, 10, null, null
        );
        assertThatThrownBy(() -> fulfillmentService.executeShipmentCreationWithLease(SELLER_ID, ORDER_ID, missingParcelInfo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("weight, length, width, height");

        verify(ghnClient, never()).createShippingOrder(any());
    }

    // ========================================================================
    // Tracking & Print Tests
    // ========================================================================

    @Test
    @DisplayName("Tracking endpoint không làm đổi trạng thái Order/Shipment")
    void testTrackingEndpointDoesNotMutateState() {
        OrderFulfillmentSnapshot snapshot = createConfirmedOrderSnapshot();
        when(commerceFacade.getOrderSnapshot(ORDER_ID)).thenReturn(Optional.of(snapshot));

        ShipmentEntity shipment = new ShipmentEntity(ORDER_ID, "OG-DEV-500-TRACK", NOW.minusSeconds(100));
        shipment.recordGhnCreated("GHN-TRACK-1234", BigDecimal.valueOf(20900), NOW.plus(Duration.ofDays(2)), NOW);
        when(shipmentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(shipment));

        OrderDetailResponseDto ghnDetail = new OrderDetailResponseDto(
                "GHN-TRACK-1234", "delivering", "CHOTHUHANG",
                "Shop", "0912345678", "From", "20101", 1442,
                "Buyer", "0987654321", "To", "20311", 1444
        );
        when(ghnClient.getOrderDetail("GHN-TRACK-1234")).thenReturn(ghnDetail);

        TrackingResponse response = fulfillmentService.getTracking(BUYER_ID, ORDER_ID);

        assertThat(response).isNotNull();
        assertThat(response.trackingNumber()).isEqualTo("GHN-TRACK-1234");
        assertThat(response.rawGhnStatus()).isEqualTo("delivering");
        assertThat(response.status()).isEqualTo("PENDING"); // DB entity status không bị đổi

        // Xác nhận không có thao tác save/update nào được gọi
        verify(shipmentRepository, never()).save(any());
        verify(commerceFacade, never()).markShipped(anyLong(), any());
        verify(commerceFacade, never()).markDeliveredDirectFromCarrier(anyLong(), any());
    }

    @Test
    @DisplayName("Print endpoint không tạo token khi chưa có vận đơn hợp lệ")
    void testPrintEndpointFailsWithoutValidTrackingNumber() {
        OrderFulfillmentSnapshot snapshot = createConfirmedOrderSnapshot();
        when(commerceFacade.getOrderSnapshot(ORDER_ID)).thenReturn(Optional.of(snapshot));

        ShipmentEntity shipmentWithoutTracking = new ShipmentEntity(ORDER_ID, "OG-DEV-500-PENDING", NOW);
        when(shipmentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(shipmentWithoutTracking));

        assertThatThrownBy(() -> fulfillmentService.printWaybill(SELLER_ID, ORDER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("chưa có mã vận đơn GHN hợp lệ");

        verify(ghnClient, never()).generatePrintToken(any());
    }

    @Test
    @DisplayName("Không có secret/token nhạy cảm trong print URL response")
    void testNoSensitiveSecretsInResponse() {
        OrderFulfillmentSnapshot snapshot = createConfirmedOrderSnapshot();
        when(commerceFacade.getOrderSnapshot(ORDER_ID)).thenReturn(Optional.of(snapshot));

        ShipmentEntity shipment = new ShipmentEntity(ORDER_ID, "OG-DEV-500-PRINT", NOW.minusSeconds(50));
        shipment.recordGhnCreated("GHN-PRINT-555", BigDecimal.valueOf(20900), NOW.plus(Duration.ofDays(2)), NOW);
        when(shipmentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(shipment));

        when(ghnClient.generatePrintToken(List.of("GHN-PRINT-555")))
                .thenReturn(new PrintTokenResponseDto("a5-dynamic-token-uuid-12345"));

        PrintWaybillResponse printResponse = fulfillmentService.printWaybill(SELLER_ID, ORDER_ID);

        assertThat(printResponse.printUrl()).contains("printA5?token=a5-dynamic-token-uuid-12345");
        assertThat(printResponse.printUrl()).doesNotContain("secret-ghn-token-never-expose");
    }

    @Test
    @DisplayName("A2.9 Reconciliation-first chưa tìm thấy đơn: ném GhnApiException, giải phóng claim, đặt nextReconciliationAt, không gọi tạo đơn mù")
    void testReconciliationFirstNotFoundThrowsWithoutCallingCreateOrder() {
        OrderFulfillmentSnapshot snapshot = createConfirmedOrderSnapshot();
        when(commerceFacade.lockOrderForFulfillment(ORDER_ID)).thenReturn(Optional.of(snapshot));

        String storedClientCode = "OG-DEV-500-RECON-NOTFOUND";
        ShipmentEntity existingPending = new ShipmentEntity(ORDER_ID, storedClientCode, "expired-claim", NOW.minusSeconds(1), NOW.minusSeconds(40));
        when(shipmentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(existingPending));
        when(shipmentRepository.acquireClaim(eq(ORDER_ID), any(), any(), any())).thenReturn(1);

        // GHN chưa tìm thấy đơn qua đối soát (indexing delay hoặc chưa nhận)
        when(ghnClient.getOrderDetailByClientCode(storedClientCode)).thenReturn(null);

        assertThatThrownBy(() -> fulfillmentService.executeShipmentCreationWithLease(SELLER_ID, ORDER_ID, createValidShipmentRequest()))
                .isInstanceOf(GhnApiException.class)
                .hasMessageContaining("Vận đơn đang trong tiến trình chờ GHN xác nhận")
                .hasMessageContaining("vui lòng không gửi lại yêu cầu tạo đơn mới");

        // Tuyệt đối KHÔNG gọi API tạo đơn mới
        verify(ghnClient, never()).createShippingOrder(any());
        // Backoff được đặt qua query nguyên tử releaseClaimWithBackoff, giải phóng claim token-safe
        verify(shipmentRepository).releaseClaimWithBackoff(
                eq(ORDER_ID),
                any(),
                eq(NOW.plusSeconds(30)),
                eq(0),
                contains("chờ GHN xác nhận sau timeout"),
                eq(NOW)
        );
        assertThat(existingPending.getRawGhnStatus()).isNull();
    }

    @Test
    @DisplayName("A2.10 Seller tra cứu getTracking: On-demand reconciliation tự động tìm thấy tracking number khi GHN đã index")
    void testSellerGetTrackingPerformsOnDemandReconciliationWhenIndexed() {
        OrderFulfillmentSnapshot snapshot = createConfirmedOrderSnapshot();
        when(commerceFacade.getOrderSnapshot(ORDER_ID)).thenReturn(Optional.of(snapshot));

        String clientCode = "OG-DEV-500-ONDEMAND";
        ShipmentEntity shipment = new ShipmentEntity(ORDER_ID, clientCode, NOW.minusSeconds(10));

        when(shipmentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(shipment));
        when(shipmentRepository.recordReconciledTracking(eq(ORDER_ID), any(), eq("GHN-ONDEMAND-888"), any(), any()))
                .thenAnswer(inv -> {
                    shipment.setTrackingNumber("GHN-ONDEMAND-888");
                    shipment.setRawGhnStatus("ready_to_pick");
                    return 1;
                });

        // GHN đã index xong và trả về orderCode
        OrderDetailResponseDto indexedDetail = new OrderDetailResponseDto(
                "GHN-ONDEMAND-888", "ready_to_pick", "CHOTHUHANG",
                "Shop", "0912345678", "Address", "20101", 1442,
                "Buyer", "0987654321", "To Address", "20311", 1444
        );
        when(ghnClient.getOrderDetailByClientCode(clientCode)).thenReturn(indexedDetail);
        when(ghnClient.getOrderDetail("GHN-ONDEMAND-888")).thenReturn(indexedDetail);

        TrackingResponse tracking = fulfillmentService.getTracking(SELLER_ID, ORDER_ID);

        assertThat(tracking.trackingNumber()).isEqualTo("GHN-ONDEMAND-888");
        assertThat(tracking.status()).isEqualTo("PENDING");
        assertThat(tracking.rawGhnStatus()).isEqualTo("ready_to_pick");
        assertThat(shipment.getTrackingNumber()).isEqualTo("GHN-ONDEMAND-888");

        verify(shipmentRepository).recordReconciledTracking(eq(ORDER_ID), any(), eq("GHN-ONDEMAND-888"), eq("ready_to_pick"), any());
        verify(shipmentRepository, never()).save(any());
        verify(shipmentRepository).releaseClaim(eq(ORDER_ID), any(), any());
    }

    @Test
    @DisplayName("A2.11 Seller tra cứu getTracking trong thời gian chờ: trả về pending_carrier_confirmation mà không ghi đè DB")
    void testSellerGetTrackingDuringPendingConfirmationReturnsClearStatus() {
        OrderFulfillmentSnapshot snapshot = createConfirmedOrderSnapshot();
        when(commerceFacade.getOrderSnapshot(ORDER_ID)).thenReturn(Optional.of(snapshot));

        String clientCode = "OG-DEV-500-WAITING";
        ShipmentEntity shipment = new ShipmentEntity(ORDER_ID, clientCode, NOW.minusSeconds(10));

        when(shipmentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(shipment));
        when(ghnClient.getOrderDetailByClientCode(clientCode)).thenReturn(null);

        TrackingResponse tracking = fulfillmentService.getTracking(SELLER_ID, ORDER_ID);

        assertThat(tracking.trackingNumber()).isNull();
        assertThat(tracking.status()).isEqualTo("PENDING");
        // DTO hiển thị pending_carrier_confirmation
        assertThat(tracking.rawGhnStatus()).isEqualTo("pending_carrier_confirmation");
        // Nhưng trong DB rawGhnStatus vẫn là null
        assertThat(shipment.getRawGhnStatus()).isNull();
        // Có đặt nextReconciliationAt 15s để chống spam
        assertThat(shipment.getNextReconciliationAt()).isEqualTo(NOW.plusSeconds(15));
        verify(shipmentRepository).releaseClaim(eq(ORDER_ID), any(), any());
    }

    @Test
    @DisplayName("A2.12 On-demand reconciliation trong getTracking: Không gọi GHN nếu không giành được claim (đang có scheduler hoặc tiến trình khác xử lý)")
    void testOnDemandReconciliationSkipsWhenClaimNotAcquired() {
        OrderFulfillmentSnapshot snapshot = createConfirmedOrderSnapshot();
        when(commerceFacade.getOrderSnapshot(ORDER_ID)).thenReturn(Optional.of(snapshot));

        String clientCode = "OG-DEV-500-CLAIM-BUSY";
        ShipmentEntity shipment = new ShipmentEntity(ORDER_ID, clientCode, NOW.minusSeconds(10));

        when(shipmentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(shipment));
        // Giả lập không giành được claim
        when(shipmentRepository.acquireClaim(eq(ORDER_ID), any(), any(), any())).thenReturn(0);

        TrackingResponse tracking = fulfillmentService.getTracking(SELLER_ID, ORDER_ID);

        assertThat(tracking.trackingNumber()).isNull();
        assertThat(tracking.rawGhnStatus()).isEqualTo("pending_carrier_confirmation");
        // Không gọi GHN ra ngoài
        verify(ghnClient, never()).getOrderDetailByClientCode(any());
        verify(shipmentRepository, never()).releaseClaim(eq(ORDER_ID), any(), any());
    }

    @Test
    @DisplayName("A2.13 On-demand reconciliation trong getTracking: Bị chặn bởi cooldown (nextReconciliationAt chưa đến hạn)")
    void testOnDemandReconciliationSkipsWhenCooldownNotPassed() {
        OrderFulfillmentSnapshot snapshot = createConfirmedOrderSnapshot();
        when(commerceFacade.getOrderSnapshot(ORDER_ID)).thenReturn(Optional.of(snapshot));

        String clientCode = "OG-DEV-500-COOLDOWN";
        ShipmentEntity shipment = new ShipmentEntity(ORDER_ID, clientCode, NOW.minusSeconds(10));
        // Backoff còn 20s nữa mới đến hạn
        shipment.setNextReconciliationAt(NOW.plusSeconds(20));

        when(shipmentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(shipment));

        TrackingResponse tracking = fulfillmentService.getTracking(SELLER_ID, ORDER_ID);

        assertThat(tracking.trackingNumber()).isNull();
        assertThat(tracking.rawGhnStatus()).isEqualTo("pending_carrier_confirmation");
        // Không gọi acquireClaim và không gọi GHN
        verify(shipmentRepository, never()).acquireClaim(eq(ORDER_ID), any(), any(), any());
        verify(ghnClient, never()).getOrderDetailByClientCode(any());
    }

    @Test
    @DisplayName("A2.14 On-demand reconciliation trong getTracking: Token-safe claim release và lease 30s")
    void testOnDemandReconciliationUsesTokenSafeReleaseAnd30sLease() {
        OrderFulfillmentSnapshot snapshot = createConfirmedOrderSnapshot();
        when(commerceFacade.getOrderSnapshot(ORDER_ID)).thenReturn(Optional.of(snapshot));

        String clientCode = "OG-DEV-500-ONDEMAND-TOKENSAFE";
        ShipmentEntity shipment = new ShipmentEntity(ORDER_ID, clientCode, NOW.minusSeconds(10));

        when(shipmentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(shipment));

        org.mockito.ArgumentCaptor<String> tokenCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<Instant> expiryCaptor = org.mockito.ArgumentCaptor.forClass(Instant.class);
        when(shipmentRepository.acquireClaim(eq(ORDER_ID), tokenCaptor.capture(), expiryCaptor.capture(), eq(NOW))).thenReturn(1);

        when(ghnClient.getOrderDetailByClientCode(clientCode)).thenReturn(null);

        TrackingResponse tracking = fulfillmentService.getTracking(SELLER_ID, ORDER_ID);

        String acquiredToken = tokenCaptor.getValue();
        Instant expiry = expiryCaptor.getValue();

        // Xác minh lease là 30s (đảm bảo > 15s GHN HTTP connect+read timeout)
        assertThat(acquiredToken).startsWith("ondemand-");
        assertThat(expiry).isEqualTo(NOW.plusSeconds(30));

        // releaseClaimWithBackoff và releaseClaim phải truyền đúng token đã giành được
        verify(shipmentRepository).releaseClaimWithBackoff(eq(ORDER_ID), eq(acquiredToken), any(), anyInt(), any(), eq(NOW));
        verify(shipmentRepository).releaseClaim(eq(ORDER_ID), eq(acquiredToken), any());
    }

    @Test
    @DisplayName("A2.15 Fenced write trong getTracking: On-demand worker A mất lease, Worker B lấy claim, A hoàn thành muộn bị chặn ghi đè dữ liệu của B")
    void testLateOnDemandWorkerAIsFencedFromOverwritingWorkerB() {
        OrderFulfillmentSnapshot snapshot = createConfirmedOrderSnapshot();
        when(commerceFacade.getOrderSnapshot(ORDER_ID)).thenReturn(Optional.of(snapshot));

        String clientCode = "OG-DEV-500-ONDEMAND-FENCED";
        // Ban đầu shipment không có claim active để Worker A có thể bắt đầu on-demand reconciliation
        ShipmentEntity initialShipment = new ShipmentEntity(ORDER_ID, clientCode, NOW.minusSeconds(10));

        // Giả lập shipment sau đó trong DB đang thuộc quyền sở hữu của Worker B
        ShipmentEntity shipmentHeldByWorkerB = new ShipmentEntity(ORDER_ID, clientCode, "workerB-token", NOW.plusSeconds(30), NOW.minusSeconds(40));
        shipmentHeldByWorkerB.setIncidentNotes("Worker B đang xử lý");

        when(shipmentRepository.findByOrderId(ORDER_ID))
                .thenReturn(Optional.of(initialShipment))
                .thenReturn(Optional.of(shipmentHeldByWorkerB));

        // Worker A ban đầu giành được claim với tokenA
        org.mockito.ArgumentCaptor<String> tokenCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        when(shipmentRepository.acquireClaim(eq(ORDER_ID), tokenCaptor.capture(), any(), eq(NOW))).thenReturn(1);

        // GHN phản hồi cho Worker A sau thời gian trễ
        OrderDetailResponseDto lateDetail = new OrderDetailResponseDto(
                "GHN-LATE-888", "ready_to_pick", "CHOTHUHANG",
                "Shop", "0912345678", "Address", "20101", 1442,
                "Buyer", "0987654321", "To Address", "20311", 1444
        );
        when(ghnClient.getOrderDetailByClientCode(clientCode)).thenReturn(lateDetail);

        // DB từ chối ghi dữ liệu của Worker A (0 rows) vì claim_token hiện tại trong DB là của Worker B
        when(shipmentRepository.recordReconciledTracking(eq(ORDER_ID), any(), any(), any(), any())).thenReturn(0);

        TrackingResponse tracking = fulfillmentService.getTracking(SELLER_ID, ORDER_ID);

        String workerAToken = tokenCaptor.getValue();
        assertThat(workerAToken).startsWith("ondemand-");

        // 1. Worker A gửi lệnh ghi có kiểm tra token nhưng DB trả về 0 rows (fenced)
        verify(shipmentRepository).recordReconciledTracking(
                eq(ORDER_ID),
                eq(workerAToken),
                eq("GHN-LATE-888"),
                eq("ready_to_pick"),
                eq(NOW)
        );

        // 2. Tuyệt đối không gọi save() làm ghi đè entity của Worker B
        verify(shipmentRepository, never()).save(any());

        // 3. Dữ liệu của Worker B trong DB được bảo toàn (chưa có tracking_number, claim của B còn nguyên)
        assertThat(shipmentHeldByWorkerB.getClaimToken()).isEqualTo("workerB-token");
        assertThat(shipmentHeldByWorkerB.getTrackingNumber()).isNull();
        assertThat(shipmentHeldByWorkerB.getIncidentNotes()).isEqualTo("Worker B đang xử lý");

        // 4. Trong finally, Worker A chỉ gọi releaseClaim với token của mình (không xóa token của Worker B)
        verify(shipmentRepository).releaseClaim(eq(ORDER_ID), eq(workerAToken), any());
    }

    @Test
    @DisplayName("A2.16 Seller getTracking on-demand: Webhook cập nhật DELIVERED ngay sau khi ghi nhận tracking thì getTracking phản ánh DELIVERED và không lùi trạng thái")
    void testOnDemandGetTrackingPreservesMonotonicWebhookStatus() {
        OrderFulfillmentSnapshot snapshot = createConfirmedOrderSnapshot();
        when(commerceFacade.getOrderSnapshot(ORDER_ID)).thenReturn(Optional.of(snapshot));

        String clientCode = "OG-DEV-500-MONO";
        ShipmentEntity shipment = new ShipmentEntity(ORDER_ID, clientCode, NOW.minusSeconds(10));

        when(shipmentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(shipment));

        String trackingCode = "GHN-ONDEMAND-999";
        OrderDetailResponseDto detail = new OrderDetailResponseDto(
                trackingCode, "ready_to_pick", "CHOTHUHANG",
                "Shop", "0912345678", "Address", "20101", 1442,
                "Buyer", "0987654321", "To Address", "20311", 1444
        );
        when(ghnClient.getOrderDetailByClientCode(clientCode)).thenReturn(detail);
        when(ghnClient.getOrderDetail(trackingCode)).thenReturn(detail);

        // recordReconciledTracking thành công
        when(shipmentRepository.recordReconciledTracking(eq(ORDER_ID), any(), eq(trackingCode), any(), any()))
                .thenReturn(1);

        // Ngay sau recordReconciledTracking, Webhook đã chuyển shipment sang DELIVERED
        ShipmentEntity webhookDelivered = new ShipmentEntity(ORDER_ID, clientCode, NOW.minusSeconds(10));
        webhookDelivered.setTrackingNumber(trackingCode);
        webhookDelivered.markDelivered(NOW, NOW, NOW);
        assertThat(webhookDelivered.getStatus()).isEqualTo(ShipmentEntity.STATUS_DELIVERED);

        // Lần findByOrderId tiếp theo trả về entity DELIVERED từ webhook
        when(shipmentRepository.findByOrderId(ORDER_ID))
                .thenReturn(Optional.of(shipment))           // lần 1 lúc bắt đầu getTracking
                .thenReturn(Optional.of(webhookDelivered));   // lần 2 sau recordReconciledTracking

        TrackingResponse tracking = fulfillmentService.getTracking(SELLER_ID, ORDER_ID);

        // 1. Phản ánh đúng trạng thái DELIVERED mới hơn từ webhook
        assertThat(tracking.status()).isEqualTo(ShipmentEntity.STATUS_DELIVERED);
        assertThat(tracking.trackingNumber()).isEqualTo(trackingCode);

        // 2. Tuyệt đối không gọi save() làm lùi trạng thái
        verify(shipmentRepository, never()).save(any());
    }
}
