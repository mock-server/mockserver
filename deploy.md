Release Process
===============

# Setting Maven Central Deployments

See:

https://docs.sonatype.org/display/Repository/Sonatype+OSS+Maven+Repository+Usage+Guide
https://oss.sonatype.org/index.html#nexus-search;quick~mockserver
http://oss.sonatype.org/content/repositories/snapshots/org/mock-server/mockserver/
https://docs.sonatype.org/display/Repository/How+To+Generate+PGP+Signatures+With+Maven

Add to maven settings.xml:

<settings>
  ...
  <servers>
    <server>
      <id>sonatype-nexus-snapshots</id>
      <username>your-jira-id</username>
      <password>your-jira-pwd</password>
    </server>
    <server>
      <id>sonatype-nexus-staging</id>
      <username>your-jira-id</username>
      <password>your-jira-pwd</password>
    </server>
  </servers>
  ...
</settings>

Run commands:

gpg --gen-key
gpg --list-keys
gpg --keyserver hkp://pool.sks-keyservers.net --send-keys A6BAB25C
mvn clean deploy -> deploy SNAPSHOT
mv .git/hooks .git/mooks && mvn release:clean && mvn release:prepare && mvn release:perform && mv .git/mooks .git/hooks

# Vert.X Module Deployment

For snapshots use other Maven repository: http://oss.sonatype.org/content/groups/staging/

# Support For Multiple Java Versions

 1. Disable git hooks

    mv .git/hooks .git/mooks

 2. update pom.xml of mockserver-examples, mockserver-jetty, mockserver-vertx to new release version (only Java 7 modules)

 3. Run build (from project root)

    export JAVA_HOME=/System/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Home
    mvn release:clean && mvn release:prepare && mvn release:perform

    if [ $? -eq 0 ]; then
        # java 1.7 build
        export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.7.0_17.jdk/Contents/Home
        mvn clean deploy
    fi

 4. Run the following command to upload the artifacts (from project root)

    mvn gpg:sign-and-deploy-file -DpomFile=/Users/jamesdbloom/.m2/repository/org/mock-server/mockserver-vertx/2.6/mockserver-vertx-2.6.pom -Dfile=mockserver-vertx/target/mockserver-vertx-2.6.jar -Dfiles=mockserver-vertx/target/mockserver-vertx-2.6-javadoc.jar,mockserver-vertx/target/mockserver-vertx-2.6-sources.jar,mockserver-vertx/target/org.mock-server~mockserver-vertx~2.6-mod.zip -Dclassifiers=javadoc,sources,mod -Dtypes=jar,jar,zip -Durl=https://oss.sonatype.org/service/local/staging/deploy/maven2/ -DrepositoryId=sonatype-nexus-staging -Dgpg.passphrase=Jim197
    mvn gpg:sign-and-deploy-file -DpomFile=/Users/jamesdbloom/.m2/repository/org/mock-server/mockserver-jetty/2.6/mockserver-jetty-2.6.pom -Dfile=mockserver-jetty/target/mockserver-jetty-2.6.jar -Dfiles=mockserver-jetty/target/mockserver-jetty-2.6-sources.jar,mockserver-jetty/target/mockserver-jetty-2.6-javadoc.jar,mockserver-jetty/target/mockserver-jetty-2.6-jar-with-dependencies.jar -Dclassifiers=javadoc,sources,jar-with-dependencies -Dtypes=jar,jar,jar -Durl=https://oss.sonatype.org/service/local/staging/deploy/maven2/ -DrepositoryId=sonatype-nexus-staging -Dgpg.passphrase=Jim197
    mvn gpg:sign-and-deploy-file -DpomFile=/Users/jamesdbloom/.m2/repository/org/mock-server/mockserver-examples/2.6/mockserver-examples-2.6.pom -Dfile=mockserver-examples/target/mockserver-examples-2.6.war -Dfiles=mockserver-examples/target/mockserver-examples-2.6-sources.jar,mockserver-examples/target/mockserver-examples-2.6-javadoc.jar -Dclassifiers=javadoc,sources -Dtypes=jar,jar -Durl=https://oss.sonatype.org/service/local/staging/deploy/maven2/ -DrepositoryId=sonatype-nexus-staging -Dgpg.passphrase=Jim197

 5. Re-enable git hooks

    mv .git/mooks .git/hooks


