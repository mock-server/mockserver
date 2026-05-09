package org.mockserver.templates.engine;

import org.mockserver.serialization.model.DTO;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

/**
 * @author jamesdbloom
 */
public interface TemplateEngine {

    <T> T executeTemplate(String template, HttpRequest httpRequest, Class<? extends DTO<T>> dtoClass);

    <T> T executeTemplate(String template, HttpRequest httpRequest, HttpResponse httpResponse, Class<? extends DTO<T>> dtoClass);

}
