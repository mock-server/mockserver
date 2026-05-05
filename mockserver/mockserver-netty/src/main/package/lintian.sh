#!/bin/bash

lintian >> /dev/null

if [ $? -ne 0 ]; then
    echo
    echo "[WARNING] lintian is not installed so debian package not linted"
    echo
    exit
fi

lintian --display-level certain --suppress-tags no-copyright-file,non-standard-dir-in-var $1