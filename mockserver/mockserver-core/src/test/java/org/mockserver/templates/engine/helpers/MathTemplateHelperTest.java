package org.mockserver.templates.engine.helpers;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class MathTemplateHelperTest {

    private final MathTemplateHelper helper = new MathTemplateHelper();

    @Test
    public void shouldGenerateRandomIntInRange() {
        for (int i = 0; i < 100; i++) {
            int result = helper.randomInt(1, 10);
            assertThat(result, is(both(greaterThanOrEqualTo(1)).and(lessThanOrEqualTo(10))));
        }
    }

    @Test
    public void shouldGenerateRandomDouble() {
        double result = helper.randomDouble();
        assertThat(result, is(both(greaterThanOrEqualTo(0.0)).and(lessThan(1.0))));
    }

    @Test
    public void shouldGenerateRandomDoubleInRange() {
        for (int i = 0; i < 100; i++) {
            double result = helper.randomDouble(5.0, 10.0);
            assertThat(result, is(both(greaterThanOrEqualTo(5.0)).and(lessThan(10.0))));
        }
    }

    @Test
    public void shouldReturnAbsoluteValueOfInt() {
        assertThat(helper.abs(-5), is(5));
        assertThat(helper.abs(5), is(5));
    }

    @Test
    public void shouldReturnAbsoluteValueOfDouble() {
        assertThat(helper.abs(-5.5), is(5.5));
        assertThat(helper.abs(5.5), is(5.5));
    }

    @Test
    public void shouldReturnMinOfTwoInts() {
        assertThat(helper.min(3, 7), is(3));
        assertThat(helper.min(7, 3), is(3));
    }

    @Test
    public void shouldReturnMaxOfTwoInts() {
        assertThat(helper.max(3, 7), is(7));
        assertThat(helper.max(7, 3), is(7));
    }

    @Test
    public void shouldRoundToScale() {
        assertThat(helper.round(3.14159, 2), is(3.14));
        assertThat(helper.round(3.145, 2), is(3.15));
        assertThat(helper.round(3.0, 0), is(3.0));
    }

    @Test
    public void shouldFormatNumber() {
        assertThat(helper.format(1234.5, "#,##0.00"), is("1,234.50"));
        assertThat(helper.format(0.5, "0.0%"), is("50.0%"));
    }

    @Test
    public void shouldCeil() {
        assertThat(helper.ceil(3.1), is(4.0));
        assertThat(helper.ceil(3.0), is(3.0));
    }

    @Test
    public void shouldFloor() {
        assertThat(helper.floor(3.9), is(3.0));
        assertThat(helper.floor(3.0), is(3.0));
    }

    @Test
    public void shouldBeRegisteredInTemplateFunctions() {
        Object mathHelper = org.mockserver.templates.engine.TemplateFunctions.BUILT_IN_HELPERS.get("math");
        assertThat(mathHelper, is(notNullValue()));
        assertThat(mathHelper, instanceOf(MathTemplateHelper.class));
    }
}
