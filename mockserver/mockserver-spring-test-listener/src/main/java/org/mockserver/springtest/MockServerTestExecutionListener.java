package org.mockserver.springtest;

import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class MockServerTestExecutionListener extends AbstractTestExecutionListener {

    private static final ConcurrentHashMap<Class<?>, List<Field>> MOCK_SERVER_FIELDS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, List<Field>> NAMED_MOCK_SERVER_FIELDS = new ConcurrentHashMap<>();

    @Override
    public void prepareTestInstance(TestContext testContext) throws Exception {
        if (isMockServerTest(testContext)) {
            ClientAndServer clientAndServer = MockServerPropertyCustomizer.getOrCreateClientAndServer();
            injectMockServerClient(testContext.getTestInstance(), clientAndServer);
        }
        injectNamedMockServerClients(testContext.getTestClass(), testContext.getTestInstance(), () -> testContext.getApplicationContext().getEnvironment());
    }

    static void injectNamedMockServerClients(Class<?> testClass, Object testInstance, Supplier<Environment> environment) {
        List<Field> fields = NAMED_MOCK_SERVER_FIELDS.computeIfAbsent(testInstance.getClass(), MockServerTestExecutionListener::findNamedMockServerFields);
        if (fields.isEmpty()) {
            return;
        }
        Map<String, NamedMockServers.Definition> definitions = new HashMap<>();
        NamedMockServers.definitionsFor(testClass).forEach(definition -> definitions.put(definition.name, definition));
        for (Field field : fields) {
            String name = field.getAnnotation(InjectMockServer.class).value();
            NamedMockServers.Definition definition = definitions.get(name);
            if (definition == null) {
                throw new IllegalStateException("@InjectMockServer(\"" + name + "\") on " + field.getDeclaringClass().getName() + "." + field.getName() + " has no matching @MockServerInstance(name = \"" + name + "\") on " + testClass.getName());
            }
            setField(testInstance, field, runningServerFor(definition, environment.get()));
        }
    }

    private static ClientAndServer runningServerFor(NamedMockServers.Definition definition, Environment environment) {
        ClientAndServer server = NamedMockServers.running(definition);
        Integer contextPort = environment.getProperty(definition.portProperty(), Integer.class);
        if (server == null || !server.getPort().equals(contextPort)) {
            throw new IllegalStateException("MockServer instance \"" + definition.name + "\" is not running on port " + contextPort
                + ", which its Spring context uses; it was stopped while that context was still cached."
                + " Do not stop an injected instance, or mark the test class @DirtiesContext");
        }
        return server;
    }

    static void injectMockServerClient(Object testInstance, MockServerClient clientAndServer) {
        for (Field field : getMockServerFields(testInstance.getClass())) {
            // A field discovered on an enclosing class (see findMockServerFields) belongs to the
            // outer instance, not to a @Nested inner test instance, so it must be set on the
            // enclosing instance that actually declares it - otherwise Field.set throws
            // IllegalArgumentException (issue #2371). Walk out via the synthetic enclosing-instance
            // reference until we reach an instance the field can be assigned to.
            setField(testInstance, field, clientAndServer);
        }
    }

    private static void setField(Object testInstance, Field field, MockServerClient client) {
        Object target = resolveTarget(testInstance, field);
        if (target == null) {
            // the declaring instance is not reachable from this test instance; nothing to inject here
            return;
        }
        boolean accessible = field.isAccessible();
        field.setAccessible(true);
        try {
            field.set(target, client);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(
                "Failed to inject MockServerClient into " + field.getDeclaringClass().getName() + "." + field.getName(), e);
        } finally {
            field.setAccessible(accessible);
        }
    }

    private static Object resolveTarget(Object testInstance, Field field) {
        Class<?> declaringClass = field.getDeclaringClass();
        Object candidate = testInstance;
        while (candidate != null && !declaringClass.isInstance(candidate)) {
            candidate = enclosingInstance(candidate);
        }
        return candidate;
    }

    private static Object enclosingInstance(Object instance) {
        for (Field field : instance.getClass().getDeclaredFields()) {
            if (field.isSynthetic() && field.getName().startsWith("this$")) {
                boolean accessible = field.isAccessible();
                field.setAccessible(true);
                try {
                    return field.get(instance);
                } catch (IllegalAccessException e) {
                    return null;
                } finally {
                    field.setAccessible(accessible);
                }
            }
        }
        return null;
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
            if (MockServerClient.class.equals(field.getType()) && !field.isAnnotationPresent(InjectMockServer.class)) {
                fields.add(field);
            }
        }
        return fields;
    }

    private static List<Field> findNamedMockServerFields(Class<?> classToCheck) {
        if (classToCheck == null || Object.class.equals(classToCheck)) {
            return new ArrayList<>();
        }
        List<Field> fields = findNamedMockServerFields(classToCheck.getSuperclass());
        if (!Modifier.isStatic(classToCheck.getModifiers())) {
            fields.addAll(findNamedMockServerFields(classToCheck.getEnclosingClass()));
        }
        for (Field field : classToCheck.getDeclaredFields()) {
            if (field.isAnnotationPresent(InjectMockServer.class)) {
                if (!MockServerClient.class.isAssignableFrom(field.getType()) || !field.getType().isAssignableFrom(ClientAndServer.class)) {
                    throw new IllegalStateException("@InjectMockServer field " + classToCheck.getName() + "." + field.getName() + " must be a MockServerClient or ClientAndServer");
                }
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
        NamedMockServers.definitionsFor(testContext.getTestClass()).forEach(NamedMockServers::resetIfRunning);
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
