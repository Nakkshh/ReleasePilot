# Rollback Runbook

## Decide quickly
If a release causes serious harm, decide between two options: fix forward with a new patch release, or roll back to the previous release. Roll back when the cause is unknown or the fix will take long. Fix forward when the cause is clear and the fix is small.

## Rolling back
Deploy the previous release's tag. Do not move or delete the bad tag. Then publish a new PATCH version that reverts the faulty change, so the default branch and the latest release agree again.

## Marking the bad release
Edit the bad GitHub release's notes to say it is withdrawn and name the version to use instead. If needed, mark it as a pre-release so it no longer shows as the latest. Deleting a release does not delete its tag, and that tag can never be reused for a new draft.

## Database changes
Check whether the release included a database migration. Migrations only move forward: never edit an applied migration. Roll the schema forward with a new migration that undoes the change.

## After the incident
Write a short post-mortem: what happened, how it was detected, how long it lasted, and which check would have caught it. If that check can be automated, add it to the release policy.