# ADR-0002: Canonical Cross-Agent Governance

- Status: Accepted
- Date: 2026-09-17

## Decision

Use `AGENTS.md` and `.agents/` as canonical project instructions. Claude and Gemini files are thin adapters.

All material changes require a pre-change report and explicit project-owner approval. Completion requires a post-change report with evidence.

## Consequences

- Lower risk of conflicting instructions and unapproved scope expansion.
- More deliberate workflow with an approval wait before implementation.
- Adapter validity must be checked when canonical skill names change.
