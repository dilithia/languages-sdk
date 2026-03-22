package sdk

import (
	"context"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

// ---------------------------------------------------------------------------
// Credential mutations (POST /call)
// ---------------------------------------------------------------------------

func TestRegisterSchema(t *testing.T) {
	var gotBody map[string]any
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost || !strings.HasSuffix(r.URL.Path, "/call") {
			t.Fatalf("unexpected request: %s %s", r.Method, r.URL.Path)
		}
		body, _ := io.ReadAll(r.Body)
		_ = json.Unmarshal(body, &gotBody)
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`{"accepted":true,"tx_hash":"0xtest"}`))
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithTimeout(time.Second))
	attrs := []SchemaAttribute{
		{Name: "name", Type: "string"},
		{Name: "age", Type: "uint64"},
	}
	result, err := client.RegisterSchema(context.Background(), "identity", "1.0", attrs)
	if err != nil {
		t.Fatalf("RegisterSchema error: %v", err)
	}
	if !result.Accepted {
		t.Fatal("expected accepted")
	}
	if result.TxHash != "0xtest" {
		t.Fatalf("unexpected tx_hash: %s", result.TxHash)
	}
	if gotBody["contract"] != "credential" {
		t.Fatalf("unexpected contract: %v", gotBody["contract"])
	}
	if gotBody["method"] != "register_schema" {
		t.Fatalf("unexpected method: %v", gotBody["method"])
	}
	args, _ := gotBody["args"].(map[string]any)
	if args["name"] != "identity" {
		t.Fatalf("unexpected name: %v", args["name"])
	}
	if args["version"] != "1.0" {
		t.Fatalf("unexpected version: %v", args["version"])
	}
	attrList, ok := args["attributes"].([]any)
	if !ok || len(attrList) != 2 {
		t.Fatalf("unexpected attributes: %v", args["attributes"])
	}
}

func TestIssueCredential(t *testing.T) {
	var gotBody map[string]any
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		body, _ := io.ReadAll(r.Body)
		_ = json.Unmarshal(body, &gotBody)
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`{"accepted":true,"tx_hash":"0xtest"}`))
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithTimeout(time.Second))
	result, err := client.IssueCredential(context.Background(), "dili1holder", "0xschema", "0xcommit", map[string]any{
		"name": "Alice",
		"age":  25,
	})
	if err != nil {
		t.Fatalf("IssueCredential error: %v", err)
	}
	if !result.Accepted {
		t.Fatal("expected accepted")
	}
	if gotBody["contract"] != "credential" {
		t.Fatalf("unexpected contract: %v", gotBody["contract"])
	}
	if gotBody["method"] != "issue" {
		t.Fatalf("unexpected method: %v", gotBody["method"])
	}
	args, _ := gotBody["args"].(map[string]any)
	if args["holder"] != "dili1holder" {
		t.Fatalf("unexpected holder: %v", args["holder"])
	}
	if args["schema_hash"] != "0xschema" {
		t.Fatalf("unexpected schema_hash: %v", args["schema_hash"])
	}
	if args["commitment"] != "0xcommit" {
		t.Fatalf("unexpected commitment: %v", args["commitment"])
	}
}

func TestIssueCredentialNilAttributes(t *testing.T) {
	var gotBody map[string]any
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		body, _ := io.ReadAll(r.Body)
		_ = json.Unmarshal(body, &gotBody)
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`{"accepted":true,"tx_hash":"0xtest"}`))
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithTimeout(time.Second))
	result, err := client.IssueCredential(context.Background(), "dili1holder", "0xschema", "0xcommit", nil)
	if err != nil {
		t.Fatalf("IssueCredential error: %v", err)
	}
	if !result.Accepted {
		t.Fatal("expected accepted")
	}
	args, _ := gotBody["args"].(map[string]any)
	if _, hasAttrs := args["attributes"]; hasAttrs {
		t.Fatal("expected no attributes key when nil")
	}
}

func TestRevokeCredential(t *testing.T) {
	var gotBody map[string]any
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		body, _ := io.ReadAll(r.Body)
		_ = json.Unmarshal(body, &gotBody)
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`{"accepted":true,"tx_hash":"0xtest"}`))
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithTimeout(time.Second))
	result, err := client.RevokeCredential(context.Background(), "0xcommit")
	if err != nil {
		t.Fatalf("RevokeCredential error: %v", err)
	}
	if !result.Accepted {
		t.Fatal("expected accepted")
	}
	if gotBody["method"] != "revoke" {
		t.Fatalf("unexpected method: %v", gotBody["method"])
	}
	args, _ := gotBody["args"].(map[string]any)
	if args["commitment"] != "0xcommit" {
		t.Fatalf("unexpected commitment: %v", args["commitment"])
	}
}

