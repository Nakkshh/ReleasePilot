# CI Failure Triage

## First look
When the latest CI run on the default branch failed, the release is NOT_READY. Start with the failed jobs of that run and read the failing step and the end of its log before deciding anything.

## Real failure or flaky run
Re-run a failed workflow once. If it passes on re-run with no code change, record it as flaky and open an issue labelled flaky. If it fails again, treat it as a real failure and fix it before releasing.

## Common causes
A failing test points at the changed code. A failing build step often means a dependency or compile problem. A failure in a checkout or setup step usually means a runner or network problem rather than the code. A run of the demo workflow with its fail input set to true fails on purpose and is expected while the release process is being tested.

## What to report
When explaining a failure, name the workflow, the failed job and step, the conclusion, and the most relevant log lines. Do not guess a cause that the logs do not show.

## After fixing
Merge the fix, wait for a green run on the default branch, and create a new draft. The old draft's evidence is stale.