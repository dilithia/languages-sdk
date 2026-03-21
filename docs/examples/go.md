# Go Examples

Complete, self-contained Go programs demonstrating common tasks with the Dilithia SDK. Each scenario is a standalone `package main` program ready to compile and run.

---

## Prerequisites

Install the SDK module:

```bash
go get github.com/dilithia/languages-sdk/go@v0.3.0
```

Set the environment variable pointing to the native Dilithium shared library:

```bash
export DILITHIUM_NATIVE_CORE_LIB=/path/to/libdilithium_core.so
```

---

## Scenario 1: Balance Monitor Bot

A bot that recovers its wallet from a saved mnemonic, checks its balance, and if the balance exceeds a threshold, sends tokens to a destination address via a contract call. Covers: client setup, wallet recovery, balance query, contract call construction, signing, submission, and receipt polling.

```go
package main

import (
	"context"
	"errors"
	"fmt"
	"log"
	"os"
	"time"

	"github.com/dilithia/languages-sdk/go/sdk"
)

const (
	rpcURL        = "https://rpc.dilithia.network/rpc"
	tokenContract = "dil1_token_main"
	destination   = "dil1_recipient_address"
	threshold     = 500_000
	sendAmount    = 100_000
)

func main() {
	ctx := context.Background()

	// 1. Initialize client with functional options and crypto adapter
	client := sdk.NewClient(rpcURL, sdk.WithTimeout(15*time.Second))
	crypto, err := sdk.LoadNativeCryptoAdapter()
	if err != nil {
		log.Fatal("crypto adapter unavailable: ", err)
	}

	// 2. Recover wallet from saved mnemonic
	mnemonic := os.Getenv("BOT_MNEMONIC")
	if mnemonic == "" {
		log.Fatal("Set BOT_MNEMONIC env var")
	}
	account, err := crypto.RecoverHDWallet(ctx, mnemonic)
	if err != nil {
		log.Fatal("wallet recovery failed: ", err)
	}
	fmt.Println("Bot address:", account.Address)

	// 3. Check current balance — returns *sdk.Balance with typed fields
	balance, err := client.GetBalance(ctx, sdk.Address(account.Address))
	if err != nil {
		var httpErr *sdk.HttpError
		var rpcErr *sdk.RpcError
		if errors.As(err, &httpErr) {
			log.Fatalf("HTTP error %d: %s", httpErr.StatusCode, httpErr.Message)
		} else if errors.As(err, &rpcErr) {
			log.Fatalf("RPC error %d: %s", rpcErr.Code, rpcErr.Message)
		}
		log.Fatal("balance query failed: ", err)
	}
	fmt.Printf("Current balance: %s (raw: %s)\n", balance.Value, balance.RawValue)

	if balance.Value.Less(sdk.TokenAmount(threshold)) {
		fmt.Printf("Balance below threshold %d. Nothing to do.\n", threshold)
		return
	}

	// 4. Build a contract call to transfer tokens
	call := client.BuildContractCall(tokenContract, "transfer", map[string]any{
		"to":     string(sdk.Address(destination)),
		"amount": sendAmount,
	}, "")

	// 5. Simulate first to verify it would succeed
	simResult, err := client.Simulate(ctx, call)
	if err != nil {
		log.Fatal("simulation failed: ", err)
	}
	fmt.Println("Simulation result:", simResult)

	// 6. Sign the message and attach the signature to the call
	sig, err := crypto.SignMessage(ctx, account.SecretKey, fmt.Sprintf("%v", call))
	if err != nil {
		log.Fatal("signing failed: ", err)
	}
	call["algorithm"] = sig.Algorithm
	call["signature"] = sig.Signature

	// 7. Submit — returns *sdk.SubmitResult with .Accepted and .TxHash
	submitted, err := client.SendCall(ctx, call)
	if err != nil {
		var dilErr *sdk.DilithiaError
		if errors.As(err, &dilErr) {
			log.Fatalf("Dilithia error: %s", dilErr.Message)
		}
		log.Fatal("submit failed: ", err)
	}
	fmt.Printf("Transaction submitted: %s (accepted: %v)\n", submitted.TxHash, submitted.Accepted)

	// 8. Poll for receipt — returns *sdk.Receipt with .TxHash, .BlockHeight, .Status
	receipt, err := client.WaitForReceipt(ctx, submitted.TxHash, 20, 2*time.Second)
	if err != nil {
		log.Fatal("receipt polling failed: ", err)
	}
	fmt.Printf("Confirmed at block %d, status: %s, tx: %s\n",
		receipt.BlockHeight, receipt.Status, receipt.TxHash)
}
```

