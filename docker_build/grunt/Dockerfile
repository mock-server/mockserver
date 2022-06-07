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

# install xvfb & chromium
RUN apt-get install -y xvfb chromium-browser
ADD chrome-xvfb /usr/bin/chrome
ENV CHROME_BIN=/usr/bin/chrome

# install node (see: https://github.com/nodesource/distributions)
RUN curl -fsSL https://deb.nodesource.com/setup_16.x | bash -
RUN apt-get install -y nodejs

# install grunt
RUN npm install -g grunt-cli
