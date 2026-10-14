import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ThemeProvider } from '@mui/material/styles';
import { buildTheme } from '../theme';
import LlmUsageDetail from '../components/LlmUsageDetail';
import { parseTraffic } from '../lib/llmTraffic';

// Gemini reports thinking tokens in usageMetadata.thoughtsTokenCount, apart from
// candidatesTokenCount, and bills them at the output price:
// https://ai.google.dev/api/generate-content and https://ai.google.dev/gemini-api/docs/pricing
function geminiReply(usageMetadata: Record<string, number>) {
  return parseTraffic({
    httpRequest: {
      method: 'POST',
      path: '/v1beta/models/gemini-2.5-pro:generateContent',
      body: { type: 'JSON', json: '{"contents":[{"role":"user","parts":[{"text":"Hi"}]}]}' },
    },
    httpResponse: {
      statusCode: 200,
      body: {
        type: 'JSON',
        json: JSON.stringify({
          candidates: [{ content: { role: 'model', parts: [{ text: 'Hello' }] }, finishReason: 'STOP' }],
          usageMetadata,
          modelVersion: 'gemini-2.5-pro',
        }),
      },
    },
  });
}

describe('LlmUsageDetail', () => {
  it('counts Gemini thinking tokens as output', () => {
    render(
      <ThemeProvider theme={buildTheme('dark')}>
        <LlmUsageDetail parsed={geminiReply({ promptTokenCount: 12, candidatesTokenCount: 40, thoughtsTokenCount: 300, totalTokenCount: 352 })} />
      </ThemeProvider>,
    );
    expect(screen.getByText('12 in / 340 out')).toBeInTheDocument();
  });

  it('shows the tokens of a Gemini reply that is all thinking', () => {
    render(
      <ThemeProvider theme={buildTheme('dark')}>
        <LlmUsageDetail parsed={geminiReply({ promptTokenCount: 12, thoughtsTokenCount: 64, totalTokenCount: 76 })} />
      </ThemeProvider>,
    );
    expect(screen.getByText('12 in / 64 out')).toBeInTheDocument();
  });

  // Vertex AI bills Gemini 2.0 Flash at 0.15 / 0.60 USD per million input / output tokens, the
  // Gemini API at 0.10 / 0.40: https://cloud.google.com/vertex-ai/generative-ai/pricing and
  // https://ai.google.dev/gemini-api/docs/pricing. Vertex AI request and response shape:
  // https://docs.cloud.google.com/vertex-ai/generative-ai/docs/model-reference/inference
  function geminiFlashCall(httpRequest: Record<string, unknown>) {
    return parseTraffic({
      httpRequest: {
        method: 'POST',
        body: { type: 'JSON', json: '{"contents":[{"role":"user","parts":[{"text":"Hi"}]}]}' },
        ...httpRequest,
      },
      httpResponse: {
        statusCode: 200,
        body: {
          type: 'JSON',
          json: JSON.stringify({
            candidates: [{ content: { role: 'model', parts: [{ text: 'Hello' }] }, finishReason: 'STOP' }],
            usageMetadata: { promptTokenCount: 1_000_000, candidatesTokenCount: 1_000_000, totalTokenCount: 2_000_000 },
            modelVersion: 'gemini-2.0-flash-001',
          }),
        },
      },
    });
  }

  const VERTEX_PATH = '/v1/projects/my-project/locations/us-central1/publishers/google/models/gemini-2.0-flash-001:generateContent';

  function renderCost(parsed: ReturnType<typeof parseTraffic>) {
    render(
      <ThemeProvider theme={buildTheme('dark')}>
        <LlmUsageDetail parsed={parsed} />
      </ThemeProvider>,
    );
  }

  it('prices a native Gemini call to a Vertex AI host at Vertex AI rates', () => {
    renderCost(geminiFlashCall({ path: VERTEX_PATH, headers: { Host: ['us-central1-aiplatform.googleapis.com:443'] } }));
    expect(screen.getByText('$0.75')).toBeInTheDocument();
  });

  it('prices a native Gemini call through a Vertex AI multi-region host at Vertex AI rates', () => {
    renderCost(geminiFlashCall({
      path: VERTEX_PATH.replace('us-central1', 'eu'),
      headers: [{ name: 'host', values: ['aiplatform.eu.rep.googleapis.com'] }],
    }));
    expect(screen.getByText('$0.75')).toBeInTheDocument();
  });

  it('takes the Vertex AI host from the socket address before the Host header, as the server does', () => {
    renderCost(geminiFlashCall({
      path: VERTEX_PATH,
      headers: { Host: ['localhost:1080'] },
      socketAddress: { host: 'aiplatform.googleapis.com', port: 443, scheme: 'HTTPS' },
    }));
    expect(screen.getByText('$0.75')).toBeInTheDocument();
  });

  it('prices a native Gemini API call at Gemini API rates', () => {
    renderCost(geminiFlashCall({
      path: '/v1beta/models/gemini-2.0-flash-001:generateContent',
      headers: { Host: ['generativelanguage.googleapis.com'] },
    }));
    expect(screen.getByText('$0.50')).toBeInTheDocument();
  });

  it('prices a Gemini call on the Vertex AI OpenAI-compatible endpoint from its usage at Vertex AI rates', () => {
    // https://docs.cloud.google.com/vertex-ai/generative-ai/docs/maas/call-open-model-apis
    renderCost(parseTraffic({
      httpRequest: {
        method: 'POST',
        path: '/v1/projects/my-project/locations/global/endpoints/openapi/chat/completions',
        headers: { Host: ['aiplatform.googleapis.com'] },
        body: { type: 'JSON', json: '{"model":"google/gemini-2.0-flash-001","messages":[{"role":"user","content":"Hi"}]}' },
      },
      httpResponse: {
        statusCode: 200,
        body: {
          type: 'JSON',
          json: JSON.stringify({
            id: 'chatcmpl-1',
            object: 'chat.completion',
            created: 1760400000,
            model: 'google/gemini-2.0-flash-001',
            choices: [{ index: 0, message: { role: 'assistant', content: 'Hello' }, finish_reason: 'stop' }],
            usage: { prompt_tokens: 1_000_000, completion_tokens: 1_000_000, total_tokens: 2_000_000 },
          }),
        },
      },
    }));
    expect(screen.getByText('1000000 in / 1000000 out')).toBeInTheDocument();
    expect(screen.getByText('$0.75')).toBeInTheDocument();
  });

  it('does not treat a host that only ends in a Vertex AI host name as Vertex AI', () => {
    renderCost(geminiFlashCall({ path: VERTEX_PATH, headers: { Host: ['evil.com-aiplatform.googleapis.com'] } }));
    expect(screen.getByText('$0.50')).toBeInTheDocument();
  });
});
