-- Migration V37: Chat text moderation & anti-disintermediation audit logging
-- 1. Add moderation violation tracking columns to users
ALTER TABLE public.users
  ADD COLUMN IF NOT EXISTS chat_violation_score INT NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS chat_restricted_until TIMESTAMPTZ NULL;

-- 2. Create chat moderation logs table
CREATE TABLE IF NOT EXISTS public.chat_moderation_logs (
  log_id BIGSERIAL PRIMARY KEY,
  conversation_id BIGINT NOT NULL REFERENCES public.conversations(conversation_id) ON DELETE CASCADE,
  sender_id BIGINT NOT NULL REFERENCES public.users(user_id) ON DELETE CASCADE,
  original_content TEXT NOT NULL,
  masked_content TEXT NOT NULL,
  violation_type VARCHAR(64) NOT NULL,
  triggered_rule VARCHAR(128) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

-- 3. Create indexes for quick lookup
CREATE INDEX IF NOT EXISTS idx_chat_moderation_logs_sender ON public.chat_moderation_logs(sender_id);
CREATE INDEX IF NOT EXISTS idx_chat_moderation_logs_conversation ON public.chat_moderation_logs(conversation_id);
