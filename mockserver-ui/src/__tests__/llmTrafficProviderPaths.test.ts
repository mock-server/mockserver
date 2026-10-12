import { describe, it, expect } from 'vitest';
import { parseTraffic, getTokenSummary, getModelLabel, getNumericTokens } from '../lib/llmTraffic';
import { decodeAwsEventStream } from '../lib/awsEventStream';
import { extractLlmExpectationFromCapture, isLlmTraffic } from '../lib/expectationFromCapture';

// Wire shapes below follow the provider references:
// - Bedrock Converse / ConverseStream / InvokeModelWithResponseStream:
//   https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_Converse.html (and _ConverseStream, _InvokeModelWithResponseStream)
// - AWS event-stream framing: prelude (total, headers length, CRC32), headers, payload, CRC32
// - Ollama /api/chat streaming (NDJSON, stream defaults to true): https://docs.ollama.com/api/chat
// - Ollama /api/generate (prompt in, response out, NDJSON when streamed): https://docs.ollama.com/api/generate
// - Gemini usageMetadata (thoughtsTokenCount apart from candidatesTokenCount): https://ai.google.dev/api/generate-content
//   and https://ai.google.dev/gemini-api/docs/pricing ("Output price (including thinking tokens)")
// - Vertex AI generateContent / streamGenerateContent and Claude rawPredict on Vertex

// ---------------------------------------------------------------------------
// AWS event-stream encoder (independent of the decoder under test)
// ---------------------------------------------------------------------------

function crc32(bytes: Uint8Array): number {
  let crc = ~0;
  for (const b of bytes) {
    crc ^= b;
    for (let k = 0; k < 8; k++) crc = (crc >>> 1) ^ (0xedb88320 & -(crc & 1));
  }
  return ~crc >>> 0;
}

function frame(headers: Record<string, string>, payload: string): Uint8Array {
  const enc = new TextEncoder();
  const headerParts: number[] = [];
  for (const [name, value] of Object.entries(headers)) {
    const n = enc.encode(name);
    const v = enc.encode(value);
    headerParts.push(n.length, ...n, 7, (v.length >> 8) & 0xff, v.length & 0xff, ...v);
  }
  const body = enc.encode(payload);
  const total = 12 + headerParts.length + body.length + 4;
  const out = new Uint8Array(total);
  const view = new DataView(out.buffer);
  view.setUint32(0, total);
  view.setUint32(4, headerParts.length);
  view.setUint32(8, crc32(out.subarray(0, 8)));
  out.set(headerParts, 12);
  out.set(body, 12 + headerParts.length);
  view.setUint32(total - 4, crc32(out.subarray(0, total - 4)));
  return out;
}

function eventFrame(eventType: string, payload: unknown): Uint8Array {
  return frame(
    { ':event-type': eventType, ':content-type': 'application/json', ':message-type': 'event' },
    JSON.stringify(payload),
  );
}

function chunkFrame(anthropicEvent: unknown): Uint8Array {
  const bytes = btoa(JSON.stringify(anthropicEvent));
  return eventFrame('chunk', { bytes, p: 'abcd' });
}

function concat(frames: Uint8Array[]): Uint8Array {
  const out = new Uint8Array(frames.reduce((n, f) => n + f.length, 0));
  let offset = 0;
  for (const f of frames) {
    out.set(f, offset);
    offset += f.length;
  }
  return out;
}

function binaryBody(bytes: Uint8Array) {
  let s = '';
  for (const b of bytes) s += String.fromCharCode(b);
  return { type: 'BINARY', base64Bytes: btoa(s), contentType: 'application/vnd.amazon.eventstream' };
}

const EVENT_STREAM_HEADERS = [{ name: 'content-type', values: ['application/vnd.amazon.eventstream'] }];

describe('decodeAwsEventStream', () => {
  it('matches the standard CRC32 check value used by the framing', () => {
    expect(crc32(new TextEncoder().encode('123456789'))).toBe(0xcbf43926);
  });

  it('decodes concatenated frames with their string headers and payload', () => {
    const bytes = concat([eventFrame('messageStart', { role: 'assistant' }), eventFrame('messageStop', { stopReason: 'end_turn' })]);
    const messages = decodeAwsEventStream(bytes);
    expect(messages).toHaveLength(2);
    expect(messages[0]!.headers[':event-type']).toBe('messageStart');
    expect(messages[0]!.headers[':message-type']).toBe('event');
    expect(new TextDecoder().decode(messages[1]!.payload)).toBe('{"stopReason":"end_turn"}');
  });

  it('keeps the complete frames of a capture truncated mid-frame', () => {
    const bytes = concat([eventFrame('messageStart', { role: 'assistant' }), eventFrame('messageStop', { stopReason: 'end_turn' })]);
    const messages = decodeAwsEventStream(bytes.subarray(0, bytes.length - 5));
    expect(messages.map((m) => m.headers[':event-type'])).toEqual(['messageStart']);
  });

  it('rejects a frame whose prelude or payload no longer matches its CRC', () => {
    for (const position of [8, 30]) {
      const bytes = eventFrame('messageStart', { role: 'assistant' });
      bytes[position] = bytes[position]! ^ 0xff;
      expect(decodeAwsEventStream(bytes)).toEqual([]);
    }
  });

  it('returns nothing for a non-event-stream body', () => {
    expect(decodeAwsEventStream(new TextEncoder().encode('{"output":{"message":{}}}'))).toEqual([]);
  });
});

// ---------------------------------------------------------------------------
// Bedrock Converse
// ---------------------------------------------------------------------------

