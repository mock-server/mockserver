package org.mockserver.matchers;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.Header;
import org.mockserver.model.Headers;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockserver.model.NottableString.not;

/**
 * @author jamesdbloom
 */
public class HeaderMatcherTest {

    @Test
    public void shouldMatchMatchingHeader() {
        assertTrue(new MultiValueMapMatcher(new MockServerLogger(), new Headers().withEntries(
            new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
            new Header("headerTwoName", "headerTwoValue")
        )).matches(
            null,
            new Headers().withEntries(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("headerTwoName", "headerTwoValue")
            )
        ));

        assertTrue(new MultiValueMapMatcher(new MockServerLogger(), new Headers().withEntries(
            new Header("header.*", "header.*")
        )).matches(
            null,
            new Headers().withEntries(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("headerTwoName", "headerTwoValue")
            )
        ));
    }

    @Test
    public void shouldNotMatchMatchingHeaderWhenNotApplied() {
        // given
        assertTrue(new MultiValueMapMatcher(new MockServerLogger(), new Headers().withEntries(
            new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
            new Header("headerTwoName", "headerTwoValue")
        )).matches(
            null,
            new Headers().withEntries(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("headerTwoName", "headerTwoValue")
            )
        ));

        // then - not matcher
        assertFalse(NotMatcher.not(new MultiValueMapMatcher(new MockServerLogger(), new Headers().withEntries(
            new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
            new Header("headerTwoName", "headerTwoValue")
        ))).matches(
            null,
            new Headers().withEntries(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("headerTwoName", "headerTwoValue")
            )
        ));

        // and - not header
        assertFalse(new MultiValueMapMatcher(new MockServerLogger(), new Headers().withEntries(
            new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
            new Header(not("headerTwoName"), not("headerTwoValue"))
        )).matches(
            null,
            new Headers().withEntries(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("headerTwoName", "headerTwoValue")
            )
        ));

        // and - not matcher and not header
        assertTrue(NotMatcher.not(new MultiValueMapMatcher(new MockServerLogger(), new Headers().withEntries(
            new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
            new Header(not("headerTwoName"), not("headerTwoValue"))
        ))).matches(
            null,
            new Headers().withEntries(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("headerTwoName", "headerTwoValue")
            )
        ));
    }

