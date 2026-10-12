package org.mockserver.llm;

/**
 * The wire format used by a provider for streaming responses.
 * <ul>
 *   <li>{@link #SSE} — Server-Sent Events ({@code text/event-stream}): each chunk
 *       is emitted as {@code data: <payload>\n\n} with optional {@code event:},
 *       {@code id:}, and {@code retry:} fields.</li>
 *   <li>{@link #NDJSON} — Newline-Delimited JSON ({@code application/x-ndjson}):
 *       each chunk is a single JSON object followed by a newline character
 *       ({@code <json>\n}). Used by Ollama.</li>
 *   <li>{@link #AWS_EVENT_STREAM} — AWS event-stream binary framing
 *       ({@code application/vnd.amazon.eventstream}): each chunk is a binary
 *       message with prelude CRC32, typed headers, a base64-wrapped JSON payload,
 *       and a trailing message CRC32. Used by Bedrock
 *       {@code InvokeModelWithResponseStream}.</li>
 *   <li>{@link #AWS_CONVERSE_EVENT_STREAM} — the same AWS event-stream binary
 *       framing, as used by Bedrock {@code ConverseStream}: each frame's
 *       {@code :event-type} header is the event name ({@code messageStart},
 *       {@code contentBlockDelta}, ...) and the payload is the event's raw JSON,
 *       with no base64 {@code bytes} wrapper.</li>
 *   <li>{@link #JSON_ARRAY} — one JSON array written incrementally
 *       ({@code application/json}): {@code [} before the first chunk, {@code ,\r\n}
 *       between chunks and {@code ]} at the end. Used by Gemini
 *       {@code streamGenerateContent} when the request does not ask for {@code alt=sse}.</li>
 * </ul>
 */
public enum StreamingFormat {
    SSE,
    NDJSON,
    AWS_EVENT_STREAM,
    AWS_CONVERSE_EVENT_STREAM,
    JSON_ARRAY
}
