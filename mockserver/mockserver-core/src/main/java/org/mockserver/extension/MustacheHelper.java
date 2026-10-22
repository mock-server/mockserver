package org.mockserver.extension;

/**
 * A Mustache section helper from a {@link TemplateHelperProvider}. MockServer renders the section's content first,
 * on the thread rendering the template, then calls {@link #apply(String)} on a helper thread; the returned text
 * replaces the section only if the helper returns in time, so a helper that times out never writes to the response.
 */
@FunctionalInterface
public interface MustacheHelper {

    /**
     * @param renderedSection the section's content, already rendered against the request
     * @return the text that replaces the section; {@code null} renders nothing
     * @throws Exception to fail the template
     */
    String apply(String renderedSection) throws Exception;
}
