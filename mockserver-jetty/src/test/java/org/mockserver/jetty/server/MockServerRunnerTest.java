package org.mockserver.jetty.server;

import org.junit.Assert;
import org.junit.Test;
import org.mockserver.server.MockServerServlet;

import static org.junit.Assert.assertEquals;


/**
 * @author jamesdbloom
 */
public class MockServerRunnerTest {

    @Test
    public void shouldReturnClassName() {
        Assert.assertEquals(new MockServerRunner().getServlet().getClass(), MockServerServlet.class);
    }

}
