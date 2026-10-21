package org.mockserver.mock.action.http;

import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpTemplate;
import org.mockserver.serialization.ExpectationSerializer;
import org.mockserver.serialization.java.HttpTemplateToJavaSerializer;
import org.mockserver.templates.engine.javascript.JavaScriptTemplateEngine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpTemplate.template;
import static org.mockserver.validator.jsonschema.JsonSchemaExpectationValidator.jsonSchemaExpectationValidator;

public class HttpTemplateParametersTest {

    private static final HttpRequest REQUEST = request().withPath("/greet");

    private final HttpResponseTemplateActionHandler handler = new HttpResponseTemplateActionHandler(mock(MockServerLogger.class), new Configuration().javascriptTemplateExecutionTimeout(0L));

    private static Map<String, Object> parameters() {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("greeting", "hello");
        parameters.put("names", Arrays.asList("ann", "bob"));
        parameters.put("empty", "");
        return parameters;
    }

    @Test
    public void shouldRenderTheParametersOfAMustacheResponseTemplate() {
        HttpTemplate httpTemplate = template(HttpTemplate.TemplateType.MUSTACHE, "{ 'statusCode': 200, 'body': '{{ parameters.greeting }} {{ parameters.names.1 }}' }")
            .withParameters(parameters());
        assertThat(handler.handle(httpTemplate, REQUEST).getBodyAsString(), is("hello bob"));
    }

    @Test
    public void shouldRenderTheParametersOfAVelocityResponseTemplate() {
        HttpTemplate httpTemplate = template(HttpTemplate.TemplateType.VELOCITY, "{ 'statusCode': 200, 'body': '$parameters.greeting $parameters.names[0]' }")
            .withParameter("greeting", "hi")
            .withParameter("names", List.of("cat"));
        assertThat(handler.handle(httpTemplate, REQUEST).getBodyAsString(), is("hi cat"));
    }

    @Test
    public void shouldRenderTheParametersOfAJavaScriptResponseTemplate() {
        assumeTrue("GraalVM Polyglot API available", JavaScriptTemplateEngine.isPolyglotAvailable());
        HttpTemplate httpTemplate = template(HttpTemplate.TemplateType.JAVASCRIPT, "return { 'statusCode': 200, 'body': parameters.greeting + ' ' + parameters.names.length };")
            .withParameters(parameters());
        assertThat(handler.handle(httpTemplate, REQUEST).getBodyAsString(), is("hello 2"));
    }

    // the templates in the Template Parameters examples on response_templates.html
    @Test
    public void shouldRenderTheDocumentedExampleInEachEngine() {
        assumeTrue("GraalVM Polyglot API available", JavaScriptTemplateEngine.isPolyglotAvailable());
        Map<HttpTemplate.TemplateType, String> examples = new LinkedHashMap<>();
        examples.put(HttpTemplate.TemplateType.MUSTACHE, "{ 'statusCode': 200, 'headers': { 'X-Served-By': [ '{{ hostname }}' ] }, 'body': '{{ parameters.greeting }} {{#lists.pickRandom}}{{#parameters.names}}{{.}}|{{/parameters.names}}{{/lists.pickRandom}}, pages {{#lists.range.1.3}}{{.}}{{^-last}},{{/-last}}{{/lists.range.1.3}}' }");
        examples.put(HttpTemplate.TemplateType.VELOCITY, "{ 'statusCode': 200, 'headers': { 'X-Served-By': [ '$hostname' ] }, 'body': '$parameters.greeting $lists.pickRandom($parameters.names), pages $lists.arrayJoin(',', $lists.range(1, 3))' }");
        examples.put(HttpTemplate.TemplateType.JAVASCRIPT, "return { 'statusCode': 200, 'headers': { 'X-Served-By': [ hostname ] }, 'body': parameters.greeting + ' ' + lists.pickRandom(parameters.names) + ', pages ' + lists.arrayJoin(',', lists.range(1, 3)) };");
        examples.forEach((type, example) -> {
            org.mockserver.model.HttpResponse response = handler.handle(template(type, example)
                .withParameter("greeting", "hello")
                .withParameter("names", Arrays.asList("ann", "bob")), REQUEST);
            assertThat(type.name(), response.getBodyAsString(), org.hamcrest.Matchers.isIn(List.of("hello ann, pages 1,2,3", "hello bob, pages 1,2,3")));
            assertThat(type.name(), response.getFirstHeader("X-Served-By"), is(org.mockserver.templates.engine.TemplateFunctions.hostname()));
        });
    }

