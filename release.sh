#!/usr/bin/env bash

# 1. update pom.xml of mockserver-examples, mockserver-jetty, mockserver-vertx

# java 1.6 build
export JAVA_HOME=/System/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Home
mvn release:clean && mvn release:prepare && mvn release:perform

if [ $? -eq 0 ]; then
    # java 1.7 build
    export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.7.0_17.jdk/Contents/Home
    mvn clean install
fi

mvn gpg:sign-and-deploy-file -DpomFile=/Users/jamesdbloom/.m2/repository/org/mock-server/mockserver-vertx/2.6/mockserver-vertx-2.6.pom -Dfile=mockserver-vertx/target/mockserver-vertx-2.6.jar -Dfiles=mockserver-vertx/target/mockserver-vertx-2.6-javadoc.jar,mockserver-vertx/target/mockserver-vertx-2.6-sources.jar,mockserver-vertx/target/org.mock-server~mockserver-vertx~2.6-mod.zip -Dclassifiers=javadoc,sources,mod -Dtypes=jar,jar,zip -Durl=https://oss.sonatype.org/service/local/staging/deploy/maven2/ -DrepositoryId=sonatype-nexus-staging -Dgpg.passphrase=Jim197
mvn gpg:sign-and-deploy-file -DpomFile=/Users/jamesdbloom/.m2/repository/org/mock-server/mockserver-jetty/2.6/mockserver-jetty-2.6.pom -Dfile=mockserver-jetty/target/mockserver-jetty-2.6.jar -Dfiles=mockserver-jetty/target/mockserver-jetty-2.6-sources.jar,mockserver-jetty/target/mockserver-jetty-2.6-javadoc.jar,mockserver-jetty/target/mockserver-jetty-2.6-jar-with-dependencies.jar -Dclassifiers=javadoc,sources,jar-with-dependencies -Dtypes=jar,jar,jar -Durl=https://oss.sonatype.org/service/local/staging/deploy/maven2/ -DrepositoryId=sonatype-nexus-staging -Dgpg.passphrase=Jim197
mvn gpg:sign-and-deploy-file -DpomFile=/Users/jamesdbloom/.m2/repository/org/mock-server/mockserver-examples/2.6/mockserver-examples-2.6.pom -Dfile=mockserver-examples/target/mockserver-examples-2.6.war -Dfiles=mockserver-examples/target/mockserver-examples-2.6-sources.jar,mockserver-examples/target/mockserver-examples-2.6-javadoc.jar -Dclassifiers=javadoc,sources -Dtypes=jar,jar -Durl=https://oss.sonatype.org/service/local/staging/deploy/maven2/ -DrepositoryId=sonatype-nexus-staging -Dgpg.passphrase=Jim197