describe('parseTraffic — Bedrock Converse', () => {
  it('parses Converse on a cross-region inference profile with a URL-encoded model id', () => {
    // Request and response are the AWS API reference Converse examples.
    const parsed = parseTraffic({
      httpRequest: {
        method: 'POST',
        path: '/model/us.anthropic.claude-3-5-sonnet-20240620-v1%3A0/converse',
        body: {
          type: 'JSON',
          json: JSON.stringify({
            messages: [{ role: 'user', content: [{ text: 'Write an article about impact of high inflation to GDP of a country' }] }],
            system: [{ text: 'You are an economist with access to lots of data' }],
            inferenceConfig: { maxTokens: 1000, temperature: 0.5 },
          }),
        },
      },
      httpResponse: {
        statusCode: 200,
        body: {
          type: 'JSON',
          json: JSON.stringify({
            output: { message: { content: [{ text: 'Inflation erodes real GDP.' }], role: 'assistant' } },
            stopReason: 'end_turn',
            usage: { inputTokens: 30, outputTokens: 628, totalTokens: 658 },
            metrics: { latencyMs: 1275 },
          }),
        },
      },
    });

    expect(parsed.kind).toBe('anthropic');
    if (parsed.kind !== 'anthropic') return;
    expect(parsed.platform).toBe('bedrock');
    expect(parsed.model).toBe('us.anthropic.claude-3-5-sonnet-20240620-v1:0');
    expect(parsed.stream).toBe(false);
    expect(parsed.messages).toEqual([
      { role: 'user', content: [{ type: 'text', text: 'Write an article about impact of high inflation to GDP of a country' }] },
    ]);
    expect(parsed.system).toEqual([{ type: 'text', text: 'You are an economist with access to lots of data' }]);
    expect(parsed.maxTokens).toBe(1000);
    expect(parsed.responseContent).toEqual([{ type: 'text', text: 'Inflation erodes real GDP.' }]);
    expect(parsed.stopReason).toBe('end_turn');
    expect(parsed.usage).toEqual({ input_tokens: 30, output_tokens: 628 });
    expect(getTokenSummary(parsed)).toBe('30 in / 628 out');
  });

  it('parses Converse on a non-Anthropic model with tools, tool use and cache counters', () => {
    const parsed = parseTraffic({
      httpRequest: {
        method: 'POST',
        path: '/model/meta.llama3-1-70b-instruct-v1:0/converse',
        body: {
          type: 'JSON',
          json: JSON.stringify({
            messages: [
              { role: 'user', content: [{ text: 'Weather in Paris?' }] },
              { role: 'assistant', content: [{ toolUse: { toolUseId: 'tooluse_1', name: 'get_weather', input: { city: 'Paris' } } }] },
              { role: 'user', content: [{ toolResult: { toolUseId: 'tooluse_1', content: [{ text: '18C' }], status: 'success' } }] },
            ],
            toolConfig: {
              tools: [{ toolSpec: { name: 'get_weather', description: 'Weather', inputSchema: { json: { type: 'object' } } } }],
            },
          }),
        },
      },
      httpResponse: {
        statusCode: 200,
        body: {
          type: 'JSON',
          json: JSON.stringify({
            output: {
              message: {
                role: 'assistant',
                content: [{ toolUse: { toolUseId: 'tooluse_2', name: 'get_weather', input: { city: 'Lyon' } } }],
              },
            },
            stopReason: 'tool_use',
            usage: { inputTokens: 40, outputTokens: 12, totalTokens: 52, cacheReadInputTokens: 20, cacheWriteInputTokens: 5 },
          }),
        },
      },
    });

    expect(parsed.kind).toBe('anthropic');
    if (parsed.kind !== 'anthropic') return;
    expect(parsed.platform).toBe('bedrock');
    expect(getModelLabel(parsed)).toBe('meta.llama3-1-70b-instruct-v1:0');
    expect(parsed.messages[1]).toEqual({
      role: 'assistant',
      content: [{ type: 'tool_use', id: 'tooluse_1', name: 'get_weather', input: { city: 'Paris' } }],
    });
    expect(parsed.messages[2]).toEqual({
      role: 'user',
      content: [{ type: 'tool_result', tool_use_id: 'tooluse_1', content: '18C' }],
    });
    expect(parsed.tools).toEqual([{ name: 'get_weather', description: 'Weather', input_schema: { type: 'object' } }]);
    expect(parsed.responseContent).toEqual([{ type: 'tool_use', id: 'tooluse_2', name: 'get_weather', input: { city: 'Lyon' } }]);
    expect(parsed.stopReason).toBe('tool_use');
    expect(parsed.usage).toEqual({
      input_tokens: 40,
      output_tokens: 12,
      cache_read_input_tokens: 20,
      cache_creation_input_tokens: 5,
    });
  });

  it('reassembles a ConverseStream response from its binary event stream', () => {
    const stream = concat([
      eventFrame('messageStart', { role: 'assistant', p: 'ab' }),
      eventFrame('contentBlockDelta', { contentBlockIndex: 0, delta: { text: 'Hello' }, p: 'ab' }),
      eventFrame('contentBlockDelta', { contentBlockIndex: 0, delta: { text: ' world' }, p: 'ab' }),
      eventFrame('contentBlockStop', { contentBlockIndex: 0, p: 'ab' }),
      eventFrame('contentBlockStart', { contentBlockIndex: 1, start: { toolUse: { toolUseId: 'tooluse_9', name: 'get_weather' } }, p: 'ab' }),
      eventFrame('contentBlockDelta', { contentBlockIndex: 1, delta: { toolUse: { input: '{"city":' } }, p: 'ab' }),
      eventFrame('contentBlockDelta', { contentBlockIndex: 1, delta: { toolUse: { input: '"Paris"}' } }, p: 'ab' }),
      eventFrame('contentBlockStop', { contentBlockIndex: 1, p: 'ab' }),
      eventFrame('messageStop', { stopReason: 'tool_use', p: 'ab' }),
      eventFrame('metadata', { usage: { inputTokens: 12, outputTokens: 7, totalTokens: 19 }, metrics: { latencyMs: 300 }, p: 'ab' }),
    ]);
    const parsed = parseTraffic({
      httpRequest: {
        method: 'POST',
        path: '/model/amazon.nova-pro-v1:0/converse-stream',
        body: { type: 'JSON', json: JSON.stringify({ messages: [{ role: 'user', content: [{ text: 'Hi' }] }] }) },
      },
      httpResponse: { statusCode: 200, headers: EVENT_STREAM_HEADERS, body: binaryBody(stream) },
    });

    expect(parsed.kind).toBe('anthropic');
    if (parsed.kind !== 'anthropic') return;
    expect(parsed.platform).toBe('bedrock');
    expect(parsed.stream).toBe(true);
    expect(parsed.model).toBe('amazon.nova-pro-v1:0');
    expect(parsed.sseEvents!.map((e) => e.event)).toEqual([
      'messageStart', 'contentBlockDelta', 'contentBlockDelta', 'contentBlockStop', 'contentBlockStart',
      'contentBlockDelta', 'contentBlockDelta', 'contentBlockStop', 'messageStop', 'metadata',
    ]);
    expect(parsed.responseContent).toEqual([
      { type: 'text', text: 'Hello world' },
      { type: 'tool_use', id: 'tooluse_9', name: 'get_weather', input: { city: 'Paris' } },
    ]);
    expect(parsed.stopReason).toBe('tool_use');
    expect(getTokenSummary(parsed)).toBe('12 in / 7 out');
  });

  it('reassembles ConverseStream events delivered as SSE', () => {
    const sse = [
      'event: messageStart', 'data: {"role":"assistant"}', '',
      'event: contentBlockDelta', 'data: {"contentBlockIndex":0,"delta":{"text":"Hi"}}', '',
      'event: messageStop', 'data: {"stopReason":"end_turn"}', '',
      'event: metadata', 'data: {"usage":{"inputTokens":3,"outputTokens":1,"totalTokens":4}}', '',
    ].join('\n');
    const parsed = parseTraffic({
      httpRequest: { method: 'POST', path: '/model/anthropic.claude-3-haiku-20240307-v1:0/converse-stream', body: null },
      httpResponse: { statusCode: 200, headers: [{ name: 'content-type', values: ['text/event-stream'] }], body: { type: 'STRING', string: sse } },
    });
    expect(parsed.kind).toBe('anthropic');
    if (parsed.kind !== 'anthropic') return;
    expect(parsed.responseContent).toEqual([{ type: 'text', text: 'Hi' }]);
    expect(parsed.stopReason).toBe('end_turn');
    expect(getTokenSummary(parsed)).toBe('3 in / 1 out');
  });

  it('classifies Converse on a Bedrock host and drafts a BEDROCK expectation from it', () => {
    const parsed = parseTraffic({
      httpRequest: {
        method: 'POST',
        path: '/model/mistral.mistral-large-2407-v1:0/converse',
        headers: [{ name: 'host', values: ['bedrock-runtime.eu-west-2.amazonaws.com'] }],
        body: { type: 'JSON', json: JSON.stringify({ messages: [{ role: 'user', content: [{ text: 'Hi' }] }] }) },
      },
      httpResponse: {
        statusCode: 200,
        body: {
          type: 'JSON',
          json: JSON.stringify({
            output: { message: { role: 'assistant', content: [{ text: 'Hello' }] } },
            stopReason: 'end_turn',
            usage: { inputTokens: 5, outputTokens: 1, totalTokens: 6 },
          }),
        },
      },
    });
    expect(parsed.kind).toBe('anthropic');
    if (parsed.kind !== 'anthropic') return;
    expect(parsed.platform).toBe('bedrock');
    expect(parsed.responseContent).toEqual([{ type: 'text', text: 'Hello' }]);
    const draft = extractLlmExpectationFromCapture(parsed, '/model/mistral.mistral-large-2407-v1:0/converse');
    expect(draft.provider).toBe('BEDROCK');
    expect(draft.text).toBe('Hello');
  });
});

