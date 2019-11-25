#
# MockServer Dockerfile
#
# https://github.com/jamesdbloom/mockserver
# http://www.mock-server.com
#

# pull base image
FROM openjdk:8-jdk-alpine

# maintainer details
MAINTAINER James Bloom "jamesdbloom@gmail.com"

# obtain MockServer jar and script
RUN apk add --update openssl ca-certificates bash wget
ADD run_mockserver.sh /opt/mockserver/run_mockserver.sh
ARG VERSION=5.7.2
ARG REPOSITORY_URL=https://oss.sonatype.org/content/repositories/releases/org/mock-server/mockserver-netty/$VERSION/mockserver-netty-$VERSION-jar-with-dependencies.jar
RUN echo "using mockserver version $REPOSITORY_URL"
RUN wget --max-redirect=10 -O /opt/mockserver/mockserver-netty-jar-with-dependencies.jar $REPOSITORY_URL

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
