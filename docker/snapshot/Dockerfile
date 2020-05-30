#
# MockServer Dockerfile
#
# https://github.com/mock-server/mockserver
# http://www.mock-server.com
#

# build image
FROM alpine as build

# download jar
RUN apk add --update openssl ca-certificates bash wget
# REPOSITORY is releases or snapshots
ARG REPOSITORY=snapshots
# VERSION is LATEST or x.x.x
ARG VERSION=LATEST
# see: https://oss.sonatype.org/nexus-restlet1x-plugin/default/docs/path__artifact_maven_redirect.html
ARG REPOSITORY_URL=https://oss.sonatype.org/service/local/artifact/maven/redirect?r=${REPOSITORY}&g=org.mock-server&a=mockserver-netty&c=jar-with-dependencies&e=jar&v=${VERSION}
RUN wget --max-redirect=10 -O mockserver-netty-jar-with-dependencies.jar "$REPOSITORY_URL"

# runtime image
FROM gcr.io/distroless/java:11

# maintainer details
MAINTAINER James Bloom "jamesdbloom@gmail.com"

# expose ports.
EXPOSE 1080

# copy in jar
COPY --from=build mockserver-netty-jar-with-dependencies.jar /

# don't run MockServer as root
#RUN addgroup -g 1000 mockserver && \
#    adduser -H -D -u 1000 -G mockserver mockserver
#RUN chown -R mockserver:mockserver /mockserver
#USER mockserver

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=90.0", "-Dfile.encoding=UTF-8", "-jar", "/mockserver-netty-jar-with-dependencies.jar"]

CMD ["-logLevel", "INFO", "-serverPort", "1080"]
