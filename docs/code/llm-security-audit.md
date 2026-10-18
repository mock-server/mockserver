# LLM Mocking Feature -- Security Audit (M5)

**Date:** 2026-05-26
**Scope:** All code introduced in M0 (fa2a5bb05) through M4 (24668ed1d)
**Method:** Manual code review with targeted grep sweeps. CodeQL was not run locally (not installed); this audit covers the same categories.

## Summary

No vulnerabilities found. Previously known limitations (Ollama NDJSON, Bedrock binary framing) have been resolved. All checked categories passed.

## What was checked

### 1. Debug output leaks (`System.out.print` / `System.err.print`)

**Result: PASS** -- Zero instances found in any LLM-related source file.

Files checked:
- All files under `mockserver-core/src/main/java/org/mockserver/llm/`
- `HttpLlmResponse.java`, `Completion.java`, `ToolUse.java`, `StreamingPhysics.java`, `EmbeddingResponse.java`, `ConversationPredicates.java`
- `HttpLlmResponseActionHandler.java`, `LlmConversationMatcher.java`
- `LlmMockBuilder.java`, `LlmConversationBuilder.java`, `TurnBuilder.java`

### 2. JSON injection in hand-built SSE chunks

**Result: PASS** -- Every `withData(...)` call in every codec's `encodeStreaming()` method either routes user-provided strings through `JsonEscape.escape()` (aliased as `escapeJson()` in each codec) or builds the event as a Jackson node and serialises it (`OpenAiResponsesCodec`).

Audit of each codec:

| Codec | User strings in `withData()` | Escaped via |
|-------|------------------------------|-------------|
| `AnthropicCodec` | `modelName`, `token` (text chunks), `toolCall.getName()`, tool `args` | `escapeJson()` -> `JsonEscape.escape()` |
| `OpenAiChatCompletionsCodec` | `model`, `token`, `toolCall.getName()`, tool `args`, `finishReason` | `escapeJson()` via `buildChunk()` |
| `OpenAiResponsesCodec` | `modelName`, `token`, `text`, `toolCall.getName()`, tool `args` | Each event is built as a Jackson `ObjectNode` and serialised with `writeValueAsString` (no string concatenation) |
| `GeminiCodec` | `token`, `modelName`, `toolCall.getName()`, tool `args` | `escapeJson()`; args also re-serialised via Jackson `writeValueAsString` |
| `BedrockCodec` | Delegates to `AnthropicCodec` (InvokeModel) or `BedrockConverseCodec` (Converse) | Same as Anthropic; see next row |
| `BedrockConverseCodec` | `token`, `toolCall.getName()`, tool `args`, reasoning text/signature, stop reason | Not hand-built: every event payload is a Jackson `ObjectNode` serialised with `writeValueAsString()` |
| `AzureOpenAiCodec` | Delegates to `OpenAiChatCompletionsCodec` | Same as OpenAI |
| `OllamaCodec` | `modelName`, `token` (text chunks) | `escapeJson()`; final chunk uses `OBJECT_MAPPER.writeValueAsString()` |

`JsonEscape.escape()` handles the seven RFC 8259 short escapes and emits `\\uXXXX` for control characters below U+0020. This is sufficient to prevent JSON injection in SSE data lines.

### 3. Secrets in log messages

**Result: PASS** -- No API keys, tokens, credentials, or authorization headers are logged anywhere in the LLM code paths.

The only body content that appears in logs is a truncated 256-byte sample in `LlmConversationMatcher` at DEBUG level, which is appropriate for diagnostics and does not include headers.

### 4. `@JsonIgnore` on sensitive fields

**Result: PASS** -- `HttpLlmResponse.conversationMatcher` (a transient evaluation-time object) is annotated with `@JsonIgnore` and declared `transient`. The `getType()` method is also `@JsonIgnore`. No sensitive fields (API keys, secrets) exist on any LLM model class.

### 5. Body-size cap enforcement

**Result: PASS** -- `LlmConversationMatcher.matches()` checks `request.getBodyAsRawBytes().length` against `ConfigurationProperties.maxLlmConversationBodySize()` **before** calling `codec.decode(request)`. Bodies exceeding the cap are treated as no-match and logged at DEBUG.

The cap is enforced with clamping in `ConfigurationProperties` (range: 16 KiB to 64 MiB, default 1 MiB).

### 6. Unbounded user input in error responses

**Result: PASS** -- Error responses in `HttpLlmResponseActionHandler` interpolate only:
- `provider` (a Java enum, always a safe constant name like `ANTHROPIC`)
- `provider.name()` (same as above)
- The literal string `null`
- `supportedProvidersJson()` which builds an array from enum names

No user-supplied strings (request bodies, headers, paths) are interpolated into error JSON.

### 7. Random ID generation

**Result: PASS (acceptable for test utility)** -- Message, item and tool-call IDs (e.g., `msg_*`, `chatcmpl-*`, `toolu_*`, `fc_*`, `call_*`) come from `UUIDService.getNonSecureUUID()` (`ThreadLocalRandom`). They only need to be unique: none of them retrieves stored data. The one ID that does, the OpenAI Responses `resp_*` id, comes from `UUIDService.getUUID()` (`SecureRandom`); see check 8. The `StreamingPhysicsExpander` uses `java.util.Random` for jitter timing, which is appropriate (timing jitter is not a security-sensitive value).

The embedding `deterministicFromInput()` deliberately uses `java.util.Random` seeded from a SHA-256 hash for reproducibility. This is a test utility, not a security primitive.

### 8. Stored OpenAI Responses bodies readable by `resp_` id

**Result: PASS** -- `GET /v1/responses/{id}` on the data plane returns a stored response body to any caller that presents its id, with no other check, so the id is generated by the secure generator and is not guessable.

