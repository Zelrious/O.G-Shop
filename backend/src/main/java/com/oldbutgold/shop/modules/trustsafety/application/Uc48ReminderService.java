package com.oldbutgold.shop.modules.trustsafety.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * UC48 Deadline Reminder Service & Scheduler:
 * - Stream A (PO-03 Option A): One reminder in the final 24h before return_due_at
 *   for UC83 return-allowed DELIVERED orders without early completion and without an open/existing return case.
 * - Stream B (BR-UC48-05): Distinct stage reminders for Buyer evidence deadline,
 *   Seller Round 2 response / evidence deadline, and KTV 48h SLA deadline on open return cases.
 */
@Service
public class Uc48ReminderService {

    private static final Logger log = LoggerFactory.getLogger(Uc48ReminderService.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate requiresNewTx;
    private Clock clock = Clock.systemUTC();

    @Autowired
    public Uc48ReminderService(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    private long onDutyKtvId;

    @org.springframework.beans.factory.annotation.Value("${app.scheduling.uc48-reminders.on-duty-ktv-id:0}")
    void setOnDutyKtvId(long userId) {
        this.onDutyKtvId = userId;
    }

    public void setClock(Clock clock) {
        if (clock != null) {
            this.clock = clock;
        }
    }

    public Uc48RunSummary reconcileAllReminders() {
        int newSent = 0;
        int stageSent = 0;
        int skipped = 0;

        for (Long orderId : scanReturnWindowReminderCandidateOrderIds()) {
            Boolean sent = processNewReturnWindowReminderForOrderInNewTx(orderId);
            if (Boolean.TRUE.equals(sent)) {
                newSent++;
            } else {
                skipped++;
            }
        }

        stageSent += reconcileOpenCaseStageReminders();
        return new Uc48RunSummary(newSent, stageSent, skipped);
    }

    public List<Long> scanReturnWindowReminderCandidateOrderIds() {
        Instant now = clock.instant();
        return jdbc.queryForList("""
                SELECT o.order_id
                FROM og_compat.orders o
                WHERE o.workflow_model = 'UC83'
                  AND o.status = 'DELIVERED'
                  AND o.return_allowed = TRUE
                  AND o.valid_delivered_at IS NOT NULL
                  AND o.early_completed_at IS NULL
                  AND o.return_due_at IS NOT NULL
                  AND o.return_due_at <= ?
                  AND ? < o.return_due_at
                  AND NOT EXISTS (
                      SELECT 1 FROM og_compat.cases c
                      WHERE c.order_id = o.order_id AND c.case_type = 'RETURN'
                  )
                  AND NOT EXISTS (
                      SELECT 1 FROM og_compat.notification_reminders nr
                      WHERE nr.user_id = o.buyer_id
                        AND nr.source_key = ('UC48:RETURN_WINDOW_24H:ORDER:' || o.order_id::text)
                  )
                ORDER BY o.return_due_at ASC, o.order_id ASC
                """, Long.class, Timestamp.from(now.plus(24, ChronoUnit.HOURS)), Timestamp.from(now));
    }

    /**
     * Executes inside an isolated REQUIRES_NEW transaction acquiring orders FOR UPDATE
     * to serialize safely with Buyer earlyCompleteOrder().
     */
    public Boolean processNewReturnWindowReminderForOrderInNewTx(Long orderId) {
        if (orderId == null) {
            return false;
        }
        return Boolean.TRUE.equals(requiresNewTx.execute(status -> {
            Instant now = clock.instant();
            List<Map<String, Object>> locked = jdbc.queryForList("""
                    SELECT order_id, buyer_id, workflow_model, status, return_allowed,
                           valid_delivered_at, return_due_at, early_completed_at
                    FROM og_compat.orders
                    WHERE order_id = ?
                    FOR UPDATE
                    """, orderId);
            if (locked.isEmpty()) {
                return false;
            }
            Map<String, Object> o = locked.getFirst();
            String workflow = (String) o.get("workflow_model");
            String statusStr = (String) o.get("status");
            Boolean returnAllowed = (Boolean) o.get("return_allowed");
            Timestamp deliveredTs = (Timestamp) o.get("valid_delivered_at");
            Timestamp dueTs = (Timestamp) o.get("return_due_at");
            Timestamp earlyTs = (Timestamp) o.get("early_completed_at");
            Long buyerId = ((Number) o.get("buyer_id")).longValue();

            if (!"UC83".equals(workflow)
                    || !"DELIVERED".equals(statusStr)
                    || !Boolean.TRUE.equals(returnAllowed)
                    || deliveredTs == null
                    || dueTs == null
                    || earlyTs != null) {
                return false;
            }

            Instant returnDueAt = dueTs.toInstant();
            if (now.isBefore(returnDueAt.minus(24, ChronoUnit.HOURS)) || !now.isBefore(returnDueAt)) {
                return false;
            }

            Boolean blocked = jdbc.queryForObject("SELECT og_order_has_blocking_case(?)", Boolean.class, orderId);
            if (Boolean.TRUE.equals(blocked)) {
                return false;
            }

            Integer existingCases = jdbc.queryForObject(
                    "SELECT count(*) FROM og_compat.cases WHERE order_id = ? AND case_type = 'RETURN'",
                    Integer.class, orderId);
            if (existingCases != null && existingCases > 0) {
                return false;
            }

            String sourceKey = "UC48:RETURN_WINDOW_24H:ORDER:" + orderId;
            String snapshotJson = String.format(
                    "{\"orderId\":%d,\"validDeliveredAt\":\"%s\",\"returnDueAt\":\"%s\",\"targetPath\":\"/orders/%d\"}",
                    orderId, deliveredTs.toInstant(), returnDueAt, orderId
            );

            try {
                int inserted = jdbc.update("""
                        INSERT INTO public.notifications(
                            source_notification_reminders_reminder_id, user_id, source_key, reminder_type,
                            scheduled_at, expires_at, state, source_snapshot, record_type
                        ) VALUES (?, ?, ?, 'RETURN_WINDOW_24H', ?, ?, 'SENT', ?::jsonb, 'notification_reminders')
                        ON CONFLICT (user_id, source_key)
                        WHERE record_type = 'notification_reminders' AND source_key IS NOT NULL AND source_key LIKE 'UC48:%'
                        DO NOTHING
                        """, UUID.randomUUID(), buyerId, sourceKey, Timestamp.from(now), Timestamp.from(returnDueAt), snapshotJson);
                if (inserted == 0) {
                    return false;
                }
            } catch (DuplicateKeyException dup) {
                return false;
            }

            String targetData = String.format(
                    "{\"destination\":\"ORDER_DETAIL\",\"orderId\":%d,\"validDeliveredAt\":\"%s\",\"returnDueAt\":\"%s\"}",
                    orderId, deliveredTs.toInstant(), returnDueAt
            );
            jdbc.update("""
                    INSERT INTO og_compat.notifications(
                        user_id, type, title, content, reference_type, reference_id, is_read, created_at, target_data
                    ) VALUES (?, 'UC48_RETURN_DEADLINE_REMINDER', ?, ?, 'ORDER', ?, false, ?, ?::jsonb)
                    """,
                    buyerId,
                    String.format("Nhắc hạn yêu cầu trả hàng đơn #%d (còn 24 giờ cuối)", orderId),
                    String.format("Đơn #%d giao thành công lúc %s; hạn mở yêu cầu trả hàng đến %s.",
                            orderId, deliveredTs.toInstant(), returnDueAt),
                    orderId,
                    Timestamp.from(now),
                    targetData
            );
            return true;
        }));
    }

    public int reconcileOpenCaseStageReminders() {
        Instant now = clock.instant();
        int sent = 0;

        List<Long> shippingCases = jdbc.queryForList("""
                SELECT case_id FROM og_compat.cases c
                WHERE c.case_type='RETURN' AND c.state='RETURN_APPROVED'
                  AND ? >= c.approved_at + INTERVAL '2 days' AND ? < c.approved_at + INTERVAL '3 days'
                  AND NOT EXISTS (SELECT 1 FROM og_compat.shipments s WHERE s.return_case_id=c.case_id AND s.leg='RETURN')
                """, Long.class, Timestamp.from(now), Timestamp.from(now));
        for (Long caseId : shippingCases) {
            if (Boolean.TRUE.equals(requiresNewTx.execute(tx -> remindBuyerToShip(caseId)))) sent++;
        }

        if (onDutyKtvId <= 0) {
            Integer unassignedDue = jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM og_compat.cases c
                    JOIN og_compat.case_rounds r USING(case_id)
                    WHERE c.case_type = 'RETURN'
                      AND c.state NOT IN ('CLOSED', 'REJECTED')
                      AND c.assigned_to IS NULL
                      AND r.state = 'OPEN'
                      AND r.ktv_due_at IS NOT NULL
                      AND ? >= r.ktv_due_at - INTERVAL '6 hours'
                      AND ? < r.ktv_due_at
                      AND NOT EXISTS (SELECT 1 FROM og_compat.case_events e WHERE e.round_id = r.round_id AND e.event_type = 'ESCALATED')
                    """, Integer.class, Timestamp.from(now), Timestamp.from(now));
            if (unassignedDue != null && unassignedDue > 0) {
                log.warn("UC48 KTV SLA reminder skipped {} unassigned due case(s) because app.scheduling.uc48-reminders.on-duty-ktv-id={} (must be > 0 to notify on-duty KTV)",
                        unassignedDue, onDutyKtvId);
            }
        }

        var candidates = eligibleStages(null, now).stream().map(StageReminder::caseId).distinct().toList();
        for (long caseId : candidates) {
            Integer count = requiresNewTx.execute(tx -> sendDueCaseReminders(caseId));
            if (count != null) sent += count;
        }
        return sent;
    }

    private record StageReminder(long caseId, long orderId, long userId, String kind, String key, Instant due) {}

    private List<StageReminder> eligibleStages(Long caseId, Instant now) {
        return jdbc.query("""
                WITH open_cases AS (
                    SELECT c.*,o.buyer_id,o.seller_id FROM og_compat.cases c JOIN og_compat.orders o USING(order_id)
                    WHERE c.case_type='RETURN' AND c.state NOT IN ('CLOSED','REJECTED') AND (?::bigint IS NULL OR c.case_id=?)
                ), stages AS (
                    SELECT c.case_id,c.order_id,c.seller_id AS recipient,'SELLER_R2_DEADLINE' AS kind,
                        'UC48:SELLER_R2_DUE:CASE:' || c.case_id AS key,c.seller_response_due_at AS due,interval '12 hours' AS lead
                    FROM open_cases c JOIN og_compat.case_rounds r USING(case_id)
                    WHERE c.state='POST_RETURN' AND r.round_no=2 AND r.state='OPEN' AND r.valid_damage_request_at IS NULL
                    UNION ALL
                    SELECT c.case_id,c.order_id,k.user_id,'KTV_SLA_DEADLINE',
                        'UC48:KTV_SLA_DUE:ROUND:' || r.round_id || ':KTV:' || k.user_id,r.ktv_due_at, interval '6 hours'
                    FROM open_cases c JOIN og_compat.case_rounds r USING(case_id)
                    JOIN LATERAL (SELECT u.user_id FROM og_compat.users u WHERE u.status='ACTIVE' AND og_has_role(u.user_id,'KTV')
                        AND u.user_id=coalesce(c.assigned_to, ?::bigint)) k ON true
                    WHERE r.state='OPEN' AND NOT EXISTS(SELECT 1 FROM og_compat.case_events e WHERE e.round_id=r.round_id AND e.event_type='ESCALATED')
                    UNION ALL
                    SELECT c.case_id,c.order_id,CASE r.round_no WHEN 1 THEN c.buyer_id ELSE c.seller_id END,
                        CASE r.round_no WHEN 1 THEN 'BUYER_EVIDENCE_DUE' ELSE 'SELLER_EVIDENCE_DUE' END,
                        CASE r.round_no WHEN 1 THEN 'UC48:BUYER_EVIDENCE_DUE:REQ:' ELSE 'UC48:SELLER_EVIDENCE_DUE:REQ:' END || e.request_id,
                        e.deadline_at,interval '12 hours'
                    FROM open_cases c JOIN og_compat.case_rounds r USING(case_id)
                    JOIN og_compat.case_evidence_requests e ON e.round_id=r.round_id AND e.case_id=c.case_id
                    WHERE r.state='OPEN' AND e.status='PENDING'
                )
                SELECT * FROM stages s WHERE ? >= due-lead AND ? < due AND NOT EXISTS(
                    SELECT 1 FROM og_compat.notification_reminders n WHERE n.user_id=s.recipient AND n.source_key=s.key)
                """, (rs,i) -> new StageReminder(rs.getLong("case_id"),rs.getLong("order_id"),rs.getLong("recipient"),
                        rs.getString("kind"),rs.getString("key"),rs.getTimestamp("due").toInstant()),
                caseId,caseId,onDutyKtvId,Timestamp.from(now),Timestamp.from(now));
    }

    int sendDueCaseReminders(long caseId) {
        jdbc.queryForList("SELECT case_id FROM og_compat.cases WHERE case_id=? FOR UPDATE",Long.class,caseId);
        int sent = 0;
        for (StageReminder row : eligibleStages(caseId,clock.instant())) {
            boolean staff = row.kind().equals("KTV_SLA_DEADLINE");
            String title = switch (row.kind()) {
                case "KTV_SLA_DEADLINE" -> "Nhắc hạn xử lý hồ sơ";
                case "SELLER_R2_DEADLINE" -> "Nhắc phản hồi sau khi nhận hàng trả";
                default -> "Nhắc bổ sung bằng chứng";
            };
            String target = String.format("{\"destination\":\"%s\",\"caseId\":%d,\"orderId\":%d,\"dueAt\":\"%s\"}",
                    staff ? "KTV_DISPUTES" : "ORDER_DETAIL",caseId,row.orderId(),row.due());
            if (insertStageReminder(row.userId(),row.key(),row.kind(),row.due(),title,
                    title+" cho hồ sơ #"+caseId+" trước "+row.due(),staff ? "CASE" : "ORDER",staff ? caseId : row.orderId(),target)) sent++;
        }
        return sent;
    }

    boolean remindBuyerToShip(long caseId) {
        var locked = jdbc.queryForList("SELECT case_id FROM og_compat.cases WHERE case_id=? FOR UPDATE", Long.class, caseId);
        if (locked.isEmpty()) return false;
        var now = Timestamp.from(clock.instant());
        var rows = jdbc.queryForList("""
                SELECT o.buyer_id, c.order_id, c.approved_at + INTERVAL '3 days' AS due
                FROM og_compat.cases c JOIN og_compat.orders o USING(order_id)
                WHERE c.case_id=? AND c.case_type='RETURN' AND c.state='RETURN_APPROVED'
                  AND ? >= c.approved_at + INTERVAL '2 days' AND ? < c.approved_at + INTERVAL '3 days'
                  AND NOT EXISTS (SELECT 1 FROM og_compat.shipments s WHERE s.return_case_id=c.case_id AND s.leg='RETURN')
                """, caseId, now, now);
        if (rows.isEmpty()) return false;
        var row = rows.getFirst();
        long order = ((Number) row.get("order_id")).longValue();
        Instant due = ((Timestamp) row.get("due")).toInstant();
        return insertStageReminder(((Number) row.get("buyer_id")).longValue(), "UC48:BUYER_SHIP:CASE:" + caseId,
                "BUYER_SHIP", due, "Nhắc gửi hàng trả", "Vui lòng gửi hàng trả cho đơn #" + order + " trước " + due,
                "ORDER", order, String.format("{\"destination\":\"ORDER_DETAIL\",\"orderId\":%d}", order));
    }

    private boolean insertStageReminder(
            long userId,
            String sourceKey,
            String reminderType,
            Instant due,
            String title,
            String content,
            String refType,
            long refId,
            String targetData
    ) {
        Instant now = clock.instant();
        int inserted = jdbc.update("""
                INSERT INTO public.notifications(
                    source_notification_reminders_reminder_id, user_id, source_key, reminder_type,
                    scheduled_at, expires_at, state, source_snapshot, record_type
                ) VALUES (?, ?, ?, ?, ?, ?, 'SENT', ?::jsonb, 'notification_reminders')
                ON CONFLICT (user_id, source_key)
                WHERE record_type = 'notification_reminders' AND source_key IS NOT NULL AND source_key LIKE 'UC48:%'
                DO NOTHING
                """, UUID.randomUUID(), userId, sourceKey, reminderType, Timestamp.from(now), Timestamp.from(due), targetData);
        if (inserted == 0) {
            return false;
        }
        jdbc.update("""
                INSERT INTO og_compat.notifications(
                    user_id, type, title, content, reference_type, reference_id, is_read, created_at, target_data
                ) VALUES (?, ?, ?, ?, ?, ?, false, ?, ?::jsonb)
                """, userId, "UC48_" + reminderType, title, content, refType, refId, Timestamp.from(now), targetData);
        return true;
    }

    public record Uc48RunSummary(int newReturnRemindersSent, int stageRemindersSent, int skipped) {}
}

