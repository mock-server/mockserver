package mockserver

import (
	"bytes"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/tls"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/base64"
	"encoding/pem"
	"log"
	"math/big"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

// tlsConfigSentinel is a recognisable TLS config used to assert precedence when
// the caller supplies their own http.Client with TLS already configured.
var tlsConfigSentinel = tls.Config{ServerName: "user-supplied-sentinel"}

// --- Control-plane bearer token ---

func TestWithControlPlaneBearerToken_AttachedToControlPlaneRequest(t *testing.T) {
	var gotAuth string
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotAuth = r.Header.Get("Authorization")
		w.WriteHeader(200)
		w.Write([]byte(`{"ports":[1080]}`))
	}))
	defer ts.Close()

	client := NewFromURL(ts.URL, WithControlPlaneBearerToken("my-jwt-token"))
	if _, err := client.Status(); err != nil {
		t.Fatalf("status failed: %v", err)
	}

	if gotAuth != "Bearer my-jwt-token" {
		t.Errorf("expected Authorization 'Bearer my-jwt-token', got %q", gotAuth)
	}
}

func TestWithControlPlaneBearerTokenSupplier_CalledPerRequest(t *testing.T) {
	var seen []string
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		seen = append(seen, r.Header.Get("Authorization"))
		w.WriteHeader(200)
		w.Write([]byte(`{"ports":[1080]}`))
	}))
	defer ts.Close()

	n := 0
	client := NewFromURL(ts.URL, WithControlPlaneBearerTokenSupplier(func() string {
		n++
		return "token-" + string(rune('0'+n))
	}))

	if _, err := client.Status(); err != nil {
		t.Fatalf("status 1 failed: %v", err)
	}
	if _, err := client.Status(); err != nil {
		t.Fatalf("status 2 failed: %v", err)
	}

	if len(seen) != 2 || seen[0] != "Bearer token-1" || seen[1] != "Bearer token-2" {
		t.Errorf("expected per-request tokens token-1, token-2; got %v", seen)
	}
}

func TestWithControlPlaneBasicAuthentication_AttachedToControlPlaneRequest(t *testing.T) {
	var seen []string
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		seen = append(seen, r.Header.Get("Authorization"))
		w.WriteHeader(200)
		w.Write([]byte(`{"ports":[1080]}`))
	}))
	defer ts.Close()

	if _, err := NewFromURL(ts.URL, WithControlPlaneBasicAuthentication("admin", "pä:ss")).Status(); err != nil {
		t.Fatalf("status failed: %v", err)
	}
	if _, err := NewFromURL(ts.URL, WithControlPlaneBasicAuthentication("admin", "s3cret"), WithControlPlaneBearerToken("jwt")).Status(); err != nil {
		t.Fatalf("status failed: %v", err)
	}
	if _, err := NewFromURL(ts.URL, WithControlPlaneBasicAuthentication("a:b", "s3cret")).Status(); err == nil || !strings.Contains(err.Error(), "without ':'") {
		t.Errorf("expected an unsendable username to fail the request, got %v", err)
	}

	want := []string{"Basic " + base64.StdEncoding.EncodeToString([]byte("admin:pä:ss")), "Bearer jwt"}
	if len(seen) != 2 || seen[0] != want[0] || seen[1] != want[1] {
		t.Errorf("expected %q, got %q", want, seen)
	}
}

func TestWithControlPlaneBasicAuthentication_WarnsWithoutTLSAndKeepsCredentialsOutOfErrors(t *testing.T) {
	var logged bytes.Buffer
	log.SetOutput(&logged)
	defer log.SetOutput(os.Stderr)
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(500)
		w.Write([]byte("failed"))
	}))
	defer ts.Close()

	_, err := NewFromURL(ts.URL, WithControlPlaneBasicAuthentication("admin", "pa55-near-miss")).Status()

	if strings.Count(logged.String(), "without TLS") != 1 || strings.Contains(logged.String(), "pa55-near-miss") {
		t.Errorf("expected one warning without the password, got %q", logged.String())
	}
	if err == nil {
		t.Fatal("expected the 500 to fail the request")
	}
	if strings.Contains(err.Error(), "pa55-near-miss") || strings.Contains(err.Error(), base64.StdEncoding.EncodeToString([]byte("admin:pa55-near-miss"))) {
		t.Errorf("credentials leaked into the error: %v", err)
	}
}

func TestNoBearerToken_NoAuthorizationHeader(t *testing.T) {
	var hadAuth bool
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_, hadAuth = r.Header["Authorization"]
		w.WriteHeader(200)
		w.Write([]byte(`{"ports":[1080]}`))
	}))
	defer ts.Close()

	client := NewFromURL(ts.URL)
	if _, err := client.Status(); err != nil {
		t.Fatalf("status failed: %v", err)
	}

	if hadAuth {
		t.Errorf("expected no Authorization header when no token configured")
	}
}

func TestEmptyToken_NoAuthorizationHeader(t *testing.T) {
	var hadAuth bool
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_, hadAuth = r.Header["Authorization"]
		w.WriteHeader(200)
		w.Write([]byte(`{"ports":[1080]}`))
	}))
	defer ts.Close()

	client := NewFromURL(ts.URL, WithControlPlaneBearerTokenSupplier(func() string { return "" }))
	if _, err := client.Status(); err != nil {
		t.Fatalf("status failed: %v", err)
	}

	if hadAuth {
		t.Errorf("expected no Authorization header when supplier returns empty string")
	}
}

// --- TLS / mTLS configuration wiring ---

