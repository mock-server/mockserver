package org.mockserver.matchers;

import org.junit.Test;
import org.mockserver.model.Header;
import org.mockserver.model.KeyToMultiValue;
import org.mockserver.model.Header;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockserver.matchers.NotMatcher.not;

/**
 * @author jamesdbloom
 */
public class HeaderMatcherTest {

    @Test
    public void shouldMatchMatchingString() {
        assertTrue(new MultiValueMapMatcher(KeyToMultiValue.toMultiMap(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("headerTwoName", "headerTwoValue")
        )).matches(new ArrayList<KeyToMultiValue>(Arrays.asList(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("headerTwoName", "headerTwoValue")
        ))));
    }

    @Test
    public void shouldNotMatchMatchingStringWhenNotApplied() {
        assertFalse(not(new MultiValueMapMatcher(KeyToMultiValue.toMultiMap(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("headerTwoName", "headerTwoValue")
        ))).matches(new ArrayList<KeyToMultiValue>(Arrays.asList(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("headerTwoName", "headerTwoValue")
        ))));
    }

    @Test
    public void shouldMatchMatchingStringWithNotHeaderAndNormalHeader() {
        assertTrue(new MultiValueMapMatcher(KeyToMultiValue.toMultiMap(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                org.mockserver.model.Not.not(new Header("headerThree", "headerThreeValueOne"))
        )).matches(new ArrayList<KeyToMultiValue>(Arrays.asList(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("headerTwoName", "headerTwoValue")
        ))));
    }

    @Test
    public void shouldMatchMatchingStringWithOnlyHeader() {
        assertTrue(new MultiValueMapMatcher(KeyToMultiValue.toMultiMap(
                org.mockserver.model.Not.not(new Header("headerThree", "headerThreeValueOne"))
        )).matches(new ArrayList<KeyToMultiValue>(Arrays.asList(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("headerTwoName", "headerTwoValue")
        ))));
    }

    @Test
    public void shouldMatchMatchingStringWithOnlyHeaderForEmptyList() {
        assertTrue(new MultiValueMapMatcher(KeyToMultiValue.toMultiMap(
                org.mockserver.model.Not.not(new Header("headerThree", "headerThreeValueOne"))
        )).matches(new ArrayList<KeyToMultiValue>()));
    }

    @Test
    public void shouldNotMatchMatchingStringWithNotHeaderAndNormalHeader() {
        assertFalse(new MultiValueMapMatcher(KeyToMultiValue.toMultiMap(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                org.mockserver.model.Not.not(new Header("headerTwoName", "headerTwoValue")
                ))).matches(new ArrayList<KeyToMultiValue>(Arrays.asList(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("headerTwoName", "headerTwoValue")
        ))));
    }

    @Test
    public void shouldNotMatchMatchingStringWithOnlyNotHeader() {
        assertFalse(new MultiValueMapMatcher(KeyToMultiValue.toMultiMap(
                org.mockserver.model.Not.not(new Header("headerTwoName", "headerTwoValue")
                ))).matches(new ArrayList<KeyToMultiValue>(Arrays.asList(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("headerTwoName", "headerTwoValue")
        ))));
    }

    @Test
    public void shouldNotMatchMatchingStringWithOnlyNotHeaderForBodyWithSingleHeader() {
        assertFalse(new MultiValueMapMatcher(KeyToMultiValue.toMultiMap(
                org.mockserver.model.Not.not(new Header("headerTwoName", "headerTwoValue")
                ))).matches(new ArrayList<KeyToMultiValue>(Arrays.asList(
                new Header("headerTwoName", "headerTwoValue")
        ))));
    }

    @Test
    public void shouldMatchNullExpectation() {
        assertTrue(new MultiValueMapMatcher(null).matches(new ArrayList<KeyToMultiValue>(Arrays.asList(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("headerTwoName", "headerTwoValue")
        ))));
    }

    @Test
    public void shouldNotMatchNullExpectationWhenNotApplied() {
        assertFalse(not(new MultiValueMapMatcher(null)).matches(new ArrayList<KeyToMultiValue>(Arrays.asList(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("headerTwoName", "headerTwoValue")
        ))));
    }

    @Test
    public void shouldMatchEmptyExpectation() {
        assertTrue(new MultiValueMapMatcher(KeyToMultiValue.toMultiMap()).matches(new ArrayList<KeyToMultiValue>(Arrays.asList(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("headerTwoName", "headerTwoValue")
        ))));
    }

    @Test
    public void shouldNotMatchEmptyExpectationWhenNotApplied() {
        assertFalse(not(new MultiValueMapMatcher(KeyToMultiValue.toMultiMap())).matches(new ArrayList<KeyToMultiValue>(Arrays.asList(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("headerTwoName", "headerTwoValue")
        ))));
    }

