#!/usr/bin/env bash

# java 1.6 build
export JAVA_HOME=/System/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Home
mvn release:clean && mvn release:prepare && mvn release:perform

if [ $? -eq 0 ]; then
    # java 1.7 build
    export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.7.0_17.jdk/Contents/Home

    cd mockserver-jetty
    mvn release:clean && mvn release:prepare && mvn release:perform
    mvn gpg:sign-and-deploy-file -DpomFile=target/mockserver-vertx-2.6.pom -Dfile=target/mockserver-vertx-2.6.jar -Dfiles=target/mockserver-vertx-2.6-javadoc.jar,target/mockserver-vertx-2.6-sources.jar,target/org.mock-server~mockserver-vertx~2.6-mod.zip -Dclassifiers=javadoc,sources,mod -Dtypes=jar,jar,zip -Durl=https://oss.sonatype.org/service/local/staging/deploy/maven2/ -DrepositoryId=sonatype-nexus-staging

    cd ../mockserver-vertx
    mvn release:clean && mvn release:prepare && mvn release:perform
    mvn gpg:sign-and-deploy-file -DpomFile=target/mockserver-vertx-2.6.pom -Dfile=target/mockserver-vertx-2.6.jar -Dfiles=target/mockserver-vertx-2.6-javadoc.jar,target/mockserver-vertx-2.6-sources.jar,target/org.mock-server~mockserver-vertx~2.6-mod.zip -Dclassifiers=javadoc,sources,mod -Dtypes=jar,jar,zip -Durl=https://oss.sonatype.org/service/local/staging/deploy/maven2/ -DrepositoryId=sonatype-nexus-staging

    cd ../mockserver-examples
    mvn release:clean && mvn release:prepare && mvn release:perform
    mvn gpg:sign-and-deploy-file -DpomFile=target/mockserver-examples-2.6.pom -Dfile=target/mockserver-examples-2.6.war -Dfiles=target/mockserver-examples-2.6-sources.jar,target/mockserver-examples-2.6-javadoc.jar -Dclassifiers=javadoc,sources -Dtypes=jar,jar -Durl=https://oss.sonatype.org/service/local/staging/deploy/maven2/ -DrepositoryId=sonatype-nexus-staging
fi

#mvn gpg:sign-and-deploy-file -DpomFile=target/mockserver-vertx-2.6.pom -Dfile=target/mockserver-vertx-2.6.jar -Dfiles=target/mockserver-vertx-2.6-javadoc.jar,target/mockserver-vertx-2.6-sources.jar,target/org.mock-server~mockserver-vertx~2.6-mod.zip -Dclassifiers=javadoc,sources,mod -Dtypes=jar,jar,zip -Durl=https://oss.sonatype.org/service/local/staging/deploy/maven2/ -DrepositoryId=sonatype-nexus-staging
#mvn gpg:sign-and-deploy-file -DpomFile=target/mockserver-jetty-2.6.pom -Dfile=target/mockserver-jetty-2.6.jar -Dfiles=target/mockserver-jetty-2.6-sources.jar,target/mockserver-jetty-2.6-javadoc.jar,target/mockserver-jetty-2.6-jar-with-dependencies.jar -Dclassifiers=javadoc,sources,jar-with-dependencies -Dtypes=jar,jar,jar -Durl=https://oss.sonatype.org/service/local/staging/deploy/maven2/ -DrepositoryId=sonatype-nexus-staging
#mvn gpg:sign-and-deploy-file -DpomFile=target/mockserver-examples-2.6.pom -Dfile=target/mockserver-examples-2.6.jar -Dfiles=target/mockserver-examples-2.6-sources.jar,target/mockserver-examples-2.6-javadoc.jar -Dclassifiers=javadoc,sources -Dtypes=jar,jar -Durl=https://oss.sonatype.org/service/local/staging/deploy/maven2/ -DrepositoryId=sonatype-nexus-staging

