#!/usr/bin/env bash

# download certificate
wget https://raw.githubusercontent.com/jamesdbloom/mockserver/master/mockserver-core/src/main/resources/org/mockserver/socket/CertificateAuthorityCertificate.pem

# test if already installed
CERT_ALREADY_INSTALLED=$(keytool -list -v -storepass changeit -alias mockserver-ca)

# if already installed remove the current certificate (just in case it has been updated)
if [ ! -z "$CERT_ALREADY_INSTALLED" -a "$CERT_ALREADY_INSTALLED" != "1" ]; then
    echo "deleting certificate";
    keytool -delete -alias mockserver-ca -storepass changeit
fi

# install the certificate
keytool -v -import -alias mockserver-ca -file CertificateAuthorityCertificate.pem -storepass changeit -trustcacerts -noprompt

# delete the downloaded file
rm -rf CertificateAuthorityCertificate.pem