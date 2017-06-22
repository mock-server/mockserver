package org.mockserver.integration.mockserver;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.hamcrest.Matchers.containsString;

/**
 * @author jamesdbloom
 */
public abstract class AbstractRestartableMockServerNettyIntegrationTest extends AbstractMockServerNettyIntegrationTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    public abstract void startServerAgain();

    @Test
    public void shouldThrowExceptionIfFailToBindToSocket() {
        // given
        System.out.println("\n\n--- IGNORE THE FOLLOWING java.net.BindException EXCEPTION ---\n\n");
        expectedException.expect(RuntimeException.class);
        expectedException.expectMessage(containsString("Exception while binding MockServer to port "));

        // when
        startServerAgain();
    }

}