---

## Scenario 2: Multi-Account Treasury Manager

A service that derives accounts 0 through 4 from a single mnemonic, checks each balance, and consolidates all funds into account 0. Covers: HD derivation loop, multiple balance queries, and batch transaction construction.

```go
package main

import (
	"context"
	"errors"
	"fmt"
	"log"
	"os"
	"time"

	"github.com/dilithia/languages-sdk/go/sdk"
)

const (
	rpcURL        = "https://rpc.dilithia.network/rpc"
	tokenContract = "dil1_token_main"
	numAccounts   = 5
)

func main() {
	ctx := context.Background()

	// Initialize client with functional options
	client := sdk.NewClient(rpcURL, sdk.WithTimeout(15*time.Second))
	crypto, err := sdk.LoadNativeCryptoAdapter()
	if err != nil {
		log.Fatal("crypto adapter unavailable: ", err)
	}

	mnemonic := os.Getenv("TREASURY_MNEMONIC")
	if mnemonic == "" {
		log.Fatal("Set TREASURY_MNEMONIC env var")
	}

	// 1. Derive all accounts from the same mnemonic
	accounts := make([]sdk.Account, numAccounts)
	for i := 0; i < numAccounts; i++ {
		acct, err := crypto.RecoverHDWalletAccount(ctx, mnemonic, i)
		if err != nil {
			log.Fatalf("failed to derive account %d: %v", i, err)
		}
		accounts[i] = acct
	}
	treasuryAddr := sdk.Address(accounts[0].Address)
	fmt.Println("Treasury address (account 0):", treasuryAddr)

	// 2. Check balances for every account — GetBalance returns *sdk.Balance
	balances := make([]*sdk.Balance, numAccounts)
	for i, acct := range accounts {
		bal, err := client.GetBalance(ctx, sdk.Address(acct.Address))
		if err != nil {
			log.Fatalf("balance query failed for account %d: %v", i, err)
		}
		balances[i] = bal
		fmt.Printf("  Account %d: %s -> %s\n", acct.AccountIndex, bal.Address, bal.Value)
	}

	// 3. Consolidate: transfer from accounts 1-4 to account 0
	for i := 1; i < numAccounts; i++ {
		if balances[i].Value.IsZero() {
			fmt.Printf("  Account %d: zero balance, skipping.\n", i)
			continue
		}

		call := client.BuildContractCall(tokenContract, "transfer", map[string]any{
			"to":     string(treasuryAddr),
			"amount": balances[i].RawValue,
		}, "")

		fmt.Printf("  Consolidating %s from account %d...\n", balances[i].Value, i)

		// Sign with the source account's secret key
		sig, err := crypto.SignMessage(ctx, accounts[i].SecretKey, fmt.Sprintf("%v", call))
		if err != nil {
			log.Fatalf("signing failed for account %d: %v", i, err)
		}
		call["algorithm"] = sig.Algorithm
		call["signature"] = sig.Signature

		// SendCall returns *sdk.SubmitResult
		submitted, err := client.SendCall(ctx, call)
		if err != nil {
			var rpcErr *sdk.RpcError
			if errors.As(err, &rpcErr) {
				log.Fatalf("RPC error for account %d: code=%d msg=%s", i, rpcErr.Code, rpcErr.Message)
			}
			log.Fatalf("submit failed for account %d: %v", i, err)
		}

		// WaitForReceipt returns *sdk.Receipt
		receipt, err := client.WaitForReceipt(ctx, submitted.TxHash, 12, time.Second)
		if err != nil {
			log.Fatalf("receipt polling failed for account %d: %v", i, err)
		}
		fmt.Printf("  Done. Block %d, status: %s\n", receipt.BlockHeight, receipt.Status)
	}

	// 4. Final balance check on treasury
	finalBalance, err := client.GetBalance(ctx, treasuryAddr)
	if err != nil {
		log.Fatal("final balance query failed: ", err)
	}
	fmt.Println("\nTreasury final balance:", finalBalance.Value)
}
```

---

## Scenario 3: Signature Verification Service

An API endpoint that receives a signed message and verifies the signature against the claimed public key and address. Covers: address validation, public key validation, signature verification, and structured error handling.

