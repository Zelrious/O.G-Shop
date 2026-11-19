# eKYC CCCD OCR & MRZ Test Matrix

## Purpose and status

This document defines a repeatable, privacy-conscious test plan for CCCD front/back OCR confirmation, with emphasis on the back-side MRZ. It is a test plan and result template, not evidence that the listed image cases have been executed.

- Dataset directory supplied for this test campaign: `D:\Khanh\Old but Gold\Source Code\TesteKYC`
- Status: `PLANNED — image pairs and expected outcomes require confirmation`
- OCR execution against the supplied images: **not performed**.
- No test may treat folder names or file names alone as ground truth.

## Observed dataset cautions

The dataset contains 25 image files across folders labelled valid pairs, mismatched back, reversed sides, non-CCCD substitution, unclear front and unclear back. Visual review and filenames show conflicting clues:

- At least some files' labels (`front`/`back`) do not match the visible side.
- Some groups appear to reuse the same back image alongside different fronts.
- Some scenario labels appear inconsistent with the apparent image quality or pairing.
- Exact cardholder/image pairings are not established for every group.

Therefore, only pairings explicitly confirmed by the dataset owner may be used as positive/negative truth cases. Unconfirmed cases must be marked `NEEDS_PAIR_CONFIRMATION`, not PASS or FAIL.

## Normative behavior under test

A card pair may be reported as OCR-confirmed only when the system has actually read and validated the required data from both sides and all required cross-checks pass:

1. The back MRZ line 1 yields the 12-digit document number at the correct position, not the preceding auxiliary digits.
2. MRZ birth date is parsed from `YYMMDD` and compared with the front-side date.
3. MRZ sex marker `M`/`F` agrees with the front-side gender and applicable document-number encoding rules.
4. MRZ line 3 name is normalized (including `<` separators and Vietnamese diacritics) and compared to the front-side name using the approved exact-match policy.
5. Missing, unreadable, malformed, or contradictory MRZ data fails closed. Front-side values must never be synthesized into or substituted for unreadable back-side fields.

The relevant expected parser behavior is also covered by unit tests. These cases do not by themselves prove that OCR read the image correctly.

## Test rules

### Data protection

- The supplied images contain identity-document PII. Keep the source directory private and do not copy images to reports, screenshots, logs, issue trackers, artifacts, or external services.
- Do not paste OCR text, names, ID numbers, dates of birth, addresses, face details, or raw model prompts/responses into this report.
- Record match outcomes as booleans (`MATCH`/`MISMATCH`/`NOT_READ`) and redact any necessary diagnostic sample to non-identifying metadata.
- Do not send images to Gemini or another external provider unless the project owner separately authorizes that exact processing. Prefer local/synthetic test paths.
- Use a mock/sandbox backend and synthetic user/profile data. Do not submit, approve, or reject a real KYC profile.

### Input integrity and isolation

- Inspect the runner and processing path before execution. Confirm the code does not overwrite, rotate, resize, rename, or otherwise mutate source images. If it does, run only on disposable copies in a separate temporary test directory with owner approval; never point a mutating processor at the source corpus.
- Do not import or copy these files into the repository without explicit approval. Refer to them by relative path in the private local result record.
- Record a SHA-256 digest for each source file before and after a run if permitted by local policy; report only whether the digest stayed unchanged, not the digest itself.
- Run each case in a clean request/session. Record whether Gemini, offline OCR, or another provider path ran. Do not silently mix results from different paths.
- Freeze the tested code revision and relevant local configuration (excluding secret values). Redact tokens and environment secrets.

### Verdict rules

- `PASS`: actual system result matches the approved expected result and all applicable assertions pass.
- `FAIL`: actual result violates an approved expected result.
- `BLOCKED`: the case cannot safely or reliably run (e.g. no confirmed pairing, external API disabled, infrastructure unavailable).
- `NEEDS_PAIR_CONFIRMATION`: the expected pair/relationship is ambiguous.
- A quality rejection can be PASS when the approved expected result is to reject/request recapture.
- “A number was OCR'd” is not a pass if required birth-date, gender, name, or authenticity checks were omitted.
- Do not infer successful OCR from parser unit tests, file names, previous status notes, or hand-copied MRZ text.

