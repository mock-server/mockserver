package org.mockserver.model;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.core.IsNull.nullValue;
import static org.mockserver.model.JsonRpcBody.jsonRpc;

public class JsonRpcBodyTest {

    @Test
    public void shouldReturnValuesFromStaticFactory() {
        // when
        JsonRpcBody body = jsonRpc("tools/list");

        // then
        assertThat(body.getMethod(), is("tools/list"));
        assertThat(body.getParamsSchema(), is(nullValue()));
        assertThat(body.getValue(), is("tools/list"));
        assertThat(body.getType(), is(Body.Type.JSON_RPC));
    }

    @Test
    public void shouldReturnValuesFromStaticFactoryWithParamsSchema() {
        // when
        JsonRpcBody body = jsonRpc("tools/call", "{\"type\": \"object\"}");

        // then
        assertThat(body.getMethod(), is("tools/call"));
        assertThat(body.getParamsSchema(), is("{\"type\": \"object\"}"));
        assertThat(body.getValue(), is("tools/call"));
        assertThat(body.getType(), is(Body.Type.JSON_RPC));
    }

    @Test
    public void shouldReturnValuesFromConstructor() {
        // when
        JsonRpcBody body = new JsonRpcBody("method_name");

        // then
        assertThat(body.getMethod(), is("method_name"));
        assertThat(body.getParamsSchema(), is(nullValue()));
        assertThat(body.getType(), is(Body.Type.JSON_RPC));
    }

    @Test
    public void shouldReturnValuesFromConstructorWithParamsSchema() {
        // when
        JsonRpcBody body = new JsonRpcBody("method_name", "{\"type\": \"string\"}");

        // then
        assertThat(body.getMethod(), is("method_name"));
        assertThat(body.getParamsSchema(), is("{\"type\": \"string\"}"));
        assertThat(body.getType(), is(Body.Type.JSON_RPC));
    }

    @Test
    public void shouldReturnMethodAsValue() {
        assertThat(jsonRpc("some_method").getValue(), is("some_method"));
    }

    @Test
    public void shouldReturnMethodAsToString() {
        assertThat(jsonRpc("some_method").toString(), is("some_method"));
    }

    @Test
    public void shouldSupportOptionalTrue() {
        // when
        Body<String> body = jsonRpc("method").withOptional(true);

        // then
        assertThat(body.getOptional(), is(true));
    }

    @Test
    public void shouldSupportOptionalFalse() {
        // when
        Body<String> body = jsonRpc("method").withOptional(false);

        // then
        assertThat(body.getOptional(), is(false));
    }

    @Test
    public void shouldHaveNullOptionalByDefault() {
        assertThat(jsonRpc("method").getOptional(), is(nullValue()));
    }

    @Test
    public void shouldSupportNotFlag() {
        // when
        JsonRpcBody body = Not.not(jsonRpc("method"));

        // then
        assertThat(body.isNot(), is(true));
    }

    @Test
    public void shouldBeEqualWhenSameMethodAndSchema() {
        // given
        JsonRpcBody bodyOne = jsonRpc("tools/list", "{\"type\": \"object\"}");
        JsonRpcBody bodyTwo = jsonRpc("tools/list", "{\"type\": \"object\"}");

        // then
        assertThat(bodyOne, is(bodyTwo));
    }

    @Test
    public void shouldHaveSameHashCodeWhenEqual() {
        // given
        JsonRpcBody bodyOne = jsonRpc("tools/list", "{\"type\": \"object\"}");
        JsonRpcBody bodyTwo = jsonRpc("tools/list", "{\"type\": \"object\"}");

        // then
        assertThat(bodyOne.hashCode(), is(bodyTwo.hashCode()));
    }

    @Test
    public void shouldBeEqualWhenSameMethodAndNullSchema() {
        // given
        JsonRpcBody bodyOne = jsonRpc("tools/list");
        JsonRpcBody bodyTwo = jsonRpc("tools/list");

        // then
        assertThat(bodyOne, is(bodyTwo));
    }

    @Test
    public void shouldNotBeEqualWhenDifferentMethod() {
        assertThat(jsonRpc("method_a"), is(not(jsonRpc("method_b"))));
    }

    @Test
    public void shouldNotBeEqualWhenDifferentSchema() {
        assertThat(
            jsonRpc("method", "{\"type\": \"object\"}"),
            is(not(jsonRpc("method", "{\"type\": \"string\"}")))
        );
    }

    @Test
    public void shouldNotBeEqualWhenOneHasSchemaAndOtherDoesNot() {
        assertThat(
            jsonRpc("method", "{\"type\": \"object\"}"),
            is(not(jsonRpc("method")))
        );
    }

    @Test
    public void shouldNotBeEqualToNull() {
        assertThat(jsonRpc("method").equals(null), is(false));
    }

    @Test
    public void shouldNotBeEqualToDifferentType() {
        assertThat(jsonRpc("method").equals("method"), is(false));
    }

    @Test
    public void shouldBeEqualToItself() {
        // given
        JsonRpcBody body = jsonRpc("method");

        // then
        assertThat(body, is(body));
    }

    @Test
    public void shouldNotBeEqualWhenDifferentOptional() {
        assertThat(
            jsonRpc("method").withOptional(true),
            is(not(jsonRpc("method").withOptional(false)))
        );
    }

    @Test
    public void shouldNotBeEqualWhenDifferentNotFlag() {
        assertThat(
            Not.not(jsonRpc("method")),
            is(not(jsonRpc("method")))
        );
    }
}
