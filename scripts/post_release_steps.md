Release Steps
1. ./scripts/local_release.sh
1. ./scripts/local_deploy_snapshot.sh
1. update mockserver-node
   1. delete package-lock.json
   1. find and replace MockServer version
   1. npm i
   1. grunt
   1. git push origin master
   1. git tag mockserver-x.x.x 
   1. git push origin --tags
   1. npm login
   1. npm publish
1. update mockserver-node
   1. delete package-lock.json
   1. find and replace MockServer version
   1. npm i
   1. grunt
   1. git push origin master
   1. git tag mockserver-x.x.x 
   1. git push origin --tags
   1. npm login (not required if done recently)
   1. npm publish
1. update mockserver-maven-plugin
   1. find and replace MockServer SNAPSHOT version to release
   1. ./scripts/local_release.sh
   1. find and replace MockServer release version to new SNAPSHOT
   1. ./scripts/local_deploy_snapshot.sh
1. update docker image
   1. update Dockerfile
   1. docker build -t jamesdbloom/mockserver:mockserver-x.x.x ./docker
   1. docker push jamesdbloom/mockserver:mockserver-x.x.x
1. update helm chart
   1. find and replace previous MockServer release version to new release
   1. cd helm
   1. helm package ./mockserver/
   1. upload to S3 https://s3.console.aws.amazon.com/s3/buckets/aws-website-mockserver-nb9hq
1. add javaDoc
   1. checkout release git hash
   1. ./mvnw javadoc:aggregate -P release -DreportOutputDirectory='/Users/jamesbloom/git/mockserver/javadoc/x.x.x'
   1. upload to S3
   1. update README.md
1. update www.mock-server.com
   1. find and replace MockServer version
   1. upload to S3
1. update homebrew
   1. https://oss.sonatype.org/content/repositories/releases/org/mock-server/mockserver-netty/x.x.x/mockserver-netty-x.x.x-brew-tar.tar
   1. shasum -a 256 mockserver-netty-x.x.x-brew-tar.tar
   1. brew bump-formula-pr --strict mockserver --url=https://oss.sonatype.org/content/repositories/releases/org/mock-server/mockserver-netty/x.x.x/mockserver-netty-x.x.x-brew-tar.tar --sha256=...