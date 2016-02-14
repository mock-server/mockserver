#!/usr/bin/env bash

###########################################################################################
#                                                                                         #
#  if `Permission denied` error is returned use sudo providing JAVA_HOME as follows:      #
#                                                                                         #
#         sudo JAVA_HOME=<JAVA_HOME> ./scripts/install_ca_certificate.sh                  #
#                                                                                         #
#  on a mac /usr/libexec/java_home tool can be used to provide the JAVA_HOME as follows:  #
#                                                                                         #
#         sudo JAVA_HOME=`/usr/libexec/java_home` ./scripts/install_ca_certificate.sh     #
#                                                                                         #
###########################################################################################

function runCommand {
    echo
    printf -v str "%-$((${#1} + 19))s" ' '; echo "${str// /=}"
    echo "Executing command: $1"
    printf -v str "%-$((${#1} + 19))s" ' '; echo "${str// /=}"
    echo
    eval $1
}

function printMessage {
    echo
    printf -v str "%-$((${#1}))s" ' '; echo "${str// /=}"
    echo "$1"
    printf -v str "%-$((${#1}))s" ' '; echo "${str// /=}"
    echo
}

function determineTrustStoreOrKeyStoreLocation {
    if [[ -z "$JAVA_HOME" ]]; then
        echo "JAVA_HOME environment variable is not set, please set this before running this script";
        exit 1;
    fi

    if [ -e "$JAVA_HOME/jre/lib/security/cacerts" ]; then
      export KEYSTORE="-keystore $JAVA_HOME/jre/lib/security/cacerts"
      printMessage "Using trust store location: $JAVA_HOME/jre/lib/security/cacerts"
    elif [ -e "$JAVA_HOME/lib/security/cacerts" ]; then
      export KEYSTORE="-keystore $JAVA_HOME/lib/security/cacerts"
      printMessage "Using trust store location: $JAVA_HOME/lib/security/cacerts"
    else
      export KEYSTORE=""
      printMessage "Trust store location not found using keystore"
    fi
}

function downloadCertificate {
    # download certificate
    runCommand "wget https://raw.githubusercontent.com/jamesdbloom/mockserver/master/mockserver-core/src/main/resources/org/mockserver/socket/CertificateAuthorityCertificate.pem"
}

function deleteDownloadedCertificate {
    # delete the downloaded file
    runCommand "rm -rf CertificateAuthorityCertificate.pem"
}

function removeIfAlreadyInstalled {
    # test if already installed
    CERT_ALREADY_INSTALLED=$(runCommand "keytool -list -v $KEYSTORE -storepass changeit -alias mockserver-ca")

    # if already installed remove the current certificate (just in case it has been updated)
    if [ ! -z "$CERT_ALREADY_INSTALLED" -a "$CERT_ALREADY_INSTALLED" != "1" ]; then
        runCommand "keytool -delete $KEYSTORE -alias mockserver-ca -storepass changeit"
    fi
}

function installCertificate {
    determineTrustStoreOrKeyStoreLocation

    downloadCertificate

    removeIfAlreadyInstalled
    # install the certificate
    STORE_RESPONSE=$(runCommand "keytool -import -v $KEYSTORE -alias mockserver-ca -file CertificateAuthorityCertificate.pem -storepass changeit -trustcacerts -noprompt 2>&1")
    echo "$STORE_RESPONSE"

    deleteDownloadedCertificate

    # print keystore location
    KEY_STORE_FILE=$(echo $STORE_RESPONSE | tr ']' ' ' | awk '{print $7}')
    echo
    printf -v str "%-$((${#KEY_STORE_FILE} + 85))s" ' '; echo "${str// /=}"
    echo "Ensure your JVM is using the correct keystore as follows: -Djavax.net.ssl.trustStore=$KEY_STORE_FILE"
    printf -v str "%-$((${#KEY_STORE_FILE} + 85))s" ' '; echo "${str// /=}"
    echo
}

installCertificate