```go
package main

import (
	"context"
	"errors"
	"fmt"
	"log"

	"github.com/dilithia/languages-sdk/go/sdk"
)

type verifyRequest struct {
	PublicKey string
	Address   sdk.Address
	Message   string
	Signature string
}

type verifyResult struct {
	Valid bool
	Error string
}

func verifySignedMessage(crypto sdk.CryptoAdapter, req verifyRequest) verifyResult {
	ctx := context.Background()

	// 1. Validate the public key format
	if err := crypto.ValidatePublicKey(ctx, req.PublicKey); err != nil {
		return verifyResult{Valid: false, Error: "Invalid public key format"}
	}

	// 2. Validate the signature format
	if err := crypto.ValidateSignature(ctx, req.Signature); err != nil {
		return verifyResult{Valid: false, Error: "Invalid signature format"}
	}

	// 3. Validate the claimed address format using sdk.Address
	if _, err := crypto.ValidateAddress(ctx, string(req.Address)); err != nil {
		return verifyResult{Valid: false, Error: "Invalid address format"}
	}

	// 4. Verify that the public key maps to the claimed address
	derivedAddress, err := crypto.AddressFromPublicKey(ctx, req.PublicKey)
	if err != nil {
		return verifyResult{Valid: false, Error: "Failed to derive address from public key"}
	}
	if sdk.Address(derivedAddress) != req.Address {
		return verifyResult{Valid: false, Error: "Address does not match public key"}
	}

	// 5. Verify the cryptographic signature
	valid, err := crypto.VerifyMessage(ctx, req.PublicKey, req.Message, req.Signature)
	if err != nil {
		var dilErr *sdk.DilithiaError
		if errors.As(err, &dilErr) {
			return verifyResult{Valid: false, Error: "Dilithia error: " + dilErr.Message}
		}
		return verifyResult{Valid: false, Error: "Signature verification error: " + err.Error()}
	}
	if !valid {
		return verifyResult{Valid: false, Error: "Signature verification failed"}
	}

	return verifyResult{Valid: true}
}

func main() {
	crypto, err := sdk.LoadNativeCryptoAdapter()
	if err != nil {
		log.Fatal("crypto adapter unavailable: ", err)
	}

	result := verifySignedMessage(crypto, verifyRequest{
		PublicKey: "abcd1234...",
		Address:   sdk.Address("dil1_abc123"),
		Message:   "Login nonce: 98765",
		Signature: "deadbeef...",
	})

	if result.Valid {
		fmt.Println("Signature is valid. User authenticated.")
	} else {
		fmt.Println("Verification failed:", result.Error)
	}
}
```

---

## Scenario 4: Wallet Backup and Recovery

Create a new wallet, save the encrypted wallet file to disk, then recover it later from the saved file. Covers the full wallet lifecycle: generate mnemonic, create encrypted wallet file, serialize, write to disk, read from disk, deserialize, and recover.

```go
package main

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"os"

	"github.com/dilithia/languages-sdk/go/sdk"
)

const (
	walletPath = "./my-wallet.json"
	password   = "my-secure-passphrase"
)

func main() {
	ctx := context.Background()

	crypto, err := sdk.LoadNativeCryptoAdapter()
	if err != nil {
		log.Fatal("crypto adapter unavailable: ", err)
	}

	if _, err := os.Stat(walletPath); os.IsNotExist(err) {
		// ---- CREATE NEW WALLET ----
		fmt.Println("No wallet found. Creating a new one...")

		// 1. Generate a fresh mnemonic
		mnemonic, err := crypto.GenerateMnemonic(ctx)
		if err != nil {
			log.Fatal("mnemonic generation failed: ", err)
		}
		fmt.Println("SAVE THIS MNEMONIC SECURELY:")
		fmt.Println(mnemonic)
		fmt.Println()

		// 2. Create an encrypted wallet file from the mnemonic
		account, err := crypto.CreateHDWalletFileFromMnemonic(ctx, mnemonic, password)
		if err != nil {
			log.Fatal("wallet creation failed: ", err)
		}
		fmt.Println("Address:", sdk.Address(account.Address))
		fmt.Println("Public key:", account.PublicKey)

		// 3. Serialize the wallet file to JSON and save to disk
		if account.WalletFile == nil {
			log.Fatal("wallet file not generated")
		}
		data, err := json.MarshalIndent(account.WalletFile, "", "  ")
		if err != nil {
			log.Fatal("JSON marshal failed: ", err)
		}
		if err := os.WriteFile(walletPath, data, 0600); err != nil {
			log.Fatal("file write failed: ", err)
		}
		fmt.Println("Wallet saved to", walletPath)
	} else {
		// ---- RECOVER EXISTING WALLET ----
		fmt.Println("Wallet file found. Recovering...")

		// 4. Read the wallet file from disk
		data, err := os.ReadFile(walletPath)
		if err != nil {
			log.Fatal("file read failed: ", err)
		}
		var walletFile sdk.WalletFile
		if err := json.Unmarshal(data, &walletFile); err != nil {
			log.Fatal("JSON unmarshal failed: ", err)
		}

		// 5. Recover using mnemonic + password
		mnemonic := os.Getenv("WALLET_MNEMONIC")
		if mnemonic == "" {
			log.Fatal("Set WALLET_MNEMONIC env var to recover")
		}

		account, err := crypto.RecoverWalletFile(ctx, walletFile, mnemonic, password)
		if err != nil {
			log.Fatal("wallet recovery failed: ", err)
		}
		fmt.Println("Recovered address:", sdk.Address(account.Address))
		fmt.Println("Public key:", account.PublicKey)
		fmt.Println("Wallet recovered successfully. Ready to sign transactions.")
	}
}
```

