# 06 — Which operators, devices and permissions does D1 document for v1?

Type: grilling
Status: open
Blocked by: 01

## Question

Decide the D1 documentation set and its audience:

- Who installs v1: a technical sideloader (Martin/dev) or a non-technical user? What does that imply for the permission checklist and troubleshooting?
- Which minimum device profile is v1 supported on (Android version, RAM, arm64, Vulkan presence)? What is best-effort vs unsupported?
- Which docs are required: build, install/sideload, adb model push, permission wizard, privacy/data-flow, known OEM quirks, uninstall/cleanup?
- How are model download/import, accessibility enablement, and overlay/mic permissions explained without assuming Android expertise?
- What is the "doctor" output that should be pasted into a bug report (backend, model, permissions, device, version), with no secrets?

Output: doc outline with owners and acceptance ("a new user can install and dictate from this alone").
