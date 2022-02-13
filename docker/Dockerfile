#
# MockServer Dockerfile
#
# https://github.com/mock-server/mockserver
# https://www.mock-server.com
#

ARG source=download

# build image
FROM alpine as download

# download jar
RUN apk add --update openssl ca-certificates bash wget
# REPOSITORY is releases or snapshots
ARG REPOSITORY=releases
# VERSION is LATEST or RELEASE or x.x.x
ARG VERSION=RELEASE
# see: https://oss.sonatype.org/nexus-restlet1x-plugin/default/docs/path__artifact_maven_redirect.html
ARG REPOSITORY_URL=https://oss.sonatype.org/service/local/artifact/maven/redirect?r=${REPOSITORY}&g=org.mock-server&a=mockserver-netty&c=shaded&e=jar&v=${VERSION}
RUN wget --max-redirect=10 -O mockserver-netty-shaded.jar "$REPOSITORY_URL"

# build image
FROM alpine as copy

# copy jar
COPY mockserver-netty-shaded.jar .

FROM ${source} as intermediate

# runtime image
FROM gcr.io/distroless/java:11

# maintainer details
MAINTAINER James Bloom "jamesdbloom@gmail.com"

# expose ports.
EXPOSE 1080

# copy in jar
COPY --from=intermediate mockserver-netty-shaded.jar /

# don't run MockServer as root
USER nonroot

ENTRYPOINT ["java", "-Dfile.encoding=UTF-8", "-cp", "/mockserver-netty-shaded.jar:/libs/*", "-Dmockserver.propertyFile=/config/mockserver.properties", "org.mockserver.cli.Main"]

ENV SERVER_PORT 1080

CMD []
