---
name: renew-test-certs
description: >
  Renews expired TLS test certificates used by MockServer integration tests.
  Use when TLS tests fail with "Channel handler removed before valid response
  has been received", "Broken pipe", certificate expired errors, or when a
  user says "renew certs", "certificates expired", "TLS tests failing".

---

# Renew Test TLS Certificates

MockServer integration tests use custom CA-signed leaf certificates for TLS testing.
These certificates have a finite validity period and must be renewed when they expire.

## Prerequisites

- **openssl**: pre-installed on macOS

## Certificate Locations

Three sets of leaf certificates exist, each signed by their own CA:

| Directory | Key Type | CA Valid Until |
|-----------|----------|---------------|
| `mockserver-netty/src/test/resources/org/mockserver/netty/integration/tls/` | RSA 2048 | Feb 2028 |
| `mockserver-netty/src/test/resources/org/mockserver/netty/integration/tls/ec/` | ECDSA P-256 | Feb 2028 |
| `mockserver-netty/src/test/resources/org/mockserver/netty/integration/tls/separateca/` | RSA 2048 | Feb 2028 |

Each directory contains:
- `ca.pem` / `ca-key.pem` / `ca-key-pkcs8.pem` -- CA certificate and keys (long-lived, do NOT regenerate unless expired)
- `leaf-cert.pem` / `leaf-key.pem` / `leaf-key-pkcs8.pem` -- Leaf certificate and keys (short-lived, REGENERATE when expired)
- `leaf-cert-chain.pem` -- Certificate chain (leaf + CA)
- `csr.json` -- Certificate signing request config (reference only)

## Step 1: Check Certificate Expiry

```bash
for dir in \
  mockserver-netty/src/test/resources/org/mockserver/netty/integration/tls \
  mockserver-netty/src/test/resources/org/mockserver/netty/integration/tls/ec \
  mockserver-netty/src/test/resources/org/mockserver/netty/integration/tls/separateca; do
  echo "=== $dir ==="
  echo -n "  CA:   "; openssl x509 -in "$dir/ca.pem" -noout -enddate
  echo -n "  Leaf: "; openssl x509 -in "$dir/leaf-cert.pem" -noout -enddate
done
```

## Step 2: Create Extensions Config

Create a temporary OpenSSL extensions config for leaf certificates.
This MUST include `authorityKeyIdentifier` because the CA and leaf share the
same subject DN -- without AKI, Java's trust manager treats the leaf as self-signed.

```bash
cat > /tmp/leaf-ext.cnf << 'EOF'
[v3_leaf]
basicConstraints = critical,CA:FALSE
keyUsage = critical,digitalSignature,keyEncipherment
extendedKeyUsage = serverAuth,clientAuth
subjectKeyIdentifier = hash
authorityKeyIdentifier = keyid,issuer
subjectAltName = DNS:example.com,DNS:www.example.com,DNS:localhost,IP:127.0.0.1,URI:https://www.example.com
EOF
```

## Step 3: Regenerate Leaf Certificates

### RSA leaf (root `tls/` directory)

```bash
CERT_DIR="mockserver-netty/src/test/resources/org/mockserver/netty/integration/tls"
cd "$CERT_DIR"

openssl req -new -newkey rsa:2048 -nodes -keyout leaf-key.pem \
  -subj "/C=UK/L=London/O=MockServer/CN=www.mockserver.com" \
  -out /tmp/leaf.csr

openssl x509 -req -in /tmp/leaf.csr \
  -CA ca.pem -CAkey ca-key.pem -CAcreateserial \
  -out leaf-cert.pem -days 1825 \
  -extfile /tmp/leaf-ext.cnf -extensions v3_leaf

openssl pkcs8 -topk8 -inform PEM -in leaf-key.pem -out leaf-key-pkcs8.pem -nocrypt
cat leaf-cert.pem > leaf-cert-chain.pem
cat ca.pem >> leaf-cert-chain.pem
rm -f ca.srl /tmp/leaf.csr
```

### EC leaf (`tls/ec/` directory)

```bash
CERT_DIR="mockserver-netty/src/test/resources/org/mockserver/netty/integration/tls/ec"
cd "$CERT_DIR"

openssl req -new -newkey ec -pkeyopt ec_paramgen_curve:prime256v1 -nodes -keyout leaf-key.pem \
  -subj "/C=UK/L=London/O=MockServer/CN=www.mockserver.com" \
  -out /tmp/leaf-ec.csr

openssl x509 -req -in /tmp/leaf-ec.csr \
  -CA ca.pem -CAkey ca-key.pem -CAcreateserial \
  -out leaf-cert.pem -days 1825 \
  -extfile /tmp/leaf-ext.cnf -extensions v3_leaf

openssl pkcs8 -topk8 -inform PEM -in leaf-key.pem -out leaf-key-pkcs8.pem -nocrypt
cat leaf-cert.pem > leaf-cert-chain.pem
cat ca.pem >> leaf-cert-chain.pem
rm -f ca.srl /tmp/leaf-ec.csr
```

