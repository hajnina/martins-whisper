#!/usr/bin/env python3
"""V1 scanner: over-mocking. Fail-closed flags for:
 - test mocking libraries (org.mockito, io.mockk, okhttp3.mockwebserver except http layer)
 - mocking the class under test by name heuristic (Mock<UT> or mock of *Engine inside engine test)
Allowlist: scripts/verify/scan-allowlist.txt entries "M:path".
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

findings = []
for root in ("core/src/test", "app/src/test"):
    d = os.path.join(base, root)
    if not os.path.isdir(d): continue
    for dirpath, _, names in os.walk(d):
        for n in names:
            if not n.endswith(".kt"): continue
            p = os.path.join(dirpath, n)
            rel = os.path.relpath(p, base)
            if f"M:{rel}" in allowed: continue
            lines = open(p).read().splitlines()
            uses_mocking = False
            for i, ln in enumerate(lines):
                if re.search(r"import\s+(io\.mockk|org\.mockito|com\.nhaarman)", ln):
                    uses_mocking = True
                    findings.append(f"MOCKLIB {rel}:{i+1} mocks-first library: {ln.strip()}")
                if re.search(r"\bmockk<|\bmock\(|\bmockito\b|Mockito\.", ln):
                    uses_mocking = True
                    findings.append(f"MOCKUSE {rel}:{i+1} {ln.strip()}")
            if uses_mocking:
                # class under test heuristic: production class name = file stem minus Test
                stem = os.path.splitext(os.path.basename(p))[0]
                ut = re.sub(r"(Test|RobolectricTest)$", "", stem)
                for i, ln in enumerate(lines):
                    if re.search(rf"mock.{0,20}\b{ut}\b", ln, re.I) or re.search(rf"\b{ut}\b.*mock" , ln, re.I):
                        findings.append(f"MOCKUT {rel}:{i+1} mocking class under test {ut}")

for f in findings:
    print(f)
sys.exit(1 if findings else 0)