---

## Scenario 5: Gas-Sponsored Meta-Transaction

Submit a transaction where the gas fee is paid by a sponsor contract instead of the sender. Useful for onboarding new users who have no tokens to pay for gas. Covers: gas sponsor connector setup, checking remaining quota, building a paymaster-attached call, signing, and submission.

```go
package main

import (
	"context"
	"errors"
	"fmt"
	"log"
	"os"
	"time"

	"github.com/dilithia/languages-sdk/go/sdk"
)

const (
	rpcURL          = "https://rpc.dilithia.network/rpc"
	sponsorContract = "dil1_gas_sponsor_v1"
	paymasterAddr   = "dil1_paymaster_addr"
	targetContract  = "dil1_nft_mint"
)

func main() {
	ctx := context.Background()

	// Initialize client with functional options
	client := sdk.NewClient(rpcURL, sdk.WithTimeout(15*time.Second))
	crypto, err := sdk.LoadNativeCryptoAdapter()
	if err != nil {
		log.Fatal("crypto adapter unavailable: ", err)
	}

	// 1. Recover the user's wallet (new user with zero balance)
	mnemonic := os.Getenv("USER_MNEMONIC")
	if mnemonic == "" {
		log.Fatal("Set USER_MNEMONIC env var")
	}
	account, err := crypto.RecoverHDWallet(ctx, mnemonic)
	if err != nil {
		log.Fatal("wallet recovery failed: ", err)
	}
	fmt.Println("User address:", sdk.Address(account.Address))

	// 2. Set up the gas sponsor connector
	sponsor := sdk.NewGasSponsorConnector(client, sponsorContract, paymasterAddr)

	// 3. Check if the sponsor will accept this call — QueryContract returns *sdk.QueryResult
	acceptResult, err := client.QueryContract(ctx, sponsorContract, "accept", map[string]any{
		"user":     string(sdk.Address(account.Address)),
		"contract": targetContract,
		"method":   "mint",
	})
	if err != nil {
		log.Fatal("accept query failed: ", err)
	}
	fmt.Println("Sponsor accepts:", acceptResult)

	// 4. Check remaining gas quota for this user
	quotaResult, err := client.QueryContract(ctx, sponsorContract, "remaining_quota", map[string]any{
		"user": string(sdk.Address(account.Address)),
	})
	if err != nil {
		log.Fatal("quota query failed: ", err)
	}
	fmt.Println("Remaining quota:", quotaResult)

	// 5. Get gas estimate — returns *sdk.GasEstimate
	gasEstimate, err := client.GetGasEstimate(ctx)
	if err != nil {
		log.Fatal("gas estimate failed: ", err)
	}
	fmt.Println("Current gas estimate:", gasEstimate)

	// 6. Build the actual contract call
	call := client.BuildContractCall(targetContract, "mint", map[string]any{
		"token_id": "nft_001",
		"metadata": "ipfs://QmSomeHash",
	}, "")

	// 7. Apply the paymaster (sponsor pays the gas)
	sponsoredCall := sponsor.ApplyPaymaster(call)
	fmt.Println("Sponsored call:", sponsoredCall)

	// 8. Sign and submit the sponsored call
	sig, err := crypto.SignMessage(ctx, account.SecretKey, fmt.Sprintf("%v", sponsoredCall))
	if err != nil {
		log.Fatal("signing failed: ", err)
	}
	sponsoredCall["algorithm"] = sig.Algorithm
	sponsoredCall["signature"] = sig.Signature

	// SendCall returns *sdk.SubmitResult
	submitted, err := client.SendCall(ctx, sponsoredCall)
	if err != nil {
		var httpErr *sdk.HttpError
		var rpcErr *sdk.RpcError
		if errors.As(err, &httpErr) {
			log.Fatalf("HTTP error %d: %s", httpErr.StatusCode, httpErr.Message)
		} else if errors.As(err, &rpcErr) {
			log.Fatalf("RPC error %d: %s", rpcErr.Code, rpcErr.Message)
		}
		log.Fatal("submit failed: ", err)
	}
	fmt.Printf("Sponsored tx submitted: %s (accepted: %v)\n", submitted.TxHash, submitted.Accepted)

	// 9. Wait for confirmation — returns *sdk.Receipt
	receipt, err := client.WaitForReceipt(ctx, submitted.TxHash, 12, time.Second)
	if err != nil {
		log.Fatal("receipt polling failed: ", err)
	}
	fmt.Printf("Confirmed at block %d, status: %s\n", receipt.BlockHeight, receipt.Status)
}
```

