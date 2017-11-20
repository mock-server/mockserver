package org.mockserver.exception;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.util.internal.PlatformDependent;

import java.nio.channels.DatagramChannel;
import java.nio.channels.SocketChannel;
import java.util.regex.Pattern;

public class ExceptionHandler {

    private static final Pattern IGNORABLE_CLASS_IN_STACK = Pattern.compile(
            "^.*(?:Socket|Datagram|Sctp|Udt)Channel.*$");
    private static final Pattern IGNORABLE_ERROR_MESSAGE = Pattern.compile(
            "^.*(?:connection.*(?:reset|closed|abort|broken)|broken.*pipe).*$", Pattern.CASE_INSENSITIVE);

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
    public static boolean shouldIgnoreException(Throwable cause) {
        String message = String.valueOf(cause.getMessage()).toLowerCase();

        // first try to match connection reset / broke peer based on the regex.
        // This is the fastest way but may fail on different jdk impls or OS's
        if (IGNORABLE_ERROR_MESSAGE.matcher(message).matches()) {
            return true;
        }

        // Inspect the StackTraceElements to see if it was a connection reset / broken pipe or not
        StackTraceElement[] elements = cause.getStackTrace();
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
                return true;
            }

            try {
                // No match by now.. Try to load the class via classloader and inspect it.
                // This is mainly done as other JDK implementations may differ in name of
                // the impl.
                Class<?> clazz = PlatformDependent.getClassLoader(ExceptionHandler.class).loadClass(classname);

                if (SocketChannel.class.isAssignableFrom(clazz)
                        || DatagramChannel.class.isAssignableFrom(clazz)) {
                    return true;
                }

                // also match against SctpChannel via String matching as it may not present.
                if (PlatformDependent.javaVersion() >= 7
                        && "com.sun.nio.sctp.SctpChannel".equals(clazz.getSuperclass().getName())) {
                    return true;
                }
            } catch (ClassNotFoundException e) {
                // This should not happen just ignore
            }
        }
        return false;
    }
}