func TestWithCACertPEM_PopulatesTransportRootCAs(t *testing.T) {
	caPEM, _, _ := generateCert(t, "MockServer Test CA", true)

	client := NewFromURL("https://localhost:1080", WithCACertPEM(caPEM))

	transport, ok := client.httpClient.Transport.(*http.Transport)
	if !ok {
		t.Fatalf("expected *http.Transport, got %T", client.httpClient.Transport)
	}
	if transport.TLSClientConfig == nil {
		t.Fatal("expected TLSClientConfig to be set")
	}
	if transport.TLSClientConfig.RootCAs == nil {
		t.Fatal("expected RootCAs to be populated")
	}
}

func TestWithCACertPEMPath_PopulatesTransportRootCAs(t *testing.T) {
	caPEM, _, _ := generateCert(t, "MockServer Test CA", true)
	dir := t.TempDir()
	caPath := filepath.Join(dir, "ca.pem")
	if err := os.WriteFile(caPath, caPEM, 0o600); err != nil {
		t.Fatal(err)
	}

	client := NewFromURL("https://localhost:1080", WithCACertPEMPath(caPath))

	transport := client.httpClient.Transport.(*http.Transport)
	if transport.TLSClientConfig == nil || transport.TLSClientConfig.RootCAs == nil {
		t.Fatal("expected RootCAs populated from PEM path")
	}
}

func TestWithCACertPEMPath_MissingFile_SurfacesError(t *testing.T) {
	client := NewFromURL("https://localhost:1080", WithCACertPEMPath("/no/such/ca.pem"))

	// Error is deferred until the next request.
	if _, err := client.Status(); err == nil {
		t.Fatal("expected error from missing CA file")
	}
}

func TestWithClientCertPEM_PopulatesTransportCertificates(t *testing.T) {
	certPEM, keyPEM, _ := generateCert(t, "client", false)
	dir := t.TempDir()
	certPath := filepath.Join(dir, "client.crt")
	keyPath := filepath.Join(dir, "client.key")
	if err := os.WriteFile(certPath, certPEM, 0o600); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(keyPath, keyPEM, 0o600); err != nil {
		t.Fatal(err)
	}

	client := NewFromURL("https://localhost:1080", WithClientCertPEM(certPath, keyPath))

	transport := client.httpClient.Transport.(*http.Transport)
	if transport.TLSClientConfig == nil {
		t.Fatal("expected TLSClientConfig to be set")
	}
	if len(transport.TLSClientConfig.Certificates) != 1 {
		t.Fatalf("expected 1 client certificate, got %d", len(transport.TLSClientConfig.Certificates))
	}
}

func TestCAAndClientCert_ComposeOnOneTLSConfig(t *testing.T) {
	caPEM, _, _ := generateCert(t, "MockServer Test CA", true)
	certPEM, keyPEM, _ := generateCert(t, "client", false)
	dir := t.TempDir()
	certPath := filepath.Join(dir, "client.crt")
	keyPath := filepath.Join(dir, "client.key")
	if err := os.WriteFile(certPath, certPEM, 0o600); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(keyPath, keyPEM, 0o600); err != nil {
		t.Fatal(err)
	}

	client := NewFromURL("https://localhost:1080",
		WithCACertPEM(caPEM),
		WithClientCertPEM(certPath, keyPath),
	)

	cfg := client.httpClient.Transport.(*http.Transport).TLSClientConfig
	if cfg == nil || cfg.RootCAs == nil || len(cfg.Certificates) != 1 {
		t.Fatalf("expected one TLS config carrying both RootCAs and a client cert; got %+v", cfg)
	}
}

func TestUserSuppliedTLSConfig_TakesPrecedence(t *testing.T) {
	caPEM, _, _ := generateCert(t, "MockServer Test CA", true)

	userTransport := &http.Transport{TLSClientConfig: &tlsConfigSentinel}
	userClient := &http.Client{Transport: userTransport, Timeout: 5 * time.Second}

	client := NewFromURL("https://localhost:1080",
		WithHTTPClient(userClient),
		WithCACertPEM(caPEM),
	)

	got := client.httpClient.Transport.(*http.Transport).TLSClientConfig
	if got != &tlsConfigSentinel {
		t.Errorf("expected user-supplied TLSClientConfig to be preserved (precedence), got %p", got)
	}
}

// generateCert returns a freshly-generated self-signed certificate as PEM and
// its private key as PEM. When isCA is true the certificate is marked as a CA.
func generateCert(t *testing.T, cn string, isCA bool) (certPEM, keyPEM []byte, der []byte) {
	t.Helper()
	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	tmpl := &x509.Certificate{
		SerialNumber:          big.NewInt(1),
		Subject:               pkix.Name{CommonName: cn},
		NotBefore:             time.Now().Add(-time.Hour),
		NotAfter:              time.Now().Add(time.Hour),
		KeyUsage:              x509.KeyUsageDigitalSignature | x509.KeyUsageCertSign,
		BasicConstraintsValid: true,
		IsCA:                  isCA,
	}
	der, err = x509.CreateCertificate(rand.Reader, tmpl, tmpl, &key.PublicKey, key)
	if err != nil {
		t.Fatal(err)
	}
	certPEM = pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: der})

	keyDER, err := x509.MarshalPKCS8PrivateKey(key)
	if err != nil {
		t.Fatal(err)
	}
	keyPEM = pem.EncodeToMemory(&pem.Block{Type: "PRIVATE KEY", Bytes: keyDER})
	return certPEM, keyPEM, der
}
