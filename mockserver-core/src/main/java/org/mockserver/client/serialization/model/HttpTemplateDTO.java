package org.mockserver.client.serialization.model;

import org.mockserver.model.HttpTemplate;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

/**
 * @author jamesdbloom
 */
public class HttpTemplateDTO extends ObjectWithReflectiveEqualsHashCodeToString {

    private String template;
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
        return new HttpTemplate()
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
}

