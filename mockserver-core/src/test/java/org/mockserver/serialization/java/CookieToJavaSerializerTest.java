package org.mockserver.serialization.java;

import org.junit.Test;
import org.mockserver.model.Cookie;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.mockserver.character.Character.NEW_LINE;

public class CookieToJavaSerializerTest {

    @Test
    public void shouldSerializeCookie() throws IOException {
        assertEquals(NEW_LINE +
                        "        new Cookie(\"requestCookieNameOne\", \"requestCookieValueOne\")",
                new CookieToJavaSerializer().serialize(1, new Cookie("requestCookieNameOne", "requestCookieValueOne"))
        );
    }

    @Test
    public void shouldSerializeMultipleCookies() throws IOException {
        assertEquals(NEW_LINE +
                        "        new Cookie(\"requestCookieNameOne\", \"requestCookieValueOne\")," +
                        NEW_LINE +
                        "        new Cookie(\"requestCookieNameTwo\", \"requestCookieValueTwo\")",
                new CookieToJavaSerializer().serializeAsJava(1, new Cookie("requestCookieNameOne", "requestCookieValueOne"), new Cookie("requestCookieNameTwo", "requestCookieValueTwo"))
        );
    }

    @Test
    public void shouldSerializeListOfCookies() throws IOException {
        assertEquals(NEW_LINE +
                        "        new Cookie(\"requestCookieNameOne\", \"requestCookieValueOne\")," +
                        NEW_LINE +
                        "        new Cookie(\"requestCookieNameTwo\", \"requestCookieValueTwo\")",
                new CookieToJavaSerializer().serializeAsJava(1, Arrays.asList(
                        new Cookie("requestCookieNameOne", "requestCookieValueOne"),
                        new Cookie("requestCookieNameTwo", "requestCookieValueTwo")
                ))
        );
    }

}