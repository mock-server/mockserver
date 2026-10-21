package org.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.mockserver.file.TemplateFileContentCache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * @author jamesdbloom
 */
public class HttpTemplate extends Action<HttpTemplate> {
    private int hashCode;
    private final TemplateType templateType;
    private String template;
    private String templateFile;
    private Type actionType;
    private HttpResponse responseOverride;
    private HttpResponseModifier responseModifier;
    private Map<String, Object> parameters;

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

    /**
     * Load the template from a file (resolved from the classpath or the filesystem) instead of
     * providing it inline. The inline template, when present, always takes precedence over the file.
     *
     * @param templateFile path to the file containing the template
     */
    public HttpTemplate withTemplateFile(String templateFile) {
        this.templateFile = templateFile;
        this.hashCode = 0;
        return this;
    }

    public String getTemplateFile() {
        return templateFile;
    }

    /**
     * Resolves the template content used by the template engines: the inline template when set,
     * otherwise the contents of {@link #getTemplateFile()} read from the classpath or filesystem.
     * The file content is cached per canonical path and revalidated by a cheap metadata check, so an
     * edited template file still takes effect on the next request without re-reading it every time.
     */
    @JsonIgnore
    public String getTemplateContent() {
        if (isNotBlank(template)) {
            return template;
        }
        if (isNotBlank(templateFile)) {
            return TemplateFileContentCache.readFileFromClassPathOrPath(templateFile);
        }
        return template;
    }

    public HttpResponse getResponseOverride() {
        return responseOverride;
    }

    public HttpTemplate withResponseOverride(HttpResponse responseOverride) {
        this.responseOverride = responseOverride;
        this.hashCode = 0;
        return this;
    }

    public HttpResponseModifier getResponseModifier() {
        return responseModifier;
    }

    public HttpTemplate withResponseModifier(HttpResponseModifier responseModifier) {
        this.responseModifier = responseModifier;
        this.hashCode = 0;
        return this;
    }

    /**
     * Values the template reads as {@code parameters}, such as {@code $parameters.greeting} in Velocity,
     * {@code {{ parameters.greeting }}} in Mustache or {@code parameters.greeting} in JavaScript, so one template
     * can be reused with different values. A value can be text, a number, a boolean, a list or a map. The
     * values are copied, and templates cannot change them.
     *
     * @param parameters the parameters, replacing any set before
     */
    @SuppressWarnings("unchecked")
    public HttpTemplate withParameters(Map<String, Object> parameters) {
        this.parameters = parameters == null || parameters.isEmpty() ? null : (Map<String, Object>) readOnlyCopy(parameters);
        this.hashCode = 0;
        return this;
    }

    /**
     * Adds one parameter the template reads as {@code parameters.<name>}; see {@link #withParameters(Map)}.
     */
    public HttpTemplate withParameter(String name, Object value) {
        Map<String, Object> updated = parameters == null ? new LinkedHashMap<>() : new LinkedHashMap<>(parameters);
        updated.put(name, value);
        return withParameters(updated);
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    /**
     * A read-only copy at every depth, with arrays as lists, so a template can neither change an expectation's
     * parameters nor see them change while it renders.
     */
    private static Object readOnlyCopy(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, entry) -> copy.put(String.valueOf(key), readOnlyCopy(entry)));
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof Object[] array) {
            return readOnlyCopy(java.util.Arrays.asList(array));
        }
        if (value instanceof Collection<?> collection) {
            List<Object> copy = new ArrayList<>(collection.size());
            for (Object element : collection) {
                copy.add(readOnlyCopy(element));
            }
            return Collections.unmodifiableList(copy);
        }
        return value;
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
        VELOCITY,
        MUSTACHE
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
            Objects.equals(templateFile, that.templateFile) &&
            actionType == that.actionType &&
            Objects.equals(responseOverride, that.responseOverride) &&
            Objects.equals(responseModifier, that.responseModifier) &&
            Objects.equals(parameters, that.parameters);
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = Objects.hash(super.hashCode(), templateType, template, templateFile, actionType, responseOverride, responseModifier, parameters);
        }
        return hashCode;
    }
}

