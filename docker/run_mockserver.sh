#!/usr/bin/env bash

set -e

function showUsage {
    echo >&2 "   run_mockserver.sh [-logLevel <level>] [-serverPort <port>] [-proxyPort <port>] [-proxyRemotePort <port>] [-proxyRemoteHost <hostname>]"
    echo >&2 "                                                                                   "
    echo >&2 "     valid options are:                                                            "
    echo >&2 "        -logLevel <level>            OFF, ERROR, WARN, INFO, DEBUG, TRACE or ALL, as follows: "
    echo >&2 "                                     WARN - exceptions and errors                  "
    echo >&2 "                                     INFO - all interactions                       "
    echo >&2 "                                                                                   "
    echo >&2 "        -serverPort <port>           Specifies the HTTP, HTTPS, SOCKS and HTTP     "
    echo >&2 "                                     CONNECT port for proxy. Port unification      "
    echo >&2 "                                     supports for all protocols on the same port   "
    echo >&2 "                                                                                   "
    echo >&2 "        -proxyPort <port>            Specifies the HTTP and HTTPS port for the     "
    echo >&2 "                                     MockServer. Port unification is used to       "
    echo >&2 "                                     support HTTP and HTTPS on the same port       "
    echo >&2 "                                                                                   "
    echo >&2 "        -proxyRemotePort <port>      Specifies the port to forward all proxy       "
    echo >&2 "                                     requests to (i.e. all requests received on    "
    echo >&2 "                                     portPort). This setting is used to enable     "
    echo >&2 "                                     the port forwarding mode therefore this       "
    echo >&2 "                                     option disables the HTTP, HTTPS, SOCKS and    "
    echo >&2 "                                     HTTP CONNECT support                          "
    echo >&2 "                                                                                   "
    echo >&2 "        -proxyRemoteHost <hostname>  Specified the host to forward all proxy       "
    echo >&2 "                                     requests to (i.e. all requests received on    "
    echo >&2 "                                     portPort). This setting is ignored unless     "
    echo >&2 "                                     proxyRemotePort has been specified. If no     "
    echo >&2 "                                     value is provided for proxyRemoteHost when    "
    echo >&2 "                                     proxyRemotePort has been specified,           "
    echo >&2 "                                     proxyRemoteHost will default to \"localhost\"."
    echo >&2 "                                                                                   "
    echo >&2 "   i.e. /opt/mockserver/run_mockserver.sh -logLevel INFO -serverPort 1080 -proxyPort 1090 -proxyRemotePort 80 -proxyRemoteHost www.mock-server.com"
    echo >&2 "                                                                                   "
    exit 1
}

function runCommand {
    echo
    printf -v str "%-$((${#1} + 19))s" ' '; echo "${str// /=}"
    echo "Executing command: $1"
    printf -v str "%-$((${#1} + 19))s" ' '; echo "${str// /=}"
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

LOG_LEVEL="INFO"

if [ -n "$logLevel" ]
then
    LOG_LEVEL="$logLevel"
fi

validateArgument $LOG_LEVEL "OFF ERROR WARN INFO DEBUG TRACE ALL." "Invalid value '$LOG_LEVEL' for 'logLevel'"

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

if [ -z "$MOCKSERVER_HOME" ]
then
    MOCKSERVER_HOME="/opt/mockserver"
fi

runCommand "java -Dfile.encoding=UTF-8 -Dmockserver.logLevel=$LOG_LEVEL -jar $MOCKSERVER_HOME/mockserver-netty-jar-with-dependencies.jar$COMMAND_LINE_OPTS"
