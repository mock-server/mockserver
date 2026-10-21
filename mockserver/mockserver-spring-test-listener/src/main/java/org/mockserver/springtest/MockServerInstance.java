package org.mockserver.springtest;

import java.lang.annotation.*;

/**
 * Starts a named MockServer for a Spring test class. Repeat it to start several, each on its own free port.
 *
 * <p>Each instance sets {@code mockserver.<name>.port} and {@code mockserver.<name>.baseUrl}
 * ({@code http://localhost:<port>}) in the Spring {@code Environment}, plus every name listed in
 * {@link #portProperties()} and {@link #baseUrlProperties()}. A {@code MockServerClient} field annotated
 * with {@link InjectMockServer} receives the client for the instance of that name.</p>
 *
 * <p>Each instance is reset after every test method and stops when the last Spring context using it closes.
 * Spring caches test contexts, so that is usually when the JVM exits, unless a context is evicted from the cache
 * or marked {@code @DirtiesContext}. Test classes that declare an instance with the same name and settings share
 * the server and the context, so do not run them in parallel in one JVM, and do not stop an injected instance.</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Repeatable(MockServerInstances.class)
public @interface MockServerInstance {

    /**
     * The instance name: letters, digits, {@code -} and {@code _} only. Unique within a test class.
     */
    String name();

    /**
     * Extra Spring property names that receive this instance's port, e.g. {@code payment.port}.
     */
    String[] portProperties() default {};

    /**
     * Extra Spring property names that receive this instance's base URL, e.g. {@code payment.url}.
     */
    String[] baseUrlProperties() default {};

    /**
     * Expectations to load when this instance starts, and to restore after each reset: a JSON file, a classpath
     * resource, a glob such as {@code src/test/resources/payment/*.json}, or a file-system directory (every
     * {@code *.json} file directly in it).
     */
    String initializationJsonPath() default "";

    /**
     * MockServer configuration for this instance only, each {@code mockserver.<property>=<value>}, using the
     * same properties as {@link MockServerTest}.
     */
    String[] properties() default {};
}
