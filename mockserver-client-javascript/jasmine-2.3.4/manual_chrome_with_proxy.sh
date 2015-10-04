#!/usr/bin/env bash

NODE_PORT=1080
MOCK_SERVER_PORT=1081

################
## START NODE ##
################
npm install
node server.js $NODE_PORT $MOCK_SERVER_PORT &
NODE_PID=$!

######################
## START MOCKSERVER ##
######################
mvn -Dmockserver.serverPort=$MOCK_SERVER_PORT -Dmockserver.proxyPort=1090 -Dmockserver.logLevel=INFO org.mock-server:mockserver-maven-plugin:LATEST:runForked

##################
## START CHROME ##
##################
if [[ "$OSTYPE" == "linux-gnu" ]]; then
    echo "Detected Linux Operating System"
    GOOGLE_CHROME='google-chrome'
elif [[ "$OSTYPE" == "freebsd" ]]; then
    echo "Detected FreeBSD Operating System"
    GOOGLE_CHROME='google-chrome'
elif [[ "$OSTYPE" == "darwin"* ]]; then
    echo "Detected Apple Mac Operating System"
    GOOGLE_CHROME='/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'
elif [[ "$OSTYPE" == "cygwin" ]]; then
    echo "Detected Cygwin Operating System"
elif [[ "$OSTYPE" == "win32" ]]; then
    echo "Detected Windows32 Operating System"
    GOOGLE_CHROME="%ProgramFiles(x86)%\Google\Chrome\Application\chrome.exe"
else
    echo "Unknown operating system: {$OS}"
fi

USER_DATA_DIR=google/user/data/`date +'%s'`
echo "$GOOGLE_CHROME --user-data-dir=$USER_DATA_DIR --no-default-browser-check --no-first-run --disable-default-apps --disable-web-security --enable-potentially-annoying-security-features --enable-extensions --proxy-server=\"http://localhost:1090\" http://localhost:$NODE_PORT/SpecRunner.html?proxy=true"
"$GOOGLE_CHROME" --user-data-dir=$USER_DATA_DIR --no-default-browser-check --no-first-run --disable-default-apps --disable-web-security --enable-potentially-annoying-security-features --enable-extensions --proxy-server="http://localhost:1090" "http://localhost:$NODE_PORT/SpecRunner.html?proxy=true" &
CHROME_PID=$!

###############
## ADD TRAPS ##
###############
trap "kill $CHROME_PID" exit INT TERM
trap "sleep 1 && rm -rf google && mvn -Dmockserver.serverPort=$MOCK_SERVER_PORT -Dmockserver.proxyPort=1090 org.mock-server:mockserver-maven-plugin:LATEST:stopForked" EXIT

wait $CHROME_PID