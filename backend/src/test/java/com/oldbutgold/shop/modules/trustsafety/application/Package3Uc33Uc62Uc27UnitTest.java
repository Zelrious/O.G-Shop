package com.oldbutgold.shop.modules.trustsafety.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oldbutgold.shop.modules.catalog.application.CatalogCommerceFacade;
import com.oldbutgold.shop.modules.commerce.application.FulfillmentSimulationGuard;
import com.oldbutgold.shop.modules.commerce.application.OrderManagementDtos;
import com.oldbutgold.shop.modules.commerce.application.OrderManagementService;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderEntity;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderItemRepository;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderRepository;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderVoucherRepository;
import com.oldbutgold.shop.modules.fulfillment.application.FulfillmentService;
import com.oldbutgold.shop.modules.fulfillment.application.FulfillmentStateService;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.persistence.ShipmentRepository;
import com.oldbutgold.shop.modules.identity.application.IdentityCommerceFacade;
import com.oldbutgold.shop.modules.payment.infrastructure.persistence.PaymentRepository;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseDecisionRepository;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseEntity;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseEventEntity;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseEventRepository;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseEvidenceEntity;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseEvidenceRepository;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseEvidenceRequestEntity;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseEvidenceRequestRepository;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseRepository;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseRoundEntity;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseRoundRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Package3Uc33Uc62Uc27UnitTest - 3A Overdue Pickup, 3B Round 1 Supplement, 3C Confirm Received Video Option")
class Package3Uc33Uc62Uc27UnitTest {

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-11-23T16:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("3C: confirmReceived records RECEIPT_CONFIRMED order_event with VIDEO_RECORDED and rejects invalid option")
    void confirmReceived_recordsVideoOptionAndRejectsInvalidValue() {
        OrderRepository orderRepo = mock(OrderRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        OrderManagementService svc = new OrderManagementService(
                orderRepo,
                mock(OrderItemRepository.class),
                mock(OrderVoucherRepository.class),
                mock(CatalogCommerceFacade.class),
                mock(PaymentRepository.class),
                mock(IdentityCommerceFacade.class),
                mock(ShipmentRepository.class),
                mock(FulfillmentService.class),
                mock(FulfillmentStateService.class),
                mock(FulfillmentSimulationGuard.class),
                mock(TransactionTemplate.class),
                fixedClock,
                jdbc,
                mock(EntityManager.class)
        );

        OrderEntity order = mock(OrderEntity.class);
        when(order.getBuyerId()).thenReturn(10L);
        when(order.getStatus()).thenReturn(OrderEntity.STATUS_DELIVERED);
        when(order.getReceivedAt()).thenReturn(null);
        when(orderRepo.findByIdForUpdate(100L)).thenReturn(Optional.of(order));

        var res = svc.confirmReceived(10L, 100L, new OrderManagementDtos.ConfirmReceivedRequest("VIDEO_RECORDED"));
        assertThat(res.orderId()).isEqualTo(100L);
        verify(order).setReceivedAt(fixedClock.instant());
        verify(jdbc).update(contains("RECEIPT_CONFIRMED"), any(), eq(100L), eq(10L), eq("{\"videoOption\":\"VIDEO_RECORDED\"}"), any(), eq("RECEIPT_CONFIRMED:100"));

        assertThatThrownBy(() -> svc.confirmReceived(10L, 100L, new OrderManagementDtos.ConfirmReceivedRequest("INVALID_OPT")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("VIDEO_RECORDED");
    }

    @Test
    @DisplayName("3B: Buyer supplements Round 1 evidence (append-only batch 2, fulfills pending Round 1 request)")
    void supplementBuyerEvidenceRound1_appendsBatch2AndFulfillsPendingRequest() {
        CaseRepository caseRepo = mock(CaseRepository.class);
        CaseRoundRepository roundRepo = mock(CaseRoundRepository.class);
        CaseEvidenceRepository evRepo = mock(CaseEvidenceRepository.class);
        CaseDecisionRepository decRepo = mock(CaseDecisionRepository.class);
        CaseEventRepository eventRepo = mock(CaseEventRepository.class);
        OrderRepository orderRepo = mock(OrderRepository.class);
        CaseEvidenceRequestRepository reqRepo = mock(CaseEvidenceRequestRepository.class);

        ReturnCaseService svc = new ReturnCaseService(
                caseRepo, roundRepo, evRepo, decRepo, eventRepo,
                orderRepo, mock(PaymentRepository.class), mock(ShipmentRepository.class),
                mock(EntityManager.class), mock(JdbcTemplate.class), new ObjectMapper(), fixedClock,
                mock(ReturnWorkflowLockGateway.class), mock(RoundDecisionSettlementPort.class),
                mock(PlatformTransactionManager.class), reqRepo
        );

        CaseEntity c = mock(CaseEntity.class);
        when(c.getId()).thenReturn(50L);
        when(c.getOrderId()).thenReturn(500L);
        when(c.getState()).thenReturn(CaseEntity.STATE_REVIEWING);
        when(c.getCreatedBy()).thenReturn(1L);
        when(caseRepo.findById(50L)).thenReturn(Optional.of(c));
        when(caseRepo.findByIdForUpdate(50L)).thenReturn(Optional.of(c));

        OrderEntity order = mock(OrderEntity.class);
        when(order.getId()).thenReturn(500L);
        when(order.getBuyerId()).thenReturn(1L);
        when(order.getSellerId()).thenReturn(2L);
        when(orderRepo.findByIdForUpdate(500L)).thenReturn(Optional.of(order));

        CaseRoundEntity round1 = mock(CaseRoundEntity.class);
        when(round1.getId()).thenReturn(11L);
        when(round1.getRoundNo()).thenReturn(1);
        when(round1.getState()).thenReturn(CaseRoundEntity.STATE_OPEN);
        when(roundRepo.findByCaseIdAndRoundNo(50L, 1)).thenReturn(Optional.of(round1));
        when(roundRepo.findByCaseIdOrderByRoundNoAsc(50L)).thenReturn(List.of(round1));
        when(decRepo.findByRoundIdOrderByDecidedAtAsc(11L)).thenReturn(List.of());

        CaseEvidenceEntity batch1 = mock(CaseEvidenceEntity.class);
        when(batch1.getSubmissionNo()).thenReturn(1);
        when(evRepo.findByRoundIdOrderBySubmittedAtAsc(11L)).thenReturn(List.of(batch1));
        when(eventRepo.findByCommandKey("CMD_SUPP_1")).thenReturn(Optional.empty());

        CaseEvidenceRequestEntity pendingReq = mock(CaseEvidenceRequestEntity.class);
        UUID reqId = UUID.randomUUID();
        when(pendingReq.getId()).thenReturn(reqId);
        when(pendingReq.getRoundId()).thenReturn(11L);
        when(reqRepo.findByCaseIdAndStatus(50L, CaseEvidenceRequestEntity.STATUS_PENDING)).thenReturn(Optional.of(pendingReq));

        String digest = "a".repeat(64);
        var req = new ReturnCaseDtos.BuyerSupplementEvidenceRequest(
                "Bổ sung ảnh cận cảnh vết nứt",
                List.of(new ReturnCaseDtos.EvidenceItemDto("IMAGE", "evidence/img2.jpg", null, digest, 1024L, null)),
                "CMD_SUPP_1"
        );

        var detail = svc.supplementBuyerEvidenceRound1(1L, 50L, req);
        assertThat(detail).isNotNull();
        verify(pendingReq).setStatus(CaseEvidenceRequestEntity.STATUS_FULFILLED);
        verify(pendingReq).setFulfilledAt(fixedClock.instant());
        verify(evRepo).save(any(CaseEvidenceEntity.class));
        verify(eventRepo).save(any(CaseEventEntity.class));
    }

    @Test
    @DisplayName("3A: reconcileOverdueReturnPickups closes buyer-fault cases and escalates carrier-fault cases; scheduler respects toggle")
    void reconcileOverdueReturnPickups_closesBuyerFaultAndEscalatesCarrierFault() {
        CaseRepository caseRepo = mock(CaseRepository.class);
        CaseRoundRepository roundRepo = mock(CaseRoundRepository.class);
        CaseEventRepository eventRepo = mock(CaseEventRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);

        ReturnCaseService svc = new ReturnCaseService(
                caseRepo, roundRepo, mock(CaseEvidenceRepository.class), mock(CaseDecisionRepository.class), eventRepo,
                mock(OrderRepository.class), mock(PaymentRepository.class), mock(ShipmentRepository.class),
                mock(EntityManager.class), jdbc, new ObjectMapper(), fixedClock,
                mock(ReturnWorkflowLockGateway.class), mock(RoundDecisionSettlementPort.class),
                mock(PlatformTransactionManager.class), mock(CaseEvidenceRequestRepository.class)
        );

        Map<String, Object> buyerFaultRow = Map.of(
                "case_id", 701L,
                "order_id", 7001L,
                "state", "RETURN_APPROVED",
                "carrier_fault_flagged", false
        );
        Map<String, Object> carrierFaultRow = Map.of(
                "case_id", 702L,
                "order_id", 7002L,
                "state", "RETURNING",
                "carrier_fault_flagged", true
        );
        when(jdbc.queryForList(contains("FROM og_compat.cases"), any(Object.class), any(Object.class)))
                .thenReturn(List.of(buyerFaultRow, carrierFaultRow));

        CaseEntity c1 = mock(CaseEntity.class);
        when(c1.getState()).thenReturn(CaseEntity.STATE_RETURN_APPROVED);
        when(c1.getCreatedBy()).thenReturn(10L);
        when(caseRepo.findByIdForUpdate(701L)).thenReturn(Optional.of(c1));

        CaseEntity c2 = mock(CaseEntity.class);
        when(c2.getState()).thenReturn(CaseEntity.STATE_RETURNING);
        when(c2.getAssignedTo()).thenReturn(99L);
        when(caseRepo.findByIdForUpdate(702L)).thenReturn(Optional.of(c2));

        when(roundRepo.findLatestRoundByCaseId(any())).thenReturn(Optional.empty());
        when(eventRepo.findByCommandKey(anyString())).thenReturn(Optional.empty());

        var result = svc.reconcileOverdueReturnPickups();
        assertThat(result.totalScanned()).isEqualTo(2);
        assertThat(result.closedBuyerFaultCaseIds()).containsExactly(701L);
        assertThat(result.escalatedCarrierFaultCaseIds()).containsExactly(702L);
        verify(c1).setState(CaseEntity.STATE_CLOSED);
        verify(c2, never()).setState(CaseEntity.STATE_CLOSED);

        // Scheduler feature toggle default=false skips execution
        ReturnCaseService mockSvc = mock(ReturnCaseService.class);
        ReturnReconciliationScheduler disabledScheduler = new ReturnReconciliationScheduler(
                mockSvc, mock(OrderManagementService.class), false, false);
        disabledScheduler.reconcileOverdueReturnPickups();
        verify(mockSvc, never()).reconcileOverdueReturnPickups();
    }
}