## Image inventory and pairing authority

Store image IDs and relative paths only. Do not embed or link the image binaries here.

| Image ID | Relative path from dataset root | Visual class/condition | Pairing authority | Notes |
|---|---|---|---|---|
| IMG-001 | `Hợp lệ\Test1\Front.jpg` | Front image, visually appears clear | Owner to confirm | Candidate valid control |
| IMG-002 | `Hợp lệ\Test1\Back.jpg` | Back image, close crop; MRZ appears readable | Owner to confirm | Candidate valid control with IMG-001 |
| IMG-003 | `Hợp lệ\Test2\front.jpg` | Front image, visually appears clear; apparently a distinct card | Owner to confirm | Candidate second control; do not assume its sibling back matches |
| IMG-004 | `Hợp lệ\Test2\back.jpg` | Back image, MRZ appears readable | Owner to confirm | Appears reused/duplicated across groups; confirm before pairing |
| IMG-005 | `Mặt sau chụp không rõ\Front.jpg` | Front image, photographed at distance | Unconfirmed | Folder label describes back quality, but this file is visually a front |
| IMG-006 | `Mặt sau chụp không rõ\Back (không khớp thông tin).jpg` | Back image, card small in frame; MRZ difficult to read at displayed scale | Unconfirmed | The filename indicates mismatch while folder indicates poor quality; intended expected result unclear |
| IMG-007 | `Mặt trước chụp không rõ\Front.jpg` | Back image, appears close and readable | Unconfirmed | Folder and filename describe a front-quality case but image appears to be back |
| IMG-008 | `Mặt trước chụp không rõ\Mặt trước chụp không rõ.jpg` | Front image, photographed at distance/soft focus | Unconfirmed | Candidate unclear-front case |
| IMG-009 | `Mặt trước chụp không rõ\Mặt trước bị che mất thông tin.jpg` | Front image with information visibly obscured | Unconfirmed | Candidate occlusion case |
| IMG-010 | `Mặt trước chụp không rõ\Mặt trước bị che mất thông tin 2.jpg` | Front image with lower information obscured; relatively small/soft | Unconfirmed | Candidate occlusion case |
| IMG-011 | `Ảnh mặt sau không khớp thông tin\Test1\front.jpg` | Front image, appears close/readable | Owner to confirm | Same-folder back likely intended, but confirm card pairing |
| IMG-012 | `Ảnh mặt sau không khớp thông tin\Test1\back.jpg` | Back image, appears readable | Owner to confirm | Candidate mismatch; folder name alone is not proof |
| IMG-013 | `Ảnh mặt sau không khớp thông tin\Test2\front.jpg` | Front image, appears close/readable | Owner to confirm | Candidate mismatch; verify against back |
| IMG-014 | `Ảnh mặt sau không khớp thông tin\Test2\back.jpg` | Back image, appears readable | Owner to confirm | Back may be reused from another group; visual pairing may conflict with folder label |
| IMG-015 | `Ảnh mặt sau không khớp thông tin\Test3\front.jfif` | Front image | Owner to confirm | Pair candidate for tilted back |
| IMG-016 | `Ảnh mặt sau không khớp thông tin\Test3\back (ảnh bị nghiêng).jfif` | Back image labelled tilted | Owner to confirm | Test perspective/orientation separately from identity mismatch |
| IMG-017 | `ảnh mặt trước, mặt sau bị đảo lộn\Test1\front.jpg` | Visually appears to be a back | Unconfirmed | Filename/visual side conflict; sibling also appears to be a back |
| IMG-018 | `ảnh mặt trước, mặt sau bị đảo lộn\Test1\back.jpg` | Visually appears to be a back | Unconfirmed | Not a confirmed front/back pair |
| IMG-019 | `ảnh mặt trước, mặt sau bị đảo lộn\Test2\Front.jpg` | Visually appears to be a back | Owner to confirm | Candidate swapped-side set; verify contents, not filename |
| IMG-020 | `ảnh mặt trước, mặt sau bị đảo lộn\Test2\Back.jpg` | Visually appears to be a front | Owner to confirm | Candidate swapped-side set; verify contents, not filename |
| IMG-021 | `ảnh mặt trước, mặt sau bị đảo lộn\Test3\Front.jfif` | Front/back classification requires confirmation | Unconfirmed | Candidate swapped-side set |
| IMG-022 | `ảnh mặt trước, mặt sau bị đảo lộn\Test3\Back.jfif` | Front/back classification requires confirmation | Unconfirmed | Candidate swapped-side set |
| IMG-023 | `1 trong 2 ảnh không phải là ảnh của cccd\Test1\front (ảnh sai).jpg` | Portrait/non-document image | Owner to confirm | Candidate non-CCCD substitution |
| IMG-024 | `1 trong 2 ảnh không phải là ảnh của cccd\Test1\back.jpg` | Back/document image | Owner to confirm | Candidate sibling for IMG-023; exact intended pair unconfirmed |
| IMG-025 | `1 trong 2 ảnh không phải là ảnh của cccd\Test2\front (ảnh sai).jpg` | Appears to be a non-CCCD card | Owner to confirm | Candidate non-CCCD substitution |

