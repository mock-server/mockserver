package org.mockserver.templates.engine.mustache;

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
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;

public class MustacheTemplateHelpersTest {

    private static final HttpRequest REQUEST = request()
        .withPath("/colours")
        .withQueryStringParameter("colour", "teal", "amber")
        .withBody("<order><item>a</item><item>b</item></order>");

    @Mock
    private MockServerLogger mockServerLogger;

    private MustacheTemplateEngine engine;

    @Before
    public void setupTestFixture() {
        openMocks(this);
        when(mockServerLogger.isEnabledForInstance(any(Level.class))).thenReturn(true);
        engine = new MustacheTemplateEngine(mockServerLogger, configuration());
    }

    private String render(String template) {
        return engine.renderTemplate(template, REQUEST);
    }

    @Test
    public void shouldPickRandomlyFromValuesInTheSection() {
        Set<String> picked = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            picked.add(render("{{#lists.pickRandom}} red | green |blue{{/lists.pickRandom}}"));
        }
        assertThat(picked, containsInAnyOrder("red", "green", "blue"));
    }

    @Test
    public void shouldPickRandomlyFromAListRenderedIntoTheSection() {
        String picked = render("{{#lists.pickRandom}}{{#request.queryStringParameters.colour}}{{.}}|{{/request.queryStringParameters.colour}}{{/lists.pickRandom}}");
        assertThat(picked, isIn(List.of("teal", "amber")));
    }

    @Test
    public void shouldPickTheSameSequenceWhenTheFakerSeedIsSet() {
        MustacheTemplateEngine first = new MustacheTemplateEngine(mockServerLogger, configuration().templateFakerSeed(42L));
        MustacheTemplateEngine second = new MustacheTemplateEngine(mockServerLogger, configuration().templateFakerSeed(42L));
        String template = "{{#lists.pickRandom}}a|b|c|d|e|f|g|h{{/lists.pickRandom}}";
        StringBuilder firstPicks = new StringBuilder();
        StringBuilder secondPicks = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            firstPicks.append(first.renderTemplate(template, REQUEST));
            secondPicks.append(second.renderTemplate(template, REQUEST));
        }
        assertThat(firstPicks.toString(), is(secondPicks.toString()));
    }

    @Test
    public void shouldFailToPickFromAnEmptySection() {
        RuntimeException failure = assertThrows(RuntimeException.class, () -> render("{{#lists.pickRandom}} | {{/lists.pickRandom}}"));
        assertThat(rootMessage(failure), is("lists.pickRandom: there is nothing to pick from; pass at least one value"));
    }

    @Test
    public void shouldJoinValuesWithTheSeparatorWrittenFirst() {
        assertThat(render("{{#lists.arrayJoin}}, |a|b|c{{/lists.arrayJoin}}"), is("a, b, c"));
        assertThat(render("[{{#lists.arrayJoin}};|{{#request.queryStringParameters.colour}}{{.}}|{{/request.queryStringParameters.colour}}{{/lists.arrayJoin}}]"), is("[teal;amber]"));
        assertThat(rootMessage(assertThrows(RuntimeException.class, () -> render("{{#lists.arrayJoin}}abc{{/lists.arrayJoin}}"))),
            containsString("write the separator first"));
    }

    @Test
    public void shouldIterateOverARangeNamedByItsBounds() {
        assertThat(render("{{#lists.range.1.5}}{{.}}{{^-last}},{{/-last}}{{/lists.range.1.5}}"), is("1,2,3,4,5"));
        assertThat(render("{{#lists.range.3.1}}{{.}}{{/lists.range.3.1}}"), is("321"));
        assertThat(render("{{#lists.range.-1.1}}[{{.}}]{{/lists.range.-1.1}}"), is("[-1][0][1]"));
        assertThat(render("{{#lists.range.0.2}}{{-index}}{{/lists.range.0.2}}"), is("123"));
    }

    @Test
    public void shouldFailForARangeThatIsTooLargeOrNotNumbers() {
        assertThat(rootMessage(assertThrows(RuntimeException.class, () -> render("{{#lists.range.0.10000}}{{.}}{{/lists.range.0.10000}}"))),
            is("lists.range: a range has at most 10000 values, but 0 to 10000 has 10001"));
        assertThat(rootMessage(assertThrows(RuntimeException.class, () -> render("{{#lists.range.one.5}}{{.}}{{/lists.range.one.5}}"))),
            containsString("lists.range: from must be a whole number, but was \"one\""));
    }

    @Test
    public void shouldFormatTheRequestXml() {
        assertThat(render("{{#xpath.formatXml}}{{{ request.body }}}{{/xpath.formatXml}}"), is("<order>\n  <item>a</item>\n  <item>b</item>\n</order>"));
        assertThat(rootMessage(assertThrows(RuntimeException.class, () -> render("{{#xpath.formatXml}}{{ request.body }}{{/xpath.formatXml}}"))),
            containsString("triple braces"));
    }

    @Test
    public void shouldRenderTheHostname() {
        assertThat(render("{{ hostname }}"), is(TemplateFunctions.hostname()));
        assertThat(TemplateFunctions.hostname().isEmpty(), is(false));
    }

    @Test
    public void shouldGenerateFakeDataForALocale() {
        MustacheTemplateEngine seeded = new MustacheTemplateEngine(mockServerLogger, configuration().templateFakerSeed(7L));
        String expected = new TemplateFaker(7L).locale("de-DE").name().firstName();
        assertThat(seeded.renderTemplate("{{ faker.locale.de-DE.name.firstName }}", REQUEST), is(expected));
        assertThat(render("{{#faker.locale.fr}}{{ address.city }}{{/faker.locale.fr}}").isEmpty(), is(false));
        String unsupported = new TemplateFaker(7L).name().firstName();
        assertThat(new MustacheTemplateEngine(mockServerLogger, configuration().templateFakerSeed(7L)).renderTemplate("{{ faker.locale.xx.name.firstName }}", REQUEST), is(unsupported));
    }

    @Test
    public void shouldExposeTemplateParameters() {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("greeting", "hello");
        parameters.put("names", List.of("ann", "bob"));
        parameters.put("limits", Map.of("max", 5));
        String body = engine.executeTemplate(
            "{ 'statusCode': 200, 'body': '{{ parameters.greeting }} {{#parameters.names}}{{.}}{{^-last}} and {{/-last}}{{/parameters.names}} max {{ parameters.limits.max }}' }",
            parameters, REQUEST, null, HttpResponseDTO.class).getBodyAsString();
        assertThat(body, is("hello ann and bob max 5"));
    }

    @Test
    public void shouldRenderParametersAsEmptyWhenThereAreNone() {
        String template = "{ 'statusCode': 200, 'body': '[{{ parameters.greeting }}]' }";
        assertThat(engine.executeTemplate(template, null, REQUEST, null, HttpResponseDTO.class).getBodyAsString(), is("[]"));
        assertThat(engine.executeTemplate(template, Map.of(), REQUEST, null, HttpResponseDTO.class).getBodyAsString(), is("[]"));
        assertThat(engine.executeTemplate(template, REQUEST, HttpResponseDTO.class).getBodyAsString(), is("[]"));
    }

    @Test
    public void shouldLeaveTemplatesThatUseNoNewHelperUnchanged() {
        assertThat(render("{{ request.path }} {{#request.queryStringParameters.colour}}{{.}} {{/request.queryStringParameters.colour}}"), is("/colours teal amber "));
        assertThat(render("{{ lists }}"), not(is("")));
    }

    // the helper's own message, from the first IllegalArgumentException the engine wrapped
    static String rootMessage(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof IllegalArgumentException) {
                return cause.getMessage();
            }
        }
        return failure.getMessage();
    }
}
