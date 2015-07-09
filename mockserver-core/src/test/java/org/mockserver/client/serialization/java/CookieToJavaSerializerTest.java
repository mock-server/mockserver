package org.mockserver.client.serialization.java;

import org.junit.Test;
import org.mockserver.model.Cookie;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class CookieToJavaSerializerTest {

    @Test
    public void shouldSerializeCookie() throws IOException {
        assertEquals(System.getProperty("line.separator") +
                        "        new Cookie(\"requestCookieNameOne\", \"requestCookieValueOne\")",
                new CookieToJavaSerializer().serializeAsJava(1, new Cookie("requestCookieNameOne", "requestCookieValueOne"))
        );
    }

    @Test
    public void shouldSerializeMultipleCookies() throws IOException {
        assertEquals(System.getProperty("line.separator") +
                        "        new Cookie(\"requestCookieNameOne\", \"requestCookieValueOne\")," +
                        System.getProperty("line.separator") +
                        "        new Cookie(\"requestCookieNameTwo\", \"requestCookieValueTwo\")",
                new CookieToJavaSerializer().serializeAsJava(1, new Cookie("requestCookieNameOne", "requestCookieValueOne"), new Cookie("requestCookieNameTwo", "requestCookieValueTwo"))
        );
    }

    @Test
    public void shouldSerializeListOfCookies() throws IOException {
        assertEquals(System.getProperty("line.separator") +
                        "        new Cookie(\"requestCookieNameOne\", \"requestCookieValueOne\")," +
                        System.getProperty("line.separator") +
                        "        new Cookie(\"requestCookieNameTwo\", \"requestCookieValueTwo\")",
                new CookieToJavaSerializer().serializeAsJava(1, Arrays.asList(
                        new Cookie("requestCookieNameOne", "requestCookieValueOne"),
                        new Cookie("requestCookieNameTwo", "requestCookieValueTwo")
                ))
        );
    }

}