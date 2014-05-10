#
# MockServer Dockerfile
#
# https://github.com/jamesdbloom/mockserver
# http://www.mock-server.com
#

# pull base image
FROM dockerfile/java

# maintainer details
MAINTAINER James Bloom "jamesdbloom@gmail.com"

# set working directory
RUN mkdir -p /opt/mockserver
WORKDIR /opt/mockserver

# download MockServer
RUN (cd /opt/mockserver && wget -O mockserver-netty-2.9-jar-with-dependencies.jar http://search.maven.org/remotecontent?filepath=org/mock-server/mockserver-netty/2.9/mockserver-netty-2.9-jar-with-dependencies.jar)
RUN (cd /opt/mockserver && wget https://raw.github.com/jamesdbloom/mockserver/master/docker/run_mockserver.sh && chmod +x /opt/mockserver/run_mockserver.sh)

# expose ports.
EXPOSE 8080 8090 9080 9090

# define default command.
CMD ["/opt/mockserver/run_mockserver.sh", "-serverPort", "8080", "-serverSecurePort", "8090", "-proxyPort", "9080", "-proxySecurePort", "9090"]