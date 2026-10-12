package org.mockserver.llm.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.action.http.HttpLlmResponseActionHandler;
import org.mockserver.model.EmbeddingResponse;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.Provider;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockserver.llm.codec.CohereImageDescriptionTest.dataUri;
import static org.mockserver.llm.codec.CohereImageDescriptionTest.encode;
import static org.mockserver.model.HttpLlmResponse.llmResponse;
import static org.mockserver.model.HttpRequest.request;

/**
 * Bedrock Cohere Embed image inputs, against the AWS model-parameters pages:
 * v3 (https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-embed-v3.html)
 * returns {@code "images":[{"width","height","format","bit_depth"}]} for an image request and no
 * {@code images} for a text request; v4
 * (https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-embed-v4.html) embeds
 * each {@code inputs} item from its text and {@code image_url} parts.
 */
public class BedrockCohereImageEmbeddingTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String V3 = "/model/cohere.embed-english-v3/invoke";
    private static final String V4 = "/model/cohere.embed-v4:0/invoke";

    private final HttpLlmResponseActionHandler handler = new HttpLlmResponseActionHandler(new MockServerLogger());

    private JsonNode post(String path, String body) throws Exception {
        HttpResponse response = handler.handle(
            llmResponse().withProvider(Provider.BEDROCK).withEmbedding(EmbeddingResponse.embedding().withDeterministicFromInput(true).withSeed(7L)),
            request().withMethod("POST").withPath(path).withBody(body)
        );
        assertThat(response.getBodyAsString(), response.getStatusCode(), is(200));
        return OBJECT_MAPPER.readTree(response.getBodyAsString());
    }

    private static List<Double> floats(JsonNode array) {
        List<Double> values = new ArrayList<>();
        array.forEach(v -> values.add(v.asDouble()));
        return values;
    }

    private static String quoted(String value) {
        return "\"" + value + "\"";
    }

    private static String imagePart(String uri) {
        return "{\"type\":\"image_url\",\"image_url\":{\"url\":" + quoted(uri) + "}}";
    }

    @Test
    public void shouldDescribeTheCohereV3ImageInEveryResponseType() throws Exception {
        String png = dataUri("image/png", encode(64, 48, BufferedImage.TYPE_INT_ARGB, "png"));
        for (String types : new String[]{"", ",\"embedding_types\":[\"int8\",\"float\"]"}) {
            JsonNode root = post(V3, "{\"images\":[" + quoted(png) + "],\"input_type\":\"image\"" + types + "}");
            JsonNode images = root.get("images");
            assertThat(images.size(), is(1));
            JsonNode image = images.get(0);
            assertThat(image.size(), is(4));
            assertThat(image.get("width").isIntegralNumber(), is(true));
            assertThat(image.get("width").asLong(), is(64L));
            assertThat(image.get("height").asLong(), is(48L));
            assertThat(image.get("format").asText(), is("png"));
            assertThat(image.get("bit_depth").isIntegralNumber(), is(true));
            assertThat(image.get("bit_depth").asLong(), is(32L));
            assertThat(root.get("texts").size(), is(0));
        }

        String jpeg = dataUri("image/jpeg", encode(400, 400, BufferedImage.TYPE_INT_RGB, "jpg"));
        JsonNode root = post(V3, "{\"images\":[" + quoted(jpeg) + "],\"input_type\":\"image\"}");
        assertThat(root.get("images").toString(), is("[{\"width\":400,\"height\":400,\"format\":\"jpeg\",\"bit_depth\":24}]"));
    }

    @Test
    public void shouldStillEmbedACohereV3ImageWhoseHeaderCannotBeRead() throws Exception {
        JsonNode root = post(V3, "{\"images\":[\"data:image/png;base64,AAAA\"],\"input_type\":\"image\"}");
        assertThat(root.get("embeddings").size(), is(1));
        assertThat(root.get("images").toString(), is("[{\"width\":0,\"height\":0,\"format\":\"png\",\"bit_depth\":0}]"));
    }

    @Test
    public void shouldOmitImagesWhenCohereEmbedsTexts() throws Exception {
        assertThat(post(V3, "{\"texts\":[\"a\"],\"input_type\":\"search_document\"}").has("images"), is(false));
        assertThat(post(V4, "{\"texts\":[\"a\"],\"input_type\":\"search_document\"}").has("images"), is(false));
    }

    @Test
    public void shouldEmbedEachCohereV4InputFromItsImagesAsWellAsItsText() throws Exception {
        String red = dataUri("image/png", encode(5, 5, BufferedImage.TYPE_INT_RGB, "png"));
        String other = dataUri("image/png", encode(6, 5, BufferedImage.TYPE_INT_RGB, "png"));
        JsonNode root = post(V4, "{\"input_type\":\"search_document\",\"inputs\":["
            + "{\"content\":[" + imagePart(red) + "]},"
            + "{\"content\":[" + imagePart(other) + "]},"
            + "{\"content\":[" + imagePart(red) + "]},"
            + "{\"content\":[{\"type\":\"text\",\"text\":\"page one\"}," + imagePart(red) + "]},"
            + "{\"content\":[{\"type\":\"text\",\"text\":\"page one\"}]}]}");
        JsonNode embeddings = root.get("embeddings");
        assertThat(embeddings.size(), is(5));
        assertThat("different images give different vectors", floats(embeddings.get(0)), is(not(floats(embeddings.get(1)))));
        assertThat("the same image gives the same vector", floats(embeddings.get(0)), is(floats(embeddings.get(2))));
        assertThat("an image changes a captioned item's vector", floats(embeddings.get(3)), is(not(floats(embeddings.get(4)))));
        assertThat(root.get("inputs").size(), is(5));

        JsonNode texts = post(V4, "{\"input_type\":\"search_document\",\"texts\":[\"page one\"]}");
        assertThat("a text-only item embeds as its text", floats(embeddings.get(4)), is(floats(texts.get("embeddings").get(0))));
        JsonNode images = post(V4, "{\"input_type\":\"search_document\",\"images\":[" + quoted(red) + "]}");
        assertThat("an image-only item embeds as the same image in images", floats(embeddings.get(0)), is(floats(images.get("embeddings").get(0))));
    }
}
