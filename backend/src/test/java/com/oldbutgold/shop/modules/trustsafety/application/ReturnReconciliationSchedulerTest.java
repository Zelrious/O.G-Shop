package com.oldbutgold.shop.modules.trustsafety.application;

import com.oldbutgold.shop.modules.commerce.application.OrderManagementDtos;
import com.oldbutgold.shop.modules.commerce.application.OrderManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReturnReconciliationSchedulerTest - Unit test for scheduler delegation & safety (Task 3.2.4 & Task 3.3)")
class ReturnReconciliationSchedulerTest {

    @Mock
    private ReturnCaseService returnCaseService;

    @Mock
    private OrderManagementService orderManagementService;

    private ReturnReconciliationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ReturnReconciliationScheduler(returnCaseService, orderManagementService, true);
    }

    @Test
    @DisplayName("Scheduler calls reconcileOverdueReturnResponses on ReturnCaseService")
    void reconcileOverdueReturnResponses_delegatesToService() {
        ReturnCaseDtos.TimeoutBatchResult result = new ReturnCaseDtos.TimeoutBatchResult(
                2, List.of(101L), List.of(102L), List.of(), List.of()
        );
        when(returnCaseService.reconcileOverdueReturnResponses()).thenReturn(result);

        scheduler.reconcileOverdueReturnResponses();

        verify(returnCaseService).reconcileOverdueReturnResponses();
    }

    @Test
    @DisplayName("Scheduler swallows unexpected exceptions safely to protect scheduling loop")
    void reconcileOverdueReturnResponses_exceptionSafety() {
        when(returnCaseService.reconcileOverdueReturnResponses())
                .thenThrow(new RuntimeException("Database down during scheduled scan"));

        // Should not throw
        scheduler.reconcileOverdueReturnResponses();

        verify(returnCaseService).reconcileOverdueReturnResponses();
    }

    @Test
    @DisplayName("Task 3.2.5: Scheduler calls reconcileKtvOverdueSla on ReturnCaseService")
    void reconcileKtvOverdueSla_delegatesToService() {
        ReturnCaseDtos.EscalationBatchResult result = new ReturnCaseDtos.EscalationBatchResult(
                2, List.of(201L), List.of(202L), List.of(), List.of()
        );
        when(returnCaseService.reconcileKtvOverdueSla()).thenReturn(result);

        scheduler.reconcileKtvOverdueSla();

        verify(returnCaseService).reconcileKtvOverdueSla();
    }

    @Test
    @DisplayName("Task 3.2.5: Scheduler swallows unexpected exceptions during SLA escalation safely")
    void reconcileKtvOverdueSla_exceptionSafety() {
        when(returnCaseService.reconcileKtvOverdueSla())
                .thenThrow(new RuntimeException("Database down during scheduled SLA escalation scan"));

        // Should not throw
        scheduler.reconcileKtvOverdueSla();

        verify(returnCaseService).reconcileKtvOverdueSla();
    }

    @Test
    @DisplayName("Task 3.3: Scheduler calls reconcileOverdueDeliveries on OrderManagementService when enabled")
    void reconcileOverdueDeliveries_delegatesToService() {
        OrderManagementDtos.OverdueDeliveryBatchResult batchResult = new OrderManagementDtos.OverdueDeliveryBatchResult(
                2, List.of(101L, 102L), List.of(), List.of()
        );
        when(orderManagementService.reconcileOverdueDeliveries()).thenReturn(batchResult);

        scheduler.reconcileOverdueDeliveries();

        verify(orderManagementService).reconcileOverdueDeliveries();
    }

    @Test
    @DisplayName("Task 3.3: Scheduler skips execution when overdueDisbursementEnabled is false")
    void reconcileOverdueDeliveries_disabled_skipsExecution() {
        ReturnReconciliationScheduler disabledScheduler = new ReturnReconciliationScheduler(
                returnCaseService, orderManagementService, false
        );

        disabledScheduler.reconcileOverdueDeliveries();

        verify(orderManagementService, never()).reconcileOverdueDeliveries();
    }

    @Test
    @DisplayName("Task 3.3: Scheduler swallows unexpected exceptions during overdue delivery disbursement safely")
    void reconcileOverdueDeliveries_exceptionSafety() {
        when(orderManagementService.reconcileOverdueDeliveries())
                .thenThrow(new RuntimeException("Database down during overdue delivery scan"));

        assertThatNoException().isThrownBy(scheduler::reconcileOverdueDeliveries);

        verify(orderManagementService).reconcileOverdueDeliveries();
    }
}