// ---------------------------------------------------------------------------
// Bedrock InvokeModel
// ---------------------------------------------------------------------------

describe('parseTraffic — Bedrock InvokeModel', () => {
  it('parses InvokeModel on a region-prefixed Claude id, taking the model from the path', () => {
    const parsed = parseTraffic({
      httpRequest: {
        method: 'POST',
        path: '/model/eu.anthropic.claude-sonnet-4-20250514-v1:0/invoke',
        body: {
          type: 'JSON',
          json: JSON.stringify({
            anthropic_version: 'bedrock-2023-05-31',
            max_tokens: 256,
            messages: [{ role: 'user', content: 'Hello' }],
          }),
        },
      },
      httpResponse: {
        statusCode: 200,
        body: {
          type: 'JSON',
          json: JSON.stringify({
            type: 'message',
            role: 'assistant',
            content: [{ type: 'text', text: 'Hi' }],
            stop_reason: 'end_turn',
            usage: { input_tokens: 9, output_tokens: 2 },
          }),
        },
      },
    });
    expect(parsed.kind).toBe('anthropic');
    if (parsed.kind !== 'anthropic') return;
    expect(parsed.platform).toBe('bedrock');
    expect(parsed.model).toBe('eu.anthropic.claude-sonnet-4-20250514-v1:0');
    expect(parsed.maxTokens).toBe(256);
    expect(parsed.responseContent).toEqual([{ type: 'text', text: 'Hi' }]);
    expect(getTokenSummary(parsed)).toBe('9 in / 2 out');
  });

  it('reassembles InvokeModelWithResponseStream chunks carrying base64 Anthropic events', () => {
    const stream = concat([
      chunkFrame({ type: 'message_start', message: { model: 'claude-3-5-sonnet-20240620', usage: { input_tokens: 10, output_tokens: 1 } } }),
      chunkFrame({ type: 'content_block_start', index: 0, content_block: { type: 'text', text: '' } }),
      chunkFrame({ type: 'content_block_delta', index: 0, delta: { type: 'text_delta', text: 'Hello' } }),
      chunkFrame({ type: 'content_block_delta', index: 0, delta: { type: 'text_delta', text: ' there' } }),
      chunkFrame({ type: 'content_block_stop', index: 0 }),
      chunkFrame({ type: 'message_delta', delta: { stop_reason: 'end_turn' }, usage: { output_tokens: 5 } }),
      chunkFrame({ type: 'message_stop', 'amazon-bedrock-invocationMetrics': { inputTokenCount: 10, outputTokenCount: 5 } }),
    ]);
    const parsed = parseTraffic({
      httpRequest: {
        method: 'POST',
        path: '/model/anthropic.claude-3-5-sonnet-20240620-v1:0/invoke-with-response-stream',
        body: { type: 'JSON', json: JSON.stringify({ anthropic_version: 'bedrock-2023-05-31', max_tokens: 64, messages: [] }) },
      },
      httpResponse: { statusCode: 200, headers: EVENT_STREAM_HEADERS, body: binaryBody(stream) },
    });
    expect(parsed.kind).toBe('anthropic');
    if (parsed.kind !== 'anthropic') return;
    expect(parsed.stream).toBe(true);
    expect(parsed.model).toBe('anthropic.claude-3-5-sonnet-20240620-v1:0');
    expect(parsed.sseEvents).toHaveLength(7);
    expect(parsed.sseEvents![2]!.event).toBe('content_block_delta');
    expect(parsed.responseContent).toEqual([{ type: 'text', text: 'Hello there' }]);
    expect(parsed.stopReason).toBe('end_turn');
    expect(getTokenSummary(parsed)).toBe('10 in / 5 out');
  });

  it('reassembles InvokeModelWithResponseStream on Nova, whose chunks wrap Converse-style events', () => {
    const chunk = (event: unknown) => eventFrame('chunk', { bytes: btoa(JSON.stringify(event)) });
    const stream = concat([
      chunk({ messageStart: { role: 'assistant' } }),
      chunk({ contentBlockDelta: { delta: { text: 'Once upon' }, contentBlockIndex: 0 } }),
      chunk({ contentBlockDelta: { delta: { text: ' a time' }, contentBlockIndex: 0 } }),
      chunk({ contentBlockStop: { contentBlockIndex: 0 } }),
      chunk({ messageStop: { stopReason: 'end_turn' } }),
      chunk({ metadata: { usage: { inputTokens: 21, outputTokens: 4 } } }),
    ]);
    const parsed = parseTraffic({
      httpRequest: {
        method: 'POST',
        path: '/model/us.amazon.nova-lite-v1:0/invoke-with-response-stream',
        body: {
          type: 'JSON',
          json: JSON.stringify({
            schemaVersion: 'messages-v1',
            messages: [{ role: 'user', content: [{ text: 'A camping trip' }] }],
            system: [{ text: 'Act as a creative writing assistant.' }],
            inferenceConfig: { maxTokens: 500, topP: 0.9, topK: 20, temperature: 0.7 },
          }),
        },
      },
      httpResponse: { statusCode: 200, headers: EVENT_STREAM_HEADERS, body: binaryBody(stream) },
    });
    expect(parsed.kind).toBe('anthropic');
    if (parsed.kind !== 'anthropic') return;
    expect(parsed.messages).toEqual([{ role: 'user', content: [{ type: 'text', text: 'A camping trip' }] }]);
    expect(parsed.maxTokens).toBe(500);
    expect(parsed.responseContent).toEqual([{ type: 'text', text: 'Once upon a time' }]);
    expect(parsed.stopReason).toBe('end_turn');
    expect(getTokenSummary(parsed)).toBe('21 in / 4 out');
  });

  it('keeps an Anthropic-on-Anthropic call labelled as Anthropic', () => {
    const parsed = parseTraffic({
      httpRequest: { method: 'POST', path: '/v1/messages', body: { type: 'JSON', json: '{"model":"claude-sonnet-4","messages":[]}' } },
      httpResponse: { statusCode: 200, body: { type: 'JSON', json: '{"content":[],"usage":{"input_tokens":1,"output_tokens":1}}' } },
    });
    expect(parsed.kind).toBe('anthropic');
    if (parsed.kind !== 'anthropic') return;
    expect(parsed.platform).toBeUndefined();
    expect(extractLlmExpectationFromCapture(parsed, '/v1/messages').provider).toBe('ANTHROPIC');
  });

  it('does not treat a lookalike path as Bedrock', () => {
    const parsed = parseTraffic({
      httpRequest: { method: 'POST', path: '/model/foo/invoked', body: null },
      httpResponse: { statusCode: 200, body: null },
    });
    expect(parsed.kind).toBe('generic');
  });
});

