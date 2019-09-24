#
# MockServer Build Dockerfile
#
# https://github.com/jamesdbloom/mockserver
# http://www.mock-server.com
#

# pull base image.
FROM debian:jessie

# update packages
RUN export DEBIAN_FRONTEND=noninteractive
RUN sed -i 's/# \(.*multiverse$\)/\1/g' /etc/apt/sources.list
RUN add-apt-repository -y ppa:openjdk-r/ppa
RUN apt-get update
RUN apt-get -y upgrade

# install basic build tools
RUN apt-get install -y build-essential
RUN apt-get install -y software-properties-common
RUN apt-get install -y curl git htop man unzip vim wget pkg-config python2.7 openjdk-8-jre

# install xvfb & chromium
RUN apt-get install -y xvfb chromium
ADD chrome-xvfb /usr/bin/chrome
ENV CHROME_BIN=/usr/bin/chrome

# install node
RUN curl -sL https://deb.nodesource.com/setup_7.x | bash -
RUN apt-get install -y nodejs

# install grunt
RUN npm install -g grunt-cli
