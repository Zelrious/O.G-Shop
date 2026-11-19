package com.oldbutgold.shop.modules.trustsafety.application;

import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderEntity;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.OrderRepository;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseEntity;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseRepository;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseRoundEntity;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseRoundRepository;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Standardized locking gateway for Return Case workflows.
 * Enforces canonical lock ordering: Order -> Case -> CaseRound -> Dependent Resources
 * to eliminate deadlocks (40P01) and race conditions across concurrent writers.
 */
@Component
public class ReturnWorkflowLockGateway {

    private static final Logger log = LoggerFactory.getLogger(ReturnWorkflowLockGateway.class);

    private final OrderRepository orderRepository;
    private final CaseRepository caseRepository;
    private final CaseRoundRepository caseRoundRepository;
    private final EntityManager entityManager;

    public record ReturnWorkflowLockedEntities(
            OrderEntity order,
            CaseEntity returnCase,
            CaseRoundEntity activeRound
    ) {
    }

    @Autowired
    public ReturnWorkflowLockGateway(
            OrderRepository orderRepository,
            CaseRepository caseRepository,
            CaseRoundRepository caseRoundRepository,
            EntityManager entityManager
    ) {
        this.orderRepository = orderRepository;
        this.caseRepository = caseRepository;
        this.caseRoundRepository = caseRoundRepository;
        this.entityManager = entityManager;
    }

    /**
     * Locks an order exclusively.
     */
    public OrderEntity lockOrder(Long orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId không được để trống");
        }
        return orderRepository.findByIdForUpdate(orderId)
                .or(() -> orderRepository.findById(orderId))
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đơn hàng #" + orderId));
    }

    /**
     * Locks Order then Case in strict canonical sequence: Order -> Case.
     */
    public ReturnWorkflowLockedEntities lockForCase(Long caseId) {
        if (caseId == null) {
            throw new IllegalArgumentException("caseId không được để trống");
        }

        // 1. Non-locking lookup to determine orderId
        CaseEntity initialCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy hồ sơ #" + caseId));
        Long orderId = initialCase.getOrderId();

        // 2. Lock Order first
        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .or(() -> orderRepository.findById(orderId))
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đơn hàng #" + orderId));

        // 3. Lock Case second
        CaseEntity lockedCase = caseRepository.findByIdForUpdate(caseId)
                .or(() -> caseRepository.findById(caseId))
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy hồ sơ #" + caseId));

        // 4. Invariant check
        if (!orderId.equals(lockedCase.getOrderId())) {
            throw new IllegalStateException("Xung đột dữ liệu: hồ sơ #" + caseId + " đã bị đổi sang đơn hàng khác");
        }

        return new ReturnWorkflowLockedEntities(order, lockedCase, null);
    }

    /**
     * Locks a specific round under an already locked case.
     */
    public CaseRoundEntity lockRound(Long caseId, int roundNo) {
        CaseRoundEntity round = caseRoundRepository.findByCaseIdAndRoundNoForUpdate(caseId, roundNo)
                .or(() -> caseRoundRepository.findByCaseIdAndRoundNo(caseId, roundNo))
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy Vòng " + roundNo + " cho hồ sơ #" + caseId));

        if (!caseId.equals(round.getCaseId())) {
            throw new IllegalStateException("Xung đột dữ liệu: Vòng " + round.getId() + " không thuộc hồ sơ #" + caseId);
        }
        return round;
    }

    /**
     * Locks Order -> Case -> CaseRound in strict canonical sequence.
     */
    public ReturnWorkflowLockedEntities lockForRound(Long caseId, int roundNo) {
        // 1 & 2. Lock Order -> Case
        ReturnWorkflowLockedEntities caseLock = lockForCase(caseId);

        // 3. Lock CaseRound
        CaseRoundEntity round = caseRoundRepository.findByCaseIdAndRoundNoForUpdate(caseId, roundNo)
                .or(() -> caseRoundRepository.findByCaseIdAndRoundNo(caseId, roundNo))
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy Vòng " + roundNo + " cho hồ sơ #" + caseId));

        // 4. Invariant check
        if (!caseId.equals(round.getCaseId())) {
            throw new IllegalStateException("Xung đột dữ liệu: Vòng " + round.getId() + " không thuộc hồ sơ #" + caseId);
        }

        return new ReturnWorkflowLockedEntities(caseLock.order(), caseLock.returnCase(), round);
    }

    /**
     * Locks Order -> Case -> Latest CaseRound in strict canonical sequence.
     */
    public ReturnWorkflowLockedEntities lockForLatestRound(Long caseId) {
        // 1 & 2. Lock Order -> Case
        ReturnWorkflowLockedEntities caseLock = lockForCase(caseId);

        // 3. Lock latest CaseRound if present
        Optional<CaseRoundEntity> roundOpt = caseRoundRepository.findLatestRoundByCaseIdForUpdate(caseId)
                .or(() -> caseRoundRepository.findLatestRoundByCaseId(caseId));

        CaseRoundEntity round = roundOpt.orElse(null);
        if (round != null && !caseId.equals(round.getCaseId())) {
            throw new IllegalStateException("Xung đột dữ liệu: Vòng " + round.getId() + " không thuộc hồ sơ #" + caseId);
        }

        return new ReturnWorkflowLockedEntities(caseLock.order(), caseLock.returnCase(), round);
    }
}
