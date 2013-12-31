package org.mockserver.server;

import org.junit.Test;

import static org.junit.Assert.assertEquals;


/**
 * @author jamesdbloom
 */
public class MockServerRunnerTest {

    @Test
    public void shouldReturnClassName() {
        assertEquals(new MockServerRunner().getServlet().getClass(), MockServerServlet.class);
    }

}
