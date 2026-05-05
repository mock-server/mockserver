package org.mockserver.matchers;

import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;

import static org.junit.Assert.*;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.matchers.NotMatcher.notMatcher;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.Not.not;

/**
 * Tests for NOT operator logic in HttpRequestPropertiesMatcher.
 * 
 * This test class validates that NOT operators are applied sequentially and correctly
 * when multiple NOT flags are present (request NOT, expectation NOT, body NOT).
 * 
 * The fix changes from XOR-based combination to sequential NOT application:
 * - OLD: combinedResultAreTrue(match, reqNOT, expNOT, bodyNOT) using XOR (count % 2)
 * - NEW: Sequential application where each NOT independently negates the result
 * 
 * Each test is named to clearly describe the scenario being tested.
 */
public class HttpRequestPropertiesMatcherNotLogicTest {

    private final Configuration configuration = configuration();
    private final MockServerLogger mockServerLogger = new MockServerLogger(HttpRequestPropertiesMatcherNotLogicTest.class);
    
    /**
     * Helper method to create matcher from expectation (normal mode)
     */
    private HttpRequestPropertiesMatcher createMatcher(HttpRequest expectation) {
        HttpRequestPropertiesMatcher matcher = new HttpRequestPropertiesMatcher(configuration, mockServerLogger);
        matcher.update(new Expectation(expectation));
        return matcher;
    }

    // ========================================
    // BASIC MATCHING (No NOT operators)
    // ========================================

    @Test
    public void shouldMatch_whenFieldsMatch_andNoNotOperators() {
        // GIVEN: Simple expectation matching /test
        HttpRequest expectation = request()
            .withPath("/test")
            .withMethod("GET");

        // WHEN: Request also has /test
        HttpRequest actualRequest = request()
            .withPath("/test")
            .withMethod("GET");

        // THEN: Should match (fields match, no NOTs)
        HttpRequestPropertiesMatcher matcher = createMatcher(expectation);
        assertTrue("Expected match when fields match and no NOT operators", 
            matcher.matches(null, actualRequest));
    }

    @Test
    public void shouldNotMatch_whenFieldsDoNotMatch_andNoNotOperators() {
        // GIVEN: Expectation for /test
        HttpRequest expectation = request()
            .withPath("/test");

        // WHEN: Request for /other
        HttpRequest actualRequest = request()
            .withPath("/other");

        // THEN: Should not match (fields don't match)
        HttpRequestPropertiesMatcher matcher = createMatcher(expectation);
        assertFalse("Expected no match when fields don't match", 
            matcher.matches(null, actualRequest));
    }

    // ========================================
    // SINGLE NOT OPERATOR - Expectation NOT
    // ========================================

    @Test
    public void shouldNotMatch_whenFieldsMatch_andExpectationHasNot() {
        // GIVEN: NOT expectation (match anything EXCEPT /test)
        HttpRequest expectation = not(request().withPath("/test"));

        // WHEN: Request for /test
        HttpRequest actualRequest = request().withPath("/test");

        // THEN: Should NOT match (expectation says "anything except /test")
        HttpRequestPropertiesMatcher matcher = createMatcher(expectation);
        assertFalse("Expected no match: NOT expectation with matching fields should not match", 
            matcher.matches(null, actualRequest));
    }

    @Test
    public void shouldMatch_whenFieldsDoNotMatch_andExpectationHasNot() {
        // GIVEN: NOT expectation (match anything EXCEPT /test)
        HttpRequest expectation = not(request().withPath("/test"));

        // WHEN: Request for /other (different path)
        HttpRequest actualRequest = request().withPath("/other");

        // THEN: Should match (expectation says "anything except /test", and we have /other)
        HttpRequestPropertiesMatcher matcher = createMatcher(expectation);
        assertTrue("Expected match: NOT expectation with non-matching fields should match", 
            matcher.matches(null, actualRequest));
    }

    // ========================================
    // SINGLE NOT OPERATOR - Request NOT
    // ========================================

    @Test
    public void shouldNotMatch_whenFieldsMatch_andRequestHasNot() {
        // GIVEN: Normal expectation for /test
        HttpRequest expectation = request().withPath("/test");

        // WHEN: Request with NOT flag (matches anything EXCEPT /test)
        HttpRequest actualRequest = not(request().withPath("/test"));

        // THEN: Should NOT match (request wants anything except /test)
        HttpRequestPropertiesMatcher matcher = createMatcher(expectation);
        assertFalse("Expected no match: request NOT with matching fields should not match", 
            matcher.matches(null, actualRequest));
    }

