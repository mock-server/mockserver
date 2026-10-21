package org.mockserver.templates.engine.model;

/**
 * Exposes the template object's package-private limits to engine tests in other packages.
 */
public final class HttpRequestTemplateObjectTestAccess {

    private HttpRequestTemplateObjectTestAccess() {
    }

    public static int maxParsedBodyBytes() {
        return HttpRequestTemplateObject.MAX_PARSED_BODY_BYTES;
    }
}
