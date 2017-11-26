package org.mockserver.integration.mockserver;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.hamcrest.Matchers.containsString;
import static org.mockserver.character.Character.NEW_LINE;

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
        System.out.println(NEW_LINE + NEW_LINE + "--- IGNORE THE FOLLOWING java.net.BindException EXCEPTION ---" + NEW_LINE + NEW_LINE);
        expectedException.expect(RuntimeException.class);
        expectedException.expectMessage(containsString("Exception while binding MockServer to port "));

        // when
        startServerAgain();
    }

}
