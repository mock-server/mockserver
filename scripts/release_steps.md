Release Steps
1. update OpenAPI Specification link
   1. update org.mockserver.validator.jsonschema.JsonSchemaValidator.OPEN_API_SPECIFICATION_URL to the correct URL
1. publish RELEASE to maven central 
   1. ./scripts/local_release.sh
   1. https://oss.sonatype.org/index.html#stagingRepositories
1. publish SNAPSHOT to sonatype 
   1. ./scripts/local_deploy_snapshot.sh
1. update mockserver-node
   1. rm -rf package-lock.json node_modules
   1. find and replace MockServer version
   1. npm i
   1. grunt
   1. git push origin master
   1. git tag mockserver-x.x.x 
   1. git push origin --tags
   1. npm login
   1. npm publish --access=public
1. update mockserver-client-node
   1. rm -rf package-lock.json node_modules
   1. find and replace MockServer version
   1. npm i
   1. grunt
   1. git push origin master
   1. git tag mockserver-x.x.x 
   1. git push origin --tags
   1. npm login (not required if done recently)
   1. npm publish --access=public
1. update mockserver-maven-plugin
   1. update parent pom SNAPSHOT version to RELEASE version
   1. update jar-with-dependencies SNAPSHOT version to RELEASE version
   1. ./scripts/local_deploy_snapshot.sh
   1. git push origin master
   1. ./scripts/local_release.sh
   1. update parent pom RELEASE version to new SNAPSHOT version
   1. update jar-with-dependencies RELEASE version to new SNAPSHOT version
   1. ./scripts/local_deploy_snapshot.sh
1. update docker image
   1. update Dockerfile
   1. docker build -t mockserver/mockserver:mockserver-x.x.x ./docker
   1. docker build -t jamesdbloom/mockserver:mockserver-x.x.x ./docker
   1. docker login
   1. docker push mockserver/mockserver:mockserver-x.x.x
   1. docker push jamesdbloom/mockserver:mockserver-x.x.x
1. update helm chart
   1. find and replace previous MockServer release version to new release
   1. cd helm
   1. helm package ./mockserver/
   1. upload to S3 https://s3.console.aws.amazon.com/s3/buckets/aws-website-mockserver-nb9hq
1. update repo
   1. update changelog
   1. delete jekyll-www.mock-server.com/_site
   1. ./mvnw clean
   1. find and replace maven / npm version references
   1. find and replace swagger version references (i.e. maven version with bugfix version as x)
   1. update README
   1. commit to github
1. add javaDoc
   1. git checkout mockserver-x.x.x
   1. ./mvnw javadoc:aggregate -P release -DreportOutputDirectory='/Users/jamesbloom/git/mockserver/javadoc/x.x.x'
   1. open /Users/jamesbloom/git/mockserver/javadoc
   1. upload as public to S3 https://s3.console.aws.amazon.com/s3/buckets/aws-website-mockserver-nb9hq/versions/?region=us-east-1
   1. git checkout master
1. update swaggehub
   1. update mockserver-core/src/main/resources/org/mockserver/openapi/mock-server-openapi-embedded-model.yaml
   1. login to https://app.swaggerhub.com/apis/jamesdbloom/mock-server-openapi
   1. create new version
   1. publish version
   1. update references to 5.x.x (i.e. 5.8.x) to correct version
1. update www.mock-server.com
   1. find and replace MockServer version
   1. upload to S3
      1. cd jekyll-www.mock-server.com
      1. rm -rf _site
      1. `rbenv which bundle` exec jekyll build
      1. cd _site
      1. copy to https://s3.console.aws.amazon.com/s3/buckets/aws-website-mockserver-nb9hq/?region=us-east-1
      1. invalidate CloudFront cache
1. create copy of document / website for existing version (for minor or major releases)
   1. create S3 bucket cloning permissions from existing
   1. copy public policy
   1. clone existing website: aws s3 sync s3://aws-website-mockserver-nb9hq s3://aws-website-mockserver--8001a --source-region us-east-1 --region eu-west-2 (**Note:** prepend with credentials from secrets manager)
   1. delete `versions` and helm charts
   1. create cloud front distribution copying existing settings
   1. create Route53 A record as alias to cloud front distribution
   1. ensure links in README are correct
1. update homebrew
   1. brew doctor
   1. delete https://github.com/jamesdbloom/homebrew-core
   1. git -C "$(brew --repo homebrew/core)" checkout master
   1. git -C "$(brew --repo homebrew/core)" branch -D mockserver-5.8.1
   1. git -C "$(brew --repo homebrew/core)" reset --hard HEAD
   1. brew update
   1. brew bump-formula-pr --strict mockserver --url=https://oss.sonatype.org/content/repositories/releases/org/mock-server/mockserver-netty/x.x.x/mockserver-netty-x.x.x-brew-tar.tar --sha256=...
   1. **Note:** use personal access token as password (due to lack of 2FA)