#
# MockServer Dockerfile
#
# https://github.com/mock-server/mockserver
# http://www.mock-server.com
#

# runtime image https://console.cloud.google.com/gcr/images/distroless/global/java17
FROM gcr.io/distroless/java17:nonroot

# maintainer details
MAINTAINER James Bloom "jamesdbloom@gmail.com"

# expose ports.
EXPOSE 1080

# copy in jar
COPY mockserver-netty-shaded.jar /

# don't run MockServer as root
USER nonroot

ENTRYPOINT ["java", "-Dfile.encoding=UTF-8", "-cp", "/mockserver-netty-shaded.jar:/libs/*", "-Dmockserver.propertyFile=/config/mockserver.properties", "org.mockserver.cli.Main"]

CMD ["-serverPort", "1080"]