---

## Scenario 6: Cross-Chain Message Sender

Send a message to another Dilithia chain via the messaging connector. Useful for bridging data or triggering actions on a remote chain. Covers: messaging connector setup, building outbound messages, signing, and submission.

```go
package main

import (
	"context"
	"errors"
	"fmt"
	"log"
	"os"
	"time"

	"github.com/dilithia/languages-sdk/go/sdk"
)

const (
	rpcURL            = "https://rpc.dilithia.network/rpc"
	messagingContract = "dil1_bridge_v1"
	paymaster         = "dil1_bridge_paymaster"
	destChain         = "dilithia-testnet-2"
)

func main() {
	ctx := context.Background()

	// Initialize client with functional options
	client := sdk.NewClient(rpcURL, sdk.WithTimeout(15*time.Second))
	crypto, err := sdk.LoadNativeCryptoAdapter()
	if err != nil {
		log.Fatal("crypto adapter unavailable: ", err)
	}

	mnemonic := os.Getenv("BRIDGE_MNEMONIC")
	if mnemonic == "" {
		log.Fatal("Set BRIDGE_MNEMONIC env var")
	}
	account, err := crypto.RecoverHDWallet(ctx, mnemonic)
	if err != nil {
		log.Fatal("wallet recovery failed: ", err)
	}
	fmt.Println("Sender address:", sdk.Address(account.Address))

	// 1. Set up the messaging connector
	messaging := sdk.NewMessagingConnector(client, messagingContract, paymaster)

	// 2. Resolve a name before sending — ResolveName returns *sdk.NameRecord
	nameRecord, err := client.ResolveName(ctx, "alice.dili")
	if err != nil {
		var rpcErr *sdk.RpcError
		if errors.As(err, &rpcErr) {
			fmt.Printf("Name resolution RPC error: code=%d msg=%s\n", rpcErr.Code, rpcErr.Message)
		} else {
			fmt.Println("Name resolution failed (using fallback address):", err)
		}
	} else {
		fmt.Printf("Resolved alice.dili -> %s\n", nameRecord)
	}

	// 3. Get network info — returns *sdk.NetworkInfo
	netInfo, err := client.GetNetworkInfo(ctx)
	if err != nil {
		log.Fatal("network info failed: ", err)
	}
	fmt.Println("Network info:", netInfo)

	// 4. Build the cross-chain message payload
	payload := map[string]any{
		"action":    "lock_tokens",
		"sender":    string(sdk.Address(account.Address)),
		"amount":    50_000,
		"recipient": string(sdk.Address("dil1_remote_recipient")),
		"timestamp": time.Now().UnixMilli(),
	}

	// 5. Build the send-message call (paymaster attached automatically)
	messageCall := messaging.BuildSendMessageCall(destChain, payload)
	fmt.Println("Message call:", messageCall)

	// 6. Simulate the message send
	simResult, err := client.Simulate(ctx, messageCall)
	if err != nil {
		log.Fatal("simulation failed: ", err)
	}
	fmt.Println("Simulation:", simResult)

	// 7. Sign and send the message
	sig, err := crypto.SignMessage(ctx, account.SecretKey, fmt.Sprintf("%v", messageCall))
	if err != nil {
		log.Fatal("signing failed: ", err)
	}
	messageCall["algorithm"] = sig.Algorithm
	messageCall["signature"] = sig.Signature

	// SendCall returns *sdk.SubmitResult
	submitted, err := client.SendCall(ctx, messageCall)
	if err != nil {
		var dilErr *sdk.DilithiaError
		if errors.As(err, &dilErr) {
			log.Fatalf("Dilithia error: %s", dilErr.Message)
		}
		log.Fatal("submit failed: ", err)
	}
	fmt.Printf("Message tx submitted: %s (accepted: %v)\n", submitted.TxHash, submitted.Accepted)

	// 8. Wait for confirmation — returns *sdk.Receipt
	receipt, err := client.WaitForReceipt(ctx, submitted.TxHash, 12, time.Second)
	if err != nil {
		log.Fatal("receipt polling failed: ", err)
	}
	fmt.Printf("Message confirmed at block %d, status: %s\n", receipt.BlockHeight, receipt.Status)

	// 9. Optionally build a receive-message call for the remote side
	receiveCall := messaging.BuildReceiveMessageCall(
		"dilithia-mainnet", // source chain
		messagingContract,  // source contract
		payload,
	)
	fmt.Println("Receive call (for remote chain):", receiveCall)
}
```

