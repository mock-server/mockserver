package org.mockserver.client.serialization.model;

import org.mockserver.model.HttpTemplate;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

/**
 * @author jamesdbloom
 */
public class HttpTemplateDTO extends ObjectWithReflectiveEqualsHashCodeToString {

    private String template;
    private HttpTemplate.TemplateType type;
    private DelayDTO delay;

    public HttpTemplateDTO(HttpTemplate httpTemplate) {
        if (httpTemplate != null) {
            template = httpTemplate.getTemplate();
            delay = (httpTemplate.getDelay() != null ? new DelayDTO(httpTemplate.getDelay()) : null);
        }
    }

    public HttpTemplateDTO() {
    }

    public HttpTemplate buildObject() {
        return new HttpTemplate(type)
                .withTemplate(template)
                .withDelay((delay != null ? delay.buildObject() : null));
    }

    public String getTemplate() {
        return template;
    }

    public HttpTemplateDTO setTemplate(String template) {
        this.template = template;
        return this;
    }

    public DelayDTO getDelay() {
        return delay;
    }

    public HttpTemplateDTO setDelay(DelayDTO delay) {
        this.delay = delay;
        return this;
    }

    public HttpTemplate.TemplateType getType() {
        return type;
    }

    public HttpTemplateDTO setType(HttpTemplate.TemplateType type) {
        this.type = type;
        return this;
    }
}

