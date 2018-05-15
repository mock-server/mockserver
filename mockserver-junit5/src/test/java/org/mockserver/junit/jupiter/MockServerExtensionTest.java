package org.mockserver.junit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.integration.ClientAndServer;

@ExtendWith(MockServerExtension.class)
class MockServerExtensionTest {
    private ClientAndServer client;

    public MockServerExtensionTest(ClientAndServer client) {
        this.client = client;
    }

    @Test
    public void test1() {
        System.out.println("test1 running with " + client.getPort());
        System.out.println("test1 done");
    }

    @Test
    public void test2() {
        System.out.println("test2 running with " + client.getPort());
        System.out.println("test2 done");
    }
}