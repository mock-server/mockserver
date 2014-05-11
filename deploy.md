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

2]^J3ha9KX;QU8~

Run commands:

    gpg --gen-key
    gpg --list-keys
    gpg --keyserver hkp://pool.sks-keyservers.net --send-keys A6BAB25C
    mvn clean deploy -> deploy SNAPSHOT
    mv .git/hooks .git/mooks && mvn release:clean && mvn release:prepare && mvn release:perform && mv .git/mooks .git/hooks
