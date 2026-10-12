package org.mockserver.llm.codec;

import org.junit.Test;
import org.mockserver.llm.ParsedConversation;
import org.mockserver.llm.ParsedMessage;
import org.mockserver.model.HttpRequest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockserver.model.HttpRequest.request;

public class OpenAiChatCompletionsCodecDecodeTest {

    private final OpenAiChatCompletionsCodec codec = new OpenAiChatCompletionsCodec();

    @Test
    public void shouldDecodeSimpleTextConversation() {
        // given
        HttpRequest request = request()
            .withBody("{\n" +
                "  \"model\": \"gpt-4o\",\n" +
                "  \"messages\": [\n" +
                "    {\"role\": \"user\", \"content\": \"What is 2+2?\"}\n" +
                "  ]\n" +
                "}");

        // when
        ParsedConversation parsed = codec.decode(request);

        // then
        assertThat(parsed.getMessages(), hasSize(1));
        ParsedMessage msg = parsed.getMessages().get(0);
        assertThat(msg.getRole(), is(ParsedMessage.Role.USER));
        assertThat(msg.getTextContent(), is("What is 2+2?"));
        assertThat(msg.getToolCalls(), is(empty()));
        assertThat(msg.getToolResults(), is(anEmptyMap()));
    }

    @Test
    public void shouldDecodeSystemMessage() {
        // given
        HttpRequest request = request()
            .withBody("{\n" +
                "  \"model\": \"gpt-4o\",\n" +
                "  \"messages\": [\n" +
                "    {\"role\": \"system\", \"content\": \"You are helpful.\"},\n" +
                "    {\"role\": \"user\", \"content\": \"Hello\"}\n" +
                "  ]\n" +
                "}");

        // when
        ParsedConversation parsed = codec.decode(request);

        // then
        assertThat(parsed.getMessages(), hasSize(2));
        assertThat(parsed.getMessages().get(0).getRole(), is(ParsedMessage.Role.SYSTEM));
        assertThat(parsed.getMessages().get(0).getTextContent(), is("You are helpful."));
        assertThat(parsed.getMessages().get(1).getRole(), is(ParsedMessage.Role.USER));
    }

    @Test
    public void shouldDecodeAssistantToolCalls() {
        // given
        HttpRequest request = request()
            .withBody("{\n" +
                "  \"model\": \"gpt-4o\",\n" +
                "  \"messages\": [\n" +
                "    {\"role\": \"user\", \"content\": \"Search for X\"},\n" +
                "    {\"role\": \"assistant\", \"content\": null, \"tool_calls\": [\n" +
                "      {\"id\": \"call_abc\", \"type\": \"function\", \"function\": {\"name\": \"search\", \"arguments\": \"{\\\"q\\\":\\\"X\\\"}\"}}\n" +
                "    ]}\n" +
                "  ]\n" +
                "}");

        // when
        ParsedConversation parsed = codec.decode(request);

        // then
        assertThat(parsed.getMessages(), hasSize(2));

        ParsedMessage assistantMsg = parsed.getMessages().get(1);
        assertThat(assistantMsg.getRole(), is(ParsedMessage.Role.ASSISTANT));
        assertThat(assistantMsg.getTextContent(), is(""));
        assertThat(assistantMsg.getToolCalls(), hasSize(1));
        assertThat(assistantMsg.getToolCalls().get(0).getName(), is("search"));
        assertThat(assistantMsg.getToolCalls().get(0).getId(), is("call_abc"));
        assertThat(assistantMsg.getToolCalls().get(0).getArguments(), containsString("X"));
    }

    @Test
    public void shouldDecodeToolRoleMessages() {
        // given
        HttpRequest request = request()
            .withBody("{\n" +
                "  \"model\": \"gpt-4o\",\n" +
                "  \"messages\": [\n" +
                "    {\"role\": \"user\", \"content\": \"Search\"},\n" +
                "    {\"role\": \"assistant\", \"content\": null, \"tool_calls\": [\n" +
                "      {\"id\": \"call_xyz\", \"type\": \"function\", \"function\": {\"name\": \"search\", \"arguments\": \"{}\"}}\n" +
                "    ]},\n" +
                "    {\"role\": \"tool\", \"tool_call_id\": \"call_xyz\", \"content\": \"search result data\"}\n" +
                "  ]\n" +
                "}");

        // when
        ParsedConversation parsed = codec.decode(request);

        // then
        assertThat(parsed.getMessages(), hasSize(3));

        ParsedMessage toolMsg = parsed.getMessages().get(2);
        assertThat(toolMsg.getRole(), is(ParsedMessage.Role.TOOL));
        assertThat(toolMsg.getToolResults(), hasEntry("call_xyz", "search result data"));
        assertThat(toolMsg.getTextContent(), is("search result data"));
    }

