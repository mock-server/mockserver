package org.mockserver.templates.engine;

import org.mockserver.client.serialization.model.DTO;
import org.mockserver.model.HttpRequest;

/**
 * @author jamesdbloom
 */
public interface TemplateEngine {

    <T> T executeTemplate(String template, HttpRequest httpRequest, Class<? extends DTO<T>> dtoClass);

}
