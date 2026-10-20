#!/usr/bin/env python3
"""Deterministic security scan of AI agent configuration, reported as SARIF 2.1.0.

Standard library only, no network access. Rules and policy are described in
docs/operations/security.md ("AI agent configuration scan"). The allowlists and
baselines live in agent-config-policy.json next to this script.

Exit status: 0 no findings, 1 findings, 2 the scan could not run.
"""

import argparse
import bisect
import hashlib
import json
import os
import posixpath
import re
import shlex
import subprocess
import sys
import unicodedata

TOOL_NAME = "agent-config-scan"
TOOL_VERSION = "1.0.0"
CATEGORY = "agent-config"
INFO_URI = ("https://github.com/mock-server/mockserver-monorepo/blob/master/"
            "docs/operations/security.md#ai-agent-configuration-scan")
FINGERPRINT_KEY = "agentConfigFinding/v1"

RULES = [
    ("agent-config/hidden-unicode", "HiddenUnicode", "error", "8.0",
     "Invisible, bidirectional or control character in AI agent configuration",
     "Zero-width, bidi-override, tag, variation-selector, private-use, unassigned "
     "or control characters (including a carriage return not followed by a line "
     "feed, invalid UTF-8, and JSON \\u escapes that decode to any of these) can "
     "hide instructions from a human reviewer while an agent still reads them "
     "(Trojan Source). Remove the character, or replace it with a visible equivalent."),
    ("agent-config/permission-drift", "PermissionDrift", "error", "7.5",
     "Agent permission differs from the committed baseline",
     "An allow entry, deny entry, read-only tool restriction or risky setting in "
     ".claude/settings*.json, .claude/agents/*.md or opencode.jsonc differs from "
     "scripts/security/agent-config-policy.json. If the change is intended, update "
     "the policy file in the same reviewed change."),
    ("agent-config/hook-not-allowlisted", "HookNotAllowlisted", "error", "8.0",
     "Hook command, helper command or plugin code is not on the allowlist",
     "Hooks, helper commands (statusLine, apiKeyHelper and similar), dev container "
     "lifecycle commands, opencode plugins and opencode custom tools run code "
     "without a prompt. Each must match an exact entry in the policy file."),
    ("agent-config/mcp-not-allowlisted", "McpNotAllowlisted", "error", "7.5",
     "MCP server is not on the allowlist",
     "Every MCP server command line and remote URL must match an exact entry in "
     "the policy file, and remote servers must use https."),
    ("agent-config/unpinned-package", "UnpinnedPackage", "error", "7.5",
     "MCP server or plugin package is not pinned to an exact version",
     "npx/uvx/dlx packages need an exact version (name@1.2.3 or name==1.2.3), "
     "container images need an @sha256 digest, and nothing may use 'latest' or "
     "pipe a downloaded script into a shell."),
    ("agent-config/secret", "SecretInAgentConfig", "error", "9.0",
     "Credential pattern in AI agent configuration",
     "A high-precision credential pattern (cloud access key, provider token, "
     "private key) matched. Revoke the credential and remove it. The matched "
     "value is never written to the report."),
    ("agent-config/codeowners-coverage", "CodeownersCoverage", "warning", "5.0",
     "AI agent configuration path has no CODEOWNERS owner",
     "Each AI configuration file, and the scanner and its policy, must be owned "
     "by a CODEOWNERS rule so changes to them require owner review."),
    ("agent-config/unparseable-config", "UnparseableConfig", "error", "6.0",
     "AI agent configuration file could not be parsed or read",
     "The file, or its frontmatter, could not be parsed or is larger than the "
     "policy's maxFileBytes, so the other rules could not check it. The scan "
     "fails closed until it parses."),
    ("agent-config/symlink", "SymlinkInRepository", "error", "7.0",
     "Symbolic link or submodule in the repository",
     "Agents follow a link (or a submodule) to content outside the scanned scope "
     "and outside CODEOWNERS coverage, for example a .claude directory that "
     "points elsewhere. The scanner never follows links. Commit the content "
     "itself, or list the path in the policy's linkExceptions with a reason."),
    ("agent-config/external-reference", "ExternalReference", "error", "7.0",
     "AI configuration pulls in content the scanner cannot check",
     "An @import in CLAUDE.md, AGENTS.md or GEMINI.md, an opencode 'instructions' "
     "entry, a {file:...} reference or a VS Code workspace folder points outside the repository, to an "
     "absolute or home path or a URL, or cannot be resolved. In-repository "
     "targets are scanned as AI configuration instead. A deliberate reference can "
     "be listed in the policy's referenceExceptions with a reason."),
    ("agent-config/unrecognised", "UnrecognisedConfig", "error", "7.0",
     "AI configuration file or key the scanner does not understand",
     "The scanner is closed-world: every file under an AI-tool configuration root, "
     "and every key in the configuration files it parses, must be claimed by a "
     "check or listed in the policy with a reason. Case variants of known names "
     "are reported too, because case-insensitive file systems load them."),
    ("agent-config/scanner-error", "ScannerError", "error", "6.0",
     "The scanner failed on this file",
     "An unexpected shape or an internal error stopped the checks for this file, "
     "so it is reported instead of passing silently."),
]
SEP = chr(0)
RULE_INDEX = {rule[0]: i for i, rule in enumerate(RULES)}
RULE_LEVEL = {rule[0]: rule[2] for rule in RULES}

DEFAULT_IGNORABLE_RANGES = [
    (0x00AD, 0x00AD), (0x034F, 0x034F), (0x061C, 0x061C), (0x115F, 0x1160), (0x17B4, 0x17B5),
    (0x180B, 0x180F), (0x200B, 0x200F), (0x202A, 0x202E), (0x2060, 0x206F), (0x3164, 0x3164),
    (0xFE00, 0xFE0F), (0xFEFF, 0xFEFF), (0xFFA0, 0xFFA0), (0xFFF0, 0xFFF8), (0x1BCA0, 0x1BCA3),
    (0x1D173, 0x1D17A), (0xE0000, 0xE0FFF),
]
INVISIBLE_EXTRA = {0x2800}
EMOJI_PRESENTATION_SELECTORS = {0xFE0E, 0xFE0F}
HIDDEN_CATEGORIES = {"Cc", "Cf", "Co", "Cn", "Cs", "Zl", "Zp"}

SECRET_PATTERNS = [
    ("AWS access key id", re.compile(r"(?<![A-Z0-9])(?:AKIA|ASIA)[A-Z0-9]{16}(?![A-Z0-9])")),
    ("GitHub token", re.compile(r"(?<![A-Za-z0-9_])gh[pousr]_[A-Za-z0-9]{36,}")),
    ("GitHub fine-grained token", re.compile(r"(?<![A-Za-z0-9_])github_pat_[A-Za-z0-9_]{22,}")),
    ("Anthropic API key", re.compile(r"(?<![A-Za-z0-9_-])sk-ant-[A-Za-z0-9_-]{20,}")),
    ("OpenAI API key", re.compile(r"(?<![A-Za-z0-9_-])sk-(?!ant-)(?:proj-|svcacct-|admin-)?[A-Za-z0-9_-]{32,}")),
    ("Slack token", re.compile(r"(?<![A-Za-z0-9_-])xox[abposr]-[A-Za-z0-9-]{10,}")),
    ("Google API key", re.compile(r"(?<![A-Za-z0-9_-])AIza[0-9A-Za-z_-]{35}")),
    ("Buildkite API token", re.compile(r"(?<![A-Za-z0-9_])bkua_[a-f0-9]{40}")),
]
PRIVATE_KEY_BLOCK = re.compile(
    r"-----BEGIN (?:[A-Z0-9]+ )*PRIVATE KEY-----[ \t]*\r?\n(?:[A-Za-z-]+:[^\n]*\n|[ \t]*\r?\n)*"
    r"[ \t]*[A-Za-z0-9+/]{40,}={0,2}[ \t]*\r?$", re.MULTILINE)

RISKY_COMMAND_PATTERNS = [
    ("downloads from the network", re.compile(r"\b(?:curl|wget|Invoke-WebRequest|iwr)\b")),
    ("pipes into a shell", re.compile(r"\|\s*(?:sudo\s+)?(?:ba|z|da|k)?sh\b")),
    ("uses eval", re.compile(r"\beval\b")),
    ("decodes base64", re.compile(r"\bbase64\b")),
    ("opens a raw socket", re.compile(r"\b(?:nc|ncat|netcat|socat)\b|/dev/tcp/")),
]
REMOTE_SCRIPT_PIPE = re.compile(r"\b(?:curl|wget)\b[^|]*\|\s*(?:sudo\s+)?(?:ba|z|da|k)?sh\b")

CLAUDE_SETTINGS_GLOB = "**/.claude/settings*.json"
CLAUDE_AGENTS_GLOB = "**/.claude/agents/**/*.md"
MCP_GLOBS = {"**/.mcp.json": "mcpServers", "**/.cursor/mcp.json": "mcpServers", "**/.vscode/mcp.json": "servers"}
OPENCODE_AGENT_GLOBS = ["**/.opencode/agent/**/*.md", "**/.opencode/agents/**/*.md"]
COMMAND_GLOBS = ["**/.claude/commands/**/*.md", "**/.opencode/command/**/*.md", "**/.opencode/commands/**/*.md"]
PLAIN_TEXT_NAMES = {".cursorrules", ".windsurfrules", ".clinerules", ".rules", ".goosehints"}
VSCODE_SETTINGS_GLOB = "**/.vscode/settings.json"
CODE_WORKSPACE_GLOB = "**/*.code-workspace"
DEVCONTAINER_GLOBS = ["**/.devcontainer/**/devcontainer.json", "**/.devcontainer.json"]
CODE_WORKSPACE_HANDLED_KEYS = {"settings", "folders", "extensions"}
DEVCONTAINER_LIFECYCLE_KEYS = ["initializeCommand", "onCreateCommand", "updateContentCommand",
                               "postCreateCommand", "postStartCommand", "postAttachCommand"]
DEVCONTAINER_HANDLED_KEYS = set(DEVCONTAINER_LIFECYCLE_KEYS) | {"customizations", "image"}
OPENCODE_CODE_GLOBS = ["**/.opencode/plugin/*", "**/.opencode/plugins/*",
                       "**/.opencode/tool/*", "**/.opencode/tools/*"]
CLAUDE_HANDLED_KEYS = {"permissions", "hooks", "env", "enabledPlugins", "extraKnownMarketplaces",
                       "enabledMcpjsonServers", "mcpServers", "apiKeyHelper", "awsAuthRefresh",
                       "awsCredentialExport", "otelHeadersHelper", "statusLine",
                       "enableAllProjectMcpServers", "disableAllHooks"}
