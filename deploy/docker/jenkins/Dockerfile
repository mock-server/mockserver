#
# MockServer Jenkins Dockerfile
#
# https://github.com/jamesdbloom/mockserver
# http://www.mock-server.com
#

# pull base image
FROM dockerfile/java

# maintainer details
MAINTAINER James Bloom "jamesdbloom@gmail.com"

# add jenkins key
RUN apt-get install -y wget
RUN wget -q -O - http://pkg.jenkins-ci.org/debian/jenkins-ci.org.key | sudo apt-key add -

# update /etc/apt/sources.list:
RUN echo deb http://archive.ubuntu.com/ubuntu precise universe >> /etc/apt/sources.list
RUN echo deb http://pkg.jenkins-ci.org/debian binary/ > /etc/apt/sources.list.d/jenkins.list

# update local package index
RUN sudo apt-get update

# install jenkins:
RUN sudo apt-get install -y jenkins
ADD run /usr/local/bin/

EXPOSE 8080

CMD ["/usr/local/bin/run"]

# to build container run:
# docker build -t="jamesdbloom/jenkins" https://raw.github.com/jamesdbloom/mockserver/master/deploy/docker/jenkins/Dockerfile