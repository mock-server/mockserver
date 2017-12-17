package org.mockserver.examples.mockserver;

import org.mockserver.client.server.MockServerClient;

import java.util.Random;

import static org.mockserver.model.HttpError.error;
import static org.mockserver.model.HttpRequest.request;

/**
 * @author jamesdbloom
 */
public class ErrorActionExamples {

    public void randomBytesError() {
        // generate random bytes
        byte[] randomByteArray = new byte[25];
        new Random().nextBytes(randomByteArray);

        new MockServerClient("localhost", 1080)
            .when(
                request()
                    .withPath("/some/path")
            )
            .error(
                error()
                    .withDropConnection(true)
                    .withResponseBytes(randomByteArray)
            );
    }

    public void dropConnectionError() {
        new MockServerClient("localhost", 1080)
            .when(
                request()
                    .withPath("/some/path")
            )
            .error(
                error()
                    .withDropConnection(true)
            );
    }
}
