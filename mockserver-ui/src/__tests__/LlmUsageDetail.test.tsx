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
});
