#
# MockServer Dockerfile
#
# https://github.com/jamesdbloom/mockserver
# http://www.mock-server.com
#

# pull base image
FROM jamesdbloom/docker_java6_maven

# maintainer details
MAINTAINER James Bloom "jamesdbloom@gmail.com"

# set working directory
RUN git clone https://github.com/jamesdbloom/mockserver.git

# run build (download maven dependencies)
RUN mvn clean install

# open terminal
CMD ["/bin/bash"]

# to build container run:
# docker build -t="jamesdbloom/build_java6_mockserver" raw.github.com/jamesdbloom/mockserver/master/build/docker/java6