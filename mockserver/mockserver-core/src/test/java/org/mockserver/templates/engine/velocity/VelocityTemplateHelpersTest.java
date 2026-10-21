package org.mockserver.templates.engine.velocity;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.serialization.model.HttpResponseDTO;
import org.mockserver.templates.engine.TemplateFunctions;
import org.mockserver.templates.engine.helpers.TemplateFaker;
import org.slf4j.event.Level;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isIn;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;

public class VelocityTemplateHelpersTest {

    private static final HttpRequest REQUEST = request()
        .withPath("/colours")
        .withQueryStringParameter("colour", "teal", "amber")
        .withQueryStringParameter("count", "3")
        .withBody("<order><item>a</item><item>b</item></order>");

    @Mock
    private MockServerLogger mockServerLogger;

    private VelocityTemplateEngine engine;

    @Before
    public void setupTestFixture() {
        openMocks(this);
        when(mockServerLogger.isEnabledForInstance(any(Level.class))).thenReturn(true);
        engine = new VelocityTemplateEngine(mockServerLogger, configuration());
    }

    private String render(String template) {
        return engine.renderTemplate(template, REQUEST);
    }

    @Test
    public void shouldPickRandomlyFromArgumentsOrAList() {
        Set<String> picked = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            picked.add(render("$lists.pickRandom('red', 'green', 'blue')"));
        }
        assertThat(picked, containsInAnyOrder("red", "green", "blue"));
        assertThat(render("$lists.pickRandom($request.queryStringParameters.colour)"), isIn(List.of("teal", "amber")));
        assertThat(render("$lists.pickRandom(['x'])"), is("x"));
    }

    @Test
    public void shouldPickTheSameSequenceWhenTheFakerSeedIsSet() {
        VelocityTemplateEngine first = new VelocityTemplateEngine(mockServerLogger, configuration().templateFakerSeed(42L));
        VelocityTemplateEngine second = new VelocityTemplateEngine(mockServerLogger, configuration().templateFakerSeed(42L));
        String template = "#foreach($i in [1..20])$lists.pickRandom('a','b','c','d','e','f','g','h')#end";
        assertThat(first.renderTemplate(template, REQUEST), is(second.renderTemplate(template, REQUEST)));
    }

    @Test
    public void shouldBuildJoinAndIterateLists() {
        assertThat(render("$lists.arrayJoin(', ', $lists.array('a', 'b', 'c'))"), is("a, b, c"));
        assertThat(render("$lists.arrayJoin('-', 'x', 'y')"), is("x-y"));
        assertThat(render("$lists.arrayJoin(';', $request.queryStringParameters.colour)"), is("teal;amber"));
        assertThat(render("#foreach($n in $lists.range(1, $request.queryStringParameters.count[0]))$n#if($foreach.hasNext),#end#end"), is("1,2,3"));
        assertThat(render("$lists.range(3, 1)"), is("[3, 2, 1]"));
    }

    @Test
    public void shouldFailForARangeThatIsTooLarge() {
        RuntimeException failure = assertThrows(RuntimeException.class, () -> render("$lists.range(1, 1000000)"));
        assertThat(helperMessage(failure), is("lists.range: a range has at most 10000 values, but 1 to 1000000 has 1000000"));
    }

    @Test
    public void shouldFormatTheRequestXml() {
        assertThat(render("$xpath.formatXml($request.body)"), is("<order>\n  <item>a</item>\n  <item>b</item>\n</order>"));
    }

    @Test
    public void shouldRenderTheHostname() {
        assertThat(render("$hostname"), is(TemplateFunctions.hostname()));
    }

    @Test
    public void shouldGenerateFakeDataForALocale() {
        VelocityTemplateEngine seeded = new VelocityTemplateEngine(mockServerLogger, configuration().templateFakerSeed(7L));
        String expected = new TemplateFaker(7L).locale("de-DE").name().firstName();
        assertThat(seeded.renderTemplate("$faker.locale('de-DE').name().firstName()", REQUEST), is(expected));
        assertThat(render("#set($german = $faker.locale('de'))$german.address().city()").isEmpty(), is(false));
        String unsupported = new TemplateFaker(7L).name().firstName();
        assertThat(new VelocityTemplateEngine(mockServerLogger, configuration().templateFakerSeed(7L)).renderTemplate("$faker.locale($request.path).name().firstName()", REQUEST), is(unsupported));
    }

    @Test
    public void shouldExposeTemplateParameters() {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("greeting", "hello");
        parameters.put("names", List.of("ann", "bob"));
        parameters.put("limits", Map.of("max", 5));
        String body = engine.executeTemplate(
            "{ 'statusCode': 200, 'body': '$parameters.greeting $lists.arrayJoin(\" and \", $parameters.names) max $parameters.limits.max' }",
            parameters, REQUEST, null, HttpResponseDTO.class).getBodyAsString();
        assertThat(body, is("hello ann and bob max 5"));
    }

    @Test
    public void shouldLeaveParametersUnboundWhenThereAreNone() {
        String template = "{ 'statusCode': 200, 'body': '[$!parameters.greeting]' }";
        assertThat(engine.executeTemplate(template, null, REQUEST, null, HttpResponseDTO.class).getBodyAsString(), is("[]"));
        assertThat(engine.executeTemplate(template, REQUEST, HttpResponseDTO.class).getBodyAsString(), is("[]"));
    }

    private static String helperMessage(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof IllegalArgumentException) {
                return cause.getMessage();
            }
        }
        return failure.getMessage();
    }
}