### RSA leaf (`tls/separateca/` directory)

```bash
CERT_DIR="mockserver-netty/src/test/resources/org/mockserver/netty/integration/tls/separateca"
cd "$CERT_DIR"

openssl req -new -newkey rsa:2048 -nodes -keyout leaf-key.pem \
  -subj "/C=UK/L=London/O=MockServer/CN=www.mockserver.com" \
  -out /tmp/leaf-sep.csr

openssl x509 -req -in /tmp/leaf-sep.csr \
  -CA ca.pem -CAkey ca-key.pem -CAcreateserial \
  -out leaf-cert.pem -days 1825 \
  -extfile /tmp/leaf-ext.cnf -extensions v3_leaf

openssl pkcs8 -topk8 -inform PEM -in leaf-key.pem -out leaf-key-pkcs8.pem -nocrypt
cat leaf-cert.pem > leaf-cert-chain.pem
cat ca.pem >> leaf-cert-chain.pem
rm -f ca.srl /tmp/leaf-sep.csr
```

## Step 4: Verify

For each directory, verify the leaf cert chains to the CA and check the AKI extension:

```bash
openssl verify -CAfile ca.pem leaf-cert.pem
openssl x509 -in leaf-cert.pem -noout -dates
openssl x509 -in leaf-cert.pem -noout -text | grep -A2 "Authority Key"
```

Then run the affected tests:

```bash
./mvnw -pl mockserver-netty verify \
  -Dtest=none \
  -Dit.test="CustomPrivateKeyAndCertificateWithECKeysMockingIntegrationTest,ClientAuthenticationCustomPrivateKeyAndCertificateMockingIntegrationTest,ClientAuthenticationAdditionalCertificateChainMockingIntegrationTest,ForwardWithCustomClientCertificateIntegrationTest" \
  -Djava.security.egd=file:/dev/urandom
```

## Step 5: Clean Up

```bash
rm -f /tmp/leaf-ext.cnf
```

## Important: Do NOT Use cfssl for Leaf Certs

`cfssl gencert` does NOT include the `authorityKeyIdentifier` extension by default.
Because the CA and leaf certificates share the same subject DN
(`C=UK, L=London, O=MockServer, CN=www.mockserver.com`), Java's X509TrustManager
cannot chain the leaf to the CA without AKI and treats it as self-signed.
Always use `openssl x509 -req` with the extensions config above.

`cfssl` CAN still be used to generate new CA certificates (via `cfssl genkey -initca`).

## Affected Test Classes

These test classes use the leaf certificates and will fail when they expire:

### Root `tls/` directory (RSA)
- `ClientAuthenticationAdditionalCertificateChainMockingIntegrationTest`
- `ClientAuthenticationCustomPrivateKeyAndCertificateMockingIntegrationTest`
- `ClientAuthenticationCustomCertificateAuthorityMockingIntegrationTest`
- `CustomCertificateAuthorityMockingIntegrationTest`
- `ForwardWithCustomTrustManagerWithCustomCAMockingIntegrationTest`
- `ForwardViaHttpsProxyWithCustomTrustManagerWithCustomCAMockingIntegrationTest`
- `ForwardWithCustomClientCertificateIntegrationTest`
- `AuthenticatedControlPlaneUsingMTLSClientMockingIntegrationTest`
- `AuthenticatedControlPlaneUsingMTLSClientNotAuthenticatedIntegrationTest`

### `tls/ec/` directory (ECDSA)
- `CustomPrivateKeyAndCertificateWithECKeysMockingIntegrationTest`

### `tls/separateca/` directory (RSA, separate CA)
- `AbstractForwardViaHttpsProxyMockingIntegrationTest` (and 9 subclasses)
- `ForwardWithCustomClientCertificateIntegrationTest`
- `AuthenticatedControlPlaneUsingMTLSClientNotAuthenticatedIntegrationTest`

## Regenerating CA Certificates

If the CA certificates themselves expire (Feb 2028), follow the instructions in:
- `mockserver-netty/src/test/resources/org/mockserver/netty/integration/tls/generate_custom_certificates.md`
- `mockserver-netty/src/test/resources/org/mockserver/netty/integration/tls/ec/generate_custom_certificates.md`