---

## Scenario 7: Contract Deployment

Deploy a WASM smart contract to the Dilithia chain. Reads the WASM binary, hashes the bytecode, builds and signs a canonical deploy payload, assembles the full `DeployPayload`, sends the deploy request, and waits for confirmation.

```go
package main

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log"
	"os"
	"time"

	"github.com/dilithia/languages-sdk/go/sdk"
)

const (
	rpcURL       = "https://rpc.dilithia.network/rpc"
	contractName = "my_contract"
	wasmPath     = "./my_contract.wasm"
	chainID      = "dilithia-mainnet"
)

func main() {
	ctx := context.Background()

	// 1. Initialize client with functional options and crypto adapter
	client := sdk.NewClient(rpcURL, sdk.WithTimeout(30*time.Second))
	crypto, err := sdk.LoadNativeCryptoAdapter()
	if err != nil {
		log.Fatal("crypto adapter unavailable: ", err)
	}

	// 2. Recover wallet from mnemonic
	mnemonic := os.Getenv("DEPLOYER_MNEMONIC")
	if mnemonic == "" {
		log.Fatal("Set DEPLOYER_MNEMONIC env var")
	}
	account, err := crypto.RecoverHDWallet(ctx, mnemonic)
	if err != nil {
		log.Fatal("wallet recovery failed: ", err)
	}
	fmt.Println("Deployer address:", sdk.Address(account.Address))

	// 3. Read the WASM file as hex
	bytecodeHex, err := sdk.ReadWasmFileHex(wasmPath)
	if err != nil {
		log.Fatal("failed to read wasm file: ", err)
	}
	fmt.Printf("Bytecode size: %d bytes\n", len(bytecodeHex)/2)

	// 4. Get the current nonce — returns *sdk.Nonce with .NextNonce
	nonceResult, err := client.GetNonce(ctx, sdk.Address(account.Address))
	if err != nil {
		var httpErr *sdk.HttpError
		if errors.As(err, &httpErr) {
			log.Fatalf("HTTP error %d: %s", httpErr.StatusCode, httpErr.Message)
		}
		log.Fatal("nonce query failed: ", err)
	}
	fmt.Printf("Current nonce: %d\n", nonceResult.NextNonce)

	// 5. Hash the bytecode hex for the canonical payload
	bytecodeHash, err := crypto.HashHex(ctx, bytecodeHex)
	if err != nil {
		log.Fatal("hashing failed: ", err)
	}
	fmt.Println("Bytecode hash:", bytecodeHash)

	// 6. Build the canonical deploy payload (keys sorted for deterministic signing)
	canonical := client.BuildDeployCanonicalPayload(
		string(sdk.Address(account.Address)), contractName, bytecodeHash, nonceResult.NextNonce, chainID,
	)
	fmt.Println("Canonical payload:", canonical)

	// 7. Sign the canonical payload
	canonicalJSON, err := json.Marshal(canonical)
	if err != nil {
		log.Fatal("JSON marshal failed: ", err)
	}
	sig, err := crypto.SignMessage(ctx, account.SecretKey, string(canonicalJSON))
	if err != nil {
		log.Fatal("signing failed: ", err)
	}
	fmt.Println("Signed with algorithm:", sig.Algorithm)

	// 8. Assemble the full DeployPayload
	deployPayload := sdk.DeployPayload{
		Name:     contractName,
		Bytecode: bytecodeHex,
		From:     string(sdk.Address(account.Address)),
		Alg:      sig.Algorithm,
		PK:       account.PublicKey,
		Sig:      sig.Signature,
		Nonce:    nonceResult.NextNonce,
		ChainID:  chainID,
		Version:  1,
	}

	// 9. Deploy the contract — returns *sdk.SubmitResult
	result, err := client.DeployContract(ctx, deployPayload)
	if err != nil {
		var rpcErr *sdk.RpcError
		var dilErr *sdk.DilithiaError
		if errors.As(err, &rpcErr) {
			log.Fatalf("RPC error %d: %s", rpcErr.Code, rpcErr.Message)
		} else if errors.As(err, &dilErr) {
			log.Fatalf("Dilithia error: %s", dilErr.Message)
		}
		log.Fatal("deploy request failed: ", err)
	}
	fmt.Printf("Deploy tx submitted: %s (accepted: %v)\n", result.TxHash, result.Accepted)

	// 10. Wait for the receipt — returns *sdk.Receipt
	receipt, err := client.WaitForReceipt(ctx, result.TxHash, 30, 3*time.Second)
	if err != nil {
		log.Fatal("receipt polling failed: ", err)
	}
	fmt.Printf("Contract deployed at block %d, status: %s, tx: %s\n",
		receipt.BlockHeight, receipt.Status, receipt.TxHash)
}
```

