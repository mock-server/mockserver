package org.mockserver.junit.jupiter;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Configure MockServer when used in conjunction with {@link MockServerExtension}
 */
@ExtendWith(MockServerExtension.class)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface MockServerSettings {
    boolean perTestSuite() default false;
    int[] ports() default {};
}