DEFAULT_MAX_FILE_BYTES = 1048576
OPENCODE_CODE_EXTENSIONS = (".ts", ".js", ".mjs", ".cjs", ".mts", ".cts")
CLAUDE_COMMAND_KEYS = ["apiKeyHelper", "awsAuthRefresh", "awsCredentialExport",
                       "otelHeadersHelper", "statusLine.command"]
NPM_RUNNERS = {"npx", "bunx", "pnpx"}
NPM_DLX = {"pnpm": {"dlx"}, "yarn": {"dlx"}, "bun": {"x"}, "npm": {"exec", "x"}}
PY_RUNNERS = {"uvx", "pipx"}
CONTAINER_RUNNERS = {"docker", "podman", "nerdctl"}
SHELLS = {"sh", "bash", "zsh", "dash", "ksh"}
NPM_PINNED = re.compile(r"^(?:@[^/@\s]+/)?[^@/\s]+@\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.+-]+)?$")
PY_PINNED = re.compile(r"^[A-Za-z0-9._-]+(?:\[[^\]]*\])?(?:==|@)\d+(?:\.\d+)+(?:[-+._0-9A-Za-z]*)$")
IMAGE_DIGEST = re.compile(r"@sha256:[0-9a-f]{64}$")


class ScanError(Exception):
    pass


class Finding:
    __slots__ = ("rule", "path", "line", "column", "message", "key")

    def __init__(self, rule, path, line, column, message, key):
        self.rule = rule
        self.path = path
        self.line = max(1, line)
        self.column = max(1, column)
        self.message = message
        self.key = key


def glob_regex(pattern):
    out, i = [], 0
    while i < len(pattern):
        if pattern.startswith("**/", i):
            out.append("(?:.*/)?")
            i += 3
        elif pattern.startswith("**", i):
            out.append(".*")
            i += 2
        elif pattern[i] == "*":
            out.append("[^/]*")
            i += 1
        elif pattern[i] == "?":
            out.append("[^/]")
            i += 1
        else:
            out.append(re.escape(pattern[i]))
            i += 1
    return "".join(out)


def glob_match(pattern, path):
    return re.fullmatch(glob_regex(pattern), path) is not None


def codeowners_regex(pattern):
    anchored = pattern.startswith("/") or "/" in pattern.strip("/")
    dir_only = pattern.endswith("/")
    core = pattern.strip("/")
    last = core.rsplit("/", 1)[-1]
    body = glob_regex(core)
    prefix = "" if anchored else "(?:.*/)?"
    if dir_only:
        suffix = "/.*"
    elif "*" in last and last != "**":
        suffix = ""
    else:
        suffix = "(?:/.*)?"
    return re.compile(prefix + body + suffix)


def git_z(root, *args):
    try:
        out = subprocess.run(["git", "-C", root] + list(args), stdout=subprocess.PIPE,
                             stderr=subprocess.PIPE, check=True).stdout
    except (OSError, subprocess.CalledProcessError) as error:
        raise ScanError("cannot list files with git in %s: %s" % (root, error))
    return [p for p in out.decode("utf-8", "surrogateescape").split("\0") if p]


def list_files(root):
    """Return (regular files, links). A link is a git mode 120000 entry, an on-disk
    symlink, or a submodule (git mode 160000); none of them is ever followed."""
    paths = sorted(set(git_z(root, "ls-files", "-z", "--cached", "--others", "--exclude-standard")))
    special = {}
    for entry in git_z(root, "ls-files", "-s", "-z"):
        mode, rest = entry.split(" ", 1)
        if mode in ("120000", "160000"):
            special[rest.split("\t", 1)[1]] = "submodule" if mode == "160000" else "symbolic link"
    files, links = [], []
    for path in paths:
        full = os.path.join(root, path)
        if path in special:
            links.append((path, special[path]))
        elif os.path.islink(full):
            links.append((path, "symbolic link"))
        elif os.path.isfile(full):
            files.append(path)
        elif os.path.isdir(full):
            links.append((path, "nested repository"))
    return files, links


def reasoned(mapping, key):
    """True when the policy lists key with a reason of 10+ characters."""
    return isinstance(mapping, dict) and len(str(mapping.get(key, "")).strip()) >= 10


def scope_exclusions(scope):
    patterns = []
    for entry in scope.get("exclude", []):
        if not isinstance(entry, dict) or not str(entry.get("path", "")).strip() \
                or len(str(entry.get("reason", "")).strip()) < 10:
            raise ScanError("every scope.exclude entry needs a 'path' and a 'reason' of 10+ characters: %r"
                            % (entry,))
        patterns.append(entry["path"])
    return patterns


def ifull(pattern, text):
    return re.fullmatch(glob_regex(pattern), text, re.IGNORECASE) is not None


def scope_match(path, scope):
    """The canonical spelling the path matched (a root segment, file name or path
    pattern), or None. Matching ignores case, as macOS file systems do."""
    segments = path.split("/")
    for segment in segments:
        for root_segment in scope.get("rootSegments", []):
            if ifull(root_segment, segment):
                return root_segment, segment
    for name in scope.get("fileNames", []):
        if ifull(name, segments[-1]):
            return name, segments[-1]
    for pattern in scope.get("paths", []):
        if ifull(pattern, path):
            return pattern, path
    return None


def in_scope(path, scope):
    if any(glob_match(p, path) for p in scope_exclusions(scope)):
        return False
    return scope_match(path, scope) is not None


DISPATCH_SEGMENTS = ["agents", "agent", "commands", "command", "plugins", "plugin", "tools", "tool", "skills",
                     "rules", "chatmodes", "prompts", "instructions", "settings*.json", "mcp.json", "hooks.json"]
TOOL_PARENTS = [".github", ".vscode"]


def canonical_path(path, scope):
    """The path with every known name spelled canonically: root segments, file names,
    .github/.vscode, and the type-selecting subdirectories below them. Case-insensitive
    file systems load the variant as the canonical file, so checks dispatch on this."""
    roots = scope.get("rootSegments", [])
    known = roots + scope.get("fileNames", []) + TOOL_PARENTS
    out, under_root = [], False
    for segment in path.split("/"):
        canonical = segment
        match = next((k for k in known if ifull(k, segment)), None)
        if match is not None:
            canonical = segment.lower() if any(c in match for c in "*?[") else match
            under_root = under_root or match in roots or match in TOOL_PARENTS
        elif under_root and any(ifull(d, segment) for d in DISPATCH_SEGMENTS):
            canonical = segment.lower()
        out.append(canonical)
    return "/".join(out)


def case_variant(path, scope):
    """The canonical spelling of a path that differs from it only by case, or None."""
    canonical = canonical_path(path, scope)
    if canonical != path:
        return canonical
    for pattern in scope.get("paths", []):
        if ifull(pattern, path) and not glob_match(pattern, path):
            return pattern
    return None


def read_text(root, path, limit=None):
    with open(os.path.join(root, path), "rb") as handle:
        data = handle.read() if limit is None else handle.read(limit + 1)
    if limit is not None and len(data) > limit:
        return None
    return data.decode("utf-8", "surrogateescape")


def position(text, index):
    if index < 0:
        return 1, 1
    line = text.count("\n", 0, index) + 1
    return line, index - (text.rfind("\n", 0, index) + 1) + 1


def line_starts(text):
    return [0] + [m.end() for m in re.finditer("\n", text)]


def position_in(starts, index):
    line = bisect.bisect_right(starts, index)
    return line, index - starts[line - 1] + 1


def locate(text, *needles):
    start, best = 0, -1
    for needle in needles:
        if needle is None:
            continue
        found = text.find(needle, start)
        if found < 0:
            break
        start = best = found
    return position(text, best)


def json_token(value):
    return json.dumps(value, ensure_ascii=False)


def strip_jsonc(text):
    out, i, n = [], 0, len(text)
    in_string = escaped = False
    while i < n:
        ch = text[i]
        if in_string:
            out.append(ch)
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == '"':
                in_string = False
            i += 1
        elif ch == '"':
            in_string = True
            out.append(ch)
            i += 1
        elif text.startswith("//", i):
            end = text.find("\n", i)
            end = n if end < 0 else end
            out.append(" " * (end - i))
            i = end
        elif text.startswith("/*", i):
            end = text.find("*/", i + 2)
            end = n if end < 0 else end + 2
            out.append("".join(c if c == "\n" else " " for c in text[i:end]))
            i = end
        else:
            out.append(ch)
            i += 1
    chars = list("".join(out))
    in_string = escaped = False
    for i, ch in enumerate(chars):
        if in_string:
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == '"':
                in_string = False
        elif ch == '"':
            in_string = True
        elif ch == ",":
            j = i + 1
            while j < len(chars) and chars[j] in " \t\r\n":
                j += 1
            if j < len(chars) and chars[j] in "}]":
                chars[i] = " "
    return "".join(chars)


def strict_object(pairs):
    data = {}
    for key, value in pairs:
        if key in data:
            raise ValueError("duplicate key %s" % json_token(key))
        data[key] = value
    return data


def reject_constant(name):
    raise ValueError("non-standard constant %s" % name)


def load_json(text, path, jsonc, findings):
    try:
        data = json.loads(strip_jsonc(text) if jsonc else text, object_pairs_hook=strict_object,
                          parse_constant=reject_constant)
    except ValueError as error:
        line = getattr(error, "lineno", 1)
        column = getattr(error, "colno", 1)
        findings.append(Finding("agent-config/unparseable-config", path, line, column,
                                "Not valid %s: %s." % ("JSONC" if jsonc else "JSON",
                                                       getattr(error, "msg", str(error))),
                                "parse"))
        return None
    if not isinstance(data, dict):
        findings.append(Finding("agent-config/unparseable-config", path, 1, 1,
                                "Top-level value must be a JSON object.", "parse"))
        return None
    return data


def dotted(data, dotted_key):
    node = data
    for part in dotted_key.split("."):
        if not isinstance(node, dict) or part not in node:
            return None
        node = node[part]
    return node


def flatten(node, prefix=""):
    flat = {}
    if isinstance(node, dict):
        for key, value in node.items():
            flat.update(flatten(value, prefix + "\u0000" + key if prefix else key))
    else:
        flat[prefix] = node
    return flat


def describe(cp):
    if 0xDC80 <= cp <= 0xDCFF:
        return "invalid UTF-8 byte 0x%02X" % (cp - 0xDC00)
    try:
        name = unicodedata.name(chr(cp))
    except ValueError:
        name = "category " + unicodedata.category(chr(cp))
    return "U+%04X %s" % (cp, name)


def is_hidden(cp, offset):
    if cp in (0x09, 0x0A, 0x0D):
        return False
    if cp == 0xFEFF and offset == 0:
        return False
    if cp in INVISIBLE_EXTRA or any(lo <= cp <= hi for lo, hi in DEFAULT_IGNORABLE_RANGES):
        return True
    return unicodedata.category(chr(cp)) in HIDDEN_CATEGORIES


