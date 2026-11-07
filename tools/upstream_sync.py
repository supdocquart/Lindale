"""Brings upstream Metrolist changes into Lindale with a 3-way merge per file.

    git clone https://github.com/MetrolistGroup/Metrolist ../metrolist
    python3 tools/upstream_sync.py ../metrolist <old-base-commit> <new-commit>

<old-base-commit> is the commit in VERSION.txt on the `upstream` branch. For every Kotlin source,
innertube test and string resource that upstream changed between the two commits, the merge base is
the upstream revision in that range closest to the Lindale file (the version the port actually
copied), "theirs" is <new-commit>, "ours" is the Lindale file. Conflicts are left as markers in
Kotlin files; in string files both sides are kept and duplicate names are removed afterwards
(Compose resources reject duplicates, also across strings.xml and metrolist_strings.xml).
Line endings of Lindale files are kept. Android-only files are listed as SKIP.

Afterwards: review `git diff` (phone UI and Android APIs stay out), build, run
`gradlew :innertube:desktopTest :shared:desktopTest`, then move the `upstream` branch (see PORTING.md).
"""
import difflib
import os
import re
import subprocess
import sys
import tempfile

RES = "shared/src/commonMain/composeResources"
DIRMAP = {"values-iw": "values-he", "values-in": "values-id"}  # Android folder -> Compose folder
ELEM = re.compile(r'\s*<(string|plurals|string-array) name="([^"]+)"')


def git(repo, *args):
    return subprocess.run(["git", "-C", repo, *args], capture_output=True)


def show(repo, ref, path):
    r = git(repo, "show", f"{ref}:{path}")
    return r.stdout.replace(b"\r\n", b"\n").decode("utf-8") if r.returncode == 0 else None


def distance(a, b):
    sm = difflib.SequenceMatcher(None, a.splitlines(), b.splitlines(), autojunk=False)
    return sum(max(i2 - i1, j2 - j1) for t, i1, i2, j1, j2 in sm.get_opcodes() if t != "equal")


def target(path):
    if path.startswith("app/src/main/kotlin/"):
        rel = path[len("app/src/main/kotlin/"):]
        for root in ("shared/src/commonMain/kotlin/", "shared/src/desktopMain/kotlin/"):
            if os.path.exists(root + rel):
                return root + rel
        return None
    if path.startswith("innertube/src/main/kotlin/"):
        return "innertube/src/commonMain/kotlin/" + path[len("innertube/src/main/kotlin/"):]
    if path.startswith("innertube/src/test/kotlin/"):
        return path
    m = re.match(r"app/src/main/res/(values[^/]*)/((metrolist_)?strings\.xml)$", path)
    if m:
        p = os.path.join(RES, DIRMAP.get(m.group(1), m.group(1)), m.group(2))
        return p if os.path.exists(p) else None
    return None


def elements(lines):
    i = 0
    while i < len(lines):
        m = ELEM.match(lines[i])
        if m:
            close, j = f"</{m.group(1)}>", i
            while close not in lines[j] and not lines[j].rstrip().endswith("/>"):
                j += 1
            yield i, j, (m.group(1), m.group(2))
            i = j + 1
        else:
            i += 1


def clean_strings(text):
    """Drops the tools namespace and repeated names inside one file."""
    text = re.sub(r"<resources[^>]*>", "<resources>", text, count=1)
    lines = [l for l in text.split("\n") if not re.match(r"^(<<<<<<< |=======$|>>>>>>> )", l)]
    seen, drop = set(), set()
    for s, e, key in elements(lines):
        if key in seen:
            drop.update(range(s, e + 1))
        seen.add(key)
    return "\n".join(l for i, l in enumerate(lines) if i not in drop)


