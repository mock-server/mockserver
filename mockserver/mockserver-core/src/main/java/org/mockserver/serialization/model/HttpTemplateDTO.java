package org.mockserver.serialization.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpResponseModifier;
import org.mockserver.model.HttpTemplate;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

/**
 * @author jamesdbloom
 */
public class HttpTemplateDTO extends ObjectWithReflectiveEqualsHashCodeToString implements DTO<HttpTemplate> {

    private String template;
    private HttpTemplate.TemplateType templateType;
    private DelayDTO delay;
    private HttpResponseDTO responseOverride;
    private HttpResponseModifierDTO responseModifier;
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean primary;

    public HttpTemplateDTO(HttpTemplate httpTemplate) {
        if (httpTemplate != null) {
            templateType = httpTemplate.getTemplateType();
            template = httpTemplate.getTemplate();
            delay = (httpTemplate.getDelay() != null ? new DelayDTO(httpTemplate.getDelay()) : null);
            if (httpTemplate.getResponseOverride() != null) {
                responseOverride = new HttpResponseDTO(httpTemplate.getResponseOverride());
            }
            if (httpTemplate.getResponseModifier() != null) {
                responseModifier = new HttpResponseModifierDTO(httpTemplate.getResponseModifier());
            }
            primary = httpTemplate.isPrimary();
        }
    }

    public HttpTemplateDTO() {
    }

    public HttpTemplate buildObject() {
        HttpTemplate result = new HttpTemplate(templateType)
            .withTemplate(template)
            .withDelay((delay != null ? delay.buildObject() : null))
            .withPrimary(primary);
        if (responseOverride != null) {
            result.withResponseOverride(responseOverride.buildObject());
        }
        if (responseModifier != null) {
            result.withResponseModifier(responseModifier.buildObject());
        }
        return result;
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

    public HttpResponseDTO getResponseOverride() {
        return responseOverride;
    }

    public HttpTemplateDTO setResponseOverride(HttpResponseDTO responseOverride) {
        this.responseOverride = responseOverride;
        return this;
    }

    public HttpResponseModifierDTO getResponseModifier() {
        return responseModifier;
    }

    public HttpTemplateDTO setResponseModifier(HttpResponseModifierDTO responseModifier) {
        this.responseModifier = responseModifier;
        return this;
    }

    public boolean isPrimary() {
        return primary;
    }

    public HttpTemplateDTO setPrimary(boolean primary) {
        this.primary = primary;
        return this;
    }
}