    @Test
    public void shouldNotMatchIncorrectHeaderName() {
        assertFalse(new MultiValueMapMatcher(KeyToMultiValue.toMultiMap(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("headerTwoName", "headerTwoValue")
        )).matches(new ArrayList<KeyToMultiValue>(Arrays.asList(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("INCORRECTheaderTwoName", "headerTwoValue")
        ))));
    }

    @Test
    public void shouldMatchIncorrectHeaderNameWhenNotApplied() {
        assertTrue(not(new MultiValueMapMatcher(KeyToMultiValue.toMultiMap(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("headerTwoName", "headerTwoValue")
        ))).matches(new ArrayList<KeyToMultiValue>(Arrays.asList(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("INCORRECTheaderTwoName", "headerTwoValue")
        ))));
    }

    @Test
    public void shouldNotMatchIncorrectHeaderValue() {
        assertFalse(new MultiValueMapMatcher(KeyToMultiValue.toMultiMap(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("headerTwoName", "headerTwoValue")
        )).matches(new ArrayList<KeyToMultiValue>(Arrays.asList(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("headerTwoName", "INCORRECTheaderTwoValue")
        ))));
    }

    @Test
    public void shouldMatchIncorrectHeaderValueWhenNotApplied() {
        assertTrue(not(new MultiValueMapMatcher(KeyToMultiValue.toMultiMap(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("headerTwoName", "headerTwoValue")
        ))).matches(new ArrayList<KeyToMultiValue>(Arrays.asList(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("headerTwoName", "INCORRECTheaderTwoValue")
        ))));
    }

    @Test
    public void shouldNotMatchIncorrectHeaderNameAndValue() {
        assertFalse(new MultiValueMapMatcher(KeyToMultiValue.toMultiMap(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("headerTwoName", "headerTwoValue")
        )).matches(new ArrayList<KeyToMultiValue>(Arrays.asList(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("INCORRECTheaderTwoName", "INCORRECTheaderTwoValue")
        ))));
    }

    @Test
    public void shouldMatchIncorrectHeaderNameAndValueWhenNotApplied() {
        assertTrue(not(new MultiValueMapMatcher(KeyToMultiValue.toMultiMap(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("headerTwoName", "headerTwoValue")
        ))).matches(new ArrayList<KeyToMultiValue>(Arrays.asList(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("INCORRECTheaderTwoName", "INCORRECTheaderTwoValue")
        ))));
    }

    @Test
    public void shouldNotMatchNullHeaderValue() {
        assertFalse(new MultiValueMapMatcher(KeyToMultiValue.toMultiMap(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("headerTwoName", "headerTwoValue")
        )).matches(new ArrayList<KeyToMultiValue>(Arrays.asList(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("headerTwoName")
        ))));
    }

    @Test
    public void shouldMatchNullHeaderValueWhenNotApplied() {
        assertTrue(not(new MultiValueMapMatcher(KeyToMultiValue.toMultiMap(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("headerTwoName", "headerTwoValue")
        ))).matches(new ArrayList<KeyToMultiValue>(Arrays.asList(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("headerTwoName")
        ))));
    }

    @Test
    public void shouldMatchNullHeaderValueInExpectation() {
        assertTrue(new MultiValueMapMatcher(KeyToMultiValue.toMultiMap(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("headerTwoName", "")
        )).matches(new ArrayList<KeyToMultiValue>(Arrays.asList(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("headerTwoName", "headerTwoValue")
        ))));
    }

    @Test
    public void shouldNotMatchMissingHeader() {
        assertFalse(new MultiValueMapMatcher(KeyToMultiValue.toMultiMap(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("headerTwoName", "headerTwoValue")
        )).matches(new ArrayList<KeyToMultiValue>(Arrays.asList(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo")
        ))));
    }

    @Test
    public void shouldMatchMissingHeaderWhenNotApplied() {
        assertTrue(not(new MultiValueMapMatcher(KeyToMultiValue.toMultiMap(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("headerTwoName", "headerTwoValue")
        ))).matches(new ArrayList<KeyToMultiValue>(Arrays.asList(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo")
        ))));
    }

    @Test
    public void shouldMatchNullTest() {
        assertTrue(new MultiValueMapMatcher(KeyToMultiValue.toMultiMap()).matches(null));
    }

    @Test
    public void shouldNotMatchNullTestWhenNotApplied() {
        assertFalse(not(new MultiValueMapMatcher(KeyToMultiValue.toMultiMap())).matches(null));
    }

    @Test
    public void shouldMatchEmptyTest() {
        assertTrue(new MultiValueMapMatcher(KeyToMultiValue.toMultiMap()).matches(new ArrayList<KeyToMultiValue>()));
    }

    @Test
    public void shouldNotMatchEmptyTestWhenNotApplied() {
        assertFalse(not(new MultiValueMapMatcher(KeyToMultiValue.toMultiMap())).matches(new ArrayList<KeyToMultiValue>()));
    }
}
