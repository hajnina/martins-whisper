# Provider status

Status: LIMITED since 2026-10-26 18:20:12 (free tier limit, free_tier_limit).

Rules while LIMITED:
- Do not launch new worker sessions.
- Running tickets stay `blocked: provider` until this file says `ok`.
- No retry loops; wait.

Clearing: update this file to `ok` when the provider is usable again, then resume KANBAN waves.
