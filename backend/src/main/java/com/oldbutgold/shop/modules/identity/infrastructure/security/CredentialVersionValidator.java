package com.oldbutgold.shop.modules.identity.infrastructure.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** Database check on every bearer request and cookie restoration, with no stale cache. */
@Component
public class CredentialVersionValidator implements OAuth2TokenValidator<Jwt> {
    private final JdbcTemplate jdbc;
    public CredentialVersionValidator(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @Override public OAuth2TokenValidatorResult validate(Jwt jwt) {
        try {
            long id=Long.parseLong(jwt.getSubject());
            Object claim=jwt.getClaims().get("cv");
            long version=claim==null ? 0 : ((Number)claim).longValue();
            Boolean valid=jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM public.users WHERE record_type='users' AND user_id=?
                  AND credential_version=? AND deleted_at IS NULL AND status='ACTIVE'
                  AND NOT EXISTS(SELECT 1 FROM og_compat.account_restrictions ar WHERE ar.user_id=public.users.user_id
                    AND ar.scope='LOGIN' AND ar.closed_at IS NULL))
                """,Boolean.class,id,version);
            if(Boolean.TRUE.equals(valid)) return OAuth2TokenValidatorResult.success();
        } catch(RuntimeException ignored) { /* Reject malformed tokens and fail closed on DB failure. */ }
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token","Session is unavailable",null));
    }
}