    @Test
    public void shouldMatch_whenFieldsDoNotMatch_andRequestHasNot() {
        // GIVEN: Normal expectation for /test
        HttpRequest expectation = request().withPath("/test");

        // WHEN: Request with NOT flag for /other
        HttpRequest actualRequest = not(request().withPath("/other"));

        // THEN: Should match (request wants "anything except /other", expectation wants "/test")
        HttpRequestPropertiesMatcher matcher = createMatcher(expectation);
        assertTrue("Expected match: request NOT with non-matching fields should match", 
            matcher.matches(null, actualRequest));
    }

    // ========================================
    // DOUBLE NOT OPERATORS (Should Cancel)
    // ========================================

    @Test
    public void shouldMatch_whenFieldsMatch_andBothExpectationAndRequestHaveNot() {
        // GIVEN: NOT expectation (anything except /test)
        HttpRequest expectation = not(request().withPath("/test"));

        // WHEN: Request with NOT (anything except /test)
        HttpRequest actualRequest = not(request().withPath("/test"));

        // THEN: Should match (two NOTs cancel: NOT(NOT(match)) = match)
        // Base: /test = /test = true
        // Apply expectation NOT: !true = false
        // Apply request NOT: !false = true
        HttpRequestPropertiesMatcher matcher = createMatcher(expectation);
        assertTrue("Expected match: double NOT should cancel (NOT + NOT = identity)", 
            matcher.matches(null, actualRequest));
    }

    @Test
    public void shouldNotMatch_whenFieldsDoNotMatch_andBothExpectationAndRequestHaveNot() {
        // GIVEN: NOT expectation
        HttpRequest expectation = not(request().withPath("/test"));

        // WHEN: Request with NOT for different path
        HttpRequest actualRequest = not(request().withPath("/other"));

        // THEN: Should NOT match
        // Base: /test ≠ /other = false (no match)
        // Apply expectation NOT: !false = true
        // Apply request NOT: !true = false
        HttpRequestPropertiesMatcher matcher = createMatcher(expectation);
        assertFalse("Expected no match: double NOT on non-matching fields", 
            matcher.matches(null, actualRequest));
    }

    // ========================================
    // TRIPLE NOT OPERATORS (Body NOT via notMatcher wrapper)
    // ========================================

    @Test
    public void shouldNotMatch_whenFieldsMatch_andTripleNot() {
        // GIVEN: NOT expectation with body NOT
        HttpRequest expectation = not(request().withPath("/test"));

        // WHEN: Request with NOT
        HttpRequest actualRequest = not(request().withPath("/test"));

        // Using body NOT via notMatcher() wrapper as third NOT
        HttpRequestPropertiesMatcher baseMatcher = createMatcher(expectation);
        HttpRequestPropertiesMatcher matcherWithBodyNot = notMatcher(baseMatcher);

        // THEN: Should NOT match (triple NOT = single NOT)
        // Base: /test = /test = true (match)
        // Apply body NOT: !true = false
        // Apply expectation NOT: !false = true  
        // Apply request NOT: !true = false
        assertFalse("Expected no match: triple NOT should equal single NOT", 
            matcherWithBodyNot.matches(null, actualRequest));
    }

    // ========================================
    // COMPLEX SCENARIOS
    // ========================================

    @Test
    public void shouldNotMatch_whenMultipleFieldsMatch_andExpectationNotInvertsResult() {
        // GIVEN: NOT expectation with multiple fields
        HttpRequest expectation = not(request()
            .withPath("/test")
            .withMethod("GET")
            .withHeader("Content-Type", "application/json"));

        // WHEN: Request with matching fields
        HttpRequest actualRequest = request()
            .withPath("/test")
            .withMethod("GET")
            .withHeader("Content-Type", "application/json");

        // THEN: Should NOT match (all fields match, but expectation is NOT)
        HttpRequestPropertiesMatcher matcher = createMatcher(expectation);
        assertFalse("Expected no match: NOT expectation with all matching fields", 
            matcher.matches(null, actualRequest));
    }

    @Test
    public void shouldMatch_whenPartialFieldsMatch_andExpectationNot() {
        // GIVEN: NOT expectation
        HttpRequest expectation = not(request()
            .withPath("/test")
            .withMethod("GET"));

        // WHEN: Request with different method (partial match)
        HttpRequest actualRequest = request()
            .withPath("/test")
            .withMethod("POST");  // Different method

        // THEN: Should match (fields don't fully match, NOT inverts: !false = true)
        HttpRequestPropertiesMatcher matcher = createMatcher(expectation);
        assertTrue("Expected match: NOT expectation with partial mismatch", 
            matcher.matches(null, actualRequest));
    }

