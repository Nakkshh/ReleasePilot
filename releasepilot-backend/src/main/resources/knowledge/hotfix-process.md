# Hotfix Process

## When a hotfix is justified
A hotfix is a release made outside the normal cycle to fix a bug that is hurting users now, such as data loss, a security problem or a broken core feature. Cosmetic problems and small annoyances wait for the next normal release.

## Steps
Open a pull request with the fix and merge it into the default branch after CI passes. Then create a draft for a PATCH version, for example v1.4.1 after v1.4.0. ReleasePilot checks readiness and drafts the notes exactly as for any other release.

## Readiness still applies
A hotfix goes through the same readiness check. If the verdict is NOT_READY because of an unrelated open bug issue or an unrelated flaky CI failure, a person may approve with an override and a written reason that names the unrelated cause. The override is recorded.

## Never skip the draft
Do not publish a hotfix release by hand. The draft records who approved it, why, and what the evidence was at the time.

## After the hotfix
Close the bug issue that triggered the hotfix and note the fixed version in it. If the hotfix reverted a change, open an issue to bring that change back properly.