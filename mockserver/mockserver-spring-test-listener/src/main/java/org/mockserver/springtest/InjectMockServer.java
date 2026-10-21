package org.mockserver.springtest;

import java.lang.annotation.*;

/**
 * Injects the client for the {@link MockServerInstance} of the given name into a field typed
 * {@code MockServerClient} or {@code ClientAndServer}.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface InjectMockServer {
    /**
     * The {@link MockServerInstance#name()} to inject.
     */
    String value();
}
