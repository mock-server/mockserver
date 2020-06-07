package org.mockserver.exception;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.ssl.NotSslRecordException;
import io.netty.util.internal.PlatformDependent;
import org.mockserver.client.SocketConnectionException;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import java.net.ConnectException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SocketChannel;
import java.security.SignatureException;
import java.security.cert.CertPathValidatorException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import static org.slf4j.event.Level.WARN;

public class ExceptionHandling {

    static MockServerLogger mockServerLogger = new MockServerLogger();

    private static final Pattern IGNORABLE_CLASS_IN_STACK = Pattern.compile("^.*(?:Socket|Datagram|Sctp|Udt)Channel.*$");
    private static final Pattern IGNORABLE_ERROR_MESSAGE = Pattern.compile("^.*(?:connection.*(?:reset|closed|abort|broken)|broken.*pipe).*$", Pattern.CASE_INSENSITIVE);

    public static void swallowThrowable(Runnable runnable) {
        try {
            runnable.run();
        } catch (Throwable throwable) {
            if (MockServerLogger.isEnabled(WARN)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(WARN)
                        .setMessageFormat(throwable.getMessage())
                        .setThrowable(throwable)
                );
            }
        }
    }

    /**
     * Closes the specified channel after all queued write requests are flushed.
     */
    public static void closeOnFlush(Channel ch) {
        if (ch != null && ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

    /**
     * returns true is the exception was caused by the connection being closed
     */
    public static boolean connectionClosedException(Throwable throwable) {
        String message = String.valueOf(throwable.getMessage()).toLowerCase();

        // is ssl exception
        if (throwable.getCause() instanceof SSLException || throwable instanceof DecoderException || throwable instanceof NotSslRecordException) {
            return false;
        }

        // first try to match connection reset / broke peer based on the regex.
        // This is the fastest way but may fail on different jdk impls or OS's
        if (IGNORABLE_ERROR_MESSAGE.matcher(message).matches()) {
            return false;
        }

        // Inspect the StackTraceElements to see if it was a connection reset / broken pipe or not
        StackTraceElement[] elements = throwable.getStackTrace();
        for (StackTraceElement element : elements) {
            String classname = element.getClassName();
            String methodname = element.getMethodName();

            // skip all classes that belong to the io.netty package
            if (classname.startsWith("io.netty.")) {
                continue;
            }

            // check if the method name is read if not skip it
            if (!"read".equals(methodname)) {
                continue;
            }

            // This will also match against SocketInputStream which is used by openjdk 7 and maybe
            // also others
            if (IGNORABLE_CLASS_IN_STACK.matcher(classname).matches()) {
                return false;
            }

            try {
                // No match by now. Try to load the class via classloader and inspect it.
                // This is mainly done as other JDK implementations may differ in name of
                // the impl.
                Class<?> clazz = PlatformDependent.getClassLoader(ExceptionHandling.class).loadClass(classname);

                if (SocketChannel.class.isAssignableFrom(clazz)
                    || DatagramChannel.class.isAssignableFrom(clazz)) {
                    return false;
                }

                // also match against SctpChannel via String matching as it may not present.
                if (PlatformDependent.javaVersion() >= 7
                    && "com.sun.nio.sctp.SctpChannel".equals(clazz.getSuperclass().getName())) {
                    return false;
                }
            } catch (ClassNotFoundException e) {
                // This should not happen just ignore
            }
        }
        return true;
    }

    private static final List<Class<? extends Exception>> SSL_HANDSHAKE_FAILURE_CLASSES = Arrays.asList(SSLException.class, SSLHandshakeException.class, CertPathValidatorException.class, SignatureException.class);
    public static boolean sslHandshakeException(Throwable throwable) {
        for (Class<? extends Throwable> cause : getCauses(throwable)) {
            if (SSL_HANDSHAKE_FAILURE_CLASSES.contains(cause)) {
                return true;
            }
        }
        return false;
    }

    private static final List<Class<? extends Exception>> CONNECTION_EXCEPTION_CLASSES = Arrays.asList(SocketConnectionException.class, ConnectException.class);
    public static boolean connectionException(Throwable throwable) {
        for (Class<? extends Throwable> cause : getCauses(throwable)) {
            if (CONNECTION_EXCEPTION_CLASSES.contains(cause)) {
                return true;
            }
        }
        return false;
    }

    private static List<Class<? extends Throwable>> getCauses(final Throwable throwable) {
        if (throwable.getCause() != null) {
            if (throwable.getClass().equals(throwable.getCause().getClass())) {
                return new ArrayList<>(Collections.singletonList(throwable.getClass()));
            } else {
                final List<Class<? extends Throwable>> causes = getCauses(throwable.getCause());
                causes.add(throwable.getClass());
                return causes;
            }
        } else {
            return new ArrayList<>(Collections.singletonList(throwable.getClass()));
        }
    }

}
