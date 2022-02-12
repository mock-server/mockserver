Release Steps

1. update OpenAPI Specification link
    1. update org.mockserver.validator.jsonschema.JsonSchemaValidator.OPEN_API_SPECIFICATION_URL to the correct URL
2. publish RELEASE to maven central
    1. ./scripts/local_release.sh
    2. https://oss.sonatype.org/index.html#stagingRepositories
3. publish SNAPSHOT to sonatype
    3. ./scripts/local_deploy_snapshot.sh
4. update mockserver-node
    1. rm -rf package-lock.json node_modules
    2. find and replace MockServer version
    3. npm i
    4. grunt
    5. git push origin master
    6. git tag mockserver-x.x.x
    7. git push origin --tags
    8. npm login
    9. npm publish --access=public --otp=****
5. update mockserver-client-node
    1. rm -rf package-lock.json node_modules
    2. find and replace MockServer version
    3. npm i
    4. grunt
    5. git push origin master
    6. git tag mockserver-x.x.x
    7. git push origin --tags
    8. npm login (not required if done recently)
    9. npm publish --access=public --otp=****
6. update mockserver-maven-plugin
    1. update parent pom SNAPSHOT version to RELEASE version
    2. update jar-with-dependencies SNAPSHOT version to RELEASE version
    3. ./scripts/local_deploy_snapshot.sh
    4. git push origin master
    5. ./scripts/local_release.sh
    6. update parent pom RELEASE version to new SNAPSHOT version
    7. update jar-with-dependencies RELEASE version to new SNAPSHOT version
    8. /scripts/local_deploy_snapshot.sh
    9. release on Maven https://oss.sonatype.org/index.html#stagingRepositories
7. update docker image
    1. ensure maven returns the latest release
        1. curl -v https://oss.sonatype.org/service/local/artifact/maven/redirect\?r\=releases\&g\=org.mock-server\&a\=mockserver-netty\&c\=jar-with-dependencies\&e\=jar\&v\=RELEASE
    2. update Dockerfile (no longer required)
    3. docker build --no-cache -t mockserver/mockserver:mockserver-x.x.x ./docker
    4. docker build --no-cache -t jamesdbloom/mockserver:mockserver-x.x.x ./docker
    5. docker login
    6. docker push mockserver/mockserver:mockserver-x.x.x
    7. docker push jamesdbloom/mockserver:mockserver-x.x.x
8. update helm chart
    1. find and replace previous MockServer release version to new release
    2. cd helm
    3. helm package ./mockserver/
    4. cd ~/git/mockserver/mockserver/helm/charts
    5. helm repo index .
    6. upload new chart and index.yaml to S3 https://s3.console.aws.amazon.com/s3/buckets/aws-website-mockserver-nb9hq
9. update repo
    1. update changelog
    2. rm -rf jekyll-www.mock-server.com/_site
    3. ./mvnw clean
    4. find and replace maven / npm version references
    5. find and replace swagger version references (i.e. in website code example documentation)
    6. update README
    7. commit to github
10. add javaDoc
    1. git checkout mockserver-x.x.x
    2. export JAVA_HOME=`/usr/libexec/java_home -v 1.8` or export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_201.jdk/Contents/Home
    3. ./mvnw javadoc:aggregate -P release -DreportOutputDirectory='/Users/jamesbloom/git/mockserver/javadoc/x.x.x'
    4. open /Users/jamesbloom/git/mockserver/javadoc
    5. upload as public to S3 https://s3.console.aws.amazon.com/s3/buckets/aws-website-mockserver-nb9hq/versions/?region=us-east-1
    6. git checkout master
11. update swaggehub
    1. update mockserver-core/src/main/resources/org/mockserver/openapi/mock-server-openapi-embedded-model.yaml
    2. login to https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi
    3. create new version
    4. publish version
    5. update references to 5.x.x (i.e. 5.12.x) to correct version
12. update www.mock-server.com
    1. find and replace MockServer version
    2. upload to S3
        1. cd jekyll-www.mock-server.com
        2. rm -rf _site
        3. `rbenv which bundle` exec jekyll build
        4. cd _site
        5. copy to https://s3.console.aws.amazon.com/s3/buckets/aws-website-mockserver-nb9hq/?region=us-east-1
        6. invalidate CloudFront cache i.e. using `/*` here: https://console.aws.amazon.com/cloudfront/v3/home?region=us-east-1#/distributions/E3R1W2C7JJIMNR/invalidations
13. create copy of document / website for existing version (for minor or major releases)
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
14. update homebrew
    1. brew doctor
    2. delete https://github.com/jamesdbloom/homebrew-core
    3. git -C "$(brew --repo homebrew/core)" checkout master
    4. git -C "$(brew --repo homebrew/core)" branch -D mockserver-x.x.x
    5. git -C "$(brew --repo homebrew/core)" reset --hard HEAD
    6. brew update
    7. brew bump-formula-pr --strict mockserver --url=https://oss.sonatype.org/content/repositories/releases/org/mock-server/mockserver-netty/x.x.x/mockserver-netty-x.x.x-brew-tar.tar
    8. **Note:** use personal access token as password (due to lack of 2FA)