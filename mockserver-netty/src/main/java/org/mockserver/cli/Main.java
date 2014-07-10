package org.mockserver.cli;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import org.mockserver.logging.Logging;
import org.mockserver.mockserver.MockServerBuilder;
import org.mockserver.proxy.http.HttpProxyBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * @author jamesdbloom
 */
public class Main {
    public static final String PROXY_PORT_KEY = "proxyPort";
    public static final String PROXY_SECURE_PORT_KEY = "proxySecurePort";
    public static final String SERVER_PORT_KEY = "serverPort";
    public static final String SERVER_SECURE_PORT_KEY = "serverSecurePort";
    public static final String USAGE = "" +
            "   java -jar <path to mockserver-jetty-jar-with-dependencies.jar> [-serverPort <port>] [-serverSecurePort <port>] [-proxyPort <port>] [-proxySecurePort <port>]" + System.getProperty("line.separator") +
            "   " + System.getProperty("line.separator") +
            "     valid options are:" + System.getProperty("line.separator") +
            "        -serverPort <port>         specifies the HTTP port for the MockServer      " + System.getProperty("line.separator") +
            "                                   if neither serverPort or serverSecurePort       " + System.getProperty("line.separator") +
            "                                   are provide the MockServer is not started       " + System.getProperty("line.separator") +
            "        -serverSecurePort <port>   specifies the HTTPS port for the MockServer     " + System.getProperty("line.separator") +
            "                                   if neither serverPort or serverSecurePort       " + System.getProperty("line.separator") +
            "                                   are provide the MockServer is not started       " + System.getProperty("line.separator") +
            "                                                                                   " + System.getProperty("line.separator") +
            "        -proxyPort <path>          specifies the HTTP port for the httpProxyBuilder           " + System.getProperty("line.separator") +
            "                                   if neither proxyPort or proxySecurePort         " + System.getProperty("line.separator") +
            "                                   are provide the MockServer is not started       " + System.getProperty("line.separator") +
            "        -proxySecurePort <path>    specifies the HTTPS port for the httpProxyBuilder          " + System.getProperty("line.separator") +
            "                                   if neither proxyPort or proxySecurePort         " + System.getProperty("line.separator") +
            "                                   are provide the MockServer is not started       " + System.getProperty("line.separator");
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    @VisibleForTesting
    static HttpProxyBuilder httpProxyBuilder = new HttpProxyBuilder();
    @VisibleForTesting
    static MockServerBuilder mockServerBuilder = new MockServerBuilder();
    @VisibleForTesting
    static PrintStream outputPrintStream = System.out;
    @VisibleForTesting
    static boolean shutdownOnUsage = true;

    /**
     * Run the MockServer directly providing the parseArguments for the server and httpProxyBuilder as the only input parameters (if not provided the server port defaults to 8080 and the httpProxyBuilder is not started).
     *
     * @param arguments the entries are in pairs:
     *                  - the first  pair is "-serverPort" followed by the server port if not provided the MockServer is not started,
     *                  - the second pair is "-proxyPort"  followed by the httpProxyBuilder  port if not provided the httpProxyBuilder      is not started
     */
    public static void main(String... arguments) {
        Map<String, Integer> parseArguments = parseArguments(arguments);

        if (logger.isDebugEnabled()) {
            logger.debug("\n\nUsing command line options: " + Joiner.on(", ").withKeyValueSeparator("=").join(parseArguments) + "" + System.getProperty("line.separator"));
        }
        Logging.overrideLogLevel(System.getProperty("mockserver.logLevel"));

        if (parseArguments.size() > 0) {
            if (parseArguments.containsKey(PROXY_PORT_KEY) || parseArguments.containsKey(PROXY_SECURE_PORT_KEY)) {
                httpProxyBuilder.withHTTPPort(parseArguments.get(PROXY_PORT_KEY));
                httpProxyBuilder.withHTTPSPort(parseArguments.get(PROXY_SECURE_PORT_KEY)).build();
            }

            if (parseArguments.containsKey(SERVER_PORT_KEY) || parseArguments.containsKey(SERVER_SECURE_PORT_KEY)) {
                mockServerBuilder.withHTTPPort(parseArguments.get(SERVER_PORT_KEY)).withHTTPSPort(parseArguments.get(SERVER_SECURE_PORT_KEY)).build();
            }
        } else {
            showUsage();
        }
    }

    private static Map<String, Integer> parseArguments(String... arguments) {
        Map<String, Integer> parsedArguments = new HashMap<String, Integer>();
        Iterator<String> argumentsIterator = Arrays.asList(arguments).iterator();
        while (argumentsIterator.hasNext()) {
            String argumentName = argumentsIterator.next();
            if (argumentsIterator.hasNext()) {
                String argumentValue = argumentsIterator.next();
                if (!parsePort(parsedArguments, SERVER_PORT_KEY, argumentName, argumentValue)
                        && !parsePort(parsedArguments, PROXY_SECURE_PORT_KEY, argumentName, argumentValue)
                        && !parsePort(parsedArguments, PROXY_PORT_KEY, argumentName, argumentValue)
                        && !parsePort(parsedArguments, SERVER_SECURE_PORT_KEY, argumentName, argumentValue)) {
                    showUsage();
                }
            } else {
                showUsage();
            }
        }
        return parsedArguments;
    }

    private static boolean parsePort(Map<String, Integer> parsedArguments, final String key, final String argumentName, final String argumentValue) {
        if (argumentName.equals("-" + key)) {
            try {
                parsedArguments.put(key, Integer.parseInt(argumentValue));
                return true;
            } catch (NumberFormatException nfe) {
                logger.error("Please provide a value integer for -" + key + ", [" + argumentValue + "] is not a valid integer", nfe);
            }
        }
        return false;
    }

    private static void showUsage() {
        outputPrintStream.println(USAGE);
        if (shutdownOnUsage) System.exit(1);
    }

}
