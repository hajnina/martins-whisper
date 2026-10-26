# 10 — What is the G1 integration acceptance checklist?

Type: grilling
Status: open
Blocked by: 03, 04, 05, 06, 07

## Question

Turn integration into a repeatable gate for the PM:

- Merge order for B1, E1, T1, D1 onto master; how to resolve contract conflicts without losing C1/I1/U1 work; who reviews (`code-review`) and what blocks a merge.
- Required commands and evidence: `:core:test`, `:app:testDebugUnitTest`, `:app:assembleDebug`, test counts, APK size/sha256, no-secrets scan, clean `git status`.
- KANBAN/LOGBOOK/DECISIONS/STATUS updates required at merge, and the exact handoff shape for the owner.
- What to do if E1 native/Vulkan is not buildable: ship the seam + CPU path, or hold the APK? Tie to ticket 02.
- Definition of done for G1: artifact path, docs, known issues, and the H1/H2 checklist ready to run on device.

Output: ordered checklist with commands, evidence requirements, and abort conditions.
