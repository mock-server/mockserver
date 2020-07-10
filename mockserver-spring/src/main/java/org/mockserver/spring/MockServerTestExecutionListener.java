package org.mockserver.spring;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

@SuppressWarnings({
        "squid:S1523" /*   Make sure that this dynamic injection or execution of code is safe.
                      - not an issue for test code */
})
public class MockServerTestExecutionListener extends AbstractTestExecutionListener {

    private static final ConcurrentHashMap<Class<?>, List<Field>> MOCK_SERVER_FIELDS = new ConcurrentHashMap<>();

    private static MockServerClient mockServerClient;

    @Override
    public void prepareTestInstance(TestContext testContext) throws Exception {
        if (isMockServerTest(testContext)) {
            List<Field> fields = getMockServerFields(testContext.getTestClass());
            if (!fields.isEmpty()) {
                initMockServerClientIfRequired(testContext);

            }
            for (Field field : fields) {
                setFieldValue(testContext, field);
            }
        }
    }

    private void setFieldValue(TestContext testContext, Field field) throws IllegalAccessException {
        boolean accessible = field.isAccessible();
        field.setAccessible(true);
        field.set(testContext.getTestInstance(), mockServerClient);
        field.setAccessible(accessible);
    }

    private static List<Field> getMockServerFields(Class<?> testClass) {
        return MOCK_SERVER_FIELDS.computeIfAbsent(testClass, MockServerTestExecutionListener::findMockServerFields);
    }

    private static List<Field> findMockServerFields(Class<?> classToCheck) {
        if (Object.class.equals(classToCheck)) {
            return new ArrayList<>();
        }
        // unfortunately we can't use the cached getMockServerFields as computeIfAbsent must not be used with recursion
        List<Field> fields = findMockServerFields(classToCheck.getSuperclass());
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
            mockServerClient.reset();
        }
    }

    private static void initMockServerClientIfRequired(TestContext testContext) {
        if (mockServerClient == null) {
            Environment environment = testContext.getApplicationContext().getEnvironment();
            int mockServerPort = Optional.ofNullable(environment.getProperty("mockServerPort"))
                    .map(Integer::valueOf)
                    .orElseThrow(()->new IllegalStateException("[mockServerPort] is not defined in context environment"));

            mockServerClient = ClientAndServer.startClientAndServer(mockServerPort);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> mockServerClient.stop()));
        }
    }

    private static boolean isMockServerTest(TestContext testContext) {
        return AnnotatedElementUtils.findMergedAnnotation(testContext.getTestClass(), MockServerTest.class) != null;
    }

}
