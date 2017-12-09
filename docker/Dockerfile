#
# MockServer Dockerfile
#
# https://github.com/jamesdbloom/mockserver
# http://www.mock-server.com
#

# pull base image
FROM openjdk:alpine

# maintainer details
MAINTAINER James Bloom "jamesdbloom@gmail.com"

# obtain MockServer jar and script
RUN apk add --update openssl ca-certificates bash
ADD run_mockserver.sh /opt/mockserver/run_mockserver.sh
RUN wget -O /opt/mockserver/mockserver-netty-jar-with-dependencies.jar https://oss.sonatype.org/content/repositories/releases/org/mock-server/mockserver-netty/5.1.1/mockserver-netty-5.1.1-jar-with-dependencies.jar

# set working directory
WORKDIR /opt/mockserver

# expose ports.
EXPOSE 1080 1090

# define default command.
CMD ["/opt/mockserver/run_mockserver.sh", "-logLevel", "INFO", "-serverPort", "1080", "-proxyPort", "1090"]
