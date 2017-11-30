package org.mockserver.client.serialization.model;

import org.mockserver.model.HttpTemplate;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

/**
 * @author jamesdbloom
 */
public class HttpTemplateDTO extends ObjectWithReflectiveEqualsHashCodeToString implements DTO<HttpTemplate> {

    private String template;
    private HttpTemplate.TemplateType templateType;
    private DelayDTO delay;

    public HttpTemplateDTO(HttpTemplate httpTemplate) {
        if (httpTemplate != null) {
            templateType = httpTemplate.getTemplateType();
            template = httpTemplate.getTemplate();
            delay = (httpTemplate.getDelay() != null ? new DelayDTO(httpTemplate.getDelay()) : null);
        }
    }

    public HttpTemplateDTO() {
    }

    public HttpTemplate buildObject() {
        return new HttpTemplate(templateType)
                .withTemplate(template)
                .withDelay((delay != null ? delay.buildObject() : null));
    }

    public HttpTemplate.TemplateType getTemplateType() {
        return templateType;
    }

    public HttpTemplateDTO setTemplateType(HttpTemplate.TemplateType templateType) {
        this.templateType = templateType;
        return this;
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
}