// ---------------------------------------------------------------------------
// Bedrock InvokeModel, native bodies of the other model families
// ---------------------------------------------------------------------------

// Request and response bodies from the AWS model-parameters pages:
// https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-meta.html
// https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-titan-text.html
// https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-mistral-text-completion.html
// https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-cohere-command.html
// https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-cohere-command-r-plus.html
// Mistral and Cohere bodies carry no token counts; Bedrock returns them in the
// x-amzn-bedrock-input-token-count / x-amzn-bedrock-output-token-count response headers.
function invokeModel(modelId: string, request: unknown, response: unknown, headers: unknown[] = []) {
  return parseTraffic({
    httpRequest: {
      method: 'POST',
      path: `/model/${modelId}/invoke`,
      headers: [{ name: 'host', values: ['bedrock-runtime.us-east-1.amazonaws.com'] }],
      body: { type: 'JSON', json: JSON.stringify(request) },
    },
    httpResponse: {
      statusCode: 200,
      headers: [{ name: 'content-type', values: ['application/json'] }, ...headers],
      body: { type: 'JSON', json: JSON.stringify(response) },
    },
  });
}

const TOKEN_COUNT_HEADERS = [
  { name: 'X-Amzn-Bedrock-Input-Token-Count', values: ['17'] },
  { name: 'X-Amzn-Bedrock-Output-Token-Count', values: ['6'] },
];

