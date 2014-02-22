#
# MockServer Dockerfile
#
# https://github.com/jamesdbloom/mockserver
# http://www.mock-server.com
#

# pull base image
FROM jamesdbloom/docker-java7-maven

# maintainer details
MAINTAINER James Bloom "jamesdbloom@gmail.com"

# set working directory
RUN git clone https://github.com/jamesdbloom/mockserver.git

# download maven dependencies
RUN cd mockserver && mvn compile test-compile clean

# open terminal
CMD ["/bin/bash"]

# to build container run:
# docker build -t="jamesdbloom/build_java7_mockserver" https://raw.github.com/jamesdbloom/mockserver/master/deploy/docker/java7/Dockerfile
