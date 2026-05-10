package org.mockserver.serialization.model;

import org.junit.Test;
import org.mockserver.model.HttpForward;
import org.mockserver.model.HttpForwardValidateAction;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.HttpForwardValidateAction.forwardValidate;

public class HttpForwardValidateActionDTOTest {

    @Test
    public void shouldBuildObjectFromDTO() {
        HttpForwardValidateActionDTO dto = new HttpForwardValidateActionDTO()
            .setSpecUrlOrPayload("someSpec")
            .setHost("someHost")
            .setPort(9090)
            .setScheme(HttpForward.Scheme.HTTPS)
            .setValidateRequest(false)
            .setValidateResponse(true)
            .setValidationMode(HttpForwardValidateAction.ValidationMode.LOG_ONLY);

        HttpForwardValidateAction action = dto.buildObject();

        assertThat(action.getSpecUrlOrPayload(), is("someSpec"));
        assertThat(action.getHost(), is("someHost"));
        assertThat(action.getPort(), is(9090));
        assertThat(action.getScheme(), is(HttpForward.Scheme.HTTPS));
        assertThat(action.getValidateRequest(), is(false));
        assertThat(action.getValidateResponse(), is(true));
        assertThat(action.getValidationMode(), is(HttpForwardValidateAction.ValidationMode.LOG_ONLY));
    }

    @Test
    public void shouldBuildDTOFromModel() {
        HttpForwardValidateAction action = forwardValidate()
            .withSpecUrlOrPayload("someSpec")
            .withHost("someHost")
            .withPort(9090)
            .withScheme(HttpForward.Scheme.HTTPS)
            .withValidateRequest(false)
            .withValidateResponse(true)
            .withValidationMode(HttpForwardValidateAction.ValidationMode.LOG_ONLY);

        HttpForwardValidateActionDTO dto = new HttpForwardValidateActionDTO(action);

        assertThat(dto.getSpecUrlOrPayload(), is("someSpec"));
        assertThat(dto.getHost(), is("someHost"));
        assertThat(dto.getPort(), is(9090));
        assertThat(dto.getScheme(), is(HttpForward.Scheme.HTTPS));
        assertThat(dto.getValidateRequest(), is(false));
        assertThat(dto.getValidateResponse(), is(true));
        assertThat(dto.getValidationMode(), is(HttpForwardValidateAction.ValidationMode.LOG_ONLY));
    }

    @Test
    public void shouldUseDefaultsWhenNullValues() {
        HttpForwardValidateActionDTO dto = new HttpForwardValidateActionDTO();
        HttpForwardValidateAction action = dto.buildObject();

        assertThat(action.getPort(), is(80));
        assertThat(action.getScheme(), is(HttpForward.Scheme.HTTP));
        assertThat(action.getValidateRequest(), is(true));
        assertThat(action.getValidateResponse(), is(true));
        assertThat(action.getValidationMode(), is(HttpForwardValidateAction.ValidationMode.STRICT));
    }

    @Test
    public void shouldHandleNullModelInConstructor() {
        HttpForwardValidateActionDTO dto = new HttpForwardValidateActionDTO(null);

        assertThat(dto.getSpecUrlOrPayload() == null, is(true));
        assertThat(dto.getHost() == null, is(true));
    }

    @Test
    public void shouldRoundTripThroughDTO() {
        HttpForwardValidateAction original = forwardValidate()
            .withSpecUrlOrPayload("roundTripSpec")
            .withHost("roundTripHost")
            .withPort(443)
            .withScheme(HttpForward.Scheme.HTTPS)
            .withValidateRequest(true)
            .withValidateResponse(false)
            .withValidationMode(HttpForwardValidateAction.ValidationMode.STRICT);

        HttpForwardValidateActionDTO dto = new HttpForwardValidateActionDTO(original);
        HttpForwardValidateAction rebuilt = dto.buildObject();

        assertThat(rebuilt.getSpecUrlOrPayload(), is(original.getSpecUrlOrPayload()));
        assertThat(rebuilt.getHost(), is(original.getHost()));
        assertThat(rebuilt.getPort(), is(original.getPort()));
        assertThat(rebuilt.getScheme(), is(original.getScheme()));
        assertThat(rebuilt.getValidateRequest(), is(original.getValidateRequest()));
        assertThat(rebuilt.getValidateResponse(), is(original.getValidateResponse()));
        assertThat(rebuilt.getValidationMode(), is(original.getValidationMode()));
    }
}
