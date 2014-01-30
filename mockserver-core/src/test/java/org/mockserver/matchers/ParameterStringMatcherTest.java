package org.mockserver.matchers;

import org.junit.Test;
import org.mockserver.model.Parameter;

import java.util.Arrays;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
    public void shouldMatchNullExpectation() {
        assertTrue(new ParameterStringMatcher(null).matches("some_value"));
    }

    @Test
    public void shouldMatchEmptyExpectation() {
        assertTrue(new ParameterStringMatcher(Arrays.<Parameter>asList()).matches("some_value"));
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
    public void shouldMatchNullTest() {
        assertTrue(new ParameterStringMatcher(Arrays.<Parameter>asList()).matches(null));
    }

    @Test
    public void shouldMatchEmptyTest() {
        assertTrue(new ParameterStringMatcher(Arrays.<Parameter>asList()).matches(""));
    }
}
