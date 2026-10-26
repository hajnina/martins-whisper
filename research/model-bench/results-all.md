# Model bench — measured results

Tasks: A = fix failing Kotlin unit test (harness-verified); B = web research with 3 citations; C = strict HANDOFF.md template; D = find canary in ~200k-token pile. `—` = not run. BLOCKED = provider/infra, not a model verdict. Recovered = app PM runs 16:58–17:03Z; new = ORIN reruns.

| Model | A | B | C | D | wall A/B/C/D (s) | tokens A/B/C/D | tools | toolErr | apiErr | repeats |
|---|---|---|---|---|---|---|---|---|---|---|
| deepseek-v4.1-flash-free | PASS | PASS | PASS | PASS | 31/129/22/9 | 137263/307869/106410/36834 | 33 | 2 | 0 | 0 |
| glm-5.3-flash-free | PASS | PASS | PASS | PASS | 11/105/47/8 | 49599/224414/61995/35743 | 21 | 2 | 0 | 1 |
| mimo-v2.6-flash-free | PASS | PASS | PASS | PASS | 12/198/64/8 | 38083/341867/133682/23800 | 34 | 3 | 0 | 0 |
| big-pickle | PASS | PASS | PASS | PASS | 8/70/31/7 | 39075/212369/138771/24327 | 28 | 1 | 0 | 0 |
| flint-40b-free | PASS | PASS | BLOCKED | PASS | 21/381/205/30 | 87473/262029/77057/64187 | 31 | 8 | 1 | 6 |
| nemotron-3-ultra-free | BLOCKED | BLOCKED | BLOCKED | BLOCKED | 76/73/76/74 | 0/0/0/0 | 0 | 0 | 4 | 0 |
| nemotron-3.5-lightning-free | PASS | PASS | PASS | PASS | 24/128/36/8 | 55851/521654/136753/41870 | 34 | 3 | 0 | 2 |
| nemotron-3.5-super-vl-free | PASS | PARTIAL | PASS | PASS | 8/395/42/14 | 40026/825900/87162/27576 | 28 | 5 | 0 | 1 |
| beam-free | PASS | PASS | PASS | PASS | 7/89/40/5 | 58635/349548/308210/36456 | 38 | 3 | 0 | 5 |

## Detail