func TestVerifyCredential(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`{"accepted":true,"tx_hash":"0xtest"}`))
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithTimeout(time.Second))
	vr, err := client.VerifyCredential(context.Background(), "0xcommit", "0xschema", "0xproof", nil, nil)
	if err != nil {
		t.Fatalf("VerifyCredential error: %v", err)
	}
	if !vr.Valid {
		t.Fatal("expected valid")
	}
	if vr.Commitment != "0xcommit" {
		t.Fatalf("unexpected commitment: %s", vr.Commitment)
	}
}

func TestVerifyCredentialWithPredicates(t *testing.T) {
	var gotBody map[string]any
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		body, _ := io.ReadAll(r.Body)
		_ = json.Unmarshal(body, &gotBody)
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`{"accepted":true,"tx_hash":"0xtest"}`))
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithTimeout(time.Second))
	revealed := map[string]any{"name": "Alice"}
	predicates := []map[string]any{{"field": "age", "op": ">=", "value": 18}}
	vr, err := client.VerifyCredential(context.Background(), "0xcommit", "0xschema", "0xproof", revealed, predicates)
	if err != nil {
		t.Fatalf("VerifyCredential error: %v", err)
	}
	if !vr.Valid {
		t.Fatal("expected valid")
	}
	args, _ := gotBody["args"].(map[string]any)
	if args["proof"] != "0xproof" {
		t.Fatalf("unexpected proof: %v", args["proof"])
	}
}

// ---------------------------------------------------------------------------
// Credential queries (via QueryContract -> GET /query)
// ---------------------------------------------------------------------------

func TestGetCredential(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`{"credential":{"issuer":"dili1issuer","holder":"dili1holder","schema_hash":"0xschema","status":"active"},"revoked":false}`))
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithChainBaseURL(srv.URL+"/chain"))
	cred, err := client.GetCredential(context.Background(), "0xcommit")
	if err != nil {
		t.Fatalf("GetCredential error: %v", err)
	}
	if cred == nil {
		t.Fatal("expected non-nil credential")
	}
	if cred.Commitment != "0xcommit" {
		t.Fatalf("unexpected commitment: %s", cred.Commitment)
	}
	if cred.Issuer != "dili1issuer" {
		t.Fatalf("unexpected issuer: %s", cred.Issuer)
	}
	if cred.Holder != "dili1holder" {
		t.Fatalf("unexpected holder: %s", cred.Holder)
	}
	if cred.SchemaHash != "0xschema" {
		t.Fatalf("unexpected schema_hash: %s", cred.SchemaHash)
	}
	if cred.Status != "active" {
		t.Fatalf("unexpected status: %s", cred.Status)
	}
	if cred.Revoked {
		t.Fatal("expected not revoked")
	}
}

func TestGetSchema(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`{"schema":{"name":"identity","version":"1.0","attributes":[{"name":"name","type":"string"},{"name":"age","type":"uint64"}]}}`))
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithChainBaseURL(srv.URL+"/chain"))
	schema, err := client.GetSchema(context.Background(), "0xschema")
	if err != nil {
		t.Fatalf("GetSchema error: %v", err)
	}
	if schema == nil {
		t.Fatal("expected non-nil schema")
	}
	if schema.Name != "identity" {
		t.Fatalf("unexpected name: %s", schema.Name)
	}
	if schema.Version != "1.0" {
		t.Fatalf("unexpected version: %s", schema.Version)
	}
	if len(schema.Attributes) != 2 {
		t.Fatalf("expected 2 attributes, got %d", len(schema.Attributes))
	}
	if schema.Attributes[0].Name != "name" || schema.Attributes[0].Type != "string" {
		t.Fatalf("unexpected first attribute: %+v", schema.Attributes[0])
	}
	if schema.Attributes[1].Name != "age" || schema.Attributes[1].Type != "uint64" {
		t.Fatalf("unexpected second attribute: %+v", schema.Attributes[1])
	}
}

func TestListCredentialsByHolder(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`{"credentials":[{"commitment":"0xc1","issuer":"dili1issuer","holder":"dili1holder","schema_hash":"0xs1","status":"active","revoked":false},{"commitment":"0xc2","issuer":"dili1issuer2","holder":"dili1holder","schema_hash":"0xs2","status":"revoked","revoked":true}]}`))
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithChainBaseURL(srv.URL+"/chain"))
	creds, err := client.ListCredentialsByHolder(context.Background(), "dili1holder")
	if err != nil {
		t.Fatalf("ListCredentialsByHolder error: %v", err)
	}
	if len(creds) != 2 {
		t.Fatalf("expected 2 credentials, got %d", len(creds))
	}
	if creds[0].Commitment != "0xc1" {
		t.Fatalf("unexpected first commitment: %s", creds[0].Commitment)
	}
	if creds[1].Revoked != true {
		t.Fatal("expected second credential to be revoked")
	}
}

