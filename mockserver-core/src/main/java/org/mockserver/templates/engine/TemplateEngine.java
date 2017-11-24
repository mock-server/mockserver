package org.mockserver.templates.engine;

import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

/**
 * @author jamesdbloom
 */
public interface TemplateEngine {

    HttpResponse executeTemplate(String template, HttpRequest httpRequest);

}
