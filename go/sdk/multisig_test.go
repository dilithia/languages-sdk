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
// Multisig mutations (POST /call)
// ---------------------------------------------------------------------------

func TestCreateMultisig(t *testing.T) {
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
	result, err := client.CreateMultisig(context.Background(), "wallet1", []string{"dili1a", "dili1b", "dili1c"}, 2)
	if err != nil {
		t.Fatalf("CreateMultisig error: %v", err)
	}
	if !result.Accepted {
		t.Fatal("expected accepted")
	}
	if result.TxHash != "0xtest" {
		t.Fatalf("unexpected tx_hash: %s", result.TxHash)
	}
	if gotBody["contract"] != "multisig" {
		t.Fatalf("unexpected contract: %v", gotBody["contract"])
	}
	if gotBody["method"] != "create" {
		t.Fatalf("unexpected method: %v", gotBody["method"])
	}
	args, _ := gotBody["args"].(map[string]any)
	if args["wallet_id"] != "wallet1" {
		t.Fatalf("unexpected wallet_id: %v", args["wallet_id"])
	}
	signers, ok := args["signers"].([]any)
	if !ok || len(signers) != 3 {
		t.Fatalf("unexpected signers: %v", args["signers"])
	}
	// JSON numbers are float64.
	if threshold, ok := args["threshold"].(float64); !ok || threshold != 2 {
		t.Fatalf("unexpected threshold: %v", args["threshold"])
	}
}

func TestProposeTx(t *testing.T) {
	var gotBody map[string]any
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		body, _ := io.ReadAll(r.Body)
		_ = json.Unmarshal(body, &gotBody)
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`{"accepted":true,"tx_hash":"0xtest"}`))
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithTimeout(time.Second))
	result, err := client.ProposeTx(context.Background(), "wallet1", "token", "transfer", map[string]any{
		"to":     "dili1bob",
		"amount": 100,
	})
	if err != nil {
		t.Fatalf("ProposeTx error: %v", err)
	}
	if !result.Accepted {
		t.Fatal("expected accepted")
	}
	if gotBody["contract"] != "multisig" {
		t.Fatalf("unexpected contract: %v", gotBody["contract"])
	}
	if gotBody["method"] != "propose_tx" {
		t.Fatalf("unexpected method: %v", gotBody["method"])
	}
	args, _ := gotBody["args"].(map[string]any)
	if args["wallet_id"] != "wallet1" {
		t.Fatalf("unexpected wallet_id: %v", args["wallet_id"])
	}
	if args["contract"] != "token" {
		t.Fatalf("unexpected inner contract: %v", args["contract"])
	}
	if args["method"] != "transfer" {
		t.Fatalf("unexpected inner method: %v", args["method"])
	}
}

func TestApproveMultisigTx(t *testing.T) {
	var gotBody map[string]any
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		body, _ := io.ReadAll(r.Body)
		_ = json.Unmarshal(body, &gotBody)
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`{"accepted":true,"tx_hash":"0xtest"}`))
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithTimeout(time.Second))
	result, err := client.ApproveMultisigTx(context.Background(), "wallet1", "tx1")
	if err != nil {
		t.Fatalf("ApproveMultisigTx error: %v", err)
	}
	if !result.Accepted {
		t.Fatal("expected accepted")
	}
	if gotBody["method"] != "approve" {
		t.Fatalf("unexpected method: %v", gotBody["method"])
	}
	args, _ := gotBody["args"].(map[string]any)
	if args["wallet_id"] != "wallet1" || args["tx_id"] != "tx1" {
		t.Fatalf("unexpected args: %v", args)
	}
}

func TestExecuteMultisigTx(t *testing.T) {
	var gotBody map[string]any
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		body, _ := io.ReadAll(r.Body)
		_ = json.Unmarshal(body, &gotBody)
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`{"accepted":true,"tx_hash":"0xtest"}`))
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithTimeout(time.Second))
	result, err := client.ExecuteMultisigTx(context.Background(), "wallet1", "tx1")
	if err != nil {
		t.Fatalf("ExecuteMultisigTx error: %v", err)
	}
	if !result.Accepted {
		t.Fatal("expected accepted")
	}
	if gotBody["method"] != "execute" {
		t.Fatalf("unexpected method: %v", gotBody["method"])
	}
	args, _ := gotBody["args"].(map[string]any)
	if args["wallet_id"] != "wallet1" || args["tx_id"] != "tx1" {
		t.Fatalf("unexpected args: %v", args)
	}
}

