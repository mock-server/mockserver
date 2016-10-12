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

# download MockServer
RUN  \
  mkdir -p /opt/mockserver && \
  wget -O /opt/mockserver/mockserver-netty-jar-with-dependencies.jar https://oss.sonatype.org/content/repositories/releases/org/mock-server/mockserver-netty/3.10.4/mockserver-netty-3.10.4-jar-with-dependencies.jar && \
  wget -O /opt/mockserver/run_mockserver.sh https://raw.github.com/jamesdbloom/mockserver/master/docker/run_mockserver.sh && \
  wget -O /opt/mockserver/CertificateAuthorityCertificate.pem https://raw.github.com/jamesdbloom/mockserver/master/mockserver-core/src/main/resources/org/mockserver/socket/CertificateAuthorityCertificate.pem && \
  chmod +x /opt/mockserver/run_mockserver.sh

# set working directory
WORKDIR /opt/mockserver

# expose ports.
EXPOSE 1080 1090

# define default command.
CMD ["/opt/mockserver/run_mockserver.sh", "-logLevel", "INFO", "-serverPort", "1080", "-proxyPort", "1090"]
