package com.oldbutgold.shop.modules.identity.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oldbutgold.shop.modules.identity.application.RegistrationDraft;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class RegistrationRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final RowMapper<Challenge> mapper;
    private static final String SELECT = """
            SELECT challenge_id,subject_key,code_digest,state,expires_at,next_send_at,
                   attempt_count,max_attempts,registration_data::text,registration_expires_at
            FROM public.auth_challenges
            WHERE record_type='auth_challenges' AND purpose='REGISTER_EMAIL'
            """;

    public RegistrationRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
        mapper = (rs, row) -> new Challenge(rs.getObject("challenge_id", UUID.class), rs.getString("subject_key"),
                rs.getString("code_digest"), rs.getString("state"), rs.getTimestamp("expires_at").toInstant(),
                rs.getTimestamp("next_send_at").toInstant(), rs.getInt("attempt_count"), rs.getInt("max_attempts"),
                decode(rs.getString("registration_data")), rs.getTimestamp("registration_expires_at") == null
                ? null : rs.getTimestamp("registration_expires_at").toInstant());
    }

    public void lockEmail(String email) { lock("email:" + email); }
    public void lockRequester(String digest) { lock("requester:" + digest); }
    private void lock(String key) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?,8201))", rs -> {}, key);
    }

    public boolean emailExists(String email) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM public.users WHERE record_type='users' AND email=?)", Boolean.class, email));
    }

    public Optional<String> emailFor(UUID id) {
        return jdbc.query(SELECT + " AND challenge_id=?", mapper, id).stream().findFirst().map(Challenge::email);
    }

    public Optional<Challenge> findLocked(UUID id) {
        return jdbc.query(SELECT + " AND challenge_id=? FOR UPDATE", mapper, id).stream().findFirst();
    }

    public Optional<Challenge> openForEmail(String email) {
        return jdbc.query(SELECT + " AND subject_key=? AND state='OPEN' ORDER BY issued_at DESC LIMIT 1",
                mapper, email).stream().findFirst();
    }

    public long emailRetryAfter(String email, Instant now, int limit) {
        return retryAfter("subject_key", email, now, limit);
    }
    public long requesterRetryAfter(String digest, Instant now, int limit) {
        return retryAfter("registration_requester_digest", digest, now, limit);
    }
    private long retryAfter(String column, String value, Instant now, int limit) {
        // column is chosen only by these two methods, never by user input.
        return jdbc.queryForObject("SELECT count(*) AS sends,min(issued_at) AS oldest FROM public.auth_challenges"
                + " WHERE record_type='auth_challenges' AND purpose='REGISTER_EMAIL' AND " + column
                + "=? AND issued_at>?", (rs, row) -> rs.getLong("sends") < limit ? 0L
                : Math.max(1, java.time.Duration.between(now, rs.getTimestamp("oldest").toInstant()
                .plusSeconds(3600)).getSeconds() + 1), value, Timestamp.from(now.minusSeconds(3600)));
    }

    public void insert(UUID id, RegistrationDraft draft, String digest, String requesterDigest, Instant now,
                       Instant expiry, Instant draftExpiry, Instant resendAt, int attempts) {
        jdbc.update("""
                INSERT INTO public.auth_challenges(challenge_id,subject_key,purpose,code_digest,issued_at,
                    expires_at,next_send_at,max_attempts,command_key,registration_data,registration_requester_digest,
                    registration_expires_at)
                VALUES(?,?,'REGISTER_EMAIL',?,?,?,?,?,?,?::jsonb,?,?)
                """, id, draft.email(), digest, Timestamp.from(now), Timestamp.from(expiry), Timestamp.from(resendAt),
                attempts, "REGISTER_CHALLENGE:" + id, encode(draft), requesterDigest, Timestamp.from(draftExpiry));
    }

    public void invalidate(UUID id, Instant now) {
        jdbc.update("""
                UPDATE public.auth_challenges SET state='INVALIDATED',invalidated_at=?,registration_data=NULL
                WHERE challenge_id=? AND state='OPEN' AND record_type='auth_challenges' AND purpose='REGISTER_EMAIL'
                """, Timestamp.from(now), id);
    }

    public void recordAttempt(UUID id, boolean success, Instant now) {
        jdbc.update("""
                UPDATE public.auth_challenges SET attempt_count=attempt_count+1,
                    state=CASE WHEN ? THEN 'CONSUMED' ELSE 'OPEN' END,
                    consumed_at=CASE WHEN ? THEN ?::timestamptz ELSE NULL END,
                    registration_data=CASE WHEN ? THEN NULL ELSE registration_data END
                WHERE challenge_id=? AND state='OPEN' AND record_type='auth_challenges' AND purpose='REGISTER_EMAIL'
                """, success, success, Timestamp.from(now), success, id);
    }

    public void createSellerDraft(long userId, RegistrationDraft.Seller seller, long pickup, long returns, Instant now) {
        var bank = java.util.Map.of("bank_name", seller.bankName().strip(), "account_number", seller.accountNumber(),
                "account_holder", seller.accountHolder().strip());
        jdbc.update("""
                INSERT INTO public.seller_profiles(user_id,state,pickup_address_id,bank_snapshot,updated_at,
                    store_name,contact_phone,return_address_id)
                VALUES(?,'PENDING_EKYC',?,?::jsonb,?,?,?,?)
                """, userId, pickup, encode(bank), Timestamp.from(now), seller.storeName().strip(),
                seller.contactPhone(), returns);
    }

    public void expireDrafts() {
        jdbc.update("""
                WITH expired AS (SELECT challenge_id FROM public.auth_challenges WHERE state='OPEN'
                    AND purpose='REGISTER_EMAIL' AND registration_data IS NOT NULL AND registration_expires_at<=clock_timestamp()
                    ORDER BY expires_at LIMIT 100 FOR UPDATE SKIP LOCKED)
                UPDATE public.auth_challenges c SET state='INVALIDATED',invalidated_at=clock_timestamp(),
                    registration_data=NULL FROM expired e WHERE c.challenge_id=e.challenge_id
                """);
    }

    private String encode(Object data) {
        try { return json.writeValueAsString(data); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Cannot store registration"); }
    }
    private RegistrationDraft decode(String data) {
        if (data == null) return null;
        try { return json.readValue(data, RegistrationDraft.class); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Cannot read registration"); }
    }

    public record Challenge(UUID id, String email, String digest, String state, Instant expiresAt, Instant resendAt,
                            int attemptCount, int maxAttempts, RegistrationDraft draft, Instant draftExpiresAt) {
        @Override public String toString() { return "RegistrationChallenge[redacted]"; }
    }
}