---

## Scenario 8: Shielded Pool Deposit & Withdraw

Deposit tokens into a shielded pool using a commitment hash and zero-knowledge proof, then withdraw them to a recipient address by revealing a nullifier. Covers: ZK adapter setup, commitment computation, proof generation, shielded deposit, commitment root retrieval, nullifier check, shielded withdrawal, and receipt polling.

```go
package main

import (
	"context"
	"fmt"
	"log"
	"time"

	"github.com/dilithia/languages-sdk/go/sdk"
)

const (
	rpcURL    = "https://rpc.dilithia.network/rpc"
	secretHex = "aabbccdd11223344aabbccdd11223344aabbccdd11223344aabbccdd11223344"
	nonceHex  = "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff"
	recipient = "dil1_recipient_address"
)

func main() {
	ctx := context.Background()

	// 1. Initialize client, crypto adapter, and ZK adapter
	client := sdk.NewClient(rpcURL, sdk.WithTimeout(10*time.Second))
	crypto, err := sdk.LoadNativeCryptoAdapter()
	if err != nil {
		log.Fatal("crypto adapter unavailable: ", err)
	}
	zk, err := sdk.LoadNativeZkAdapter()
	if err != nil {
		log.Fatal("zk adapter unavailable: ", err)
	}

	// 2. Compute the commitment for the deposit
	var depositValue int64 = 250_000
	commitment, err := zk.ComputeCommitment(ctx, depositValue, secretHex, nonceHex)
	if err != nil {
		log.Fatal("commitment computation failed: ", err)
	}
	fmt.Println("Commitment:", commitment)

	// 3. Hash the commitment for on-chain storage
	commitmentHash, err := crypto.HashHex(ctx, commitment)
	if err != nil {
		log.Fatal("hashing commitment failed: ", err)
	}
	fmt.Println("Commitment hash:", commitmentHash)

	// 4. Generate a preimage proof for the deposit
	proofResult, err := zk.GeneratePreimageProof(ctx, []int64{depositValue})
	if err != nil {
		log.Fatal("proof generation failed: ", err)
	}
	fmt.Println("Deposit proof generated")

	// 5. Deposit into the shielded pool
	depositTx, err := client.ShieldedDeposit(ctx, commitmentHash, depositValue, proofResult)
	if err != nil {
		log.Fatal("shielded deposit failed: ", err)
	}
	fmt.Printf("Deposit tx submitted: %s\n", depositTx)

	// 6. Wait for the deposit receipt
	depositReceipt, err := client.WaitForReceipt(ctx, depositTx, 20, 2*time.Second)
	if err != nil {
		log.Fatal("deposit receipt polling failed: ", err)
	}
	fmt.Printf("Deposit confirmed at block %d, status: %s\n",
		depositReceipt.BlockHeight, depositReceipt.Status)

	// 7. Retrieve the current commitment root
	commitmentRoot, err := client.GetCommitmentRoot(ctx)
	if err != nil {
		log.Fatal("commitment root retrieval failed: ", err)
	}
	fmt.Println("Commitment root:", commitmentRoot)

	// 8. Compute the nullifier for withdrawal
	nullifierHash, err := zk.ComputeNullifier(ctx, secretHex, nonceHex)
	if err != nil {
		log.Fatal("nullifier computation failed: ", err)
	}
	fmt.Println("Nullifier hash:", nullifierHash)

	// 9. Check that the nullifier has not already been spent
	spent, err := client.IsNullifierSpent(ctx, nullifierHash)
	if err != nil {
		log.Fatal("nullifier check failed: ", err)
	}
	if spent {
		log.Fatal("nullifier already spent — cannot withdraw")
	}
	fmt.Println("Nullifier is unspent, proceeding with withdrawal")

	// 10. Generate a withdrawal proof
	withdrawProof, err := zk.GeneratePreimageProof(ctx, []int64{depositValue})
	if err != nil {
		log.Fatal("withdrawal proof generation failed: ", err)
	}

	// 11. Withdraw from the shielded pool
	var withdrawAmount int64 = 250_000
	withdrawTx, err := client.ShieldedWithdraw(
		ctx, nullifierHash, withdrawAmount, recipient, withdrawProof, commitmentRoot,
	)
	if err != nil {
		log.Fatal("shielded withdraw failed: ", err)
	}
	fmt.Printf("Withdraw tx submitted: %s\n", withdrawTx)

	// 12. Wait for the withdrawal receipt
	withdrawReceipt, err := client.WaitForReceipt(ctx, withdrawTx, 20, 2*time.Second)
	if err != nil {
		log.Fatal("withdraw receipt polling failed: ", err)
	}
	fmt.Printf("Withdraw confirmed at block %d, status: %s\n",
		withdrawReceipt.BlockHeight, withdrawReceipt.Status)
	fmt.Println("Shielded deposit and withdrawal complete.")
}
```

