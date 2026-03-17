# Go Examples

Complete, self-contained Go programs demonstrating common tasks with the Dilithia SDK. Each scenario is a standalone `package main` program ready to compile and run.

---

## Prerequisites

Install the SDK module:

```bash
go get github.com/dilithia/languages-sdk/go@v0.2.0
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

	// 1. Initialize client and crypto adapter
	client := sdk.NewClient(rpcURL, 15*time.Second)
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

	// 3. Check current balance
	balanceResult, err := client.GetBalance(ctx, account.Address)
	if err != nil {
		log.Fatal("balance query failed: ", err)
	}
	balance, _ := balanceResult["balance"].(float64)
	fmt.Printf("Current balance: %.0f\n", balance)

	if balance < threshold {
		fmt.Printf("Balance %.0f below threshold %d. Nothing to do.\n", balance, threshold)
		return
	}

	// 4. Build a contract call to transfer tokens
	call := client.BuildContractCall(tokenContract, "transfer", map[string]any{
		"to":     destination,
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

	submitted, err := client.SendCall(ctx, call)
	if err != nil {
		log.Fatal("submit failed: ", err)
	}
	txHash, _ := submitted["tx_hash"].(string)
	fmt.Println("Transaction submitted:", txHash)

	// 7. Poll for receipt
	receipt, err := client.WaitForReceipt(ctx, txHash, 20, 2*time.Second)
	if err != nil {
		log.Fatal("receipt polling failed: ", err)
	}
	fmt.Println("Transaction confirmed:", receipt)
}
```

---

## Scenario 2: Multi-Account Treasury Manager

A service that derives accounts 0 through 4 from a single mnemonic, checks each balance, and consolidates all funds into account 0. Covers: HD derivation loop, multiple balance queries, and batch transaction construction.

```go
package main

import (
	"context"
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

	client := sdk.NewClient(rpcURL, 10*time.Second)
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
	treasuryAddress := accounts[0].Address
	fmt.Println("Treasury address (account 0):", treasuryAddress)

	// 2. Check balances for every account
	balances := make([]float64, numAccounts)
	for i, acct := range accounts {
		result, err := client.GetBalance(ctx, acct.Address)
		if err != nil {
			log.Fatalf("balance query failed for account %d: %v", i, err)
		}
		bal, _ := result["balance"].(float64)
		balances[i] = bal
		fmt.Printf("  Account %d: %s -> %.0f\n", acct.AccountIndex, acct.Address, bal)
	}

	// 3. Consolidate: transfer from accounts 1-4 to account 0
	for i := 1; i < numAccounts; i++ {
		if balances[i] <= 0 {
			fmt.Printf("  Account %d: zero balance, skipping.\n", i)
			continue
		}

		call := client.BuildContractCall(tokenContract, "transfer", map[string]any{
			"to":     treasuryAddress,
			"amount": balances[i],
		}, "")

		fmt.Printf("  Consolidating %.0f from account %d...\n", balances[i], i)

		// Sign with the source account's secret key
		sig, err := crypto.SignMessage(ctx, accounts[i].SecretKey, fmt.Sprintf("%v", call))
		if err != nil {
			log.Fatalf("signing failed for account %d: %v", i, err)
		}
		call["algorithm"] = sig.Algorithm
		call["signature"] = sig.Signature

		submitted, err := client.SendCall(ctx, call)
		if err != nil {
			log.Fatalf("submit failed for account %d: %v", i, err)
		}
		txHash, _ := submitted["tx_hash"].(string)

		receipt, err := client.WaitForReceipt(ctx, txHash, 12, time.Second)
		if err != nil {
			log.Fatalf("receipt polling failed for account %d: %v", i, err)
		}
		fmt.Printf("  Done. Receipt: %v\n", receipt)
	}

	// 4. Final balance check on treasury
	finalResult, err := client.GetBalance(ctx, treasuryAddress)
	if err != nil {
		log.Fatal("final balance query failed: ", err)
	}
	fmt.Println("\nTreasury final balance:", finalResult["balance"])
}
```

---

## Scenario 3: Signature Verification Service

An API endpoint that receives a signed message and verifies the signature against the claimed public key and address. Covers: address validation, public key validation, signature verification, and structured error handling.

