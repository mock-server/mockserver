#
# MockServer Performance Dockerfile
#
# https://github.com/mock-server/mockserver
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
RUN apt-get install -y apache2-utils libssl-dev curl git htop man unzip vim wget pkg-config

# install locustio
RUN apt-get -y install python-setuptools python-dev build-essential
RUN easy_install pip
RUN pip install locustio

# install wrk
RUN git clone https://github.com/giltene/wrk2.git
WORKDIR wrk2
RUN make
RUN cp wrk /usr/local/bin
