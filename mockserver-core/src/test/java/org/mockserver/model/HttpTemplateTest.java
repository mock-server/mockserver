package org.mockserver.model;

import junit.framework.TestCase;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpTemplate.TemplateType.JAVASCRIPT;
import static org.mockserver.model.HttpTemplate.TemplateType.VELOCITY;
import static org.mockserver.model.HttpTemplate.template;

/**
 * @author jamesdbloom
 */
public class HttpTemplateTest {

    @Test
    @SuppressWarnings("AccessStaticViaInstance")
    public void shouldAlwaysCreateNewObject() {
        assertEquals(template(JAVASCRIPT), template(JAVASCRIPT));
        assertEquals(template(VELOCITY), template(VELOCITY));
        assertNotSame(template(JAVASCRIPT), template(JAVASCRIPT));
        assertNotSame(template(VELOCITY), template(VELOCITY));
    }

    @Test
    public void returnsTemplate() {
        assertEquals("some_template", new HttpTemplate(JAVASCRIPT).withTemplate("some_template").getTemplate());
    }

    @Test
    public void returnsTemplateType() {
        assertEquals(JAVASCRIPT, new HttpTemplate(JAVASCRIPT).getTemplateType());
    }

    @Test
    public void returnsDelay() {
        assertEquals(new Delay(TimeUnit.HOURS, 1), new HttpForward().withDelay(new Delay(TimeUnit.HOURS, 1)).getDelay());
        assertEquals(new Delay(TimeUnit.HOURS, 1), new HttpForward().withDelay(TimeUnit.HOURS, 1).getDelay());
    }

    @Test
    public void shouldReturnFormattedRequestInToString() {
        TestCase.assertEquals("{" + NEW_LINE +
                "  \"delay\" : {" + NEW_LINE +
                "    \"timeUnit\" : \"HOURS\"," + NEW_LINE +
                "    \"value\" : 1" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"templateType\" : \"JAVASCRIPT\"," + NEW_LINE +
                "  \"template\" : \"some_template\"" + NEW_LINE +
                "}",
            template(JAVASCRIPT)
                .withTemplate("some_template")
                .withDelay(TimeUnit.HOURS, 1)
                .toString()
        );
    }

}
