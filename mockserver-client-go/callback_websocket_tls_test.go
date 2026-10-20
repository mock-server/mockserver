package mockserver

import (
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/tls"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/pem"
	"math/big"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/gorilla/websocket"
)

// fakeTLSCallbackWebSocketServer upgrades /_mockserver_callback_websocket over
// TLS, asking for (but not requiring) a client certificate, and records the
// certificates each upgrade presented.
func fakeTLSCallbackWebSocketServer(t *testing.T) (*httptest.Server, chan []*x509.Certificate) {
	t.Helper()
	presented := make(chan []*x509.Certificate, 10)
	srv := httptest.NewUnstartedServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/_mockserver_callback_websocket" {
			w.WriteHeader(http.StatusNotFound)
			return
		}
		presented <- r.TLS.PeerCertificates
		conn, err := (&websocket.Upgrader{}).Upgrade(w, r, nil)
		if err != nil {
			return
		}
		defer conn.Close()
		_ = conn.WriteMessage(websocket.TextMessage, []byte(`{"type":"org.mockserver.serialization.model.WebSocketClientIdDTO","value":"{\"clientId\":\"client-one\"}"}`))
		for {
			if _, _, err := conn.ReadMessage(); err != nil {
				return
			}
		}
	}))
	srv.TLS = &tls.Config{ClientAuth: tls.RequestClientCert}
	srv.StartTLS()
	t.Cleanup(srv.Close)
	return srv, presented
}

func serverCAPEM(srv *httptest.Server) []byte {
	return pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: srv.Certificate().Raw})
}

// writeClientCertificate writes a self-signed client certificate and its key
// as PEM files, returning their paths.
func writeClientCertificate(t *testing.T, commonName string) (string, string) {
	t.Helper()
	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	template := &x509.Certificate{
		SerialNumber: big.NewInt(1),
		Subject:      pkix.Name{CommonName: commonName},
		NotBefore:    time.Now().Add(-time.Hour),
		NotAfter:     time.Now().Add(time.Hour),
		ExtKeyUsage:  []x509.ExtKeyUsage{x509.ExtKeyUsageClientAuth},
	}
	der, err := x509.CreateCertificate(rand.Reader, template, template, &key.PublicKey, key)
	if err != nil {
		t.Fatal(err)
	}
	keyDER, err := x509.MarshalPKCS8PrivateKey(key)
	if err != nil {
		t.Fatal(err)
	}
	dir := t.TempDir()
	certPath := filepath.Join(dir, "client-cert.pem")
	keyPath := filepath.Join(dir, "client-key.pem")
	if err := os.WriteFile(certPath, pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: der}), 0o600); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(keyPath, pem.EncodeToMemory(&pem.Block{Type: "PRIVATE KEY", Bytes: keyDER}), 0o600); err != nil {
		t.Fatal(err)
	}
	return certPath, keyPath
}

func callbackWebSocketPeerCertificates(t *testing.T, srv *httptest.Server, presented chan []*x509.Certificate, opts ...Option) []*x509.Certificate {
	t.Helper()
	client := NewFromURL(srv.URL, opts...)
	if err := client.ensureBreakpointWS(); err != nil {
		t.Fatalf("callback WebSocket connect failed: %v", err)
	}
	defer client.breakpointWS.close()
	select {
	case certificates := <-presented:
		return certificates
	case <-time.After(5 * time.Second):
		t.Fatal("no WebSocket upgrade received")
		return nil
	}
}

func TestCallbackWebSocket_PresentsTheConfiguredClientCertificate(t *testing.T) {
	srv, presented := fakeTLSCallbackWebSocketServer(t)
	certPath, keyPath := writeClientCertificate(t, "callback-client")

	certificates := callbackWebSocketPeerCertificates(t, srv, presented, WithCACertPEM(serverCAPEM(srv)), WithClientCertPEM(certPath, keyPath))

	if len(certificates) != 1 || certificates[0].Subject.CommonName != "callback-client" {
		t.Fatalf("expected the configured client certificate on the upgrade, got %v", certificates)
	}
}

func TestCallbackWebSocket_PresentsNoClientCertificateWithoutOne(t *testing.T) {
	srv, presented := fakeTLSCallbackWebSocketServer(t)

	certificates := callbackWebSocketPeerCertificates(t, srv, presented, WithCACertPEM(serverCAPEM(srv)))

	if len(certificates) != 0 {
		t.Fatalf("expected no client certificate on the upgrade, got %v", certificates)
	}
}

func TestCallbackWebSocket_UsesTheSuppliedHTTPClientsTLSConfig(t *testing.T) {
	srv, presented := fakeTLSCallbackWebSocketServer(t)
	certPath, keyPath := writeClientCertificate(t, "supplied-client")
	certificate, err := tls.LoadX509KeyPair(certPath, keyPath)
	if err != nil {
		t.Fatal(err)
	}
	roots := x509.NewCertPool()
	roots.AddCert(srv.Certificate())
	httpClient := &http.Client{Transport: &http.Transport{TLSClientConfig: &tls.Config{
		RootCAs:      roots,
		Certificates: []tls.Certificate{certificate},
		NextProtos:   []string{"h2", "http/1.1"},
	}}}

	certificates := callbackWebSocketPeerCertificates(t, srv, presented, WithHTTPClient(httpClient))

	if len(certificates) != 1 || certificates[0].Subject.CommonName != "supplied-client" {
		t.Fatalf("expected the supplied HTTP client's certificate on the upgrade, got %v", certificates)
	}
}

func TestCallbackWebSocket_ReportsATLSConfigurationError(t *testing.T) {
	srv, presented := fakeTLSCallbackWebSocketServer(t)
	client := NewFromURL(srv.URL, WithCACertPEMPath(filepath.Join(t.TempDir(), "missing-ca.pem")))

	if err := client.ensureBreakpointWS(); err == nil || !strings.Contains(err.Error(), "read CA cert") {
		t.Fatalf("expected the TLS configuration error, got %v", err)
	}
	select {
	case <-presented:
		t.Fatal("expected no WebSocket upgrade")
	default:
	}
}

func TestCallbackWebSocket_RejectsAServerTheConfiguredCADidNotSign(t *testing.T) {
	srv, presented := fakeTLSCallbackWebSocketServer(t)
	certPath, keyPath := writeClientCertificate(t, "callback-client")
	otherCA, err := os.ReadFile(certPath)
	if err != nil {
		t.Fatal(err)
	}
	client := NewFromURL(srv.URL, WithCACertPEM(otherCA), WithClientCertPEM(certPath, keyPath))

	if err := client.ensureBreakpointWS(); err == nil {
		client.breakpointWS.close()
		t.Fatal("expected the connection to be refused: the configured CA did not sign the server's certificate")
	}
	select {
	case <-presented:
		t.Fatal("expected no WebSocket upgrade")
	default:
	}
}