> Inventory describes visible image properties only. It is not a recognition result, a verified identity pairing, or ground truth. Confirm all case pairings and expected outcomes with the dataset owner before scoring.

## Scenario matrix

| Case ID | Inputs | Scenario | Expected result (pending owner confirmation where noted) | Key assertions | Status |
|---|---|---|---|---|---|
| OCR-01 | IMG-001 + IMG-002 | Candidate valid clear pair | OCR confirmation succeeds only if all four cross-checks pass | Correct 12-digit ID extraction; DOB, gender, normalized full name all match; no front-to-back field substitution | NEEDS_PAIR_CONFIRMATION |
| OCR-02 | IMG-003 + IMG-004 | Candidate second valid pair | Success only if owner confirms these are a matching pair; otherwise do not score | Same assertions as OCR-01 | NEEDS_PAIR_CONFIRMATION |
| OCR-03 | IMG-001 + IMG-004 | Deliberate cross-pair between distinct candidate fronts/back | Reject on mismatch; no synthetic completion | Any mismatch rejects; required back fields remain sourced from back OCR | NEEDS_PAIR_CONFIRMATION |
| OCR-04 | IMG-011 + IMG-012 | Candidate mismatched back, Test1 | Reject only if owner confirms mismatch; otherwise BLOCKED | Correctly report mismatched field(s); never rewrite back values with front values | NEEDS_PAIR_CONFIRMATION |
| OCR-05 | IMG-013 + IMG-014 | Candidate mismatched back, Test2 | Same as OCR-04; pairing conflicts need resolution | Same as OCR-04 | NEEDS_PAIR_CONFIRMATION |
| OCR-06 | IMG-015 + IMG-016 | Tilted back | Either successful complete read and exact cross-check, or actionable quality/recapture rejection; never partial success | Correct orientation handling; no false identity mismatch from unreadable data | NEEDS_PAIR_CONFIRMATION |
| OCR-07 | IMG-019 + IMG-020 | Front/back inputs swapped relative to filenames | Reject wrong-side upload or correctly identify sides before continuing | Classification uses image content; never trust filename as side | NEEDS_PAIR_CONFIRMATION |
| OCR-08 | IMG-017 + IMG-018 | Two files labelled as swapped Test1 | No expected outcome until correct sides/pair confirmed | Do not score based on folder label | NEEDS_PAIR_CONFIRMATION |
| OCR-09 | IMG-023 + IMG-024 | Portrait substituted for one side | Reject the non-document image with a clear error | No OCR confirmation; valid side must not be treated as a substitute for missing side | NEEDS_PAIR_CONFIRMATION |
| OCR-10 | IMG-025 + a confirmed CCCD opposite side | Non-CCCD card substituted | Reject the non-CCCD card | Side classification fails closed; no front/back merge to success | NEEDS_PAIR_CONFIRMATION |
| OCR-11 | IMG-008 + confirmed matching back | Blurred/distant front | Recapture/quality error or fail-closed missing fields | No success if mandatory front fields are missing or uncertain | NEEDS_PAIR_CONFIRMATION |
| OCR-12 | IMG-009 + confirmed matching back | Front information obscured | Recapture/fail-closed if mandatory fields are obscured | No inferred front data from back may be represented as front OCR | NEEDS_PAIR_CONFIRMATION |
| OCR-13 | IMG-010 + confirmed matching back | Second partial-occlusion sample | Same as OCR-12 | Same as OCR-12 | NEEDS_PAIR_CONFIRMATION |
| OCR-14 | IMG-005 + IMG-006 | Distant back/poor readability candidate | Actionable recapture/unreadable error; no confirmation | Required MRZ lines and checks must be present; front cannot fill gaps | NEEDS_PAIR_CONFIRMATION |
| OCR-15 | IMG-007 + confirmed matching front | Folder says front unclear but visible image appears to be back | Do not score until correct role and pairing confirmed | Test input identity based on visual side, not path name | NEEDS_PAIR_CONFIRMATION |
| OCR-16 | Confirmed valid pair, then replace one side with another confirmed card | Changed-side regression | Reject after replacement; stale success/error state must not survive a new upload | New image causes fresh processing; no prior result reuse | NEEDS_PAIR_CONFIRMATION |

