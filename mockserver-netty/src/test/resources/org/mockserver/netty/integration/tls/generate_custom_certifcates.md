# Install CloudFlare's PKI/TLS toolkit (CFSSL)

- See: https://github.com/cloudflare/cfssl
- Homebrew: https://formulae.brew.sh/formula/cfssl

# Generate CA X509 and Leaf Private Key & X509

```bash
## CA
# 1. create CSR for CA
cat << EOF > ca-csr.json
{
  "key": {
    "algo": "rsa",
    "size": 2048
  },
  "names": [
    {
      "C": "UK",
      "L": "London",
      "O": "MockServer",
      "CN": "www.mockserver.com"
    }
  ]
}
EOF
# 1. generate CA X509 and CA Private Key (PKCS#1)
cfssl genkey -initca ca-csr.json | cfssljson -bare ca
# 2. clean up CSR file
rm -rf ca.csr
# 3. convert CA Private Key for PKCS#1 to PKCS#8
openssl pkcs8 -topk8 -inform PEM -in ca-key.pem -out ca-key-pkcs8.pem -nocrypt

## Leaf
# 1. create CSR for leaf
cat << EOF > ca-csr.json
{
  "hosts": [
    "example.com",
    "www.example.com",
    "https://www.example.com",
    "localhost",
    "127.0.0.1"
  ],
  "key": {
    "algo": "rsa",
    "size": 2048
  },
  "names": [
    {
      "C": "UK",
      "L": "London",
      "O": "MockServer",
      "CN": "www.mockserver.com"
    }
  ]
}
EOF
# 4. generate leaf X509 and Private Key (PKCS#1) as JSON
cfssl gencert -ca ca.pem -ca-key ca-key.pem csr.json | jq > leaf-cert-and-key.json
# 5. separate leaf X509 from JSON into PEM
cat leaf-cert-and-key.json | jq  -r  '.cert' > leaf-cert.pem
# 6. separate leaf Private Key (PKCS#1) from JSON into PEM
cat leaf-cert-and-key.json | jq  -r  '.key' > leaf-key.pem
# 7. convert leaf Private Key for PKCS#1 to PKCS#8
openssl pkcs8 -topk8 -inform PEM -in leaf-key.pem -out leaf-key-pkcs8.pem -nocrypt

# Certificate Chain
# 8. create chain file
cat leaf-cert.pem > leaf-cert-chain.pem
cat ca.pem >> leaf-cert-chain.pem
# 9. clean up json file
rm -rf leaf-cert-and-key.json
```

# Download certificates from existing domain

```bash
# for example to download stackoverflow.com certificate chain
openssl s_client -host stackoverflow.com -port 443 -prexit -showcerts > stackoverflow-chain.pem
```
