package org.mockserver.client;

import org.junit.Test;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpTemplate;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.mockserver.client.A2aMockBuilder.a2aMock;

public class A2aMockBuilderTest {

    @Test
    public void shouldBuildMinimalA2aMock() {
        Expectation[] expectations = a2aMock().build();
        assertThat(expectations.length, is(4));
    }

    @Test
    public void shouldBuildWithCustomPath() {
        Expectation[] expectations = a2aMock("/custom/a2a").build();
        assertThat(expectations.length, is(4));
    }

    @Test
    public void shouldBuildWithSkills() {
        Expectation[] expectations = a2aMock()
            .withSkill("translate")
                .withName("Translation")
                .withDescription("Translates text between languages")
                .withTag("translation")
                .withTag("language")
                .withExample("Translate hello to Spanish")
                .and()
            .withSkill("summarize")
                .withName("Summarization")
                .withDescription("Summarizes text")
                .and()
            .build();

        assertThat(expectations.length, is(4));
    }

    @Test
    public void shouldBuildWithTaskHandlers() {
        Expectation[] expectations = a2aMock()
            .onTaskSend()
                .matchingMessage("translate.*")
                .respondingWith("Translation: Hola")
                .and()
            .onTaskSend()
                .matchingMessage("summarize.*")
                .respondingWith("Summary: Brief text")
                .and()
            .build();

        assertThat(expectations.length, is(6));
    }

    @Test
    public void shouldUseStaticResponseForAgentCard() {
        Expectation[] expectations = a2aMock()
            .withAgentName("TestAgent")
            .withAgentVersion("2.0.0")
            .build();

        Expectation agentCardExp = expectations[0];
        assertNotNull(agentCardExp.getHttpResponse());
        assertThat(agentCardExp.getHttpResponse().getStatusCode(), is(200));
        String body = agentCardExp.getHttpResponse().getBodyAsString();
        assertThat(body, containsString("TestAgent"));
        assertThat(body, containsString("2.0.0"));
    }

    @Test
    public void shouldUseVelocityTemplatesForTaskResponses() {
        Expectation[] expectations = a2aMock().build();

        Expectation tasksSendExp = expectations[1];
        assertNotNull(tasksSendExp.getHttpResponseTemplate());
        assertThat(tasksSendExp.getHttpResponseTemplate().getTemplateType(), is(HttpTemplate.TemplateType.VELOCITY));
    }

    @Test
    public void shouldIncludeSkillsInAgentCard() {
        Expectation[] expectations = a2aMock()
            .withSkill("skill1")
                .withName("Skill One")
                .withDescription("First skill")
                .withTag("test")
                .and()
            .build();

        String body = expectations[0].getHttpResponse().getBodyAsString();
        assertThat(body, containsString("skill1"));
        assertThat(body, containsString("Skill One"));
        assertThat(body, containsString("First skill"));
        assertThat(body, containsString("test"));
    }

    @Test
    public void shouldSupportCustomAgentCardPath() {
        Expectation[] expectations = a2aMock()
            .withAgentCardPath("/agent-card")
            .build();

        HttpRequest request = (HttpRequest) expectations[0].getHttpRequest();
        assertThat(request.getPath().getValue(), is("/agent-card"));
    }

    @Test
    public void shouldSupportErrorTaskHandlers() {
        Expectation[] expectations = a2aMock()
            .onTaskSend()
                .matchingMessage("bad_request.*")
                .respondingWith("Error occurred", true)
                .and()
            .build();

        Expectation handler = expectations[1];
        String template = handler.getHttpResponseTemplate().getTemplate();
        assertThat(template, containsString("failed"));
    }

    @Test
    public void shouldSetDefaultTaskResponse() {
        Expectation[] expectations = a2aMock()
            .withDefaultTaskResponse("Custom default response")
            .build();

        String template = expectations[1].getHttpResponseTemplate().getTemplate();
        assertThat(template, containsString("Custom default response"));
    }

