package org.mockserver.junit.jupiter;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.*;
import org.junit.platform.commons.support.AnnotationSupport;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.socket.PortFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class MockServerExtension implements ParameterResolver, BeforeAllCallback, AfterAllCallback {
    private ClientAndServer client;
    private boolean perTestSuite;

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getParameter().getType().equals(ClientAndServer.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return client;
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        List<Integer> ports = new ArrayList<>();
        Optional<MockServerSettings> mockServerSettingsOptional = retrieveAnnotationFromTestClass(context);
        if (mockServerSettingsOptional.isPresent()) {
            MockServerSettings mockServerSettings = mockServerSettingsOptional.get();
            perTestSuite = mockServerSettings.perTestSuite();
            int[] portsSettings = mockServerSettings.ports();
            if (portsSettings.length > 0) {
                for (int port : portsSettings) {
                    ports.add(port);
                }
            }
        }
        if (ports.isEmpty()) {
            ports.add(PortFactory.findFreePort());
        }

        this.client = ClientAndServer.startClientAndServer(ports.toArray(new Integer[0]));
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {
        if (client.isRunning()) {
            client.stop();
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
