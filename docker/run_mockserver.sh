#!/usr/bin/env bash

set -e

function showUsage {
    echo >&2 "   run_mockserver.sh [-logLevel <level>] [-serverPort <port>] [-proxyPort <port>] [-proxyRemotePort <port>] [-proxyRemoteHost <hostname>]"
    echo >&2 "                                                                                   "
    echo >&2 "     valid options are:                                                            "
    echo >&2 "        -logLevel <level>                       OFF, ERROR, WARN, INFO, DEBUG, TRACE or ALL, as follows:"
    echo >&2 "                                                WARN - exceptions and errors"
    echo >&2 "                                                INFO - all interactions"
    echo >&2 "                                                "
    echo >&2 "        -serverPort <port>                      Specifies the HTTP and HTTPS port(s) for the"
    echo >&2 "                                                MockServer. Port unification is used to"
    echo >&2 "                                                support HTTP and HTTPS on the same port(s)"
    echo >&2 "                                                "
    echo >&2 "        -proxyPort <port>                       Specifies the HTTP, HTTPS, SOCKS and HTTP"
    echo >&2 "                                                CONNECT port for proxy. Port unification"
    echo >&2 "                                                supports for all protocols on the same port"
    echo >&2 "                                                "
    echo >&2 "        -proxyRemotePort <port>                 Specifies the port to forward all proxy"
    echo >&2 "                                                requests to (i.e. all requests received on"
    echo >&2 "                                                portPort). This setting is used to enable"
    echo >&2 "                                                the port forwarding mode therefore this"
    echo >&2 "                                                option disables the HTTP, HTTPS, SOCKS and"
    echo >&2 "                                                HTTP CONNECT support"
    echo >&2 "                                                "
    echo >&2 "        -proxyRemoteHost <hostname>             Specified the host to forward all proxy"
    echo >&2 "                                                requests to (i.e. all requests received on"
    echo >&2 "                                                portPort). This setting is ignored unless"
    echo >&2 "                                                proxyRemotePort has been specified. If no"
    echo >&2 "                                                value is provided for proxyRemoteHost when"
    echo >&2 "                                                proxyRemotePort has been specified,"
    echo >&2 "                                                proxyRemoteHost will default to \"localhost\"."
    echo >&2 "                                                "
    echo >&2 "        -genericJVMOptions <system parameters>  Specified generic JVM options or system properties."
    echo >&2 "                                                                                   "
    echo >&2 "   i.e. /opt/mockserver/run_mockserver.sh -logLevel INFO -serverPort 1080,1081 -proxyPort 1090 -proxyRemotePort 80 -proxyRemoteHost www.mock-server.com -genericJVMOptions \"-Dmockserver.enableCORSForAllResponses=true -Dmockserver.sslSubjectAlternativeNameDomains='org.mock-server.com,mock-server.com'\""
    echo >&2 "                                                                                   "
    exit 1
}

function runCommand {
    echo
    echo "$1"
    echo
    eval $1
}

function validateArgument {
    local validArgument=false
    for validValue in $2
    do
        if [ "$1" = "$validValue" ];
        then
            validArgument=true
        fi
    done
    if [ "$validArgument" = false ];
    then
        echo
        echo "   Error: $3"
        echo
        showUsage
    fi
}

while [ $# -gt 0 ]
do
    case "$1" in
        -logLevel) logLevel="$2"; shift;;
        -serverPort) serverPort="$2"; shift;;
        -proxyPort) proxyPort="$2"; shift;;
        -proxyRemotePort) proxyRemotePort="$2"; shift;;
        -proxyRemoteHost) proxyRemoteHost="$2"; shift;;
        -genericJVMOptions) genericJVMOptions="$2"; shift;;
        -*) notset="true"; break;;
        *) break;;
    esac
    shift
done

if [ -z $serverPort ] && [ -z $proxyPort ]
then
    echo
    echo "   Error: At least 'serverPort' or 'proxyPort' must be provided"
    echo
    showUsage
fi

if [ -z "$LOG_LEVEL" ]
then
    if [ -n "$logLevel" ]
    then
        LOG_LEVEL="$logLevel"
    else
        LOG_LEVEL="INFO"
    fi
fi

validateArgument $LOG_LEVEL "OFF ERROR WARN INFO DEBUG TRACE ALL." "Invalid value '$LOG_LEVEL' for 'logLevel'"

COMMAND_LINE_OPTS=""

if [ -n "$serverPort" ]
then
    SERVER_PORT="$serverPort"
fi
if [ -n "$SERVER_PORT" ]
then
    COMMAND_LINE_OPTS="$COMMAND_LINE_OPTS -serverPort $SERVER_PORT"
fi

if [ -n "$proxyPort" ]
then
    PROXY_PORT="$proxyPort"
fi
if [ -n "$PROXY_PORT" ]
then
    COMMAND_LINE_OPTS="$COMMAND_LINE_OPTS -proxyPort $PROXY_PORT"
fi

if [ -n "$proxyRemotePort" ]
then
    PROXY_REMOTE_PORT="$proxyRemotePort"
fi
if [ -n "$PROXY_REMOTE_PORT" ]
then
    COMMAND_LINE_OPTS="$COMMAND_LINE_OPTS -proxyRemotePort $PROXY_REMOTE_PORT"
fi

if [ -n "$proxyRemoteHost" ]
then
    PROXY_REMOTE_HOST="$proxyRemoteHost"
fi
if [ -n "$PROXY_REMOTE_HOST" ]
then
    COMMAND_LINE_OPTS="$COMMAND_LINE_OPTS -proxyRemoteHost $PROXY_REMOTE_HOST"
fi

if [ -n "$genericJVMOptions" ]
then
    JVM_OPTIONS="$genericJVMOptions"
fi

runCommand "java $JVM_OPTIONS -Dfile.encoding=UTF-8 -Dmockserver.logLevel=$LOG_LEVEL -jar /opt/mockserver/mockserver-netty-jar-with-dependencies.jar$COMMAND_LINE_OPTS"