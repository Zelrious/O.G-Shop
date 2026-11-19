package com.oldbutgold.shop.modules.trustsafety.application;

import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderEntity;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderRepository;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseEntity;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseRepository;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseRoundEntity;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseRoundRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReturnWorkflowLockGatewayTest - Tests canonical lock ordering Order -> Case -> CaseRound")
class ReturnWorkflowLockGatewayTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CaseRepository caseRepository;

    @Mock
    private CaseRoundRepository caseRoundRepository;

    @Mock
    private EntityManager entityManager;

    private ReturnWorkflowLockGateway gateway;

    private final Long orderId = 1001L;
    private final Long caseId = 501L;

    @BeforeEach
    void setUp() {
        gateway = new ReturnWorkflowLockGateway(orderRepository, caseRepository, caseRoundRepository, entityManager);
    }

    @Test
    @DisplayName("lockOrder: Acquires lock on order successfully")
    void lockOrder_success() {
        OrderEntity order = mock(OrderEntity.class);
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        OrderEntity locked = gateway.lockOrder(orderId);

        assertThat(locked).isSameAs(order);
        verify(orderRepository).findByIdForUpdate(orderId);
    }

    @Test
    @DisplayName("lockOrder: Throws NoSuchElementException when order not found")
    void lockOrder_notFound_throwsException() {
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.empty());
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gateway.lockOrder(orderId))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Không tìm thấy đơn hàng");
    }

    @Test
    @DisplayName("lockForCase: Strictly acquires lock on Order first, then Case second")
    void lockForCase_strictOrderThenCaseLock() {
        CaseEntity uncommittedCase = mock(CaseEntity.class);
        when(uncommittedCase.getOrderId()).thenReturn(orderId);

        OrderEntity order = mock(OrderEntity.class);
        CaseEntity lockedCase = mock(CaseEntity.class);
        when(lockedCase.getOrderId()).thenReturn(orderId);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(uncommittedCase));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(caseRepository.findByIdForUpdate(caseId)).thenReturn(Optional.of(lockedCase));

        var result = gateway.lockForCase(caseId);

        assertThat(result.order()).isSameAs(order);
        assertThat(result.returnCase()).isSameAs(lockedCase);
        assertThat(result.activeRound()).isNull();

        // Verify strict lock sequence: Order -> Case
        InOrder inOrder = inOrder(orderRepository, caseRepository);
        inOrder.verify(caseRepository).findById(caseId);
        inOrder.verify(orderRepository).findByIdForUpdate(orderId);
        inOrder.verify(caseRepository).findByIdForUpdate(caseId);
    }

    @Test
    @DisplayName("lockForCase: Throws IllegalStateException when invariant violated (orderId mismatch)")
    void lockForCase_invariantViolation_throwsException() {
        CaseEntity uncommittedCase = mock(CaseEntity.class);
        when(uncommittedCase.getOrderId()).thenReturn(orderId);

        OrderEntity order = mock(OrderEntity.class);
        CaseEntity lockedCase = mock(CaseEntity.class);
        when(lockedCase.getOrderId()).thenReturn(9999L); // Mismatch!

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(uncommittedCase));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(caseRepository.findByIdForUpdate(caseId)).thenReturn(Optional.of(lockedCase));

        assertThatThrownBy(() -> gateway.lockForCase(caseId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Xung đột dữ liệu");
    }

    @Test
    @DisplayName("lockForRound: Strictly acquires lock on Order -> Case -> CaseRound")
    void lockForRound_strictOrderCaseRoundSequence() {
        CaseEntity uncommittedCase = mock(CaseEntity.class);
        when(uncommittedCase.getOrderId()).thenReturn(orderId);

        OrderEntity order = mock(OrderEntity.class);
        CaseEntity lockedCase = mock(CaseEntity.class);
        when(lockedCase.getOrderId()).thenReturn(orderId);

        CaseRoundEntity round1 = mock(CaseRoundEntity.class);
        when(round1.getCaseId()).thenReturn(caseId);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(uncommittedCase));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(caseRepository.findByIdForUpdate(caseId)).thenReturn(Optional.of(lockedCase));
        when(caseRoundRepository.findByCaseIdAndRoundNoForUpdate(caseId, 1)).thenReturn(Optional.of(round1));

        var result = gateway.lockForRound(caseId, 1);

        assertThat(result.order()).isSameAs(order);
        assertThat(result.returnCase()).isSameAs(lockedCase);
        assertThat(result.activeRound()).isSameAs(round1);

        InOrder inOrder = inOrder(orderRepository, caseRepository, caseRoundRepository);
        inOrder.verify(orderRepository).findByIdForUpdate(orderId);
        inOrder.verify(caseRepository).findByIdForUpdate(caseId);
        inOrder.verify(caseRoundRepository).findByCaseIdAndRoundNoForUpdate(caseId, 1);
    }

    @Test
    @DisplayName("lockForLatestRound: Acquires lock on Order -> Case -> Latest Round")
    void lockForLatestRound_success() {
        CaseEntity uncommittedCase = mock(CaseEntity.class);
        when(uncommittedCase.getOrderId()).thenReturn(orderId);

        OrderEntity order = mock(OrderEntity.class);
        CaseEntity lockedCase = mock(CaseEntity.class);
        when(lockedCase.getOrderId()).thenReturn(orderId);

        CaseRoundEntity round2 = mock(CaseRoundEntity.class);
        when(round2.getCaseId()).thenReturn(caseId);

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(uncommittedCase));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(caseRepository.findByIdForUpdate(caseId)).thenReturn(Optional.of(lockedCase));
        when(caseRoundRepository.findLatestRoundByCaseIdForUpdate(caseId)).thenReturn(Optional.of(round2));

        var result = gateway.lockForLatestRound(caseId);

        assertThat(result.order()).isSameAs(order);
        assertThat(result.returnCase()).isSameAs(lockedCase);
        assertThat(result.activeRound()).isSameAs(round2);
    }
}

