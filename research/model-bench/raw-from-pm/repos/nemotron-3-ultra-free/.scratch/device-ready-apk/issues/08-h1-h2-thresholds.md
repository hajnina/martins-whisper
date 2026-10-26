# 08 — What H1/H2 thresholds and app matrix define hardware acceptance?

Type: grilling
Status: open
Blocked by: 02, 06

## Question

Make hardware acceptance measurable before a device exists:

- Which apps/fields form the insertion matrix (Compose, classic EditText, WebView, chat apps, browser address bar, password/PIN must-refuse, multiline, RTL/emoji)? Pick a realistic short list.
- H1 pass: install, permissions, overlay focus retention, insertion with accessibility on, graceful fallback with it off, reboot re-arm notification, idle unload, no crash across the matrix.
- H2 thresholds: cold/warm latency for base and tiny, peak RAM, idle-unload frees memory (measure method), GPU vs CPU on the actual device, thermal/battery sanity. What numbers pass, what is "report only"?
- How is each result captured (screen recording, `dumpsys meminfo`, logcat excerpt) and attached to the handoff without secrets?
- What blocks sign-off vs what becomes a documented known issue?

Output: H1 checklist + H2 measurement table with pass/fail criteria.
