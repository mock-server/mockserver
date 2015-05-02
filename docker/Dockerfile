#
# MockServer Dockerfile
#
# https://github.com/jamesdbloom/mockserver
# http://www.mock-server.com
#

# pull base image
FROM java

# maintainer details
MAINTAINER James Bloom "jamesdbloom@gmail.com"

# set working directory
RUN mkdir -p /opt/mockserver
WORKDIR /opt/mockserver

# download MockServer
RUN (cd /opt/mockserver && wget -O mockserver-netty-3.9.11-jar-with-dependencies.jar https://oss.sonatype.org/content/repositories/releases/org/mock-server/mockserver-netty/3.9.11/mockserver-netty-3.9.11-jar-with-dependencies.jar)
RUN (cd /opt/mockserver && wget https://raw.github.com/jamesdbloom/mockserver/master/docker/run_mockserver.sh && chmod +x /opt/mockserver/run_mockserver.sh)

# expose ports.
EXPOSE 8080 9090

# define default command.
CMD ["/opt/mockserver/run_mockserver.sh", "-serverPort", "8080", "-proxyPort", "9090"]
