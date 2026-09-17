---
name: secure-change-review
description: Review an O.G Shop change for authentication, authorization, input handling, secrets, privacy, abuse, payment/webhook safety, and trust-boundary failures. Use for sensitive flows or pre-merge security review.
---

# Secure change review

1. Read `../../rules/security.md` and follow `../../workflows/security-review.md`.
2. Trace actor-controlled input through trust boundaries to data and side effects.
3. Verify server-side authorization, least privilege, validation, safe logging and secret handling.
4. For transaction callbacks, verify authenticity, replay protection, idempotency and legal state transitions.
5. Report evidence-linked findings by impact and include negative-path verification.
