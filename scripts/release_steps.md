## Release Steps

1. publish RELEASE to maven central
    1. ./scripts/local_release.sh
    2. https://oss.sonatype.org/index.html#stagingRepositories
    3. close -> release (auto drop)
2. publish SNAPSHOT to sonatype
    3. ./scripts/local_deploy_snapshot.sh
3. update repo
    4. update changelog
    5. update README
    6. ./mvnw clean && rm -rf jekyll-www.mock-server.com/_site
    8. update jekyll-www.mock-server.com/_config.yml to new mockserver versions
    9. find and replace maven / npm version references include 5.15.x, 5.15.0 and 5.15.1-SNAPSHOT (to new SNAPSHOT version)
    10. find and replace swagger version references (i.e. in website code example documentation) - 5.15.x mentioned in item above
    11. find and replace SNAPSHOT version references - 5.15.1-SNAPSHOT mentioned in item above
    12. git add -A && git commit -m "updates after a release" && git push origin master
4. update mockserver-node
    1. git pull --rebase 
    2. rm -rf package-lock.json node_modules
    2. find and replace MockServer version both 5.15.0 and 5.15.x
    3. nvm use v16.14.1
    4. npm i &&  npm audit fix
    5. grunt
    6. git add -A && git commit -m "upgraded to MockServer 5.15.0" && git push origin master && git tag mockserver-5.15.0 && git push origin --tags
    11. npm login
    12. npm publish --access=public --otp=****
5. update mockserver-client-node
    1. rm -rf package-lock.json node_modules
    2. find and replace MockServer version both 5.15.0 and 5.15.x
    3. nvm use v16.14.1
    4. npm i
    5. grunt
    6. git add -A && git commit -m "upgraded to MockServer 5.15.0" && git push origin master && git tag mockserver-5.15.0 && git push origin --tags
    9. npm login (not required if done recently)
    10. npm publish --access=public --otp=****
6. update mockserver-maven-plugin
    1. update parent pom SNAPSHOT version to RELEASE version
    2. update jar-with-dependencies SNAPSHOT version to RELEASE version
    3. update integration-testing SNAPSHOT version to RELEASE version
    4. ./scripts/local_deploy_snapshot.sh
    5. git add -A && git commit -m "upgraded to MockServer 5.15.0" && git push origin master
    6. ./scripts/local_release.sh
    7. release on Maven https://oss.sonatype.org/index.html#stagingRepositories
    8. close -> release (auto drop)
    9. update parent pom RELEASE version to new SNAPSHOT version
    10. update jar-with-dependencies RELEASE version to new SNAPSHOT version
    11. update integration-testing RELEASE version to new SNAPSHOT version
    12. ./scripts/local_deploy_snapshot.sh
    13. git add -A && git commit -m "updates after a release" && git push origin master
7. update docker image
    1. ensure maven returns the latest release
        1. curl -v https://oss.sonatype.org/service/local/artifact/maven/redirect\?r\=releases\&g\=org.mock-server\&a\=mockserver-netty\&c\=shaded\&e\=jar\&v\=RELEASE
    2. re-run git hub actions pipeline to ensure container has latest release https://github.com/mock-server/mockserver/actions/workflows/build-docker-image.yml
8. update helm chart
    1. find and replace previous MockServer release version to new release
    2. cd helm
    3. helm package ./mockserver/
    4. cd ~/git/mockserver/mockserver/helm/charts
    5. mv /Users/jamesbloom/git/mockserver/mockserver/helm/mockserver-5.15.0.tgz .
    6. helm repo index .
    7. open . && open https://s3.console.aws.amazon.com/s3/buckets/aws-website-mockserver-nb9hq
    8. upload new chart and index.yaml to S3 https://s3.console.aws.amazon.com/s3/buckets/aws-website-mockserver-nb9hq
    9. git add -A && git commit -m "added new heml chart release" && git pull --rebase && git push origin master
9. add javaDoc
    1. git checkout mockserver-5.15.0
    2. export JAVA_HOME=`/usr/libexec/java_home -v 1.8` or export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_201.jdk/Contents/Home
    3. ./mvnw javadoc:aggregate -P release -DreportOutputDirectory='/Users/jamesbloom/git/mockserver/javadoc/5.15.0'
    4. open /Users/jamesbloom/git/mockserver/javadoc && open https://s3.console.aws.amazon.com/s3/buckets/aws-website-mockserver-nb9hq/versions/?region=us-east-1
    5. upload as public to S3 https://s3.console.aws.amazon.com/s3/buckets/aws-website-mockserver-nb9hq/versions/?region=us-east-1
    6. git checkout master
10. update swaggerhub
    1. update mockserver-core/src/main/resources/org/mockserver/openapi/mock-server-openapi-embedded-model.yaml
    2. login to https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi
    3. create new version
    4. publish version
    5. update references to 5.x.x (i.e. 5.15.x) to correct version
11. update www.mock-server.com
    1. find and replace MockServer version
    2. upload to S3
        1. cd jekyll-www.mock-server.com
        2. rm -rf _site
        3. `rbenv which bundle` exec jekyll build
        4. open _site && open https://s3.console.aws.amazon.com/s3/buckets/aws-website-mockserver-nb9hq/?region=us-east-1
        5. copy to https://s3.console.aws.amazon.com/s3/buckets/aws-website-mockserver-nb9hq/?region=us-east-1
        6. invalidate CloudFront cache i.e. using `/*` here: https://console.aws.amazon.com/cloudfront/v3/home?region=us-east-1#/distributions/E3R1W2C7JJIMNR/invalidations
12. create copy of document / website for existing version (for minor or major releases)
    1. create S3 bucket cloning permissions from existing
    2. copy public policy
    3. enabled ACLs and set `Object writer` object ownership
    4. turn off `Block all public access`
    5. upload to S3
        1. cd jekyll-www.mock-server.com
        2. rm -rf _site
        3. `rbenv which bundle` exec jekyll build
        4. cd _site
        5. copy to new bucket
    6. create cloud front distribution copying existing settings
    7. make sure to set the default root object to "index.html"
    8. create Route53 A record as alias to cloud front distribution
    9. ensure links in README are correct
13. update homebrew
    1. brew doctor
    2. delete https://github.com/jamesdbloom/homebrew-core
    3. rename forked repos if they exist i.e. https://github.com/jamesdbloom/homebrew-core-1 to https://github.com/jamesdbloom/homebrew-core
    4. git -C "$(brew --repo homebrew/core)" checkout master
    5. git -C "$(brew --repo homebrew/core)" branch -D bump-mockserver-5.15.0
    6. git -C "$(brew --repo homebrew/core)" reset --hard HEAD
    7. brew update
    8. HOMEBREW_GITHUB_API_TOKEN=<token value> **Note:** use personal access token as password (due to lack of 2FA) - if expired create new token with full repo access only
    9. brew bump-formula-pr --strict mockserver --url="https://search.maven.org/remotecontent?filepath=org/mock-server/mockserver-netty/5.15.0/mockserver-netty-5.15.0-brew-tar.tar"

## Cleanup Failed Release Steps

1. revert git and do forced push
    1. git reset --hard 4fa8917c8
    2. git push --force
2. delete tags
    1. git tag -d mockserver-5.15.0
    2. git push origin :refs/tags/mockserver-5.15.0
3. drop staging repository https://oss.sonatype.org/#stagingRepositories