def hidden_in(line, offset=1):
    """(column, code point) of each hidden character in one line. A variation
    selector U+FE0E/FE0F is visible only directly after a visible base character."""
    hits, previous_hidden = [], True
    for col, ch in enumerate(line, 1):
        cp = ord(ch)
        if cp in EMOJI_PRESENTATION_SELECTORS:
            hidden = previous_hidden or line[col - 2].isspace()
        elif ch == "\r":
            hidden = col != len(line)
        else:
            hidden = is_hidden(cp, offset + col - 1)
        if hidden:
            hits.append((col, cp))
        previous_hidden = hidden or cp in EMOJI_PRESENTATION_SELECTORS \
            or unicodedata.category(ch) in ("Mn", "Me", "Zs")
    return hits


def name_list(codepoints):
    seen = list(dict.fromkeys(codepoints))
    names = ", ".join(describe(cp) for cp in seen[:5])
    if len(seen) > 5:
        names += " and %d more" % (len(seen) - 5)
    return names


def check_hidden_unicode(path, text, findings):
    offset = 0
    for number, line in enumerate(text.split("\n"), 1):
        hits = hidden_in(line, offset)
        offset += len(line) + 1
        if hits:
            findings.append(Finding("agent-config/hidden-unicode", path, number, hits[0][0],
                                    "%d hidden or control character(s): %s."
                                    % (len(hits), name_list(cp for _, cp in hits)), line))


def check_decoded_strings(path, text, strings, findings):
    """Flag decoded string values (JSON or YAML escapes) that carry hidden characters
    which do not appear raw in the file, so the line scan could not see them."""
    for keys, value in strings:
        escaped = [cp for _, cp in hidden_in(value) if chr(cp) not in text]
        if escaped:
            line, column = locate(text, *['"%s"' % k for k in keys])
            findings.append(Finding("agent-config/hidden-unicode", path, line, column,
                                    "Escaped string at %s decodes to %d hidden or control character(s): %s."
                                    % (".".join(keys) or "(root)", len(escaped), name_list(escaped)),
                                    "escaped\u0000" + "\u0000".join(keys) + "\u0000" + value))


def json_strings(node, keys=()):
    if isinstance(node, dict):
        for key, value in node.items():
            yield list(keys) + [key], key
            for item in json_strings(value, tuple(keys) + (key,)):
                yield item
    elif isinstance(node, list):
        for value in node:
            for item in json_strings(value, keys):
                yield item
    elif isinstance(node, str):
        yield list(keys), node


def check_secrets(path, text, findings):
    for number, line in enumerate(text.split("\n"), 1):
        for label, pattern in SECRET_PATTERNS:
            for match in pattern.finditer(line):
                if "EXAMPLE" in match.group(0):
                    continue
                findings.append(Finding("agent-config/secret", path, number, match.start() + 1,
                                        "Possible %s. Revoke it and remove it from the file; "
                                        "the value is not reported." % label, label))
    starts = None
    for match in PRIVATE_KEY_BLOCK.finditer(text):
        starts = starts or line_starts(text)
        line, column = position_in(starts, match.start())
        findings.append(Finding("agent-config/secret", path, line, column,
                                "Possible private key. Revoke it and remove it from the file; "
                                "the value is not reported.", "private key"))


def risk_notes(command):
    notes = [label for label, pattern in RISKY_COMMAND_PATTERNS if pattern.search(command)]
    return (" It " + ", ".join(notes) + ".") if notes else ""


CLAUDE_PERMISSION_KEYS = {"allow", "ask", "deny", "additionalDirectories", "defaultMode"}
CLAUDE_HOOK_EVENTS = {"PreToolUse", "PostToolUse", "Notification", "UserPromptSubmit", "Stop",
                      "SubagentStop", "PreCompact", "SessionStart", "SessionEnd"}
CLAUDE_STATUS_LINE_KEYS = {"type", "command", "padding"}


def unrecognised_keys(path, text, mapping, handled, listed, where, needles, findings):
    """Report every key of mapping that no check handles and the policy does not list
    with a reason."""
    if not isinstance(mapping, dict):
        line, column = locate(text, *needles)
        findings.append(Finding("agent-config/unparseable-config", path, line, column,
                                "%s must be a JSON object." % where, "shape" + SEP + where))
        return
    for key in sorted(mapping):
        if key not in handled and not reasoned(listed, key):
            line, column = locate(text, *(list(needles) + ['"%s"' % key]))
            findings.append(Finding("agent-config/unrecognised", path, line, column,
                                    "Key %s in %s is not one the scanner checks. Add a check, or list it in "
                                    "the policy as known-safe with a reason." % (json_token(key), where),
                                    "key" + SEP + where + SEP + key))


def entry_list_of(value):
    return value if isinstance(value, list) else [value]


def check_claude_settings(path, text, data, policy, findings):
    claude = policy.get("claudeSettings", {})
    unrecognised_keys(path, text, data, CLAUDE_HANDLED_KEYS, claude.get("allowedKeys", {}),
                      "the settings file", [], findings)
    perms = data.get("permissions", {})
    unrecognised_keys(path, text, perms, CLAUDE_PERMISSION_KEYS, {}, "permissions", ['"permissions"'], findings)
    perms = perms if isinstance(perms, dict) else {}
    for list_key in ("allow", "ask", "additionalDirectories"):
        baseline = claude.get(list_key, [])
        for entry in entry_list_of(perms.get(list_key, [])):
            if entry not in baseline:
                line, column = locate(text, '"permissions"', '"%s"' % list_key, json_token(entry))
                findings.append(Finding("agent-config/permission-drift", path, line, column,
                                        "permissions.%s entry %s is not in the baseline."
                                        % (list_key, json_token(entry)), list_key + SEP + json_token(entry)))
    deny = entry_list_of(perms.get("deny", []))
    for required in claude.get("requiredDeny", []):
        if required not in deny:
            line, column = locate(text, '"permissions"', '"deny"' if "deny" in perms else None)
            findings.append(Finding("agent-config/permission-drift", path, line, column,
                                    "Required permissions.deny entry %s is missing."
                                    % json_token(required), "deny" + SEP + required))
    if "defaultMode" in perms and perms["defaultMode"] != claude.get("defaultMode"):
        line, column = locate(text, '"permissions"', '"defaultMode"')
        findings.append(Finding("agent-config/permission-drift", path, line, column,
                                "permissions.defaultMode %s differs from the baseline (%s)."
                                % (json_token(perms["defaultMode"]), json_token(claude.get("defaultMode"))),
                                "defaultMode"))
    for setting, forbidden in sorted(claude.get("forbiddenSettings", {}).items()):
        value = dotted(data, setting)
        if value is not None and value in forbidden:
            line, column = locate(text, *['"%s"' % p for p in setting.split(".")])
            findings.append(Finding("agent-config/permission-drift", path, line, column,
                                    "%s is set to %s, which the policy forbids."
                                    % (setting, json_token(value)), "setting" + SEP + setting))
    env = data.get("env", {})
    baseline_env = claude.get("env", {})
    for name in sorted(env) if isinstance(env, dict) else [""]:
        if not isinstance(env, dict) or name not in baseline_env or baseline_env[name] != env[name]:
            line, column = locate(text, '"env"', '"%s"' % name if name else None)
            findings.append(Finding("agent-config/permission-drift", path, line, column,
                                    "env.%s is not in the baseline env. Variables such as NODE_OPTIONS, "
                                    "LD_PRELOAD, PATH or *_BASE_URL run code or redirect model traffic; "
                                    "the value is not reported." % name, "env" + SEP + name))
    plugins = data.get("enabledPlugins", {})
    allowed_plugins = claude.get("enabledPlugins", [])
    for plugin in sorted(plugins) if isinstance(plugins, dict) else [json_token(plugins)]:
        if (not isinstance(plugins, dict) or plugins[plugin] is not False) and plugin not in allowed_plugins:
            line, column = locate(text, '"enabledPlugins"', '"%s"' % plugin)
            findings.append(Finding("agent-config/hook-not-allowlisted", path, line, column,
                                    "Plugin %s is enabled but not on the allowlist; plugins ship their own "
                                    "hooks, commands and MCP servers." % json_token(plugin),
                                    "plugin" + SEP + plugin))
    markets = data.get("extraKnownMarketplaces", {})
    allowed_markets = claude.get("extraKnownMarketplaces", {})
    for market in sorted(markets) if isinstance(markets, dict) else [json_token(markets)]:
        if not isinstance(markets, dict) or allowed_markets.get(market) != markets[market]:
            line, column = locate(text, '"extraKnownMarketplaces"', '"%s"' % market)
            findings.append(Finding("agent-config/hook-not-allowlisted", path, line, column,
                                    "Plugin marketplace %s is not on the allowlist with this exact source."
                                    % json_token(market), "marketplace" + SEP + market))
    allowed_mcpjson = policy.get("mcp", {}).get("enabledMcpjsonServers", [])
    for server in entry_list_of(data.get("enabledMcpjsonServers", [])):
        if server not in allowed_mcpjson:
            line, column = locate(text, '"enabledMcpjsonServers"', json_token(server))
            findings.append(Finding("agent-config/mcp-not-allowlisted", path, line, column,
                                    "enabledMcpjsonServers entry %s is not on the allowlist." % json_token(server),
                                    "mcpjson" + SEP + json_token(server)))


def check_command(path, text, command, where, allowed, findings, needles, allowlist="the hook allowlist"):
    if not isinstance(command, str):
        command = " ".join(str(part) for part in command) if isinstance(command, list) else json_token(command)
    if command in allowed:
        return
    line, column = locate(text, *needles)
    findings.append(Finding("agent-config/hook-not-allowlisted", path, line, column,
                            "%s runs a command that is not on %s: %s%s"
                            % (where, allowlist, json_token(command)[:200], risk_notes(command)),
                            where + SEP + command))


