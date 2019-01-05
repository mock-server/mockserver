#!/usr/bin/env bash

function runCommand {
    echo
    printf -v str "%-$((${#1} + 19))s" ' '; echo "${str// /=}"
    echo "Executing command: $1"
    printf -v str "%-$((${#1} + 19))s" ' '; echo "${str// /=}"
    eval $1
    echo
}

COMMIT_LIST="5.5.1-11d8a96b0eaf07b7fffd29444203503b1cdca653 5.5.0-06e6fdc4757f13fb5943fc281d5e55dc1c30919d 5.4.1-7cd5defc7463e8773d011467147a8a0f7e7b4af8 5.3.0-ad62bbc4fdc1470818ffab14630623dc591ead74 5.2.3-e81c53852b763f88b2399090ef414f074b3e3d81 5.2.2-b47090b579d35c7136b84378402ff466db0bfb60 5.2.1-834ec8fcac335b10d09183cecfe6dae358a4080c 5.2.0-ccb4d241b55dcebc9f8abfb3722cadad143f3acf 5.1.1-664afb2c539333ce89559fb3153e56bc48ba9cb5 5.1.0-bbdda1898eb3f396d56f7268faa6c2a644449ae3 5.0.1-975fb8971da1cd32891201733a2bc6aa4080d7ae 5.0.0-ed5d13e863a25e00ab404735e183df2ce4afe635 4.1.0-4e37b27b9b1bc786d0b5f53d5f1a39dd457f5d34 4.0.0-8b24553c6b7aabbe4ef5e99b37449330f5b908d7 3.12-d2a2b1b7399e8405f2d19bc105c99a0a26327c61 3.11-fdcb1113ecd075ec7d9b1d065ed778dadebb1772 3.10.8-0e6e1227f5e3d5d9faa68434d3ed708edee7b9ee 3.10.7-99abb290e31e9a65706e64a360f4ad318723f0ba 3.10.6-2198972a3911efcf0fa116f4cdd0851ab31699c1 3.10.5-a59e750432f9d9431c1c6352953e1309d53178fc 3.10.4-cbe92f844f4357230b16ae29fe97cf177bd0d757"

for ITEM in $COMMIT_LIST; do
	VERSION=$(echo $ITEM | cut -d'-' -f 1)
    COMMIT=$(echo $ITEM | cut -d'-' -f 2)
    runCommand "git checkout $COMMIT"
	runCommand "./mvnw javadoc:aggregate -DreportOutputDirectory='/Users/jamesbloom/git/mockserver/javadoc/$VERSION'"
done

runCommand "git checkout master"
