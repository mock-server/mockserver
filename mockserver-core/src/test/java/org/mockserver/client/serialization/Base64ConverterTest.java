package org.mockserver.client.serialization;

import org.junit.Test;

import javax.xml.bind.DatatypeConverter;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author jamesdbloom
 */
public class Base64ConverterTest {

    @Test
    public void shouldConvertToBase64Value() {
        assertThat(new Base64Converter().stringToBase64Bytes("some_value".getBytes()), is(DatatypeConverter.printBase64Binary("some_value".getBytes())));
    }

    @Test
    public void shouldConvertBase64ValueToString() {
        assertThat(new String(Base64Converter.base64StringToBytes(DatatypeConverter.printBase64Binary("some_value".getBytes()))), is("some_value"));
    }

    @Test
    public void shouldNotConvertNoneBase64Value() {
        assertThat(new String(Base64Converter.base64StringToBytes("some_value")), is("some_value"));
    }
}