def check_claude_hooks(path, text, data, policy, findings):
    allowed = policy.get("hooks", {}).get("commands", [])
    hooks = data.get("hooks", {})
    unrecognised_keys(path, text, hooks, CLAUDE_HOOK_EVENTS, {}, "hooks", ['"hooks"'], findings)
    hooks = hooks if isinstance(hooks, dict) else {}
    for event in sorted(hooks):
        needles = ['"hooks"', '"%s"' % event]
        for group in entry_list_of(hooks[event]):
            unrecognised_keys(path, text, group, {"matcher", "hooks"}, {}, "a %s hook group" % event,
                              needles, findings)
            inner = group.get("hooks", []) if isinstance(group, dict) else []
            for hook in entry_list_of(inner):
                if not isinstance(hook, dict) or hook.get("type", "command") != "command" \
                        or not isinstance(hook.get("command"), str) or set(hook) - {"type", "command", "timeout"}:
                    line, column = locate(text, *needles)
                    findings.append(Finding("agent-config/hook-not-allowlisted", path, line, column,
                                            "A %s hook is not a plain command hook (type %s, keys %s); only "
                                            "allowlisted command hooks are accepted."
                                            % (event, json_token(hook.get("type") if isinstance(hook, dict) else None),
                                               ", ".join(sorted(hook)) if isinstance(hook, dict) else "none"),
                                            "hook-shape" + SEP + event + SEP + canonical(hook)))
                    continue
                check_command(path, text, hook["command"], "Hook %s" % event, allowed, findings,
                              needles + [json_token(hook["command"])])
    status_line = data.get("statusLine")
    if status_line is not None:
        unrecognised_keys(path, text, status_line, CLAUDE_STATUS_LINE_KEYS, {}, "statusLine",
                          ['"statusLine"'], findings)
    for key in CLAUDE_COMMAND_KEYS:
        value = dotted(data, key)
        if value is not None:
            check_command(path, text, value, "Setting %s" % key, allowed, findings,
                          ['"%s"' % p for p in key.split(".")] + [json_token(value)])


class FrontmatterError(Exception):
    pass


FM_KEY = re.compile(r"^([A-Za-z0-9_][A-Za-z0-9_-]*):(?:[ ]+(.*?))?[ ]*$")
FM_BLOCK_SCALAR = re.compile(r"^[|>](?:[-+]?[1-9]?|[1-9][-+])(?:[ ]+#.*)?$")
FM_PLAIN_FORBIDDEN_START = set("&*!{}[]|>?%@`,#'\"")


def frontmatter(text):
    """The frontmatter lines (without the --- fences), or None when the file has none."""
    lines = (text[1:] if text.startswith("\ufeff") else text).split("\n")
    lines = [line[:-1] if line.endswith("\r") else line for line in lines]
    if lines[0].startswith(("---", "+++")) and lines[0].rstrip() != "---":
        raise FrontmatterError("the opening fence %r selects a non-YAML or other-language frontmatter engine"
                               % lines[0][:20])
    if lines[0].rstrip() != "---":
        return None
    for end in range(1, len(lines)):
        if lines[end].rstrip() == "---":
            return lines[1:end]
    raise FrontmatterError("the frontmatter opened with '---' is never closed")


def parse_scalar(raw, where):
    raw = raw.strip()
    if raw.startswith('"'):
        end = raw.find('"', 1)
        while end > 0 and raw[end - 1] == "\\":
            end = raw.find('"', end + 1)
        if end < 0 or raw[end + 1:].strip()[:1] not in ("", "#"):
            raise FrontmatterError("%s: unsupported double-quoted scalar" % where)
        try:
            return json.loads(raw[:end + 1])
        except ValueError:
            raise FrontmatterError("%s: unsupported escape in a double-quoted scalar" % where)
    if raw.startswith("'"):
        match = re.match(r"^'((?:[^']|'')*)'\s*(?:#.*)?$", raw)
        if not match:
            raise FrontmatterError("%s: unsupported single-quoted scalar" % where)
        return match.group(1).replace("''", "'")
    value = re.sub(r"\s+#.*$", "", raw)
    if value[:1] in FM_PLAIN_FORBIDDEN_START or value.startswith("- ") or ": " in value or value.endswith(":"):
        raise FrontmatterError("%s: value %r uses YAML syntax outside the supported subset "
                               "(anchors, aliases, tags, flow mappings, nested keys)" % (where, value[:40]))
    return value


def parse_frontmatter(lines):
    """Parse the supported YAML subset: a block mapping of plain keys whose values are
    plain or quoted scalars, block scalars (| or >), block lists or one-level flow lists
    of scalars. Anything else raises FrontmatterError, so unusual YAML fails closed."""
    entries, i = {}, 0
    while i < len(lines):
        line = lines[i]
        number = i + 2
        if not line.strip() or line.startswith("#"):
            i += 1
            continue
        match = FM_KEY.match(line)
        if not match:
            raise FrontmatterError("line %d is not a plain 'key: value' entry" % number)
        key, raw = match.group(1), (match.group(2) or "")
        if key in entries:
            raise FrontmatterError("key %s appears more than once" % key)
        where = "line %d (%s)" % (number, key)
        i += 1
        stripped = re.sub(r"^\s*#.*$", "", raw).strip()
        if not stripped:
            items = []
            while i < len(lines) and (not lines[i].strip() or lines[i][:1] in (" ", "\t")):
                item = lines[i]
                i += 1
                if not item.strip() or item.lstrip().startswith("#"):
                    continue
                if "\t" in item[:len(item) - len(item.lstrip())]:
                    raise FrontmatterError("line %d is indented with a tab" % (i + 1))
                listed = re.match(r"^ +- +(\S.*)$", item)
                if not listed:
                    raise FrontmatterError("line %d under %s is a nested mapping or other unsupported "
                                           "form" % (i + 1, key))
                items.append(parse_scalar(listed.group(1), "line %d" % (i + 1)))
            entries[key] = (number, "list" if items else "null", items)
        elif FM_BLOCK_SCALAR.match(stripped):
            body = []
            while i < len(lines) and (not lines[i].strip() or lines[i][:1] == " "):
                body.append(lines[i].strip())
                i += 1
            entries[key] = (number, "block", " ".join(b for b in body if b))
        elif stripped.startswith("["):
            collected = stripped
            while "]" not in collected:
                if i >= len(lines) or lines[i][:1] not in (" ", "\t"):
                    raise FrontmatterError("%s: the flow list is never closed" % where)
                collected += " " + lines[i].strip()
                i += 1
            close = collected.index("]")
            if collected[close + 1:].strip()[:1] not in ("", "#"):
                raise FrontmatterError("%s: unexpected text after the flow list" % where)
            entries[key] = (number, "list", [parse_scalar(item, where) for item in collected[1:close].split(",")
                                             if item.strip()])
        else:
            entries[key] = (number, "scalar", parse_scalar(raw, where))
    return entries


def entry_list(entries, key):
    number, kind, value = entries[key]
    if kind == "list":
        return number, value
    if kind == "null":
        return number, []
    if kind == "scalar":
        return number, [item.strip() for item in value.split(",") if item.strip()]
    raise FrontmatterError("line %d: %s must be a list or a comma-separated scalar" % (number, key))


FRONTMATTER_SAFE_KEYS = {
    "name": "display name only",
    "description": "prose shown to the user and the model; scanned for hidden characters and secrets",
    "argument-hint": "placeholder text for command arguments",
    "license": "licence metadata",
    "color": "display colour",
}
FRONTMATTER_CAPABILITIES = {
    "hooks": ("agent-config/hook-not-allowlisted", "defines hooks, which run commands"),
    "mcpServers": ("agent-config/mcp-not-allowlisted", "defines MCP servers"),
    "mcp-servers": ("agent-config/mcp-not-allowlisted", "defines MCP servers"),
    "permissionMode": ("agent-config/permission-drift", "changes the permission mode"),
    "tools": ("agent-config/permission-drift", "sets the tools this file may use"),
    "permission": ("agent-config/permission-drift", "overrides permissions outside the main configuration"),
    "allowed-tools": ("agent-config/permission-drift", "grants tools without a prompt"),
}


def check_frontmatter(path, canonical, text, policy, findings):
    try:
        lines = frontmatter(text)
        if lines is None:
            if path in policy.get("claudeAgentTools", {}):
                findings.append(Finding("agent-config/permission-drift", path, 1, 1,
                                        "The frontmatter, and with it the 'tools' restriction, was "
                                        "removed, so this agent now inherits every tool.", "tools-removed"))
            return
        entries = parse_frontmatter(lines)
        strings = []
        for key, (_, kind, value) in entries.items():
            for item in value if isinstance(value, list) else [value]:
                if isinstance(item, str):
                    strings.append(([key], item))
        check_decoded_strings(path, text, strings, findings)
        claude_agent = glob_match(CLAUDE_AGENTS_GLOB, canonical)
        allowed = policy.get("frontmatterAllow", {}).get(path, [])
        for key in sorted(entries):
            if key in FRONTMATTER_SAFE_KEYS or reasoned(policy.get("frontmatterKnownKeys", {}), key) \
                    or key in allowed or (claude_agent and key in ("tools", "permissionMode")):
                continue
            rule, why = FRONTMATTER_CAPABILITIES.get(key, ("agent-config/unrecognised", None))
            message = ("Frontmatter key %s %s; it is not allowed for this file in the policy's frontmatterAllow."
                       % (key, why)) if why else \
                ("Frontmatter key %s is not one the scanner understands. List it in the policy's "
                 "frontmatterKnownKeys with a reason if it is inert, or in frontmatterAllow for this file." % key)
            findings.append(Finding(rule, path, entries[key][0], 1, message, "frontmatter" + SEP + key))
        if claude_agent:
            check_claude_agent(path, entries, policy, findings)
    except FrontmatterError as error:
        findings.append(Finding("agent-config/unparseable-config", path, 1, 1,
                                "Frontmatter is outside the supported YAML subset: %s." % error, "frontmatter"))


def check_claude_agent(path, entries, policy, findings):
    if "permissionMode" in entries and (entries["permissionMode"][2] == "bypassPermissions"
                                        or "permissionMode" not in policy.get("frontmatterAllow", {}).get(path, [])):
        findings.append(Finding("agent-config/permission-drift", path, entries["permissionMode"][0], 1,
                                "Agent sets permissionMode %s; only an entry in frontmatterAllow (never "
                                "bypassPermissions) is accepted." % json_token(entries["permissionMode"][2]),
                                "permissionMode"))
    baseline = policy.get("claudeAgentTools", {}).get(path)
    if baseline is None:
        return
    if "tools" not in entries:
        findings.append(Finding("agent-config/permission-drift", path, 1, 1,
                                "The 'tools' restriction was removed, so this agent now inherits "
                                "every tool. Baseline: %s." % ", ".join(baseline), "tools-removed"))
        return
    line, tools = entry_list(entries, "tools")
    for tool in tools:
        if tool not in baseline:
            findings.append(Finding("agent-config/permission-drift", path, line, 1,
                                    "Tool %s is not in this agent's baseline tool list." % json_token(tool),
                                    "tool\u0000" + tool))