    @Test
    public void shouldNotLetATemplateChangeItsParameters() {
        HttpTemplate httpTemplate = template(HttpTemplate.TemplateType.VELOCITY, "#set($ignored = $parameters.put('greeting', 'changed')){ 'statusCode': 200, 'body': '$parameters.greeting' }")
            .withParameters(parameters());
        try {
            handler.handle(httpTemplate, REQUEST);
        } catch (RuntimeException templateFailedAsExpected) {
            // the read-only map refuses put, so the template fails whichever way failures are configured
        }
        assertThat(httpTemplate.getParameters().get("greeting"), is("hello"));
        assertThrows(UnsupportedOperationException.class, () -> httpTemplate.getParameters().put("x", "y"));
        @SuppressWarnings("unchecked")
        List<Object> names = (List<Object>) httpTemplate.getParameters().get("names");
        assertThrows(UnsupportedOperationException.class, () -> names.add("eve"));
    }

    @Test
    public void shouldCopyParametersAndTreatEmptyParametersAsNone() {
        Map<String, Object> original = parameters();
        HttpTemplate httpTemplate = template(HttpTemplate.TemplateType.MUSTACHE, "x").withParameters(original);
        original.put("greeting", "changed");
        assertThat(httpTemplate.getParameters().get("greeting"), is("hello"));
        assertThat(template(HttpTemplate.TemplateType.MUSTACHE, "x").withParameters(Map.of()).getParameters(), is(nullValue()));
        assertThat(template(HttpTemplate.TemplateType.MUSTACHE, "x").withParameters(original), not(is(template(HttpTemplate.TemplateType.MUSTACHE, "x"))));
        assertThat(template(HttpTemplate.TemplateType.MUSTACHE, "x").withParameter("a", new Object[]{"b"}).getParameters().get("a"), is(List.of("b")));
    }

    @Test
    public void shouldRoundTripParametersThroughJsonKeepingEmptyValues() {
        ExpectationSerializer serializer = new ExpectationSerializer(new MockServerLogger());
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("max", 5);
        nested.put("none", null);
        Map<String, Object> parameters = parameters();
        parameters.put("limits", nested);
        parameters.put("list", new ArrayList<>());
        Expectation expectation = new Expectation(request().withPath("/greet"))
            .thenRespond(template(HttpTemplate.TemplateType.MUSTACHE, "{{ parameters.greeting }}").withParameters(parameters));

        String json = serializer.serialize(expectation);
        assertThat(json, containsString("\"parameters\" : {"));
        assertThat(json, containsString("\"empty\" : \"\""));
        assertThat(json, containsString("\"none\" : null"));
        assertThat(jsonSchemaExpectationValidator(new MockServerLogger()).isValid(json), is(""));

        Expectation deserialized = serializer.deserialize(json);
        Map<String, Object> roundTripped = deserialized.getHttpResponseTemplate().getParameters();
        assertThat(roundTripped.get("names"), instanceOf(List.class));
        assertThat(roundTripped, is(expectation.getHttpResponseTemplate().getParameters()));
        assertThat(deserialized.getHttpResponseTemplate(), is(expectation.getHttpResponseTemplate()));
    }

    @Test
    public void shouldLeaveParametersOutOfJsonWhenThereAreNone() {
        String json = new ExpectationSerializer(new MockServerLogger()).serialize(new Expectation(request().withPath("/greet"))
            .thenRespond(template(HttpTemplate.TemplateType.MUSTACHE, "x")));
        assertThat(json, not(containsString("parameters")));
    }

