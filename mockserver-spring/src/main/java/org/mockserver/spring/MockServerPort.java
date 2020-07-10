package org.mockserver.spring;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.beans.factory.annotation.Value;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Value("${mockServerPort}")
public @interface MockServerPort {}