def check_opencode(path, text, data, policy, findings):
    opencode = policy.get("opencode", {})
    actual = flatten(data.get("permission", {})) if isinstance(data.get("permission"), dict) else {}
    if "permission" in data and not isinstance(data.get("permission"), dict):
        actual = {"": data["permission"]}
    baseline = flatten(opencode.get("permission", {}))
    for key in sorted(actual):
        if key not in baseline or baseline[key] != actual[key]:
            parts = [p for p in key.split("\u0000") if p]
            line, column = locate(text, '"permission"', *['"%s"' % p for p in parts])
            findings.append(Finding("agent-config/permission-drift", path, line, column,
                                    "permission.%s = %s differs from the baseline (%s)."
                                    % (".".join(parts), json_token(actual[key]),
                                       json_token(baseline[key]) if key in baseline else "absent"),
                                    "permission\u0000" + key))
    for key in sorted(set(baseline) - set(actual)):
        parts = key.split("\u0000")
        line, column = locate(text, '"permission"' if "permission" in data else None)
        findings.append(Finding("agent-config/permission-drift", path, line, column,
                                "Baseline permission.%s = %s is missing."
                                % (".".join(parts), json_token(baseline[key])),
                                "permission-missing\u0000" + key))
    if "agent" in data and not isinstance(data["agent"], dict):
        findings.append(Finding("agent-config/unparseable-config", path, *locate(text, '"agent"'),
                                message="agent must be an object that maps agent names to agents.", key="agent-shape"))
    agents = data.get("agent", {}) if isinstance(data.get("agent"), dict) else {}
    for name in sorted(agents):
        unrecognised_keys(path, text, agents[name], OPENCODE_AGENT_HANDLED_KEYS,
                          opencode.get("agentAllowedKeys", {}), "agent.%s" % name, ['"agent"', '"%s"' % name],
                          findings)
    for name, restricted in sorted(opencode.get("agentTools", {}).items()):
        cfg = agents.get(name)
        if not isinstance(cfg, dict):
            continue
        tools = cfg.get("tools", {}) if isinstance(cfg.get("tools"), dict) else {}
        for tool, enabled in sorted(restricted.items()):
            if enabled is False and tools.get(tool, True) is not False:
                line, column = locate(text, '"agent"', '"%s"' % name)
                findings.append(Finding("agent-config/permission-drift", path, line, column,
                                        "Agent %s must keep tools.%s = false (baseline)." % (name, tool),
                                        "agent-tool\u0000" + name + "\u0000" + tool))
    agent_permission = opencode.get("agentPermission", {})
    for name in sorted(agents):
        cfg = agents[name]
        if isinstance(cfg, dict) and "permission" in cfg:
            if flatten(cfg["permission"]) != flatten(agent_permission.get(name, {})):
                line, column = locate(text, '"agent"', '"%s"' % name, '"permission"')
                findings.append(Finding("agent-config/permission-drift", path, line, column,
                                        "Agent %s permission map differs from the baseline." % name,
                                        "agent-permission\u0000" + name))
    allowed_plugins = set(opencode.get("plugins", []))
    plugins = data.get("plugin", [])
    for plugin in plugins if isinstance(plugins, list) else [plugins]:
        line, column = locate(text, '"plugin"', json_token(plugin))
        if plugin not in allowed_plugins:
            findings.append(Finding("agent-config/hook-not-allowlisted", path, line, column,
                                    "opencode plugin package %s is not on the allowlist." % json_token(plugin),
                                    "plugin\u0000" + str(plugin)))
        if isinstance(plugin, str) and not plugin.startswith((".", "/", "file:")) \
                and not NPM_PINNED.match(plugin):
            findings.append(Finding("agent-config/unpinned-package", path, line, column,
                                    "opencode plugin package %s has no exact version." % json_token(plugin),
                                    "plugin\u0000" + plugin))
    check_opencode_keys(path, text, data, policy, findings)


OPENCODE_AGENT_HANDLED_KEYS = {"tools", "permission", "prompt"}
OPENCODE_HANDLED_KEYS = {"permission", "agent", "mcp", "plugin", "experimental", "instructions",
                         "formatter", "lsp"}


def command_strings(node):
    """Every 'command' value found anywhere inside node, as a display string."""
    if isinstance(node, dict):
        for key, value in node.items():
            if key == "command":
                yield " ".join(str(v) for v in value) if isinstance(value, list) else str(value)
            else:
                for item in command_strings(value):
                    yield item
    elif isinstance(node, list):
        for value in node:
            for item in command_strings(value):
                yield item


VSCODE_DANGEROUS = re.compile(r"auto-?approve|allowedcommands|alwaysallow|auto-?execute|dangerous|skip-?permission|yolo",
                              re.IGNORECASE)


def vscode_leaves(node, prefix, scope, leaves, problems):
    """Flatten VS Code settings to (dotted key, value, scope) leaves. Keys are split on
    '.' as VS Code does, so dotted and nested spellings of a setting are the same leaf;
    '[lang]' override blocks are flattened in their own scope; mcp.servers stays whole."""
    for key, value in node.items():
        if not prefix and key.startswith("[") and key.endswith("]"):
            if scope or not isinstance(value, dict):
                problems.append("language override %s must be a top-level object" % key)
                continue
            vscode_leaves(value, "", key, leaves, problems)
            continue
        full = (prefix + "." + key) if prefix else key
        if full.lower() == "mcp.servers" or not isinstance(value, dict) or not value:
            leaves.append((full, value, scope))
        else:
            vscode_leaves(value, full, scope, leaves, problems)


def check_vscode_settings(path, text, data, policy, findings, needles=()):
    """Closed-world check of VS Code settings; returns the MCP server maps it found.
    needles locate the settings object inside a larger file (a workspace or devcontainer)."""
    vscode = policy.get("vscodeSettings", {})
    ai_prefixes = [p.lower() for p in vscode.get("aiKeyPrefixes", [])]
    inert_prefixes = vscode.get("inertKeyPrefixes", {})
    inert_keys = vscode.get("inertKeys", {})
    exact = vscode.get("exactKeys", {})
    leaves, problems = [], []
    vscode_leaves(data, "", "", leaves, problems)
    seen, servers = {}, []
    for full, value, scope in leaves:
        identity = (scope, full.lower())
        if identity in seen:
            problems.append("%s is set more than once (dotted and nested spellings are the same setting)" % full)
        seen[identity] = True
    for problem in problems:
        findings.append(Finding("agent-config/unparseable-config", path, 1, 1, "VS Code settings: %s." % problem,
                                "vscode-shape" + SEP + problem))
    for full, value, scope in leaves:
        lower = full.lower()
        where = (scope + " " if scope else "") + full
        line, column = locate(text, *(list(needles) + ['"%s"' % full.split(".")[0]]))
        if VSCODE_DANGEROUS.search(full) and value not in (False, None, {}, [], ""):
            findings.append(Finding("agent-config/permission-drift", path, line, column,
                                    "%s auto-approves or auto-runs agent actions without a prompt." % where,
                                    "vscode-danger" + SEP + where))
        elif lower == "mcp.servers":
            servers.append(value)
        elif full in exact:
            if canonical(value) != canonical(exact[full]):
                findings.append(Finding("agent-config/permission-drift", path, line, column,
                                        "%s differs from vscodeSettings.exactKeys." % where, "vscode-exact" + SEP + where))
        elif reasoned(vscode.get("allowedKeys", {}), full):
            continue
        elif not any(lower.startswith(p) for p in ai_prefixes) and (
                reasoned(inert_keys, full) or any(full.startswith(p) and reasoned(inert_prefixes, p)
                                                  for p in inert_prefixes)):
            continue
        else:
            findings.append(Finding("agent-config/unrecognised", path, line, column,
                                    "Setting %s is not one the scanner understands. List it in vscodeSettings."
                                    "allowedKeys (AI settings) or inertKeys/inertKeyPrefixes (editor settings) "
                                    "with a reason." % json_token(where), "vscode" + SEP + where))
    return servers


def check_vscode_block(path, canonical_name, text, settings, where, needles, policy, findings):
    """A VS Code settings object, top-level or nested, through the settings and MCP rules."""
    if not isinstance(settings, dict):
        line, column = locate(text, *needles)
        findings.append(Finding("agent-config/unparseable-config", path, line, column,
                                "%s must be a JSON object." % where, "shape" + SEP + where))
        return
    for servers in check_vscode_settings(path, text, settings, policy, findings, needles):
        check_mcp(path, canonical_name, "vscode-settings", text, {"mcp": {"servers": servers}}, policy, findings)


def check_code_workspace(path, canonical_name, text, data, policy, findings, files):
    """A .code-workspace file: settings as .vscode/settings.json, folders inside the repository."""
    unrecognised_keys(path, text, data, CODE_WORKSPACE_HANDLED_KEYS,
                      policy.get("codeWorkspace", {}).get("allowedKeys", {}), "the workspace file", [], findings)
    if "settings" in data:
        check_vscode_block(path, canonical_name, text, data["settings"], "settings", ['"settings"'],
                           policy, findings)
    if "extensions" in data:
        extensions = data["extensions"]
        unrecognised_keys(path, text, extensions, {"recommendations"}, {}, "extensions", ['"extensions"'], findings)
        recommended = extensions.get("recommendations", []) if isinstance(extensions, dict) else []
        if not isinstance(recommended, list) or not all(isinstance(r, str) for r in recommended):
            findings.append(Finding("agent-config/unparseable-config", path,
                                    *locate(text, '"extensions"', '"recommendations"'),
                                    message="extensions.recommendations must be a list of extension ids.",
                                    key="shape" + SEP + "recommendations"))
    folders = data.get("folders", [])
    if not isinstance(folders, list):
        findings.append(Finding("agent-config/unparseable-config", path, *locate(text, '"folders"'),
                                message="folders must be a list.", key="shape" + SEP + "folders"))
        folders = []
    exceptions = policy.get("referenceExceptions", {}).get(path, {})
    for folder in folders:
        unrecognised_keys(path, text, folder, {"path", "name", "uri"}, {}, "a workspace folder", ['"folders"'],
                          findings)
        if not isinstance(folder, dict):
            continue
        if "uri" not in folder and not (isinstance(folder.get("path"), str) and folder["path"]):
            findings.append(Finding("agent-config/unparseable-config", path, *locate(text, '"folders"'),
                                    message="Each workspace folder needs a non-empty path or a uri.",
                                    key="shape" + SEP + "folder" + SEP + canonical(folder)))
            continue
        target = folder.get("uri", folder.get("path"))
        why = workspace_folder_problem(posixpath.dirname(path), folder, files)
        if why and not reasoned(exceptions, str(target)):
            findings.append(Finding("agent-config/external-reference", path,
                                    *locate(text, '"folders"', json_token(target)),
                                    message="Workspace folder %s %s, so its .vscode settings and MCP servers are "
                                            "not scanned. List it under referenceExceptions for this file with a "
                                            "reason if it is deliberate." % (json_token(target)[:200], why),
                                    key="folder" + SEP + str(target)))


