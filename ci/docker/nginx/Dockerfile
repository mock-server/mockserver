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

# create new configuration for jenkins
RUN echo 'upstream app_server { \n\
    server 127.0.0.1:8080 fail_timeout=0; \n\
} \n\
  \n\
server { \n\
    listen 80; \n\
    listen [::]:80 default ipv6only=on; \n\
    server_name ci.jamesdbloom.com; \n\
\n\
    location / { \n\
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for; \n\
        proxy_set_header Host $http_host; \n\
        proxy_redirect off; \n\
 \n\
        if (!-f $request_filename) { \n\
            proxy_pass http://app_server; \n\
            break; \n\
        } \n\
    } \n\
}' > jenkins

# link new configuration
RUN ln -s /etc/nginx/sites-available/jenkins /etc/nginx/sites-enabled/

# to build container run:
# docker build -t="mockserver/nginx" https://raw.github.com/jamesdbloom/mockserver/master/ci/docker/nginx/Dockerfile

########################
# CONFIGURE SUPERVISOR #
########################

# add to supervisor
RUN echo '[program:nginx]\ncommand=service nginx restart\nredirect_stderr=true' > /etc/supervisor/conf.d/nginx

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
# docker run -name nginx -link jenkins -p 80:80 -d mockserver/nginx

############
# NOTES... #
############

# The following files locations are used
# service: /etc/init.d/nginx
# config: /etc/nginx/nginx.conf
# access_logs: /var/log/nginx/access.log
# error_logs: /var/log/nginx/error.log