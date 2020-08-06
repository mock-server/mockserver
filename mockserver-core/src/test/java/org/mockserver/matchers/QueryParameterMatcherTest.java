package org.mockserver.matchers;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.Parameter;
import org.mockserver.model.Parameters;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockserver.model.NottableString.not;

/**
 * @author jamesdbloom
 */
public class QueryParameterMatcherTest {

    @Test
    public void shouldMatchMatchingParameter() {
        assertTrue(new MultiValueMapMatcher(new MockServerLogger(), new Parameters().withEntries(
            new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
            new Parameter("parameterTwoName", "parameterTwoValue")
        ), true).matches(null,
            new Parameters().withEntries(
                new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                new Parameter("parameterTwoName", "parameterTwoValue")
            )
        ));

        assertTrue(new MultiValueMapMatcher(new MockServerLogger(), new Parameters().withEntries(
            new Parameter("parameter.*", "parameter.*")
        ), true).matches(
            null,
            new Parameters().withEntries(
                new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                new Parameter("parameterTwoName", "parameterTwoValue")
            )
        ));
    }

    @Test
    public void shouldNotMatchMatchingParameterWhenNotApplied() {
        // given
        assertTrue(new MultiValueMapMatcher(new MockServerLogger(), new Parameters().withEntries(
            new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
            new Parameter("parameterTwoName", "parameterTwoValue")
        ), true).matches(
            null,
            new Parameters().withEntries(
                new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                new Parameter("parameterTwoName", "parameterTwoValue")
            )
        ));

        // then - not matcher
        assertFalse(NotMatcher.notMatcher(new MultiValueMapMatcher(new MockServerLogger(), new Parameters().withEntries(
            new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
            new Parameter("parameterTwoName", "parameterTwoValue")
        ), true)).matches(
            null,
            new Parameters().withEntries(
                new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                new Parameter("parameterTwoName", "parameterTwoValue")
            )
        ));

        // and - not parameter
        assertFalse(new MultiValueMapMatcher(new MockServerLogger(), new Parameters().withEntries(
            new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
            new Parameter(not("parameterTwoName"), not("parameterTwoValue"))
        ), true).matches(
            null,
            new Parameters().withEntries(
                new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                new Parameter("parameterTwoName", "parameterTwoValue")
            )
        ));

        // and - not matcher and not parameter
        assertTrue(NotMatcher.notMatcher(new MultiValueMapMatcher(new MockServerLogger(), new Parameters().withEntries(
            new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
            new Parameter(not("parameterTwoName"), not("parameterTwoValue"))
        ), true)).matches(
            null,
            new Parameters().withEntries(
                new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                new Parameter("parameterTwoName", "parameterTwoValue")
            )
        ));
    }

