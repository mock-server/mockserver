package org.mockserver.springtest;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

public class MockServerPropertyCustomizerEqualityTest {

    @Test
    public void hashCodeShouldBeTheSameForNoParamsInConstructor() {
        MockServerPropertyCustomizer actual = new MockServerPropertyCustomizer();
        MockServerPropertyCustomizer expected = new MockServerPropertyCustomizer();

        assertThat(actual.hashCode(), is(expected.hashCode()));
    }

    @Test
    public void hashCodeShouldBeTheSameForClassWithTheSameParamsInConstructor() {
        MockServerPropertyCustomizer actual = new MockServerPropertyCustomizer("A", "B");
        MockServerPropertyCustomizer expected = new MockServerPropertyCustomizer("A", "B");

        assertThat(actual.hashCode(), is(expected.hashCode()));
    }

    @Test
    public void hashCodeShouldBeDifferentForDifferentParamsInConstructor() {
        MockServerPropertyCustomizer actual = new MockServerPropertyCustomizer("A", "B");
        MockServerPropertyCustomizer expected = new MockServerPropertyCustomizer("A", "C");

        assertThat(actual.hashCode(), is(not(expected.hashCode())));
    }

    @Test
    public void shouldBeEqualForObjectsWithoutParamsInConstructor() {
        MockServerPropertyCustomizer actual = new MockServerPropertyCustomizer();
        MockServerPropertyCustomizer p2 = new MockServerPropertyCustomizer();

        assertThat(actual, is(p2));
    }

    @Test
    public void shouldBeEqualForObjectsWithSameParamsInConstructor() {
        MockServerPropertyCustomizer actual = new MockServerPropertyCustomizer("A", "B");
        MockServerPropertyCustomizer expected = new MockServerPropertyCustomizer("A", "B");

        assertThat(actual, is(expected));
    }

    @Test
    public void shouldNotBeEqualForObjectsWithDifferentParamsInConstructor() {
        MockServerPropertyCustomizer actual = new MockServerPropertyCustomizer("A", "B");
        MockServerPropertyCustomizer expected = new MockServerPropertyCustomizer("A", "C");

        assertThat(actual, is(not(expected)));
    }
}