describe('parseTraffic — Bedrock InvokeModel on Llama, Titan, Mistral and Cohere', () => {
  it('shows a Llama generation with its prompt and token counts', () => {
    const parsed = invokeModel(
      'meta.llama3-70b-instruct-v1:0',
      { prompt: '<|begin_of_text|>What is a hello world program?', max_gen_len: 512, temperature: 0.5 },
      { generation: '\n\nA first program.', prompt_token_count: 12, generation_token_count: 4, stop_reason: 'stop' },
    );
    expect(parsed.kind).toBe('anthropic');
    if (parsed.kind !== 'anthropic') return;
    expect(parsed.platform).toBe('bedrock');
    expect(parsed.model).toBe('meta.llama3-70b-instruct-v1:0');
    expect(parsed.messages).toEqual([{ role: 'user', content: '<|begin_of_text|>What is a hello world program?' }]);
    expect(parsed.maxTokens).toBe(512);
    expect(parsed.responseContent).toEqual([{ type: 'text', text: '\n\nA first program.' }]);
    expect(parsed.stopReason).toBe('stop');
    expect(getTokenSummary(parsed)).toBe('12 in / 4 out');
  });

  it('shows Titan Text results with their token counts', () => {
    const parsed = invokeModel(
      'amazon.titan-text-express-v1',
      { inputText: 'User: Name a colour\nBot:', textGenerationConfig: { maxTokenCount: 4096, temperature: 0, topP: 1, stopSequences: [] } },
      { inputTextTokenCount: 9, results: [{ tokenCount: 3, outputText: '\nBlue.\n', completionReason: 'FINISHED' }] },
    );
    expect(parsed.kind).toBe('anthropic');
    if (parsed.kind !== 'anthropic') return;
    expect(parsed.messages).toEqual([{ role: 'user', content: 'User: Name a colour\nBot:' }]);
    expect(parsed.maxTokens).toBe(4096);
    expect(parsed.responseContent).toEqual([{ type: 'text', text: '\nBlue.\n' }]);
    expect(parsed.stopReason).toBe('FINISHED');
    expect(getTokenSummary(parsed)).toBe('9 in / 3 out');
  });

  it('shows Mistral outputs, taking the token counts from the response headers', () => {
    const parsed = invokeModel(
      'mistral.mistral-7b-instruct-v0:2',
      { prompt: '<s>[INST] What is your favourite condiment? [/INST]', max_tokens: 400, temperature: 0.7, top_p: 0.7, top_k: 50 },
      { outputs: [{ text: 'Lemon juice.', stop_reason: 'stop' }] },
      TOKEN_COUNT_HEADERS,
    );
    expect(parsed.kind).toBe('anthropic');
    if (parsed.kind !== 'anthropic') return;
    expect(parsed.messages).toEqual([{ role: 'user', content: '<s>[INST] What is your favourite condiment? [/INST]' }]);
    expect(parsed.maxTokens).toBe(400);
    expect(parsed.responseContent).toEqual([{ type: 'text', text: 'Lemon juice.' }]);
    expect(parsed.stopReason).toBe('stop');
    expect(getTokenSummary(parsed)).toBe('17 in / 6 out');
  });

  it('shows every Cohere Command generation, taking the token counts from the response headers', () => {
    const parsed = invokeModel(
      'cohere.command-text-v14',
      { prompt: 'Summarize this dialogue', max_tokens: 200, temperature: 0.6, p: 1, k: 0, num_generations: 2 },
      {
        generations: [
          { finish_reason: 'COMPLETE', id: 'g1', text: 'A password reset.' },
          { finish_reason: 'COMPLETE', id: 'g2', text: 'Lost access.' },
        ],
        id: 'r1',
        prompt: 'Summarize this dialogue',
      },
      TOKEN_COUNT_HEADERS,
    );
    expect(parsed.kind).toBe('anthropic');
    if (parsed.kind !== 'anthropic') return;
    expect(parsed.maxTokens).toBe(200);
    expect(parsed.responseContent).toEqual([
      { type: 'text', text: 'A password reset.' },
      { type: 'text', text: 'Lost access.' },
    ]);
    expect(parsed.stopReason).toBe('COMPLETE');
    expect(getTokenSummary(parsed)).toBe('17 in / 6 out');
  });

  it('shows a Cohere Command R reply, chat history, preamble and tool calls', () => {
    const parsed = invokeModel(
      'cohere.command-r-v1:0',
      {
        message: 'What are some skills I should have?',
        chat_history: [
          { role: 'USER', message: 'What is an interesting new role in AI?' },
          { role: 'CHATBOT', message: 'You could explore being a prompt engineer!' },
        ],
        preamble: 'You are a careers adviser.',
        max_tokens: 2000,
      },
      {
        response_id: 'r1',
        text: 'Writing and testing.',
        generation_id: 'g1',
        finish_reason: 'COMPLETE',
        tool_calls: [{ name: 'top_song', parameters: { sign: 'WZPZ' } }],
      },
      TOKEN_COUNT_HEADERS,
    );
    expect(parsed.kind).toBe('anthropic');
    if (parsed.kind !== 'anthropic') return;
    expect(parsed.messages).toEqual([
      { role: 'user', content: 'What is an interesting new role in AI?' },
      { role: 'assistant', content: 'You could explore being a prompt engineer!' },
      { role: 'user', content: 'What are some skills I should have?' },
    ]);
    expect(parsed.system).toBe('You are a careers adviser.');
    expect(parsed.maxTokens).toBe(2000);
    expect(parsed.responseContent).toEqual([
      { type: 'text', text: 'Writing and testing.' },
      { type: 'tool_use', name: 'top_song', input: { sign: 'WZPZ' } },
    ]);
    expect(parsed.stopReason).toBe('COMPLETE');
    expect(getTokenSummary(parsed)).toBe('17 in / 6 out');
  });

  it('prefers the token counts in a Cohere Command R meta.billed_units over the headers', () => {
    const parsed = invokeModel(
      'cohere.command-r-plus-v1:0',
      { message: 'Hi' },
      { response_id: 'r1', text: 'Hello.', finish_reason: 'COMPLETE', meta: { billed_units: { input_tokens: 2, output_tokens: 3 } } },
      TOKEN_COUNT_HEADERS,
    );
    expect(getTokenSummary(parsed)).toBe('2 in / 3 out');
  });

  // InvokeModelWithResponseStream chunk fields: Titan from its model-parameters page;
  // Llama `generation`, Mistral `outputs`, Cohere `text`/`is_finished` and the
  // `amazon-bedrock-invocationMetrics` on the last chunk as read by langchain-aws
  // (libs/aws/langchain_aws/llms/bedrock.py, prepare_output_stream).
  function invokeModelStream(modelId: string, chunks: unknown[]) {
    const stream = concat(chunks.map((c) => eventFrame('chunk', { bytes: btoa(JSON.stringify(c)) })));
    return parseTraffic({
      httpRequest: {
        method: 'POST',
        path: `/model/${modelId}/invoke-with-response-stream`,
        headers: [{ name: 'host', values: ['bedrock-runtime.us-east-1.amazonaws.com'] }],
        body: { type: 'JSON', json: '{"prompt":"Hi"}' },
      },
      httpResponse: { statusCode: 200, headers: EVENT_STREAM_HEADERS, body: binaryBody(stream) },
    });
  }
  const METRICS = { 'amazon-bedrock-invocationMetrics': { inputTokenCount: 8, outputTokenCount: 5, invocationLatency: 300, firstByteLatency: 100 } };

  it.each([
    ['Llama', 'meta.llama3-8b-instruct-v1:0', [
      { generation: 'Hello', prompt_token_count: 8, generation_token_count: 1, stop_reason: null },
      { generation: ' there', prompt_token_count: null, generation_token_count: 5, stop_reason: 'stop', ...METRICS },
    ], 'stop'],
    ['Titan', 'amazon.titan-text-lite-v1', [
      { index: 0, inputTextTokenCount: 8, totalOutputTextTokenCount: 1, outputText: 'Hello', completionReason: null },
      { index: 1, inputTextTokenCount: 8, totalOutputTextTokenCount: 5, outputText: ' there', completionReason: 'FINISHED', ...METRICS },
    ], 'FINISHED'],
    ['Mistral', 'mistral.mixtral-8x7b-instruct-v0:1', [
      { outputs: [{ text: 'Hello', stop_reason: null }] },
      { outputs: [{ text: ' there', stop_reason: 'stop' }], ...METRICS },
    ], 'stop'],
    ['Cohere', 'cohere.command-light-text-v14', [
      { text: 'Hello', is_finished: false },
      { text: ' there', is_finished: false },
      { is_finished: true, finish_reason: 'COMPLETE', ...METRICS },
    ], 'COMPLETE'],
    // Cohere chat stream events: https://docs.cohere.com/v1/reference/chat-stream. The
    // stream-end event repeats the whole reply in `response`, which must not be added again.
    ['Cohere Command R', 'cohere.command-r-v1:0', [
      { is_finished: false, event_type: 'stream-start', generation_id: 'g1' },
      { is_finished: false, event_type: 'text-generation', text: 'Hello' },
      { is_finished: false, event_type: 'text-generation', text: ' there' },
      { is_finished: true, event_type: 'stream-end', finish_reason: 'COMPLETE', response: { response_id: 'r1', text: 'Hello there', generation_id: 'g1', finish_reason: 'COMPLETE' }, ...METRICS },
    ], 'COMPLETE'],
  ])('reassembles a streamed %s reply and its invocation metrics', (_family, modelId, chunks, stopReason) => {
    const parsed = invokeModelStream(modelId as string, chunks as unknown[]);
    expect(parsed.kind).toBe('anthropic');
    if (parsed.kind !== 'anthropic') return;
    expect(parsed.stream).toBe(true);
    expect(parsed.responseContent).toEqual([{ type: 'text', text: 'Hello there' }]);
    expect(parsed.stopReason).toBe(stopReason);
    expect(getTokenSummary(parsed)).toBe('8 in / 5 out');
  });

  it('keeps the text streamed before an exception frame, and the exception in the stream events', () => {
    const stream = concat([
      eventFrame('chunk', { bytes: btoa(JSON.stringify({ generation: 'Hello', prompt_token_count: 8, generation_token_count: 1, stop_reason: null })) }),
      eventFrame('chunk', { bytes: btoa(JSON.stringify({ generation: ' there', prompt_token_count: null, generation_token_count: 2, stop_reason: null })) }),
      frame(
        { ':exception-type': 'modelStreamErrorException', ':content-type': 'application/json', ':message-type': 'exception' },
        JSON.stringify({ message: 'The model stream failed', originalStatusCode: 500 }),
      ),
    ]);
    const parsed = parseTraffic({
      httpRequest: {
        method: 'POST',
        path: '/model/meta.llama3-8b-instruct-v1:0/invoke-with-response-stream',
        body: { type: 'JSON', json: '{"prompt":"Hi"}' },
      },
      httpResponse: { statusCode: 200, headers: EVENT_STREAM_HEADERS, body: binaryBody(stream) },
    });
    expect(parsed.kind).toBe('anthropic');
    if (parsed.kind !== 'anthropic') return;
    expect(parsed.responseContent).toEqual([{ type: 'text', text: 'Hello there' }]);
    expect(parsed.sseEvents!.map((e) => e.event)).toEqual(['chunk', 'chunk', 'modelStreamErrorException']);
    expect(parsed.sseEvents![2]!.data).toContain('The model stream failed');
  });
});