func TestListCredentialsByIssuer(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`{"credentials":[{"commitment":"0xc1","issuer":"dili1issuer","holder":"dili1holder","schema_hash":"0xs1","status":"active","revoked":false}]}`))
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithChainBaseURL(srv.URL+"/chain"))
	creds, err := client.ListCredentialsByIssuer(context.Background(), "dili1issuer")
	if err != nil {
		t.Fatalf("ListCredentialsByIssuer error: %v", err)
	}
	if len(creds) != 1 {
		t.Fatalf("expected 1 credential, got %d", len(creds))
	}
	if creds[0].Issuer != "dili1issuer" {
		t.Fatalf("unexpected issuer: %s", creds[0].Issuer)
	}
}

// ---------------------------------------------------------------------------
// GetCredential edge cases
// ---------------------------------------------------------------------------

func TestGetCredentialNoCredentialField(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`{"revoked":false}`))
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithChainBaseURL(srv.URL+"/chain"))
	cred, err := client.GetCredential(context.Background(), "0xmissing")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if cred != nil {
		t.Fatal("expected nil credential when 'credential' key is missing")
	}
}

func TestGetCredentialDefaultStatus(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`{"credential":{"issuer":"i","holder":"h","schema_hash":"s"}}`))
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithChainBaseURL(srv.URL+"/chain"))
	cred, err := client.GetCredential(context.Background(), "0xcommit")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if cred == nil {
		t.Fatal("expected non-nil credential")
	}
	if cred.Status != "active" {
		t.Fatalf("expected default status 'active', got: %s", cred.Status)
	}
	if cred.Revoked {
		t.Fatal("expected revoked=false when field is missing")
	}
}

func TestGetCredentialRevoked(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`{"credential":{"issuer":"i","holder":"h","schema_hash":"s","status":"revoked"},"revoked":true}`))
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithChainBaseURL(srv.URL+"/chain"))
	cred, err := client.GetCredential(context.Background(), "0xrev")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !cred.Revoked {
		t.Fatal("expected revoked=true")
	}
	if cred.Status != "revoked" {
		t.Fatalf("unexpected status: %s", cred.Status)
	}
}

func TestGetCredentialHTTPError(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithChainBaseURL(srv.URL+"/chain"))
	_, err := client.GetCredential(context.Background(), "0xfail")
	if err == nil {
		t.Fatal("expected error for 500 response")
	}
}

func TestGetCredentialUnexpectedResultType(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`"just a string"`))
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithChainBaseURL(srv.URL+"/chain"))
	_, err := client.GetCredential(context.Background(), "0xbad")
	if err == nil {
		t.Fatal("expected error for unexpected result type")
	}
}

// ---------------------------------------------------------------------------
// listCredentials edge cases
// ---------------------------------------------------------------------------

func TestListCredentialsByHolderEmpty(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`{}`))
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithChainBaseURL(srv.URL+"/chain"))
	creds, err := client.ListCredentialsByHolder(context.Background(), "dili1nobody")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if creds != nil {
		t.Fatalf("expected nil for missing credentials key, got %d items", len(creds))
	}
}

func TestListCredentialsByHolderHTTPError(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithChainBaseURL(srv.URL+"/chain"))
	_, err := client.ListCredentialsByHolder(context.Background(), "dili1fail")
	if err == nil {
		t.Fatal("expected error for 500 response")
	}
}

func TestListCredentialsByHolderUnexpectedType(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`"not an object"`))
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithChainBaseURL(srv.URL+"/chain"))
	_, err := client.ListCredentialsByHolder(context.Background(), "dili1bad")
	if err == nil {
		t.Fatal("expected error for unexpected result type")
	}
}

func TestListCredentialsByIssuerEmpty(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`{}`))
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithChainBaseURL(srv.URL+"/chain"))
	creds, err := client.ListCredentialsByIssuer(context.Background(), "dili1nobody")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if creds != nil {
		t.Fatalf("expected nil for missing credentials key, got %d items", len(creds))
	}
}

// ---------------------------------------------------------------------------
// GetSchema edge cases
// ---------------------------------------------------------------------------

func TestGetSchemaNotFound(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`{}`))
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithChainBaseURL(srv.URL+"/chain"))
	schema, err := client.GetSchema(context.Background(), "0xmissing")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if schema != nil {
		t.Fatal("expected nil schema when key is missing")
	}
}

func TestGetSchemaEmptyAttributes(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`{"schema":{"name":"test","version":"1.0"}}`))
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithChainBaseURL(srv.URL+"/chain"))
	schema, err := client.GetSchema(context.Background(), "0xschema")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if schema == nil {
		t.Fatal("expected non-nil schema")
	}
	if len(schema.Attributes) != 0 {
		t.Fatalf("expected 0 attributes, got %d", len(schema.Attributes))
	}
}
