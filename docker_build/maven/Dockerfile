#
# MockServer Build Dockerfile
#
# https://github.com/jamesdbloom/mockserver
# http://www.mock-server.com
#

# pull base image.
FROM ubuntu:trusty

# maintainer details
MAINTAINER James Bloom "jamesdbloom@gmail.com"

# update packages
RUN export DEBIAN_FRONTEND=noninteractive
RUN sed -i 's/# \(.*multiverse$\)/\1/g' /etc/apt/sources.list
RUN apt-get update
RUN apt-get -y upgrade

# install basic build tools
RUN apt-get install -y build-essential
RUN apt-get install -y software-properties-common
RUN apt-get install -y curl git htop man unzip vim wget pkg-config

# install java
RUN add-apt-repository -y ppa:openjdk-r/ppa
RUN apt-get update
RUN apt-get install -y openjdk-8-jdk maven
COPY settings.xml /etc/maven/settings.xml
RUN rm -f /etc/alternatives/java && ln -s /usr/lib/jvm/java-8-openjdk-amd64/jre/bin/java /etc/alternatives/java

# pre-fetch maven dependencies
RUN git clone https://github.com/jamesdbloom/mockserver.git
RUN cd mockserver && ./mvnw -s /etc/maven/settings.xml dependency:go-offline && cd .. && rm -rf mockserver
