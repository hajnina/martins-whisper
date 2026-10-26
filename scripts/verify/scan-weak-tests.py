#!/usr/bin/env python3
"""V1 scanner core: weak/tautological tests. Prints findings as TYPE file:line msg."""
import os, re, sys

base = sys.argv[1]
_allow = os.path.join(os.path.dirname(os.path.abspath(__file__)), "scan-allowlist.txt")
allowed = set()
if os.path.exists(_allow):
    for line in open(_allow):
        line = line.strip()
        if line and not line.startswith("#"):
            allowed.add(line)

def is_allowed(kind, rel):
    return f"{kind}:{rel}" in allowed

files = []
for root in ("core/src/test", "app/src/test"):
    d = os.path.join(base, root)
    if os.path.isdir(d):
        for dirpath, _, names in os.walk(d):
            for n in names:
                if n.endswith(".kt"):
                    files.append(os.path.join(dirpath, n))

assertions = re.compile(
    r"\bassertEquals\b|\bassertTrue\b|\bassertFalse\b|\bassertNull\b|\bassertNotNull\b|"
    r"\bassertSame\b|\bassertNotEquals\b|\bassertFailsWith\b|\bassertThat\b|\bassertThrows\b|\brequire\(|\bcheck\("
)

findings = []
def report(kind, path, lineno, text):
    findings.append((kind, path, lineno, text))

for path in files:
    rel = os.path.relpath(path, base)
    lines = open(path).read().splitlines()
    # whole-file quick checks
    for i, ln in enumerate(lines):
        if re.search(r"assertTrue\(\s*true\s*\)", ln):
            if not is_allowed("A", rel): report("TAUTOLOGY", rel, i + 1, "assertTrue(true)")
        m = (re.search(r"assertEquals\(\s*([A-Za-z_$][\w$.]*)\s*,\s*\1\s*[\),]", ln)
             or re.search(r'assertEquals\(\s*"([^"]*)"\s*,\s*"\1"\s*[\),]', ln)
             or re.search(r"assertTrue\(\s*([A-Za-z_$][\w$.]*)\s*==\s*\1\s*[\),]", ln))
        if m:
            if not is_allowed("A", rel): report("TAUTOLOGY", rel, i + 1, f"assertEquals({m.group(1)},{m.group(1)})")
        if re.search(r"@(Ignore|Disabled)\b", ln):
            report("SKIP", rel, i + 1, ln.strip())
        if re.search(r"\bassume(True|False|That)\b", ln):
            report("SKIP", rel, i + 1, ln.strip())
    # per @Test block
    i = 0
    n = len(lines)
    while i < n:
        if re.match(r"^\s*@Test\b", lines[i]):
            j = i
            while j < n and "fun " not in lines[j]:
                j += 1
            funname = "?"
            if j < n:
                fm = re.search(r"fun\s+`?([^`(\s]+)`?", lines[j])
                if fm: funname = fm.group(1)
            k = j; depth = 0; started = False; end = None
            while k < n:
                for ch in lines[k]:
                    if ch == "{":
                        depth += 1; started = True
                    elif ch == "}":
                        depth -= 1
                        if started and depth == 0:
                            end = k; break
                if end is not None: break
                k += 1
            block = lines[i:end + 1] if end is not None else lines[i:]
            blocktext = "\n".join(block)
            if not is_allowed("B", rel) and not assertions.search(blocktext):
                report("NOASSERT", rel, i + 1, f"fun `{funname}` has zero assertions")
            # swallowed catch blocks inside the test body (from the fun line on)
            body = lines[j + 1:end + 1] if end is not None else []
            for bi, bl in enumerate(body):
                cm = re.search(r"catch\s*\(\s*_\s*:\s*(\w+)\s*\)", bl)
                if cm:
                    # find end of try block for context
                    after = "\n".join(body[bi + 1:])
                    # find next catch-closing brace followed by assertions after catch
                    after_text = after[:after.find("}") if 0 <= after.find("}") < after.find("fun") else len(after)]
                    if not assertions.search(after_text):
                        if not is_allowed("D", rel):
                            report("SWALLOW", rel, i + bi + 1, f"catch(_:{cm.group(1)}) with no assertion after")
            i = (end + 1) if end is not None else n
        else:
            i += 1

for kind, rel, lineno, text in findings:
    print(f"{kind} {rel}:{lineno} {text}")
sys.exit(1 if findings else 0)
