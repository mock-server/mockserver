package org.mockserver.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;


/**
 * @author jamesdbloom
 */
public class HttpStatusCodeTest {

    @Test
    public void shouldFindEnumForCode() {
        assertEquals(HttpStatusCode.FOUND_302, HttpStatusCode.code(302));
        assertEquals(HttpStatusCode.BAD_GATEWAY_502, HttpStatusCode.code(502));
        assertEquals(HttpStatusCode.INSUFFICIENT_STORAGE_507, HttpStatusCode.code(507));
        assertNull(HttpStatusCode.code(600));
    }

    @Test
    public void shouldReturnCorrectValues() {
        assertEquals(HttpStatusCode.FOUND_302.reasonPhrase(), "Moved Temporarily");
        assertEquals(HttpStatusCode.FOUND_302.code(), 302);
    }
}
