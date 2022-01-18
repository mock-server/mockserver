package org.mockserver.springtest;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

public class MockServerPropertyCustomizerEqualityTest {

    @Test
    public void hashCodeShouldBeTheSameForNoParamsInConstructor() {
        MockServerPropertyCustomizer actual = new MockServerPropertyCustomizer(Collections.emptyList());
        MockServerPropertyCustomizer expected = new MockServerPropertyCustomizer(Collections.emptyList());

        assertThat(actual.hashCode(), is(expected.hashCode()));
    }

    @Test
    public void hashCodeShouldBeTheSameForClassWithTheSameParamsInConstructor() {
        MockServerPropertyCustomizer actual = new MockServerPropertyCustomizer(Arrays.asList("A", "B"));
        MockServerPropertyCustomizer expected = new MockServerPropertyCustomizer(Arrays.asList("A", "B"));

        assertThat(actual.hashCode(), is(expected.hashCode()));
    }

    @Test
    public void hashCodeShouldBeDifferentForDifferentParamsInConstructor() {
        MockServerPropertyCustomizer actual = new MockServerPropertyCustomizer(Arrays.asList("A", "B"));
        MockServerPropertyCustomizer expected = new MockServerPropertyCustomizer(Arrays.asList("A", "C"));

        assertThat(actual.hashCode(), is(not(expected.hashCode())));
    }

    @Test
    public void shouldBeEqualForObjectsWithoutParamsInConstructor() {
        MockServerPropertyCustomizer actual = new MockServerPropertyCustomizer(Collections.emptyList());
        MockServerPropertyCustomizer p2 = new MockServerPropertyCustomizer(Collections.emptyList());

        assertThat(actual, is(p2));
    }

    @Test
    public void shouldBeEqualForObjectsWithSameParamsInConstructor() {
        MockServerPropertyCustomizer actual = new MockServerPropertyCustomizer(Arrays.asList("A", "B"));
        MockServerPropertyCustomizer expected = new MockServerPropertyCustomizer(Arrays.asList("A", "B"));

        assertThat(actual, is(expected));
    }

    @Test
    public void shouldNotBeEqualForObjectsWithDifferentParamsInConstructor() {
        MockServerPropertyCustomizer actual = new MockServerPropertyCustomizer(Arrays.asList("A", "B"));
        MockServerPropertyCustomizer expected = new MockServerPropertyCustomizer(Arrays.asList("A", "C"));

        assertThat(actual, is(not(expected)));
    }
}
