"""Fixture serialization, storage, and loading for Parallel Search MCP."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from .capture import CapturedSession
from .exceptions import FixtureNotFoundError, FixtureValidationError


DEFAULT_FIXTURE_FILENAME = "parallel_search_fixtures.json"


def get_default_fixture_path() -> Path:
    """Return the default path to the bundled representative fixture."""
    return Path(__file__).parent / "fixtures" / DEFAULT_FIXTURE_FILENAME


def save_fixture(session: CapturedSession, file_path: Path | str) -> Path:
    """Serialize a CapturedSession to a formatted JSON fixture file."""
    path = Path(file_path)
    path.parent.mkdir(parents=True, exist_ok=True)

    data = session.to_dict()
    temp_path = path.with_suffix(".tmp")
    with open(temp_path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")

    temp_path.replace(path)
    return path


def load_fixture(file_path: Path | str | None = None) -> CapturedSession:
    """Load a CapturedSession from a JSON fixture file."""
    path = Path(file_path) if file_path is not None else get_default_fixture_path()
    if not path.exists():
        raise FixtureNotFoundError(f"Fixture file does not exist: {path}")

    try:
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
    except json.JSONDecodeError as err:
        raise FixtureValidationError(f"Invalid JSON in fixture file {path}: {err}") from err
    except OSError as err:
        raise FixtureValidationError(f"Failed to read fixture file {path}: {err}") from err

    if not isinstance(data, dict):
        raise FixtureValidationError(f"Root JSON entity in {path} must be a dictionary")

    return CapturedSession.from_dict(data)


def validate_fixture_integrity(session: CapturedSession) -> None:
    """Validate that a CapturedSession contains all necessary interactions."""
    if not session.initialize:
        raise FixtureValidationError("Fixture is missing 'initialize' handshake interaction")

    if not session.tools:
        raise FixtureValidationError("Fixture is missing 'tools' list interaction")

    tool_names = {t.get("name") for t in session.tools if isinstance(t, dict)}
    if "web_search" not in tool_names or "web_fetch" not in tool_names:
        raise FixtureValidationError(
            f"Fixture missing required tools ('web_search', 'web_fetch'). Found: {tool_names}"
        )

    if not session.search or not session.search.get("content"):
        raise FixtureValidationError("Fixture is missing valid 'web_search' result content")

    if not session.fetch or not session.fetch.get("content"):
        raise FixtureValidationError("Fixture is missing valid 'web_fetch' result content")


def validate_fixture_file(file_path: Path | str | None = None) -> CapturedSession:
    """Load and validate integrity of a fixture file."""
    session = load_fixture(file_path)
    validate_fixture_integrity(session)
    return session

