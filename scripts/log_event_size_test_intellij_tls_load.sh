#!/usr/bin/env bash
for counter in $(seq 1 1 5000); do
  echo "count: ${counter}"
  # valid client key and cert combination
  curl -s --key /Users/jamesbloom/git/mockserver/mockserver/mockserver-core/src/test/resources/org/mockserver/authentication/mtls/leaf-key.pem --cert /Users/jamesbloom/git/mockserver/mockserver/mockserver-core/src/test/resources/org/mockserver/authentication/mtls/leaf-cert.pem --cacert /Users/jamesbloom/git/mockserver/mockserver/mockserver-core/src/test/resources/org/mockserver/authentication/mtls/ca.pem -X PUT 'https://localhost:1080/some/path' >/dev/null &
  # invalid client key and cert combination
  curl -s --key /Users/jamesbloom/git/mockserver/mockserver/mockserver-core/src/test/resources/org/mockserver/authentication/mtls/separateca/leaf-key.pem --cert /Users/jamesbloom/git/mockserver/mockserver/mockserver-core/src/test/resources/org/mockserver/authentication/mtls/leaf-cert.pem --cacert /Users/jamesbloom/git/mockserver/mockserver/mockserver-core/src/test/resources/org/mockserver/authentication/mtls/ca.pem -X PUT 'https://localhost:1080/some/path' >/dev/null &
  # untrusted client cert
  curl -s --key /Users/jamesbloom/git/mockserver/mockserver/mockserver-core/src/test/resources/org/mockserver/authentication/mtls/separateca/leaf-key.pem --cert /Users/jamesbloom/git/mockserver/mockserver/mockserver-core/src/test/resources/org/mockserver/authentication/mtls/separateca/leaf-cert.pem --cacert /Users/jamesbloom/git/mockserver/mockserver/mockserver-core/src/test/resources/org/mockserver/authentication/mtls/ca.pem -X PUT 'https://localhost:1080/some/path' >/dev/null &
  # untrusted server cert
  curl -s --key /Users/jamesbloom/git/mockserver/mockserver/mockserver-core/src/test/resources/org/mockserver/authentication/mtls/leaf-key.pem --cert /Users/jamesbloom/git/mockserver/mockserver/mockserver-core/src/test/resources/org/mockserver/authentication/mtls/leaf-cert.pem --cacert /Users/jamesbloom/git/mockserver/mockserver/mockserver-core/src/test/resources/org/mockserver/authentication/mtls/separateca/ca.pem -X PUT 'https://localhost:1080/some/path' >/dev/null &
  # no client cert
  curl -s --cacert /Users/jamesbloom/git/mockserver/mockserver/mockserver-core/src/test/resources/org/mockserver/authentication/mtls/ca.pem -X PUT 'https://localhost:1080/some/path' >/dev/null
done
