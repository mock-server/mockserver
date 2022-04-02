## Release Steps

1. publish RELEASE to maven central
    1. ./scripts/local_release.sh
    2. https://oss.sonatype.org/index.html#stagingRepositories
2. publish SNAPSHOT to sonatype
    3. ./scripts/local_deploy_snapshot.sh
3. update mockserver-node
    1. rm -rf package-lock.json node_modules
    2. find and replace MockServer version both 5.13.0 and 5.13.x
    3. npm i
    4. grunt
    5. git push origin master
    6. git tag mockserver-x.x.x
    7. git push origin --tags
    8. npm login
    9. npm publish --access=public --otp=****
4. update mockserver-client-node
    1. rm -rf package-lock.json node_modules
    2. find and replace MockServer version both 5.13.0 and 5.13.x
    3. npm i
    4. grunt
    5. git push origin master
    6. git tag mockserver-x.x.x
    7. git push origin --tags
    8. npm login (not required if done recently)
    9. npm publish --access=public --otp=****
5. update mockserver-maven-plugin
    1. update parent pom SNAPSHOT version to RELEASE version
    2. update shaded SNAPSHOT version to RELEASE version
    3. ./scripts/local_deploy_snapshot.sh
    4. git push origin master
    5. ./scripts/local_release.sh
    6. update parent pom RELEASE version to new SNAPSHOT version
    7. update shaded RELEASE version to new SNAPSHOT version
    8. /scripts/local_deploy_snapshot.sh
    9. release on Maven https://oss.sonatype.org/index.html#stagingRepositories
6. update docker image
    1. ensure maven returns the latest release
        1. curl -v https://oss.sonatype.org/service/local/artifact/maven/redirect\?r\=releases\&g\=org.mock-server\&a\=mockserver-netty\&c\=shaded\&e\=jar\&v\=RELEASE
    2. update Dockerfile (no longer required)
    3. docker build --no-cache -t mockserver/mockserver:mockserver-x.x.x ./docker
    4. docker build --no-cache -t mockserver/mockserver:x.x.x ./docker
    5. docker build --no-cache -t jamesdbloom/mockserver:mockserver-x.x.x ./docker
    6. docker login
    7. docker push mockserver/mockserver:mockserver-x.x.x
    8. docker push mockserver/mockserver:x.x.x
    9. docker push jamesdbloom/mockserver:mockserver-x.x.x
7. update helm chart
    1. find and replace previous MockServer release version to new release
    2. cd helm
    3. helm package ./mockserver/
    4. cd ~/git/mockserver/mockserver/helm/charts
    5. cp /Users/jamesbloom/git/mockserver/mockserver/helm/mockserver-x.x.x.tgz .
    6. helm repo index .
    7. upload new chart and index.yaml to S3 https://s3.console.aws.amazon.com/s3/buckets/aws-website-mockserver-nb9hq
8. update repo
    1. update changelog
    2. rm -rf jekyll-www.mock-server.com/_site
    3. ./mvnw clean
    4. find and replace maven / npm version references include 5.13.x, 5.13.0 and 5.13.1-SNAPSHOT (to new SNAPSHOT verion) 
    5. find and replace swagger version references (i.e. in website code example documentation)
    6. find and replace SNAPSHOT version references
    7. update README
    8. commit to github
9. add javaDoc
   1. git checkout mockserver-x.x.x
   2. export JAVA_HOME=`/usr/libexec/java_home -v 1.8` or export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_201.jdk/Contents/Home
   3. ./mvnw javadoc:aggregate -P release -DreportOutputDirectory='/Users/jamesbloom/git/mockserver/javadoc/x.x.x'
   4. open /Users/jamesbloom/git/mockserver/javadoc
   5. upload as public to S3 https://s3.console.aws.amazon.com/s3/buckets/aws-website-mockserver-nb9hq/versions/?region=us-east-1
   6. git checkout master
10. update swaggehub
    1. update mockserver-core/src/main/resources/org/mockserver/openapi/mock-server-openapi-embedded-model.yaml
    2. login to https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi
    3. create new version
    4. publish version
    5. update references to 5.x.x (i.e. 5.13.x) to correct version
11. update www.mock-server.com
    1. find and replace MockServer version
    2. upload to S3
        1. cd jekyll-www.mock-server.com
        2. rm -rf _site
        3. `rbenv which bundle` exec jekyll build
        4. cd _site
        5. copy to https://s3.console.aws.amazon.com/s3/buckets/aws-website-mockserver-nb9hq/?region=us-east-1
        6. invalidate CloudFront cache i.e. using `/*` here: https://console.aws.amazon.com/cloudfront/v3/home?region=us-east-1#/distributions/E3R1W2C7JJIMNR/invalidations
12. create copy of document / website for existing version (for minor or major releases)
    1. create S3 bucket cloning permissions from existing
    2. copy public policy
    3. enabled ACLs and set `Object writer` object ownership
    4. upload to S3
        1. cd jekyll-www.mock-server.com
        2. rm -rf _site
        3. `rbenv which bundle` exec jekyll build
        4. cd _site
        5. copy to new bucket
    5. create cloud front distribution copying existing settings
    6. make sure to set the default root object to "index.html"
    7. create Route53 A record as alias to cloud front distribution
    8. ensure links in README are correct
13. update homebrew
    1. brew doctor
    2. delete https://github.com/jamesdbloom/homebrew-core
    3. rename forked repos if they exist i.e. https://github.com/jamesdbloom/homebrew-core-1 to https://github.com/jamesdbloom/homebrew-core
    4. git -C "$(brew --repo homebrew/core)" checkout master
    5. git -C "$(brew --repo homebrew/core)" branch -D bump-mockserver-x.x.x
    6. git -C "$(brew --repo homebrew/core)" reset --hard HEAD
    7. brew update
    8. HOMEBREW_GITHUB_API_TOKEN=<token value> **Note:** use personal access token as password (due to lack of 2FA)
    9. brew bump-formula-pr --strict mockserver --url=https://oss.sonatype.org/content/repositories/releases/org/mock-server/mockserver-netty/x.x.x/mockserver-netty-x.x.x-brew-tar.tar

## Cleanup Failed Release Steps

1. revert git and do forced push
   1. git reset --hard 4fa8917c8
   2. git push --force
2. delete tags
   1. git tag -d mockserver-5.13.1
   2. git push origin :refs/tags/mockserver-5.13.1
3. drop staging repository https://oss.sonatype.org/#stagingRepositories