```go
package main

import (
	"context"
	"fmt"
	"log"

	"github.com/dilithia/languages-sdk/go/sdk"
)

type verifyRequest struct {
	PublicKey string
	Address   string
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

	// 3. Validate the claimed address format
	if _, err := crypto.ValidateAddress(ctx, req.Address); err != nil {
		return verifyResult{Valid: false, Error: "Invalid address format"}
	}

	// 4. Verify that the public key maps to the claimed address
	derivedAddress, err := crypto.AddressFromPublicKey(ctx, req.PublicKey)
	if err != nil {
		return verifyResult{Valid: false, Error: "Failed to derive address from public key"}
	}
	if derivedAddress != req.Address {
		return verifyResult{Valid: false, Error: "Address does not match public key"}
	}

	// 5. Verify the cryptographic signature
	valid, err := crypto.VerifyMessage(ctx, req.PublicKey, req.Message, req.Signature)
	if err != nil {
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
		Address:   "dil1_abc123",
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
		fmt.Println("Address:", account.Address)
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
		fmt.Println("Recovered address:", account.Address)
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

	client := sdk.NewClient(rpcURL, 10*time.Second)
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
	fmt.Println("User address:", account.Address)

	// 2. Set up the gas sponsor connector
	sponsor := sdk.NewGasSponsorConnector(client, sponsorContract, paymasterAddr)

	// 3. Check if the sponsor will accept this call
	acceptQuery := sponsor.BuildAcceptQuery(account.Address, targetContract, "mint")
	acceptArgs, _ := acceptQuery["args"].(map[string]any)
	acceptResult, err := client.QueryContract(ctx, sponsorContract, "accept", acceptArgs)
	if err != nil {
		log.Fatal("accept query failed: ", err)
	}
	fmt.Println("Sponsor accepts:", acceptResult)

	// 4. Check remaining gas quota for this user
	quotaQuery := sponsor.BuildRemainingQuotaQuery(account.Address)
	quotaArgs, _ := quotaQuery["args"].(map[string]any)
	quotaResult, err := client.QueryContract(ctx, sponsorContract, "remaining_quota", quotaArgs)
	if err != nil {
		log.Fatal("quota query failed: ", err)
	}
	fmt.Println("Remaining quota:", quotaResult)

	// 5. Build the actual contract call
	call := client.BuildContractCall(targetContract, "mint", map[string]any{
		"token_id": "nft_001",
		"metadata": "ipfs://QmSomeHash",
	}, "")

	// 6. Apply the paymaster (sponsor pays the gas)
	sponsoredCall := sponsor.ApplyPaymaster(call)
	fmt.Println("Sponsored call:", sponsoredCall)

	// 7. Sign and submit the sponsored call
	sig, err := crypto.SignMessage(ctx, account.SecretKey, fmt.Sprintf("%v", sponsoredCall))
	if err != nil {
		log.Fatal("signing failed: ", err)
	}
	sponsoredCall["algorithm"] = sig.Algorithm
	sponsoredCall["signature"] = sig.Signature

	submitted, err := client.SendCall(ctx, sponsoredCall)
	if err != nil {
		log.Fatal("submit failed: ", err)
	}
	txHash, _ := submitted["tx_hash"].(string)
	fmt.Println("Sponsored tx submitted:", txHash)

	// 8. Wait for confirmation
	receipt, err := client.WaitForReceipt(ctx, txHash, 12, time.Second)
	if err != nil {
		log.Fatal("receipt polling failed: ", err)
	}
	fmt.Println("Confirmed:", receipt)
}
```

---

## Scenario 6: Cross-Chain Message Sender

Send a message to another Dilithia chain via the messaging connector. Useful for bridging data or triggering actions on a remote chain. Covers: messaging connector setup, building outbound messages, signing, and submission.

```go
package main

import (
	"context"
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

	client := sdk.NewClient(rpcURL, 10*time.Second)
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
	fmt.Println("Sender address:", account.Address)

	// 1. Set up the messaging connector
	messaging := sdk.NewMessagingConnector(client, messagingContract, paymaster)

	// 2. Build the cross-chain message payload
	payload := map[string]any{
		"action":    "lock_tokens",
		"sender":    account.Address,
		"amount":    50_000,
		"recipient": "dil1_remote_recipient",
		"timestamp": time.Now().UnixMilli(),
	}

	// 3. Build the send-message call (paymaster attached automatically)
	messageCall := messaging.BuildSendMessageCall(destChain, payload)
	fmt.Println("Message call:", messageCall)

	// 4. Simulate the message send
	simResult, err := client.Simulate(ctx, messageCall)
	if err != nil {
		log.Fatal("simulation failed: ", err)
	}
	fmt.Println("Simulation:", simResult)

	// 5. Sign and send the message
	sig, err := crypto.SignMessage(ctx, account.SecretKey, fmt.Sprintf("%v", messageCall))
	if err != nil {
		log.Fatal("signing failed: ", err)
	}
	messageCall["algorithm"] = sig.Algorithm
	messageCall["signature"] = sig.Signature

	submitted, err := client.SendCall(ctx, messageCall)
	if err != nil {
		log.Fatal("submit failed: ", err)
	}
	txHash, _ := submitted["tx_hash"].(string)
	fmt.Println("Message tx submitted:", txHash)

	// 6. Wait for confirmation
	receipt, err := client.WaitForReceipt(ctx, txHash, 12, time.Second)
	if err != nil {
		log.Fatal("receipt polling failed: ", err)
	}
	fmt.Println("Message confirmed:", receipt)

	// 7. Optionally build a receive-message call for the remote side
	receiveCall := messaging.BuildReceiveMessageCall(
		"dilithia-mainnet", // source chain
		messagingContract,  // source contract
		payload,
	)
	fmt.Println("Receive call (for remote chain):", receiveCall)
}
```