func TestRevokeMultisigApproval(t *testing.T) {
	var gotBody map[string]any
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		body, _ := io.ReadAll(r.Body)
		_ = json.Unmarshal(body, &gotBody)
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`{"accepted":true,"tx_hash":"0xtest"}`))
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithTimeout(time.Second))
	result, err := client.RevokeMultisigApproval(context.Background(), "wallet1", "tx1")
	if err != nil {
		t.Fatalf("RevokeMultisigApproval error: %v", err)
	}
	if !result.Accepted {
		t.Fatal("expected accepted")
	}
	if gotBody["method"] != "revoke" {
		t.Fatalf("unexpected method: %v", gotBody["method"])
	}
	args, _ := gotBody["args"].(map[string]any)
	if args["wallet_id"] != "wallet1" || args["tx_id"] != "tx1" {
		t.Fatalf("unexpected args: %v", args)
	}
}

func TestAddMultisigSigner(t *testing.T) {
	var gotBody map[string]any
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		body, _ := io.ReadAll(r.Body)
		_ = json.Unmarshal(body, &gotBody)
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`{"accepted":true,"tx_hash":"0xtest"}`))
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithTimeout(time.Second))
	result, err := client.AddMultisigSigner(context.Background(), "wallet1", "dili1new")
	if err != nil {
		t.Fatalf("AddMultisigSigner error: %v", err)
	}
	if !result.Accepted {
		t.Fatal("expected accepted")
	}
	if gotBody["method"] != "add_signer" {
		t.Fatalf("unexpected method: %v", gotBody["method"])
	}
	args, _ := gotBody["args"].(map[string]any)
	if args["wallet_id"] != "wallet1" || args["signer"] != "dili1new" {
		t.Fatalf("unexpected args: %v", args)
	}
}

func TestRemoveMultisigSigner(t *testing.T) {
	var gotBody map[string]any
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		body, _ := io.ReadAll(r.Body)
		_ = json.Unmarshal(body, &gotBody)
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`{"accepted":true,"tx_hash":"0xtest"}`))
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithTimeout(time.Second))
	result, err := client.RemoveMultisigSigner(context.Background(), "wallet1", "dili1old")
	if err != nil {
		t.Fatalf("RemoveMultisigSigner error: %v", err)
	}
	if !result.Accepted {
		t.Fatal("expected accepted")
	}
	if gotBody["method"] != "remove_signer" {
		t.Fatalf("unexpected method: %v", gotBody["method"])
	}
	args, _ := gotBody["args"].(map[string]any)
	if args["wallet_id"] != "wallet1" || args["signer"] != "dili1old" {
		t.Fatalf("unexpected args: %v", args)
	}
}

// ---------------------------------------------------------------------------
// Multisig queries (via QueryContract -> GET /query)
// ---------------------------------------------------------------------------

func TestGetMultisigWallet(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`{"wallet_id":"wallet1","signers":["dili1a","dili1b","dili1c"],"threshold":2}`))
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithChainBaseURL(srv.URL+"/chain"))
	wallet, err := client.GetMultisigWallet(context.Background(), "wallet1")
	if err != nil {
		t.Fatalf("GetMultisigWallet error: %v", err)
	}
	if wallet == nil {
		t.Fatal("expected non-nil wallet")
	}
	if wallet.WalletID != "wallet1" {
		t.Fatalf("unexpected wallet_id: %s", wallet.WalletID)
	}
	if len(wallet.Signers) != 3 {
		t.Fatalf("expected 3 signers, got %d", len(wallet.Signers))
	}
	if wallet.Signers[0] != "dili1a" {
		t.Fatalf("unexpected first signer: %s", wallet.Signers[0])
	}
	if wallet.Threshold != 2 {
		t.Fatalf("unexpected threshold: %d", wallet.Threshold)
	}
}

func TestGetMultisigTx(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`{"tx_id":"tx1","contract":"token","method":"transfer","args":{"to":"dili1bob","amount":100},"approvals":["dili1a"]}`))
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithChainBaseURL(srv.URL+"/chain"))
	tx, err := client.GetMultisigTx(context.Background(), "wallet1", "tx1")
	if err != nil {
		t.Fatalf("GetMultisigTx error: %v", err)
	}
	if tx == nil {
		t.Fatal("expected non-nil tx")
	}
	if tx.TxID != "tx1" {
		t.Fatalf("unexpected tx_id: %s", tx.TxID)
	}
	if tx.Contract != "token" {
		t.Fatalf("unexpected contract: %s", tx.Contract)
	}
	if tx.Method != "transfer" {
		t.Fatalf("unexpected method: %s", tx.Method)
	}
	if len(tx.Approvals) != 1 || tx.Approvals[0] != "dili1a" {
		t.Fatalf("unexpected approvals: %v", tx.Approvals)
	}
}