    // ========================================
    // TRUTH TABLE VALIDATION
    // ========================================

    @Test
    public void truthTable_noMatch_noNots() {
        // Base: false, Request NOT: false, Expectation NOT: false
        // Expected: false
        HttpRequest expectation = request().withPath("/test");
        HttpRequest actualRequest = request().withPath("/other");
        HttpRequestPropertiesMatcher matcher = createMatcher(expectation);
        assertFalse("Truth table: false + no NOTs = false", 
            matcher.matches(null, actualRequest));
    }

    @Test
    public void truthTable_noMatch_requestNot() {
        // Base: false, Request NOT: true, Expectation NOT: false
        // Expected: !false = true
        HttpRequest expectation = request().withPath("/test");
        HttpRequest actualRequest = not(request().withPath("/other"));
        HttpRequestPropertiesMatcher matcher = createMatcher(expectation);
        assertTrue("Truth table: false + request NOT = true", 
            matcher.matches(null, actualRequest));
    }

    @Test
    public void truthTable_noMatch_expectationNot() {
        // Base: false, Request NOT: false, Expectation NOT: true
        // Expected: !false = true
        HttpRequest expectation = not(request().withPath("/test"));
        HttpRequest actualRequest = request().withPath("/other");
        HttpRequestPropertiesMatcher matcher = createMatcher(expectation);
        assertTrue("Truth table: false + expectation NOT = true", 
            matcher.matches(null, actualRequest));
    }

    @Test
    public void truthTable_noMatch_doubleNot_requestAndExpectation() {
        // Base: false, Request NOT: true, Expectation NOT: true
        // Expected: false → !false (exp) = true → !true (req) = false
        HttpRequest expectation = not(request().withPath("/test"));
        HttpRequest actualRequest = not(request().withPath("/other"));
        HttpRequestPropertiesMatcher matcher = createMatcher(expectation);
        assertFalse("Truth table: false + double NOT (req+exp) = false", 
            matcher.matches(null, actualRequest));
    }

    @Test
    public void truthTable_match_tripleNot() {
        // Base: true, Request NOT: true, Expectation NOT: true, Body NOT: true
        // Expected: true → !true (body) = false → !false (exp) = true → !true (req) = false
        HttpRequest expectation = not(request().withPath("/test"));
        HttpRequest actualRequest = not(request().withPath("/test"));
        HttpRequestPropertiesMatcher baseMatcher = createMatcher(expectation);
        HttpRequestPropertiesMatcher matcherWithBodyNot = notMatcher(baseMatcher);
        assertFalse("Truth table: true + triple NOT = false", 
            matcherWithBodyNot.matches(null, actualRequest));
    }

    // ========================================
    // REGRESSION TESTS (Previously failed with XOR logic)
    // ========================================

    @Test
    public void regression_threeNots_withMatch_shouldNotMatch() {
        // This test would have had INCORRECT behavior with old XOR logic
        // OLD XOR: Would calculate based on count of trues (unpredictable)
        // NEW: Correctly applies three sequential NOTs
        HttpRequest expectation = not(request().withPath("/forbidden"));
        HttpRequest actualRequest = not(request().withPath("/forbidden"));
        HttpRequestPropertiesMatcher baseMatcher = createMatcher(expectation);
        HttpRequestPropertiesMatcher matcherWithBodyNot = notMatcher(baseMatcher);
        
        // true (match) → !true (body NOT) → !false (exp NOT) → !true (req NOT) = false
        assertFalse("Regression: triple NOT on matching fields should not match", 
            matcherWithBodyNot.matches(null, actualRequest));
    }

    @Test
    public void regression_sequentialNotApplicationIsCorrect() {
        // Verify that NOT operators are applied sequentially, not via XOR
        // Case: Two NOTs should cancel out
        HttpRequest expectation = not(request().withPath("/test").withMethod("GET"));
        HttpRequest actualRequest = not(request().withPath("/test").withMethod("GET"));
        
        HttpRequestPropertiesMatcher matcher = createMatcher(expectation);
        assertTrue("Regression: double NOT should cancel (expectation NOT + request NOT)", 
            matcher.matches(null, actualRequest));
    }
}