- **deepseek-v4.1-flash-free A** PASS — verify=0 apiErr=0; source=recovered; wall=31s tokens=137263 tools=9 repeats=0 gaps120s=0
- **deepseek-v4.1-flash-free B** PASS — urls=3 android15/api35=true magic=true mit=true exit=0; source=recovered; wall=129s tokens=307869 tools=15 repeats=0 gaps120s=0
- **deepseek-v4.1-flash-free C** PASS — sections=8/8 summary=true exit=0; source=recovered; wall=22s tokens=106410 tools=7 repeats=0 gaps120s=0
- **deepseek-v4.1-flash-free D** PASS — canary=true exit=0; source=recovered; wall=9s tokens=36834 tools=2 repeats=0 gaps120s=0
- **glm-5.3-flash-free A** PASS — verify=0 apiErr=0; source=recovered; wall=11s tokens=49599 tools=3 repeats=0 gaps120s=0
- **glm-5.3-flash-free B** PASS — urls=4 android15/api35=true magic=true mit=true exit=0; source=recovered; wall=105s tokens=224414 tools=11 repeats=1 gaps120s=0
- **glm-5.3-flash-free C** PASS — sections=8/8 summary=true exit=0; source=recovered; wall=47s tokens=61995 tools=5 repeats=0 gaps120s=0
- **glm-5.3-flash-free D** PASS — canary=true exit=0; source=recovered; wall=8s tokens=35743 tools=2 repeats=0 gaps120s=0
- **mimo-v2.6-flash-free A** PASS — verify=0 apiErr=0; source=recovered; wall=12s tokens=38083 tools=2 repeats=0 gaps120s=0
- **mimo-v2.6-flash-free B** PASS — urls=4 android15/api35=true magic=true mit=true exit=0; source=recovered; wall=198s tokens=341867 tools=16 repeats=0 gaps120s=0
- **mimo-v2.6-flash-free C** PASS — sections=8/8 summary=true exit=0; source=recovered note=PASS manually (8/8 with variant headings); naive heading match gave a false negative; wall=64s tokens=133682 tools=15 repeats=0 gaps120s=0
- **mimo-v2.6-flash-free D** PASS — canary=true exit=0; source=recovered; wall=8s tokens=23800 tools=1 repeats=0 gaps120s=0
- **big-pickle A** PASS — verify=0 apiErr=0; source=recovered; wall=8s tokens=39075 tools=2 repeats=0 gaps120s=0
- **big-pickle B** PASS — urls=3 android15/api35=true magic=true mit=true exit=0; source=recovered; wall=70s tokens=212369 tools=14 repeats=0 gaps120s=0
- **big-pickle C** PASS — sections=8/8 summary=true exit=0; source=recovered; wall=31s tokens=138771 tools=11 repeats=0 gaps120s=0
- **big-pickle D** PASS — canary=true exit=0; source=recovered; wall=7s tokens=24327 tools=1 repeats=0 gaps120s=0
- **flint-40b-free A** PASS — verify=0 apiErr=0; source=recovered; wall=21s tokens=87473 tools=6 repeats=2 gaps120s=0
- **flint-40b-free B** PASS — urls=4 android15/api35=true magic=true mit=true exit=0; source=new; wall=381s tokens=262029 tools=10 repeats=3 gaps120s=0
- **flint-40b-free C** BLOCKED — blocked: provider (Upstream request failed: Endpoint is unavailable.); source=new; wall=205s tokens=77057 tools=11 repeats=1 gaps120s=0
- **flint-40b-free D** PASS — canary=true exit=0; source=new; wall=30s tokens=64187 tools=4 repeats=0 gaps120s=0
- **nemotron-3-ultra-free A** BLOCKED — blocked: provider (Upstream request failed: Model is unavailable.); source=new; wall=76s tokens=0 tools=0 repeats=0 gaps120s=0
- **nemotron-3-ultra-free B** BLOCKED — blocked: provider (Upstream request failed: Model is unavailable.); source=new; wall=73s tokens=0 tools=0 repeats=0 gaps120s=0
- **nemotron-3-ultra-free C** BLOCKED — blocked: provider (Upstream request failed: Model is unavailable.); source=new; wall=76s tokens=0 tools=0 repeats=0 gaps120s=0
- **nemotron-3-ultra-free D** BLOCKED — blocked: provider (Upstream request failed: Model is unavailable.); source=new; wall=74s tokens=0 tools=0 repeats=0 gaps120s=0
- **nemotron-3.5-lightning-free A** PASS — verify=0 apiErr=0; source=recovered; wall=24s tokens=55851 tools=3 repeats=1 gaps120s=0
- **nemotron-3.5-lightning-free B** PASS — urls=3 android15/api35=true magic=true mit=true exit=0; source=new; wall=128s tokens=521654 tools=20 repeats=1 gaps120s=0
- **nemotron-3.5-lightning-free C** PASS — sections=8/8 summary=true exit=0; source=new note=PASS content, but wrote outside its workdir (worktree + owner dir); containment risk; wall=36s tokens=136753 tools=9 repeats=0 gaps120s=0
- **nemotron-3.5-lightning-free D** PASS — canary=true exit=0; source=new; wall=8s tokens=41870 tools=2 repeats=0 gaps120s=0
- **nemotron-3.5-super-vl-free A** PASS — verify=0 apiErr=0; source=recovered; wall=8s tokens=40026 tools=2 repeats=0 gaps120s=0
- **nemotron-3.5-super-vl-free B** PARTIAL — urls=5 android15/api35=true magic=true mit=true exit=0; source=new note=PARTIAL: (a) withheld — source 1 unreachable; (b) correct; wall=395s tokens=825900 tools=19 repeats=1 gaps120s=0
- **nemotron-3.5-super-vl-free C** PASS — sections=8/8 summary=true exit=0; source=new; wall=42s tokens=87162 tools=6 repeats=0 gaps120s=0
- **nemotron-3.5-super-vl-free D** PASS — canary=true exit=0; source=new; wall=14s tokens=27576 tools=1 repeats=0 gaps120s=0
- **beam-free A** PASS — verify=0 apiErr=0; source=recovered; wall=7s tokens=58635 tools=4 repeats=0 gaps120s=0
- **beam-free B** PASS — urls=3 android15/api35=true magic=true mit=true exit=0; source=new; wall=89s tokens=349548 tools=13 repeats=1 gaps120s=0
- **beam-free C** PASS — sections=8/8 summary=true exit=0; source=new; wall=40s tokens=308210 tools=19 repeats=4 gaps120s=0
- **beam-free D** PASS — canary=true exit=0; source=new; wall=5s tokens=36456 tools=2 repeats=0 gaps120s=0
