#!/usr/bin/env bash

set -v

#nvm use v16.13.2

# install project npm modules

#npm install grunt --global
npm cache verify
npm install --no-optional
npm audit fix

# run project build

grunt