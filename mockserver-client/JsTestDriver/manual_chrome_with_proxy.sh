#!/usr/bin/env bash

#
# Run chrome with extension and without cross origin security
#

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
echo "$GOOGLE_CHROME --user-data-dir=$USER_DATA_DIR --no-default-browser-check --no-first-run --disable-default-apps --disable-web-security --enable-extensions file://$(pwd)/SpecRunner.html?proxy=true"
"$GOOGLE_CHROME" --user-data-dir=$USER_DATA_DIR --no-default-browser-check --no-first-run --disable-default-apps --disable-web-security --enable-extensions --proxy-server="http://localhost:1090" "file://$(pwd)/SpecRunner.html?proxy=true" &
PID=$!

trap "kill $PID" exit INT TERM
trap "sleep 1 && rm -rf google" EXIT

wait