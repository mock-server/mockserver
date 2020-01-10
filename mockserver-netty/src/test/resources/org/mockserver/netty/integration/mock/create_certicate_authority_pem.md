To generate CA X509 Certificate and Private Key PEMs

```bash
go get -u github.com/cloudflare/cfssl/cmd/...
cat << EOF > ca-csr.json
{
  "hosts": [
    "127.0.0.1",
    "localhost"
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
cfssl gencert -initca ca-csr.json | cfssljson -bare ca
```