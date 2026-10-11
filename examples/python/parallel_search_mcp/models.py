"""Data models for structured representation of Parallel Search MCP results."""

from __future__ import annotations

import json
from dataclasses import dataclass, field
from typing import Any


@dataclass
class SearchResultItem:
    """A single search result item."""

    url: str
    title: str | None = None
    publish_date: str | None = None
    excerpts: list[str] = field(default_factory=list)

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> SearchResultItem:
        return cls(
            url=data.get("url", ""),
            title=data.get("title"),
            publish_date=data.get("publish_date"),
            excerpts=data.get("excerpts", []) or [],
        )


@dataclass
class SearchResponse:
    """Parsed structured response for a web_search invocation."""

    search_id: str | None = None
    results: list[SearchResultItem] = field(default_factory=list)
    session_id: str | None = None
    raw_text: str = ""

    @property
    def urls(self) -> list[str]:
        return [r.url for r in self.results]

    @property
    def total_results(self) -> int:
        return len(self.results)

    @classmethod
    def from_tool_result(cls, result: dict[str, Any]) -> SearchResponse:
        content_items = result.get("content", [])
        if not content_items:
            return cls()

        first_text = content_items[0].get("text", "")
        try:
            parsed = json.loads(first_text)
            if isinstance(parsed, dict):
                items = [SearchResultItem.from_dict(item) for item in parsed.get("results", [])]
                return cls(
                    search_id=parsed.get("search_id"),
                    results=items,
                    session_id=parsed.get("session_id"),
                    raw_text=first_text,
                )
        except (json.JSONDecodeError, TypeError):
            pass

        return cls(raw_text=first_text)


@dataclass
class FetchResultItem:
    """A single page extraction result item."""

    url: str
    title: str | None = None
    publish_date: str | None = None
    excerpts: list[str] = field(default_factory=list)
    full_content: str | None = None

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> FetchResultItem:
        return cls(
            url=data.get("url", ""),
            title=data.get("title"),
            publish_date=data.get("publish_date"),
            excerpts=data.get("excerpts", []) or [],
            full_content=data.get("full_content"),
        )


@dataclass
class FetchResponse:
    """Parsed structured response for a web_fetch invocation."""

    extract_id: str | None = None
    results: list[FetchResultItem] = field(default_factory=list)
    errors: list[dict[str, Any]] = field(default_factory=list)
    session_id: str | None = None
    raw_text: str = ""

    @property
    def total_results(self) -> int:
        return len(self.results)

    @property
    def urls(self) -> list[str]:
        return [r.url for r in self.results]

    @classmethod
    def from_tool_result(cls, result: dict[str, Any]) -> FetchResponse:
        content_items = result.get("content", [])
        if not content_items:
            return cls()

        first_text = content_items[0].get("text", "")
        try:
            parsed = json.loads(first_text)
            if isinstance(parsed, dict):
                items = [FetchResultItem.from_dict(item) for item in parsed.get("results", [])]
                return cls(
                    extract_id=parsed.get("extract_id"),
                    results=items,
                    errors=parsed.get("errors", []) or [],
                    session_id=parsed.get("session_id"),
                    raw_text=first_text,
                )
        except (json.JSONDecodeError, TypeError):
            pass

        return cls(raw_text=first_text)
