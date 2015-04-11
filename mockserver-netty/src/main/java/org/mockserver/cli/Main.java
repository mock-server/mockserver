package org.mockserver.cli;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import org.mockserver.logging.Logging;
import org.mockserver.mockserver.MockServerBuilder;
import org.mockserver.proxy.ProxyBuilder;
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
    public static final String SERVER_PORT_KEY = "serverPort";
    public static final String PROXY_PORT_KEY = "proxyPort";
    public static final String PROXY_REMOTE_PORT_KEY = "proxyRemotePort";
    public static final String PROXY_REMOTE_HOST_KEY = "proxyRemoteHost";
    public static final String USAGE = "" +
            "   java -jar <path to mockserver-jetty-jar-with-dependencies.jar> [-serverPort <port>] [-proxyPort <port>] [-proxyRemotePort <port>] [-proxyRemoteHost <hostname>]" + System.getProperty("line.separator") +
            "                                                                                       " + System.getProperty("line.separator") +
            "     valid options are:                                                                " + System.getProperty("line.separator") +
            "        -serverPort <port>           specifies the HTTP, HTTPS, SOCKS and HTTP         " + System.getProperty("line.separator") +
            "                                     CONNECT port for proxy, port unification          " + System.getProperty("line.separator") +
            "                                     supports for all protocols on the same port       " + System.getProperty("line.separator") +
            "                                                                                       " + System.getProperty("line.separator") +
            "        -proxyPort <port>            specifies the HTTP and HTTPS port for the         " + System.getProperty("line.separator") +
            "                                     MockServer port unification is used to            " + System.getProperty("line.separator") +
            "                                     support HTTP and HTTPS on the same port           " + System.getProperty("line.separator") +
            "                                                                                       " + System.getProperty("line.separator") +
            "        -proxyRemotePort <port>      specifies the port to forward all proxy           " + System.getProperty("line.separator") +
            "                                     requests to (i.e. all requests received on        " + System.getProperty("line.separator") +
            "                                     portPort), this setting is used to enable         " + System.getProperty("line.separator") +
            "                                     the port forwarding mode therefore this           " + System.getProperty("line.separator") +
            "                                     option disables the HTTP, HTTPS, SOCKS and        " + System.getProperty("line.separator") +
            "                                     HTTP CONNECT support                              " + System.getProperty("line.separator") +
            "                                                                                       " + System.getProperty("line.separator") +
            "        -proxyRemoteHost <hostname>  specified the host to forward all proxy           " + System.getProperty("line.separator") +
            "                                     requests to (i.e. all requests received on        " + System.getProperty("line.separator") +
            "                                     portPort), this setting is ignored unless         " + System.getProperty("line.separator") +
            "                                     proxyRemotePort has been specified, if no         " + System.getProperty("line.separator") +
            "                                     value is provided for proxyRemoteHost when        " + System.getProperty("line.separator") +
            "                                     proxyRemotePort has been specified,               " + System.getProperty("line.separator") +
            "                                     proxyRemoteHost will default to \"localhost\"     " + System.getProperty("line.separator") +
            "                                                                                       " + System.getProperty("line.separator");

    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    @VisibleForTesting
    static ProxyBuilder httpProxyBuilder = new ProxyBuilder();
    @VisibleForTesting
    static MockServerBuilder mockServerBuilder = new MockServerBuilder();
    @VisibleForTesting
    static PrintStream outputPrintStream = System.out;
    @VisibleForTesting
    static Runtime runtime = Runtime.getRuntime();
    private static boolean usagePrinted = false;


    /**
     * Run the MockServer directly providing the parseArguments for the server and httpProxyBuilder as the only input parameters (if not provided the server port defaults to 8080 and the httpProxyBuilder is not started).
     *
     * @param arguments the entries are in pairs:
     *                  - the first  pair is "-serverPort" followed by the server port if not provided the MockServer is not started,
     *                  - the second pair is "-proxyPort"  followed by the httpProxyBuilder  port if not provided the httpProxyBuilder      is not started
     */
    public static void main(String... arguments) {
        usagePrinted = false;

        Map<String, String> parsedArguments = parseArguments(arguments);

        if (logger.isDebugEnabled()) {
            logger.debug(System.getProperty("line.separator") + System.getProperty("line.separator") + "Using command line options: " +
                    Joiner.on(", ").withKeyValueSeparator("=").join(parsedArguments) + System.getProperty("line.separator"));
        }
        Logging.overrideLogLevel(System.getProperty("mockserver.logLevel"));

        if (parsedArguments.size() > 0) {
            if (parsedArguments.containsKey(SERVER_PORT_KEY)) {
                mockServerBuilder.withHTTPPort(Integer.parseInt(parsedArguments.get(SERVER_PORT_KEY))).build();
            }
            if (parsedArguments.containsKey(PROXY_PORT_KEY)) {
                ProxyBuilder proxyBuilder = httpProxyBuilder.withLocalPort(Integer.parseInt(parsedArguments.get(PROXY_PORT_KEY)));
                if (parsedArguments.containsKey(PROXY_REMOTE_PORT_KEY)) {
                    String remoteHost = parsedArguments.get(PROXY_REMOTE_HOST_KEY);
                    if (Strings.isNullOrEmpty(remoteHost)) {
                        remoteHost = "localhost";
                    }
                    proxyBuilder.withDirect(remoteHost, Integer.parseInt(parsedArguments.get(PROXY_REMOTE_PORT_KEY)));
                }
                proxyBuilder.build();
            }
        } else {
            showUsage();
        }
    }

    private static Map<String, String> parseArguments(String... arguments) {
        Map<String, String> parsedIntegerArguments = new HashMap<String, String>();
        Iterator<String> argumentsIterator = Arrays.asList(arguments).iterator();
        while (argumentsIterator.hasNext()) {
            String argumentName = argumentsIterator.next();
            if (argumentsIterator.hasNext()) {
                String argumentValue = argumentsIterator.next();
                if (!parsePort(parsedIntegerArguments, SERVER_PORT_KEY, argumentName, argumentValue)
                        && !parsePort(parsedIntegerArguments, PROXY_PORT_KEY, argumentName, argumentValue)
                        && !parsePort(parsedIntegerArguments, PROXY_REMOTE_PORT_KEY, argumentName, argumentValue)
                        && !("-" + PROXY_REMOTE_HOST_KEY).equalsIgnoreCase(argumentName)) {
                    showUsage();
                    break;
                }
                if (("-" + PROXY_REMOTE_HOST_KEY).equalsIgnoreCase(argumentName)) {
                    parsedIntegerArguments.put(PROXY_REMOTE_HOST_KEY, argumentValue);
                }
            } else {
                showUsage();
                break;
            }
        }
        return parsedIntegerArguments;
    }

    private static boolean parsePort(Map<String, String> parsedArguments, final String key, final String argumentName, final String argumentValue) {
        if (argumentName.equals("-" + key)) {
            try {
                parsedArguments.put(key, String.valueOf(Integer.parseInt(argumentValue)));
                return true;
            } catch (NumberFormatException nfe) {
                logger.error("Please provide a value integer for -" + key + ", [" + argumentValue + "] is not a valid integer", nfe);
            }
        }
        return false;
    }

    private static void showUsage() {
        if (!usagePrinted) {
            outputPrintStream.print(USAGE);
            runtime.exit(1);
            usagePrinted = true;
        }
    }

}