## Per-run result record

Create one record per case and per execution path. Use one row per image input where image-specific observations differ. Do not enter raw OCR PII.

| Field | Value |
|---|---|
| Case ID / run ID | |
| Date/time (local) | |
| Tested commit/revision | |
| Environment (local/sandbox) | |
| OCR path (Gemini/offline/mock/other) | |
| Provider authorization checked | |
| Image IDs and relative paths | |
| Per-image observed side and quality before test | |
| Source image digest unchanged before/after (yes/no/not checked) | |
| Expected outcome and approval source | |
| Front OCR required-field availability (present/missing only) | |
| MRZ line 1/2/3 status (read/unreadable/malformed; no raw text) | |
| ID cross-check (match/mismatch/not read/not applicable) | |
| DOB cross-check (match/mismatch/not read/not applicable) | |
| Gender cross-check (match/mismatch/not read/not applicable) | |
| Name cross-check (match/mismatch/not read/not applicable) | |
| Any front-to-back substitution detected (yes/no/not checked) | |
| System final state (success/reject/recapture/error) | |
| Redacted error category/code | |
| Verdict (PASS/FAIL/BLOCKED/NEEDS_PAIR_CONFIRMATION) | |
| Evidence reference (sanitized test log ID only) | |
| Notes / follow-up | |

## Execution checklist

- [ ] Owner confirms intended pairing and expected result for every scored scenario.
- [ ] Confirm test account and service are sandbox/mock; no real profile decision will be made.
- [ ] Confirm whether provider calls are local or external; obtain explicit approval before transmitting any supplied image externally.
- [ ] Inspect relevant code for source-image mutation; preserve originals and verify unchanged after tests.
- [ ] Record code revision and configuration with secrets redacted.
- [ ] Run each case independently and record the selected OCR path.
- [ ] Check all four cross-check fields; do not accept a partial check as success.
- [ ] Review logs/output for PII before saving the result record.
- [ ] Report failed, blocked, unrun, and passed cases separately.

## Related references

- `services/ekyc-service/README.md`
- `services/ekyc-service/cccd_module/back_card_processor.py`
- `services/ekyc-service/cccd_module/mrz_parser.py`
- `services/ekyc-service/tests/test_back_card_processor.py`
- `services/ekyc-service/tests/test_mrz_parser.py`
- `docs/progress/active/TASK-0108-ekyc-mrz-cross-check-hardening.md`
- `docs/progress/STATUS.md`
- `.agents/rules/approval-and-reporting.md`
- `.agents/rules/testing.md`