def workspace_folder_problem(base_dir, folder, files):
    """Why the scanner cannot follow a workspace folder entry, or None when it is a directory of
    discovered files. A missing or gitignored folder is unscanned, so it is a problem too."""
    if "uri" in folder:
        return "is a uri"
    target = folder["path"]
    if re.match(r"^[A-Za-z][A-Za-z0-9+.-]*:", target) or target.startswith(("/", "\\", "~")):
        return "is not a relative path"
    resolved = posixpath.normpath(posixpath.join(base_dir, target.replace("\\", "/")))
    if resolved == ".." or resolved.startswith("../"):
        return "points outside the repository"
    if resolved != "." and not any(f.startswith(resolved + "/") for f in files):
        return "does not resolve to a directory of tracked or unignored files in the repository"
    return None


def check_devcontainer_image(path, text, image, allowed, findings):
    """The image runs with the workspace mounted read-write, so it must be digest-pinned or allowlisted."""
    line, column = locate(text, '"image"')
    if not isinstance(image, str) or not image.strip():
        findings.append(Finding("agent-config/unparseable-config", path, line, column,
                                "image must be a non-empty string.", "shape" + SEP + "image"))
    elif not IMAGE_DIGEST.search(image) and image not in allowed:
        findings.append(Finding("agent-config/unpinned-package", path, line, column,
                                "devcontainer image %s is not pinned by @sha256 digest and is not a "
                                "devcontainer.images entry; it runs with the workspace mounted read-write."
                                % json_token(image)[:200], "image" + SEP + image))


def lifecycle_commands(value):
    """(suffix, command) for each command in a devcontainer lifecycle value: a string,
    which a shell runs, an argv list, or an object of named strings or argv lists."""
    def one(suffix, item):
        if isinstance(item, str) or isinstance(item, list) and item and all(isinstance(a, str) for a in item):
            return [(suffix, item)]
        raise ShapeError("must be a string, a non-empty list of strings or an object of those")
    if isinstance(value, dict) and value:
        return [c for name in sorted(value) for c in one("." + name, value[name])]
    return one("", value)


def check_devcontainer(path, canonical_name, text, data, policy, findings):
    """A devcontainer.json: lifecycle commands through the command and launcher allowlists,
    customizations.vscode.settings as VS Code settings, every other key inert or unrecognised."""
    devcontainer = policy.get("devcontainer", {})
    unrecognised_keys(path, text, data, DEVCONTAINER_HANDLED_KEYS, devcontainer.get("inertKeys", {}),
                      "the devcontainer configuration", [], findings)
    if "image" in data:
        check_devcontainer_image(path, text, data["image"], devcontainer.get("images", []), findings)
    allowed = devcontainer.get("commands", [])
    local_commands = policy.get("mcp", {}).get("localCommands", [])
    for key in DEVCONTAINER_LIFECYCLE_KEYS:
        if key not in data:
            continue
        needles = ['"%s"' % key]
        try:
            commands = lifecycle_commands(data[key])
        except ShapeError as error:
            findings.append(Finding("agent-config/unparseable-config", path, *locate(text, *needles),
                                    message="%s %s." % (key, error), key="shape" + SEP + key))
            continue
        for suffix, command in commands:
            where = "devcontainer %s%s" % (key, suffix)
            check_command(path, text, command, where, allowed, findings, needles, "devcontainer.commands")
            argv = ["sh", "-c", command] if isinstance(command, str) else command
            reasons = unpinned_reasons(argv[0], argv[1:], local_commands)
            if reasons:
                findings.append(Finding("agent-config/unpinned-package", path, *locate(text, *needles),
                                        message="%s is not pinned: %s." % (where, "; ".join(reasons)),
                                        key=where + SEP + canonical(command)))
    if "customizations" not in data:
        return
    customizations = data["customizations"]
    unrecognised_keys(path, text, customizations, {"vscode"}, {}, "customizations", ['"customizations"'], findings)
    vscode = customizations.get("vscode") if isinstance(customizations, dict) else None
    if vscode is None:
        return
    needles = ['"customizations"', '"vscode"']
    unrecognised_keys(path, text, vscode, {"settings"}, {}, "customizations.vscode", needles, findings)
    if isinstance(vscode, dict) and "settings" in vscode:
        check_vscode_block(path, canonical_name, text, vscode["settings"], "customizations.vscode.settings",
                           needles + ['"settings"'], policy, findings)


def check_opencode_keys(path, text, data, policy, findings):
    opencode = policy.get("opencode", {})
    exact = opencode.get("exactKeys", {})
    unrecognised_keys(path, text, data, OPENCODE_HANDLED_KEYS | set(exact), opencode.get("allowedKeys", {}),
                      "the opencode configuration", [], findings)
    for key in sorted(set(exact) & set(data)):
        if canonical(data[key]) != canonical(exact[key]):
            line, column = locate(text, '"%s"' % key)
            findings.append(Finding("agent-config/permission-drift", path, line, column,
                                    "%s differs from the baseline in the policy's opencode.exactKeys." % key,
                                    "exact" + SEP + key))
    experimental = data.get("experimental", {})
    unrecognised_keys(path, text, experimental, {"hook"}, opencode.get("experimentalAllowedKeys", {}),
                      "experimental", ['"experimental"'], findings)
    hook_root = experimental.get("hook", {}) if isinstance(experimental, dict) else {}
    hook_baseline = opencode.get("experimentalHook", {})
    allowed = policy.get("hooks", {}).get("commands", [])
    for event in sorted(hook_root) if isinstance(hook_root, dict) else []:
        needles = ['"experimental"', '"hook"', '"%s"' % event]
        if canonical(hook_root[event]) != canonical(hook_baseline.get(event)):
            line, column = locate(text, *needles)
            findings.append(Finding("agent-config/hook-not-allowlisted", path, line, column,
                                    "opencode hook %s differs from the policy's opencode.experimentalHook "
                                    "baseline." % event, "oc-hook" + SEP + event + SEP + canonical(hook_root[event])))
        for command in command_strings(hook_root[event]):
            check_command(path, text, command, "opencode hook %s" % event, allowed, findings, needles)
    local_commands = policy.get("mcp", {}).get("localCommands", [])
    for section in ("formatter", "lsp"):
        value = data.get(section)
        if value is None or value is False:
            continue
        baseline = opencode.get(section, {})
        if not isinstance(value, dict):
            findings.append(Finding("agent-config/unparseable-config", path, *locate(text, '"%s"' % section),
                                    message="%s must be false or an object." % section, key=section))
            continue
        for name in sorted(value):
            entry = value[name]
            line, column = locate(text, '"%s"' % section, '"%s"' % name)
            if canonical(entry) != canonical(baseline.get(name)):
                findings.append(Finding("agent-config/hook-not-allowlisted", path, line, column,
                                        "opencode %s %s runs a program and is not an exact opencode.%s entry."
                                        % (section, json_token(name), section),
                                        section + SEP + name + SEP + canonical(entry)))
            command = entry.get("command") if isinstance(entry, dict) else None
            if isinstance(command, list) and command:
                reasons = unpinned_reasons(str(command[0]), [str(c) for c in command[1:]], local_commands)
                if reasons:
                    findings.append(Finding("agent-config/unpinned-package", path, line, column,
                                            "opencode %s %s is not pinned: %s."
                                            % (section, json_token(name), "; ".join(reasons)),
                                            section + SEP + name))


def package_argument(args, value_flags):
    for i, arg in enumerate(args):
        if arg in value_flags and i + 1 < len(args):
            return args[i + 1]
        for flag in value_flags:
            if arg.startswith(flag + "="):
                return arg.split("=", 1)[1]
    for arg in args:
        if not arg.startswith("-"):
            return arg
    return None


ENV_VALUE_FLAGS = {"-u", "--unset", "-C", "--chdir"}
DOCKER_BOOLEAN_FLAGS = {"-i", "-t", "-it", "-ti", "--rm", "--init", "--interactive", "--tty"}
DOCKER_VALUE_FLAGS = {"-e", "--env", "--env-file", "-v", "--volume", "--mount", "--network", "--name",
                      "-w", "--workdir", "-u", "--user", "-p", "--publish"}
SHELL_SUBSTITUTION = re.compile(r"\$\(|`|<\(|>\(|\$\{")


def command_segments(command, args, depth=0):
    """Unwrap the launchers the scanner understands (env, sh -c, cmd /c and a
    command string) into the leaf commands they run."""
    if depth > 5:
        raise ValueError("wrapper nesting is too deep")
    if not args and re.search(r"\s", command):
        parts = shlex.split(command)
        if not parts:
            return []
        command, args = parts[0], parts[1:]
    base = os.path.basename(command).lower()
    base = base[:-4] if base.endswith(".exe") else base
    if base == "env":
        i = 0
        while i < len(args):
            arg = args[i]
            if arg in ("-S", "--split-string") and i + 1 < len(args):
                rest = shlex.split(args[i + 1]) + args[i + 2:]
                return command_segments(rest[0], rest[1:], depth + 1) if rest else []
            if arg == "--":
                i += 1
                break
            if arg in ENV_VALUE_FLAGS:
                i += 2
            elif arg.startswith("-") or "=" in arg:
                i += 1
            else:
                break
        rest = args[i:]
        return command_segments(rest[0], rest[1:], depth + 1) if rest else [(command, args)]
    if base == "cmd" and args and args[0].lower() in ("/c", "/k"):
        return script_segments(" ".join(args[1:]), depth)
    if base in SHELLS and len(args) == 2 and re.fullmatch(r"-[a-z]*c[a-z]*", args[0]):
        return script_segments(args[1], depth)
    return [(command, args)]


def script_segments(script, depth):
    if SHELL_SUBSTITUTION.search(script):
        raise ValueError("the shell script uses command or parameter substitution")
    out = []
    for part in re.split(r"&&|\|\||[;|&\n]", script):
        tokens = shlex.split(part)
        if tokens:
            out += command_segments(tokens[0], tokens[1:], depth + 1)
    return out


def unpinned_reasons(command, args, local_commands):
    reasons = []
    if REMOTE_SCRIPT_PIPE.search(" ".join([command] + args)):
        reasons.append("pipes a downloaded script into a shell")
    try:
        segments = command_segments(command, args)
    except ValueError as error:
        segments = []
        reasons.append("the wrapped command could not be parsed (%s)" % error)
    for segment_command, segment_args in segments:
        for reason in package_reasons(segment_command, segment_args, local_commands):
            if reason not in reasons:
                reasons.append(reason)
    return reasons