    @Test
    public void shouldMatchMatchingParameterWithNotParameterAndNormalParameter() {
        // not matching parameter
        assertFalse(new MultiValueMapMatcher(new MockServerLogger(), new Parameters().withEntries(
            new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
            new Parameter(not("parameterTwoName"), not("parameterTwoValue"))
        ), true).matches(
            null,
            new Parameters().withEntries(
                new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                new Parameter("parameterTwoName", "parameterTwoValue")
            )
        ));

        // not extra parameter (number of parameters don't match)
        assertFalse(new MultiValueMapMatcher(new MockServerLogger(), new Parameters().withEntries(
            new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
            new Parameter("parameterTwoName", "parameterTwoValue"),
            new Parameter(not("parameterThree"), not("parameterThreeValueOne"))
        ), true).matches(
            null,
            new Parameters().withEntries(
                new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                new Parameter("parameterTwoName", "parameterTwoValue")
            )
        ));

        // not only parameter
        assertTrue(new MultiValueMapMatcher(new MockServerLogger(), new Parameters().withEntries(
            new Parameter(not("parameterThree"), not("parameterThreeValueOne"))
        ), true).matches(
            null,
            new Parameters().withEntries(
                new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                new Parameter("parameterTwoName", "parameterTwoValue")
            )
        ));

        // not all parameters (but matching)
        assertFalse(new MultiValueMapMatcher(new MockServerLogger(), new Parameters().withEntries(
            new Parameter(not("parameter.*"), not(".*"))
        ), true).matches(
            null,
            new Parameters().withEntries(
                new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                new Parameter("parameterTwoName", "parameterTwoValue")
            )
        ));

        // not all parameters (but not matching name)
        assertFalse(new MultiValueMapMatcher(new MockServerLogger(), new Parameters().withEntries(
            new Parameter(not("parameter.*"), not("parameter.*"))
        ), true).matches(
            null,
            new Parameters().withEntries(
                new Parameter("notParameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                new Parameter("notParameterTwoName", "parameterTwoValue")
            )
        ));

        // not all parameters (but not matching value)
        assertFalse(new MultiValueMapMatcher(new MockServerLogger(), new Parameters().withEntries(
            new Parameter(not("parameter.*"), not("parameter.*"))
        ), true).matches(
            null,
            new Parameters().withEntries(
                new Parameter("parameterOneName", "notParameterOneValueOne", "notParameterOneValueTwo"),
                new Parameter("parameterTwoName", "notParameterTwoValue")
            )
        ));
    }

    @Test
    public void shouldMatchMatchingParameterWithOnlyParameter() {
        assertTrue(new MultiValueMapMatcher(new MockServerLogger(), new Parameters().withEntries(
            new Parameter(not("parameterThree"), not("parameterThreeValueOne"))
        ), true).matches(
            null,
            new Parameters().withEntries(
                new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                new Parameter("parameterTwoName", "parameterTwoValue")
            )
        ));
        assertFalse(new MultiValueMapMatcher(new MockServerLogger(), new Parameters().withEntries(
            new Parameter("parameterThree", "parameterThreeValueOne")
        ), true).matches(
            null,
            new Parameters().withEntries(
                new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                new Parameter("parameterTwoName", "parameterTwoValue")
            )
        ));

        assertFalse(new MultiValueMapMatcher(new MockServerLogger(), new Parameters().withEntries(
            new Parameter(not("parameterOneName"), not("parameterOneValueOne"), not("parameterOneValueTwo"))
        ), true).matches(
            null,
            new Parameters().withEntries(
                new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                new Parameter("parameterTwoName", "parameterTwoValue")
            )
        ));
        assertTrue(new MultiValueMapMatcher(new MockServerLogger(), new Parameters().withEntries(
            new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo")
        ), true).matches(
            null,
            new Parameters().withEntries(
                new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                new Parameter("parameterTwoName", "parameterTwoValue")
            )
        ));
    }

    @Test
    public void shouldMatchMatchingParameterWithOnlyParameterForEmptyList() {
        assertTrue(new MultiValueMapMatcher(new MockServerLogger(), new Parameters(), true).matches(
            null,
            new Parameters().withEntries(
                new Parameter("parameterThree", "parameterThreeValueOne")
            )
        ));

        assertFalse(new MultiValueMapMatcher(new MockServerLogger(), new Parameters().withEntries(
            new Parameter("parameterThree", "parameterThreeValueOne")
        ), true).matches(null, new Parameters()));

        assertTrue(new MultiValueMapMatcher(new MockServerLogger(), new Parameters().withEntries(
            new Parameter(not("parameterThree"), not("parameterThreeValueOne"))
        ), true).matches(null, new Parameters()));
    }

    @Test
    public void shouldNotMatchMatchingParameterWithNotParameterAndNormalParameter() {
        assertFalse(new MultiValueMapMatcher(new MockServerLogger(), new Parameters().withEntries(
            new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
            new Parameter(not("parameterTwoName"), not("parameterTwoValue"))
        ), true).matches(
            null,
            new Parameters().withEntries(
                new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                new Parameter("parameterTwoName", "parameterTwoValue")
            )
        ));
    }

    @Test
    public void shouldMatchMatchingParameterWithOnlyNotParameter() {
        assertTrue(new MultiValueMapMatcher(new MockServerLogger(), new Parameters().withEntries(
            new Parameter(not("parameterTwoName"), not("parameterTwoValue"))
        ), true).matches(
            null,
            new Parameters().withEntries(
                new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                new Parameter("notParameterTwoName", "parameterTwoValue")
            )
        ));
        assertFalse(new MultiValueMapMatcher(new MockServerLogger(), new Parameters().withEntries(
            new Parameter(not("parameterTwoName"), "parameterTwoValue")
        ), true).matches(
            null,
            new Parameters().withEntries(
                new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                new Parameter("parameterTwoName", "parameterTwoValue")
            )
        ));
    }

    @Test
    public void shouldNotMatchMatchingParameterWithOnlyNotParameterForBodyWithSingleParameter() {
        assertFalse(new MultiValueMapMatcher(new MockServerLogger(), new Parameters().withEntries(
            new Parameter(not("parameterTwoName"), not("parameterTwoValue"))
        ), true).matches(
            null,
            new Parameters().withEntries(
                new Parameter("parameterTwoName", "parameterTwoValue")
            )
        ));
    }

    @Test
    public void shouldMatchNullExpectation() {
        assertTrue(new MultiValueMapMatcher(new MockServerLogger(), null, true).matches(
            null,
            new Parameters().withEntries(
                new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                new Parameter("parameterTwoName", "parameterTwoValue")
            )
        ));
    }

    @Test
    public void shouldNotMatchNullExpectationWhenNotApplied() {
        assertFalse(NotMatcher.notMatcher(new MultiValueMapMatcher(new MockServerLogger(), null, true))
            .matches(
                null,
                new Parameters().withEntries(
                    new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                    new Parameter("parameterTwoName", "parameterTwoValue")
                )
            ));
    }

    @Test
    public void shouldMatchEmptyExpectation() {
        assertTrue(new MultiValueMapMatcher(new MockServerLogger(), new Parameters(), true).matches(
            null,
            new Parameters().withEntries(
                new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                new Parameter("parameterTwoName", "parameterTwoValue")
            )
        ));
    }

    @Test

    public void shouldNotMatchEmptyExpectationWhenNotApplied() {
        assertFalse(NotMatcher.notMatcher(new MultiValueMapMatcher(new MockServerLogger(), new Parameters(), true))
            .matches(
                null,
                new Parameters().withEntries(
                    new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                    new Parameter("parameterTwoName", "parameterTwoValue")
                )
            ));
    }

    @Test
    public void shouldNotMatchIncorrectParameterName() {
        assertFalse(new MultiValueMapMatcher(new MockServerLogger(), new Parameters().withEntries(
            new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
            new Parameter("parameterTwoName", "parameterTwoValue")
        ), true).matches(
            null,
            new Parameters().withEntries(
                new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                new Parameter("INCORRECTparameterTwoName", "parameterTwoValue")
            )
        ));
    }

    @Test
    public void shouldMatchIncorrectParameterNameWhenNotApplied() {
        assertTrue(NotMatcher.notMatcher(new MultiValueMapMatcher(new MockServerLogger(), new Parameters().withEntries(
            new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
            new Parameter("parameterTwoName", "parameterTwoValue")
        ), true)).matches(
            null,
            new Parameters().withEntries(
                new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                new Parameter("INCORRECTparameterTwoName", "parameterTwoValue")
            )
        ));
    }

    @Test
    public void shouldNotMatchIncorrectParameterValue() {
        assertFalse(new MultiValueMapMatcher(new MockServerLogger(), new Parameters().withEntries(
            new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
            new Parameter("parameterTwoName", "parameterTwoValue")
        ), true).matches(
            null,
            new Parameters().withEntries(
                new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                new Parameter("parameterTwoName", "INCORRECTparameterTwoValue")
            )
        ));
    }

    @Test
    public void shouldMatchIncorrectParameterValueWhenNotApplied() {
        assertTrue(NotMatcher.notMatcher(new MultiValueMapMatcher(new MockServerLogger(), new Parameters().withEntries(
            new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
            new Parameter("parameterTwoName", "parameterTwoValue")
        ), true)).matches(
            null,
            new Parameters().withEntries(
                new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                new Parameter("parameterTwoName", "INCORRECTparameterTwoValue")
            )
        ));
    }

    @Test
    public void shouldNotMatchIncorrectParameterNameAndValue() {
        assertFalse(new MultiValueMapMatcher(new MockServerLogger(), new Parameters().withEntries(
            new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
            new Parameter("parameterTwoName", "parameterTwoValue")
        ), true).matches(
            null,
            new Parameters().withEntries(
                new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                new Parameter("INCORRECTparameterTwoName", "INCORRECTparameterTwoValue")
            )
        ));
    }

    @Test
    public void shouldMatchIncorrectParameterNameAndValueWhenNotApplied() {
        assertTrue(NotMatcher.notMatcher(new MultiValueMapMatcher(new MockServerLogger(), new Parameters().withEntries(
            new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
            new Parameter("parameterTwoName", "parameterTwoValue")
        ), true)).matches(
            null,
            new Parameters().withEntries(
                new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                new Parameter("INCORRECTparameterTwoName", "INCORRECTparameterTwoValue")
            )
        ));
    }

    @Test
    public void shouldMatchNullParameterValue() {
        assertTrue(new MultiValueMapMatcher(new MockServerLogger(), new Parameters().withEntries(
            new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
            new Parameter("parameterTwoName", "parameterTwoValue")
        ), true).matches(
            null,
            new Parameters().withEntries(
                new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                new Parameter("parameterTwoName")
            )
        ));
    }

    @Test
    public void shouldNotMatchNullParameterValueWhenNotApplied() {
        assertFalse(NotMatcher.notMatcher(new MultiValueMapMatcher(new MockServerLogger(), new Parameters().withEntries(
            new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
            new Parameter("parameterTwoName", "parameterTwoValue")
        ), true)).matches(
            null,
            new Parameters().withEntries(
                new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                new Parameter("parameterTwoName")
            )
        ));
    }

    @Test
    public void shouldMatchNullParameterValueInExpectation() {
        assertTrue(new MultiValueMapMatcher(new MockServerLogger(), new Parameters().withEntries(
            new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
            new Parameter("parameterTwoName", "")
        ), true).matches(
            null,
            new Parameters().withEntries(
                new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
                new Parameter("parameterTwoName", "parameterTwoValue")
            )
        ));
    }

    @Test
    public void shouldNotMatchMissingParameter() {
        assertFalse(new MultiValueMapMatcher(new MockServerLogger(), new Parameters().withEntries(
            new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
            new Parameter("parameterTwoName", "parameterTwoValue")
        ), true).matches(
            null,
            new Parameters().withEntries(
                new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo")
            )
        ));
    }

    @Test
    public void shouldMatchMissingParameterWhenNotApplied() {
        assertTrue(NotMatcher.notMatcher(new MultiValueMapMatcher(new MockServerLogger(), new Parameters().withEntries(
            new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo"),
            new Parameter("parameterTwoName", "parameterTwoValue")
        ), true)).matches(
            null,
            new Parameters().withEntries(
                new Parameter("parameterOneName", "parameterOneValueOne", "parameterOneValueTwo")
            )
        ));
    }

    @Test
    public void shouldMatchNullTest() {
        assertTrue(new MultiValueMapMatcher(new MockServerLogger(), new Parameters(), true).matches(null, null));
    }

    @Test
    public void shouldNotMatchNullTestWhenNotApplied() {
        assertFalse(NotMatcher.notMatcher(new MultiValueMapMatcher(new MockServerLogger(), new Parameters(), true)).matches(null, null));
    }

    @Test
    public void shouldMatchEmptyTest() {
        assertTrue(new MultiValueMapMatcher(new MockServerLogger(), new Parameters(), true).matches(null, new Parameters()));
    }

    @Test
    public void shouldNotMatchEmptyTestWhenNotApplied() {
        assertFalse(NotMatcher.notMatcher(new MultiValueMapMatcher(new MockServerLogger(), new Parameters(), true)).matches(null, new Parameters()));
    }
}
