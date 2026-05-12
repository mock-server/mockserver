#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}"

# Test-only defaults — use shorter validity and your organisation's PKI policy for non-local environments
DAYS=3650
KEY_SIZE=2048
COUNTRY="UK"
STATE="England"
CITY="London"
ORG="MockServer"

echo "=== Generating mTLS certificates for MockServer ==="
echo ""

generate_with_openssl() {
    echo "--- Using OpenSSL ---"
    echo ""

    echo "[1/6] Generating CA private key..."
    openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:${KEY_SIZE} -out ca-key.pem

    echo "[2/6] Generating CA certificate..."
    openssl req -new -x509 -key ca-key.pem -out ca.pem -days ${DAYS} \
        -subj "/C=${COUNTRY}/ST=${STATE}/L=${CITY}/O=${ORG}/CN=MockServer CA"

    echo "[3/6] Generating server private key (PKCS#8)..."
    openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:${KEY_SIZE} -out server-key-pkcs8.pem

    echo "[4/6] Generating server certificate signed by CA..."
    openssl req -new -key server-key-pkcs8.pem -out server.csr \
        -subj "/C=${COUNTRY}/ST=${STATE}/L=${CITY}/O=${ORG}/CN=localhost"

    cat > server-ext.cnf <<EOF
authorityKeyIdentifier=keyid,issuer
basicConstraints=CA:FALSE
keyUsage=digitalSignature,keyEncipherment
extendedKeyUsage=serverAuth
subjectAltName=DNS:localhost,DNS:mockserver,DNS:host.docker.internal,IP:127.0.0.1,IP:0.0.0.0
EOF

    openssl x509 -req -in server.csr -CA ca.pem -CAkey ca-key.pem -CAcreateserial \
        -out server-cert.pem -days ${DAYS} -extfile server-ext.cnf

    cat server-cert.pem ca.pem > server-cert-chain.pem

    echo "[5/6] Generating client private key (PKCS#8)..."
    openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:${KEY_SIZE} -out client-key-pkcs8.pem

    echo "[6/6] Generating client certificate signed by CA..."
    openssl req -new -key client-key-pkcs8.pem -out client.csr \
        -subj "/C=${COUNTRY}/ST=${STATE}/L=${CITY}/O=${ORG}/CN=MockServer Client"

    cat > client-ext.cnf <<EOF
authorityKeyIdentifier=keyid,issuer
basicConstraints=CA:FALSE
keyUsage=digitalSignature,keyEncipherment
extendedKeyUsage=clientAuth
EOF

    openssl x509 -req -in client.csr -CA ca.pem -CAkey ca-key.pem -CAcreateserial \
        -out client-cert.pem -days ${DAYS} -extfile client-ext.cnf

    cat client-cert.pem ca.pem > client-cert-chain.pem

    rm -f server.csr server-ext.cnf client.csr client-ext.cnf ca.srl
}

generate_with_cfssl() {
    echo "--- Using CFSSL ---"
    echo "(install: brew install cfssl)"
    echo ""

    echo "[1/5] Generating CA..."
    cat > ca-csr.json <<EOF
{
  "key": { "algo": "rsa", "size": ${KEY_SIZE} },
  "names": [{ "C": "${COUNTRY}", "L": "${CITY}", "O": "${ORG}", "CN": "MockServer CA" }]
}
EOF
    cfssl genkey -initca ca-csr.json | cfssljson -bare ca
    rm -f ca.csr

    echo "[2/5] Creating signing config..."
    cat > cfssl-config.json <<EOF
{
  "signing": {
    "default": { "expiry": "87600h" },
    "profiles": {
      "server": {
        "expiry": "87600h",
        "usages": ["signing", "key encipherment", "server auth"]
      },
      "client": {
        "expiry": "87600h",
        "usages": ["signing", "key encipherment", "client auth"]
      }
    }
  }
}
EOF

    echo "[3/5] Generating server certificate..."
    cat > server-csr.json <<EOF
{
  "CN": "localhost",
  "hosts": ["localhost", "mockserver", "host.docker.internal", "127.0.0.1", "0.0.0.0"],
  "key": { "algo": "rsa", "size": ${KEY_SIZE} },
  "names": [{ "C": "${COUNTRY}", "L": "${CITY}", "O": "${ORG}" }]
}
EOF
    cfssl gencert -ca ca.pem -ca-key ca-key.pem -config cfssl-config.json -profile server server-csr.json | cfssljson -bare server
    rm -f server.csr
    openssl pkcs8 -topk8 -inform PEM -in server-key.pem -out server-key-pkcs8.pem -nocrypt
    mv server.pem server-cert.pem
    cat server-cert.pem ca.pem > server-cert-chain.pem

    echo "[4/5] Generating client certificate..."
    cat > client-csr.json <<EOF
{
  "CN": "MockServer Client",
  "key": { "algo": "rsa", "size": ${KEY_SIZE} },
  "names": [{ "C": "${COUNTRY}", "L": "${CITY}", "O": "${ORG}" }]
}
EOF
    cfssl gencert -ca ca.pem -ca-key ca-key.pem -config cfssl-config.json -profile client client-csr.json | cfssljson -bare client
    rm -f client.csr
    openssl pkcs8 -topk8 -inform PEM -in client-key.pem -out client-key-pkcs8.pem -nocrypt
    mv client.pem client-cert.pem
    cat client-cert.pem ca.pem > client-cert-chain.pem

    echo "[5/5] Cleaning up..."
    rm -f ca-csr.json cfssl-config.json server-csr.json client-csr.json server-key.pem client-key.pem

    echo ""
}

case "${1:-openssl}" in
    openssl)
        if ! command -v openssl &>/dev/null; then
            echo "ERROR: openssl is required but not found in PATH."
            exit 1
        fi
        generate_with_openssl
        ;;
    cfssl)
        if ! command -v cfssl &>/dev/null || ! command -v cfssljson &>/dev/null; then
            echo "ERROR: cfssl and cfssljson are required. Install with: brew install cfssl"
            exit 1
        fi
        generate_with_cfssl
        ;;
    -h|--help)
        echo "Usage: $0 [openssl|cfssl]"
        echo "  openssl  Generate certificates using OpenSSL (default)"
        echo "  cfssl    Generate certificates using CloudFlare CFSSL"
        exit 0
        ;;
    *)
        echo "Usage: $0 [openssl|cfssl]"
        exit 2
        ;;
esac

echo ""
echo "=== Certificates generated successfully ==="
echo ""
echo "Files created:"
echo "  CA:     ca-key.pem, ca.pem"
echo "  Server: server-key-pkcs8.pem, server-cert.pem, server-cert-chain.pem"
echo "  Client: client-key-pkcs8.pem, client-cert.pem, client-cert-chain.pem"
echo ""
echo "=== Next steps ==="
echo ""
echo "Start MockServer with mTLS:"
echo "  docker compose up -d"
echo ""
echo "Test with curl (successful mTLS request):"
echo "  curl --cacert certs/ca.pem --cert certs/client-cert.pem --key certs/client-key-pkcs8.pem https://localhost:1080/hello"
echo ""
echo "Test without client cert (should fail with TLS handshake error):"
echo "  curl --cacert certs/ca.pem https://localhost:1080/hello"
