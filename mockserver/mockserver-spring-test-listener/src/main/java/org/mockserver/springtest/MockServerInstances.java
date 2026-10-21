package org.mockserver.springtest;

import java.lang.annotation.*;

/**
 * Container for repeated {@link MockServerInstance} annotations.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface MockServerInstances {
    MockServerInstance[] value();
}