    @Test
    public void shouldBuildFullA2aMock() {
        Expectation[] expectations = a2aMock("/agent")
            .withAgentName("FullAgent")
            .withAgentDescription("A complete mock agent")
            .withAgentVersion("3.0.0")
            .withAgentUrl("http://localhost:8080/agent")
            .withSkill("translate")
                .withName("Translation")
                .withDescription("Translates text")
                .withTag("i18n")
                .withExample("Translate hello to French")
                .and()
            .withDefaultTaskResponse("Default done")
            .onTaskSend()
                .matchingMessage("translate.*")
                .respondingWith("Bonjour")
                .and()
            .build();

        assertThat(expectations.length, is(5));

        String agentCard = expectations[0].getHttpResponse().getBodyAsString();
        assertThat(agentCard, containsString("FullAgent"));
        assertThat(agentCard, containsString("http://localhost:8080/agent"));
        assertThat(agentCard, containsString("translate"));
    }

    @Test
    public void shouldPlaceCustomHandlersBeforeDefaultHandlers() {
        Expectation[] expectations = a2aMock()
            .onTaskSend()
                .matchingMessage("custom.*")
                .respondingWith("Custom response")
                .and()
            .build();

        assertThat(expectations.length, is(5));

        assertNotNull(expectations[0].getHttpResponse());

        Expectation customHandler = expectations[1];
        assertNotNull(customHandler.getHttpResponseTemplate());
        String customTemplate = customHandler.getHttpResponseTemplate().getTemplate();
        assertThat(customTemplate, containsString("Custom response"));

        Expectation defaultSend = expectations[2];
        assertNotNull(defaultSend.getHttpResponseTemplate());
    }

    @Test
    public void shouldEscapeVelocityMetacharactersInTaskResponse() {
        Expectation[] expectations = a2aMock()
            .withDefaultTaskResponse("$100 off #sale")
            .build();

        Expectation tasksSendExp = expectations[1];
        String template = tasksSendExp.getHttpResponseTemplate().getTemplate();
        assertThat(template, containsString("${esc.d}100 off ${esc.h}sale"));
    }

    @Test
    public void shouldEscapeVelocityMetacharactersInCustomHandler() {
        Expectation[] expectations = a2aMock()
            .onTaskSend()
                .matchingMessage("test.*")
                .respondingWith("Price is $50 #discount")
                .and()
            .build();

        Expectation customHandler = expectations[1];
        String template = customHandler.getHttpResponseTemplate().getTemplate();
        assertThat(template, containsString("${esc.d}50 ${esc.h}discount"));
    }

    @Test
    public void shouldEscapeSlashInCustomHandlerMessagePattern() {
        Expectation[] expectations = a2aMock()
            .onTaskSend()
                .matchingMessage("path/to/resource")
                .respondingWith("found")
                .and()
            .build();

        Expectation customHandler = expectations[1];
        HttpRequest request = (HttpRequest) customHandler.getHttpRequest();
        String body = request.getBody().toString();
        assertThat(body, containsString("path\\\\/to\\\\/resource"));
    }

    @Test
    public void shouldEscapeBackslashAndNewlineInMessagePattern() {
        Expectation[] expectations = a2aMock()
            .onTaskSend()
                .matchingMessage("line1\nline2\\d+")
                .respondingWith("found")
                .and()
            .build();

        Expectation customHandler = expectations[1];
        HttpRequest request = (HttpRequest) customHandler.getHttpRequest();
        String body = request.getBody().toString();
        assertThat(body, containsString("line1\\\\nline2\\\\d+"));
        assertFalse("Should not contain literal newline", body.contains("line1\nline2"));
    }

    @Test
    public void shouldEscapeCarriageReturnInMessagePattern() {
        Expectation[] expectations = a2aMock()
            .onTaskSend()
                .matchingMessage("before\rafter")
                .respondingWith("found")
                .and()
            .build();

        Expectation customHandler = expectations[1];
        HttpRequest request = (HttpRequest) customHandler.getHttpRequest();
        String body = request.getBody().toString();
        assertThat(body, containsString("before\\\\rafter"));
    }

    @Test
    public void shouldStripNullBytesInMessagePattern() {
        Expectation[] expectations = a2aMock()
            .onTaskSend()
                .matchingMessage("before\0after")
                .respondingWith("found")
                .and()
            .build();

        Expectation customHandler = expectations[1];
        HttpRequest request = (HttpRequest) customHandler.getHttpRequest();
        String body = request.getBody().toString();
        assertThat(body, containsString("beforeafter"));
        assertThat(body, not(containsString("\0")));
    }

    @Test
    public void shouldEscapeSpecialJsonCharacters() {
        Expectation[] expectations = a2aMock()
            .withDefaultTaskResponse("line1\nline2\ttab")
            .build();

        assertThat(expectations.length, is(4));
    }
}
