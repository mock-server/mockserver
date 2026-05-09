package org.mockserver.serialization.model;

import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.mockserver.model.HttpResponseModifier;
import org.mockserver.model.HttpTemplate;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.Delay.delay;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpResponseModifier.responseModifier;

/**
 * @author jamesdbloom
 */
public class HttpTemplateDTOTest {

    @Test
    public void shouldReturnValuesSetInConstructor() {
        // given
        HttpTemplate.TemplateType type = HttpTemplate.TemplateType.VELOCITY;

        HttpTemplate httpTemplate = new HttpTemplate(type);

        // when
        HttpTemplateDTO httpTemplateDTO = new HttpTemplateDTO(httpTemplate);

        // then
        assertThat(httpTemplateDTO.getTemplateType(), is(type));
    }

    @Test
    public void shouldBuildObject() {
        // given
        String template = "some_random_template";

        HttpTemplate httpTemplate = new HttpTemplate(HttpTemplate.TemplateType.JAVASCRIPT)
                .withTemplate(template)
                .withDelay(SECONDS, 5);

        // when
        HttpTemplate builtHttpTemplate = new HttpTemplateDTO(httpTemplate).buildObject();

        // then
        assertThat(builtHttpTemplate.getTemplate(), is(template));
        assertThat(builtHttpTemplate.getDelay(), is(delay(SECONDS, 5)));
    }

    @Test
    public void shouldReturnValuesSetInSetter() {
        // given
        String template = "some_random_template";

        HttpTemplate httpTemplate = new HttpTemplate(HttpTemplate.TemplateType.VELOCITY);

        // when
        HttpTemplateDTO httpTemplateDTO = new HttpTemplateDTO(httpTemplate);
        httpTemplateDTO.setTemplate(template);
        httpTemplateDTO.setDelay(new DelayDTO(delay(SECONDS, 5)));

        // then
        assertThat(httpTemplateDTO.getTemplate(), is(template));
    }

    @Test
    public void shouldBuildObjectWithResponseOverrideAndModifier() {
        // given
        HttpResponseModifier httpResponseModifier = responseModifier().withHeaders(ImmutableList.of(), ImmutableList.of(), null);

        HttpTemplate httpTemplate = new HttpTemplate(HttpTemplate.TemplateType.JAVASCRIPT)
                .withTemplate("some_template")
                .withResponseOverride(response().withStatusCode(201))
                .withResponseModifier(httpResponseModifier);

        // when
        HttpTemplate builtHttpTemplate = new HttpTemplateDTO(httpTemplate).buildObject();

        // then
        assertThat(builtHttpTemplate.getTemplate(), is("some_template"));
        assertThat(builtHttpTemplate.getResponseOverride(), is(response().withStatusCode(201)));
        assertThat(builtHttpTemplate.getResponseModifier(), is(httpResponseModifier));
    }

    @Test
    public void shouldHandleNullObjectInput() {
        // when
        HttpTemplateDTO httpTemplateDTO = new HttpTemplateDTO(null);

        // then
        assertThat(httpTemplateDTO.getTemplate(), is(nullValue()));
    }
}