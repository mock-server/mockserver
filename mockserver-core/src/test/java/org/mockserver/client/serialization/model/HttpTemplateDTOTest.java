package org.mockserver.client.serialization.model;

import org.junit.Test;
import org.mockserver.model.Delay;
import org.mockserver.model.HttpTemplate;

import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static org.mockserver.model.Delay.delay;

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
    public void shouldHandleNullObjectInput() {
        // when
        HttpTemplateDTO httpTemplateDTO = new HttpTemplateDTO(null);

        // then
        assertThat(httpTemplateDTO.getTemplate(), is(nullValue()));
    }
}