package org.mockserver.matchers;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;

import static junit.framework.TestCase.*;
import static org.mockserver.matchers.NotMatcher.notMatcher;

public class GraphQLMatcherTest {

    @Test
    public void shouldMatchSimpleGraphQLQuery() {
        assertTrue(new GraphQLMatcher(new MockServerLogger(), "{ user(id: 1) { name } }", null, null).matches(null, "{\"query\": \"{ user(id: 1) { name } }\"}"));
    }

    @Test
    public void shouldMatchQueryWithWhitespaceNormalization() {
        assertTrue(new GraphQLMatcher(new MockServerLogger(), "{ user(id: 1) { name email } }", null, null).matches(null, "{\"query\": \"{\\n  user(id: 1) {\\n    name\\n    email\\n  }\\n}\"}"));
        assertTrue(new GraphQLMatcher(new MockServerLogger(), "{\n  user(id: 1) {\n    name\n    email\n  }\n}", null, null).matches(null, "{\"query\": \"{ user(id: 1) { name email } }\"}"));
    }

    @Test
    public void shouldMatchQueryWithOperationName() {
        assertTrue(new GraphQLMatcher(new MockServerLogger(), "query GetUser { user(id: 1) { name } }", "GetUser", null).matches(null, "{\"query\": \"query GetUser { user(id: 1) { name } }\", \"operationName\": \"GetUser\"}"));
    }

    @Test
    public void shouldMatchQueryWithRegexOperationName() {
        assertTrue(new GraphQLMatcher(new MockServerLogger(), "query GetUser { user(id: 1) { name } }", "Get.*", null).matches(null, "{\"query\": \"query GetUser { user(id: 1) { name } }\", \"operationName\": \"GetUser\"}"));
        assertTrue(new GraphQLMatcher(new MockServerLogger(), "query GetUser { user(id: 1) { name } }", "Get(User|Account)", null).matches(null, "{\"query\": \"query GetUser { user(id: 1) { name } }\", \"operationName\": \"GetUser\"}"));
        assertFalse(new GraphQLMatcher(new MockServerLogger(), "query GetUser { user(id: 1) { name } }", "Delete.*", null).matches(null, "{\"query\": \"query GetUser { user(id: 1) { name } }\", \"operationName\": \"GetUser\"}"));
    }

    @Test
    public void shouldNotMatchWhenQueryDiffers() {
        assertFalse(new GraphQLMatcher(new MockServerLogger(), "{ user(id: 1) { name } }", null, null).matches(null, "{\"query\": \"{ user(id: 2) { email } }\"}"));
    }

    @Test
    public void shouldNotMatchWhenOperationNameDiffers() {
        assertFalse(new GraphQLMatcher(new MockServerLogger(), "query GetUser { user(id: 1) { name } }", "GetUser", null).matches(null, "{\"query\": \"query GetUser { user(id: 1) { name } }\", \"operationName\": \"ListUsers\"}"));
    }

    @Test
    public void shouldNotMatchInvalidJson() {
        assertFalse(new GraphQLMatcher(new MockServerLogger(), "{ user(id: 1) { name } }", null, null).matches(null, "{not valid json"));
    }

    @Test
    public void shouldNotMatchNullBody() {
        assertFalse(new GraphQLMatcher(new MockServerLogger(), "{ user(id: 1) { name } }", null, null).matches(null, null));
    }

    @Test
    public void shouldNotMatchEmptyBody() {
        assertFalse(new GraphQLMatcher(new MockServerLogger(), "{ user(id: 1) { name } }", null, null).matches(null, ""));
    }

    @Test
    public void shouldNotMatchMissingQueryField() {
        assertFalse(new GraphQLMatcher(new MockServerLogger(), "{ user(id: 1) { name } }", null, null).matches(null, "{\"operationName\": \"GetUser\"}"));
    }

    @Test
    public void shouldSupportNotMatcher() {
        assertTrue(notMatcher(new GraphQLMatcher(new MockServerLogger(), "{ user(id: 1) { name } }", null, null)).matches(null, "{\"query\": \"{ user(id: 2) { email } }\"}"));
        assertFalse(notMatcher(new GraphQLMatcher(new MockServerLogger(), "{ user(id: 1) { name } }", null, null)).matches(null, "{\"query\": \"{ user(id: 1) { name } }\"}"));
    }

    @Test
    public void shouldMatchWithoutOperationNameWhenNotRequired() {
        assertTrue(new GraphQLMatcher(new MockServerLogger(), "{ user(id: 1) { name } }", null, null).matches(null, "{\"query\": \"{ user(id: 1) { name } }\", \"operationName\": \"GetUser\"}"));
        assertTrue(new GraphQLMatcher(new MockServerLogger(), "{ user(id: 1) { name } }", null, null).matches(null, "{\"query\": \"{ user(id: 1) { name } }\"}"));
    }

    @Test
    public void shouldMatchQueryWithVariables() {
        assertTrue(new GraphQLMatcher(new MockServerLogger(), "query GetUser($id: ID!) { user(id: $id) { name } }", null, null).matches(null, "{\"query\": \"query GetUser($id: ID!) { user(id: $id) { name } }\", \"variables\": {\"id\": 1}}"));
    }