// ---------------------------------------------------------------------------
// Ollama streaming (NDJSON)
// ---------------------------------------------------------------------------

describe('parseTraffic — Ollama NDJSON stream', () => {
  const ndjson = [
    '{"model":"llama3.2","created_at":"2024-07-22T20:33:28.123Z","message":{"role":"assistant","content":"The"},"done":false}',
    '{"model":"llama3.2","created_at":"2024-07-22T20:33:28.234Z","message":{"role":"assistant","content":" sky is blue"},"done":false}',
    '{"model":"llama3.2","created_at":"2024-07-22T20:33:28.345Z","message":{"role":"assistant","content":""},"done":true,"done_reason":"stop","total_duration":4883583458,"load_duration":1334875,"prompt_eval_count":26,"prompt_eval_duration":342546000,"eval_count":259,"eval_duration":4232710000}',
    '',
  ].join('\n');

  it('reassembles a streamed /api/chat reply and defaults stream to true', () => {
    const parsed = parseTraffic({
      httpRequest: {
        method: 'POST',
        path: '/api/chat',
        body: { type: 'JSON', json: JSON.stringify({ model: 'llama3.2', messages: [{ role: 'user', content: 'Why is the sky blue?' }] }) },
      },
      httpResponse: {
        statusCode: 200,
        headers: [{ name: 'content-type', values: ['application/x-ndjson'] }],
        body: { type: 'STRING', string: ndjson },
      },
    });
    expect(parsed.kind).toBe('ollama');
    if (parsed.kind !== 'ollama') return;
    expect(parsed.stream).toBe(true);
    expect(parsed.responseMessage).toEqual({ role: 'assistant', content: 'The sky is blue' });
    expect(parsed.done).toBe(true);
    expect(parsed.usage).toEqual({ prompt_eval_count: 26, eval_count: 259 });
    expect(parsed.sseEvents).toHaveLength(3);
    expect(getTokenSummary(parsed)).toBe('26 in / 259 out');
  });

  it('collects streamed tool calls and thinking', () => {
    const body = [
      '{"model":"qwen3","message":{"role":"assistant","content":"","thinking":"Need weather."},"done":false}',
      '{"model":"qwen3","message":{"role":"assistant","content":"","tool_calls":[{"function":{"name":"get_weather","arguments":{"city":"Paris"}}}]},"done":false}',
      '{"model":"qwen3","message":{"role":"assistant","content":""},"done":true,"done_reason":"stop","prompt_eval_count":5,"eval_count":9}',
    ].join('\n');
    const parsed = parseTraffic({
      httpRequest: { method: 'POST', path: '/api/chat', body: { type: 'JSON', json: '{"model":"qwen3","messages":[]}' } },
      httpResponse: { statusCode: 200, body: { type: 'STRING', string: body } },
    });
    expect(parsed.kind).toBe('ollama');
    if (parsed.kind !== 'ollama') return;
    expect(parsed.responseMessage).toEqual({
      role: 'assistant',
      content: '',
      thinking: 'Need weather.',
      tool_calls: [{ function: { name: 'get_weather', arguments: { city: 'Paris' } } }],
    });
  });

  it('keeps stream false when the request disables it', () => {
    const parsed = parseTraffic({
      httpRequest: { method: 'POST', path: '/api/chat', body: { type: 'JSON', json: '{"model":"llama3.2","stream":false,"messages":[]}' } },
      httpResponse: { statusCode: 200, body: { type: 'JSON', json: '{"model":"llama3.2","message":{"role":"assistant","content":"x"},"done":true}' } },
    });
    expect(parsed.kind).toBe('ollama');
    if (parsed.kind !== 'ollama') return;
    expect(parsed.stream).toBe(false);
    expect(parsed.sseEvents).toBeNull();
  });
});

// ---------------------------------------------------------------------------
// Vertex AI
// ---------------------------------------------------------------------------

