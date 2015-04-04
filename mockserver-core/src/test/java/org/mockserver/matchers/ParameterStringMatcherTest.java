package org.mockserver.matchers;

import org.junit.Test;
import org.mockserver.model.Parameter;

import java.util.Arrays;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockserver.matchers.NotMatcher.not;

/**
 * @author jamesdbloom
 */
public class ParameterStringMatcherTest {

    @Test
    public void shouldMatchMatchingString() {
        assertTrue(new ParameterStringMatcher(Arrays.asList(
                new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                new Parameter("parameterTwoName", "parameterTwoValue")
        )).matches("" +
                "parameterOneName=parameterOneValueOne" +
                "&parameterOneName=parameterOneValueTwo" +
                "&parameterTwoName=parameterTwoValue"));
    }

    @Test
    public void shouldNotMatchMatchingStringWhenNotApplied() {
        assertFalse(not(new ParameterStringMatcher(Arrays.asList(
                new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                new Parameter("parameterTwoName", "parameterTwoValue")
        ))).matches("" +
                "parameterOneName=parameterOneValueOne" +
                "&parameterOneName=parameterOneValueTwo" +
                "&parameterTwoName=parameterTwoValue"));
    }

    @Test
    public void shouldMatchMatchingStringWithNotParameterAndNormalParameter() {
        assertTrue(new ParameterStringMatcher(Arrays.asList(
                new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                org.mockserver.model.Not.not(new Parameter("parameterThree", "parameterThreeValueOne"))
        )).matches("" +
                "parameterOneName=parameterOneValueOne" +
                "&parameterOneName=parameterOneValueTwo" +
                "&parameterTwoName=parameterTwoValue"));
    }

    @Test
    public void shouldMatchMatchingStringWithOnlyParameter() {
        assertTrue(new ParameterStringMatcher(Arrays.asList(
                org.mockserver.model.Not.not(new Parameter("parameterThree", "parameterThreeValueOne"))
        )).matches("" +
                "parameterOneName=parameterOneValueOne" +
                "&parameterOneName=parameterOneValueTwo" +
                "&parameterTwoName=parameterTwoValue"));
    }

    @Test
    public void shouldMatchMatchingStringWithOnlyParameterForEmptyBody() {
        assertTrue(new ParameterStringMatcher(Arrays.asList(
                org.mockserver.model.Not.not(new Parameter("parameterThree", "parameterThreeValueOne"))
        )).matches(""));
    }

    @Test
    public void shouldNotMatchMatchingStringWithNotParameterAndNormalParameter() {
        assertFalse(new ParameterStringMatcher(Arrays.asList(
                new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                org.mockserver.model.Not.not(new Parameter("parameterTwoName", "parameterTwoValue")
                ))).matches("" +
                "parameterOneName=parameterOneValueOne" +
                "&parameterOneName=parameterOneValueTwo" +
                "&parameterTwoName=parameterTwoValue"));
    }

    @Test
    public void shouldNotMatchMatchingStringWithOnlyNotParameter() {
        assertFalse(new ParameterStringMatcher(Arrays.asList(
                org.mockserver.model.Not.not(new Parameter("parameterTwoName", "parameterTwoValue")
                ))).matches("" +
                "parameterOneName=parameterOneValueOne" +
                "&parameterOneName=parameterOneValueTwo" +
                "&parameterTwoName=parameterTwoValue"));
    }

    @Test
    public void shouldNotMatchMatchingStringWithOnlyNotParameterForBodyWithSingleParameter() {
        assertFalse(new ParameterStringMatcher(Arrays.asList(
                org.mockserver.model.Not.not(new Parameter("parameterTwoName", "parameterTwoValue")
                ))).matches("" +
                "parameterTwoName=parameterTwoValue"));
    }

    @Test
    public void shouldMatchNullExpectation() {
        assertTrue(new ParameterStringMatcher(null).matches("some_value"));
    }

    @Test
    public void shouldNotMatchNullExpectationWhenNotApplied() {
        assertFalse(not(new ParameterStringMatcher(null)).matches("some_value"));
    }

    @Test
    public void shouldMatchEmptyExpectation() {
        assertTrue(new ParameterStringMatcher(Arrays.<Parameter>asList()).matches("some_value"));
    }

    @Test
    public void shouldNotMatchEmptyExpectationWhenNotApplied() {
        assertFalse(not(new ParameterStringMatcher(Arrays.<Parameter>asList())).matches("some_value"));
    }

    @Test
    public void shouldNotMatchIncorrectParameterName() {
        assertFalse(new ParameterStringMatcher(Arrays.asList(
                new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                new Parameter("parameterTwoName", "parameterTwoValue")
        )).matches("" +
                "parameterOneName=parameterOneValueOne" +
                "&INCORRECTParameterOneName=parameterOneValueTwo" +
                "&parameterTwoName=parameterTwoValue"));
    }

