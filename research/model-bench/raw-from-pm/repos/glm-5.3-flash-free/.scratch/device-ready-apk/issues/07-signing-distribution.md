# 07 — How is the v1 APK signed and distributed?

Type: grilling
Status: open
Blocked by: none

## Question

Decide before G1 so the artifact is reproducible and upgrades work:

- Is v1 a debug-signed APK for Martin's device only, or a release-signed APK with a keystore? If release: who generates and holds the keystore, where does it live (never in the repo), and how is it backed up?
- What is the versionCode/versionName policy for the first push and subsequent builds?
- Where is the APK published for the device test: local path, GitHub release asset by the owner, or private file share? What checksum is published?
- What must never be committed (keystore, passwords, `local.properties`, model files), and how do we verify before the owner pushes?
- What is the upgrade path if the signing key changes (uninstall/reinstall acceptable)?

Output: signed-artifact decision, custody/backup steps for Martin, and push checklist.