    @Test
    public void shouldDecodeContentAsArray() {
        // given
        HttpRequest request = request()
            .withBody("{\n" +
                "  \"model\": \"gpt-4o\",\n" +
                "  \"messages\": [\n" +
                "    {\"role\": \"user\", \"content\": [{\"type\": \"text\", \"text\": \"Part A\"}, {\"type\": \"text\", \"text\": \" Part B\"}]}\n" +
                "  ]\n" +
                "}");

        // when
        ParsedConversation parsed = codec.decode(request);

        // then
        assertThat(parsed.getMessages(), hasSize(1));
        assertThat(parsed.getMessages().get(0).getTextContent(), is("Part A Part B"));
    }

    @Test
    public void shouldReturnEmptyForMalformedJson() {
        // given
        HttpRequest request = request().withBody("not json at all");

        // when
        ParsedConversation parsed = codec.decode(request);

        // then
        assertThat(parsed.getMessages(), is(empty()));
    }

    @Test
    public void shouldReturnEmptyForNullBody() {
        // given
        HttpRequest request = request();

        // when
        ParsedConversation parsed = codec.decode(request);

        // then
        assertThat(parsed.getMessages(), is(empty()));
    }

    @Test
    public void shouldReturnEmptyForWrongShape() {
        // given - no messages array
        HttpRequest request = request().withBody("{\"model\": \"gpt-4o\"}");

        // when
        ParsedConversation parsed = codec.decode(request);

        // then
        assertThat(parsed.getMessages(), is(empty()));
    }

    @Test
    public void shouldReturnEmptyForNullRequest() {
        // when
        ParsedConversation parsed = codec.decode(null);

        // then
        assertThat(parsed.getMessages(), is(empty()));
    }

    @Test
    public void shouldDecodeFullToolCallLoop() {
        // given
        HttpRequest request = request()
            .withBody("{\n" +
                "  \"model\": \"gpt-4o\",\n" +
                "  \"messages\": [\n" +
                "    {\"role\": \"system\", \"content\": \"You are helpful.\"},\n" +
                "    {\"role\": \"user\", \"content\": \"What is the weather?\"},\n" +
                "    {\"role\": \"assistant\", \"content\": null, \"tool_calls\": [\n" +
                "      {\"id\": \"call_1\", \"type\": \"function\", \"function\": {\"name\": \"get_weather\", \"arguments\": \"{\\\"city\\\":\\\"Paris\\\"}\"}}\n" +
                "    ]},\n" +
                "    {\"role\": \"tool\", \"tool_call_id\": \"call_1\", \"content\": \"18C and sunny\"},\n" +
                "    {\"role\": \"assistant\", \"content\": \"It is 18C and sunny in Paris.\"}\n" +
                "  ]\n" +
                "}");

        // when
        ParsedConversation parsed = codec.decode(request);

        // then
        assertThat(parsed.getMessages(), hasSize(5));

        assertThat(parsed.getMessages().get(0).getRole(), is(ParsedMessage.Role.SYSTEM));
        assertThat(parsed.getMessages().get(1).getRole(), is(ParsedMessage.Role.USER));
        assertThat(parsed.getMessages().get(2).getRole(), is(ParsedMessage.Role.ASSISTANT));
        assertThat(parsed.getMessages().get(2).getToolCalls(), hasSize(1));
        assertThat(parsed.getMessages().get(2).getToolCalls().get(0).getId(), is("call_1"));

        assertThat(parsed.getMessages().get(3).getRole(), is(ParsedMessage.Role.TOOL));
        assertThat(parsed.getMessages().get(3).getToolResults(), hasEntry("call_1", "18C and sunny"));

        assertThat(parsed.getMessages().get(4).getRole(), is(ParsedMessage.Role.ASSISTANT));
        assertThat(parsed.getMessages().get(4).getTextContent(), is("It is 18C and sunny in Paris."));
    }

    @Test
    public void shouldHandleNullContent() {
        // given
        HttpRequest request = request()
            .withBody("{\n" +
                "  \"messages\": [\n" +
                "    {\"role\": \"assistant\", \"content\": null}\n" +
                "  ]\n" +
                "}");

        // when
        ParsedConversation parsed = codec.decode(request);

        // then
        assertThat(parsed.getMessages(), hasSize(1));
        assertThat(parsed.getMessages().get(0).getTextContent(), is(""));
    }

    @Test
    public void shouldDecodeDeveloperMessageAsSystem() {
        // "With o1 models and newer, developer messages replace the previous system messages."
        // (openai-python types/chat/chat_completion_developer_message_param.py)
        HttpRequest request = request()
            .withBody("{\n" +
                "  \"model\": \"o3\",\n" +
                "  \"messages\": [\n" +
                "    {\"role\": \"developer\", \"content\": [{\"type\": \"text\", \"text\": \"Answer in French.\"}]},\n" +
                "    {\"role\": \"user\", \"content\": \"Hello\"}\n" +
                "  ]\n" +
                "}");

        ParsedConversation parsed = codec.decode(request);

        assertThat(parsed.getMessages(), hasSize(2));
        assertThat(parsed.getMessages().get(0).getRole(), is(ParsedMessage.Role.SYSTEM));
        assertThat(parsed.getMessages().get(0).getTextContent(), is("Answer in French."));
        assertThat(parsed.getMessages().get(1).getRole(), is(ParsedMessage.Role.USER));
    }
}
