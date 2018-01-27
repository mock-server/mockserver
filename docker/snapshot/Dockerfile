#
# MockServer Dockerfile
#
# https://github.com/jamesdbloom/mockserver
# http://www.mock-server.com
#

# pull base image
FROM openjdk:8u151-jre-alpine3.7

# maintainer details
MAINTAINER James Bloom "jamesdbloom@gmail.com"

# obtain MockServer jar and script
RUN apk add --update openssl ca-certificates bash wget
ADD run_mockserver.sh /opt/mockserver/run_mockserver.sh
RUN wget --max-redirect=1 -O /opt/mockserver/mockserver-netty-jar-with-dependencies.jar https://oss.sonatype.org/service/local/artifact/maven/redirect?r=snapshots&g=org.mock-server&a=mockserver-netty&c=jar-with-dependencies&e=jar&v=LATEST

# set working directory
WORKDIR /opt/mockserver

# expose ports.
EXPOSE 1080

# define default command.
CMD ["/opt/mockserver/run_mockserver.sh", "-logLevel", "INFO", "-serverPort", "1080"]
