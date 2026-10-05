package com.oldbutgold.shop.modules.payment.application;

import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductMediaEntity;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductMediaRepository;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderEntity;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderItemEntity;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderItemRepository;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderRepository;
import com.oldbutgold.shop.modules.payment.infrastructure.persistence.PaymentEntity;
import com.oldbutgold.shop.modules.payment.infrastructure.persistence.PaymentRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentService {
    public static final String BANK_NAME = "Ngân hàng Quân Đội (MB Bank)";
    public static final String ACCOUNT_NUMBER = "0388654321";
    public static final String ACCOUNT_NAME = "CONG TY CP OLD BUT GOLD";

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductMediaRepository productMediaRepository;
    private final Clock clock;

    public PaymentService(
            PaymentRepository paymentRepository,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            ProductMediaRepository productMediaRepository,
            Clock clock
    ) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.productMediaRepository = productMediaRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PaymentDtos.PaymentInfoResponse getPaymentInfo(Long buyerId, Long orderId) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng #" + orderId));

        if (!order.getBuyerId().equals(buyerId)) {
            throw new AccessDeniedException("Bạn không có quyền truy cập thông tin thanh toán của đơn hàng này.");
        }

        Instant now = clock.instant();
        long remainingSeconds = 0;
        if (OrderEntity.STATUS_PAYMENT_PENDING.equals(order.getStatus())) {
            remainingSeconds = Math.max(0, Duration.between(now, order.getPaymentDueAt()).getSeconds());
        }

        List<OrderItemEntity> items = orderItemRepository.findByOrderId(orderId);
        String productTitle = !items.isEmpty() ? items.get(0).getProductTitle() : "Đơn hàng #" + orderId;
        String productThumbnail = null;
        if (!items.isEmpty()) {
            List<ProductMediaEntity> media = productMediaRepository.findByProductIdOrderByDisplayOrderAscIdAsc(items.get(0).getProductId());
            if (!media.isEmpty()) {
                productThumbnail = media.get(0).getMediaUrl();
            }
        }

        PaymentEntity payment = paymentRepository.findByOrderId(orderId).orElse(null);

        String transferContent = "OGSHOP " + orderId;
        String qrCodeMockUrl = String.format(
                "https://img.vietqr.io/image/MB-%s-compact2.png?amount=%s&addInfo=%s&accountName=%s",
                ACCOUNT_NUMBER,
                order.getTotalAmount().toPlainString(),
                transferContent.replace(" ", "%20"),
                ACCOUNT_NAME.replace(" ", "%20")
        );

        String fullAddress = String.format("%s, %s, %s, %s",
                order.getShippingDetailAddress(),
                order.getShippingWard(),
                order.getShippingDistrict(),
                order.getShippingProvince());

        return new PaymentDtos.PaymentInfoResponse(
                order.getId(),
                payment != null ? payment.getId() : null,
                productTitle,
                productThumbnail,
                order.getTotalAmount(),
                order.getCurrency(),
                order.getStatus(),
                payment != null ? payment.getStatus() : "CHUA_KHOI_TAO",
                payment != null ? payment.getPaymentMethod() : PaymentEntity.METHOD_BANK_TRANSFER,
                payment != null ? payment.getTransactionCode() : null,
                order.getPaymentDueAt(),
                remainingSeconds,
                qrCodeMockUrl,
                BANK_NAME,
                ACCOUNT_NUMBER,
                ACCOUNT_NAME,
                transferContent,
                order.getShippingRecipientName(),
                order.getShippingPhoneNumber(),
                fullAddress,
                order.getSubtotal(),
                order.getShippingFee(),
                order.getVoucherDiscountAmount(),
                order.getSellerId()
        );
    }

    @Transactional
    public PaymentDtos.PaymentProcessResultResponse processMockPayment(Long buyerId, PaymentDtos.ProcessMockPaymentRequest request) {
        Instant now = clock.instant();

        OrderEntity order = orderRepository.findByIdForUpdate(request.orderId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng #" + request.orderId()));

        if (!order.getBuyerId().equals(buyerId)) {
            throw new AccessDeniedException("Bạn không có quyền thanh toán cho đơn hàng này.");
        }

        if (!OrderEntity.STATUS_PAYMENT_PENDING.equals(order.getStatus())) {
            throw new IllegalStateException("Đơn hàng không ở trạng thái chờ thanh toán. Trạng thái hiện tại: " + order.getStatus());
        }

        if (now.isAfter(order.getPaymentDueAt())) {
            throw new IllegalStateException("Đơn hàng đã quá hạn thanh toán. Vui lòng đặt hàng lại.");
        }

        String method = request.paymentMethod().trim().toUpperCase();
        if (!PaymentEntity.METHOD_BANK_TRANSFER.equals(method) &&
            !PaymentEntity.METHOD_E_WALLET.equals(method) &&
            !PaymentEntity.METHOD_COD.equals(method)) {
            throw new IllegalArgumentException("Phương thức thanh toán không hợp lệ: " + method);
        }

        PaymentEntity payment = paymentRepository.findByOrderIdForUpdate(order.getId())
                .orElseGet(() -> new PaymentEntity(order.getId(), order.getTotalAmount(), method, now));
        if (PaymentEntity.METHOD_VNPAY.equals(payment.getPaymentMethod())) {
            throw new IllegalStateException("Giao dịch VNPAY phải được xác nhận bởi nhà cung cấp.");
        }
        payment.setPaymentMethod(method);

        if (request.simulateSuccess()) {
            String transactionCode = "TXN_MOCK_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            payment.markHeld(transactionCode, now);
            order.markPaidHeld(now);

            paymentRepository.save(payment);
            orderRepository.save(order);

            return new PaymentDtos.PaymentProcessResultResponse(
                    payment.getId(),
                    order.getId(),
                    order.getStatus(),
                    payment.getStatus(),
                    payment.getTransactionCode(),
                    "Thanh toán thành công! Tiền được đưa vào tài khoản Ký quỹ Escrow của Old but Gold để bảo vệ người mua và người bán."
            );
        } else {
            payment.markFailed("Người mua giả lập lỗi giao dịch thanh toán", now);
            paymentRepository.save(payment);

            return new PaymentDtos.PaymentProcessResultResponse(
                    payment.getId(),
                    order.getId(),
                    order.getStatus(),
                    payment.getStatus(),
                    null,
                    "Giao dịch thanh toán thất bại (Mô phỏng). Bạn có thể thử lại trước hạn thanh toán của đơn."
            );
        }
    }
}
