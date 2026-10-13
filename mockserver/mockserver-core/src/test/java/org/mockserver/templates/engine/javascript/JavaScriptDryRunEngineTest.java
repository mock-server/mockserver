package org.mockserver.templates.engine.javascript;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.serialization.model.HttpResponseDTO;

import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;

/**
 * The dry-run JavaScript engine renders like a serving engine but keeps its one-off scripts out of the parsed-source
 * cache that serving engines share, so dry runs cannot fill it.
 */
public class JavaScriptDryRunEngineTest {

    private static final MockServerLogger LOGGER = new MockServerLogger(JavaScriptDryRunEngineTest.class);

    private static String uniqueTemplate() {
        return "return { statusCode: 200, body: '" + UUID.randomUUID() + "' };";
    }

    @Test
    public void dryRunEngineDoesNotCacheItsScripts() {
        assertThat(JavaScriptTemplateEngine.isPolyglotAvailable(), is(true));
        String template = uniqueTemplate();

        JavaScriptTemplateEngine.forDryRun(LOGGER, configuration(), 5_000L).executeTemplate(template, request(), HttpResponseDTO.class);

        assertThat(PolyglotRunner.sourceCacheContains(template), is(false));
    }

    @Test
    public void servingEngineCachesItsScripts() {
        assertThat(JavaScriptTemplateEngine.isPolyglotAvailable(), is(true));
        String template = uniqueTemplate();

        new JavaScriptTemplateEngine(LOGGER, configuration()).executeTemplate(template, request(), HttpResponseDTO.class);

        // the cache is shared and bounded, so a full cache can legitimately hold nothing new
        assertThat(PolyglotRunner.sourceCacheContains(template) || PolyglotRunner.sourceCacheIsFull(), is(true));
    }
}
