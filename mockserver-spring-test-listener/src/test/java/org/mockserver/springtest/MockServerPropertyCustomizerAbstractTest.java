package org.mockserver.springtest;

import org.springframework.beans.factory.annotation.Value;

@MockServerTest("server.url.path-a=http://localhost:${mockServerPort}/a")
abstract class MockServerPropertyCustomizerAbstractTest {

    @Value("${server.url.path-a}")
    protected String serverUrlPathA;
}
