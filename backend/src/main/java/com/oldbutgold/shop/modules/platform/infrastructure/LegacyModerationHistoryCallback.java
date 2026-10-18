package com.oldbutgold.shop.modules.platform.infrastructure;

import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.springframework.stereotype.Component;

import java.sql.SQLException;

/** Preserve V10 history and finish deferred row checks before V26 creates its index. */
@Component
public class LegacyModerationHistoryCallback implements Callback {
    @Override public boolean supports(Event event, Context context) {
        return event == Event.BEFORE_EACH_MIGRATE && context.getMigrationInfo() != null
                && context.getMigrationInfo().getVersion() != null
                && java.util.Set.of("10", "26").contains(context.getMigrationInfo().getVersion().getVersion());
    }
    @Override public boolean canHandleInTransaction(Event event, Context context) { return true; }
    @Override public String getCallbackName() { return "preserve-legacy-moderation-history"; }
    @Override public void handle(Event event, Context context) {
        try (var statement = context.getConnection().createStatement()) {
            if ("26".equals(context.getMigrationInfo().getVersion().getVersion())) {
                // V26 updates populated Google identities before CREATE INDEX.
                // Immediate checks prevent PostgreSQL's pending-trigger-events
                // DDL error; transaction commit restores normal deferred mode.
                statement.execute("SET CONSTRAINTS ALL IMMEDIATE");
                return;
            }
            statement.execute("""
                CREATE TABLE IF NOT EXISTS product_moderation_legacy_history (
                    decision_id BIGINT PRIMARY KEY REFERENCES product_moderation_decisions(decision_id),
                    original_record JSONB NOT NULL,
                    preserved_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                )
                """);
            statement.executeUpdate("""
                INSERT INTO product_moderation_legacy_history(decision_id, original_record)
                SELECT decision_id, to_jsonb(d) FROM product_moderation_decisions d
                WHERE decision = 'APPROVED' AND reason IS NOT NULL
                ON CONFLICT (decision_id) DO NOTHING
                """);
        } catch (SQLException error) {
            throw new IllegalStateException("Cannot prepare migration V" + context.getMigrationInfo().getVersion(), error);
        }
    }
}
