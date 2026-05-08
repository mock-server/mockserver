#
# MockServer Build Dockerfile
#
# https://github.com/mock-server/mockserver
# http://www.mock-server.com
#

FROM ubuntu:24.04

LABEL maintainer="James Bloom <jamesdbloom@gmail.com>"

ENV DEBIAN_FRONTEND=noninteractive
ENV TZ=Europe/London

ARG MAVEN_VERSION=3.9.15

RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone \
    && apt-get update \
    && apt-get -y upgrade \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        git \
        gnupg \
        jq \
        oathtool \
        ruby-dev \
        ruby-bundler \
        unzip \
        openjdk-21-jdk \
    && gem install jekyll --no-document \
    && rm -rf /var/lib/apt/lists/*

RUN ln -s /usr/lib/jvm/java-21-openjdk-$(dpkg --print-architecture) /usr/lib/jvm/java-21-openjdk
ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk

COPY corporate-root-ca.pem /tmp/corporate-root-ca.pem
RUN CACERTS=$(find /usr/lib/jvm -name cacerts -path "*/security/cacerts" | head -1) \
    && if [ -s /tmp/corporate-root-ca.pem ]; then \
        cp /tmp/corporate-root-ca.pem /usr/local/share/ca-certificates/corporate-root-ca.crt \
        && update-ca-certificates \
        && keytool -noprompt -import -alias corporate_root_ca \
            -keystore "$CACERTS" \
            -storepass changeit \
            -file /tmp/corporate-root-ca.pem; \
    fi \
    && rm -f /tmp/corporate-root-ca.pem

RUN curl -fsSL https://dlcdn.apache.org/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz \
        | tar xz -C /opt \
    && ln -s /opt/apache-maven-${MAVEN_VERSION}/bin/mvn /usr/local/bin/mvn

COPY settings.xml /opt/apache-maven-${MAVEN_VERSION}/conf/settings.xml

RUN ARCH=$(dpkg --print-architecture) \
    && curl -fsSL "https://awscli.amazonaws.com/awscli-exe-linux-$(uname -m).zip" -o /tmp/awscliv2.zip \
    && unzip -q /tmp/awscliv2.zip -d /tmp \
    && /tmp/aws/install \
    && rm -rf /tmp/awscliv2.zip /tmp/aws

RUN ARCH=$(dpkg --print-architecture) \
    && curl -fsSL "https://get.helm.sh/helm-v3.17.3-linux-${ARCH}.tar.gz" | tar xz -C /tmp \
    && mv /tmp/linux-${ARCH}/helm /usr/local/bin/helm \
    && rm -rf /tmp/linux-${ARCH}

RUN ARCH=$(dpkg --print-architecture) \
    && curl -fsSL "https://github.com/cli/cli/releases/latest/download/gh_$(curl -sfL https://api.github.com/repos/cli/cli/releases/latest | grep -oP '"tag_name": "v\K[^"]+')_linux_${ARCH}.tar.gz" \
    | tar xz -C /tmp --strip-components=1 \
    && mv /tmp/bin/gh /usr/local/bin/gh \
    && rm -rf /tmp/bin /tmp/share

RUN git clone https://github.com/mock-server/mockserver.git \
    && cd mockserver \
    && ./mvnw install -DskipTests \
    && cd .. \
    && rm -rf mockserver