    @Test
    public void shouldWriteParametersAsJava() {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("greeting", "say \"hi\"");
        parameters.put("count", 3);
        parameters.put("big", 3L);
        parameters.put("ratio", 0.5);
        parameters.put("on", true);
        parameters.put("names", Arrays.asList("ann", null));
        parameters.put("limits", Map.of("max", 5));
        String java = new HttpTemplateToJavaSerializer().serialize(0, template(HttpTemplate.TemplateType.VELOCITY, "x").withParameters(parameters));
        assertThat(java, containsString(".withParameter(\"greeting\", \"say \\\"hi\\\"\")"));
        assertThat(java, containsString(".withParameter(\"count\", 3)"));
        assertThat(java, containsString(".withParameter(\"big\", 3L)"));
        assertThat(java, containsString(".withParameter(\"ratio\", 0.5)"));
        assertThat(java, containsString(".withParameter(\"on\", true)"));
        assertThat(java, containsString(".withParameter(\"names\", java.util.Arrays.<Object>asList(\"ann\", (Object) null))"));
        assertThat(java, containsString(".withParameter(\"limits\", java.util.Map.of(\"max\", 5))"));
    }

    @Test
    public void shouldGenerateJavaThatCompilesAndRebuildsTheSameParameters() throws Exception {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("x", null);
        options.put("y", Arrays.asList((Object) null));
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("ids", Arrays.asList((Object) null));
        parameters.put("names", Arrays.asList("ann", null));
        parameters.put("opts", options);
        parameters.put("limits", Map.of("max", 5));
        parameters.put("huge", new java.math.BigInteger("123456789012345678901234567890"));
        parameters.put("count", 3L);
        HttpTemplate expected = template(HttpTemplate.TemplateType.VELOCITY, "x").withParameters(parameters);
        String source = "import org.mockserver.model.HttpTemplate;\n" +
            "import static org.mockserver.model.HttpTemplate.template;\n" +
            "public class GeneratedTemplate { public static HttpTemplate build() { return " +
            new HttpTemplateToJavaSerializer().serialize(0, expected) + "; } }";
        java.nio.file.Path directory = java.nio.file.Files.createDirectories(java.nio.file.Paths.get("target", "generated-template-test", String.valueOf(System.nanoTime())));
        java.nio.file.Path file = directory.resolve("GeneratedTemplate.java");
        java.nio.file.Files.writeString(file, source);
        javax.tools.JavaCompiler compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        assumeTrue("a JDK compiler is available", compiler != null);
        java.io.ByteArrayOutputStream errors = new java.io.ByteArrayOutputStream();
        int result = compiler.run(null, null, errors, "-classpath", System.getProperty("java.class.path"), "-d", directory.toString(), file.toString());
        assertThat(errors.toString(), result, is(0));
        try (java.net.URLClassLoader loader = new java.net.URLClassLoader(new java.net.URL[]{directory.toUri().toURL()}, getClass().getClassLoader())) {
            Object built = loader.loadClass("GeneratedTemplate").getMethod("build").invoke(null);
            assertThat(((HttpTemplate) built).getParameters(), is(expected.getParameters()));
        }
    }

    @Test
    public void shouldWriteNestedNullsAndLargeNumbersAsJavaThatRuns() {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("x", null);
        options.put("y", 1);
        String java = new HttpTemplateToJavaSerializer().serialize(0, template(HttpTemplate.TemplateType.VELOCITY, "x")
            .withParameter("opts", options)
            .withParameter("huge", new java.math.BigInteger("123456789012345678901234567890"))
            .withParameter("precise", new java.math.BigDecimal("0.1000000000000000000001")));
        assertThat(java, containsString(".withParameter(\"opts\", new java.util.LinkedHashMap<String, Object>() {{ put(\"x\", null); put(\"y\", 1); }})"));
        assertThat(java, containsString(".withParameter(\"huge\", new java.math.BigInteger(\"123456789012345678901234567890\"))"));
        assertThat(java, containsString(".withParameter(\"precise\", new java.math.BigDecimal(\"0.1000000000000000000001\"))"));
    }
}
