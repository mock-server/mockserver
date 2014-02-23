#
# MockServer Jenkins Dockerfile
#
# https://github.com/jamesdbloom/mockserver
# http://www.mock-server.com
#

# pull base image
FROM mockserver/java7

# maintainer details
MAINTAINER James Bloom "jamesdbloom@gmail.com"

###################
# INSTALL JENKINS #
###################

# add jenkins key
RUN wget -q -O - http://pkg.jenkins-ci.org/debian/jenkins-ci.org.key | apt-key add -

# add jenkins repository
RUN echo deb http://pkg.jenkins-ci.org/debian binary/ > /etc/apt/sources.list.d/jenkins.list

# update local package index
RUN apt-get update

# install jenkins:
RUN apt-get install -y jenkins

########################
# CONFIGURE SUPERVISOR #
########################

# add to supervisor
RUN echo -e '[program:jenkins]\n#command=java -jar /var/lib/jenkins/jenkins.war\ncommand=service jenkins restart\nredirect_stderr=true' > /etc/supervisor/conf.d/jenkins

# start supervisor daemon
CMD ["supervisord", "-c", "/etc/supervisor/supervisor.conf"]

################
# EXPORT PORTS #
################

EXPOSE 8080

###############
# TO BUILD... #
###############

# to build container run:
# docker build -t="mockserver/jenkins" https://raw.github.com/jamesdbloom/mockserver/master/ci/docker/jenkins/Dockerfile