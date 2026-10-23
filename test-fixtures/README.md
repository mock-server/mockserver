# Cross-language client round-trip fidelity fixtures

**Outcome:** one canonical, server-validated set of kitchen-sink Expectation JSON fixtures that every
MockServer client library (Python, Ruby, Go, Rust, C#/.NET, Node, PHP) deserializes with its native model
and re-serializes, asserting semantic equality with the input. This proves the generated/hand-written client
code carries every server feature — and, where a model currently drops a field, pins that drop as an explicit,
machine-readable known gap so a regression cannot hide and a later fix auto-arms the assertion.

## Why this exists

The dashboard's per-language code tabs embed faithful `buildExpectationJson` JSON and hydrate it through each
client's deserializer. Fidelity is therefore gated by each client MODEL's completeness, and any missing field
is dropped **silently**. This harness turns every such silent drop into a visible, tracked ledger entry.

```
test-fixtures/expectations/
  *.json               63 single-Expectation fixtures, each validated 2xx by PUT /mockserver/expectation
  known-gaps.json      language -> [dropped/mutated JSON paths] ledger (the model-completion backlog)
```

## Layout / flow

```mermaid
flowchart LR
  FX["test-fixtures/expectations/*.json\n(server-valid)"] --> T
  KG["known-gaps.json\n(per-language excused paths)"] --> T
  subgraph T["each client's native test suite (already in CI)"]
    P["python pytest"]
    R["ruby rspec"]
    G["go test"]
    RS["rust cargo test"]
    D["dotnet test"]
    N["node tsc + node:test"]
    PH["php phpunit"]
  end
  FX --> M["mockserver-ui vitest meta-test\n(coverage gate: every action / body-matcher / top-level key)"]
```

Each client test: parse fixture → client-deserialize → client-serialize → compare (null==absent normalized;
headers/query/trailers/cookies canonicalized across both valid encodings) → assert every diff path is listed in
`known-gaps.json` for that language. A **ratchet** also fails the test if a listed gap no longer matches any
diff (i.e. the model was fixed) — forcing the stale entry to be removed.

## Fixture inventory (63)

- Body matchers: STRING(+subString), JSON(+matchType STRICT), JSON_SCHEMA, JSON_PATH, XML, XML_SCHEMA, XPATH,
  REGEX(+not/optional), PARAMETERS, BINARY, GRAPHQL, WASM, ALL_OF; JSON_PATH and XPATH with a `dateTime`
  comparison on the selected value; plus JWT request matcher and pathParameters.
- Matcher values: the nottable object form (literal `!`/`?` values) and the date-time matcher object form
  (`before` / `after` / `equalToDateTime` with every option, beside a plain value that only looks like one).
- Actions: static response (delay+connectionOptions+cookies+trailers+reasonPhrase), FILE response body
  (+templateType+templatedFilePath), forward, override (+primary),
  forward with fallback, forward validate, forward template (+delay+primary), forward class/object callback,
  response template (JS; MUSTACHE+delay+primary+parameters), response class/object callback, error, websocket, sse, binary (+upstream), dns, grpc stream, grpc bidi, llm
  (completion+conversationPredicates+normalization+streamingPhysics; embedding+rerank+moderation+contentFilter+chaos).
- Response sequences: httpResponses with responseMode SEQUENTIAL / WEIGHTED(+responseWeights) / SWITCH(+switchAfter) / RANDOM.
- Top-level: chaos, rateLimit, percentage, beforeActions, afterActions, capture, namespace, metadata (`top_metadata.json`: nested values, a null, and `!`/`?`/`%`/`+`/space characters), steps, scenario
  bindings + crossProtocolScenarios, explicit priority / times / timeToLive.

Every fixture is a single Expectation object and is validated against a live MockServer (201 on PUT). Adding a
new server feature should add a fixture; the meta-test fails CI until every action type, body-matcher type and
top-level Expectation key is exercised.
