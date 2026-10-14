#!/usr/bin/env python3
"""Keep the Go modules' major-version import suffix in step with the release.

Go's semantic import versioning requires a module at major version 2 or above to
end its path in /vN, and the module proxy rejects a vN.x.y tag whose go.mod says
otherwise. Both Go modules live in subdirectories of this repository, so their
tags stay <subdir>/vX.Y.Z while the module path (and every import of it) carries
the /vN suffix.

  go_module_major.py --check N   exit 1 unless every reference uses /vN
  go_module_major.py --set N     rewrite every reference to /vN, print changed files

References are found in tracked text files. changelog.md and docs/plans/ are
historical records and are never rewritten or checked.
"""
import argparse
import pathlib
import re
import subprocess
import sys

MODULE_DIRS = ("mockserver-client-go", "mockserver-testcontainers/go")
REPO_PREFIX = "github.com/mock-server/mockserver-monorepo/"
EXCLUDED = (re.compile(r"^changelog\.md$"), re.compile(r"^docs/plans/"), re.compile(r"^\.tmp/"))

# A module path: a module base, optionally followed by a /vN suffix. A suffixed
# path may continue into a package ("/vN/internal", "/vN/..."), but not into a
# file ("/vN/README.md"). A trailing "/vN.N" is a version (e.g. a tag), not a
# suffix. A bare base followed by a word, "-", "/" or ".x" is a different path
# (".../go.mod", ".../README.md"), not a module path.
_BASES = "|".join(re.escape(REPO_PREFIX + d) for d in MODULE_DIRS)
REFERENCE = re.compile(
    r"(?P<base>" + _BASES + r")"
    r"(?:/v(?P<major>\d+)"
    r"(?:(?![\w/-])(?!\.\d)|(?=/\.\.\.)|(?=/[A-Za-z_][\w-]*(?![\w-])(?!\.\w)))"
    r"|(?![\w/-])(?!\.\w))"
)
# A suffixed module path with a version: a go.mod require ("path vX.Y.Z", on one
# line) or a query ("path@vX.Y.Z"). The version's major must match the suffix.
VERSIONED = re.compile(
    r"(?P<path>(?:" + _BASES + r")/v(?P<major>\d+))"
    r"(?P<sep>[ \t]+|@)v(?P<vmajor>\d+)\.\d+\.\d+(?![\w-])(?!\.\d)"
)


def tracked_files(root):
    out = subprocess.run(["git", "-C", str(root), "ls-files", "-z"], check=True, capture_output=True).stdout
    for rel in out.decode().split("\0"):
        if rel and not any(p.search(rel) for p in EXCLUDED):
            yield rel


def read_text(path):
    try:
        data = path.read_bytes()
    except (FileNotFoundError, IsADirectoryError):
        return None
    if b"\0" in data:
        return None
    try:
        return data.decode("utf-8")
    except UnicodeDecodeError:
        return None


def _versioned(m):
    if m.group("vmajor") == m.group("major"):
        return m.group(0)
    return f"{m.group('path')}{m.group('sep')}v{m.group('major')}.0.0"


def rewrite(text, major):
    text = REFERENCE.sub(lambda m: f"{m.group('base')}/v{major}", text)
    return VERSIONED.sub(_versioned, text)


def stale_references(text, major):
    for m in REFERENCE.finditer(text):
        if m.group("major") != str(major):
            yield m.group(0), text.count("\n", 0, m.start()) + 1
    for m in VERSIONED.finditer(text):
        if m.group("vmajor") != m.group("major"):
            yield m.group(0), text.count("\n", 0, m.start()) + 1


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--check", type=int, metavar="N")
    mode.add_argument("--set", type=int, metavar="N")
    parser.add_argument("--root", default=pathlib.Path(__file__).resolve().parents[2], type=pathlib.Path)
    args = parser.parse_args()
    major = args.check if args.check is not None else args.set
    if major < 2:
        sys.exit("ERROR: a /vN suffix only applies from major version 2")

    for d in MODULE_DIRS:
        if not (args.root / d / "go.mod").is_file():
            sys.exit(f"ERROR: {d}/go.mod not found under {args.root}")

    problems, changed = [], []
    for rel in tracked_files(args.root):
        path = args.root / rel
        text = read_text(path)
        if text is None or REPO_PREFIX not in text:
            continue
        if args.set is not None:
            new_text = rewrite(text, major)
            if new_text != text:
                path.write_text(new_text, encoding="utf-8")
                changed.append(rel)
            text = new_text
        for ref, line in stale_references(text, major):
            problems.append(f"{rel}:{line}: {ref}")
        if rel in (f"{d}/go.mod" for d in MODULE_DIRS):
            module_line = re.search(r"(?m)^module\s+(\S+)", text)
            if not module_line or not module_line.group(1).endswith(f"/v{major}"):
                problems.append(f"{rel}: module path {module_line.group(1) if module_line else '?'} does not end in /v{major}")

    for rel in changed:
        print(rel)
    if problems:
        print(f"ERROR: Go module references not at /v{major}:", file=sys.stderr)
        for p in problems:
            print(f"  {p}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
