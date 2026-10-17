## Security

- **Authentication**: username/password login issues a short-lived HS256 JWT (`auth.token-ttl-minutes`, default 60). Passwords are bcrypt-hashed, there is no self-registration, and the first admin is bootstrapped from environment variables only while the user table is empty.
- **Per-request account check**: every authenticated request re-reads the user. Disabling an account, changing its role or resetting its password takes effect immediately; older tokens stop working.
- **Roles**: anonymous visitors can read drafts and the knowledge base and run the rate-limited agents. Only `APPROVER`/`ADMIN` can create, approve, reject or retry release drafts. Only `ADMIN` manages users and reindexes.
- **Public view**: anonymous visitors get a trimmed draft view without usernames, decision comments, override reasons or error text.
- **Human in the loop**: the agent only drafts. A person approves, and a non-READY verdict needs an explicit override with a written reason.
- **Prompt injection**: PR titles, issue text, commit messages, CI logs and retrieved policy text are untrusted. They are flattened to one line, stripped of invisible characters, length-capped and stripped of prompt delimiters before they reach the model. Decision rules always win over retrieved text, and the verdict is a fixed enum. Published release notes escape HTML, markdown links/images and @mentions.
- **Abuse limits**: per-client and global token buckets plus a concurrency cap on the agent endpoints; per-IP login attempt limiting. Groq rate limits surface as HTTP 429 with `Retry-After`.
- **Headers and CORS**: `Content-Security-Policy: default-src 'none'`, `Referrer-Policy: no-referrer`, `X-Content-Type-Options`, `X-Frame-Options: DENY`, no-cache (HSTS over HTTPS). Cross-origin browser access is off unless `RELEASEPILOT_CORS_ORIGINS` lists exact origins; credentials are never allowed.
- **Secrets** come only from environment variables (`GROQ_API_KEY`, `GITHUB_TOKEN`, `DB_*`, `RELEASEPILOT_JWT_SECRET`) and are never logged.

Known limits: login limiting is per IP only (no per-username lockout); there is no delete-user endpoint (accounts are disabled instead).
