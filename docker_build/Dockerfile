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

# install Java
RUN \
  export DEBIAN_FRONTEND=noninteractive && \
  sed -i 's/# \(.*multiverse$\)/\1/g' /etc/apt/sources.list && \
  apt-get update && \
  apt-get -y upgrade && \
  apt-get install -y build-essential && \
  apt-get install -y software-properties-common && \
  add-apt-repository -y ppa:webupd8team/java && \
  curl -sL https://deb.nodesource.com/setup_7.x | sudo -E bash - && \
  apt-get install -y nodejs curl git htop man unzip vim wget pkg-config && \
  echo debconf shared/accepted-oracle-license-v1-1 select true | debconf-set-selections && \
  echo debconf shared/accepted-oracle-license-v1-1 seen true | debconf-set-selections && \
  apt-get install -y oracle-java6-installer maven

# set working directory
WORKDIR /build/mockserver

# define default command.
CMD ["/build/mockserver/scripts/local_quick_build.sh"]
