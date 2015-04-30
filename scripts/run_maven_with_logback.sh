#!/bin/bash

M2_LIB_DIRECTORY=`mvn --version | grep "Maven home" | awk '{print ($3 "/lib")}'`

echo "Maven lib directory is: $M2_LIB_DIRECTORY"

# ls $M2_LIB_DIRECTORY

LOGBACK_CLASSIC_JAR="logback-classic-1.1.3.jar"
LOGBACK_CORE_JAR="logback-core-1.1.3.jar"
SLF4J_JAR="slf4j-api-1.7.12.jar"

echo "Downloading logback 1.1.3 libraries"

wget -q -O $LOGBACK_CLASSIC_JAR http://search.maven.org/remotecontent?filepath=ch/qos/logback/logback-classic/1.1.3/logback-classic-1.1.3.jar
wget -q -O $LOGBACK_CORE_JAR http://search.maven.org/remotecontent?filepath=ch/qos/logback/logback-core/1.1.3/logback-core-1.1.3.jar
wget -q -O $SLF4J_JAR http://search.maven.org/remotecontent?filepath=org/slf4j/slf4j-api/1.7.12/slf4j-api-1.7.12.jar


echo "Copying logback 1.1.3 libraries to maven lib directory"

cp "$LOGBACK_CLASSIC_JAR" "$M2_LIB_DIRECTORY"
cp "$LOGBACK_CORE_JAR" "$M2_LIB_DIRECTORY"
cp "$SLF4J_JAR" "$M2_LIB_DIRECTORY"

# disable current maven logging jars
mv "$M2_LIB_DIRECTORY/slf4j-simple-1.7.5.jar" "$M2_LIB_DIRECTORY/slf4j-simple-1.7.5.old"
mv "$M2_LIB_DIRECTORY/slf4j-api-1.7.5.jar" "$M2_LIB_DIRECTORY/slf4j-api-1.7.5.old"

# ls $M2_LIB_DIRECTORY

echo "Adding logback configuration file to MAVEN_OPTS"

echo "Current MAVEN_OPTS: $MAVEN_OPTS"

export MAVEN_OPTS="$MAVEN_OPTS -Dlogback.configurationFile=/Users/james.bloom/git/mockserver/mockserver-netty/src/main/resources/example_logback.xml"

echo "Updated MAVEN_OPTS: $MAVEN_OPTS"

./scripts/travis_build.sh

echo "Removing downloaded logback 1.1.3 libraries"

# delete locally
rm "$LOGBACK_CLASSIC_JAR"
rm "$LOGBACK_CORE_JAR"
rm "$SLF4J_JAR"

# delete from maven lib directory
rm "$M2_LIB_DIRECTORY/$LOGBACK_CLASSIC_JAR"
rm "$M2_LIB_DIRECTORY/$LOGBACK_CORE_JAR"
rm "$M2_LIB_DIRECTORY/$SLF4J_JAR"

# put back previous maven logging jars
mv "$M2_LIB_DIRECTORY/slf4j-simple-1.7.5.old" "$M2_LIB_DIRECTORY/slf4j-simple-1.7.5.jar"
mv "$M2_LIB_DIRECTORY/slf4j-api-1.7.5.old" "$M2_LIB_DIRECTORY/slf4j-api-1.7.5.jar"