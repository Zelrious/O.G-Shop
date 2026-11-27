package com.oldbutgold.shop.modules.trustsafety.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class Uc48ReminderSchedulerConditionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(Uc48ReminderService.class, () -> mock(Uc48ReminderService.class))
            .withConfiguration(AutoConfigurations.of(Uc48ReminderScheduler.class));

    @Test
    @DisplayName("Uc48ReminderScheduler is disabled by default and enabled only when app.scheduling.uc48-reminders.enabled=true")
    void schedulerBeanRegisteredOnlyWhenEnabled() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(Uc48ReminderScheduler.class));

        runner.withPropertyValues("app.scheduling.uc48-reminders.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(Uc48ReminderScheduler.class));

        runner.withPropertyValues("app.scheduling.uc48-reminders.enabled=true")
                .run(ctx -> assertThat(ctx).hasSingleBean(Uc48ReminderScheduler.class));
    }
}

