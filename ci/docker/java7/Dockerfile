#
# MockServer Java 1.7 Dockerfile
#
# https://github.com/jamesdbloom/mockserver
# http://www.mock-server.com
#

# pull base image.
FROM mockserver/base

# maintainer details
MAINTAINER James Bloom "jamesdbloom@gmail.com"

################
# INSTALL JAVA #
################

# add oracle java repositories
RUN add-apt-repository -y ppa:webupd8team/java

# update apt-get
RUN apt-get update && apt-get -y upgrade

# accept license
RUN echo debconf shared/accepted-oracle-license-v1-1 select true | debconf-set-selections
RUN echo debconf shared/accepted-oracle-license-v1-1 seen true | debconf-set-selections

# install Java
RUN apt-get -y install oracle-java7-installer && apt-get clean
RUN update-alternatives --display java
ENV JAVA_HOME /usr/lib/jvm/java-7-oracle

# to build container run:
# docker build -t="mockserver/java7" https://raw.github.com/jamesdbloom/mockserver/master/ci/docker/java7/Dockerfile
