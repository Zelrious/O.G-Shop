-- V41__create_checkout_idempotency_table.sql
-- Dedicated Checkout Idempotency Table per Decision D-08-IDEMP-01
-- Guarantees atomic command deduplication and multi-order replay independent of checkout_groups.

CREATE TABLE public.checkout_idempotency_entries (
    buyer_id            BIGINT NOT NULL,
    command_key         VARCHAR(150) NOT NULL,
    request_hash        CHAR(64) NOT NULL CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    checkout_group_id   UUID NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_checkout_idempotency PRIMARY KEY (buyer_id, command_key)
);

CREATE INDEX ix_checkout_idempotency_group ON public.checkout_idempotency_entries (buyer_id, checkout_group_id);

