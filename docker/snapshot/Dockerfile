#
# MockServer Dockerfile
#
# https://github.com/mock-server/mockserver
# http://www.mock-server.com
#

# pull base image
FROM openjdk:8-jdk-alpine

# maintainer details
MAINTAINER James Bloom "jamesdbloom@gmail.com"

# obtain MockServer jar and script
RUN apk add --update openssl ca-certificates bash wget
ADD run_mockserver.sh /opt/mockserver/run_mockserver.sh
RUN wget --max-redirect=1 -O /opt/mockserver/mockserver-netty-jar-with-dependencies.jar "https://oss.sonatype.org/service/local/artifact/maven/redirect?r=snapshots&g=org.mock-server&a=mockserver-netty&c=jar-with-dependencies&e=jar&v=LATEST"

# set working directory
WORKDIR /opt/mockserver

# expose ports.
EXPOSE 1080

# don't run MockServer as root
RUN addgroup -g 1000 mockserver && \
    adduser -H -D -u 1000 -G mockserver mockserver
RUN chown -R mockserver:mockserver /opt/mockserver
USER mockserver

ENTRYPOINT ["/opt/mockserver/run_mockserver.sh"]

CMD ["-logLevel", "INFO", "-serverPort", "1080"]
