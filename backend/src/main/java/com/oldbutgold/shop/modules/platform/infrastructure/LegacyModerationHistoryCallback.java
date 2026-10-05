package com.oldbutgold.shop.modules.platform.infrastructure;

import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.springframework.stereotype.Component;

import java.sql.SQLException;

/** Preserve historical notes before immutable V10 normalizes them. */
@Component
public class LegacyModerationHistoryCallback implements Callback {
    @Override public boolean supports(Event event, Context context) {
        return event == Event.BEFORE_EACH_MIGRATE && context.getMigrationInfo() != null
                && context.getMigrationInfo().getVersion() != null
                && "10".equals(context.getMigrationInfo().getVersion().getVersion());
    }
    @Override public boolean canHandleInTransaction(Event event, Context context) { return true; }
    @Override public String getCallbackName() { return "preserve-legacy-moderation-history"; }
    @Override public void handle(Event event, Context context) {
        try (var statement = context.getConnection().createStatement()) {
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
            throw new IllegalStateException("Cannot preserve legacy moderation history before V10", error);
        }
    }
}
