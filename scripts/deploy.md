Release Process Notes
=====================

See:

https://docs.sonatype.org/display/Repository/Sonatype+OSS+Maven+Repository+Usage+Guide
https://oss.sonatype.org/index.html#nexus-search;quick~mockserver
http://oss.sonatype.org/content/repositories/snapshots/org/mock-server/mockserver/
https://docs.sonatype.org/display/Repository/How+To+Generate+PGP+Signatures+With+Maven
http://blog.sonatype.com/2010/01/how-to-generate-pgp-signatures-with-maven/

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
    
    > pub   2048R/7E0E8A0A 2017-04-25
    > uid       [ultimate] James Duncan Bloom <jamesdbloom@gmail.com>
    > sub   2048R/FCF5FB31 2017-04-25
    
    gpg --keyserver hkp://pool.sks-keyservers.net --send-keys 7E0E8A0A 
    
    export JAVA_HOME=`/usr/libexec/java_home -v 1.6`
    mvn release:clean -Dgpg.passphrase=my_pass_phrase -Drelease.arguments="-DnonReleaseBuild=false -Dmaven.test.skip=true -DskipTests=true" && \
    mvn release:prepare -Dgpg.passphrase=my_pass_phrase -Drelease.arguments="-DnonReleaseBuild=false -Dmaven.test.skip=true -DskipTests=true" && \
    mvn release:perform -Dgpg.passphrase=my_pass_phrase -Drelease.arguments="-DnonReleaseBuild=false -Dmaven.test.skip=true -DskipTests=true"
    
Test username & password:
    
    settings.xml must contain:
    
    <servers>
        <server>
            <id>sonatype-nexus-snapshots</id>
            <username><username></username>
            <password><password></password>
        </server>
        <server>
            <id>sonatype-nexus-staging</id>
            <username><username></username>
            <password><password></password>
        </server>
    </servers>
    
    Note: username and password is the same values as for https://oss.sonatype.org/index.html#nexus-search;quick~mockserver
    
    curl -v -u <username>:<password> --upload-file pom.xml https://oss.sonatype.org/service/local/staging/deploy/maven2/org/mock-server/mockserver/3.10.5/mockserver-3.10.5.pom
    
    see: https://support.sonatype.com/hc/en-us/articles/213465818-How-can-I-programmatically-upload-an-artifact-into-Nexus-
    
Delete tag:

    git tag -d 12345
    git push origin :refs/tags/12345
    
Reset git history:

    git reset --hard <commit-hash>
    git push -f origin master