describe('parseTraffic — Vertex AI paths', () => {
  const vertexGemini = '/v1/projects/my-project/locations/us-central1/publishers/google/models/gemini-2.0-flash-001';

  it('classifies a Vertex generateContent path as Gemini without a host header', () => {
    const parsed = parseTraffic({
      httpRequest: { method: 'POST', path: `${vertexGemini}:generateContent`, body: { type: 'JSON', json: '{"contents":[{"role":"user","parts":[{"text":"Hi"}]}]}' } },
      httpResponse: {
        statusCode: 200,
        body: { type: 'JSON', json: '{"candidates":[{"content":{"role":"model","parts":[{"text":"Hello"}]},"finishReason":"STOP"}],"usageMetadata":{"promptTokenCount":2,"candidatesTokenCount":1}}' },
      },
    });
    expect(parsed.kind).toBe('gemini');
    if (parsed.kind !== 'gemini') return;
    expect(parsed.model).toBe('gemini-2.0-flash-001');
    expect(parsed.stream).toBe(false);
    expect(getTokenSummary(parsed)).toBe('2 in / 1 out');
  });

  it('classifies a Vertex error reply by its path alone', () => {
    // A 429 carries no candidates and the captured request body may be absent.
    const parsed = parseTraffic({
      httpRequest: { method: 'POST', path: `${vertexGemini}:streamGenerateContent`, body: null },
      httpResponse: {
        statusCode: 429,
        body: { type: 'JSON', json: '{"error":{"code":429,"message":"Resource exhausted.","status":"RESOURCE_EXHAUSTED"}}' },
      },
    });
    expect(parsed.kind).toBe('gemini');
    if (parsed.kind !== 'gemini') return;
    expect(parsed.model).toBe('gemini-2.0-flash-001');
  });

  it('reassembles a streamGenerateContent JSON-array response (no alt=sse)', () => {
    const chunks = [
      { candidates: [{ content: { role: 'model', parts: [{ text: 'Hel' }] } }] },
      { candidates: [{ content: { role: 'model', parts: [{ text: 'lo' }] }, finishReason: 'STOP' }], usageMetadata: { promptTokenCount: 4, candidatesTokenCount: 2 } },
    ];
    const parsed = parseTraffic({
      httpRequest: { method: 'POST', path: `${vertexGemini}:streamGenerateContent`, body: { type: 'JSON', json: '{"contents":[]}' } },
      httpResponse: { statusCode: 200, headers: [{ name: 'content-type', values: ['application/json'] }], body: { type: 'JSON', json: JSON.stringify(chunks) } },
    });
    expect(parsed.kind).toBe('gemini');
    if (parsed.kind !== 'gemini') return;
    expect(parsed.stream).toBe(true);
    expect(parsed.sseEvents).toHaveLength(2);
    expect(parsed.candidates).toEqual([{ content: { parts: [{ text: 'Hel' }, { text: 'lo' }], role: 'model' }, finishReason: 'STOP' }]);
    expect(getTokenSummary(parsed)).toBe('4 in / 2 out');
  });

  it('parses Claude on Vertex (rawPredict) as Anthropic, on the Vertex host and without it', () => {
    const path = '/v1/projects/my-project/locations/us-east5/publishers/anthropic/models/claude-opus-4-1@20250805:rawPredict';
    const request = {
      method: 'POST',
      path,
      body: { type: 'JSON', json: JSON.stringify({ anthropic_version: 'vertex-2023-10-16', max_tokens: 64, messages: [{ role: 'user', content: 'Hi' }] }) },
    };
    const response = {
      statusCode: 200,
      body: { type: 'JSON', json: '{"type":"message","content":[{"type":"text","text":"Hello"}],"stop_reason":"end_turn","usage":{"input_tokens":3,"output_tokens":1}}' },
    };
    for (const headers of [[{ name: 'host', values: ['us-east5-aiplatform.googleapis.com'] }], []]) {
      const parsed = parseTraffic({ httpRequest: { ...request, headers }, httpResponse: response });
      expect(parsed.kind).toBe('anthropic');
      if (parsed.kind !== 'anthropic') return;
      expect(parsed.model).toBe('claude-opus-4-1@20250805');
      expect(parsed.responseContent).toEqual([{ type: 'text', text: 'Hello' }]);
    }
  });
});

// ---------------------------------------------------------------------------
// Gemini thinking tokens
// ---------------------------------------------------------------------------

describe('parseTraffic — Gemini thinking tokens count as output', () => {
  const usageMetadata = { promptTokenCount: 12, candidatesTokenCount: 40, thoughtsTokenCount: 300, totalTokenCount: 352 };
  const request = {
    method: 'POST',
    headers: [{ name: 'host', values: ['generativelanguage.googleapis.com'] }],
    body: { type: 'JSON', json: '{"contents":[{"role":"user","parts":[{"text":"Why is the sky blue?"}]}]}' },
  };

  it('adds thoughtsTokenCount to candidatesTokenCount on a generateContent reply', () => {
    const parsed = parseTraffic({
      httpRequest: { ...request, path: '/v1beta/models/gemini-2.5-pro:generateContent' },
      httpResponse: {
        statusCode: 200,
        body: {
          type: 'JSON',
          json: JSON.stringify({
            candidates: [{ content: { role: 'model', parts: [{ text: 'Rayleigh scattering.' }] }, finishReason: 'STOP' }],
            usageMetadata,
            modelVersion: 'gemini-2.5-pro',
          }),
        },
      },
    });
    expect(parsed.kind).toBe('gemini');
    if (parsed.kind !== 'gemini') return;
    expect(parsed.usage).toEqual({ promptTokenCount: 12, candidatesTokenCount: 40, thoughtsTokenCount: 300 });
    expect(getNumericTokens(parsed)).toEqual({ inputTokens: 12, outputTokens: 340 });
    expect(getTokenSummary(parsed)).toBe('12 in / 340 out (300 thinking)');
  });

  it('takes the thinking count from the last chunk of a streamed reply', () => {
    const sse = [
      `data: ${JSON.stringify({ candidates: [{ content: { role: 'model', parts: [{ text: 'Rayleigh' }] } }], usageMetadata: { promptTokenCount: 12, thoughtsTokenCount: 300, totalTokenCount: 312 } })}`,
      `data: ${JSON.stringify({ candidates: [{ content: { role: 'model', parts: [{ text: ' scattering.' }] }, finishReason: 'STOP' }], usageMetadata })}`,
      '',
    ].join('\n\n');
    const parsed = parseTraffic({
      httpRequest: { ...request, path: '/v1beta/models/gemini-2.5-flash:streamGenerateContent?alt=sse' },
      httpResponse: { statusCode: 200, headers: [{ name: 'content-type', values: ['text/event-stream'] }], body: { type: 'STRING', string: sse } },
    });
    expect(parsed.kind).toBe('gemini');
    if (parsed.kind !== 'gemini') return;
    expect(getNumericTokens(parsed)).toEqual({ inputTokens: 12, outputTokens: 340 });
  });

  it('counts the thinking tokens of a streamed reply returned as a JSON array', () => {
    const parsed = parseTraffic({
      httpRequest: { ...request, path: '/v1beta/models/gemini-2.5-flash:streamGenerateContent' },
      httpResponse: {
        statusCode: 200,
        body: { type: 'JSON', json: JSON.stringify([{ candidates: [{ content: { role: 'model', parts: [{ text: 'x' }] }, finishReason: 'STOP' }], usageMetadata }]) },
      },
    });
    expect(parsed.kind).toBe('gemini');
    if (parsed.kind !== 'gemini') return;
    expect(getNumericTokens(parsed)).toEqual({ inputTokens: 12, outputTokens: 340 });
  });

  it('shows a reply of thoughts only, and leaves a non-thinking reply unchanged', () => {
    const reply = (usage: Record<string, number>) =>
      parseTraffic({
        httpRequest: { ...request, path: '/v1beta/models/gemini-2.5-pro:generateContent' },
        httpResponse: { statusCode: 200, body: { type: 'JSON', json: JSON.stringify({ candidates: [], usageMetadata: usage }) } },
      });
    const thoughtsOnly = reply({ promptTokenCount: 5, thoughtsTokenCount: 64, totalTokenCount: 69 });
    expect(getNumericTokens(thoughtsOnly)).toEqual({ inputTokens: 5, outputTokens: 64 });
    expect(getTokenSummary(thoughtsOnly)).toBe('5 in / 64 out (64 thinking)');
    const plain = reply({ promptTokenCount: 5, candidatesTokenCount: 7, totalTokenCount: 12 });
    expect(getTokenSummary(plain)).toBe('5 in / 7 out');
  });
});

