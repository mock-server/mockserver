package org.mockserver.proxy.http;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.ssl.SslHandler;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class HttpProxyUnificationHandlerSslErrorsTest {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    /*
    Format of an SSL record
    Byte   0       =                SSL record type
    Bytes 1-2      =                SSL version (major/minor)
    Bytes 3-4      =                Length of data in the record (excluding the header itself).
                                    The maximum SSL supports is 16384 (16K).

    Byte 0 in the record has the following record type values:
    SSL3_RT_CHANGE_CIPHER_SPEC      20   (x'14')
    SSL3_RT_ALERT                   21   (x'15')
    SSL3_RT_HANDSHAKE               22   (x'16')
    SSL3_RT_APPLICATION_DATA        23   (x'17')

    Bytes 1-2 in the record have the following version values:
    SSL3_VERSION                    x'0300'
    TLS1_VERSION                    x'0301'
    TLS2_VERSION                    x'0302'
    TLS3_VERSION                    x'0303'

    Format of an SSL handshake record
    Byte   0       =                SSL record type = 22 (SSL3_RT_HANDSHAKE)
    Bytes 1-2      =                SSL version (major/minor)
    Bytes 3-4      =                Length of data in the record (excluding the header itself).
    Byte   5       =                Handshake type
    Bytes 6-8      =                Length of data to follow in this record
    Bytes 9-n      =                Command-specific data

    Byte 5 in the record has the following handshake type values:
    SSL3_MT_HELLO_REQUEST            0   (x'00')
    SSL3_MT_CLIENT_HELLO             1   (x'01')
    SSL3_MT_SERVER_HELLO             2   (x'02')
    SSL3_MT_CERTIFICATE             11   (x'0B')
    SSL3_MT_SERVER_KEY_EXCHANGE     12   (x'0C')
    SSL3_MT_CERTIFICATE_REQUEST     13   (x'0D')
    SSL3_MT_SERVER_DONE             14   (x'0E')
    SSL3_MT_CERTIFICATE_VERIFY      15   (x'0F')
    SSL3_MT_CLIENT_KEY_EXCHANGE     16   (x'10')
    SSL3_MT_FINISHED                20   (x'14')

    The Change Cipher Spec type has the following value:
    SSL3_MT_CCS                     1

    Format of an SSL alert record
    SSL3_AL_WARNINGS                1
    SSL3_AL_FATAL                   2

    The following shows the alert values for SSL and TLS.
    Alerts that begin with SSL3 are supported by both SSL version 3 and TLS version 1.
    Alerts that begin with TLS1 are supported by TLS only.
    SSL3_AD_CLOSE_NOTIFY             0       x'00'
    SSL3_AD_UNEXPECTED_MESSAGE       10      x'0A'
    SSL3_AD_BAD_RECORD_MAC           20      x'14'
    TLS1_AD_DECRYPTION_FAILED        21      x'15'
    TLS1_AD_RECORD_OVERFLOW          22      x'16'
    SSL3_AD_DECOMPRESSION_FAILURE    30      x'1E'
    SSL3_AD_HANDSHAKE_FAILURE        40      x'28'
    SSL3_AD_NO_CERTIFICATE           41      x'29'
    SSL3_AD_BAD_CERTIFICATE          42      x'2A'
    SSL3_AD_UNSUPPORTED_CERTIFICATE  43      x'2B'
    SSL3_AD_CERTIFICATE_REVOKED      44      x'2C'
    SSL3_AD_CERTIFICATE_EXPIRED      45      x'2D'
    SSL3_AD_CERTIFICATE_UNKNOWN      46      x'2E'
    SSL3_AD_ILLEGAL_PARAMETER        47      x'2F'
    TLS1_AD_UNKNOWN_CA               48      x'30'
    TLS1_AD_ACCESS_DENIED            49      x'31'
    TLS1_AD_DECODE_ERROR             50      x'32'
    TLS1_AD_DECRYPT_ERROR            51      x'33'
    TLS1_AD_EXPORT_RESTRICTION       60      x'3C'
    TLS1_AD_PROTOCOL_VERSION         70      x'46'
    TLS1_AD_INSUFFICIENT_SECURITY    71      x'47'
    TLS1_AD_INTERNAL_ERROR           80      x'50'
    TLS1_AD_USER_CANCELLED           90      x'5A'
    TLS1_AD_NO_RENEGOTIATION         100     x'64'
    */

    @Test
    public void shouldHandleCloseDuringSslHandshake() {
        // then
        exception.expect(DecoderException.class);
        exception.expectMessage(containsString("ciphertext sanity check failed"));

        // given
        EmbeddedChannel embeddedChannel = new EmbeddedChannel(new HttpProxyUnificationHandler());

        // and - no SSL handler
        assertThat(embeddedChannel.pipeline().get(SslHandler.class), is(nullValue()));

        // when - first part of a 5-byte handshake message (SSL3_RT_HANDSHAKE)
        embeddedChannel.writeInbound(Unpooled.wrappedBuffer(new byte[]{
                (byte) 0x16 /* 0 */,                                             // handshake
                (byte) 0x03 /* 1 */, (byte) 0x01 /* 2 */,                        // version TLS1
                (byte) 0x00 /* 3 */, (byte) 0xE9 /* 4 */,                        // package length (233-byte)
                (byte) 0x01 /* 5 */,                                             // hello request
                (byte) 0x00 /* 6 */, (byte) 0x00 /* 7 */, (byte) 0xE5, /* 8 */   // length of data to follow (229-byte)

                (byte) 0x03, (byte) 0x03, (byte) 0xb9, (byte) 0xdb, (byte) 0x96, // random data generated for use in key generation
                (byte) 0x7f, (byte) 0x9f, (byte) 0x99, (byte) 0x36, (byte) 0x63,
                (byte) 0x01, (byte) 0x24, (byte) 0x31, (byte) 0x79, (byte) 0xbf,
                (byte) 0x82, (byte) 0xbc, (byte) 0xd4, (byte) 0xd0, (byte) 0xb2,
                (byte) 0xce, (byte) 0xc7, (byte) 0xab, (byte) 0xe1, (byte) 0x58,
                (byte) 0xb0, (byte) 0x39, (byte) 0x59, (byte) 0x98, (byte) 0x53,
                (byte) 0x52, (byte) 0x4a, (byte) 0x5a, (byte) 0xe7, (byte) 0x20,
                (byte) 0x55, (byte) 0x86, (byte) 0xfa, (byte) 0xe5, (byte) 0x0b,
                (byte) 0xec, (byte) 0x32, (byte) 0x12, (byte) 0x1c, (byte) 0xd0,
                (byte) 0x05, (byte) 0xcb, (byte) 0xcd, (byte) 0x58, (byte) 0xdd,
                (byte) 0x22, (byte) 0xfe, (byte) 0xf2, (byte) 0x61, (byte) 0x4a,
                (byte) 0x07, (byte) 0x14, (byte) 0x3a, (byte) 0x87, (byte) 0x24,
                (byte) 0x45, (byte) 0x5b, (byte) 0xad, (byte) 0x65, (byte) 0xd1,
                (byte) 0x75, (byte) 0xca, (byte) 0x00, (byte) 0x22, (byte) 0xc0,
                (byte) 0x2b, (byte) 0xc0, (byte) 0x2f, (byte) 0x00, (byte) 0x9e,
                (byte) 0xcc, (byte) 0x14, (byte) 0xcc, (byte) 0x13, (byte) 0xcc,
                (byte) 0x15, (byte) 0xc0, (byte) 0x0a, (byte) 0xc0, (byte) 0x14,
                (byte) 0x00, (byte) 0x39, (byte) 0xc0, (byte) 0x09, (byte) 0xc0,
                (byte) 0x13, (byte) 0x00, (byte) 0x33, (byte) 0x00, (byte) 0x9c,
                (byte) 0x00, (byte) 0x35, (byte) 0x00, (byte) 0x2f, (byte) 0x00,
                (byte) 0x0a, (byte) 0x00, (byte) 0xff, (byte) 0x01, (byte) 0x00,
                (byte) 0x00, (byte) 0x7a, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x0e, (byte) 0x00, (byte) 0x0c, (byte) 0x00, (byte) 0x00,
                (byte) 0x09, (byte) 0x6c, (byte) 0x6f, (byte) 0x63, (byte) 0x61,
                (byte) 0x6c, (byte) 0x68, (byte) 0x6f, (byte) 0x73, (byte) 0x74,
                (byte) 0x00, (byte) 0x17, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x23, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x0d,
                (byte) 0x00, (byte) 0x16, (byte) 0x00, (byte) 0x14, (byte) 0x06,
                (byte) 0x01, (byte) 0x06, (byte) 0x03, (byte) 0x05, (byte) 0x01,
                (byte) 0x05, (byte) 0x03, (byte) 0x04, (byte) 0x01, (byte) 0x04,
                (byte) 0x03, (byte) 0x03, (byte) 0x01, (byte) 0x03, (byte) 0x03,
                (byte) 0x02, (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x00,
                (byte) 0x05, (byte) 0x00, (byte) 0x05, (byte) 0x01, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x33, (byte) 0x74,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x12, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x10, (byte) 0x00, (byte) 0x1d,
                (byte) 0x00, (byte) 0x1b, (byte) 0x08, (byte) 0x68, (byte) 0x74,
                (byte) 0x74, (byte) 0x70, (byte) 0x2f, (byte) 0x31, (byte) 0x2e,
                (byte) 0x31, (byte) 0x08, (byte) 0x73, (byte) 0x70, (byte) 0x64,
                (byte) 0x79, (byte) 0x2f, (byte) 0x33, (byte) 0x2e, (byte) 0x31,
                (byte) 0x05, (byte) 0x68, (byte) 0x32, (byte) 0x2d, (byte) 0x31,
                (byte) 0x34, (byte) 0x02, (byte) 0x68, (byte) 0x32, (byte) 0x75,
                (byte) 0x50, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x0b,
                (byte) 0x00, (byte) 0x02, (byte) 0x01, (byte) 0x00, (byte) 0x00,
                (byte) 0x0a, (byte) 0x00, (byte) 0x06, (byte) 0x00, (byte) 0x04,
                (byte) 0x00, (byte) 0x17, (byte) 0x00, (byte) 0x18

        }));
        // and - second part (SSL3_MT_CLIENT_HELLO)
        embeddedChannel.writeInbound(Unpooled.wrappedBuffer(new byte[]{
                (byte) 0x16 /* 0 */,                                             // handshake
                (byte) 0x03 /* 1 */, (byte) 0x03 /* 2 */,                        // version TLS3
                (byte) 0x00 /* 3 */, (byte) 0x46 /* 4 */,                        // package length (46-byte)
                (byte) 0x10 /* 5 */,                                             // client key exchange
                (byte) 0x00 /* 6 */, (byte) 0x00 /* 7 */, (byte) 0x42, /* 8 */   // length of data to follow (42-byte)

                (byte) 0x41, (byte) 0x04, (byte) 0x31, (byte) 0x77, (byte) 0xfd, // pre-master secret created by the client &
                (byte) 0xbb, (byte) 0x54, (byte) 0x1c, (byte) 0x5d, (byte) 0xd9, // encrypted using the server's public key
                (byte) 0x60, (byte) 0x62, (byte) 0xb7, (byte) 0xbf, (byte) 0x0e,
                (byte) 0x2c, (byte) 0xdc, (byte) 0x60, (byte) 0x25, (byte) 0xb3,
                (byte) 0x52, (byte) 0x6a, (byte) 0x4d, (byte) 0xe1, (byte) 0x2d,
                (byte) 0x61, (byte) 0xdc, (byte) 0x4f, (byte) 0xc4, (byte) 0xc0,
                (byte) 0x2d, (byte) 0x8e, (byte) 0xe7, (byte) 0x28, (byte) 0xf7,
                (byte) 0xbf, (byte) 0x89, (byte) 0x64, (byte) 0x30, (byte) 0x22,
                (byte) 0x9f, (byte) 0xfb, (byte) 0x2b, (byte) 0xd8, (byte) 0xc6,
                (byte) 0x83, (byte) 0xa8, (byte) 0x7b, (byte) 0x7a, (byte) 0xc7,
                (byte) 0x2e, (byte) 0x28, (byte) 0x70, (byte) 0x20, (byte) 0x00,
                (byte) 0x76, (byte) 0xcd, (byte) 0x15, (byte) 0x13, (byte) 0x73,
                (byte) 0x7f, (byte) 0x7d, (byte) 0xb0, (byte) 0xc1, (byte) 0x19,
                (byte) 0x7d, (byte) 0x14, (byte) 0x03, (byte) 0x03, (byte) 0x00,
                (byte) 0x01, (byte) 0x01, (byte) 0x16, (byte) 0x03, (byte) 0x03,
                (byte) 0x00, (byte) 0x28, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0xeb, (byte) 0x66, (byte) 0x99, (byte) 0xbc, (byte) 0xdd,
                (byte) 0x2b, (byte) 0xbf, (byte) 0xc8, (byte) 0xc9, (byte) 0x2d,
                (byte) 0xca, (byte) 0x80, (byte) 0xf0, (byte) 0x76, (byte) 0x7e,
                (byte) 0x85, (byte) 0x46, (byte) 0xae, (byte) 0x35, (byte) 0x22,
                (byte) 0x91, (byte) 0x66, (byte) 0xcd, (byte) 0xb5, (byte) 0x77,
                (byte) 0x71, (byte) 0x2b, (byte) 0x79, (byte) 0xf2, (byte) 0xc7,
                (byte) 0x20, (byte) 0x39
        }));
        // add - channel is closed
        embeddedChannel.close();
    }

    @Test
    public void shouldHandleErrorDuringSslHandshake() {
        // then
        exception.expect(DecoderException.class);
        exception.expectMessage(containsString("Illegal server handshake"));

        // given
        EmbeddedChannel embeddedChannel = new EmbeddedChannel(new HttpProxyUnificationHandler());

        // and - no SSL handler
        assertThat(embeddedChannel.pipeline().get(SslHandler.class), is(nullValue()));

        // when - first part of a 5-byte handshake message
        embeddedChannel.writeInbound(Unpooled.wrappedBuffer(new byte[]{
                (byte) 0x16,                // handshake
                (byte) 0x03, (byte) 0x01,   // version TLS1
                (byte) 0x00, (byte) 0x05    // package length (5-byte)
        }));
        // and - invalid second part of the 5-byte handshake message
        embeddedChannel.writeInbound(Unpooled.wrappedBuffer(new byte[]{
                (byte) 0x17,                // application data
                (byte) 0x00, (byte) 0x00,   // invalid version
                (byte) 0x01, (byte) 0x00    // package length (256-byte)
        }));

        // and then
        assertThat(embeddedChannel.isOpen(), is(false));
    }

}