package org.mockserver.llm.codec;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.mockserver.model.Provider;

/**
 * Codec for Mistral AI ({@code api.mistral.ai}). The OpenAI Chat Completions shape, except
 * that a stream always reports usage: Mistral has no {@code stream_options} request
 * parameter and puts {@code usage} on the chunk that carries {@code finish_reason}.
 */
public class MistralCodec extends OpenAiCompatibleChatCodec {

    @Override
    public Provider provider() {
        return Provider.MISTRAL;
    }

    @Override
    protected boolean streamsUsageChunk(boolean includeUsageRequested) {
        return false;
    }

    @Override
    protected String finishChunkFields(String providerRequestId, ObjectNode usage) {
        return ",\"usage\":" + toJson(usage);
    }
}
