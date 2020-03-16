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
RUN apk update --no-cache
RUN apk add curl --no-cache
RUN apk add busybox-extras --no-cache
RUN apk add bind-tools --no-cache
USER locust
