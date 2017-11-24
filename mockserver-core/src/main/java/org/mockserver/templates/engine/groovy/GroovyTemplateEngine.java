package org.mockserver.templates.engine.groovy;

import com.fasterxml.jackson.databind.ObjectMapper;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.client.serialization.model.HttpResponseDTO;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.templates.engine.TemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jamesdbloom
 */
public class GroovyTemplateEngine implements TemplateEngine {

    private final static ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
    private final static Logger logger = LoggerFactory.getLogger(GroovyTemplateEngine.class);

    public HttpResponse executeTemplate(String template, HttpRequest httpRequest) {
        try {
            Binding binding = new Binding();
            binding.setVariable("httpRequest", httpRequest);
            binding.setVariable("template", template);
            Object value = new GroovyShell(binding).evaluate("new groovy.text.SimpleTemplateEngine().createTemplate(template).make([\"request\":httpRequest])");
            return objectMapper.readValue(String.valueOf(value), HttpResponseDTO.class).buildObject();
        } catch (Exception e) {
            logger.error("Exception transforming template:\n\"" + template + "\" with request:\n" + httpRequest, e);
        }
        return null;
    }
}
