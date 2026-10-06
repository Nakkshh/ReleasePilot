# Release Policy

## Purpose
Every release of this project is created by a person who has read an evidence-based readiness report. ReleasePilot gathers the evidence and drafts the notes, but a human always makes the final decision.

## Readiness rules
A release is READY only when the latest CI run on the default branch succeeded and no open issue is labelled bug. A release is NOT_READY when the latest CI run failed, or when at least one open issue carries the bug label. If the evidence is missing or contradictory, the verdict is UNKNOWN.

## What does not block a release
Open pull requests never block a release. Their review status is reported for information only: approved, changes requested, or no approval. Open issues without the bug label, documentation-only changes and chores do not block a release either.

## Human approval and overrides
A draft that is READY can be approved as it is. A draft that is NOT_READY or UNKNOWN can only be approved with an explicit override and a written reason of at least 10 characters. The reason is stored in the audit trail. Release notes that mention pull requests which are not part of the evidence can never be approved; create a new draft instead.

## Draft expiry
A draft expires 24 hours after it is created, because CI results and issues change. An expired draft cannot be approved; create a new one.

## Guidance that never changes the verdict
Prefer releasing when someone is available to watch the result. Prefer small releases over large ones. These are recommendations only and never turn a READY verdict into NOT_READY.