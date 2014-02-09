#
# MockServer Dockerfile
#
# https://github.com/jamesdbloom/mockserver
#

# Pull base image
FROM dockerfile/java

# Attach volumes
RUN mkdir -p /opt/mockserver
VOLUME /opt/mockserver

# Set working directory
WORKDIR /opt/mockserver

# Download MockServer
RUN wget http://search.maven.org/remotecontent?filepath=org/mock-server/mockserver-netty/2.6/mockserver-netty-2.6-jar-with-dependencies.jar
RUN wget https://github.com/jamesdbloom/mockserver/blob/master/run_mockserver.sh

# Expose ports.
EXPOSE 8080 8090 9080 9090

# Define default command.
CMD ["run_mockserver", "-serverPort", "8080", "-serverSecurePort", "8090", "-proxyPort", "9080", "-proxySecurePort", "9090"]


