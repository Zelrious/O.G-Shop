-- Migration V38: Chat moderation reports & KTV dispatching queue (UC56 / UC74)
CREATE TABLE IF NOT EXISTS public.chat_moderation_reports (
  report_id BIGSERIAL PRIMARY KEY,
  conversation_id BIGINT NOT NULL REFERENCES public.conversations(conversation_id) ON DELETE CASCADE,
  offender_id BIGINT NOT NULL REFERENCES public.users(user_id) ON DELETE CASCADE,
  reporter_id BIGINT NULL REFERENCES public.users(user_id) ON DELETE SET NULL,
  violation_type VARCHAR(64) NOT NULL,
  severity_level VARCHAR(32) NOT NULL DEFAULT 'MEDIUM',
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  flagged_content TEXT NOT NULL,
  context_snippet TEXT NOT NULL,
  decision_action VARCHAR(64) NULL,
  reputation_penalty INT NOT NULL DEFAULT 0,
  decision_note TEXT NULL,
  resolved_by BIGINT NULL REFERENCES public.users(user_id),
  resolved_at TIMESTAMPTZ NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX IF NOT EXISTS idx_chat_moderation_reports_status ON public.chat_moderation_reports(status);
CREATE INDEX IF NOT EXISTS idx_chat_moderation_reports_offender ON public.chat_moderation_reports(offender_id);

