#
# MockServer Nginx Dockerfile
#
# https://github.com/jamesdbloom/mockserver
# http://www.mock-server.com
#

# pull base image
FROM mockserver/base

# maintainer details
MAINTAINER James Bloom "jamesdbloom@gmail.com"

#################
# INSTALL NGINX #
#################

# add nginx repository
RUN add-apt-repository -y ppa:nginx/stable

# update local package index
RUN apt-get update

# install nginx
RUN apt-get install -y nginx
RUN echo "\ndaemon off;" >> /etc/nginx/nginx.conf

##############################
# CONFIGURE NGINX TO JENKINS #
##############################

# change to nginx configuration folder
WORKDIR /etc/nginx/sites-available

# remove default nginx configuration
RUN rm -rf default ../sites-enabled/default

# create new configuration for jenkins (add to tmp folder so that the correct values for jenkins port and ip can be inserted at runtime)
RUN mkdir -p /etc/nginx/tmp && echo 'upstream app_server {' > /etc/nginx/tmp/jenkins && \
    echo "    server CI_PORT_8080_TCP_ADDR:CI_PORT_8080_TCP_PORT fail_timeout=0;" >> /etc/nginx/tmp/jenkins && \
    echo '}' >> /etc/nginx/tmp/jenkins && \
    echo ' ' >> /etc/nginx/tmp/jenkins && \
    echo 'server {' >> /etc/nginx/tmp/jenkins && \
    echo '    listen 80;' >> /etc/nginx/tmp/jenkins && \
    echo '    listen [::]:80 default ipv6only=on;' >> /etc/nginx/tmp/jenkins && \
    echo '    server_name ci.jamesdbloom.com;' >> /etc/nginx/tmp/jenkins && \
    echo ' ' >> /etc/nginx/tmp/jenkins && \
    echo '    location / {' >> /etc/nginx/tmp/jenkins && \
    echo '        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;' >> /etc/nginx/tmp/jenkins && \
    echo '        proxy_set_header Host $http_host;' >> /etc/nginx/tmp/jenkins && \
    echo '        proxy_redirect off;' >> /etc/nginx/tmp/jenkins && \
    echo ' ' >> /etc/nginx/tmp/jenkins && \
    echo '        if (!-f $request_filename) {' >> /etc/nginx/tmp/jenkins && \
    echo '            proxy_pass http://app_server;' >> /etc/nginx/tmp/jenkins && \
    echo '            break;' >> /etc/nginx/tmp/jenkins && \
    echo '        }' >> /etc/nginx/tmp/jenkins && \
    echo '    }' >> /etc/nginx/tmp/jenkins && \
    echo '}' >> /etc/nginx/tmp/jenkins

# link new configuration
RUN ln -s /etc/nginx/sites-available/jenkins /etc/nginx/sites-enabled/

# to build container run:
# docker build -t="mockserver/nginx" https://raw.github.com/jamesdbloom/mockserver/master/ci/docker/nginx/Dockerfile

########################
# CONFIGURE SUPERVISOR #
########################

# add to supervisor
RUN echo '[program:nginx]' > /etc/supervisor/conf.d/nginx && \
    echo 'command=sed "s/CI_PORT_8080_TCP_ADDR/$CI_PORT_8080_TCP_ADDR/g" /etc/nginx/tmp/jenkins | sed "s/CI_PORT_8080_TCP_PORT/$CI_PORT_8080_TCP_PORT/g" > /etc/nginx/sites-available/jenkins' >> /etc/supervisor/conf.d/nginx && \
    echo 'command=service nginx restart' >> /etc/supervisor/conf.d/nginx && \
    echo 'redirect_stderr=true' >> /etc/supervisor/conf.d/nginx

# start supervisor daemon
CMD ["supervisord", "-c", "/etc/supervisor/supervisor.conf"]

################
# EXPORT PORTS #
################

EXPOSE 80

###############
# TO BUILD... #
###############

# to build container:
# docker build -t="mockserver/nginx" https://raw.github.com/jamesdbloom/mockserver/master/ci/docker/nginx/Dockerfile
# to run container:
# docker run -name nginx -link jenkins:ci -p 80:80 -d mockserver/nginx

############
# NOTES... #
############

# The following files locations are used
# service: /etc/init.d/nginx
# config: /etc/nginx/nginx.conf
# access_logs: /var/log/nginx/access.log
# error_logs: /var/log/nginx/error.log