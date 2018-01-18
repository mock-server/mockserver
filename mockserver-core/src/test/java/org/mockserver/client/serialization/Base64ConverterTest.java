package org.mockserver.client.serialization;

import org.junit.Test;

import javax.xml.bind.DatatypeConverter;

import static com.google.common.base.Charsets.UTF_8;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author jamesdbloom
 */
public class Base64ConverterTest {

    private final Base64Converter base64Converter = new Base64Converter();

    @Test
    public void shouldConvertToBase64Value() {
        assertThat(base64Converter.bytesToBase64String("some_value".getBytes(UTF_8)), is(DatatypeConverter.printBase64Binary("some_value".getBytes(UTF_8))));
        assertThat(base64Converter.bytesToBase64String("some_value".getBytes(UTF_8)), is("c29tZV92YWx1ZQ=="));
    }

    @Test
    public void shouldConvertBase64ValueToString() {
        assertThat(new String(base64Converter.base64StringToBytes(DatatypeConverter.printBase64Binary("some_value".getBytes(UTF_8)))), is("some_value"));
        assertThat(base64Converter.base64StringToBytes("c29tZV92YWx1ZQ=="), is("some_value".getBytes(UTF_8)));
    }

    @Test
    public void shouldConvertBase64NullValueToString() {
        assertThat(new String(base64Converter.base64StringToBytes(null)), is(""));
    }

    @Test
    public void shouldNotConvertNoneBase64Value() {
        assertThat(new String(base64Converter.base64StringToBytes("some_value")), is("some_value"));
    }
}
