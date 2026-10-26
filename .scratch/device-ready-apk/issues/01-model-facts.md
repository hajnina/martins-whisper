# 01 — Which Whisper GGML models can v1 ship, from where, at what size?

Type: research
Status: open
Blocked by: none

## Question

Establish the facts the model catalog and D1 docs depend on:

- Exact whisper.cpp GGML/GGUF model files for tiny, base, small, medium, large-v3-turbo (multilingual and English where relevant): canonical download URL, file size, SHA256 if published, and license.
- Which of those are realistic on a 6–8 GB phone, and what the SPEC R4 default (base) costs in storage/RAM.
- Distribution constraints: can the app download directly from the upstream host, is a mirror needed, and what attribution/license text must ship?
- Any GGML format-version compatibility constraints for the whisper.cpp revision chosen by E1.

Do not choose the product policy yet; deliver facts with primary-source links.
