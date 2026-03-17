package sdk

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

func TestVersions(t *testing.T) {
	if SDKVersion != "0.2.0" {
		t.Fatalf("unexpected SDKVersion: %s", SDKVersion)
	}
	if RPCLineVersion != "0.2.0" {
		t.Fatalf("unexpected RPCLineVersion: %s", RPCLineVersion)
	}
}

func TestNativeCryptoAdapterPlaceholder(t *testing.T) {
	adapter, err := LoadNativeCryptoAdapter()
	if !errors.Is(err, ErrNativeCryptoUnavailable) {
		t.Fatalf("expected native crypto unavailable error, got: %v", err)
	}
	if adapter != nil {
		t.Fatalf("expected nil adapter when bridge is unavailable")
	}
}

func TestGetBalance(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/balance/user1" {
			t.Fatalf("unexpected path: %s", r.URL.Path)
		}
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`{"balance":42}`))
	}))
	defer srv.Close()

	client := NewClient(srv.URL, time.Second)
	result, err := client.GetBalance(context.Background(), "user1")
	if err != nil {
		t.Fatalf("GetBalance error: %v", err)
	}
	if result["balance"].(float64) != 42 {
		t.Fatalf("unexpected balance payload: %#v", result)
	}
}

func TestConfigurableURLsAndContractQuery(t *testing.T) {
	requests := make(chan string, 2)
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		requests <- r.URL.String()
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`{"ok":true}`))
	}))
	defer srv.Close()

	client := NewClientWithConfig(ClientConfig{
		RPCURL:       srv.URL + "/rpc",
		ChainBaseURL: srv.URL + "/chain/",
		IndexerURL:   srv.URL + "/indexer/",
		OracleURL:    srv.URL + "/oracle/",
		JWT:          "secret-token",
		Headers:      map[string]string{"x-network": "devnet"},
		Timeout:      time.Second,
	})

	if client.baseURL != srv.URL+"/chain" {
		t.Fatalf("unexpected baseURL: %s", client.baseURL)
	}
	if client.WSURL() != "ws://"+strings.TrimPrefix(srv.URL+"/chain", "http://") {
		t.Fatalf("unexpected wsURL: %s", client.WSURL())
	}
	auth := client.BuildAuthHeaders(map[string]string{"accept": "application/json"})
	if auth["Authorization"] != "Bearer secret-token" || auth["x-network"] != "devnet" {
		t.Fatalf("unexpected auth headers: %#v", auth)
	}
	if _, err := client.ResolveName(context.Background(), "alice.dili"); err != nil {
		t.Fatalf("ResolveName error: %v", err)
	}
	if _, err := client.QueryContract(context.Background(), "wasm:amm", "get_reserves", map[string]any{}); err != nil {
		t.Fatalf("QueryContract error: %v", err)
	}
	if got := <-requests; got != "/chain/names/resolve/alice.dili" {
		t.Fatalf("unexpected resolve path: %s", got)
	}
	if got := <-requests; got != "/chain/query?contract=wasm%3Aamm&method=get_reserves&args=%7B%7D" {
		t.Fatalf("unexpected query path: %s", got)
	}
}

func TestBuildJSONRPCAndWSRequests(t *testing.T) {
	client := NewClient("http://rpc.example/rpc", time.Second)
	rpc := client.BuildJSONRPCRequest("qsc_head", map[string]any{"full": true}, 1)
	if rpc["method"] != "qsc_head" {
		t.Fatalf("unexpected rpc request: %#v", rpc)
	}
	ws := client.BuildWSRequest("subscribe_heads", map[string]any{"full": true}, 2)
	if ws["id"].(int) != 2 {
		t.Fatalf("unexpected ws request: %#v", ws)
	}
}

func TestWaitForReceipt(t *testing.T) {
	attempts := 0
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		attempts++
		if attempts == 1 {
			http.Error(w, "missing", http.StatusNotFound)
			return
		}
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`{"tx_hash":"abc"}`))
	}))
	defer srv.Close()

	client := NewClient(srv.URL, time.Second)
	result, err := client.WaitForReceipt(context.Background(), "abc", 2, 5*time.Millisecond)
	if err != nil {
		t.Fatalf("WaitForReceipt error: %v", err)
	}
	if result["tx_hash"].(string) != "abc" {
		t.Fatalf("unexpected receipt payload: %#v", result)
	}
}

func TestAccountJSONMappingMatchesNativeCore(t *testing.T) {
	var account Account
	payload := []byte(`{
		"address":"dili1abc",
		"public_key":"pubhex",
		"secret_key":"sechex",
		"account_index":2,
		"wallet_file":{"version":1,"address":"dili1abc"}
	}`)
	if err := json.Unmarshal(payload, &account); err != nil {
		t.Fatalf("json unmarshal failed: %v", err)
	}
	if account.PublicKey != "pubhex" || account.SecretKey != "sechex" {
		t.Fatalf("unexpected account mapping: %#v", account)
	}
	if account.AccountIndex != 2 {
		t.Fatalf("unexpected account index: %#v", account)
	}
	if account.WalletFile["version"].(float64) != 1 {
		t.Fatalf("unexpected wallet file mapping: %#v", account.WalletFile)
	}
}

func TestSignatureJSONMappingMatchesNativeCore(t *testing.T) {
	var signature Signature
	payload := []byte(`{"algorithm":"mldsa65","signature":"deadbeef"}`)
	if err := json.Unmarshal(payload, &signature); err != nil {
		t.Fatalf("json unmarshal failed: %v", err)
	}
	if signature.Algorithm != "mldsa65" || signature.Signature != "deadbeef" {
		t.Fatalf("unexpected signature mapping: %#v", signature)
	}
}

func TestSponsorAndMessagingConnectors(t *testing.T) {
	client := NewClient("http://rpc.example/rpc", time.Second)
	sponsor := NewGasSponsorConnector(client, "wasm:gas_sponsor", "gas_sponsor")
	applied := sponsor.ApplyPaymaster(map[string]any{"contract": "wasm:amm", "method": "swap"})
	if applied["paymaster"] != "gas_sponsor" {
		t.Fatalf("expected paymaster to be applied: %#v", applied)
	}

	messaging := NewMessagingConnector(client, "wasm:messaging", "gas_sponsor")
	outbound := messaging.BuildSendMessageCall("ethereum", map[string]any{"amount": 1})
	if outbound["method"] != "send_message" {
		t.Fatalf("unexpected outbound method: %#v", outbound)
	}
	inbound := messaging.BuildReceiveMessageCall("ethereum", "bridge", map[string]any{"tx": "0xabc"})
	args := inbound["args"].(map[string]any)
	if args["source_chain"] != "ethereum" || args["source_contract"] != "bridge" {
		t.Fatalf("unexpected inbound args: %#v", inbound)
	}
}
