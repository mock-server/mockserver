#!/usr/bin/env bash

while [ $# -gt 0 ]
do
    case "$1" in
        -logLevel) logLevel="$2"; shift;;
        -serverPort) serverPort="$2"; shift;;
        -proxyPort) proxyPort="$2"; shift;;
        -proxyRemotePort) proxyRemotePort="$2"; shift;;
        -proxyRemoteHost) proxyRemoteHost="$2"; shift;;
        -*)
            notset="true"
            break;;
        *)
            break;;  # terminate while loop
    esac
    shift
done

if [ -z $serverPort ] && [ -z $proxyPort ]
then
    echo >&2 "   java -jar <path to mockserver-jetty-jar-with-dependencies.jar> [-logLevel <level>] [-serverPort <port>] [-proxyPort <port>] [-proxyRemotePort <port>] [-proxyRemoteHost <hostname>]"
    echo >&2 ""
    echo >&2 "     valid options are:"
    echo >&2 "        -logLevel <level>            OFF, ERROR, WARN, INFO, DEBUG, TRACE or ALL, as follows: "
    echo >&2 "                                     WARN - exceptions and errors                 "
    echo >&2 "                                     INFO - all interactions (summerised)         "
    echo >&2 "                                                                                  "
    echo >&2 "        -serverPort <port>           specifies the HTTP, HTTPS, SOCKS and HTTP    "
    echo >&2 "                                     CONNECT port for proxy, port unification     "
    echo >&2 "                                     supports for all protocols on the same port  "
    echo >&2 "                                                                                  "
    echo >&2 "        -proxyPort <port>            specifies the HTTP and HTTPS port for the    "
    echo >&2 "                                     MockServer port unification is used to       "
    echo >&2 "                                     support HTTP and HTTPS on the same port      "
    echo >&2 "                                                                                  "
    echo >&2 "        -proxyRemotePort <port>      specifies the port to forward all proxy      "
    echo >&2 "                                     requests to (i.e. all requests received on   "
    echo >&2 "                                     portPort), this setting is used to enable    "
    echo >&2 "                                     the port forwarding mode therefore this      "
    echo >&2 "                                     option disables the HTTP, HTTPS, SOCKS and   "
    echo >&2 "                                     HTTP CONNECT support                         "
    echo >&2 "                                                                                  "
    echo >&2 "        -proxyRemoteHost <hostname>  specified the host to forward all proxy      "
    echo >&2 "                                     requests to (i.e. all requests received on   "
    echo >&2 "                                     portPort), this setting is ignored unless    "
    echo >&2 "                                     proxyRemotePort has been specified, if no    "
    echo >&2 "                                     value is provided for proxyRemoteHost when   "
    echo >&2 "                                     proxyRemotePort has been specified,          "
    echo >&2 "                                     proxyRemoteHost will default to \"localhost\"";
    exit 1
fi

LOG_LEVEL="INFO"

if [ -n "$logLevel" ]
then
    LOG_LEVEL="$logLevel"
fi

COMMAND_LINE_OPTS=""

if [ -n "$serverPort" ]
then
    COMMAND_LINE_OPTS="$COMMAND_LINE_OPTS -serverPort $serverPort"
fi
if [ -n "$proxyPort" ]
then
    COMMAND_LINE_OPTS="$COMMAND_LINE_OPTS -proxyPort $proxyPort"
fi
if [ -n "$proxyRemotePort" ]
then
    COMMAND_LINE_OPTS="$COMMAND_LINE_OPTS -proxyRemotePort $proxyRemotePort"
fi
if [ -n "$proxyRemoteHost" ]
then
    COMMAND_LINE_OPTS="$COMMAND_LINE_OPTS -proxyRemoteHost $proxyRemoteHost"
fi

echo "java -Dfile.encoding=UTF-8 -Dmockserver.logLevel=$LOG_LEVEL -Dlog.dir=/usr/local/var/log/mockserver/ -jar /usr/local/lib/mockserver/mockserver-netty-jar-with-dependencies.jar $COMMAND_LINE_OPTS"
java -Dfile.encoding=UTF-8 -Dmockserver.logLevel=$LOG_LEVEL -Dlog.dir=/usr/local/var/log/mockserver/ -jar /usr/local/lib/mockserver/mockserver-netty-jar-with-dependencies.jar $COMMAND_LINE_OPTS