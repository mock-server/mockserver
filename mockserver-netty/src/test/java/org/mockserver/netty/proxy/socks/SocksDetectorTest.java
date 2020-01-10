package org.mockserver.netty.proxy.socks;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SocksDetectorTest {

    @Test
    public void successfullyParseSocks4ConnectRequest() {
        ByteBuf msg = Unpooled.wrappedBuffer(new byte[]{
            0x04, // protocol version
            0x01, // command code
            0x00, 0x00, // port
            0x06, 0x06, 0x06, 0x06,  // ip
            'f', 'o', 'o', 0x00 // username
        });
        assertTrue(SocksDetector.isSocks4(msg, msg.readableBytes()));
    }

    @Test
    public void successfullyParseSocks4BindRequest() {
        ByteBuf msg = Unpooled.wrappedBuffer(new byte[]{
            0x04, // protocol version
            0x02, // command code
            0x00, 0x00, // port
            0x06, 0x06, 0x06, 0x06,  // ip
            'f', 'o', 'o', 0x00 // username
        });
        assertTrue(SocksDetector.isSocks4(msg, msg.readableBytes()));
    }

    @Test
    public void successfullyParseSocks4RequestWithEmptyUsername() {
        ByteBuf msg = Unpooled.wrappedBuffer(new byte[]{
            0x04, // protocol version
            0x01, // command code
            0x00, 0x00, // port
            0x06, 0x06, 0x06, 0x06,  // ip
            0x00 // username
        });
        assertTrue(SocksDetector.isSocks4(msg, msg.readableBytes()));
    }

    @Test
    public void successfullyParseSocks4RequestWithMaxLengthUsername() {
        ByteBuf msg = Unpooled.wrappedBuffer(new byte[]{
            0x04, // protocol version
            0x01, // command code
            0x00, 0x00, // port
            0x06, 0x06, 0x06, 0x06,  // ip
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', // username
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', 0x00
        });
        assertTrue(SocksDetector.isSocks4(msg, msg.readableBytes()));
    }

    @Test
    public void failParsingSocks4RequestWithWrongProtocolVersion() {
        ByteBuf msg = Unpooled.wrappedBuffer(new byte[]{
            0x05, // protocol version
            0x01, // command code
            0x00, 0x00, // port
            0x06, 0x06, 0x06, 0x06,  // ip
            'f', 'o', 'o', 0x00 // username
        });
        assertFalse(SocksDetector.isSocks4(msg, msg.readableBytes()));
    }

    @Test
    public void failParsingSocks4RequestWithUnsupportedCommandCode() {
        ByteBuf msg = Unpooled.wrappedBuffer(new byte[]{
            0x04, // protocol version
            0x03, // command code
            0x00, 0x00, // port
            0x06, 0x06, 0x06, 0x06,  // ip
            'f', 'o', 'o', 0x00 // username
        });
        assertFalse(SocksDetector.isSocks4(msg, msg.readableBytes()));
    }

    @Test
    public void failParsingSocks4RequestWithTooLongUsername() {
        ByteBuf msg = Unpooled.wrappedBuffer(new byte[]{
            0x04, // protocol version
            0x01, // command code
            0x00, 0x00, // port
            0x06, 0x06, 0x06, 0x06,  // ip
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', // username
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', 0x00
        });
        assertFalse(SocksDetector.isSocks4(msg, msg.readableBytes()));
    }

    @Test
    public void failParsingSocks4RequestWithAdditionalReadableBytes() {
        ByteBuf msg = Unpooled.wrappedBuffer(new byte[]{
            0x04, // protocol version
            0x01, // command code
            0x00, 0x00, // port
            0x06, 0x06, 0x06, 0x06,  // ip
            'f', 'o', 'o', 0x00, // username
            0x00 // additional byte
        });
        assertFalse(SocksDetector.isSocks4(msg, msg.readableBytes()));
    }

    @Test
    public void successfullyParseSocks4aRequest() {
        ByteBuf msg = Unpooled.wrappedBuffer(new byte[]{
            0x04, // protocol version
            0x01, // command code
            0x00, 0x00, // port
            0x00, 0x00, 0x00, 0x06,  // invalid ip
            'f', 'o', 'o', 0x00, // username
            'f', 'o', 'o', 0x00 // hostname
        });
        assertTrue(SocksDetector.isSocks4(msg, msg.readableBytes()));
    }

    @Test
    public void successfullyParseSocks4aRequestWithEmptyUsername() {
        ByteBuf msg = Unpooled.wrappedBuffer(new byte[]{
            0x04, // protocol version
            0x01, // command code
            0x00, 0x00, // port
            0x00, 0x00, 0x00, 0x06,  // invalid ip
            0x00, // username
            'f', 'o', 'o', 0x00 // hostname
        });
        assertTrue(SocksDetector.isSocks4(msg, msg.readableBytes()));
    }

    @Test
    public void successfullyParseSocks4aRequestWithMaxLengthHostname() {
        ByteBuf msg = Unpooled.wrappedBuffer(new byte[]{
            0x04, // protocol version
            0x01, // command code
            0x00, 0x00, // port
            0x00, 0x00, 0x00, 0x06,  // invalid ip
            'f', 'o', 'o', 0x00, // username
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', // hostname
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', 0x00
        });
        assertTrue(SocksDetector.isSocks4(msg, msg.readableBytes()));
    }

    @Test
    public void failParsingSocks4aRequestWithTooLongHostname() {
        ByteBuf msg = Unpooled.wrappedBuffer(new byte[]{
            0x04, // protocol version
            0x01, // command code
            0x00, 0x00, // port
            0x00, 0x00, 0x00, 0x06,  // invalid ip
            'f', 'o', 'o', 0x00, // username
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', // hostname
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '1', '2', '3', '4', '5', '6', 0x00
        });
        assertFalse(SocksDetector.isSocks4(msg, msg.readableBytes()));
    }

    @Test
    public void failParsingSocks4aRequestWithEmptyHostname() {
        ByteBuf msg = Unpooled.wrappedBuffer(new byte[]{
            0x04, // protocol version
            0x01, // command code
            0x00, 0x00, // port
            0x00, 0x00, 0x00, 0x06,  // invalid ip
            'f', 'o', 'o', 0x00, // username
            0x00 // hostname
        });
        assertFalse(SocksDetector.isSocks4(msg, msg.readableBytes()));
    }

    @Test
    public void failParsingSocks4aRequestWithAdditionalReadableBytes() {
        ByteBuf msg = Unpooled.wrappedBuffer(new byte[]{
            0x04, // protocol version
            0x01, // command code
            0x00, 0x00, // port
            0x00, 0x00, 0x00, 0x06,  // invalid ip
            'f', 'o', 'o', 0x00, // username
            'f', 'o', 'o', 0x00, // hostname
            0x00 // additional byte
        });
        assertFalse(SocksDetector.isSocks4(msg, msg.readableBytes()));
    }

    @Test
    public void successfullyParseSocks5Request() {
        ByteBuf msg = Unpooled.wrappedBuffer(new byte[]{
            0x05, // protocol version
            0x02, // amount of authentication methods
            0x00, 0x02 // authentication methods
        });
        assertTrue(SocksDetector.isSocks5(msg, msg.readableBytes()));
    }

    @Test
    public void successfullyParseSocks5RequestWithManyMethods() {
        ByteBuf msg = Unpooled.wrappedBuffer(new byte[]{
            0x05, // protocol version
            0x05, // amount of authentication methods
            0x00, 0x01, 0x02, 0x00, 0x01 // authentication methods
        });
        assertTrue(SocksDetector.isSocks5(msg, msg.readableBytes()));
    }

    @Test
    public void failParsingSocks5RequestWithWrongProtocolVersion() {
        ByteBuf msg = Unpooled.wrappedBuffer(new byte[]{
            0x04, // protocol version
            0x05, // amount of authentication methods
            0x00, 0x01, 0x02, 0x00, 0x01 // authentication methods
        });
        assertFalse(SocksDetector.isSocks5(msg, msg.readableBytes()));
    }

    @Test
    public void failParsingSocks5RequestWithUnsupportedAuthenticationMethod() {
        ByteBuf msg = Unpooled.wrappedBuffer(new byte[]{
            0x05, // protocol version
            0x01, // amount of authentication methods
            0x05 // authentication methods
        });
        assertFalse(SocksDetector.isSocks5(msg, msg.readableBytes()));
    }

    @Test
    public void failParsingSocks5RequestWithAdditionalReadableBytes() {
        ByteBuf msg = Unpooled.wrappedBuffer(new byte[]{
            0x05, // protocol version
            0x01, // amount of authentication methods
            0x00, // authentication methods
            0x00 // additional byte
        });
        assertFalse(SocksDetector.isSocks5(msg, msg.readableBytes()));
    }
}
