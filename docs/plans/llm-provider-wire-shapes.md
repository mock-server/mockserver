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
| 39i | Forwarded Bedrock InvokeModelWithResponseStream calls to non-Anthropic models are not counted | 5 |
| 39j | No include_usage hint when a Bedrock OpenAI-endpoint stream reports no usage | 5 |
| 39k | Mocked Bedrock InvokeModel responses send no prompt-cache token headers | 5 |
| 39l | Forwarded calls to Vertex AI's OpenAI-compatible endpoint are counted in tokens but not priced | 5 |
| 33e | Anthropic usage omits fields the real API always sends | 5 |

## Items

| # | Problem | Fix |
|---|---|---|
| 39i | Found while closing item 39g: The final chunk of an InvokeModelWithResponseStream stream carries `amazon-bedrock-invocationMetrics` with `inputTokenCount` and `outputTokenCount`. langchain-aws (`llms/bedrock.py`, `_get_invocation_metrics_chunk`) reads it for every provider, including gpt-oss ("openai"), Qwen, Mistral and Llama. LlmStreamUsageScanner reads only a `usage` object, so these streams are not counted. docs/code/llm-mocking.md and the consumer page list this as a limit. | capture `amazon-bedrock-invocationMetrics` (exactly 32 bytes, the scanner's MAX_NAME_BYTES) inside the decoded `bytes` payload, map inputTokenCount/outputTokenCount in BedrockLlmClient.parseUsage, and keep Anthropic `usage` first when both are present. Verify the field against a recorded stream or the AWS SDK before relying on it. |
| 39j | Found while closing item 39g: HttpActionHandler.logStreamedUsageUnavailable adds the "set stream_options.include_usage" hint only for OPENAI and non-Responses AZURE_OPENAI. A Bedrock /openai/v1/chat/completions stream without include_usage gets the generic "not counted" line. | Extend usageIsOptIn to a BEDROCK request whose path ends in /chat/completions. This was left out of 39g because it changes HttpActionHandler, a file shared with parallel units. |
| 39k | Found while closing item 39e (low priority): real Amazon Nova InvokeModel responses also carry `X-Amzn-Bedrock-Cache-Read-Input-Token-Count` and `X-Amzn-Bedrock-Cache-Write-Input-Token-Count` (recorded in OpenTelemetry Java instrumentation testinvokemodelamazonnova.yaml), and langchain-aws and openllmetry read them. No recorded Anthropic-on-Bedrock response with them was found, so item 39e does not send them; a mocked Claude completion with cached tokens reports them only in the body. | Find a recorded Claude InvokeModel response with prompt caching to confirm whether Bedrock sends the cache headers for Anthropic models, then send them from `BedrockCodec.encode` when the usage has cached or cache-creation tokens. |
| 39l | Found while closing item 39d: forwarded calls to Vertex AI's OpenAI-compatible endpoint (`/endpoints/openapi/chat/completions`, `/responses`) and to AI21 Jamba are now sniffed as `OPENAI` (or `OPENAI_RESPONSES`) and counted in tokens, but the model names they carry have a publisher prefix (`google/gemini-2.5-flash`, `meta/llama-3.3-70b-instruct-maas`) or a version suffix (`jamba-1.5-large@001`), which the OpenAI pricing table does not match, so a Gemini call through this endpoint adds no cost and does not count towards `llmCostBudgetUsd`. | Price these calls by the vendor named in the prefix, as `LlmPricing` already does for OpenRouter (`openRouterPricing`), at Vertex AI's own list prices where they differ, and decide whether a `-maas` suffix or an `@version` suffix is stripped. Check each rate against Vertex AI's pricing page. |
| 33e | Found while closing item 33d. Two contracts added by 33d replay live responses recorded on 2026-09-28 (`provider-contracts/anthropic/stream-text-recorded`, `thinking-text-recorded`) and allow, with `openItem` "33e", these gaps: the whole message and `message_start` usage lack `cache_creation` (`{ephemeral_5m_input_tokens, ephemeral_1h_input_tokens}`), `service_tier`, `inference_geo`, and `cache_creation_input_tokens` / `cache_read_input_tokens` when they are zero (MockServer emits them only when non-zero); the `message_delta` usage lacks `input_tokens`, `cache_creation_input_tokens` and `cache_read_input_tokens`; and `usage.output_tokens_details.thinking_tokens` is never emitted even when the completion sets `reasoningTokens`. The Messages API reference marks all of these required (nullable). | Fix in `AnthropicCodec`, check what Bedrock InvokeModel sends (the 2026-09-29 Bedrock recordings have `cache_creation` and `service_tier` but no `inference_geo` in `message_start`), and delete the allowances from the two contracts.  Also emit the Bedrock `message_delta` usage `output_tokens_details`, which the Bedrock recording carries. |
