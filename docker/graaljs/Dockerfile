#
# MockServer Dockerfile — GraalJS variant
#
# Includes GraalJS JavaScript engine for JavaScript templating support.
# Use this image if you need JavaScript-based response/forward templates.
#
# https://github.com/mock-server/mockserver
# https://www.mock-server.com
#

ARG source=download

# build image — downloads release artifacts from Sonatype, tcnative from Maven Central
FROM alpine as download

RUN apk add --update openssl ca-certificates bash wget
ARG VERSION=RELEASE
ARG REPOSITORY_URL=https://repo1.maven.org/maven2/org/mock-server/mockserver-netty/${VERSION}/mockserver-netty-${VERSION}-jar-with-dependencies.jar
RUN wget --max-redirect=10 -O mockserver-netty-jar-with-dependencies.jar "$REPOSITORY_URL"
ARG TARGETARCH=amd64
ARG NETTY_TCNATIVE=2.0.77.Final
RUN if [ "$TARGETARCH" = "amd64" ]; \
    then wget -O netty-tcnative-boringssl-static.jar "https://repo1.maven.org/maven2/io/netty/netty-tcnative-boringssl-static/$NETTY_TCNATIVE/netty-tcnative-boringssl-static-$NETTY_TCNATIVE-linux-x86_64.jar"; \
    else wget -O netty-tcnative-boringssl-static.jar "https://repo1.maven.org/maven2/io/netty/netty-tcnative-boringssl-static/$NETTY_TCNATIVE/netty-tcnative-boringssl-static-$NETTY_TCNATIVE-linux-aarch_64.jar"; \
    fi
RUN unzip netty-tcnative-boringssl-static.jar

# build image — copies JAR from context, downloads tcnative from Maven Central
FROM alpine as copy

RUN apk add --update ca-certificates wget
COPY mockserver-netty-jar-with-dependencies.jar .
ARG TARGETARCH=amd64
ARG NETTY_TCNATIVE=2.0.77.Final
RUN if [ "$TARGETARCH" = "amd64" ]; \
    then wget -O netty-tcnative-boringssl-static.jar "https://repo1.maven.org/maven2/io/netty/netty-tcnative-boringssl-static/$NETTY_TCNATIVE/netty-tcnative-boringssl-static-$NETTY_TCNATIVE-linux-x86_64.jar"; \
    else wget -O netty-tcnative-boringssl-static.jar "https://repo1.maven.org/maven2/io/netty/netty-tcnative-boringssl-static/$NETTY_TCNATIVE/netty-tcnative-boringssl-static-$NETTY_TCNATIVE-linux-aarch_64.jar"; \
    fi
RUN unzip netty-tcnative-boringssl-static.jar

# download GraalJS JARs
FROM alpine as graaljs

RUN apk add --update ca-certificates wget
ARG GRAALJS_VERSION=24.1.1
RUN mkdir -p /graaljs && \
    wget -O /graaljs/polyglot.jar "https://repo1.maven.org/maven2/org/graalvm/polyglot/polyglot/$GRAALJS_VERSION/polyglot-$GRAALJS_VERSION.jar" && \
    wget -O /graaljs/js-language.jar "https://repo1.maven.org/maven2/org/graalvm/js/js-language/$GRAALJS_VERSION/js-language-$GRAALJS_VERSION.jar" && \
    wget -O /graaljs/js-scriptengine.jar "https://repo1.maven.org/maven2/org/graalvm/js/js-scriptengine/$GRAALJS_VERSION/js-scriptengine-$GRAALJS_VERSION.jar" && \
    wget -O /graaljs/truffle-api.jar "https://repo1.maven.org/maven2/org/graalvm/truffle/truffle-api/$GRAALJS_VERSION/truffle-api-$GRAALJS_VERSION.jar" && \
    wget -O /graaljs/truffle-runtime.jar "https://repo1.maven.org/maven2/org/graalvm/truffle/truffle-runtime/$GRAALJS_VERSION/truffle-runtime-$GRAALJS_VERSION.jar" && \
    wget -O /graaljs/truffle-compiler.jar "https://repo1.maven.org/maven2/org/graalvm/truffle/truffle-compiler/$GRAALJS_VERSION/truffle-compiler-$GRAALJS_VERSION.jar" && \
    wget -O /graaljs/collections.jar "https://repo1.maven.org/maven2/org/graalvm/sdk/collections/$GRAALJS_VERSION/collections-$GRAALJS_VERSION.jar" && \
    wget -O /graaljs/nativeimage.jar "https://repo1.maven.org/maven2/org/graalvm/sdk/nativeimage/$GRAALJS_VERSION/nativeimage-$GRAALJS_VERSION.jar" && \
    wget -O /graaljs/word.jar "https://repo1.maven.org/maven2/org/graalvm/sdk/word/$GRAALJS_VERSION/word-$GRAALJS_VERSION.jar" && \
    wget -O /graaljs/regex.jar "https://repo1.maven.org/maven2/org/graalvm/regex/regex/$GRAALJS_VERSION/regex-$GRAALJS_VERSION.jar" && \
    wget -O /graaljs/icu4j.jar "https://repo1.maven.org/maven2/com/ibm/icu/icu4j/$GRAALJS_VERSION/icu4j-76.1.jar" || true

FROM ${source} as intermediate

# runtime image https://console.cloud.google.com/gcr/images/distroless/global/java17
FROM gcr.io/distroless/java17:nonroot

# maintainer details
MAINTAINER James Bloom "jamesdbloom@gmail.com"

# expose ports.
EXPOSE 1080

# copy in jar
COPY --from=intermediate mockserver-netty-jar-with-dependencies.jar /
COPY --from=intermediate META-INF/native/libnetty_tcnative_linux_*.so /usr/lib/

# copy GraalJS JARs into /libs
COPY --from=graaljs /graaljs/*.jar /libs/

# don't run MockServer as root
USER nonroot

ENTRYPOINT ["java", "-Dfile.encoding=UTF-8", "-cp", "/mockserver-netty-jar-with-dependencies.jar:/libs/*", "-Dmockserver.propertyFile=/config/mockserver.properties", "org.mockserver.cli.Main"]

ENV SERVER_PORT 1080

HEALTHCHECK --interval=10s --timeout=5s --start-period=120s --retries=3 \
  CMD ["java", "-cp", "/mockserver-netty-jar-with-dependencies.jar", "org.mockserver.cli.HealthCheck"]

CMD []
