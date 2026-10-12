# LLM Codec Golden-File Fixtures

This directory holds codec-generated golden files for automated LLM provider
codec drift detection. Each provider has its own subdirectory containing
normalized encode output that is asserted on every test run by
`LlmCodecGoldenFileTest`.

See [docs/code/llm-codec-fixtures.md](../../../../../../../docs/code/llm-codec-fixtures.md)
for the full golden-file process, normalization rules, and refresh instructions.

## Directory layout

```
fixtures/
  anthropic/          Anthropic Messages API codec output
  openai/             OpenAI Chat Completions API codec output
  openai-responses/   OpenAI Responses API codec output
  gemini/             Google Gemini generateContent API codec output
  bedrock/            AWS Bedrock InvokeModel (Anthropic-on-Bedrock) codec output
  bedrock-converse/   AWS Bedrock Converse / ConverseStream codec output
  azure-openai/       Azure OpenAI Chat Completions codec output
  ollama/             Ollama /api/chat codec output (generate-* files: /api/generate)
  embeddings/         every embedding wire shape (LlmEmbeddingGoldenFileTest)
```

Each directory contains:

- `text-completion.json` -- normalized non-streaming text completion
- `tool-call.json` -- normalized non-streaming tool-call completion
- `streaming-text.jsonl` -- normalized streaming text completion (one event per line)
- `streaming-tool-call.jsonl` -- normalized streaming tool-call completion

## Refresh

To regenerate golden files after intentional codec changes:

```bash
mvn -pl mockserver/mockserver-core test \
  -Dtest=LlmCodecGoldenFileTest \
  -Dmockserver.updateLlmGoldens=true
```

Review the diff and commit alongside codec changes.
