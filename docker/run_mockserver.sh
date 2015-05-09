#!/usr/bin/env bash

while [ $# -gt 0 ]
do
    case "$1" in
        -serverPort) serverPort="$2"; shift;;
        -proxyPort) proxyPort="$2"; shift;;
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
    echo >&2 "   java -jar <path to mockserver-jetty-jar-with-dependencies.jar> [-serverPort <port>] [-proxyPort <port>]"
    echo >&2 ""
    echo >&2 "     valid options are:"
    echo >&2 "        -serverPort <port>         specifies the HTTP and HTTPS port for the       "
    echo >&2 "                                   MockServer port unification is used to          "
    echo >&2 "                                   support HTTP and HTTPS on the same port         "
    echo >&2 "                                                                                   "
    echo >&2 "        -proxyPort <path>          specifies the HTTP, HTTPS, SOCKS and HTTP       "
    echo >&2 "                                   CONNECT port for proxy, port unification        "
    echo >&2 "                                   supports for all protocols on the same port     ";
    exit 1
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

if [ -z "$MOCKSERVER_HOME" ]
then
    MOCKSERVER_HOME="/opt/mockserver"
fi


echo "java -Dfile.encoding=UTF-8 -Dmockserver.logLevel=WARN -jar $MOCKSERVER_HOME/mockserver-netty-3.9.12-jar-with-dependencies.jar $COMMAND_LINE_OPTS"
java -Dfile.encoding=UTF-8 -Dmockserver.logLevel=WARN -jar $MOCKSERVER_HOME/mockserver-netty-3.9.12-jar-with-dependencies.jar $COMMAND_LINE_OPTS
