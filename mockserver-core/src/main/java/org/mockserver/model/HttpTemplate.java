package org.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Objects;

/**
 * @author jamesdbloom
 */
public class HttpTemplate extends Action<HttpTemplate> {
    private int hashCode;
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
        this.hashCode = 0;
        return this;
    }

    public String getTemplate() {
        return template;
    }

    public void withActionType(Type actionType) {
        this.actionType = actionType;
        this.hashCode = 0;
    }

    @Override
    @JsonIgnore
    public Type getType() {
        return actionType;
    }

    public enum TemplateType {
        JAVASCRIPT,
        VELOCITY
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (hashCode() != o.hashCode()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        HttpTemplate that = (HttpTemplate) o;
        return templateType == that.templateType &&
            Objects.equals(template, that.template) &&
            actionType == that.actionType;
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = Objects.hash(super.hashCode(), templateType, template, actionType);
        }
        return hashCode;
    }
}

