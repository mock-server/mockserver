package org.mockserver.socket.tls.jdk;

/**
 * @author jamesdbloom
 */
public class X509AndPrivateKey {

    private String cert;
    private String privateKey;

    public String getCert() {
        return cert;
    }

    public X509AndPrivateKey setCert(String cert) {
        this.cert = cert;
        return this;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public X509AndPrivateKey setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
        return this;
    }
}
