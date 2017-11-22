package org.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.mockserver.model.Cookie.cookie;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class HttpTemplate extends Action {
    private String template;
    private Delay delay;

    /**
     * Static builder to create an template for responding or forwarding requests.
     */
    public static HttpTemplate template() {
        return new HttpTemplate();
    }

    /**
     * Static builder to create an template for responding or forwarding requests.
     *
     * @param template the template for the response or request
     */
    public static HttpTemplate template(String template) {
        return new HttpTemplate().withTemplate(template);
    }

    public HttpTemplate withTemplate(String template) {
        this.template = template;
        return this;
    }

    public String getTemplate() {
        return template;
    }

    /**
     * The delay before responding with this request as a Delay object, for example new Delay(TimeUnit.SECONDS, 3)
     *
     * @param delay a Delay object, for example new Delay(TimeUnit.SECONDS, 3)
     */
    public HttpTemplate withDelay(Delay delay) {
        this.delay = delay;
        return this;
    }

    /**
     * The delay before responding with this request as a Delay object, for example new Delay(TimeUnit.SECONDS, 3)
     *
     * @param timeUnit a the time unit, for example TimeUnit.SECONDS
     * @param value    a the number of time units to delay the response
     */
    public HttpTemplate withDelay(TimeUnit timeUnit, long value) {
        this.delay = new Delay(timeUnit, value);
        return this;
    }

    public Delay getDelay() {
        return delay;
    }

    @JsonIgnore
    public HttpTemplate applyDelay() {
        if (delay != null) {
            delay.applyDelay();
        }
        return this;
    }

    @Override
    @JsonIgnore
    public Type getType() {
        return Type.RESPONSE_TEMPLATE;
    }

    public HttpTemplate shallowClone() {
        return template()
                .withTemplate(getTemplate())
                .withDelay(getDelay());
    }
}

