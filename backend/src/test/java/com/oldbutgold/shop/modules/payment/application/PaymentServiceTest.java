package com.oldbutgold.shop.modules.payment.application;

import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductMediaRepository;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderEntity;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderItemEntity;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderItemRepository;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderRepository;
import com.oldbutgold.shop.modules.payment.infrastructure.persistence.PaymentEntity;
import com.oldbutgold.shop.modules.payment.infrastructure.persistence.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentServiceTest {
    private PaymentRepository paymentRepository;
    private OrderRepository orderRepository;
    private OrderItemRepository orderItemRepository;
    private ProductMediaRepository productMediaRepository;
    private Clock clock;

    private PaymentService paymentService;
    private Instant now;
    private OrderEntity order;

    @BeforeEach
    void setUp() throws Exception {
        paymentRepository = mock(PaymentRepository.class);
        orderRepository = mock(OrderRepository.class);
        orderItemRepository = mock(OrderItemRepository.class);
        productMediaRepository = mock(ProductMediaRepository.class);

        now = Instant.parse("2026-09-21T10:00:00Z");
        clock = Clock.fixed(now, ZoneId.of("UTC"));

        paymentService = new PaymentService(
                paymentRepository,
                orderRepository,
                orderItemRepository,
                productMediaRepository,
                clock
        );

        order = new OrderEntity(
                UUID.randomUUID(),
                100L, // buyerId
                200L, // sellerId
                1L,
                "Nguyễn Văn A",
                "0901234567",
                "Hồ Chí Minh",
                "Quận 1",
                "Phường Bến Nghé",
                "123 Lê Lợi",
                new BigDecimal("500000.00"),
                new BigDecimal("30000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("500000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "PLATFORM",
                new BigDecimal("530000.00"),
                now.plus(Duration.ofHours(1)),
                now
        );
        setId(order, 1L);
    }

    @Test
    void getPaymentInfo_success() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.empty());

        OrderItemEntity item = new OrderItemEntity(
                1L, 10L, "Máy ảnh cơ Canon QL17 GIII", (short) 1,
                new BigDecimal("500000.00"), new BigDecimal("500000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("500000.00"),
                new BigDecimal("500000.00"), 1L, null, "LIST_PRICE"
        );
        when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of(item));

        PaymentDtos.PaymentInfoResponse response = paymentService.getPaymentInfo(100L, 1L);

        assertThat(response.orderId()).isEqualTo(1L);
        assertThat(response.productTitle()).isEqualTo("Máy ảnh cơ Canon QL17 GIII");
        assertThat(response.totalAmount()).isEqualByComparingTo("530000.00");
        assertThat(response.orderStatus()).isEqualTo("PAYMENT_PENDING");
        assertThat(response.remainingSeconds()).isEqualTo(3600); // 1 hour = 3600s
        assertThat(response.recipientName()).isEqualTo("Nguyễn Văn A");
        assertThat(response.fullAddress()).contains("123 Lê Lợi");
        assertThat(response.qrCodeMockUrl()).contains("0388654321");
    }

    @Test
    void getPaymentInfo_wrongBuyer_throwsAccessDenied() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentService.getPaymentInfo(999L, 1L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Bạn không có quyền truy cập");
    }

    @Test
    void processMockPayment_success_marksHeldAndPaidHeld() {
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdForUpdate(1L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(PaymentEntity.class))).thenAnswer(inv -> {
            PaymentEntity p = inv.getArgument(0);
            setId(p, 55L);
            return p;
        });
        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentDtos.ProcessMockPaymentRequest request = new PaymentDtos.ProcessMockPaymentRequest(
                1L,
                PaymentEntity.METHOD_BANK_TRANSFER,
                true
        );

        PaymentDtos.PaymentProcessResultResponse result = paymentService.processMockPayment(100L, request);

        assertThat(result.orderId()).isEqualTo(1L);
        assertThat(result.orderStatus()).isEqualTo("PAID_HELD");
        assertThat(result.paymentStatus()).isEqualTo("HELD");
        assertThat(result.transactionCode()).startsWith("TXN_MOCK_");
        assertThat(order.getStatus()).isEqualTo(OrderEntity.STATUS_PAID_HELD);
    }

    @Test
    void processMockPayment_failureSimulation_marksFailed() {
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdForUpdate(1L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(PaymentEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentDtos.ProcessMockPaymentRequest request = new PaymentDtos.ProcessMockPaymentRequest(
                1L,
                PaymentEntity.METHOD_E_WALLET,
                false
        );

        PaymentDtos.PaymentProcessResultResponse result = paymentService.processMockPayment(100L, request);

        assertThat(result.orderStatus()).isEqualTo("PAYMENT_PENDING");
        assertThat(result.paymentStatus()).isEqualTo("FAILED");
        assertThat(order.getStatus()).isEqualTo(OrderEntity.STATUS_PAYMENT_PENDING);
    }

    @Test
    void processMockPayment_overdueOrder_throwsIllegalStateException() {
        // Fast-forward clock 65 minutes (over 1 hour)
        Clock lateClock = Clock.fixed(now.plus(Duration.ofMinutes(65)), ZoneId.of("UTC"));
        PaymentService lateService = new PaymentService(
                paymentRepository, orderRepository, orderItemRepository, productMediaRepository, lateClock
        );

        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        PaymentDtos.ProcessMockPaymentRequest request = new PaymentDtos.ProcessMockPaymentRequest(
                1L,
                PaymentEntity.METHOD_BANK_TRANSFER,
                true
        );

        assertThatThrownBy(() -> lateService.processMockPayment(100L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("quá hạn thanh toán");
    }

    private static void setId(Object target, Long id) throws Exception {
        Field idField = target.getClass().getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(target, id);
    }
}
