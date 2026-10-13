# LLM Provider Wire-Shape Fixes

## Outcome

Every `httpLlmResponse` provider should return what the real provider API returns, and accept
what real SDKs send, so client libraries, agent frameworks and gateways work against a mock
without special cases. GitHub discussion
[#2757](https://github.com/mock-server/mockserver-monorepo/discussions/2757) found that
`BEDROCK` returned Anthropic's Messages shape where the docs promised the AWS Converse API. An
audit of every provider on 2026-10-03 then found the same class of bug elsewhere. The items
below are worked in batches, most SDK-breaking first. The release is **not** gated on finishing
this plan: each item ships when it lands. When every item is closed this file is deleted in the
same commit. Updated 2026-10-13: every item listed below is still open; closed items have been removed.

## Why this class of bug got through

```mermaid
flowchart LR
    C["Codec emits a shape"] --> G["Golden files and tests\nrecorded from the codec"]
    G --> P["Tests pass"]
    R["Real provider API"] -. "never compared" .-> G
```

The codec tests derive their expected values from the codec itself. Examples:
`shouldEncodeCanonicalTokenUsageCounts` checks only non-streaming bodies and excludes the
OpenAI-compatible aliases, and the Responses tests set `call_id` equal to `id`. Item 16 closes
this gap.

## What remains

| # | Item | Batch |
|---|---|---|
| 39l | Forwarded calls to Vertex AI's OpenAI-compatible endpoint are counted in tokens but not priced | 5 |
| 39m | Mocked Bedrock InvokeModelWithResponseStream streams have no amazon-bedrock-invocationMetrics on their last chunk | 5 |
| 33f | The chaos content-filter Anthropic refusal body sends a short usage object | 5 |

## Items

| # | Problem | Fix |
|---|---|---|
| 39l | Found while closing item 39d: forwarded calls to Vertex AI's OpenAI-compatible endpoint (`/endpoints/openapi/chat/completions`, `/responses`) and to AI21 Jamba are now sniffed as `OPENAI` (or `OPENAI_RESPONSES`) and counted in tokens, but the model names they carry have a publisher prefix (`google/gemini-2.5-flash`, `meta/llama-3.3-70b-instruct-maas`) or a version suffix (`jamba-1.5-large@001`), which the OpenAI pricing table does not match, so a Gemini call through this endpoint adds no cost and does not count towards `llmCostBudgetUsd`. | Price these calls by the vendor named in the prefix, as `LlmPricing` already does for OpenRouter (`openRouterPricing`), at Vertex AI's own list prices where they differ, and decide whether a `-maas` suffix or an `@version` suffix is stripped. Check each rate against Vertex AI's pricing page. |
| 39m | Found while closing item 39i: real Bedrock adds `amazon-bedrock-invocationMetrics` (`inputTokenCount`, `outputTokenCount`, `invocationLatency`, `firstByteLatency`) to the event inside the last `chunk` of an InvokeModelWithResponseStream stream, for Claude on its `message_stop` event. langchain-aws (`llms/bedrock.py`, `prepare_output_stream`) builds a streamed call's `usage_metadata` only from that object, at `message_stop` for Claude. `BedrockCodec.encodeStreaming` wraps AnthropicCodec's events unchanged, so a mocked stream has no such object and LangChain's `ChatBedrock` (non-Converse) may report no usage for it. | Verify first against langchain-aws: if `ChatBedrock` streaming reports no usage from a mocked stream, add the object to the event of the mock's last chunk (`message_stop`), with counts equal to the completion's usage, and test it as the real wire shape. |
| 33f | Found while closing item 33e. The Anthropic and Bedrock InvokeModel codecs now send the full Usage object the real APIs send (both cache counts, 0 when unset, `cache_creation`, `service_tier`, and `inference_geo` for the Claude API only). The fixed refusal body a chaos content-filter block returns (`LlmRefusalPresets.ANTHROPIC_REFUSAL_BODY` / `BEDROCK_INVOKE_REFUSAL_BODY`) still sends only `{"input_tokens":0,"output_tokens":0}`. It was left alone because 33e was scoped to the codec. | Build the refusal body's usage the way `AnthropicCodec` does (Claude API with `inference_geo`, Bedrock without) and assert it in the content-filter wire-shape tests. |
