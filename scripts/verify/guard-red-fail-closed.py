#!/usr/bin/env python3
"""Per-file test/assert count ratchet + ratchet.json + gate hash check."""
import hashlib, json, os, re, subprocess, sys

base, BASE, strict = sys.argv[1], sys.argv[2], sys.argv[3]
ratchet_path = os.path.join(base, "scripts/verify/ratchet.json")

def files_at(rev):
    out = subprocess.run(["git", "ls-tree", "-r", "--name-only", rev, "core/src/test", "app/src/test"],
                         cwd=base, capture_output=True, text=True)
    return {l for l in out.stdout.split() if l.endswith(".kt")}

def content_at(rev, f):
    if rev == "HEAD":
        try:
            return open(os.path.join(base, f)).read()
        except OSError:
            return ""
    out = subprocess.run(["git", "show", f"{rev}:{f}"], cwd=base, capture_output=True, text=True)
    return out.stdout if out.returncode == 0 else ""

def counts(text):
    tests = len(re.findall(r"@Test\b", text))
    asserts = len(re.findall(
        r"\bassertEquals\b|\bassertTrue\b|\bassertFalse\b|\bassertNull\b|\bassertNotNull\b|"
        r"\bassertSame\b|\bassertNotEquals\b|\bassertFailsWith\b|\bassertThat\b|\bassertThrows\b|"
        r"\brequire\(|\bcheck\(", text))
    answers = len(re.findall(r"throw AssertionError", text))
    return tests, asserts + answers

fails = []
current = {}
base_map = {}
for f in files_at("HEAD") | files_at(BASE):
    t0, a0 = counts(content_at(BASE, f))
    t1, a1 = counts(content_at("HEAD", f))
    base_map[f] = [t0, a0]
    current[f] = [t1, a1]
    if t1 < t0:
        fails.append(f"TEST-COUNT {f}: {t1} < base {t0}")
    if a1 < a0:
        fails.append(f"ASSERT-COUNT {f}: {a1} < base {a0}")

gate_files = [
    "scripts/verify/scan-weak-tests.sh", "scripts/verify/scan-weak-tests.py",
    "scripts/verify/scan-mocks.sh", "scripts/verify/scan-mocks.py",
    "scripts/verify/scan-oracle-copy.sh", "scripts/verify/scan-oracle-copy.py",
    "scripts/verify/mutate.sh", "scripts/verify/mutate.py",
    "scripts/verify/guard-test-diff.sh", "scripts/verify/red-first.sh",
    "scripts/verify/verify-gate.sh", "scripts/verify/ratchet.json",
]
hashes = {g: hashlib.sha256(open(os.path.join(base, g), "rb").read()).hexdigest()
          for g in gate_files if os.path.exists(os.path.join(base, g))}

if os.path.exists(ratchet_path):
    rj = json.load(open(ratchet_path))
    for f, (t1, a1) in current.items():
        if f in rj.get("files", {}):
            rt, ra = rj["files"][f]
            if t1 < rt:
                fails.append(f"RATCHET {f}: tests {t1} < floor {rt}")
            if a1 < ra:
                fails.append(f"RATCHET {f}: asserts {a1} < floor {ra}")
    if "strict" in rj and "verify_sha256_owner" in rj:
        owner_file = rj["verify_sha256_owner"]
        if owner_file and not os.path.exists(owner_file) and strict != "0":
            fails.append(f"GATE-HASH owner file {owner_file} missing in strict mode")
else:
    if strict != "0":
        fails.append("RATCHET ratchet.json missing in strict mode")

json.dump({
    "note": "ratchet floors: [tests, assertions]. Only owner may lower.",
    "files": {f: base_map[f] if f not in current else current[f] for f in sorted(current)},
    "gate_hashes": hashes,
}, open(ratchet_path, "w"), indent=1)

for f in fails:
    print("FAIL " + f)
sys.exit(1 if fails else 0)
