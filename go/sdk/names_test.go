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
// Name service mutations (POST /call)
// ---------------------------------------------------------------------------

func TestRegisterName(t *testing.T) {
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
	result, err := client.RegisterName(context.Background(), "alice.dili")
	if err != nil {
		t.Fatalf("RegisterName error: %v", err)
	}
	if !result.Accepted {
		t.Fatal("expected accepted")
	}
	if result.TxHash != "0xtest" {
		t.Fatalf("unexpected tx_hash: %s", result.TxHash)
	}
	if gotBody["contract"] != "name_service" {
		t.Fatalf("unexpected contract: %v", gotBody["contract"])
	}
	if gotBody["method"] != "register" {
		t.Fatalf("unexpected method: %v", gotBody["method"])
	}
	args, _ := gotBody["args"].(map[string]any)
	if args["name"] != "alice.dili" {
		t.Fatalf("unexpected args: %v", args)
	}
}

func TestRenewName(t *testing.T) {
	var gotBody map[string]any
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		body, _ := io.ReadAll(r.Body)
		_ = json.Unmarshal(body, &gotBody)
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`{"accepted":true,"tx_hash":"0xtest"}`))
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithTimeout(time.Second))
	result, err := client.RenewName(context.Background(), "alice.dili")
	if err != nil {
		t.Fatalf("RenewName error: %v", err)
	}
	if !result.Accepted {
		t.Fatal("expected accepted")
	}
	if gotBody["contract"] != "name_service" {
		t.Fatalf("unexpected contract: %v", gotBody["contract"])
	}
	if gotBody["method"] != "renew" {
		t.Fatalf("unexpected method: %v", gotBody["method"])
	}
	args, _ := gotBody["args"].(map[string]any)
	if args["name"] != "alice.dili" {
		t.Fatalf("unexpected args: %v", args)
	}
}

func TestTransferName(t *testing.T) {
	var gotBody map[string]any
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		body, _ := io.ReadAll(r.Body)
		_ = json.Unmarshal(body, &gotBody)
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`{"accepted":true,"tx_hash":"0xtest"}`))
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithTimeout(time.Second))
	result, err := client.TransferName(context.Background(), "alice.dili", "dili1bob")
	if err != nil {
		t.Fatalf("TransferName error: %v", err)
	}
	if !result.Accepted {
		t.Fatal("expected accepted")
	}
	args, _ := gotBody["args"].(map[string]any)
	if args["name"] != "alice.dili" {
		t.Fatalf("unexpected name arg: %v", args)
	}
	if args["new_owner"] != "dili1bob" {
		t.Fatalf("unexpected new_owner arg: %v", args)
	}
}

func TestSetNameTarget(t *testing.T) {
	var gotBody map[string]any
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		body, _ := io.ReadAll(r.Body)
		_ = json.Unmarshal(body, &gotBody)
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`{"accepted":true,"tx_hash":"0xtest"}`))
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithTimeout(time.Second))
	result, err := client.SetNameTarget(context.Background(), "alice.dili", "dili1target")
	if err != nil {
		t.Fatalf("SetNameTarget error: %v", err)
	}
	if !result.Accepted {
		t.Fatal("expected accepted")
	}
	if gotBody["method"] != "set_target" {
		t.Fatalf("unexpected method: %v", gotBody["method"])
	}
	args, _ := gotBody["args"].(map[string]any)
	if args["name"] != "alice.dili" || args["target"] != "dili1target" {
		t.Fatalf("unexpected args: %v", args)
	}
}

func TestSetNameRecord(t *testing.T) {
	var gotBody map[string]any
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		body, _ := io.ReadAll(r.Body)
		_ = json.Unmarshal(body, &gotBody)
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`{"accepted":true,"tx_hash":"0xtest"}`))
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithTimeout(time.Second))
	result, err := client.SetNameRecord(context.Background(), "alice.dili", "avatar", "https://example.com/a.png")
	if err != nil {
		t.Fatalf("SetNameRecord error: %v", err)
	}
	if !result.Accepted {
		t.Fatal("expected accepted")
	}
	if gotBody["method"] != "set_record" {
		t.Fatalf("unexpected method: %v", gotBody["method"])
	}
	args, _ := gotBody["args"].(map[string]any)
	if args["name"] != "alice.dili" || args["key"] != "avatar" || args["value"] != "https://example.com/a.png" {
		t.Fatalf("unexpected args: %v", args)
	}
}

func TestReleaseName(t *testing.T) {
	var gotBody map[string]any
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		body, _ := io.ReadAll(r.Body)
		_ = json.Unmarshal(body, &gotBody)
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`{"accepted":true,"tx_hash":"0xtest"}`))
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithTimeout(time.Second))
	result, err := client.ReleaseName(context.Background(), "alice.dili")
	if err != nil {
		t.Fatalf("ReleaseName error: %v", err)
	}
	if !result.Accepted {
		t.Fatal("expected accepted")
	}
	if gotBody["method"] != "release" {
		t.Fatalf("unexpected method: %v", gotBody["method"])
	}
	args, _ := gotBody["args"].(map[string]any)
	if args["name"] != "alice.dili" {
		t.Fatalf("unexpected args: %v", args)
	}
}

