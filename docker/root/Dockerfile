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
ARG REPOSITORY_URL=https://oss.sonatype.org/service/local/artifact/maven/redirect?r=${REPOSITORY}&g=org.mock-server&a=mockserver-netty&c=jar-with-dependencies&e=jar&v=${VERSION}
RUN wget --max-redirect=10 -O mockserver-netty-jar-with-dependencies.jar "$REPOSITORY_URL"
# add netty-tcnative-boringssl so file
RUN wget --max-redirect=10 -O netty-tcnative-boringssl-static.jar "https://oss.sonatype.org/service/local/artifact/maven/redirect?r=releases&g=io.netty&a=netty-tcnative-boringssl-static&c=linux-x86_64&e=jar&v=2.0.50.Final"
RUN unzip netty-tcnative-boringssl-static.jar

# build image
FROM alpine as copy

# copy jar
COPY mockserver-netty-jar-with-dependencies.jar .
# add netty-tcnative-boringssl so file
RUN apk add --update openssl ca-certificates bash wget
RUN wget --max-redirect=10 -O netty-tcnative-boringssl-static.jar "https://oss.sonatype.org/service/local/artifact/maven/redirect?r=releases&g=io.netty&a=netty-tcnative-boringssl-static&c=linux-x86_64&e=jar&v=2.0.50.Final"
RUN unzip netty-tcnative-boringssl-static.jar

FROM ${source} as intermediate

# runtime image https://console.cloud.google.com/gcr/images/distroless/global/java17
FROM gcr.io/distroless/java17

# maintainer details
MAINTAINER James Bloom "jamesdbloom@gmail.com"

# expose ports.
EXPOSE 1080

# copy in jar
COPY --from=intermediate mockserver-netty-jar-with-dependencies.jar /
COPY --from=intermediate META-INF/native/libnetty_tcnative_linux_x86_64.so /usr/lib/x86_64-linux-gnu/libnetty_tcnative_linux_x86_64.so

# run MockServer as root
USER root

ENTRYPOINT ["java", "-Dfile.encoding=UTF-8", "-cp", "/mockserver-netty-jar-with-dependencies.jar:/libs/*", "-Dmockserver.propertyFile=/config/mockserver.properties", "org.mockserver.cli.Main"]

ENV SERVER_PORT 1080

CMD []