```mermaid
flowchart LR
    P["POST /v1/responses\ninstructions, tools, metadata"] --> E["OpenAiResponsesCodec.encode\nechoes them in the body"]
    E --> S["OpenAiResponsesStore\nkeyed by resp_ id"]
    G["GET /v1/responses/{id}"] --> S
    S --> R["stored body returned verbatim"]
```

| Aspect | Behaviour |
|--------|-----------|
| What is stored | The encoded body of each `OPENAI_RESPONSES` turn (for a streamed turn, the response object of its `response.completed` event), which echoes the request's `instructions`, `tools`, `metadata`, `tool_choice` and `previous_response_id`, plus the decoded conversation used for `previous_response_id` chaining |
| Who can read it | Any data-plane caller that knows the `resp_` id. Retrieval is not a control-plane call, so control-plane authentication does not apply. It runs only when no expectation matched and the server is not proxying |
| Guard | The id is `resp_` plus 24 hex characters of a `UUIDService.getUUID()` value (`SecureRandom`, 90 random bits). `getNonSecureUUID()` must not be used for it |
| Not stored | A turn whose request sets `store:false` |
| Lifetime | Until `reset`, or until evicted as least recently used once the store holds 10,000 entries or its byte budget (`maxOpenAiResponsesSizeInBytes`) is exceeded |

The id is a bearer token for that one body: it is returned to the client that made the request and appears in the event log, so anyone who can read either can read the stored body. That matches the body the client already received. A chained request (`previous_response_id`) uses the stored conversation for matching only and never returns it.

## Known limitations

### Ollama NDJSON wire format (RESOLVED)

**Resolved.** `OllamaCodec` now declares `StreamingFormat.NDJSON` and the `HttpSseResponseActionHandler` emits raw `<json>\n` lines (no SSE `data:` prefix) for Ollama streaming responses.

### BedrockCodec binary framing (RESOLVED)

**Compatibility limitation — actionable for raw HTTP clients.**

**Resolved** in G14. `BedrockCodec` now declares `StreamingFormat.AWS_EVENT_STREAM` and the `HttpSseResponseActionHandler` encodes each streaming chunk as a binary AWS event-stream message via `BedrockEventStreamEncoder`. Each message carries headers (`:event-type=chunk`, `:content-type=application/json`, `:message-type=event`), CRC32 integrity checks (prelude and message), and a payload of `{"bytes":"<base64(chunkJson)>"}` matching the `InvokeModelWithResponseStream` wire format. Raw (non-SDK) Bedrock streaming clients now work against MockServer.

The limitation is documented in the `BedrockCodec` javadoc. Not a security concern.

