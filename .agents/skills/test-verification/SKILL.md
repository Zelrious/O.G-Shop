---
name: test-verification
description: Select and run the smallest meaningful O.G Shop verification set, then report exact commands and observed results. Use after changes, before completion claims, or when diagnosing a failed quality gate.
---

# Test verification

1. Read `../../rules/testing.md` and the affected module's acceptance criteria.
2. Map each material risk to a unit, integration, security, concurrency, contract or UI behavior check.
3. Run focused checks first, then the module quality gate when justified.
4. Capture command, exit result and relevant failure; never convert an unrun check into a pass.
5. Update the active task with evidence and remaining limitations.
