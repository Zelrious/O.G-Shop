-- Migration V39: Chat moderation full stream enhancements & manual flagging (UC56 / UC74)

ALTER TABLE public.chat_moderation_reports
  ADD COLUMN IF NOT EXISTS flagged_message_id BIGINT NULL REFERENCES public.messages(message_id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_chat_moderation_reports_flagged_msg
  ON public.chat_moderation_reports(flagged_message_id);

CREATE INDEX IF NOT EXISTS idx_messages_conversation_id_msg_id
  ON public.messages(conversation_id, message_id);

