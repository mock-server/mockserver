#!/bin/bash

dput >> /dev/null

if [ $? -ne 0 ]; then
    echo
    echo "[WARNING] dput is not installed so debian package uploaded to repository"
    echo
    exit
fi

dput -d mockserver $1
