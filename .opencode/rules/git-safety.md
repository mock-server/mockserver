# Git Safety — Destructive Command Protection

## NEVER Run Destructive Git Commands Without Explicit User Confirmation

The following commands can cause **irreversible data loss** of uncommitted work. You MUST ask the user for confirmation before running any of them, clearly explaining what will be lost:

### Banned Without Confirmation

| Command | Risk |
|---------|------|
| `git checkout -- .` | Reverts ALL uncommitted changes in the working tree |
| `git checkout -- <file>` | Reverts uncommitted changes in a specific file |
| `git restore .` | Same as `git checkout -- .` |
| `git restore <file>` | Discards unstaged changes to a file |
| `git clean -fd` | Deletes untracked files and directories permanently |
| `git reset --hard` | Resets staging area AND working tree — destroys all uncommitted work |
| `git stash drop` | Permanently deletes a stash entry |
| `git stash clear` | Permanently deletes ALL stash entries |
| `git branch -D` | Force-deletes a branch even if unmerged |
| `git push --force` | Rewrites remote history — can destroy others' work |
| `git rebase` (on shared branches) | Rewrites commit history |

### Before Running Any Destructive Command

1. **Run `git status` and `git diff --stat`** to show the user exactly what uncommitted changes exist.
2. **Warn the user** what will be lost, listing affected files.
3. **Suggest `git stash`** as a safer alternative when the goal is to temporarily set aside changes.
4. **Wait for explicit confirmation** before proceeding.

### Safer Alternatives

| Instead of... | Use... |
|---------------|--------|
| `git checkout -- .` | `git stash` (preserves changes, retrievable later) |
| `git reset --hard` | `git stash` then `git reset` (soft) |
| `git clean -fd` | `git clean -fdn` (dry-run first to preview) |

### File Renames and Moves

When renaming or moving files, **always use `git mv`** instead of the OS-level `mv` command.
This preserves file history in Git, making `git log --follow` work correctly.

| Instead of... | Use... |
|---------------|--------|
| `mv old.java new.java` | `git mv old.java new.java` |
| `mv src/foo/ src/bar/` | `git mv src/foo/ src/bar/` |

If a file has already been moved with `mv`, recover history tracking by staging it as a rename:

```bash
git add -A  # Git detects the delete + create as a rename if similarity >= 50%
```

### Exception

If the user explicitly asks to discard/revert/reset changes (e.g., "throw away my changes", "reset everything"), you may proceed — but still show `git status` output first so they can verify what will be lost.
