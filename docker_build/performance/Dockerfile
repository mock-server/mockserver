#
# MockServer Performance Dockerfile
#
# https://github.com/mock-server/mockserver
# http://www.mock-server.com
#

# pull base image.
FROM locustio/locust

# maintainer details
MAINTAINER James Bloom "jamesdbloom@gmail.com"

# install basic build tools
USER root
RUN apt update
RUN apt upgrade -y
RUN apt install -y curl
USER locust
