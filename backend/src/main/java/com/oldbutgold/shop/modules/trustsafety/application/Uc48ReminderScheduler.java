package com.oldbutgold.shop.modules.trustsafety.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled trigger for {@link Uc48ReminderService}.
 * Gated by {@code app.scheduling.uc48-reminders.enabled=true} (default: false).
 * Environments requiring automatic deadline reminders (Staging / Production) must set UC48_REMINDERS_ENABLED=true.
 */
@Component
@ConditionalOnProperty(
        name = "app.scheduling.uc48-reminders.enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class Uc48ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(Uc48ReminderScheduler.class);

    private final Uc48ReminderService uc48ReminderService;

    public Uc48ReminderScheduler(Uc48ReminderService uc48ReminderService) {
        this.uc48ReminderService = uc48ReminderService;
    }

    @Scheduled(fixedDelayString = "${app.scheduling.uc48-reminders.fixed-delay-ms:60000}")
    public void scheduledRun() {
        try {
            Uc48ReminderService.Uc48RunSummary summary = uc48ReminderService.reconcileAllReminders();
            if (summary.newReturnRemindersSent() > 0 || summary.stageRemindersSent() > 0) {
                log.info("UC48 reminder run finished: newReturnSent={}, stageSent={}, skipped={}",
                        summary.newReturnRemindersSent(), summary.stageRemindersSent(), summary.skipped());
            }
        } catch (Exception ex) {
            log.warn("UC48 reminder scheduled run failed: {}", ex.getMessage());
        }
    }
}