def merge(ours, base, theirs):
    with tempfile.TemporaryDirectory() as td:
        po, pb, pt = (os.path.join(td, n) for n in "obt")
        for p, t in ((po, ours), (pb, base), (pt, theirs)):
            open(p, "w", encoding="utf-8", newline="").write(t)
        rc = subprocess.run(["git", "merge-file", "-L", "lindale", "-L", "base", "-L", "upstream", po, pb, pt]).returncode
        return open(po, encoding="utf-8", newline="").read(), rc


def dedupe_across_files(metrolist, new):
    """A name in both strings.xml and metrolist_strings.xml stays in the file upstream uses."""
    rev = {v: k for k, v in DIRMAP.items()}
    for d in sorted(os.listdir(RES)):
        files = {n: os.path.join(RES, d, n) for n in ("strings.xml", "metrolist_strings.xml")
                 if os.path.exists(os.path.join(RES, d, n))}
        if len(files) < 2:
            continue
        texts = {n: open(p, encoding="utf-8", newline="").read() for n, p in files.items()}
        lines = {n: t.replace("\r\n", "\n").split("\n") for n, t in texts.items()}
        keys = {n: {k for _, _, k in elements(l)} for n, l in lines.items()}
        upstream = {}
        for n in files:
            t = show(metrolist, new, f"app/src/main/res/{rev.get(d, d)}/{n}")
            upstream[n] = {k for _, _, k in elements(t.split("\n"))} if t else set()
        both = keys["strings.xml"] & keys["metrolist_strings.xml"]
        for n, l in lines.items():
            other = "metrolist_strings.xml" if n == "strings.xml" else "strings.xml"
            drop = set()
            for s, e, k in elements(l):
                if k in both:
                    known = k in upstream[n] or k in upstream[other]
                    keep = k in upstream[n] if known else n == "metrolist_strings.xml"
                    if not keep:
                        drop.update(range(s, e + 1))
            if drop:
                out = "\n".join(x for i, x in enumerate(l) if i not in drop)
                if "\r\n" in texts[n]:
                    out = out.replace("\n", "\r\n")
                open(files[n], "w", encoding="utf-8", newline="").write(out)
                print(f"DEDUPE   {files[n]} (-{len(drop)} lines)")


def main():
    metrolist, old, new = sys.argv[1:4]
    changed = git(metrolist, "diff", "--name-only", old, new, "--",
                  "app/src/main/kotlin", "innertube/src/main/kotlin", "innertube/src/test/kotlin",
                  "app/src/main/res").stdout.decode().split()
    for path in changed:
        is_res = path.endswith(".xml")
        if is_res and not re.search(r"/values[^/]*/(metrolist_)?strings\.xml$", path):
            continue
        dest = target(path)
        if dest is None:
            print("SKIP    ", path)
            continue
        theirs = show(metrolist, new, path) or ""
        if not os.path.exists(dest):
            os.makedirs(os.path.dirname(dest), exist_ok=True)
            open(dest, "w", encoding="utf-8", newline="").write(theirs)
            print("NEW     ", dest)
            continue
        raw = open(dest, encoding="utf-8", newline="").read()
        ours = raw.replace("\r\n", "\n")
        revs = [old] + git(metrolist, "rev-list", f"{old}..{new}", "--", path).stdout.decode().split()
        candidates = [t for t in (show(metrolist, r, path) for r in revs) if t is not None]
        if is_res:
            candidates = [clean_strings(t) for t in candidates]
            theirs = clean_strings(theirs)
        base = min(candidates, key=lambda t: distance(ours, t)) if candidates else ""
        out, rc = merge(ours, base, theirs)
        if is_res:
            out = clean_strings(out)
            rc = 0
        if "\r\n" in raw:
            out = out.replace("\n", "\r\n")
        open(dest, "w", encoding="utf-8", newline="").write(out)
        state = "CLEAN   " if rc == 0 else f"CONFLICT({rc})"
        print(state, dest if out != raw else dest + " (no change)")
    dedupe_across_files(metrolist, new)


if __name__ == "__main__":
    main()