    @Test
    public void shouldMatchMatchingHeaderWithNotHeaderAndNormalHeader() {
        // not matching header
        assertFalse(new MultiValueMapMatcher(new MockServerLogger(), new Headers().withEntries(
            new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
            new Header(not("headerTwoName"), not("headerTwoValue"))
        )).matches(
            null,
            new Headers().withEntries(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("headerTwoName", "headerTwoValue")
            )
        ));

        // not extra header
        assertTrue(new MultiValueMapMatcher(new MockServerLogger(), new Headers().withEntries(
            new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
            new Header("headerTwoName", "headerTwoValue"),
            new Header(not("headerThree"), not("headerThreeValueOne"))
        )).matches(
            null,
            new Headers().withEntries(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("headerTwoName", "headerTwoValue")
            )
        ));

        // not only header
        assertTrue(new MultiValueMapMatcher(new MockServerLogger(), new Headers().withEntries(
            new Header(not("headerThree"), not("headerThreeValueOne"))
        )).matches(
            null,
            new Headers().withEntries(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("headerTwoName", "headerTwoValue")
            )
        ));

        // not all headers (but matching)
        assertFalse(new MultiValueMapMatcher(new MockServerLogger(), new Headers().withEntries(
            new Header(not("header.*"), not(".*"))
        )).matches(
            null,
            new Headers().withEntries(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("headerTwoName", "headerTwoValue")
            )
        ));

        // not all headers (but not matching name)
        assertFalse(new MultiValueMapMatcher(new MockServerLogger(), new Headers().withEntries(
            new Header(not("header.*"), not("header.*"))
        )).matches(
            null,
            new Headers().withEntries(
                new Header("notHeaderOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("notHeaderTwoName", "headerTwoValue")
            )
        ));

        // not all headers (but not matching value)
        assertFalse(new MultiValueMapMatcher(new MockServerLogger(), new Headers().withEntries(
            new Header(not("header.*"), not("header.*"))
        )).matches(
            null,
            new Headers().withEntries(
                new Header("headerOneName", "notHeaderOneValueOne", "notHeaderOneValueTwo"),
                new Header("headerTwoName", "notHeaderTwoValue")
            )
        ));
    }

    @Test
    public void shouldMatchMatchingHeaderWithOnlyHeader() {
        assertTrue(new MultiValueMapMatcher(new MockServerLogger(), new Headers().withEntries(
            new Header(not("headerThree"), not("headerThreeValueOne"))
        )).matches(
            null,
            new Headers().withEntries(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("headerTwoName", "headerTwoValue")
            )
        ));
        assertFalse(new MultiValueMapMatcher(new MockServerLogger(), new Headers().withEntries(
            new Header("headerThree", "headerThreeValueOne")
        )).matches(
            null,
            new Headers().withEntries(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("headerTwoName", "headerTwoValue")
            )
        ));

        assertFalse(new MultiValueMapMatcher(new MockServerLogger(), new Headers().withEntries(
            new Header(not("headerOneName"), not("headerOneValueOne"), not("headerOneValueTwo"))
        )).matches(
            null,
            new Headers().withEntries(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("headerTwoName", "headerTwoValue")
            )
        ));
        assertTrue(new MultiValueMapMatcher(new MockServerLogger(), new Headers().withEntries(
            new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo")
        )).matches(
            null,
            new Headers().withEntries(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("headerTwoName", "headerTwoValue")
            )
        ));
    }

    @Test
    public void shouldMatchMatchingHeaderWithOnlyHeaderForEmptyList() {
        assertTrue(new MultiValueMapMatcher(new MockServerLogger(), new Headers()).matches(
            null,
            new Headers().withEntries(
                new Header("headerThree", "headerThreeValueOne")
            )
        ));

        assertFalse(new MultiValueMapMatcher(new MockServerLogger(), new Headers().withEntries(
            new Header("headerThree", "headerThreeValueOne")
        )).matches(
            null,
            new Headers()
        ));

        assertTrue(new MultiValueMapMatcher(new MockServerLogger(), new Headers().withEntries(
            new Header(not("headerThree"), not("headerThreeValueOne"))
        )).matches(
            null,
            new Headers()
        ));
    }

    @Test
    public void shouldNotMatchMatchingHeaderWithNotHeaderAndNormalHeader() {
        assertFalse(new MultiValueMapMatcher(new MockServerLogger(), new Headers().withEntries(
            new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
            new Header(not("headerTwoName"), not("headerTwoValue"))
        )).matches(
            null,
            new Headers().withEntries(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("headerTwoName", "headerTwoValue")
            )
        ));
    }

    @Test
    public void shouldNotMatchMatchingHeaderWithOnlyNotHeader() {
        assertFalse(new MultiValueMapMatcher(new MockServerLogger(), new Headers().withEntries(
            new Header(not("headerTwoName"), not("headerTwoValue"))
        )).matches(
            null,
            new Headers().withEntries(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("headerTwoName", "headerTwoValue")
            )
        ));
    }

    @Test
    public void shouldNotMatchMatchingHeaderWithOnlyNotHeaderForBodyWithSingleHeader() {
        assertFalse(new MultiValueMapMatcher(new MockServerLogger(), new Headers().withEntries(
            new Header(not("headerTwoName"), not("headerTwoValue"))
        )).matches(
            null,
            new Headers().withEntries(
                new Header("headerTwoName", "headerTwoValue")
            )
        ));
    }

    @Test
    public void shouldMatchNullExpectation() {
        assertTrue(new MultiValueMapMatcher(new MockServerLogger(), null).matches(
            null,
            new Headers().withEntries(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("headerTwoName", "headerTwoValue")
            )
        ));
    }

    @Test
    public void shouldNotMatchNullExpectationWhenNotApplied() {
        assertFalse(NotMatcher.not(new MultiValueMapMatcher(new MockServerLogger(), null))
            .matches(
                null,
                new Headers().withEntries(
                    new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                    new Header("headerTwoName", "headerTwoValue")
                )
            ));
    }

    @Test
    public void shouldMatchEmptyExpectation() {
        assertTrue(new MultiValueMapMatcher(new MockServerLogger(), new Headers()).matches(
            null,
            new Headers().withEntries(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("headerTwoName", "headerTwoValue")
            )
        ));
    }

    @Test

    public void shouldNotMatchEmptyExpectationWhenNotApplied() {
        assertFalse(NotMatcher.not(new MultiValueMapMatcher(new MockServerLogger(), new Headers()))
            .matches(
                null,
                new Headers().withEntries(
                    new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                    new Header("headerTwoName", "headerTwoValue")
                )
            ));
    }

    @Test
    public void shouldNotMatchIncorrectHeaderName() {
        assertFalse(new MultiValueMapMatcher(new MockServerLogger(), new Headers().withEntries(
            new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
            new Header("headerTwoName", "headerTwoValue")
        )).matches(
            null,
            new Headers().withEntries(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("INCORRECTheaderTwoName", "headerTwoValue")
            )
        ));
    }

    @Test
    public void shouldMatchIncorrectHeaderNameWhenNotApplied() {
        assertTrue(NotMatcher.not(new MultiValueMapMatcher(new MockServerLogger(), new Headers().withEntries(
            new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
            new Header("headerTwoName", "headerTwoValue")
        ))).matches(
            null,
            new Headers().withEntries(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("INCORRECTheaderTwoName", "headerTwoValue")
            )
        ));
    }

    @Test
    public void shouldNotMatchIncorrectHeaderValue() {
        assertFalse(new MultiValueMapMatcher(new MockServerLogger(), new Headers().withEntries(
            new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
            new Header("headerTwoName", "headerTwoValue")
        )).matches(
            null,
            new Headers().withEntries(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("headerTwoName", "INCORRECTheaderTwoValue")
            )
        ));
    }

    @Test
    public void shouldMatchIncorrectHeaderValueWhenNotApplied() {
        assertTrue(NotMatcher.not(new MultiValueMapMatcher(new MockServerLogger(), new Headers().withEntries(
            new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
            new Header("headerTwoName", "headerTwoValue")
        ))).matches(
            null,
            new Headers().withEntries(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("headerTwoName", "INCORRECTheaderTwoValue")
            )
        ));
    }

    @Test
    public void shouldNotMatchIncorrectHeaderNameAndValue() {
        assertFalse(new MultiValueMapMatcher(new MockServerLogger(), new Headers().withEntries(
            new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
            new Header("headerTwoName", "headerTwoValue")
        )).matches(
            null,
            new Headers().withEntries(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("INCORRECTheaderTwoName", "INCORRECTheaderTwoValue")
            )
        ));
    }

    @Test
    public void shouldMatchIncorrectHeaderNameAndValueWhenNotApplied() {
        assertTrue(NotMatcher.not(new MultiValueMapMatcher(new MockServerLogger(), new Headers().withEntries(
            new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
            new Header("headerTwoName", "headerTwoValue")
        ))).matches(
            null,
            new Headers().withEntries(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("INCORRECTheaderTwoName", "INCORRECTheaderTwoValue")
            )
        ));
    }

    @Test
    public void shouldMatchNullHeaderValue() {
        assertTrue(new MultiValueMapMatcher(new MockServerLogger(), new Headers().withEntries(
            new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
            new Header("headerTwoName", "headerTwoValue")
        )).matches(
            null,
            new Headers().withEntries(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("headerTwoName")
            )
        ));
    }

    @Test
    public void shouldNotMatchNullHeaderValueWhenNotApplied() {
        assertFalse(NotMatcher.not(new MultiValueMapMatcher(new MockServerLogger(), new Headers().withEntries(
            new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
            new Header("headerTwoName", "headerTwoValue")
        ))).matches(
            null,
            new Headers().withEntries(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("headerTwoName")
            )
        ));
    }

    @Test
    public void shouldMatchNullHeaderValueInExpectation() {
        assertTrue(new MultiValueMapMatcher(new MockServerLogger(), new Headers().withEntries(
            new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
            new Header("headerTwoName", "")
        )).matches(
            null,
            new Headers().withEntries(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
                new Header("headerTwoName", "headerTwoValue")
            )
        ));
    }

    @Test
    public void shouldNotMatchMissingHeader() {
        assertFalse(new MultiValueMapMatcher(new MockServerLogger(), new Headers().withEntries(
            new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
            new Header("headerTwoName", "headerTwoValue")
        )).matches(
            null,
            new Headers().withEntries(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo")
            )
        ));
    }

    @Test
    public void shouldMatchMissingHeaderWhenNotApplied() {
        assertTrue(NotMatcher.not(new MultiValueMapMatcher(new MockServerLogger(), new Headers().withEntries(
            new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo"),
            new Header("headerTwoName", "headerTwoValue")
        ))).matches(
            null,
            new Headers().withEntries(
                new Header("headerOneName", "headerOneValueOne", "headerOneValueTwo")
            )
        ));
    }

    @Test
    public void shouldMatchNullTest() {
        assertTrue(new MultiValueMapMatcher(new MockServerLogger(), new Headers()).matches(
            null,
            new Headers()
        ));
    }

    @Test
    public void shouldNotMatchNullTestWhenNotApplied() {
        assertFalse(NotMatcher.not(new MultiValueMapMatcher(new MockServerLogger(), new Headers())).matches(
            null,
            new Headers()
        ));
    }

    @Test
    public void shouldMatchEmptyTest() {
        assertTrue(new MultiValueMapMatcher(new MockServerLogger(), new Headers()).matches(
            null,
            new Headers()
        ));
    }

    @Test
    public void shouldNotMatchEmptyTestWhenNotApplied() {
        assertFalse(NotMatcher.not(new MultiValueMapMatcher(new MockServerLogger(), new Headers())).matches(
            null,
            new Headers()
        ));
    }
}
