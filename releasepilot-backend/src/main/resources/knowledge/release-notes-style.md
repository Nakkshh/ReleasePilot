# Release Notes Style Guide

## Structure
Release notes have a short title, a one or two sentence summary, and up to four lists: Breaking changes, Features, Fixes and Chores. Empty lists are omitted.

## Categories
Features are new user-visible capabilities, usually pull requests titled feat. Fixes correct wrong behavior, usually titled fix. Chores cover documentation, refactoring, dependencies, build and CI changes. Breaking changes are anything callers must adapt to, and they are listed first.

## Item format
Each item is written as the pull request number, the title and the author, for example: #12 feat: add export endpoint (@alice). Titles are kept as the author wrote them. Do not add details that are not in the pull request title or labels.

## Accuracy rules
Only mention pull requests that appear in the evidence. Never invent a pull request number, an author or a feature. Every merged pull request in the evidence appears exactly once. If a title is unclear, list it under Chores rather than guessing what it does.

## Tone
Plain, factual and short. No marketing language, no emoji and no promises about future work. The summary says what changed, not how exciting it is.