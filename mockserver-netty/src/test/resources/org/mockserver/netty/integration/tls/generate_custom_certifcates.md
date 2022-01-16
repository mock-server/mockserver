# Generate custom CA X509 and Private Key and Leaf X509 and Private Key

## generate CA X509 and CA Private Key (PKCS#1)
cfssl genkey -initca ca-csr.json | cfssljson -bare ca
## clean up CSR file
rm -rf ca.csr
## convert CA Private Key for PKCS#1 to PKCS#8
openssl pkcs8 -topk8 -inform PEM -in ca-key.pem -out ca-key-pkcs8.pem -nocrypt

## generate leaf X509 and Private Key (PKCS#1) as JSON
cfssl gencert -ca ca.pem -ca-key ca-key.pem csr.json | jq > leaf-cert-and-key.json
## separate leaf X509 from JSON into PEM
cat leaf-cert-and-key.json | jq  -r  '.cert' > leaf-cert.pem
## separate leaf Private Key (PKCS#1) from JSON into PEM
cat leaf-cert-and-key.json | jq  -r  '.key' > leaf-key.pem
## convert leaf Private Key for PKCS#1 to PKCS#8
openssl pkcs8 -topk8 -inform PEM -in leaf-key.pem -out leaf-key-pkcs8.pem -nocrypt
## create chain file
cat leaf-cert.pem > leaf-cert-chain.pem
cat ca.pem >> leaf-cert-chain.pem
## clean up json file
rm -rf leaf-cert-and-key.json


# download certificates from domain
openssl s_client -host stackoverflow.com -port 443 -prexit -showcerts > stackoverflow-chain.pem

# finally update the chains