func TestListMultisigPendingTxs(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`{"pending_txs":[{"tx_id":"tx1","contract":"token","method":"transfer","args":{},"approvals":["dili1a"]},{"tx_id":"tx2","contract":"staking","method":"delegate","args":{},"approvals":[]}]}`))
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithChainBaseURL(srv.URL+"/chain"))
	txs, err := client.ListMultisigPendingTxs(context.Background(), "wallet1")
	if err != nil {
		t.Fatalf("ListMultisigPendingTxs error: %v", err)
	}
	if len(txs) != 2 {
		t.Fatalf("expected 2 pending txs, got %d", len(txs))
	}
	if txs[0].TxID != "tx1" {
		t.Fatalf("unexpected first tx_id: %s", txs[0].TxID)
	}
	if txs[1].TxID != "tx2" {
		t.Fatalf("unexpected second tx_id: %s", txs[1].TxID)
	}
	if txs[1].Contract != "staking" {
		t.Fatalf("unexpected second contract: %s", txs[1].Contract)
	}
}

// ---------------------------------------------------------------------------
// GetMultisigWallet edge cases
// ---------------------------------------------------------------------------

func TestGetMultisigWalletHTTPError(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithChainBaseURL(srv.URL+"/chain"))
	_, err := client.GetMultisigWallet(context.Background(), "wallet1")
	if err == nil {
		t.Fatal("expected error for 500 response")
	}
}

func TestGetMultisigWalletUnexpectedType(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`"not an object"`))
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithChainBaseURL(srv.URL+"/chain"))
	_, err := client.GetMultisigWallet(context.Background(), "wallet1")
	if err == nil {
		t.Fatal("expected error for unexpected result type")
	}
}

func TestGetMultisigWalletEmptySigners(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`{"wallet_id":"wallet1","signers":[],"threshold":0}`))
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithChainBaseURL(srv.URL+"/chain"))
	wallet, err := client.GetMultisigWallet(context.Background(), "wallet1")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if wallet.WalletID != "wallet1" {
		t.Fatalf("unexpected wallet_id: %s", wallet.WalletID)
	}
	if len(wallet.Signers) != 0 {
		t.Fatalf("expected 0 signers, got %d", len(wallet.Signers))
	}
	if wallet.Threshold != 0 {
		t.Fatalf("expected 0 threshold, got %d", wallet.Threshold)
	}
}

// ---------------------------------------------------------------------------
// GetMultisigTx edge cases
// ---------------------------------------------------------------------------

func TestGetMultisigTxHTTPError(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithChainBaseURL(srv.URL+"/chain"))
	_, err := client.GetMultisigTx(context.Background(), "wallet1", "tx1")
	if err == nil {
		t.Fatal("expected error for 500 response")
	}
}

func TestGetMultisigTxUnexpectedType(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`"not an object"`))
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithChainBaseURL(srv.URL+"/chain"))
	_, err := client.GetMultisigTx(context.Background(), "wallet1", "tx1")
	if err == nil {
		t.Fatal("expected error for unexpected result type")
	}
}

func TestGetMultisigTxEmptyApprovals(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`{"tx_id":"tx1","contract":"token","method":"transfer","args":{},"approvals":[]}`))
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithChainBaseURL(srv.URL+"/chain"))
	tx, err := client.GetMultisigTx(context.Background(), "wallet1", "tx1")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if tx.TxID != "tx1" {
		t.Fatalf("unexpected tx_id: %s", tx.TxID)
	}
	if len(tx.Approvals) != 0 {
		t.Fatalf("expected 0 approvals, got %d", len(tx.Approvals))
	}
}

// ---------------------------------------------------------------------------
// ListMultisigPendingTxs edge cases
// ---------------------------------------------------------------------------

func TestListMultisigPendingTxsHTTPError(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithChainBaseURL(srv.URL+"/chain"))
	_, err := client.ListMultisigPendingTxs(context.Background(), "wallet1")
	if err == nil {
		t.Fatal("expected error for 500 response")
	}
}

func TestListMultisigPendingTxsUnexpectedType(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`"not an object"`))
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithChainBaseURL(srv.URL+"/chain"))
	_, err := client.ListMultisigPendingTxs(context.Background(), "wallet1")
	if err == nil {
		t.Fatal("expected error for unexpected result type")
	}
}

func TestListMultisigPendingTxsEmpty(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`{}`))
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithChainBaseURL(srv.URL+"/chain"))
	txs, err := client.ListMultisigPendingTxs(context.Background(), "wallet1")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if txs != nil {
		t.Fatalf("expected nil for missing pending_txs key, got %d items", len(txs))
	}
}

func TestListMultisigPendingTxsEmptyArray(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`{"pending_txs":[]}`))
	}))
	defer srv.Close()

	client := NewClient(srv.URL+"/rpc", WithChainBaseURL(srv.URL+"/chain"))
	txs, err := client.ListMultisigPendingTxs(context.Background(), "wallet1")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if len(txs) != 0 {
		t.Fatalf("expected 0 txs, got %d", len(txs))
	}
}
