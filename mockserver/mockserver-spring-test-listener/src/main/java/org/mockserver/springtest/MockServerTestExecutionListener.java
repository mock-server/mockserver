package org.mockserver.springtest;

import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class MockServerTestExecutionListener extends AbstractTestExecutionListener {

    private static final ConcurrentHashMap<Class<?>, List<Field>> MOCK_SERVER_FIELDS = new ConcurrentHashMap<>();

    @Override
    public void prepareTestInstance(TestContext testContext) throws Exception {
        if (isMockServerTest(testContext)) {
            ClientAndServer clientAndServer = MockServerPropertyCustomizer.getOrCreateClientAndServer();
            for (Field field : getMockServerFields(testContext.getTestClass())) {
                boolean accessible = field.isAccessible();
                field.setAccessible(true);
                try {
                    field.set(testContext.getTestInstance(), clientAndServer);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(
                        "Failed to inject MockServerClient into " + field.getDeclaringClass().getName() + "." + field.getName(), e);
                } finally {
                    field.setAccessible(accessible);
                }
            }
        }
    }

    private static List<Field> getMockServerFields(Class<?> testClass) {
        return MOCK_SERVER_FIELDS.computeIfAbsent(testClass, MockServerTestExecutionListener::findMockServerFields);
    }

    private static List<Field> findMockServerFields(Class<?> classToCheck) {
        if (classToCheck == null || Object.class.equals(classToCheck)) {
            return new ArrayList<>();
        }
        List<Field> fields = findMockServerFields(classToCheck.getSuperclass());
        if (fields.isEmpty()) {
            fields = findMockServerFields(classToCheck.getEnclosingClass());
        }
        for (Field field : classToCheck.getDeclaredFields()) {
            if (MockServerClient.class.equals(field.getType())) {
                fields.add(field);
            }
        }
        return fields;
    }

    @Override
    public void afterTestMethod(TestContext testContext) {
        if (isMockServerTest(testContext)) {
            MockServerPropertyCustomizer.getOrCreateClientAndServer().reset();
        }
    }

    private static boolean isMockServerTest(TestContext testContext) {
        Class<?> testClass = testContext.getTestClass();
        while (testClass != null && !Object.class.equals(testClass)) {
            if (AnnotatedElementUtils.findMergedAnnotation(testClass, MockServerTest.class) != null) {
                return true;
            }
            testClass = testClass.getEnclosingClass();
        }
        return false;
    }

}
