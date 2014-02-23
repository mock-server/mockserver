#
# MockServer Base Dockerfile
#
# https://github.com/jamesdbloom/mockserver
# http://www.mock-server.com
#

# pull base image
FROM dockerfile/ubuntu

# maintainer details
MAINTAINER James Bloom "jamesdbloom@gmail.com"

###############
# BASIC SETUP #
###############

# install basic components
RUN apt-get install -y software-properties-common
RUN apt-get install -y python-software-properties
RUN apt-get install -y wget

# add universe and multiverse repositories
RUN echo deb http://archive.ubuntu.com/ubuntu precise universe multiverse >> /etc/apt/sources.list

# upgrade packages
RUN apt-get update
RUN apt-get -y upgrade

######################
# INSTALL SUPERVISOR #
######################

# use easy_install as debian package broken
RUN apt-get -y install python-setuptools
RUN easy_install supervisor
RUN mkdir -p /var/log/supervisor

# directory for supervised containers
RUN mkdir -p /etc/supervisor/conf.d

# add supervisor config
echo "[supervisord]
nodaemon=true

[include]
files = /etc/supervisor/conf.d/*" > /etc/supervisor

# to build container run:
# docker build -t="mockserver/base" https://raw.github.com/jamesdbloom/mockserver/master/ci/docker/base/Dockerfile