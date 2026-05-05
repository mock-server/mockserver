#!/usr/bin/env bash

# install project npm modules

npm cache verify
npm install --no-optional

# run project build

grunt headless