# Recovered PM bench (container-local runs, logs in runs/)

Measured 2026-10-26 16:58-17:03 UTC by the app PM; re-analyzed from JSONL. `—` = not run or interrupted.

| Model | A code | B research | C format | D long-ctx | wall A/B/C/D (s) | tokens A/B/C/D | tools | toolErr | apiErr | repeats |
|---|---|---|---|---|---|---|---|---|---|---|
| deepseek-v4.1-flash-free | PASS | PASS | PASS | PASS | 31/129/22/9 | 137263/307869/106410/36834 | 33 | 2 | 0 | 0 |
| glm-5.3-flash-free | PASS | PASS | PASS | PASS | 11/105/47/8 | 49599/224414/61995/35743 | 21 | 2 | 0 | 1 |
| mimo-v2.6-flash-free | PASS | PASS | PASS | PASS | 12/198/64/8 | 38083/341867/133682/23800 | 34 | 3 | 0 | 0 |
| big-pickle | PASS | PASS | PASS | PASS | 8/70/31/7 | 39075/212369/138771/24327 | 28 | 1 | 0 | 0 |
| flint-40b-free | PASS | N/A | FAIL | N/A | 21/—/137/— | 87473/41558/88600/35317 | 27 | 7 | 1 | 3 |
| nemotron-3-ultra-free | N/A | N/A | N/A | N/A | 72/—/—/— | 0/0/0/0 | 0 | 0 | 1 | 0 |
| nemotron-3.5-lightning-free | PASS | N/A | N/A | N/A | 24/—/—/— | 55851/0/0/0 | 3 | 0 | 0 | 1 |
| nemotron-3.5-super-vl-free | PASS | N/A | N/A | N/A | 8/—/—/— | 40026/0/0/0 | 2 | 0 | 0 | 0 |
| beam-free | PASS | N/A | N/A | N/A | 7/—/—/— | 58635/0/0/0 | 4 | 1 | 0 | 0 |

## Run detail

- **deepseek-v4.1-flash-free A** PASS — verify=0 apiErr=0; wall=31s tokens=137263 tools=9 (bash:6, read:2, edit:1)
- **deepseek-v4.1-flash-free B** PASS — urls=3 android15/api35=true magic=true mit=true exit=0; wall=129s tokens=307869 tools=15 (webfetch:4, bash:11)
- **deepseek-v4.1-flash-free C** PASS — sections=8/8 summary=true exit=0; wall=22s tokens=106410 tools=7 (bash:5, write:2)
- **deepseek-v4.1-flash-free D** PASS — canary=true exit=0; wall=9s tokens=36834 tools=2 (grep:1, read:1)
- **glm-5.3-flash-free A** PASS — verify=0 apiErr=0; wall=11s tokens=49599 tools=3 (glob:1, read:1, edit:1)
- **glm-5.3-flash-free B** PASS — urls=4 android15/api35=true magic=true mit=true exit=0; wall=105s tokens=224414 tools=11 (webfetch:4, bash:7)
- **glm-5.3-flash-free C** PASS — sections=8/8 summary=true exit=0; wall=47s tokens=61995 tools=5 (bash:3, write:2)
- **glm-5.3-flash-free D** PASS — canary=true exit=0; wall=8s tokens=35743 tools=2 (grep:1, bash:1)
- **mimo-v2.6-flash-free A** PASS — verify=0 apiErr=0; wall=12s tokens=38083 tools=2 (read:1, edit:1)
- **mimo-v2.6-flash-free B** PASS — urls=4 android15/api35=true magic=true mit=true exit=0; wall=198s tokens=341867 tools=16 (webfetch:4, grep:1, bash:11)
- **mimo-v2.6-flash-free C** PASS — sections=8/8 summary=true exit=0; wall=64s tokens=133682 tools=15 (read:2, glob:3, skill:1, bash:3, write:2, edit:4)
- **mimo-v2.6-flash-free D** PASS — canary=true exit=0; wall=8s tokens=23800 tools=1 (grep:1)
- **big-pickle A** PASS — verify=0 apiErr=0; wall=8s tokens=39075 tools=2 (bash:1, edit:1)
- **big-pickle B** PASS — urls=3 android15/api35=true magic=true mit=true exit=0; wall=70s tokens=212369 tools=14 (webfetch:3, bash:11)
- **big-pickle C** PASS — sections=8/8 summary=true exit=0; wall=31s tokens=138771 tools=11 (bash:9, write:2)
- **big-pickle D** PASS — canary=true exit=0; wall=7s tokens=24327 tools=1 (grep:1)
- **flint-40b-free A** PASS — verify=0 apiErr=0; wall=21s tokens=87473 tools=6 (read:4, edit:2)
- **flint-40b-free B** FAIL — urls=0 android15/api35=false magic=false mit=false exit=none (no meta: interrupted); wall=—s tokens=41558 tools=8 (webfetch:7, websearch:1)
- **flint-40b-free C** FAIL — sections=0/8 summary=false exit=1 apiErr=Upstream request failed: Endpoint is unavailable.; wall=137s tokens=88600 tools=8 (bash:4, glob:2, read:2)
- **flint-40b-free D** FAIL — canary=false exit=none; wall=—s tokens=35317 tools=5 (bash:5)
- **nemotron-3-ultra-free A** BLOCKED — blocked: provider (no model output); wall=72s tokens=0 tools=0
- **nemotron-3-ultra-free B** FAIL — urls=0 android15/api35=false magic=false mit=false exit=none (no meta: interrupted); wall=—s tokens=0 tools=0
- **nemotron-3-ultra-free C** FAIL — sections=0/8 summary=false exit=none; wall=—s tokens=0 tools=0
- **nemotron-3-ultra-free D** FAIL — canary=false exit=none; wall=—s tokens=0 tools=0
- **nemotron-3.5-lightning-free A** PASS — verify=0 apiErr=0; wall=24s tokens=55851 tools=3 (read:2, edit:1)
- **nemotron-3.5-lightning-free B** FAIL — urls=0 android15/api35=false magic=false mit=false exit=none (no meta: interrupted); wall=—s tokens=0 tools=0
- **nemotron-3.5-lightning-free C** FAIL — sections=0/8 summary=false exit=none; wall=—s tokens=0 tools=0
- **nemotron-3.5-lightning-free D** FAIL — canary=false exit=none; wall=—s tokens=0 tools=0
- **nemotron-3.5-super-vl-free A** PASS — verify=0 apiErr=0; wall=8s tokens=40026 tools=2 (read:1, edit:1)
- **nemotron-3.5-super-vl-free B** FAIL — urls=0 android15/api35=false magic=false mit=false exit=none (no meta: interrupted); wall=—s tokens=0 tools=0
- **nemotron-3.5-super-vl-free C** FAIL — sections=0/8 summary=false exit=none; wall=—s tokens=0 tools=0
- **nemotron-3.5-super-vl-free D** FAIL — canary=false exit=none; wall=—s tokens=0 tools=0
- **beam-free A** PASS — verify=0 apiErr=0; wall=7s tokens=58635 tools=4 (read:2, glob:1, edit:1)
- **beam-free B** FAIL — urls=0 android15/api35=false magic=false mit=false exit=none (no meta: interrupted); wall=—s tokens=0 tools=0
- **beam-free C** FAIL — sections=0/8 summary=false exit=none; wall=—s tokens=0 tools=0
- **beam-free D** FAIL — canary=false exit=none; wall=—s tokens=0 tools=0
