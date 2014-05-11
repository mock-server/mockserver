#!/usr/bin/env bash

while [ $# -gt 0 ]
do
    case "$1" in
        -serverPort) serverPort="$2"; shift;;
        -serverSecurePort) serverSecurePort="$2"; shift;;
        -proxyPort) proxyPort="$2"; shift;;
        -proxySecurePort) proxySecurePort="$2"; shift;;
        -*)
            notset="true"
   break;;
*)
            break;;  # terminate while loop
    esac
    shift
done

if [ -z $serverPort ] && [ -z $serverSecurePort ] && [ -z $proxyPort ] && [ -z $proxySecurePort ]
then
    echo >&2 "   java -jar <path to mockserver-jetty-jar-with-dependencies.jar> [-serverPort <port>] [-serverSecurePort <port>] [-proxyPort <port>] [-proxySecurePort <port>]"
    echo >&2 ""
    echo >&2 "     valid options are:"
    echo >&2 "        -serverPort <port>         specifies the HTTP port for the MockServer      "
    echo >&2 "                                   if neither serverPort or serverSecurePort       "
    echo >&2 "                                   are provide the MockServer is not started       "
    echo >&2 "        -serverSecurePort <port>   specifies the HTTPS port for the MockServer     "
    echo >&2 "                                   if neither serverPort or serverSecurePort       "
    echo >&2 "                                   are provide the MockServer is not started       "
    echo >&2 "                                                                                   "
    echo >&2 "        -proxyPort <path>          specifies the HTTP port for the httpProxyBuilder"
    echo >&2 "                                   if neither proxyPort or proxySecurePort         "
    echo >&2 "                                   are provide the MockServer is not started       "
    echo >&2 "        -proxySecurePort <path>    specifies the HTTPS port for the httpProxyBuilder"
    echo >&2 "                                   if neither proxyPort or proxySecurePort         "
    echo >&2 "                                   are provide the MockServer is not started       ";
    exit 1
fi

COMMAND_LINE_OPTS=""

if [ -n "$serverPort" ]
then
    COMMAND_LINE_OPTS="$COMMAND_LINE_OPTS -serverPort $serverPort"
fi
if [ -n "$serverSecurePort" ]
then
    COMMAND_LINE_OPTS="$COMMAND_LINE_OPTS -serverSecurePort $serverSecurePort"
fi
if [ -n "$proxyPort" ]
then
    COMMAND_LINE_OPTS="$COMMAND_LINE_OPTS -proxyPort $proxyPort"
fi
if [ -n "$proxySecurePort" ]
then
    COMMAND_LINE_OPTS="$COMMAND_LINE_OPTS -proxySecurePort $proxySecurePort"
fi

if [ -z "$MOCKSERVER_HOME" ]
then
    MOCKSERVER_HOME="/opt/mockserver"
fi


echo "java -Dfile.encoding=UTF-8 -Dmockserver.logLevel=WARN -jar $MOCKSERVER_HOME/mockserver-netty-2.9-jar-with-dependencies.jar $COMMAND_LINE_OPTS"
java -Dfile.encoding=UTF-8 -Dmockserver.logLevel=WARN -jar $MOCKSERVER_HOME/mockserver-netty-2.9-jar-with-dependencies.jar $COMMAND_LINE_OPTS