---

## Scenario 9: ZK Proof Generation & Verification

Generate and verify zero-knowledge proofs without interacting with the chain. Demonstrates standalone Poseidon hashing, preimage proof round-trips, and range proof round-trips. Covers: ZK adapter setup, Poseidon hash, preimage proof generation and verification, range proof generation and verification.

```go
package main

import (
	"context"
	"fmt"
	"log"

	"github.com/dilithia/languages-sdk/go/sdk"
)

func main() {
	ctx := context.Background()

	// 1. Load the ZK adapter (no client or crypto adapter needed)
	zk, err := sdk.LoadNativeZkAdapter()
	if err != nil {
		log.Fatal("zk adapter unavailable: ", err)
	}

	// ---- Poseidon Hashing ----

	// 2. Compute a Poseidon hash over a set of field elements
	hash, err := zk.PoseidonHash(ctx, []int64{42, 100, 7})
	if err != nil {
		log.Fatal("poseidon hash failed: ", err)
	}
	fmt.Println("Poseidon hash:", hash)

	// 3. Hash a single value to show deterministic output
	hashSingle, err := zk.PoseidonHash(ctx, []int64{12345})
	if err != nil {
		log.Fatal("poseidon hash (single) failed: ", err)
	}
	fmt.Println("Poseidon hash (single):", hashSingle)

	// ---- Preimage Proof Generation & Verification ----

	// 4. Generate a preimage proof for a known set of inputs
	preimageInputs := []int64{10, 20, 30}
	preimageProof, err := zk.GeneratePreimageProof(ctx, preimageInputs)
	if err != nil {
		log.Fatal("preimage proof generation failed: ", err)
	}
	fmt.Println("Preimage proof generated successfully")

	// 5. Verify the preimage proof
	preimageValid, err := zk.VerifyPreimageProof(ctx, preimageProof.Proof, preimageProof.VK, preimageProof.Inputs)
	if err != nil {
		log.Fatal("preimage proof verification failed: ", err)
	}
	fmt.Printf("Preimage proof valid: %v\n", preimageValid)

	// ---- Range Proof Generation & Verification ----

	// 6. Generate a range proof asserting that a value lies within [min, max]
	var value int64 = 500
	var min int64 = 0
	var max int64 = 1000
	rangeProof, err := zk.GenerateRangeProof(ctx, value, min, max)
	if err != nil {
		log.Fatal("range proof generation failed: ", err)
	}
	fmt.Printf("Range proof generated for value=%d in [%d, %d]\n", value, min, max)

	// 7. Verify the range proof
	rangeValid, err := zk.VerifyRangeProof(ctx, rangeProof.Proof, rangeProof.VK, rangeProof.Inputs)
	if err != nil {
		log.Fatal("range proof verification failed: ", err)
	}
	fmt.Printf("Range proof valid: %v\n", rangeValid)

	// ---- Edge Case: Out-of-Range Value ----

	// 8. Attempt to generate a range proof for a value outside the range
	var outOfRange int64 = 2000
	_, err = zk.GenerateRangeProof(ctx, outOfRange, min, max)
	if err != nil {
		fmt.Printf("Expected error for out-of-range value %d: %v\n", outOfRange, err)
	} else {
		fmt.Println("Warning: proof generated for out-of-range value (unexpected)")
	}

	fmt.Println("All ZK operations completed.")
}
```