def docker_image(args):
    i = 1
    while i < len(args):
        arg = args[i]
        if arg in DOCKER_BOOLEAN_FLAGS or (arg.startswith("--") and "=" in arg):
            i += 1
        elif arg in DOCKER_VALUE_FLAGS:
            i += 2
        elif arg.startswith("-"):
            raise ValueError("docker run option %s is not supported by the scanner" % arg)
        else:
            return arg
    raise ValueError("docker run names no image")


def package_reasons(command, args, local_commands):
    base = os.path.basename(command).lower()
    base = base[:-4] if base.endswith((".exe", ".cmd")) else base
    reasons = []
    if any(a == "latest" or a.endswith(("@latest", ":latest")) for a in args):
        reasons.append("uses 'latest'")
    if base in NPM_RUNNERS or (base in NPM_DLX and args[:1] and args[0] in NPM_DLX[base]):
        package = package_argument(args if base in NPM_RUNNERS else args[1:], ("-p", "--package"))
        if package is None or not NPM_PINNED.match(package):
            reasons.append("npm package %s has no exact version" % json_token(package))
    elif base == "uvx" or (base == "pipx" and args[:1] == ["run"]) or (base == "uv" and args[:2] == ["tool", "run"]):
        rest = {"uvx": args, "pipx": args[1:], "uv": args[2:]}[base]
        package = package_argument(rest, ("--from", "--spec"))
        if package is None or not PY_PINNED.match(package):
            reasons.append("Python package %s has no exact version" % json_token(package))
    elif base in CONTAINER_RUNNERS and args[:1] == ["run"]:
        try:
            if not IMAGE_DIGEST.search(docker_image(args)):
                reasons.append("container image is not pinned by @sha256 digest")
        except ValueError as error:
            reasons.append(str(error))
    elif base not in local_commands:
        reasons.append("launcher %s is not a recognised package runner or an mcp.localCommands entry"
                       % json_token(command))
    return reasons


class ShapeError(Exception):
    pass


def mcp_servers(kind, canonical_name, data):
    """(name, command, args, url, raw config) for each MCP server the file declares."""
    opencode = kind == "opencode"
    if opencode:
        where, servers = "mcp", data.get("mcp")
    elif kind == "vscode-settings":
        container = data.get("mcp")
        where, servers = "mcp.servers", container.get("servers") if isinstance(container, dict) else None
    elif kind == "mcp" and glob_match("**/.vscode/mcp.json", canonical_name):
        where, servers = "servers", data.get("servers")
    else:
        where, servers = "mcpServers", data.get("mcpServers")
    if servers is None:
        return []
    if not isinstance(servers, dict):
        raise ShapeError("%s must be an object that maps server names to servers" % where)
    out = []
    for name, cfg in sorted(servers.items()):
        if not isinstance(cfg, dict):
            out.append((name, None, [], None, cfg))
            continue
        command = cfg.get("command")
        if opencode and isinstance(command, list) and command:
            out.append((name, str(command[0]), [str(a) for a in command[1:]], cfg.get("url"), cfg))
            continue
        args = cfg.get("args", [])
        out.append((name, command if isinstance(command, str) else None,
                    [str(a) for a in args] if isinstance(args, list) else [str(args)], cfg.get("url"), cfg))
    return out


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=True)


def check_mcp(path, canonical_name, kind, text, data, policy, findings):
    mcp = policy.get("mcp", {})
    allowed = {canonical(entry) for entry in mcp.get("allowedServers", [])}
    local_commands = mcp.get("localCommands", [])
    try:
        servers = mcp_servers(kind, canonical_name, data)
    except ShapeError as error:
        findings.append(Finding("agent-config/unparseable-config", path, 1, 1, "%s." % error, "mcp-shape"))
        return
    for name, command, args, url, cfg in servers:
        line, column = locate(text, '"%s"' % name)
        key = "mcp\u0000" + name + "\u0000" + canonical(cfg)
        described = json_token(" ".join([str(command)] + args) if command is not None else url)[:200]
        if canonical(cfg) not in allowed:
            findings.append(Finding("agent-config/mcp-not-allowlisted", path, line, column,
                                    "MCP server %s (%s) is not an exact mcp.allowedServers entry; every "
                                    "field, including env, cwd and headers, must match."
                                    % (json_token(name), described), key))
        if command is None and url is None:
            findings.append(Finding("agent-config/unparseable-config", path, line, column,
                                    "MCP server %s has neither a command nor a url." % json_token(name), key))
            continue
        if url is not None and not str(url).startswith("https://"):
            findings.append(Finding("agent-config/mcp-not-allowlisted", path, line, column,
                                    "MCP server %s uses a non-https url." % json_token(name), key + "\u0000https"))
        if command is not None:
            reasons = unpinned_reasons(str(command), args, local_commands)
            if reasons:
                findings.append(Finding("agent-config/unpinned-package", path, line, column,
                                        "MCP server %s is not pinned: %s." % (json_token(name), "; ".join(reasons)),
                                        key))


def check_opencode_code_files(files, policy, findings):
    allowed = policy.get("hooks", {}).get("opencodePluginFiles", [])
    for path in files:
        canonical = canonical_path(path, policy.get("scope", {}))
        if canonical.lower().endswith(OPENCODE_CODE_EXTENSIONS) \
                and any(glob_match(g, canonical) for g in OPENCODE_CODE_GLOBS) and path not in allowed:
            findings.append(Finding("agent-config/hook-not-allowlisted", path, 1, 1,
                                    "opencode plugin or custom tool file is not on the allowlist; it runs "
                                    "in every opencode session.", "code-file"))


def load_codeowners(root, policy):
    for candidate in policy.get("codeowners", {}).get("files", [".github/CODEOWNERS"]):
        full = os.path.join(root, candidate)
        if os.path.isfile(full):
            rules = []
            with open(full, encoding="utf-8", errors="replace") as handle:
                for raw in handle:
                    line = raw.strip()
                    if not line or line.startswith("#"):
                        continue
                    parts = line.split()
                    owners = []
                    for token in parts[1:]:
                        if token.startswith("#"):
                            break
                        owners.append(token)
                    rules.append((codeowners_regex(parts[0]), bool(owners)))
            return candidate, rules
    return None, []


def check_codeowners(root, scoped, policy, policy_path, findings):
    source, rules = load_codeowners(root, policy)
    if source is None:
        findings.append(Finding("agent-config/codeowners-coverage", policy_path, 1, 1,
                                "No CODEOWNERS file was found, so no AI configuration path has an owner.",
                                "no-codeowners"))
        return
    required = list(scoped) + [p for p in policy.get("codeowners", {}).get("requiredOwnedPaths", [])
                               if p not in scoped]
    for path in required:
        owned = False
        for regex, has_owners in rules:
            if regex.fullmatch(path):
                owned = has_owners
        if not owned:
            findings.append(Finding("agent-config/codeowners-coverage", path, 1, 1,
                                    "No owner in %s covers this file." % source, "unowned"))


MEMORY_FILE_NAMES = {"CLAUDE.md", "CLAUDE.local.md", "AGENTS.md", "AGENT.md", "GEMINI.md"}
IMPORT_TOKEN = re.compile(r"(?<![^\s(\[])@([^\s`]+)")
FILE_REFERENCE = re.compile(r"\{file:([^}]*)\}")
FENCE_OPEN = re.compile(r"^ {0,3}(`{3,}|~{3,})(.*)$")


def code_span_mask(line):
    """Mask CommonMark code spans; a backslash-escaped backtick never opens one."""
    masked, i = list(line), 0
    while i < len(line):
        if line[i] == "\\":
            i += 2
            continue
        if line[i] != "`":
            i += 1
            continue
        run = len(line[i:]) - len(line[i:].lstrip("`"))
        close = re.compile(r"(?<!`)`{%d}(?!`)" % run).search(line, i + run)
        if not close:
            i += run
            continue
        for j in range(i, close.end()):
            masked[j] = " "
        i = close.end()
    return "".join(masked)


def markdown_imports(text):
    """(line, column, target, definite) for each @token. Tokens inside code spans or
    fenced blocks are not definite imports; they are followed only when they resolve."""
    out, fence = [], None
    for number, line in enumerate(text.split("\n"), 1):
        opening = FENCE_OPEN.match(line)
        if fence:
            if opening and opening.group(1)[0] == fence[0] and len(opening.group(1)) >= len(fence) \
                    and not opening.group(2).strip():
                fence = None
            else:
                out += [(number, m.start() + 1, m.group(1), False) for m in IMPORT_TOKEN.finditer(line)]
            continue
        if opening and not (opening.group(1)[0] == "`" and "`" in opening.group(2)):
            fence = opening.group(1)
            continue
        masked = code_span_mask(line)
        for match in IMPORT_TOKEN.finditer(line):
            definite = masked[match.start()] == "@"
            out.append((number, match.start() + 1, match.group(1), definite))
    return out


def resolve_reference(base_dir, target, files):
    """A list of in-repository files the reference loads, or a string saying why
    the scanner cannot follow it."""
    if re.match(r"^[A-Za-z][A-Za-z0-9+.-]+:", target):
        return "is a URL or scheme reference"
    if target.startswith("~"):
        return "is a home-directory path"
    if target.startswith(("/", "\\")) or re.match(r"^[A-Za-z]:[\\/]", target):
        return "is an absolute path"
    candidates = [target] + ([target.rstrip(".,;:)]")] if target.rstrip(".,;:)]") != target else [])
    for candidate in candidates:
        resolved = posixpath.normpath(posixpath.join(base_dir, candidate))
        if resolved == ".." or resolved.startswith("../"):
            return "points outside the repository"
        if any(c in candidate for c in "*?["):
            matches = [f for f in files if glob_match(resolved, f)]
            if matches:
                return matches
        elif resolved in files:
            return [resolved]
    return "does not resolve to a file in the repository"


def references(path, text, data, opencode_like, imports_followed):
    """(line, column, kind, target, definite) for every reference that loads more content."""
    refs = []
    if imports_followed:
        refs += [(line, column, "@import", target, definite)
                 for line, column, target, definite in markdown_imports(text)]
    if opencode_like and data is not None:
        for entry in entry_list_of(data.get("instructions", [])):
            refs.append(locate(text, '"instructions"', json_token(entry)) + ("instructions", str(entry), True))
        for keys, value in json_strings(data):
            for match in FILE_REFERENCE.finditer(value):
                refs.append(locate(text, *['"%s"' % k for k in keys]) + ("{file:}", match.group(1), True))
    return refs


