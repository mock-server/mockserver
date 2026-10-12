package org.mockserver.llm.codec;

import org.mockserver.model.Provider;

/**
 * Base codec for OpenAI-chat-compatible providers (Mistral, xAI/Grok, DeepSeek,
 * Groq, OpenRouter, OrcaRouter). These providers expose the OpenAI Chat Completions wire format
 * ({@code POST /v1/chat/completions}, {@code Authorization: Bearer}) on a different
 * host, so encoding and decoding come from {@link OpenAiChatCompletionsCodec}. A subclass
 * adds what its provider sends on top of that shape (its reasoning field, extra usage
 * fields, usage streamed without {@code stream_options.include_usage}) by overriding the
 * hooks {@link OpenAiChatCompletionsCodec} declares.
 */
public abstract class OpenAiCompatibleChatCodec extends OpenAiChatCompletionsCodec {

    @Override
    public abstract Provider provider();
}
