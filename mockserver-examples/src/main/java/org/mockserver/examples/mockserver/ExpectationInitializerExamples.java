package org.mockserver.examples.mockserver;

import org.mockserver.examples.mockserver.initializer.ExpectationInitializerExample;
import org.mockserver.integration.ClientAndServer;

public class ExpectationInitializerExamples {

    public void startWithInitializer() {
        System.setProperty("mockserver.initializationClass", ExpectationInitializerExample.class.getName());
        int mockServerPort = new ClientAndServer().getLocalPort();
        // send requests
    }
}
