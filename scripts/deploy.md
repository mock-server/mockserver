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

Run commands (DO NOT USE GPG2 THIS IS INCOMPATIBLE WITH MAVEN):

    $ gpg --gen-key
    
    gpg (GnuPG) 2.0.30; Copyright (C) 2015 Free Software Foundation, Inc.
    This is free software: you are free to change and redistribute it.
    There is NO WARRANTY, to the extent permitted by law.
    
    Please select what kind of key you want:
       (1) RSA and RSA (default)
       (2) DSA and Elgamal
       (3) DSA (sign only)
       (4) RSA (sign only)
    Your selection? 1
    RSA keys may be between 1024 and 4096 bits long.
    What keysize do you want? (2048) 4096
    Requested keysize is 4096 bits       
    Please specify how long the key should be valid.
             0 = key does not expire
          <n>  = key expires in n days
          <n>w = key expires in n weeks
          <n>m = key expires in n months
          <n>y = key expires in n years
    Key is valid for? (0) 3y
    Key expires at Wed Nov 10 10:40:35 2021 GMT
    Is this correct? (y/N) y
                            
    GnuPG needs to construct a user ID to identify your key.
    
    Real name: James Duncan Bloom <jamesdbloom@gmail.com>
    Invalid character in name                            
    Real name: James Duncan Bloom
    Email address: jamesdbloom@gmail.com
    Comment:                            
    You selected this USER-ID:
        "James Duncan Bloom <jamesdbloom@gmail.com>"
    
    $ gpg --list-keys
    
    gpg: checking the trustdb
    gpg: 3 marginal(s) needed, 1 complete(s) needed, PGP trust model
    gpg: depth: 0  valid:   1  signed:   0  trust: 0-, 0q, 0n, 0m, 0f, 1u
    gpg: next trustdb check due at 2021-11-10
    pub   4096R/603F270A 2018-11-11 [expires: 2021-11-10]
          Key fingerprint = 4711 5A53 6E6D 8459 005B  724D 550F 93AD 603F 270A
    uid       [ultimate] James Duncan Bloom <jamesdbloom@gmail.com>
    sub   4096R/6F7EFCF7 2018-11-11 [expires: 2021-11-10]
    
    $ gpg --keyserver hkp://pool.sks-keyservers.net --send-keys 603F270A 
    
    export JAVA_HOME=`/usr/libexec/java_home -v 1.7`
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
