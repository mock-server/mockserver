package org.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * @author jamesdbloom
 */
public class HttpTemplate extends Action<HttpTemplate> {
    private final TemplateType templateType;
    private String template;
    private Type actionType;

    public HttpTemplate(TemplateType type) {
        this.templateType = type;
    }

    /**
     * Static builder to create an template for responding or forwarding requests.
     */
    public static HttpTemplate template(TemplateType type) {
        return new HttpTemplate(type);
    }

    /**
     * Static builder to create an template for responding or forwarding requests.
     *
     * @param template the template for the response or request
     */
    public static HttpTemplate template(TemplateType type, String template) {
        return new HttpTemplate(type).withTemplate(template);
    }

    public TemplateType getTemplateType() {
        return templateType;
    }

    public HttpTemplate withTemplate(String template) {
        this.template = template;
        return this;
    }

    public String getTemplate() {
        return template;
    }

    public void setActionType(Type actionType) {
        this.actionType = actionType;
    }

    @Override
    @JsonIgnore
    public Type getType() {
        return actionType;
    }

    public HttpTemplate shallowClone() {
        return template(getTemplateType())
            .withTemplate(getTemplate())
            .withDelay(getDelay());
    }

    public enum TemplateType {
        JAVASCRIPT,
        VELOCITY
    }
}

