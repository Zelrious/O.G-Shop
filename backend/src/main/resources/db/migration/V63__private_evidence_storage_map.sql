-- V63: Private evidence storage ownership & append-only legacy mapping (og_compat.case_evidence remains append-only per V22)

CREATE TABLE IF NOT EXISTS public.evidence_uploads (
    filename     VARCHAR(128) PRIMARY KEY,
    uploader_id  BIGINT NOT NULL,
    media_type   VARCHAR(16) NOT NULL,
    content_type VARCHAR(64) NOT NULL,
    sha256       VARCHAR(64) NOT NULL,
    size_bytes   BIGINT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS ix_evidence_uploads_uploader_created
    ON public.evidence_uploads (uploader_id, created_at DESC);

CREATE TABLE IF NOT EXISTS public.evidence_storage_map (
    legacy_key        VARCHAR(255) PRIMARY KEY,
    private_filename  VARCHAR(128) NOT NULL UNIQUE,
    sha256            VARCHAR(64) NOT NULL,
    size_bytes        BIGINT NOT NULL,
    migrated_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS public.evidence_blocked_public_keys (
    legacy_key  VARCHAR(255) PRIMARY KEY,
    blocked_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

