package org.mockserver.junit.jupiter;

import org.junit.jupiter.api.extension.*;
import org.junit.platform.commons.support.AnnotationSupport;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.scheduler.Scheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MockServerExtension implements ParameterResolver, BeforeAllCallback, AfterAllCallback {
    protected static ClientAndServer perTestSuiteClientAndServer;
    protected ClientAndServer customClientAndServer;
    protected ClientAndServer clientAndServer;
    protected boolean perTestSuite;

    public MockServerExtension() {

    }

    public MockServerExtension(ClientAndServer clientAndServer) {
        this.customClientAndServer = clientAndServer;
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return MockServerClient.class.isAssignableFrom(parameterContext.getParameter().getType());
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return clientAndServer;
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        List<Integer> ports = new ArrayList<>();
        Optional<MockServerSettings> mockServerSettingsOptional = retrieveAnnotationFromTestClass(context);
        if (mockServerSettingsOptional.isPresent()) {
            MockServerSettings mockServerSettings = mockServerSettingsOptional.get();
            perTestSuite = mockServerSettings.perTestSuite();
            for (int port : mockServerSettings.ports()) {
                ports.add(port);
            }
        }
        clientAndServer = instantiateClient(ports);
    }

    ClientAndServer instantiateClient(List<Integer> ports) {
        synchronized (MockServerExtension.class) {
            if (perTestSuite) {
                if (perTestSuiteClientAndServer == null) {
                    perTestSuiteClientAndServer = ClientAndServer.startClientAndServer(ports);
                    Runtime.getRuntime().addShutdownHook(new Scheduler.SchedulerThreadFactory("MockServer Test Extension ShutdownHook").newThread(() -> perTestSuiteClientAndServer.stop()));
                }
                return perTestSuiteClientAndServer;
            } else if (customClientAndServer != null) {
                return customClientAndServer;
            } else {
                return ClientAndServer.startClientAndServer(ports);
            }
        }
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        if (!perTestSuite && clientAndServer.isRunning()) {
            clientAndServer.stop();
        }
    }

    private Optional<MockServerSettings> retrieveAnnotationFromTestClass(final ExtensionContext context) {
        ExtensionContext currentContext = context;
        Optional<MockServerSettings> annotation;

        do {
            annotation = AnnotationSupport.findAnnotation(currentContext.getElement(), MockServerSettings.class);
            if (!currentContext.getParent().isPresent()) {
                break;
            }
            currentContext = currentContext.getParent().get();
        } while (!annotation.isPresent() && currentContext != context.getRoot());

        return annotation;
    }
}