def follow_reference(state, path, line, column, kind, target, definite):
    resolved = resolve_reference(posixpath.dirname(path), target, state["file_set"])
    if isinstance(resolved, str):
        if definite and not reasoned(state["policy"].get("referenceExceptions", {}).get(path, {}), target):
            state["findings"].append(Finding(
                "agent-config/external-reference", path, line, column,
                "%s %s %s, so the scanner cannot check what it loads. If it is not an import, wrap it in "
                "backticks; if it is deliberate, list it under referenceExceptions for this file with a reason."
                % (kind, json_token(target)[:200], resolved), kind + SEP + target))
        return
    for target_path in resolved:
        newly_imported = kind == "@import" and target_path not in state["follow_imports"]
        if newly_imported:
            state["follow_imports"].add(target_path)
        if target_path not in state["seen"]:
            state["seen"].add(target_path)
            state["scoped"].append(target_path)
            state["queue"].append((target_path, False))
        elif newly_imported:
            state["queue"].append((target_path, True))


def claim(path, canonical, policy):
    """The check that understands this file, or None. Dispatch uses the canonical spelling."""
    known = policy.get("knownFiles", {})
    if any(glob_match(pattern, path) and reasoned(known, pattern) for pattern in known):
        return "known"
    path = canonical
    name = posixpath.basename(path)
    if glob_match(CLAUDE_SETTINGS_GLOB, path):
        return "claude-settings"
    if glob_match(VSCODE_SETTINGS_GLOB, path):
        return "vscode-settings"
    if ifull(CODE_WORKSPACE_GLOB, path):
        return "code-workspace"
    if any(ifull(g, path) for g in DEVCONTAINER_GLOBS):
        return "devcontainer"
    if name in ("opencode.json", "opencode.jsonc"):
        return "opencode"
    if any(glob_match(g, path) for g in MCP_GLOBS):
        return "mcp"
    if path.lower().endswith(".md"):
        return "markdown"
    if name in PLAIN_TEXT_NAMES:
        return "text"
    if path.lower().endswith(OPENCODE_CODE_EXTENSIONS) and any(glob_match(g, path) for g in OPENCODE_CODE_GLOBS):
        return "code"
    return None


JSONC_KINDS = ("opencode", "vscode-settings", "code-workspace", "devcontainer")
JSON_KINDS = ("claude-settings", "mcp") + JSONC_KINDS


def process_file(state, path, imports_only, discovered):
    policy, findings = state["policy"], state["findings"]
    text = read_text(state["root"], path, state["max_bytes"])
    if text is None:
        if not imports_only:
            findings.append(Finding("agent-config/unparseable-config", path, 1, 1,
                                    "File is larger than maxFileBytes (%d) and was not scanned."
                                    % state["max_bytes"], "oversized"))
        return
    if imports_only:
        for ref in references(path, text, None, False, True):
            follow_reference(state, path, *ref)
        return
    check_hidden_unicode(path, text, findings)
    check_secrets(path, text, findings)
    canonical = canonical_path(path, policy.get("scope", {}))
    kind = claim(path, canonical, policy)
    if discovered:
        variant = case_variant(path, policy.get("scope", {}))
        if variant:
            findings.append(Finding("agent-config/unrecognised", path, 1, 1,
                                    "This path spells %s with different case; case-insensitive file systems "
                                    "load it as %s, so commit it with the canonical spelling. It is checked as "
                                    "that file." % (variant, variant), "case" + SEP + variant))
        if kind is None:
            findings.append(Finding("agent-config/unrecognised", path, 1, 1,
                                    "This file sits in AI-tool configuration but no check understands it. Add "
                                    "a check, or list it in the policy's knownFiles with a reason.", "file"))
            return
    opencode = policy.get("opencode", {})
    opencode_file = opencode.get("file", "opencode.jsonc")
    data = None
    if kind == "opencode" and canonical != opencode_file and path not in opencode.get("extraFiles", []):
        findings.append(Finding("agent-config/permission-drift", path, 1, 1,
                                "Additional opencode configuration file is not in the policy; opencode "
                                "merges it with %s." % opencode_file, "opencode-file"))
    if kind in JSON_KINDS:
        data = load_json(text, path, kind in JSONC_KINDS, findings)
        if data is None:
            return
        check_decoded_strings(path, text, json_strings(data), findings)
        if kind == "claude-settings":
            check_claude_settings(path, text, data, policy, findings)
            check_claude_hooks(path, text, data, policy, findings)
        elif kind == "opencode":
            check_opencode(path, text, data, policy, findings)
        elif kind == "vscode-settings":
            check_vscode_block(path, canonical, text, data, "the settings file", [], policy, findings)
        elif kind == "code-workspace":
            check_code_workspace(path, canonical, text, data, policy, findings, state["file_set"])
        elif kind == "devcontainer":
            check_devcontainer(path, canonical, text, data, policy, findings)
        else:
            servers_key = [v for g, v in MCP_GLOBS.items() if glob_match(g, canonical)][0]
            unrecognised_keys(path, text, data, {servers_key}, {}, "the MCP file", [], findings)
        if kind in ("claude-settings", "opencode", "mcp"):
            check_mcp(path, canonical, kind, text, data, policy, findings)
    if kind == "markdown":
        check_frontmatter(path, canonical, text, policy, findings)
    for ref in references(path, text, data, kind == "opencode", path in state["follow_imports"]):
        follow_reference(state, path, *ref)


def scan(root, policy, policy_path):
    files, links = list_files(root)
    scope = policy.get("scope", {})
    scoped = [p for p in files if in_scope(p, scope)]
    if not scoped:
        raise ScanError("no files matched the policy scope; refusing to report a clean scan")
    skip_ext = tuple(scope.get("skipContentExtensions", []))
    findings = []
    for path, kind in links:
        if not reasoned(policy.get("linkExceptions", {}), path):
            findings.append(Finding("agent-config/symlink", path, 1, 1,
                                    "This path is a %s; the scanner does not follow it, but agents do. Commit "
                                    "the content itself, or add it to linkExceptions with a reason." % kind,
                                    "link"))
    discovered = set(scoped)
    state = {"root": root, "policy": policy, "findings": findings, "file_set": set(files),
             "max_bytes": int(policy.get("maxFileBytes", DEFAULT_MAX_FILE_BYTES)),
             "queue": [(p, False) for p in scoped], "seen": set(scoped), "scoped": scoped,
             "follow_imports": {p for p in scoped if posixpath.basename(p) in MEMORY_FILE_NAMES}}
    while state["queue"]:
        path, imports_only = state["queue"].pop(0)
        if skip_ext and path.lower().endswith(skip_ext):
            continue
        try:
            process_file(state, path, imports_only, path in discovered and not imports_only)
        except Exception as error:
            findings.append(Finding("agent-config/scanner-error", path, 1, 1,
                                    "Scanner error at %s: %s: %s" % (path, type(error).__name__, str(error)[:200]),
                                    "error" + SEP + type(error).__name__))
    for name, check in (("opencode code files", lambda: check_opencode_code_files(scoped, policy, findings)),
                        ("CODEOWNERS", lambda: check_codeowners(root, scoped, policy, policy_path, findings))):
        try:
            check()
        except Exception as error:
            findings.append(Finding("agent-config/scanner-error", policy_path, 1, 1,
                                    "Scanner error in the %s check: %s: %s" % (name, type(error).__name__,
                                                                               str(error)[:200]), "error" + SEP + name))
    return scoped, findings


def fingerprints(findings):
    counts, out = {}, []
    for finding in findings:
        base = "\u0000".join([finding.rule, finding.path, finding.key])
        ordinal = counts.get(base, 0)
        counts[base] = ordinal + 1
        out.append(hashlib.sha256(("%s\u0000%d" % (base, ordinal)).encode("utf-8", "surrogatepass")).hexdigest())
    return out


def to_sarif(findings):
    rules = [{
        "id": rule_id,
        "name": name,
        "shortDescription": {"text": short},
        "fullDescription": {"text": full},
        "help": {"text": full},
        "helpUri": INFO_URI,
        "defaultConfiguration": {"level": level},
        "properties": {"tags": ["security", "ai-agent-config"], "precision": "high",
                       "security-severity": severity},
    } for rule_id, name, level, severity, short, full in RULES]
    results = []
    for finding, fingerprint in zip(findings, fingerprints(findings)):
        results.append({
            "ruleId": finding.rule,
            "ruleIndex": RULE_INDEX[finding.rule],
            "level": RULE_LEVEL[finding.rule],
            "message": {"text": finding.message},
            "locations": [{"physicalLocation": {
                "artifactLocation": {"uri": finding.path, "uriBaseId": "%SRCROOT%"},
                "region": {"startLine": finding.line, "startColumn": finding.column},
            }}],
            "partialFingerprints": {FINGERPRINT_KEY: fingerprint},
        })
    return {
        "$schema": "https://json.schemastore.org/sarif-2.1.0.json",
        "version": "2.1.0",
        "runs": [{
            "tool": {"driver": {"name": TOOL_NAME, "version": TOOL_VERSION,
                                "informationUri": INFO_URI, "rules": rules}},
            "automationDetails": {"id": CATEGORY + "/"},
            "columnKind": "unicodeCodePoints",
            "results": results,
        }],
    }


def main(argv=None):
    here = os.path.dirname(os.path.abspath(__file__))
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--root", default=os.path.abspath(os.path.join(here, "..", "..")),
                        help="repository root to scan (default: this repository)")
    parser.add_argument("--policy", default=os.path.join(here, "agent-config-policy.json"),
                        help="policy file with the allowlists and baselines")
    parser.add_argument("--sarif", help="write SARIF 2.1.0 to this path")
    args = parser.parse_args(argv)
    root = os.path.abspath(args.root)
    policy_path = os.path.relpath(os.path.abspath(args.policy), root).replace(os.sep, "/")
    try:
        with open(args.policy, encoding="utf-8") as handle:
            policy = json.load(handle)
        scanned, findings = scan(root, policy, policy_path)
    except Exception as error:
        print("agent-config-scan: ERROR: %s: %s" % (type(error).__name__, error), file=sys.stderr)
        if args.sarif:
            failure = Finding("agent-config/scanner-error", policy_path, 1, 1,
                              "The scan could not run: %s: %s" % (type(error).__name__, str(error)[:300]), "fatal")
            with open(args.sarif, "w", encoding="utf-8") as handle:
                json.dump(to_sarif([failure]), handle, indent=2, ensure_ascii=True)
                handle.write("\n")
        return 2
    findings.sort(key=lambda f: (f.path, f.line, f.column, f.rule, f.message))
    if args.sarif:
        with open(args.sarif, "w", encoding="utf-8") as handle:
            json.dump(to_sarif(findings), handle, indent=2, ensure_ascii=True)
            handle.write("\n")
    for finding in findings:
        print("%s:%d:%d: [%s] %s" % (finding.path, finding.line, finding.column, finding.rule,
                                     finding.message.encode("ascii", "backslashreplace").decode("ascii")))
    print("agent-config-scan: %d finding(s) in %d file(s) scanned" % (len(findings), len(scanned)),
          file=sys.stderr)
    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main())
