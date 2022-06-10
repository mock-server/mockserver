#
# MockServer Build Dockerfile
#
# https://github.com/mock-server/mockserver
# http://www.mock-server.com
#

# pull base image.
FROM ubuntu:20.04

# maintainer details
MAINTAINER James Bloom "jamesdbloom@gmail.com"

# update packages
ENV DEBIAN_FRONTEND=noninteractive
RUN export DEBIAN_FRONTEND=noninteractive
RUN sed -i 's/# \(.*multiverse$\)/\1/g' /etc/apt/sources.list
RUN apt-get update
RUN apt-get -y upgrade

# install basic build tools
ENV TZ=Europe/London
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone
RUN apt-get install -y build-essential
RUN apt-get install -y software-properties-common
RUN apt-get install -y curl git htop man unzip vim wget pkg-config python2.7

# install java
RUN add-apt-repository -y ppa:openjdk-r/ppa
RUN apt-get update
RUN apt-get install -y openjdk-8-jdk
RUN rm -f /etc/alternatives/java && ln -s /usr/lib/jvm/java-8-openjdk-amd64/jre/bin/java /etc/alternatives/java

# install maven
RUN apt-get install -y maven
COPY settings.xml /etc/maven/settings.xml

# pre-fetch maven dependencies
RUN git clone https://github.com/mock-server/mockserver.git
RUN cd mockserver && \
    ./mvnw -s /etc/maven/settings.xml dependency:go-offline && \
    ./mvnw -s /etc/maven/settings.xml package -DskipTests && \
    cd .. && \
    rm -rf mockserver
