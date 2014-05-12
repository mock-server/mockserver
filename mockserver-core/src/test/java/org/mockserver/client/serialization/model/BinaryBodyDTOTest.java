package org.mockserver.client.serialization.model;

import org.junit.Test;
import org.mockserver.model.BinaryBody;
import org.mockserver.model.Body;

import javax.xml.bind.DatatypeConverter;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

/**
 * @author jamesdbloom
 */
public class BinaryBodyDTOTest {

    @Test
    public void shouldReturnValueSetInConstructor() {
        // given
        byte[] body = DatatypeConverter.parseBase64Binary("some_body");

        // when
        BinaryBodyDTO binaryBody = new BinaryBodyDTO(new BinaryBody(body));

        // then
        assertThat(binaryBody.getValue(), is(DatatypeConverter.printBase64Binary(body)));
        assertThat(binaryBody.getType(), is(Body.Type.BINARY));
    }


    @Test
    public void shouldBuildCorrectObject() {
        // given
        byte[] body = DatatypeConverter.parseBase64Binary("some_body");

        // when
        BinaryBody binaryBody = new BinaryBodyDTO(new BinaryBody(body)).buildObject();

        // then
        assertThat(binaryBody.getValue(), is(body));
        assertThat(binaryBody.getType(), is(Body.Type.BINARY));
    }

    @Test
    public void coverage(){
        new BinaryBodyDTO();
    }
}
