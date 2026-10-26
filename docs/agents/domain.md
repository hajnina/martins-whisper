# Domain Docs

How the engineering skills consume this repo's domain documentation.

## Before exploring, read these

- `GLOSSARY.md` at the repo root, or `GLOSSARY-MAP.md` if it exists.
- `docs/adr/` for architecture decisions. This repo also keeps accepted decisions in `DECISIONS.md` at the root.

If these files don't exist, proceed silently.

## File structure

Single-context repo:

```
/
├── GLOSSARY.md
├── DECISIONS.md
├── docs/adr/
└── core/, app/
```

## Use the glossary's vocabulary

When output names a domain concept (issue title, refactor proposal, hypothesis, test name), use the term as defined in `GLOSSARY.md`. Note gaps for `/domain-modeling` instead of inventing synonyms.

## Flag ADR conflicts

If output contradicts an existing ADR in `DECISIONS.md` or `docs/adr/`, surface it explicitly:

> _Contradicts ADR-004 (GGML-only v1), but worth reopening because…_
