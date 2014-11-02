#!/usr/bin/env bash

`ps -ef | grep mockserver | grep -v grep | grep -v $0 | awk '{print $2}' | xargs -t -I '{}' kill '{}'`; echo done