    @Test
    public void shouldMatchIncorrectParameterNameWhenNotApplied() {
        assertTrue(not(new ParameterStringMatcher(Arrays.asList(
                new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                new Parameter("parameterTwoName", "parameterTwoValue")
        ))).matches("" +
                "parameterOneName=parameterOneValueOne" +
                "&INCORRECTParameterOneName=parameterOneValueTwo" +
                "&parameterTwoName=parameterTwoValue"));
    }

    @Test
    public void shouldNotMatchIncorrectParameterValue() {
        assertFalse(new ParameterStringMatcher(Arrays.asList(
                new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                new Parameter("parameterTwoName", "parameterTwoValue")
        )).matches("" +
                "parameterOneName=parameterOneValueOne" +
                "&parameterOneName=parameterOneValueTwo" +
                "&parameterTwoName=INCORRECTParameterTwoValue"));
    }

    @Test
    public void shouldMatchIncorrectParameterValueWhenNotApplied() {
        assertTrue(not(new ParameterStringMatcher(Arrays.asList(
                new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                new Parameter("parameterTwoName", "parameterTwoValue")
        ))).matches("" +
                "parameterOneName=parameterOneValueOne" +
                "&parameterOneName=parameterOneValueTwo" +
                "&parameterTwoName=INCORRECTParameterTwoValue"));
    }

    @Test
    public void shouldNotMatchIncorrectParameterNameAndValue() {
        assertFalse(new ParameterStringMatcher(Arrays.asList(
                new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                new Parameter("parameterTwoName", "parameterTwoValue")
        )).matches("" +
                "parameterOneName=parameterOneValueOne" +
                "&parameterOneName=parameterOneValueTwo" +
                "&INCORRECTParameterTwoName=INCORRECTParameterTwoValue"));
    }

    @Test
    public void shouldMatchIncorrectParameterNameAndValueWhenNotApplied() {
        assertTrue(not(new ParameterStringMatcher(Arrays.asList(
                new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                new Parameter("parameterTwoName", "parameterTwoValue")
        ))).matches("" +
                "parameterOneName=parameterOneValueOne" +
                "&parameterOneName=parameterOneValueTwo" +
                "&INCORRECTParameterTwoName=INCORRECTParameterTwoValue"));
    }

    @Test
    public void shouldNotMatchNullParameterValue() {
        assertFalse(new ParameterStringMatcher(Arrays.asList(
                new Parameter("parameterOneName", "parameterValueOne"),
                new Parameter("parameterTwoName", "parameterTwoValue")
        )).matches("" +
                "parameterOneName=parameterValueOne" +
                "&parameterTwoName="));
    }

    @Test
    public void shouldMatchNullParameterValueWhenNotApplied() {
        assertTrue(not(new ParameterStringMatcher(Arrays.asList(
                new Parameter("parameterOneName", "parameterValueOne"),
                new Parameter("parameterTwoName", "parameterTwoValue")
        ))).matches("" +
                "parameterOneName=parameterValueOne" +
                "&parameterTwoName="));
    }

    @Test
    public void shouldMatchNullParameterValueInExpectation() {
        assertTrue(new ParameterStringMatcher(Arrays.asList(
                new Parameter("parameterOneName", "parameterValueOne"),
                new Parameter("parameterTwoName", "")
        )).matches("" +
                "parameterOneName=parameterValueOne" +
                "&parameterTwoName=parameterTwoValue"));
    }

    @Test
    public void shouldNotMatchMissingParameter() {
        assertFalse(new ParameterStringMatcher(Arrays.asList(
                new Parameter("parameterOneName", "parameterValueOne"),
                new Parameter("parameterTwoName", "parameterTwoValue")
        )).matches("" +
                "parameterOneName=parameterValueOne"));
    }

    @Test
    public void shouldMatchMissingParameterWhenNotApplied() {
        assertTrue(not(new ParameterStringMatcher(Arrays.asList(
                new Parameter("parameterOneName", "parameterValueOne"),
                new Parameter("parameterTwoName", "parameterTwoValue")
        ))).matches("" +
                "parameterOneName=parameterValueOne"));
    }

    @Test
    public void shouldMatchNullTest() {
        assertTrue(new ParameterStringMatcher(Arrays.<Parameter>asList()).matches(null));
    }

    @Test
    public void shouldNotMatchNullTestWhenNotApplied() {
        assertFalse(not(new ParameterStringMatcher(Arrays.<Parameter>asList())).matches(null));
    }

    @Test
    public void shouldMatchEmptyTest() {
        assertTrue(new ParameterStringMatcher(Arrays.<Parameter>asList()).matches(""));
    }

    @Test
    public void shouldNotMatchEmptyTestWhenNotApplied() {
        assertFalse(not(new ParameterStringMatcher(Arrays.<Parameter>asList())).matches(""));
    }
}