// ---------------------------------------------------------------------------
// Ollama /api/generate
// ---------------------------------------------------------------------------

describe('parseTraffic — Ollama /api/generate', () => {
  it('shows system and prompt as messages and response as the reply', () => {
    const parsed = parseTraffic({
      httpRequest: {
        method: 'POST',
        path: '/api/generate',
        body: {
          type: 'JSON',
          json: JSON.stringify({ model: 'llava', system: 'Be brief.', prompt: 'What is in this picture?', images: ['iVBORw0KGgo='], stream: false }),
        },
      },
      httpResponse: {
        statusCode: 200,
        body: {
          type: 'JSON',
          json: JSON.stringify({
            model: 'llava',
            created_at: '2025-10-17T23:14:07.414671Z',
            response: 'A cat.',
            done: true,
            done_reason: 'stop',
            context: [1, 2, 3],
            total_duration: 5043500667,
            prompt_eval_count: 26,
            eval_count: 4,
          }),
        },
      },
    });
    expect(parsed.kind).toBe('ollama');
    if (parsed.kind !== 'ollama') return;
    expect(parsed.endpoint).toBe('generate');
    expect(parsed.model).toBe('llava');
    expect(parsed.stream).toBe(false);
    expect(parsed.messages).toEqual([
      { role: 'system', content: 'Be brief.' },
      { role: 'user', content: 'What is in this picture?', images: ['iVBORw0KGgo='] },
    ]);
    expect(parsed.tools).toBeNull();
    expect(parsed.responseMessage).toEqual({ role: 'assistant', content: 'A cat.' });
    expect(parsed.done).toBe(true);
    expect(parsed.sseEvents).toBeNull();
    expect(getTokenSummary(parsed)).toBe('26 in / 4 out');
  });

  it('reassembles a streamed NDJSON reply, with thinking, and defaults stream to true', () => {
    const ndjson = [
      '{"model":"qwen3","created_at":"2025-10-17T23:14:07.1Z","response":"","thinking":"Short answer.","done":false}',
      '{"model":"qwen3","created_at":"2025-10-17T23:14:07.2Z","response":"The sky","done":false}',
      '{"model":"qwen3","created_at":"2025-10-17T23:14:07.3Z","response":" is blue.","done":false}',
      '{"model":"qwen3","created_at":"2025-10-17T23:14:07.4Z","response":"","done":true,"done_reason":"stop","prompt_eval_count":11,"eval_count":19}',
      '',
    ].join('\n');
    const parsed = parseTraffic({
      httpRequest: { method: 'POST', path: '/api/generate', body: { type: 'JSON', json: '{"model":"qwen3","prompt":"Why is the sky blue?","think":true}' } },
      httpResponse: { statusCode: 200, headers: [{ name: 'content-type', values: ['application/x-ndjson'] }], body: { type: 'STRING', string: ndjson } },
    });
    expect(parsed.kind).toBe('ollama');
    if (parsed.kind !== 'ollama') return;
    expect(parsed.stream).toBe(true);
    expect(parsed.messages).toEqual([{ role: 'user', content: 'Why is the sky blue?' }]);
    expect(parsed.responseMessage).toEqual({ role: 'assistant', content: 'The sky is blue.', thinking: 'Short answer.' });
    expect(parsed.done).toBe(true);
    expect(parsed.usage).toEqual({ prompt_eval_count: 11, eval_count: 19 });
    expect(parsed.sseEvents).toHaveLength(4);
  });

  it('is captured as an OLLAMA expectation on its own path, since the Ollama mock answers /api/generate', () => {
    const generate = parseTraffic({
      httpRequest: { method: 'POST', path: '/api/generate', body: { type: 'JSON', json: '{"model":"llama3.2","prompt":"Hi","stream":false}' } },
      httpResponse: { statusCode: 200, body: { type: 'JSON', json: '{"model":"llama3.2","response":"Hello","done":true}' } },
    });
    const chat = parseTraffic({
      httpRequest: { method: 'POST', path: '/api/chat', body: { type: 'JSON', json: '{"model":"llama3.2","messages":[]}' } },
      httpResponse: { statusCode: 200, body: { type: 'JSON', json: '{"model":"llama3.2","message":{"role":"assistant","content":"x"},"done":true}' } },
    });
    expect(generate.kind).toBe('ollama');
    expect(isLlmTraffic(generate)).toBe(true);
    expect(isLlmTraffic(chat)).toBe(true);
    if (chat.kind === 'ollama') expect(chat.endpoint).toBeUndefined();
    const draft = extractLlmExpectationFromCapture(generate, '/api/generate');
    expect(draft).toMatchObject({ kind: 'llm', path: '/api/generate', provider: 'OLLAMA', model: 'llama3.2', text: 'Hello', streaming: false });
  });

  it('reads tool calls from the top level of the final generate object', () => {
    const ndjson = [
      '{"model":"qwen3","created_at":"2025-10-17T23:14:07.1Z","response":"","done":false}',
      '{"model":"qwen3","created_at":"2025-10-17T23:14:07.2Z","response":"","done":true,"done_reason":"stop",'
        + '"tool_calls":[{"function":{"name":"get_weather","arguments":{"city":"Tokyo"}}}],"prompt_eval_count":11,"eval_count":19}',
      '',
    ].join('\n');
    const parsed = parseTraffic({
      httpRequest: { method: 'POST', path: '/api/generate', body: { type: 'JSON', json: '{"model":"qwen3","prompt":"Weather in Tokyo?"}' } },
      httpResponse: { statusCode: 200, headers: [{ name: 'content-type', values: ['application/x-ndjson'] }], body: { type: 'STRING', string: ndjson } },
    });
    expect(parsed.kind).toBe('ollama');
    if (parsed.kind !== 'ollama') return;
    expect(parsed.responseMessage).toEqual({
      role: 'assistant',
      content: '',
      tool_calls: [{ function: { name: 'get_weather', arguments: { city: 'Tokyo' } } }],
    });
    expect(extractLlmExpectationFromCapture(parsed, '/api/generate').toolCalls).toEqual([
      { name: 'get_weather', arguments: '{"city":"Tokyo"}' },
    ]);
  });

  it('does not take other paths that start with /api/generate', () => {
    const parsed = parseTraffic({
      httpRequest: { method: 'POST', path: '/api/generated-reports', body: { type: 'JSON', json: '{"prompt":"x"}' } },
      httpResponse: { statusCode: 200, body: { type: 'JSON', json: '{"response":"y"}' } },
    });
    expect(parsed.kind).toBe('generic');
  });
});
