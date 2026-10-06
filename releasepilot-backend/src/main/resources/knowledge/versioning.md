# Versioning and Tags

## Tag format
Tags look like v1.2.3 (MAJOR.MINOR.PATCH) with an optional pre-release suffix such as v1.2.3-rc.1 or v2.0.0-beta.2. The leading v is required. Tags without it, such as 1.2.3, or with only two numbers, such as v1.2, are rejected when a draft is created.

## When to bump what
Bump MAJOR for a breaking change: an API that existing callers must change to keep working, a removed endpoint, or a database change that cannot be rolled back. Bump MINOR for new, backwards-compatible features. Bump PATCH for bug fixes and small chores that change no behavior.

## Pre-releases
Use a suffix for release candidates and betas: -rc.1, -rc.2, -beta.1. Set the prerelease flag when creating the draft. A pre-release does not replace the latest stable release.

## Tags are never reused
A tag, once created, is permanent. Deleting a GitHub release does not delete its tag, and a draft for an existing tag is refused. If a release was wrong, publish a new, higher version instead of moving or reusing the tag.

## First release
The first stable release of a project is usually v1.0.0, once the core features are settled. Before that, use v0.x.y versions, where a minor bump may include breaking changes.