    @Test
    public void shouldMatchMutationQuery() {
        assertTrue(new GraphQLMatcher(new MockServerLogger(), "mutation CreateUser($name: String!) { createUser(name: $name) { id name } }", null, null).matches(null, "{\"query\": \"mutation CreateUser($name: String!) { createUser(name: $name) { id name } }\"}"));
    }

    @Test
    public void shouldMatchSubscriptionQuery() {
        assertTrue(new GraphQLMatcher(new MockServerLogger(), "subscription OnUserCreated { userCreated { id name } }", null, null).matches(null, "{\"query\": \"subscription OnUserCreated { userCreated { id name } }\"}"));
    }

    @Test
    public void shouldMatchQueryWithFragments() {
        String queryWithFragment = "query GetUser { user(id: 1) { ...UserFields } } fragment UserFields on User { name email }";
        assertTrue(new GraphQLMatcher(new MockServerLogger(), queryWithFragment, null, null).matches(null, "{\"query\": \"query GetUser { user(id: 1) { ...UserFields } } fragment UserFields on User { name email }\"}"));
    }

    @Test
    public void shouldMatchCompactPunctuationFormatting() {
        assertTrue(new GraphQLMatcher(new MockServerLogger(), "{ user { id } }", null, null).matches(null, "{\"query\": \"{user{id}}\"}"));
        assertTrue(new GraphQLMatcher(new MockServerLogger(), "{user{id}}", null, null).matches(null, "{\"query\": \"{ user { id } }\"}"));
    }

    @Test
    public void shouldPreserveStringLiteralWhitespace() {
        assertFalse(new GraphQLMatcher(new MockServerLogger(), "{ search(text: \"a b\") { id } }", null, null).matches(null, "{\"query\": \"{ search(text: \\\"a   b\\\") { id } }\"}"));
    }

    @Test
    public void shouldHandleCommentsInQuery() {
        assertTrue(new GraphQLMatcher(new MockServerLogger(), "{ user { name } }", null, null).matches(null, "{\"query\": \"# fetch user\\n{ user { name } }\"}"));
    }

    @Test
    public void shouldHandleCommasAsInsignificant() {
        assertTrue(new GraphQLMatcher(new MockServerLogger(), "{ user { name email } }", null, null).matches(null, "{\"query\": \"{ user { name, email } }\"}"));
    }

    @Test
    public void shouldMatchBlockStringLiterals() {
        String expected = "{ schema { description } }";
        assertTrue(new GraphQLMatcher(new MockServerLogger(), expected, null, null).matches(null, "{\"query\": \"{schema{description}}\"}"));
    }

    @Test
    public void shouldHandleEscapedCharactersInStrings() {
        String query = "{ search(text: \"hello\\\"world\") { id } }";
        assertTrue(new GraphQLMatcher(new MockServerLogger(), query, null, null).matches(null, "{\"query\": \"{ search(text: \\\"hello\\\\\\\"world\\\") { id } }\"}"));
    }

    @Test
    public void shouldValidateVariablesWithSchema() {
        String schema = "{\"type\": \"object\", \"properties\": {\"id\": {\"type\": \"integer\"}}, \"required\": [\"id\"]}";
        assertTrue(new GraphQLMatcher(new MockServerLogger(), "query GetUser($id: ID!) { user(id: $id) { name } }", null, schema).matches(null, "{\"query\": \"query GetUser($id: ID!) { user(id: $id) { name } }\", \"variables\": {\"id\": 1}}"));
    }

    @Test
    public void shouldRejectInvalidVariablesWithSchema() {
        String schema = "{\"type\": \"object\", \"properties\": {\"id\": {\"type\": \"integer\"}}, \"required\": [\"id\"]}";
        assertFalse(new GraphQLMatcher(new MockServerLogger(), "query GetUser($id: ID!) { user(id: $id) { name } }", null, schema).matches(null, "{\"query\": \"query GetUser($id: ID!) { user(id: $id) { name } }\", \"variables\": {\"name\": \"test\"}}"));
    }

    @Test
    public void shouldRejectMissingVariablesWhenSchemaRequired() {
        String schema = "{\"type\": \"object\", \"properties\": {\"id\": {\"type\": \"integer\"}}, \"required\": [\"id\"]}";
        assertFalse(new GraphQLMatcher(new MockServerLogger(), "query GetUser($id: ID!) { user(id: $id) { name } }", null, schema).matches(null, "{\"query\": \"query GetUser($id: ID!) { user(id: $id) { name } }\"}"));
    }

    @Test
    public void shouldAcceptNullVariablesWhenNoSchema() {
        assertTrue(new GraphQLMatcher(new MockServerLogger(), "{ user { name } }", null, null).matches(null, "{\"query\": \"{ user { name } }\", \"variables\": null}"));
    }

    @Test
    public void shouldNormalizeQueryCorrectly() {
        assertEquals("{user{id}}", GraphQLMatcher.normalizeQuery("{ user { id } }"));
        assertEquals("{user{id}}", GraphQLMatcher.normalizeQuery("{user{id}}"));
        assertEquals("{user{id}}", GraphQLMatcher.normalizeQuery("  {  user  {  id  }  }  "));
        assertEquals("query GetUser{user(id:1){name email}}", GraphQLMatcher.normalizeQuery("query GetUser { user(id: 1) { name email } }"));
        assertEquals("{search(text:\"a b\"){id}}", GraphQLMatcher.normalizeQuery("{ search(text: \"a b\") { id } }"));
    }
}
