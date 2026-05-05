package org.mockserver.springtest;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface MockServerTest {
    String[] value() default {};
}
