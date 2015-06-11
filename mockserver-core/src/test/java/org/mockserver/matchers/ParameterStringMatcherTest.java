package org.mockserver.matchers;

import org.junit.Test;
import org.mockserver.model.Not;
import org.mockserver.model.Parameter;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockserver.model.NottableString.not;

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

        assertTrue(new ParameterStringMatcher(Collections.singletonList(
                new Parameter("parameter.*", "parameter.*")
        )).matches("" +
                "parameterOneName=parameterOneValueOne" +
                "&parameterOneName=parameterOneValueTwo" +
                "&parameterTwoName=parameterTwoValue"));
    }

    @Test
    public void shouldNotMatchMatchingStringWhenNotApplied() {
        // given
        assertTrue(new ParameterStringMatcher(Arrays.asList(
                new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                new Parameter("parameterTwoName", "parameterTwoValue")
        )).matches("" +
                "parameterOneName=parameterOneValueOne" +
                "&parameterOneName=parameterOneValueTwo" +
                "&parameterTwoName=parameterTwoValue"));
        
        // then - not matcher
        assertFalse(NotMatcher.not(new ParameterStringMatcher(Arrays.asList(
                new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                new Parameter("parameterTwoName", "parameterTwoValue")
        ))).matches("" +
                "parameterOneName=parameterOneValueOne" +
                "&parameterOneName=parameterOneValueTwo" +
                "&parameterTwoName=parameterTwoValue"));

        // and - not parameter
        assertFalse(new ParameterStringMatcher(Arrays.asList(
                new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                new Parameter(not("parameterTwoName"), not("parameterTwoValue"))
        )).matches("" +
                "parameterOneName=parameterOneValueOne" +
                "&parameterOneName=parameterOneValueTwo" +
                "&parameterTwoName=parameterTwoValue"));

        // and - not parameter
        assertTrue(NotMatcher.not(new ParameterStringMatcher(Arrays.asList(
                new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                new Parameter(not("parameterTwoName"), not("parameterTwoValue"))
        ))).matches("" +
                "parameterOneName=parameterOneValueOne" +
                "&parameterOneName=parameterOneValueTwo" +
                "&parameterTwoName=parameterTwoValue"));
    }

    @Test
    public void shouldMatchMatchingStringWithNotParameterAndNormalParameter() {
        // not matching parameter
        assertFalse(new ParameterStringMatcher(Arrays.asList(
                new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                new Parameter(not("parameterTwoName"), not("parameterTwoValue"))
        )).matches("" +
                "parameterOneName=parameterOneValueOne" +
                "&parameterOneName=parameterOneValueTwo" +
                "&parameterTwoName=parameterTwoValue"));

        // not extra parameter
        assertTrue(new ParameterStringMatcher(Arrays.asList(
                new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                new Parameter(not("parameterThree"), not("parameterThreeValueOne"))
        )).matches("" +
                "parameterOneName=parameterOneValueOne" +
                "&parameterOneName=parameterOneValueTwo" +
                "&parameterTwoName=parameterTwoValue"));

        // not only parameter
        assertTrue(new ParameterStringMatcher(Collections.singletonList(
                new Parameter(not("parameterThree"), not("parameterThreeValueOne"))
        )).matches("" +
                "parameterOneName=parameterOneValueOne" +
                "&parameterOneName=parameterOneValueTwo" +
                "&parameterTwoName=parameterTwoValue"));

        // not all parameters (but matching)
        assertFalse(new ParameterStringMatcher(Collections.singletonList(
                new Parameter(not("parameter.*"), not(".*"))
        )).matches("" +
                "parameterOneName=parameterOneValueOne" +
                "&parameterOneName=parameterOneValueTwo" +
                "&parameterTwoName=parameterTwoValue"));

        // not all parameters (but not matching name)
        assertFalse(new ParameterStringMatcher(Collections.singletonList(
                new Parameter(not("parameter.*"), not("parameter.*"))
        )).matches("" +
                "notParameterOneName=parameterOneValueOne" +
                "&notParameterOneName=parameterOneValueTwo" +
                "&notParameterTwoName=parameterTwoValue"));

        // not all parameters (but not matching value)
        assertFalse(new ParameterStringMatcher(Collections.singletonList(
                new Parameter(not("parameter.*"), not("parameter.*"))
        )).matches("" +
                "parameterOneName=notParameterOneValueOne" +
                "&parameterOneName=notParameterOneValueTwo" +
                "&parameterTwoName=notParameterTwoValue"));
    }

    @Test
    public void shouldMatchMatchingStringWithOnlyParameter() {
        assertTrue(new ParameterStringMatcher(Collections.singletonList(
                new Parameter(not("parameterThree"), not("parameterThreeValueOne"))
        )).matches("" +
                "parameterOneName=parameterOneValueOne" +
                "&parameterOneName=parameterOneValueTwo" +
                "&parameterTwoName=parameterTwoValue"));
        assertFalse(new ParameterStringMatcher(Collections.singletonList(
                new Parameter("parameterThree", "parameterThreeValueOne")
        )).matches("" +
                "parameterOneName=parameterOneValueOne" +
                "&parameterOneName=parameterOneValueTwo" +
                "&parameterTwoName=parameterTwoValue"));

        assertFalse(new ParameterStringMatcher(Collections.singletonList(
                new Parameter(not("parameterOneName"), not("parameterOneValueOne"), not("parameterOneValueTwo"))
        )).matches("" +
                "parameterOneName=parameterOneValueOne" +
                "&parameterOneName=parameterOneValueTwo" +
                "&parameterTwoName=parameterTwoValue"));
        assertTrue(new ParameterStringMatcher(Collections.singletonList(
                new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo")
        )).matches("" +
                "parameterOneName=parameterOneValueOne" +
                "&parameterOneName=parameterOneValueTwo" +
                "&parameterTwoName=parameterTwoValue"));
    }

    @Test
    public void shouldMatchMatchingStringWithOnlyParameterForEmptyBody() {
        assertTrue(new ParameterStringMatcher(
                        Collections.<Parameter>emptyList()
        ).matches("parameterThree=parameterThreeValueOne"));

        assertFalse(new ParameterStringMatcher(Collections.singletonList(
                new Parameter("parameterThree", "parameterThreeValueOne")
        )).matches(""));

        assertTrue(new ParameterStringMatcher(Collections.singletonList(
                new Parameter(not("parameterThree"), not("parameterThreeValueOne"))
        )).matches(""));
    }

    @Test
    public void shouldNotMatchMatchingStringWithNotParameterAndNormalParameter() {
        assertFalse(new ParameterStringMatcher(Arrays.asList(
                new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                new Parameter(not("parameterTwoName"), not("parameterTwoValue")))).matches("" +
                "parameterOneName=parameterOneValueOne" +
                "&parameterOneName=parameterOneValueTwo" +
                "&parameterTwoName=parameterTwoValue"));
    }

    @Test
    public void shouldNotMatchMatchingStringWithOnlyNotParameter() {
        assertFalse(new ParameterStringMatcher(Collections.singletonList(
                new Parameter(not("parameterTwoName"), not("parameterTwoValue")))).matches("" +
                "parameterOneName=parameterOneValueOne" +
                "&parameterOneName=parameterOneValueTwo" +
                "&parameterTwoName=parameterTwoValue"));
    }

    @Test
    public void shouldNotMatchMatchingStringWithOnlyNotParameterForBodyWithSingleParameter() {
        assertFalse(new ParameterStringMatcher(Collections.singletonList(
                new Parameter(not("parameterTwoName"), not("parameterTwoValue")))).matches("" +
                "parameterTwoName=parameterTwoValue"));
    }

    @Test
    public void shouldMatchNullExpectation() {
        assertTrue(new ParameterStringMatcher(null).matches("some_value"));
    }

    @Test
    public void shouldNotMatchNullExpectationWhenNotApplied() {
        assertFalse(NotMatcher.not(new ParameterStringMatcher(null)).matches("some_value"));
    }

    @Test
    public void shouldMatchEmptyExpectation() {
        assertTrue(new ParameterStringMatcher(Collections.<Parameter>emptyList()).matches("some_value"));
    }

    @Test
    public void shouldNotMatchEmptyExpectationWhenNotApplied() {
        assertFalse(NotMatcher.not(new ParameterStringMatcher(Collections.<Parameter>emptyList())).matches("some_value"));
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
        assertTrue(NotMatcher.not(new ParameterStringMatcher(Arrays.asList(
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
        assertTrue(NotMatcher.not(new ParameterStringMatcher(Arrays.asList(
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
        assertTrue(NotMatcher.not(new ParameterStringMatcher(Arrays.asList(
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
        assertTrue(NotMatcher.not(new ParameterStringMatcher(Arrays.asList(
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
        assertTrue(NotMatcher.not(new ParameterStringMatcher(Arrays.asList(
                new Parameter("parameterOneName", "parameterValueOne"),
                new Parameter("parameterTwoName", "parameterTwoValue")
        ))).matches("" +
                "parameterOneName=parameterValueOne"));
    }

    @Test
    public void shouldMatchNullTest() {
        assertTrue(new ParameterStringMatcher(Collections.<Parameter>emptyList()).matches(null));
    }

    @Test
    public void shouldNotMatchNullTestWhenNotApplied() {
        assertFalse(NotMatcher.not(new ParameterStringMatcher(Collections.<Parameter>emptyList())).matches(null));
    }

    @Test
    public void shouldMatchEmptyTest() {
        assertTrue(new ParameterStringMatcher(Collections.<Parameter>emptyList()).matches(""));
    }

    @Test
    public void shouldNotMatchEmptyTestWhenNotApplied() {
        assertFalse(NotMatcher.not(new ParameterStringMatcher(Collections.<Parameter>emptyList())).matches(""));
    }
}
