#!/usr/bin/env bash

set -e
#set -o xtrace

function showUsage {
    echo >&2 "   run_mockserver.sh -serverPort <port> [-proxyRemotePort <port>] [-proxyRemoteHost <hostname>] [-logLevel <level>] [-jvmOptions <system parameters>]"
    echo >&2 "                                                                                   "
    echo >&2 "     valid options are:                                                            "
    echo >&2 "        -serverPort <port>                      The HTTP, HTTPS, SOCKS and HTTP CONNECT"
    echo >&2 "                                                port(s) for both mocking and proxying"
    echo >&2 "                                                requests.  Port unification is used to"
    echo >&2 "                                                support all protocols for proxying and"
    echo >&2 "                                                mocking on the same port(s). Supports"
    echo >&2 "                                                comma separated list for binding to"
    echo >&2 "                                                multiple ports."
    echo >&2 "                                                "
    echo >&2 "        -proxyRemotePort <port>                 Optionally enables port forwarding mode."
    echo >&2 "                                                When specified all requests received will"
    echo >&2 "                                                be forwarded to the specified port, unless"
    echo >&2 "                                                they match an expectation."
    echo >&2 "                                                "
    echo >&2 "        -proxyRemoteHost <hostname>             Specified the host to forward all proxy"
    echo >&2 "                                                requests to when port forwarding mode has"
    echo >&2 "                                                been enabled using the proxyRemotePort"
    echo >&2 "                                                option.  This setting is ignored unless"
    echo >&2 "                                                proxyRemotePort has been specified. If no"
    echo >&2 "                                                value is provided for proxyRemoteHost when"
    echo >&2 "                                                proxyRemotePort has been specified,"
    echo >&2 "                                                proxyRemoteHost will default to \"localhost\"."
    echo >&2 "                                                "
    echo >&2 "        -logLevel <level>                       Optionally specify log level using SLF4J levels:"
    echo >&2 "                                                TRACE, DEBUG, INFO, WARN, ERROR, OFF or Java"
    echo >&2 "                                                Logger levels: FINEST, FINE, INFO, WARNING,"
    echo >&2 "                                                SEVERE or OFF. If not specified default is INFO"
    echo >&2 "                                                "
    echo >&2 "        -jvmOptions <system parameters>         Specified generic JVM options or system properties."
    echo >&2 "                                                                                   "
    echo >&2 "   i.e. /opt/mockserver/run_mockserver.sh -serverPort 1080,1081 -proxyRemotePort 80 -proxyRemoteHost www.mock-server.com -logLevel DEBUG -jvmOptions \"-Dmockserver.enableCORSForAllResponses=true -Dmockserver.sslSubjectAlternativeNameDomains='org.mock-server.com,mock-server.com'\""
    echo >&2 "                                                                                   "
    exit 1
}

function runCommand {
    echo
    echo "$1"
    echo
    eval $1
}

function prep_term {
    unset term_child_pid
    unset term_kill_needed
    trap 'handle_term' TERM INT
}

function handle_term {
    if [ "${term_child_pid}" ]; then
        kill -TERM "${term_child_pid}" 2>/dev/null
    else
        term_kill_needed="yes"
    fi
}

function wait_term {
    term_child_pid=$!
    if [ "${term_kill_needed}" ]; then
        kill -TERM "${term_child_pid}" 2>/dev/null
    fi
    wait ${term_child_pid}
    trap - TERM INT
    wait ${term_child_pid}
}

while [ $# -gt 0 ]
do
    case "$1" in
        -serverPort) serverPort="$2"; shift;;
        -proxyRemotePort) proxyRemotePort="$2"; shift;;
        -proxyRemoteHost) proxyRemoteHost="$2"; shift;;
        -logLevel) logLevel="$2"; shift;;
        -jvmOptions) jvmOptions="$2"; shift;;
        -*) notset="true"; break;;
        *) break;;
    esac
    shift
done

COMMAND_LINE_OPTS=""

# serverPort
if [ -z "$SERVER_PORT" ]
then
    if [ -n "$serverPort" ]
    then
        SERVER_PORT="$serverPort"
    fi
fi
if [ -n "$SERVER_PORT" ]
then
    COMMAND_LINE_OPTS="$COMMAND_LINE_OPTS -serverPort $SERVER_PORT"
else
    echo
    echo "   Error: At least 'serverPort' must be provided"
    echo
    showUsage
fi

# proxyRemotePort
if [ -z "$PROXY_REMOTE_PORT" ]
then
    if [ -n "$proxyRemotePort" ]
    then
        PROXY_REMOTE_PORT="$proxyRemotePort"
    fi
fi
if [ -n "$PROXY_REMOTE_PORT" ]
then
    COMMAND_LINE_OPTS="$COMMAND_LINE_OPTS -proxyRemotePort $PROXY_REMOTE_PORT"
fi

# proxyRemoteHost
if [ -z "$PROXY_REMOTE_HOST" ]
then
    if [ -n "$proxyRemoteHost" ]
    then
        PROXY_REMOTE_HOST="$proxyRemoteHost"
    fi
fi
if [ -n "$PROXY_REMOTE_HOST" ]
then
    COMMAND_LINE_OPTS="$COMMAND_LINE_OPTS -proxyRemoteHost $PROXY_REMOTE_HOST"
fi

# logLevel
if [ -z "$LOG_LEVEL" ]
then
    if [ -n "$logLevel" ]
    then
        LOG_LEVEL="$logLevel"
    else
        LOG_LEVEL="INFO"
    fi
fi
if [ -n "$LOG_LEVEL" ]
then
    COMMAND_LINE_OPTS="$COMMAND_LINE_OPTS -logLevel $LOG_LEVEL"
fi

if [ -z "$JVM_OPTIONS" ]
then
    if [ -n "$jvmOptions" ]
    then
        JVM_OPTIONS="$jvmOptions"
    fi
fi

prep_term
runCommand "java -XX:+UseContainerSupport -XX:MaxRAMPercentage=90.0 ${JVM_OPTIONS} -Dfile.encoding=UTF-8 -jar /opt/mockserver/mockserver-netty-jar-with-dependencies.jar ${COMMAND_LINE_OPTS} &"
wait_term
