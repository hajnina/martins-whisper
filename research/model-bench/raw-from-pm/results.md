# Bench results

| Model | A code | B research | C format | D long-ctx | A/B/C/D wall (s) | Tokens A/B/C/D | Tool calls | Repeated | Errors | Gaps>120s |
|---|---|---|---|---|---|---|---|---|---|---|
| deepseek-v4.1-flash-free | PASS | PASS | PASS | PASS | 31/129/22/9 | 137263/307869/106410/36834 | 33 | 0 | 2 | 0 |
| glm-5.3-flash-free | PASS | PASS | PASS | PASS | 11/105/47/8 | 49599/224414/61995/35743 | 21 | 1 | 2 | 0 |
| mimo-v2.6-flash-free | PASS | PASS | PASS | PASS | 12/198/64/8 | 38083/341867/133682/23800 | 34 | 0 | 3 | 0 |
| big-pickle | PASS | PASS | PASS | PASS | 8/70/31/7 | 39075/212369/138771/24327 | 28 | 0 | 1 | 0 |
| flint-40b-free | PASS | PASS | FAIL | FAIL | 21/418/137/165 | 87473/1088174/88600/35317 | 52 | 5 | 9 | 0 |
| nemotron-3-ultra-free | FAIL | FAIL | FAIL | FAIL | 72/72/76/71 | 0/0/0/0 | 0 | 0 | 0 | 0 |
| nemotron-3.5-lightning-free | PASS | PASS | PASS | PASS | 24/95/46/6 | 55851/149645/112752/25031 | 19 | 1 | 2 | 0 |
| nemotron-3.5-super-vl-free | PASS | PASS | PASS | PASS | 8/233/23/7 | 40026/587842/52608/25094 | 36 | 0 | 2 | 0 |
| beam-free | PASS | PASS | PASS | PASS | 7/63/29/7 | 58635/403744/206808/44909 | 35 | 0 | 3 | 0 |

## Failure detail

- **flint-40b-free C**: sections=0/8 summary=false (exit 1, timeout false, wall 137s)
- **flint-40b-free D**: canary missing (exit 1, timeout false, wall 165s)
- **nemotron-3-ultra-free A**: verify=1 fixed=false (exit 1, timeout false, wall 72s)
- **nemotron-3-ultra-free B**: urls=0 api35=false magic=false mit=false (exit 1, timeout false, wall 72s)
- **nemotron-3-ultra-free C**: sections=0/8 summary=false (exit 1, timeout false, wall 76s)
- **nemotron-3-ultra-free D**: canary missing (exit 1, timeout false, wall 71s)