// ---------------------------------------------------------------------------
// Name service queries (GET)
// ---------------------------------------------------------------------------

func TestIsNameAvailable(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet {
			t.Fatalf("expected GET, got %s", r.Method)
		}
		if !strings.HasSuffix(r.URL.Path, "/names/available/alice.dili") {
			t.Fatalf("unexpected path: %s", r.URL.Path)
		}
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`{"available":true}`))
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithChainBaseURL(srv.URL+"/chain"))
	available, err := client.IsNameAvailable(context.Background(), "alice.dili")
	if err != nil {
		t.Fatalf("IsNameAvailable error: %v", err)
	}
	if !available {
		t.Fatal("expected name to be available")
	}
}

func TestIsNameAvailableFalse(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`{"available":false}`))
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithChainBaseURL(srv.URL+"/chain"))
	available, err := client.IsNameAvailable(context.Background(), "taken.dili")
	if err != nil {
		t.Fatalf("IsNameAvailable error: %v", err)
	}
	if available {
		t.Fatal("expected name to be unavailable")
	}
}

func TestLookupName(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if !strings.HasSuffix(r.URL.Path, "/names/lookup/alice.dili") {
			t.Fatalf("unexpected path: %s", r.URL.Path)
		}
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`{"name":"alice.dili","address":"dili1alice","target":"dili1target","expiry":1700000000}`))
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithChainBaseURL(srv.URL+"/chain"))
	entry, err := client.LookupName(context.Background(), "alice.dili")
	if err != nil {
		t.Fatalf("LookupName error: %v", err)
	}
	if entry.Name != "alice.dili" {
		t.Fatalf("unexpected name: %s", entry.Name)
	}
	if entry.Address != "dili1alice" {
		t.Fatalf("unexpected address: %s", entry.Address)
	}
	if entry.Target != "dili1target" {
		t.Fatalf("unexpected target: %s", entry.Target)
	}
	if entry.Expiry != 1700000000 {
		t.Fatalf("unexpected expiry: %d", entry.Expiry)
	}
}

func TestGetNameRecords(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if !strings.HasSuffix(r.URL.Path, "/names/records/alice.dili") {
			t.Fatalf("unexpected path: %s", r.URL.Path)
		}
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`{"records":{"avatar":"https://example.com/a.png","bio":"Hello"}}`))
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithChainBaseURL(srv.URL+"/chain"))
	records, err := client.GetNameRecords(context.Background(), "alice.dili")
	if err != nil {
		t.Fatalf("GetNameRecords error: %v", err)
	}
	if len(records) != 2 {
		t.Fatalf("expected 2 records, got %d", len(records))
	}
	if records["avatar"] != "https://example.com/a.png" {
		t.Fatalf("unexpected avatar: %s", records["avatar"])
	}
	if records["bio"] != "Hello" {
		t.Fatalf("unexpected bio: %s", records["bio"])
	}
}

func TestGetNamesByOwner(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if !strings.HasSuffix(r.URL.Path, "/names/owner/dili1alice") {
			t.Fatalf("unexpected path: %s", r.URL.Path)
		}
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`{"names":[{"name":"alice.dili","address":"dili1alice","target":"dili1target","expiry":1700000000},{"name":"bob.dili","address":"dili1alice","target":"","expiry":1800000000}]}`))
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithChainBaseURL(srv.URL+"/chain"))
	entries, err := client.GetNamesByOwner(context.Background(), "dili1alice")
	if err != nil {
		t.Fatalf("GetNamesByOwner error: %v", err)
	}
	if len(entries) != 2 {
		t.Fatalf("expected 2 entries, got %d", len(entries))
	}
	if entries[0].Name != "alice.dili" {
		t.Fatalf("unexpected first name: %s", entries[0].Name)
	}
	if entries[1].Name != "bob.dili" {
		t.Fatalf("unexpected second name: %s", entries[1].Name)
	}
	if entries[1].Expiry != 1800000000 {
		t.Fatalf("unexpected second expiry: %d", entries[1].Expiry)
	}
}

func TestGetRegistrationCost(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if !strings.HasSuffix(r.URL.Path, "/names/cost/alice.dili") {
			t.Fatalf("unexpected path: %s", r.URL.Path)
		}
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`{"name":"alice.dili","cost":5000000,"duration":31536000}`))
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithChainBaseURL(srv.URL+"/chain"))
	cost, err := client.GetRegistrationCost(context.Background(), "alice.dili")
	if err != nil {
		t.Fatalf("GetRegistrationCost error: %v", err)
	}
	if cost.Name != "alice.dili" {
		t.Fatalf("unexpected name: %s", cost.Name)
	}
	if cost.Cost != 5000000 {
		t.Fatalf("unexpected cost: %d", cost.Cost)
	}
	if cost.Duration != 31536000 {
		t.Fatalf("unexpected duration: %d", cost.Duration)
	}
}

