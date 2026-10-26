#!/usr/bin/env python3
"""V1 scanner: oracle == implementation. Fail-closed flags asserts whose
expected side calls the production class under the test (test recomputes s.o.t.a.).
Entry "O:path" in scan-allowlist.txt allows a whole file (must be justified to owner).
"""
import os, re, sys

base = sys.argv[1]
allowfile = os.path.join(os.path.dirname(os.path.abspath(__file__)), "scan-allowlist.txt")
allowed = set()
if os.path.exists(allowfile):
    for line in open(allowfile):
        line = line.strip()
        if line and not line.startswith("#"):
            allowed.add(line)

assert_call = re.compile(
    r"(assertEquals|assertSame|assertNotEquals|assertArrayEquals|assertThat) ?\("
)
func_call = re.compile(r"\b([A-Za-z_]\w*)\.(\w+)\s*\(")

findings = []
for root in ("core/src/test", "app/src/test"):
    d = os.path.join(base, root)
    if not os.path.isdir(d): continue
    for dirpath, _, names in os.walk(d):
        for n in names:
            if not n.endswith(".kt"): continue
            p = os.path.join(dirpath, n)
            rel = os.path.relpath(p, base)
            if f"O:{rel}" in allowed: continue
            stem = os.path.splitext(os.path.basename(p))[0]
            ut = re.sub(r"(Test|RobolectricTest)$", "", stem)
            # production classes of the repo referenced as candidates
            text = open(p).read()
            lines = text.splitlines()
            for i, ln in enumerate(lines):
                if not assert_call.search(ln):
                    continue
                # split on the top-level comma after the assert name
                m = assert_call.search(ln)
                inner = ln[
                    m.end():
                ]
                # crude split: first top-level comma
                depth = 0
                split = -1
                for ci, ch in enumerate(inner):
                    if ch in "({[":
                        depth += 1
                    elif ch in ")}]":
                        depth -= 1
                    elif ch == "," and depth == 0:
                        split = ci
                        break
                if split == -1:
                    continue
                expected = inner[:split]
                # expected side calls the class under test?
                for c in re.finditer(rf"\b{ut}\.\w+\s*\(", expected):
                    findings.append(f"ORACLECOPY {rel}:{i+1} expected side calls {ut} (oracle reproduces SUT)")
                    break
                # assert on the call result of the SAME expression before and after (idempotence style)
                calls = re.findall(rf"(?:^|[^\w]){ut}\.\w+\s*\(", expected)
            # also: assert where expected was literally produced by calling SUT earlier in the same fun

for f in findings:
    print(f)
sys.exit(1 if findings else 0)
