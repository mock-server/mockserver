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
RUN add-apt-repository -y ppa:webupd8team/java
RUN apt-get update
RUN echo debconf shared/accepted-oracle-license-v1-1 select true | debconf-set-selections
RUN echo debconf shared/accepted-oracle-license-v1-1 seen true | debconf-set-selections
RUN apt-get install -y oracle-java6-installer maven

# install node
RUN curl -sL https://deb.nodesource.com/setup_7.x | bash -
RUN apt-get install -y nodejs