`ConverseStream` (selected by a `/converse-stream` path, GitHub discussion #2757) uses the same binary framing via `StreamingFormat.AWS_CONVERSE_EVENT_STREAM` and `BedrockEventStreamEncoder.encodeEvent`, but each frame's `:event-type` is the Converse event name and its payload is the raw event JSON (no base64 wrapper), as AWS sends it. The `:event-type` value is one of the codec's fixed event-name constants, never user input. A nameless event (the malformed-SSE chaos chunk, which has no name) is framed as `contentBlockDelta` so its corrupt payload reaches the client instead of being dropped by the SDK for an empty `:event-type`.

### Runtime LLM client — Bedrock SigV4 signing (RESOLVED)

**Resolved.** `BedrockLlmClient` now implements automatic AWS Signature Version 4 request signing via `AwsSigV4Signer`, a pure, stateless signer using only JDK crypto (SHA-256, HmacSHA256 -- no third-party dependencies).

**Credential sourcing:** AWS credentials are parsed from `LlmBackend.apiKey()` in the format `accessKeyId:secretAccessKey` (or `accessKeyId:secretAccessKey:sessionToken` for STS temporary credentials). When `apiKey` is null, blank, or does not contain a `:` separator, signing is skipped and the original escape-hatch behaviour is preserved (backward compatible).

**Region extraction:** The region is parsed from the `baseUrl` host (`bedrock-runtime.<region>.amazonaws.com`); defaults to `us-east-1` if the host does not match. The AWS service is `bedrock`.

**Authorization precedence:** When SigV4 credentials are present, the auto-generated `Authorization` header takes precedence over any `Authorization` supplied via the `LlmBackend.headers()` escape hatch. The escape hatch remains fully supported for pre-signed / signing-proxy setups when no credentials are provided.

**Test verification:** The signing-key derivation is verified against the AWS-published test vector (secret `wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY`, date `20120215`, region `us-east-1`, service `iam` -- expected signing key hex `f4780e2d9f65fa895f9c67b32ce1baf0b0d8a43505a000a1a9e090d414db404d`). Full end-to-end signing is tested for structural correctness, determinism, session-token inclusion, and body-sensitivity. The signing timestamp is injectable for offline test determinism.

### Gemini API key in query string (runtime client)

The runtime-LLM `GeminiLlmClient` passes the API key as a `?key=` query parameter, as Gemini's API-key auth requires. Unlike header credentials, query-string keys can surface in HTTP access/proxy logs. This is a property of the provider's API, not a MockServer choice; front the call with a gateway that injects the key after ingress in high-security environments. Documented in the `GeminiLlmClient` javadoc. Not a MockServer defect.

### Gemini tool-call argument re-serialisation

The `GeminiCodec.encodeStreaming()` method re-serialises tool-call arguments through Jackson (`OBJECT_MAPPER.readTree` + `writeValueAsString`) before embedding them in the SSE chunk. If the arguments are not valid JSON, they are wrapped in a `{"value":"<escaped>"}` object. This is a safe fallback that prevents malformed arguments from corrupting the JSON chunk. No action required.

### `whenContainsToolResultFor` E2E false-negative for Gemini and Ollama — RESOLVED

**Resolved.** This was previously reported as an E2E-only false-negative (the matcher unit tests passed for all providers, but the predicate was believed to fail through the full Netty pipeline for Gemini/Ollama turn-2 requests). It no longer reproduces: `LlmAgentLoopE2eTest.shouldMatchContainsToolResultForGeminiEndToEnd` and `…ForOllamaEndToEnd` drive turn 2 purely via `whenContainsToolResultFor` (not scenario ordering), through the real Netty pipeline, and both pass — Gemini's name-keyed correlation and Ollama's positional fallback work end-to-end. These regression tests guard against recurrence. (The earlier behaviour was fixed by subsequent matcher/codec work; the body is delivered to the matcher correctly E2E, as the Anthropic/OpenAI/Azure/Bedrock predicate-driven E2E tests also demonstrate.) Not a security issue.

## Usage scanning of proxied LLM streams (2026-10-04)

**Outcome:** Counting a streamed proxied LLM call reads bytes an upstream controls, on the upstream
event loop, without being able to hold the stream, throw into the relay, or log its content.

| Concern | Control |
|---------|---------|
| Memory | `LlmStreamUsageScanner` holds one captured value of at most 8 KiB (`MAX_VALUE_BYTES`; a larger value is dropped) and a 32-byte field name. The usage merged across events is capped at 32 fields, each a whole number or an object of at most 16 whole numbers, under names of at most 64 characters (under 64 KiB as JSON): a known field is replaced, a further new one refused, and anything that is not a whole-number count is ignored. Model name and stop reason are kept only up to 128 characters. Strings it does not want are skipped byte by byte, so event length is unbounded without cost. A `bytes` value (Bedrock) is base64-decoded only after passing the same 8 KiB bound. |
| Malformed or hostile input | The skimmer has no failure state: unbalanced or invalid JSON moves counters that are clamped at zero, and they reset at the next Server-Sent Event or event-stream message (newline-delimited JSON has no such boundary, so a malformed line can hide the usage of the lines after it: the call is then reported as not counted). An AWS event-stream prelude with an impossible length stops the scan. A captured value is parsed by Jackson inside a catch; its nesting limit rejects a deeply nested value. `accept` catches every runtime exception and disables the scanner. |
| Effect on the response | The observer runs in `StreamingBody.addChunk` on a slice of the chunk, before the chunk is relayed; an observer that throws is dropped and the chunk is relayed as normal. The accounting at stream end is inside `emitForwardGenAiSpan`'s catch. |
| Logging | The usage log lines carry only the provider and the model name. No header, body or event text is logged, and the message formats are constants (`LogMessageFormatGuardTest`). Each line is written at most once a minute per provider and model, from a map capped at 256 keys and cleared when full: repeats of one provider and model are limited to one line a minute, and distinct model names are bounded only by the call rate. |
| False counting | A hostile upstream can report any usage it likes, which is the same trust the whole-response path already places in it; it can only raise the recorded cost of its own calls. Usage is read only at the top of a document or one object down inside a known parent (arrays do not count as depth), so text that quotes a usage object, a tool schema or a guardrail trace is not counted. |

Evidence: `LlmStreamUsageScannerTest` (hostile and oversized input, cut-off streams, and
`shouldKeepABoundedUsageHoweverManyDistinctFieldsTheStreamSends`),
`HttpActionHandlerStreamedLlmUsageTest.shouldRelayAHostileStreamUnchangedAndCountNothing`,
`StreamingBodyTest.shouldKeepRelayingTheStreamWhenTheObserverThrows`.

## Embedding request limits (2026-10-03)

**Outcome:** An embedding mock's response size is bounded whatever the request asks for. The request chooses how many vectors come back (one per input) and, when the expectation sets no `dimensions`, how long each is, so without a limit a request of a few kilobytes could ask for a response of any size. A request over a limit gets a `400` in the provider's own error shape before any vector is built.

| Limit | Value | Constant (`EmbeddingWire`) |
|-------|-------|----------------------------|
| Request-supplied dimensions (`dimensions`, `outputDimensionality`, `output_dimension`, `embeddingConfig.outputEmbeddingLength`) | an integer from 1 to 8,192; the provider's own set where it has one (Titan V2 256/512/1024, Titan Multimodal 256/384/1024, Cohere v4 256/512/1024/1536) | `MAX_REQUESTED_DIMENSIONS` |
| Inputs per request (OpenAI-style `input`, Ollama `input`) | 2,048; Gemini `requests` 100; Cohere `texts` / `inputs` 96 | `MAX_INPUTS`; `GeminiCodec.MAX_BATCH_EMBED_REQUESTS` |
| Tokens per request (OpenAI and Azure OpenAI only) | 300,000, rejected only when certainly over: a token array counts its length and a text one token (its minimum), so a batch a tokeniser packed to the limit is never refused; OpenAI's own `max_tokens_per_request` error | `OpenAiChatCompletionsCodec.MAX_EMBEDDING_REQUEST_TOKENS` |
| Tokens per input (OpenAI and Azure OpenAI only) | 8,192, checked only for token arrays, which count their length; a text is never rejected for its length; OpenAI's own `invalid_request_error` text | `OpenAiChatCompletionsCodec.MAX_EMBEDDING_INPUT_TOKENS` |
| Rerank documents (Voyage only) | 1,000, Voyage's own limit | `VoyageCodec.MAX_DOCUMENTS` |
| Values per response, as JSON numbers | 262,144 by default (`llmEmbeddingMaxResponseValues`): inputs x dimensions, x the number of Cohere `embedding_types`; Titan V2 counts each array it returns. Also bounds a `dimensions` set on the expectation | `DEFAULT_MAX_TOTAL_VALUES`, `maxTotalValues()` |
| Values per response, as OpenAI `encoding_format: "base64"` | four times the JSON limit: 1,048,576 by default | `maxTotalBase64Values()` |
| Characters per input, `deterministicFromInput` only | 1,048,576 by default (`llmEmbeddingMaxInputCharacters`), above the largest input a real provider accepts | `DEFAULT_MAX_INPUT_CHARACTERS`, `maxInputCharacters()` |
| Embedding types (Cohere `embedding_types`, Titan V2 `embeddingTypes`) | only the provider's own values, so at most 5 and 2 | `EmbeddingWire.requestedTypes` |

Both configurable limits are read from the server's `Configuration` for each request, so `PUT /mockserver/configuration` changes them at runtime; a value below 1 is treated as 1.

**Why these totals.** A value costs about 21 characters as a JSON number and about 5.4 as base64 float32, so both totals bound the response body at about 5.6 MB. That is under the 10 MiB a request body may be by default (`maxRequestBodySize`), so an embedding response is never larger than a response body an expectation could have been given through the control plane. The largest real Cohere batch (96 texts at 1,536 dimensions, 147,456 values) fits; 170 OpenAI inputs at 1,536 dimensions fit as JSON numbers and 682 as base64, which the OpenAI SDKs ask for by default.

**Measured (2026-10-03, JDK 17, random full-precision vectors, the worst case for size).**

Through `HttpLlmResponseActionHandler` alone, one request:

| Response | Request | Body | Allocated | Smallest heap that completed |
|----------|---------|------|-----------|------------------------------|
| 262,144 JSON values (the limit) | 0.8 KB | 5.6 MB | 63 MB | `-Xmx48m` |
| 1,048,576 base64 values (the limit) | 3.5 KB | 5.6 MB | 55 MB | `-Xmx48m` |
| 524,288 JSON values | 1.7 KB | 11.1 MB | 120 MB | `-Xmx80m` |
| 4,194,304 JSON values | 15 KB | 89 MB | 931 MB | `-Xmx512m` (`OutOfMemoryError` at `-Xmx384m`) |

Through the built server (`mockserver-netty` jar-with-dependencies, `-Xmx256m`, the Docker images' smallest heap), requests of 2,048 inputs sent by 4, 8, 16 and then 32 concurrent clients, four rounds each; the figure is responses received in full:

| Response | Log level | 4 clients | 8 clients | 16 clients | 32 clients |
|----------|-----------|-----------|-----------|------------|------------|
| 262,144 JSON values (the limit) | `WARN` | 16/16 | 32/32 | 64/64 | 119/128 |
| 262,144 JSON values (the limit) | `INFO` | 16/16 | 32/32 | 61/64 | 110/128 |
| 1,048,576 base64 values (the limit) | `WARN` | 16/16 | 32/32 | 64/64 | 114/128 |
| 1,048,576 base64 values (the limit) | `INFO` | 16/16 | 32/32 | 61/64 | 105/128 |
| A static 5.6 MB `httpResponse` body, for comparison | `INFO` | 16/16 | 32/32 | 64/64 | 114/128 |
| 131,072 JSON values | `INFO` | 16/16 | 32/32 | 64/64 | 128/128 |

In every run the server stayed up and answered `PUT /mockserver/status` afterwards; a request that failed got no response, and the server log shows `OutOfMemoryError` on request threads. A request one dimension over the limit (2,048 inputs at 129) got its `400` at every concurrency, with no `OutOfMemoryError`. A limit of 524,288 JSON values (an 11 MB body, which the control plane could not have been given by default) was tried first and rejected on this measurement: at `-Xmx256m` and `WARN` it lost requests from 4 concurrent clients (15/16, then 29/32 at 8 and 54/64 at 16).

**Error text.** The `400` body is built with Jackson and names only the parameter, the limit and counts; no request value is copied into it.

**Residual:**
- **(MEDIUM) The limits are per request; nothing bounds the embedding responses in flight.** As the table shows, at the smallest heap 16 or more concurrent requests at the limit can exhaust it, as 32 concurrent requests for a static body of the same size do. Building a response costs more than serving a stored one (about 60 MB allocated per request at the limit), so embeddings reach that point at about half the concurrency. An in-flight budget like the one for inbound request bodies would close this; it is not part of this change.
- **With `deterministicFromInput`, allocation scales with the size of each input's text; `llmEmbeddingMaxInputCharacters` bounds it (2026-10-12).** `EmbeddingVectors.build` (`generateDeterministicVector`, reached through `EmbeddingWire.build`) tokenises each input and builds word and character n-gram maps before hashing them into the vector. The reviewer of this change measured, on a single thread with all-unique short tokens, about 46 MiB allocated for a 256 KiB input, 175 MiB for 1 MiB and 355 MiB for 2 MiB: roughly 175 bytes per input byte. A request at the default 10 MiB `maxRequestBodySize` would therefore allocate on the order of 1.75 GB over its lifetime (extrapolated, not measured). The figures above of about 60 MB allocated per request at the limit, and of a request completing at `-Xmx48m`, hold for random vectors only. The mode is opt-in on the expectation and predates this change on the OpenAI path, where a top-level `input` was already embedded; this change widens it to Gemini, Titan, Cohere and Ollama `prompt`, whose text was not read before. Each input is now capped at 1,048,576 characters by default, which the reviewer measured at 175 MiB allocated for one all-unique input, and a longer one gets a `400` before it is hashed. The default sits above the largest input a real provider accepts (Cohere Embed v4, 128k tokens), so no request a provider would take is refused; lower it to bound the cost further. 8.0.0 hashed inputs of any length. The cap is per input: a request of many inputs under the cap still allocates in proportion to its whole text over its lifetime, but holds only one input's features at a time, and the request body itself is bounded by `maxRequestBodySize`. An image input (Bedrock Cohere `images`, or a v4 `inputs` `image_url` part) is hashed from a SHA-256 digest of its data URI, so its size does not matter. The v3 `images` description decodes only header bytes, straight from the base64 text, and walks at most 1,024 JPEG segments, so it allocates nothing in proportion to the image and a malformed header yields zeros rather than an error. <!-- version-ref:keep -->
- (LOW, resolved 2026-10-12) The limits were fixed. They are now configurable: a LangChain `OpenAIEmbeddings` batch at its default `chunk_size` of 1,000 and 1,536 dimensions (1,536,000 base64 values) is over the default limit, and fits with `llmEmbeddingMaxResponseValues` at 384,000 or more. Raising the limit raises the per-request cost measured above in proportion, and the in-flight residual above still applies.
- (LOW) Two Cohere embedding types for a full batch of 96 at 1,536 dimensions (294,912 values) is over the limit; one type, or two at 1,024 dimensions, fits.
- (LOW, resolved 2026-10-12) A zero or negative `dimensions` on the expectation was not validated. The expectation schema now requires at least 1, and an expectation that reaches a codec without schema validation fails the request with a `502`.

## Outbound prompt redaction — `generateExpectation` and drift (2026-09-28)

**Outcome:** Prompts built for an external LLM backend are redacted before they leave the process. This closes a gap where `PUT /mockserver/generateExpectation`, with an LLM backend configured, sent the unmatched request's `Authorization`, `Cookie` and API-key headers, up to 2,000 characters of body, and the paths of up to 10 existing expectations to a third-party service unredacted.

Redaction here is **always on** and **independent of `redactSecretsInLog`** — that setting governs the local event log, whereas this governs data leaving the process to a third party — and it operates on **copies**: the served request, the event log and the returned expectations are never mutated.

### What is redacted before a prompt is sent

| Element | Rule |
|---------|------|
| Request / context headers | Values of `Authorization`, `Proxy-Authorization`, `Cookie`, `Set-Cookie`, `x-api-key`, `api-key` (reusing `FixtureRedactor.defaultSensitiveHeaders()`), plus `X-Auth-Token`, `X-Access-Token`, `X-Amz-Security-Token`, `X-Csrf-Token` and the configured `dataPlaneApiKeyAuthenticationHeader`, matched case-insensitively, are replaced with `FixtureRedactor.REDACTED_PLACEHOLDER` (`***REDACTED***`). Header names are kept; a multi-value header is collapsed to one masked value. The extra headers are added in `LlmPromptRedactor` only — `FixtureRedactor`'s defaults are unchanged. Every other header value has `key=value` credentials, JWTs and URL userinfo masked (so `X-Session: eyJ…` or `X-Debug: token=…` is masked); before 2026-10-13 those values were sent as-is. |
| Query string | Values of parameters named like a credential are masked via `FixtureRedactor`: its query defaults (`key`, `api_key`, `apikey`, `access_token`, `token`, `signature`, …) plus every JSON credential field name below (`password`, `client_secret`, `refresh_token`, `id_token`, `code_verifier`, …). Every other value has `key=value` credentials, JWTs and URL userinfo masked (so `?state=eyJ…` is masked). Before 2026-10-13 only the query defaults were masked. |
| OAuth authorization code | `code` is not a credential name on its own (`?code=GB` is often a country or voucher code), so it is masked only beside an OAuth signal: a path segment containing `callback`, `oauth`, `oidc`, `authorize` or `authorise`; a `state`, `session_state`, `redirect_uri`, `client_id`, `code_verifier` or `iss` (RFC 9207 issuer) parameter; or `grant_type=authorization_code`. The `code` query parameter is masked when the signal is in the request's path or query; `code=` in a form body when it is in the body, path or query; a JSON `code` field when it is in a sibling field, the path or the query; and `code=` in a header value, a path, a response body or a drift value (such as a redirect `Location`) when it is in the same text. A free-text `code=` must start the text or follow `?`, `&`, `;`, `#` or whitespace, so `error_code=` is never treated as `code`. The trade-off is deliberate: a non-OAuth `code` beside a parameter that happens to be called `state` (`?code=GB&state=CA`) is masked too, which costs the model a little context but cannot leak a credential. Before 2026-10-13 an authorization code was sent in full. The PKCE `code_verifier` is a credential field in its own right (masked whole in a query, form or JSON body). Known gaps, not closed: (1) a code on a generic path with no signal at all (`/auth?code=X` from a PKCE flow that sends neither `state` nor `iss`) is sent; (2) a header value such as `Referer` is checked against its own text only, so a code in it is sent when the signal is elsewhere in the request; (3) a JSON `code` field nested under an object whose `state` sits on the parent, not a sibling, is sent. |
| JSON body | Values of credential-like fields (`password`, `passwd`, `secret`, `token`, `access_token`, `refresh_token`, `id_token`, `client_secret`, `api_key`, `apikey`, `authorization`, …) masked at any depth. Every JSON string leaf is also swept: a stringified-JSON value (`{"data":"{\"password\":\"x\"}"}`) is parsed and redacted recursively, and `key=value` credentials inside a string leaf are masked. |
| JWTs | `eyJ`-prefixed three-segment base64url tokens masked anywhere in a body. |
| URL userinfo | `scheme://user:pass@host` → `scheme://***REDACTED***@host` in request path, context paths and bodies. |
| Request and context paths | URL userinfo, JWTs (a magic-link token in a path segment) and `key=value` credential pairs (a `;token=` matrix parameter) are masked (`maskPath`). Before 2026-10-13 a path got URL-userinfo masking only. |
| Drift record values | The whole value is masked when the field is a credential-like body field or a sensitive response header (`header.set-cookie`, `header.authorization`, `header.x-auth-token`, …, matched case-insensitively against the header set above, including the configured `dataPlaneApiKeyAuthenticationHeader`). Any other value has `key=value` credentials, JWTs and URL userinfo masked (`maskDriftValue`). Before 2026-10-13 a drift value got JWT and URL-userinfo masking only, so a `Set-Cookie` session id or a `token=` pair in a changed response header reached the backend. |
| Non-JSON body | `key=value` credential pairs (same field set) masked in form / plain-text bodies. A field matches when it is not preceded by an ASCII letter or digit (so `my_password=` / `x-api_key=` match) and is a whole word (so `tokenizer=on` is not treated as `token`). |
| Unparseable JSON | A body that declares or looks like JSON but cannot be parsed is **dropped** (`[body omitted: could not be parsed for redaction]`) — fail closed. |
| Binary body | Omitted from the prompt. |
| Body length | Redacted body then truncated to 2,000 characters. |

Implemented in `org.mockserver.llm.LlmPromptRedactor`, applied by `StubGenerationPromptBuilder` (constructed with the configured data-plane API-key header name in `HttpState.handleGenerateExpectation`) and by `SemanticDriftExtension.buildPrompt` (constructed with the same header name in `DriftWiring.semanticExtension` (`org.mockserver.lifecycle`); drift analysis sends response bodies and drift record values; a header drift record's value is the response header's value, so its field name is checked against the sensitive header set). Header and query masking reuse `FixtureRedactor`; JSON-tree field masking, stringified-JSON descent, JWT, URL-userinfo and non-JSON `key=value` masking are done in `LlmPromptRedactor` because a prompt is free text rather than a structured expectation.

**Explain narration (2026-10-14).** `PUT /mockserver/explain?narrate=true` and `PUT /mockserver/explainUnmatched?narrate=true` send the deterministic explanation JSON to the configured backend (`ExplanationNarrator.buildPrompt`), but only when the operator sets `llmExplainNarrationEnabled` (default `false`): a backend configured for `/generateExpectation` or drift does not by itself let explanations leave the process, and explain is a READ for control-plane authorisation. The match differences in it quote the recorded request and the expectations verbatim when `redactSecretsInLog` is off, so before sending: every string is scrubbed of the credential values (sensitive headers including the data-plane API-key header, cookies, credential-named query parameters and body fields) that the explained requests and the evaluated expectation matchers carry, using this redactor's sets via `LlmPromptRedactor.sensitiveValues`; each string then goes through `redactText` BEFORE it is capped at 500 characters (capping first could cut a JWT inside its header, leaving a fragment the JWT pattern no longer matches; `ExplanationNarratorTest.shouldMaskAJwtThatStraddlesTheStringCap`); the whole JSON then goes through `redactText` again (credential-named fields, stringified JSON, JWTs, `key=value` pairs, URL userinfo) and is capped at 16,000 characters. A value shorter than `FixtureRedactor.MIN_SCRUBBED_VALUE_LENGTH` (4) is not searched for in free text, as in the event log. The prompt also tells the model to treat the JSON as data, and the answer is returned labelled as AI-generated beside the unchanged facts. `HttpStateExplainNarrationTest.shouldRedactCredentialsFromThePromptThatTheFactsQuoteVerbatim` asserts the facts quote the secrets and the prompt sent on the wire does not.

**Note on `EmbeddedCredentialRedaction`:** despite its name it masks configuration credentials by field/header *name* only and, by its own documented design, does **not** redact credentials embedded in a URL. URL-userinfo masking for prompts is therefore implemented in `LlmPromptRedactor`, not delegated to that class.

## Event-log redaction of proxied LLM traffic — `redactSecretsInLog` (2026-09-28)

**Outcome:** With `redactSecretsInLog` enabled, a proxied or forwarded exchange — including a recorded LLM call carrying `Authorization`, `x-api-key`, a Gemini `?key=` or a session cookie — no longer leaves any credential in the event log's rendered forms, nor does a request that fails to match an expectation or a failed response verification. Before this, the masked `httpRequest` sat beside unmasked copies of the same data on the same entry.

| Leak (before) | Surfaces reached | Fix |
|---------------|------------------|-----|
| The curl argument of every `FORWARDED_REQUEST` entry (all headers, cookies, query string, body) was rendered as text, which redaction never inspected | console log (full and compact), retrieve `LOGS` (text and `LOG_ENTRIES` `message`/`arguments`), dashboard | `DeferredLogArgument` holds the request and address; `LogEntry` renders it from the redacted request with the effective configuration. Its `toString()` is always redacted (fail closed) |
| The synthetic `expectation` field of a JSON log entry was built from the raw request/response | retrieve `LOGS` `LOG_ENTRIES` | `LogEntry.getRedactedExpectation(configuration)` rebuilds it from the redacted copies |
| `FixtureRedactor` masked the `Cookie`/`Set-Cookie` headers but not the parsed cookie list | JSON `cookies`, HAR, retrieve `REQUESTS`, `format=CURL` (rebuilds `Cookie` from the list), dashboard, disk archive, recorded expectations | cookie values masked whenever `Cookie` / `Set-Cookie` is a sensitive header |
| The message was rendered with the static setting and memoised; the dashboard DTO read arguments without its configuration | everything that shows the message, when redaction was enabled over `PUT /mockserver/configuration` | configuration threaded through message building; the memo is tagged with the redaction settings it was rendered under |
| A matcher's "because" (and each TRACE match difference) quotes the header, cookie and query values it compared: `expected: Bearer expected found: Bearer <token>` | console, retrieve `LOGS` (text and `LOG_ENTRIES` `because`/`message`/`arguments`), dashboard, MCP `retrieve_logs` | at render time, free text on an entry is scrubbed of the credential values that entry's own request and response carry (`FixtureRedactor.sensitiveValues`, `SensitiveValueMatcher#scrub`); matching and the stored entry are unchanged |
| `explainUnmatched` differences and hints quote the recorded unmatched request's values | `PUT /mockserver/explainUnmatched`, MCP `explain_unmatched_requests` and `suggest_expectation_fix`; `explain` and MCP `explain_request` likewise; MCP `explain_forward_failure` renders the logged reason through the entry's own scrub | the same scrub, per recorded request |
| A failed response verification (single and sequence) serialized the raw recorded responses into the failure returned to the caller, the closest-response diff and the logged entry | verify response body, console, retrieve `LOGS`, dashboard | matched on the raw pairs, shown from redacted copies — as the request side already did |
| Requests and responses nested in a log argument (a list, an array, a request/response pair) were not redacted | as above | redacted element by element |
| Error entries concatenated the whole request into the message format with no arguments (failed forward — reachable by a client sending a missing or non-numeric-port `Host` — request processing in Netty and both WARs, relay, request, response and request/response serializers), and the missing-`Host` exception embedded the whole request | console (message and stack trace), retrieve `LOGS` (`messageFormat`, `message`, `throwable`), dashboard (message parts and stack trace) | the sites pass the request/response as an argument and attach it to the entry (the list serializers attach every request, and the response when there is one); `messageFormat` is scrubbed even with no arguments; a throwable whose message quotes a credential is rendered as a `RedactedThrowable` copy (cycle-safe); the `Host` exception names only method and path |
| An undecodable query string (`?q=50%`, `%zz`) was logged at ERROR with the raw query and the decoder's exception quoting the full URI, on an entry with no request (`ExpandedParameterDecoder`, from both the Netty and servlet request decoders) | console, retrieve `LOGS`, dashboard, MCP — at the default log level | the parameters, split without decoding, are attached as the entry's request, so sensitive values are masked in the logged string and the exception message; with redaction off the entry is unchanged |
| Mapping failures quoted a Netty request/response, whose text lists every header, with nothing attached | console, retrieve `LOGS`, dashboard | `NettyMessageForLog` attaches the headers, raw path and raw query, without decoding |
| The regex matcher's DEBUG "would match" and pattern-error entries quoted the request value it compared, with no request | console, retrieve `LOGS`, dashboard (DEBUG) | passed as a `SensitiveLogValue`: `***REDACTED***` with redaction on, unchanged with it off, and scrubbed from the rest of the entry's text |
| The compact console form shortened each argument (to about 117 characters) before scrubbing, leaving the prefix of a long credential such as a JWT | console with `compactLogFormat` | each argument is redacted and scrubbed before it is shortened |
| The CONNECT relay's write-failure entry (broken pipe, connection reset) concatenated the relayed Netty response, whose text lists `Set-Cookie` / `Authorization`, into its format | console, retrieve `LOGS`, dashboard (ERROR) | logged as `...:{}` with the response's headers attached; a message with no headers is a `SensitiveLogValue` |
| A plain-text request with a method the HTTP decoder does not recognise (`PROPFIND`, `REPORT`, `PURGE`, `QUERY`) goes down the binary path, which logged it as hex and as UTF-8 text at INFO, headers included (as did the binary proxy's other entries and the HTTP client's binary send) | console, retrieve `LOGS`, dashboard | every hex dump and UTF-8 rendering of a binary payload is a `SensitiveLogValue` |
| (New surface, not a past leak.) The dashboard shortens bodies over 64 KiB in its live updates and loads one whole with `GET /mockserver/logEntryBody?id=&part=request\|response`, which returns a log entry's own request or response in full | dashboard (Load Full Body) | served through the same `getHttpUpdatedRequests` / `getHttpUpdatedResponse(configuration)` view as the dashboard frame, behind control-plane authentication; the frame's cut is applied after redaction, so a cut never shows a value redaction hides. Covered by `LogEntryRedactionSurfacesTest` (with redaction on, static and instance, and off) and `DashboardFrameBodyCapTest` |

Free-text scrubbing replaces exact occurrences (and their JSON-escaped form) of: sensitive header values and the credential after a scheme such as `Bearer`, cookie values (from the list and from the `Cookie` / `Set-Cookie` header text), sensitive query-string values and configured `fixtureBodyRedactFields` values. Values shorter than `FixtureRedactor.MIN_SCRUBBED_VALUE_LENGTH` (4), bare cookie values shorter than `MIN_SCRUBBED_COOKIE_VALUE_LENGTH` (8) and the words `true`, `false` and `null` are not searched for in free text — they are still masked in their own fields — because they would mostly mask unrelated words and numbers; session identifiers are generated tokens far longer than 8, while preference cookies (`lang=en`, `theme=dark`) are short, and their `name=value` pair is still scrubbed. The flip side: a short credential can mask identical text elsewhere in the same message. With redaction off nothing is scrubbed.

**Structural protections**, so a new site is covered without being found first:
- The credential values a render scrubs are collected from the entry's own requests and response **and** from every request, response, request/response pair, expectation, log entry and `SensitiveLogValue` among its arguments, also inside collections and arrays: a site that passes a request only as an argument has the rest of its text (another argument, the format, the exception) scrubbed as if it had attached it.
- `LogMessageFormatGuardTest` (core, scans every module's main sources) fails the build when a `setMessageFormat` argument concatenates, `String.format`s or `formatLogMessage`s a value that is not on its reasoned allow-list, or when a `setArguments` / `setBecause` argument is string concatenation of such a value or turns a value into text (`String.valueOf`, `toString()`, `String.format`, `formatted`) outside its own reasoned allow-list (constants, exception messages, identifiers, network endpoints, file paths, counts, configuration values, class names, enums, and named pass-through variables in files whose callers were reviewed); it also fails on an allow-list entry that no longer matches anything.

Tested by asserting on whole outputs rather than fields (`LogEntryRedactionSurfacesTest`, `DashboardRedactionFrameTest`, `RedactSecretsInLogProxyIntegrationTest` through a real proxied request), for both the static and the instance configuration paths, and with redaction off against the previous output.

**Residual (LOW):**
- Scrubbing finds a credential only as it appears in the entry's own request or response. A value a template transforms before writing it (encoded, truncated, re-cased) is not recognised in a `TEMPLATE_GENERATED` entry's output; a value copied verbatim is.
- The `action` argument of a forwarded entry (a user-authored forward/override definition) and a real expectation attached to an entry are logged as authored: they are configuration, not captured traffic. A credential they contain that the request itself does not carry is not masked.
- Values shorter than 4 characters are masked in their own fields but not searched for in free text.
- Values longer than 4,096 characters, and values beyond the first 10,000 or 262,144 characters an entry carries (a one-time WARN says when that happens), are masked in their own fields but not searched for in free text. Such a value is a structured body field or a whole message rather than a credential, and an entry's own requests and responses are redacted field by field whatever they carry; the gap is free text (a matcher's "because", a template's output, an exception message) quoting one of them.
- `redactSecretsInRecordedExpectations` masks headers, cookies and the query string only, not `fixtureBodyRedactFields`.
- `debugMismatch` returns differences for the request the caller submits in the call, not for a logged request, so it is not scrubbed.
- The serializers' thrown exceptions (e.g. `Exception while serializing HttpRequest to JSON with value …`) still quote the value, because callers and a test depend on that text; the serializer's own log entry carries the request, so that throwable is scrubbed there, but a caller that logs the exception without the request is not.
- A user-registered log event listener calling `logEntry.getMessage()` without a configuration follows the static store, not the server's instance setting.
- `JsonSchemaBodyDecoder`'s XML-to-JSON conversion error quotes the XML body; the request is attached, but configured `fixtureBodyRedactFields` are only found in JSON and SSE bodies, so XML field values are not scrubbed from that text.
- `MediaType` warnings quote a `Content-Type` charset or parameter string, and the configuration, expectation, verification and OpenAPI serializers and matchers quote user-authored definitions; none is a credential place, so none is masked.
- The regex matcher's pattern-error entries are converted but effectively unreachable: `NottableString` handles an invalid pattern itself.
- The TRACE wire dump (`org.mockserver.logging.LoggingHandler`, enabled only at TRACE) writes raw bytes to slf4j directly, outside the event log, and is not redacted.
- `LlmConversationMatcher` logs up to 256 characters of an unparseable LLM request body at DEBUG straight to slf4j (outside the event log), unredacted.
- A value turned into text before it reaches a log call (a local `String` built from a request), or concatenated with another non-literal operand (`prefix + request`, no string literal in the chain), is not seen by the guard.
- Bodies, paths and other fields that are not sensitive by name are masked only through `fixtureBodyRedactFields`: a response body that quotes a request's header credential (an echo endpoint returning `Authorization`) is shown as-is on every surface, because requests and responses are redacted field by field, not scrubbed as text.
- The guard judges a pass-through variable by its name in a reviewed file, not by what was concatenated into it; a new caller of such a helper is not re-checked.

**Behaviour changes with redaction off:** the forward-action log's "in json" part now shows the request (it showed the response twice); the error entries above render the request as a formatted argument rather than inline, and the regex matcher's pattern-error entries and the serializers' "exception while serializing ... with value" entries likewise; the converted error entries (forward failure, request processing, relay, serializers, undecodable query string, Netty mapping failures, CONNECT relay write failure) now **attach** the request or response they describe, so it appears in the `LOG_ENTRIES` JSON (`httpRequest` / `httpResponse`) and in the dashboard's request pane, and a request-filtered retrieve of `LOGS` now matches them; the missing-`Host` exception message names method and path instead of dumping the request; the streaming-decision DEBUG line shows the raw path without the query string. The undecodable-query entry's text is unchanged; it now also attaches the parameters split without decoding.

**Cost with redaction on:** in proportion to what an entry quotes, and bounded. Requests, responses, pairs and expectations are redacted field by field; the format and each free-text or other argument are scrubbed on their own through one `SensitiveValueMatcher` per render. It checks short texts value by value and, once that work would pass the cost of building it, an Aho-Corasick automaton of the entry's values; either way it masks the union of every value's occurrences (overlapping ones merge into one placeholder). The automaton's children are held per node in character order, so each character of the text costs at most a binary search over one node's children, whatever the alphabet. The values it searches for are bounded: a value longer than 4,096 characters is not searched for, and at most 10,000 values and 262,144 characters per render are (kept in order: the header, cookie and query-string values of every request and response, then body-field values, then `SensitiveLogValue` texts); the automaton holds at most about 18 bytes per character of those values, plus about 14 while it is built. A sensitive body field that holds an object contributes its leaves and, once, its whole text (at most 4,096 characters, found without serializing more), not the text of every nested level. (Before: scrubbing the formatted whole, then checking every value against each long text, cost values x length: 11 s for a verification failure quoting 10,000 requests, 3.5 s for the curl of a 1.9 MB body with 32,000 masked fields, 3.9 s for 4,000 expectations. The first automaton walked a list of each node's children, 21 s for 20,000 values with distinct CJK first characters against 1M characters of CJK text; and collecting a nested sensitive object's text at every level ran out of memory at `-Xmx2g` on a 255 KB body.) Measured now: `LOG_ENTRIES` retrieve of 2,000 proxied entries 62 ms against 39 ms; verification failure quoting 1,000 / 3,000 / 10,000 requests about 18 / 43 / 118 ms against 10 / 22 / 47 ms; 4,000 expectations 67 ms against 30 ms; the 2.5 MB-body curl 377 ms against 47 ms; a forwarded 1 MB body with 40,000 masked values allocates about 103 MB against 96 MB off (337 MB without the bounds), and a 900-level nested sensitive object 23 MB against 70 MB (184 MB collecting every level). `LogEntryRedactionSurfacesTest` fails if a 5,000-request verification failure, the curl of a 20,000-item body, a list of 4,000 expectations, a 900-level nested sensitive body or a 1 MB flat sensitive body renders more than 5x (plus 250 ms) slower with redaction on, or the last two allocate more than 1.5x (plus 32 MB) what they allocate with it off. `FixtureRedactorTest` checks the automaton and the value-by-value path both mask exactly the union of occurrences on a random corpus that includes overlaps, non-ASCII text and surrogate pairs; that both give exactly what the previous value-by-value replacement did where occurrences do not overlap (where they do, it could leave part of a value); and that 1M characters of non-ASCII text scrub in under a second against 20,000 values that give one node (the root, or a node after an ASCII prefix) 20,000 non-ASCII children.