// ---------------------------------------------------------------------------
// parseNameEntry
// ---------------------------------------------------------------------------

func TestParseNameEntry(t *testing.T) {
	entry := parseNameEntry(map[string]any{
		"name":    "alice.dili",
		"address": "dili1alice",
		"target":  "dili1target",
		"expiry":  "1700000000",
	})
	if entry.Name != "alice.dili" {
		t.Fatalf("unexpected name: %s", entry.Name)
	}
	if entry.Address != "dili1alice" {
		t.Fatalf("unexpected address: %s", entry.Address)
	}
	if entry.Target != "dili1target" {
		t.Fatalf("unexpected target: %s", entry.Target)
	}
	if entry.Expiry != 1700000000 {
		t.Fatalf("unexpected expiry: %d", entry.Expiry)
	}
}

func TestParseNameEntryEmpty(t *testing.T) {
	entry := parseNameEntry(map[string]any{})
	if entry.Name != "" {
		t.Fatalf("expected empty name, got: %s", entry.Name)
	}
	if entry.Address != "" {
		t.Fatalf("expected empty address, got: %s", entry.Address)
	}
	if entry.Target != "" {
		t.Fatalf("expected empty target, got: %s", entry.Target)
	}
	if entry.Expiry != 0 {
		t.Fatalf("expected zero expiry, got: %d", entry.Expiry)
	}
}

func TestParseNameEntryNilValues(t *testing.T) {
	entry := parseNameEntry(map[string]any{
		"name":    nil,
		"address": nil,
		"target":  nil,
		"expiry":  nil,
	})
	if entry.Name != "" {
		t.Fatalf("expected empty name for nil, got: %s", entry.Name)
	}
	if entry.Address != "" {
		t.Fatalf("expected empty address for nil, got: %s", entry.Address)
	}
}

// ---------------------------------------------------------------------------
// IsNameAvailable edge cases
// ---------------------------------------------------------------------------

func TestIsNameAvailableHTTPError(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithChainBaseURL(srv.URL+"/chain"))
	_, err := client.IsNameAvailable(context.Background(), "test.dili")
	if err == nil {
		t.Fatal("expected error for 500 response")
	}
}

func TestIsNameAvailableMissingField(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`{}`))
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithChainBaseURL(srv.URL+"/chain"))
	available, err := client.IsNameAvailable(context.Background(), "test.dili")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if available {
		t.Fatal("expected false when 'available' field is missing")
	}
}

// ---------------------------------------------------------------------------
// GetNameRecords edge cases
// ---------------------------------------------------------------------------

func TestGetNameRecordsEmpty(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`{}`))
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithChainBaseURL(srv.URL+"/chain"))
	records, err := client.GetNameRecords(context.Background(), "alice.dili")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if len(records) != 0 {
		t.Fatalf("expected 0 records, got %d", len(records))
	}
}

func TestGetNameRecordsNonStringValues(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`{"records":{"avatar":"https://example.com/a.png","num":123}}`))
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithChainBaseURL(srv.URL+"/chain"))
	records, err := client.GetNameRecords(context.Background(), "alice.dili")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	// Only string values should be included
	if len(records) != 1 {
		t.Fatalf("expected 1 record (non-string skipped), got %d", len(records))
	}
	if records["avatar"] != "https://example.com/a.png" {
		t.Fatalf("unexpected avatar: %s", records["avatar"])
	}
}

// ---------------------------------------------------------------------------
// GetNamesByOwner edge cases
// ---------------------------------------------------------------------------

func TestGetNamesByOwnerEmpty(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`{}`))
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithChainBaseURL(srv.URL+"/chain"))
	entries, err := client.GetNamesByOwner(context.Background(), "dili1nobody")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if len(entries) != 0 {
		t.Fatalf("expected 0 entries, got %d", len(entries))
	}
}

func TestGetNamesByOwnerNonMapItems(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`{"names":["not-a-map",{"name":"alice.dili","address":"dili1alice"}]}`))
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithChainBaseURL(srv.URL+"/chain"))
	entries, err := client.GetNamesByOwner(context.Background(), "dili1alice")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	// Only the valid map entry should be parsed
	if len(entries) != 1 {
		t.Fatalf("expected 1 entry, got %d", len(entries))
	}
	if entries[0].Name != "alice.dili" {
		t.Fatalf("unexpected name: %s", entries[0].Name)
	}
}

// ---------------------------------------------------------------------------
// Mutation HTTP error paths
// ---------------------------------------------------------------------------

func TestRegisterNameHTTPError(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithTimeout(time.Second))
	_, err := client.RegisterName(context.Background(), "fail.dili")
	if err == nil {
		t.Fatal("expected error for 500 response")
	}
}

func TestRenewNameHTTPError(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithTimeout(time.Second))
	_, err := client.RenewName(context.Background(), "fail.dili")
	if err == nil {
		t.Fatal("expected error for 500 response")
	}
}
