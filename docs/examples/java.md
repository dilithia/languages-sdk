# Java Examples

Complete, self-contained Java programs demonstrating the Dilithia SDK. Each scenario is a standalone class with a `public static void main` entry point. All examples target Java 17+ and use the native crypto bridge via JNA.

---

## Prerequisites

### Maven dependency

```xml
<dependency>
  <groupId>org.dilithia</groupId>
  <artifactId>dilithia-sdk-java</artifactId>
  <version>0.3.0</version>
</dependency>
```

### Native library

Set the `DILITHIUM_NATIVE_CORE_LIB` environment variable to the path of the compiled
Dilithia native core shared library before running any example:

```bash
export DILITHIUM_NATIVE_CORE_LIB=/usr/local/lib/libdilithia_core.so
```

The `NativeCryptoBridge` constructor reads this variable and loads the library via JNA.
If the variable is missing or blank, it throws `IllegalStateException`.

---

## Scenario 1: Balance Monitor Bot

A bot that recovers its wallet from a mnemonic, checks its balance, and sends tokens
to a destination address when the balance exceeds a threshold. Covers: client setup,
wallet recovery, balance query, contract call construction, signing, submission, and
receipt polling.

```java
package com.example;

import org.dilithia.sdk.*;
import org.dilithia.sdk.model.*;
import org.dilithia.sdk.crypto.NativeCryptoBridge;

import java.time.Duration;

public class BalanceMonitorBot {
    static final String RPC_URL = "https://rpc.dilithia.network/rpc";
    static final String TOKEN_CONTRACT = "dil1_token_main";
    static final String DESTINATION = "dil1_recipient_address";
    static final long THRESHOLD = 500_000;
    static final long SEND_AMOUNT = 100_000;

    public static void main(String[] args) {
        try {
            // 1. Initialize client with builder and crypto adapter
            var client = Dilithia.client(RPC_URL).timeout(Duration.ofSeconds(15)).build();
            var crypto = new NativeCryptoBridge();

            // 2. Recover wallet from saved mnemonic
            var mnemonic = System.getenv("BOT_MNEMONIC");
            if (mnemonic == null || mnemonic.isBlank()) {
                throw new IllegalStateException("Set BOT_MNEMONIC env var");
            }
            var account = crypto.recoverHdWallet(mnemonic);
            System.out.println("Bot address: " + account.address());

            // 3. Check current balance — returns Balance with typed fields
            Balance balance = client.balance(Address.of(account.address())).get();
            System.out.println("Current balance: " + balance.value()
                    + " (raw: " + balance.rawValue() + ")");

            if (balance.value().lessThan(TokenAmount.dili(String.valueOf(THRESHOLD)))) {
                System.out.printf("Balance below threshold %d. Nothing to do.%n", THRESHOLD);
                return;
            }

            // 4. Build a contract call to transfer tokens, sign, and submit
            DilithiaSigner signer = payload -> new SignedPayload(
                    "mldsa65",
                    PublicKey.of(account.publicKey()),
                    crypto.signMessage(account.secretKey(), payload).signature());

            // 5. Send the contract call — returns Receipt directly
            Receipt receipt = client.contract(TOKEN_CONTRACT)
                    .call("transfer", java.util.Map.of(
                            "to", DESTINATION,
                            "amount", SEND_AMOUNT))
                    .send(signer);
            System.out.printf("Confirmed at block %d, status: %s, tx: %s%n",
                    receipt.blockHeight(), receipt.status(), receipt.txHash());

        } catch (HttpException e) {
            System.err.printf("HTTP error %d: %s%n", e.statusCode(), e.getMessage());
            System.exit(1);
        } catch (RpcException e) {
            System.err.printf("RPC error %d: %s%n", e.code(), e.getMessage());
            System.exit(1);
        } catch (TimeoutException e) {
            System.err.println("Timeout: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Fatal error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
```

---

## Scenario 2: Multi-Account Treasury Manager

A service that manages multiple HD wallet accounts derived from a single mnemonic.
It derives accounts 0 through 4, checks each balance, and consolidates all funds
into account 0. Covers: HD derivation loop, multiple balance queries, and batch
transaction construction.

```java
package com.example;

import org.dilithia.sdk.*;
import org.dilithia.sdk.model.*;
import org.dilithia.sdk.crypto.NativeCryptoBridge;

import java.time.Duration;
import java.util.ArrayList;

public class TreasuryManager {
    static final String RPC_URL = "https://rpc.dilithia.network/rpc";
    static final String TOKEN_CONTRACT = "dil1_token_main";
    static final int NUM_ACCOUNTS = 5;

    public static void main(String[] args) {
        try {
            // Initialize client with builder
            var client = Dilithia.client(RPC_URL).timeout(Duration.ofSeconds(15)).build();
            var crypto = new NativeCryptoBridge();

            var mnemonic = System.getenv("TREASURY_MNEMONIC");
            if (mnemonic == null || mnemonic.isBlank()) {
                throw new IllegalStateException("Set TREASURY_MNEMONIC env var");
            }

            // 1. Derive all accounts
            var accounts = new ArrayList<DilithiaAccount>();
            for (int i = 0; i < NUM_ACCOUNTS; i++) {
                accounts.add(crypto.recoverHdWalletAccount(mnemonic, i));
            }
            var treasuryAccount = accounts.getFirst();
            System.out.println("Treasury address (account 0): " + treasuryAccount.address());

            // 2. Check balances — balance() returns Balance with typed fields
            var balances = new ArrayList<Balance>();
            for (int i = 0; i < NUM_ACCOUNTS; i++) {
                Balance bal = client.balance(Address.of(accounts.get(i).address())).get();
                balances.add(bal);
                System.out.printf("  Account %d: %s -> %s%n",
                        i, bal.address(), bal.value());
            }

            // 3. Consolidate from accounts 1-4 to account 0
            for (int i = 1; i < NUM_ACCOUNTS; i++) {
                if (balances.get(i).value().isZero()) {
                    System.out.printf("  Account %d: zero balance, skipping.%n", i);
                    continue;
                }

                final int accountIdx = i;
                DilithiaSigner signer = payload -> new SignedPayload(
                        "mldsa65",
                        PublicKey.of(accounts.get(accountIdx).publicKey()),
                        crypto.signMessage(
                                accounts.get(accountIdx).secretKey(), payload).signature());

                System.out.printf("  Consolidating %s from account %d...%n",
                        balances.get(i).value(), i);

                // contract().call().send() returns Receipt
                Receipt receipt = client.contract(TOKEN_CONTRACT)
                        .call("transfer", java.util.Map.of(
                                "to", treasuryAccount.address(),
                                "amount", balances.get(i).rawValue()))
                        .send(signer);
                System.out.printf("  Done. Block %d, status: %s%n",
                        receipt.blockHeight(), receipt.status());
            }

            // 4. Final balance check
            Balance finalBalance = client.balance(Address.of(treasuryAccount.address())).get();
            System.out.println("\nTreasury final balance: " + finalBalance.value());

        } catch (HttpException | RpcException | TimeoutException e) {
            System.err.println("SDK error: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Fatal error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
```

---

## Scenario 3: Signature Verification Service

An API endpoint that receives a signed message and verifies the signature against the
claimed public key and address. Covers: address validation, public key validation,
signature verification, and structured error handling.

```java
package com.example;

import org.dilithia.sdk.*;
import org.dilithia.sdk.model.*;
import org.dilithia.sdk.crypto.NativeCryptoBridge;

public class SignatureVerifier {

    record VerifyRequest(String publicKey, String address, String message, String signature) {}
    record VerifyResult(boolean valid, String error) {}

    static VerifyResult verify(NativeCryptoBridge crypto, VerifyRequest req) {
        // 1. Validate the public key format
        try {
            crypto.validatePublicKey(req.publicKey());
        } catch (Exception e) {
            return new VerifyResult(false, "Invalid public key: " + e.getMessage());
        }

        // 2. Validate the signature format
        try {
            crypto.validateSignature(req.signature());
        } catch (Exception e) {
            return new VerifyResult(false, "Invalid signature: " + e.getMessage());
        }

        // 3. Validate the claimed address format using Address.of
        try {
            Address.of(req.address());
            crypto.validateAddress(req.address());
        } catch (Exception e) {
            return new VerifyResult(false, "Invalid address: " + e.getMessage());
        }

        // 4. Verify the public key maps to the claimed address
        try {
            var derived = crypto.addressFromPublicKey(req.publicKey());
            if (!Address.of(derived).equals(Address.of(req.address()))) {
                return new VerifyResult(false, "Address does not match public key");
            }
        } catch (Exception e) {
            return new VerifyResult(false, "Address derivation failed: " + e.getMessage());
        }

        // 5. Verify the cryptographic signature
        try {
            boolean valid = crypto.verifyMessage(req.publicKey(), req.message(), req.signature());
            if (!valid) {
                return new VerifyResult(false, "Signature verification failed");
            }
        } catch (Exception e) {
            return new VerifyResult(false, "Verification error: " + e.getMessage());
        }

        return new VerifyResult(true, null);
    }

    public static void main(String[] args) {
        try {
            var crypto = new NativeCryptoBridge();

            // Generate a keypair and sign a message to test verification
            var keypair = crypto.keygen();
            var message = "Login nonce: 98765";
            var sig = crypto.signMessage(keypair.secretKey(), message);
            var address = crypto.addressFromPublicKey(keypair.publicKey());

            System.out.println("Testing with generated keypair:");
            System.out.println("  Address:   " + Address.of(address));
            System.out.println("  Public key: " + keypair.publicKey().substring(0, 32) + "...");

            // Verify with correct data
            var goodResult = verify(crypto, new VerifyRequest(
                    keypair.publicKey(), address, message, sig.signature()));
            System.out.println("Valid signature result: " + goodResult.valid());

            // Verify with wrong message
            var badResult = verify(crypto, new VerifyRequest(
                    keypair.publicKey(), address, "tampered message", sig.signature()));
            System.out.println("Tampered message result: " + badResult.valid()
                    + (badResult.error() != null ? " (" + badResult.error() + ")" : ""));

        } catch (Exception e) {
            System.err.println("Fatal error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
```

---

## Scenario 4: Wallet Backup and Recovery

Create a new wallet, save the encrypted wallet file to disk, then recover it later
from the saved file. Covers the full wallet lifecycle: generate mnemonic, create
encrypted wallet file, serialize, write to disk, read from disk, deserialize, and
recover.

```java
package com.example;

import org.dilithia.sdk.*;
import org.dilithia.sdk.model.*;
import org.dilithia.sdk.crypto.NativeCryptoBridge;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.Map;

public class WalletBackup {
    static final String WALLET_PATH = "./my-wallet.json";
    static final String PASSWORD = "my-secure-passphrase";

    public static void main(String[] args) {
        try {
            var mapper = new ObjectMapper();
            var crypto = new NativeCryptoBridge();

            var walletFile = new File(WALLET_PATH);

            if (!walletFile.exists()) {
                // ---- CREATE NEW WALLET ----
                System.out.println("No wallet found. Creating a new one...");

                // 1. Generate a fresh mnemonic
                var mnemonic = crypto.generateMnemonic();
                System.out.println("SAVE THIS MNEMONIC SECURELY:");
                System.out.println(mnemonic);
                System.out.println();

                // 2. Validate the generated mnemonic
                crypto.validateMnemonic(mnemonic);
                System.out.println("Mnemonic validated successfully.");

                // 3. Create an encrypted wallet file
                var account = crypto.createHdWalletFileFromMnemonic(mnemonic, PASSWORD);
                System.out.println("Address:    " + Address.of(account.address()));
                System.out.println("Public key: " + account.publicKey());

                // 4. Save to disk
                mapper.writerWithDefaultPrettyPrinter()
                        .writeValue(walletFile, account.walletFile());
                System.out.println("Wallet saved to " + WALLET_PATH);

                // 5. Verify a round-trip sign/verify
                var sig = crypto.signMessage(account.secretKey(), "hello from new wallet");
                boolean ok = crypto.verifyMessage(account.publicKey(), "hello from new wallet", sig.signature());
                System.out.println("Sign/verify round-trip: " + (ok ? "PASS" : "FAIL"));

            } else {
                // ---- RECOVER EXISTING WALLET ----
                System.out.println("Wallet file found. Recovering...");

                // 6. Read and deserialize
                @SuppressWarnings("unchecked")
                var savedWallet = mapper.readValue(walletFile, Map.class);

                // 7. Recover using mnemonic + password
                var mnemonic = System.getenv("WALLET_MNEMONIC");
                if (mnemonic == null || mnemonic.isBlank()) {
                    throw new IllegalStateException("Set WALLET_MNEMONIC env var to recover");
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> walletMap = (Map<String, Object>) savedWallet;
                var account = crypto.recoverWalletFile(walletMap, mnemonic, PASSWORD);
                System.out.println("Recovered address:    " + Address.of(account.address()));
                System.out.println("Recovered public key: " + account.publicKey());
                System.out.println("Wallet recovered successfully. Ready to sign transactions.");
            }

        } catch (Exception e) {
            System.err.println("Fatal error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
```

---

## Scenario 5: Gas-Sponsored Meta-Transaction

Submit a transaction where the gas fee is paid by a sponsor contract instead of the
sender. Useful for onboarding new users who have no tokens to pay for gas. Covers:
client setup, building a paymaster-attached call, signing with the native adapter,
and submission.

```java
package com.example;

import org.dilithia.sdk.*;
import org.dilithia.sdk.model.*;
import org.dilithia.sdk.crypto.NativeCryptoBridge;

import java.time.Duration;
import java.util.Map;

public class GasSponsoredMint {
    static final String RPC_URL = "https://rpc.dilithia.network/rpc";
    static final String SPONSOR_CONTRACT = "dil1_gas_sponsor_v1";
    static final String PAYMASTER = "dil1_paymaster_addr";
    static final String TARGET_CONTRACT = "dil1_nft_mint";

    public static void main(String[] args) {
        try {
            // 1. Initialize client with builder and crypto adapter
            var client = Dilithia.client(RPC_URL).timeout(Duration.ofSeconds(15)).build();
            var crypto = new NativeCryptoBridge();

            // 2. Recover the user's wallet
            var mnemonic = System.getenv("USER_MNEMONIC");
            if (mnemonic == null || mnemonic.isBlank()) {
                throw new IllegalStateException("Set USER_MNEMONIC env var");
            }
            var account = crypto.recoverHdWallet(mnemonic);
            System.out.println("User address: " + Address.of(account.address()));

            // 3. Check if the sponsor accepts this call — query returns QueryResult
            QueryResult acceptResult = client.contract(SPONSOR_CONTRACT)
                    .query("accept", Map.of(
                            "user", account.address(),
                            "contract", TARGET_CONTRACT,
                            "method", "mint"))
                    .get();
            System.out.println("Sponsor accepts: " + acceptResult);

            // 4. Check remaining quota — query returns QueryResult
            QueryResult quotaResult = client.contract(SPONSOR_CONTRACT)
                    .query("remaining_quota", Map.of("user", account.address()))
                    .get();
            System.out.println("Remaining quota: " + quotaResult);

            // 5. Get gas estimate — returns GasEstimate
            GasEstimate gasEstimate = client.network().gasEstimate();
            System.out.println("Current gas estimate: " + gasEstimate);

            // 6. Build signer
            DilithiaSigner signer = payload -> new SignedPayload(
                    "mldsa65",
                    PublicKey.of(account.publicKey()),
                    crypto.signMessage(account.secretKey(), payload).signature());

            // 7. Send the sponsored call — returns Receipt
            Receipt receipt = client.contract(TARGET_CONTRACT)
                    .call("mint", Map.of(
                            "token_id", "nft_001",
                            "metadata", "ipfs://QmSomeHash"))
                    .send(signer);
            System.out.printf("Sponsored tx confirmed at block %d, status: %s%n",
                    receipt.blockHeight(), receipt.status());

        } catch (HttpException e) {
            System.err.printf("HTTP error %d: %s%n", e.statusCode(), e.getMessage());
            System.exit(1);
        } catch (RpcException e) {
            System.err.printf("RPC error %d: %s%n", e.code(), e.getMessage());
            System.exit(1);
        } catch (TimeoutException e) {
            System.err.println("Timeout: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Fatal error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
```

---

## Scenario 6: Cross-Chain Message Sender

Send a message to another Dilithia chain via the messaging contract. Useful for
bridging data or triggering actions on a remote chain. Covers: client setup,
building outbound messages, signing, submission, and receipt polling.

```java
package com.example;

import org.dilithia.sdk.*;
import org.dilithia.sdk.model.*;
import org.dilithia.sdk.crypto.NativeCryptoBridge;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public class CrossChainSender {
    static final String RPC_URL = "https://rpc.dilithia.network/rpc";
    static final String MESSAGING_CONTRACT = "dil1_bridge_v1";
    static final String PAYMASTER = "dil1_bridge_paymaster";
    static final String DEST_CHAIN = "dilithia-testnet-2";

    public static void main(String[] args) {
        try {
            // 1. Initialize client with builder
            var client = Dilithia.client(RPC_URL).timeout(Duration.ofSeconds(15)).build();
            var crypto = new NativeCryptoBridge();

            // 2. Recover sender wallet
            var mnemonic = System.getenv("SENDER_MNEMONIC");
            if (mnemonic == null || mnemonic.isBlank()) {
                throw new IllegalStateException("Set SENDER_MNEMONIC env var");
            }
            var account = crypto.recoverHdWallet(mnemonic);
            System.out.println("Sender address: " + Address.of(account.address()));

            // 3. Resolve a name — returns NameRecord
            NameRecord resolved = client.names().resolve("alice.dili").get();
            System.out.println("Resolved alice.dili -> " + resolved);

            // 4. Build the cross-chain message payload
            Map<String, Object> messagePayload = new LinkedHashMap<>();
            messagePayload.put("action", "lock_tokens");
            messagePayload.put("sender", account.address());
            messagePayload.put("amount", TokenAmount.dili("50000"));
            messagePayload.put("recipient", Address.of("dil1_remote_recipient").value());

            // 5. Build signer
            DilithiaSigner signer = payload -> new SignedPayload(
                    "mldsa65",
                    PublicKey.of(account.publicKey()),
                    crypto.signMessage(account.secretKey(), payload).signature());

            // 6. Send the cross-chain message call — returns Receipt
            Map<String, Object> sendArgs = new LinkedHashMap<>();
            sendArgs.put("dest_chain", DEST_CHAIN);
            sendArgs.put("payload", messagePayload);
            Receipt receipt = client.contract(MESSAGING_CONTRACT)
                    .call("send_message", sendArgs)
                    .send(signer);
            System.out.printf("Message tx confirmed at block %d, status: %s, tx: %s%n",
                    receipt.blockHeight(), receipt.status(), receipt.txHash());

            // 7. Optionally wait for the receipt with explicit polling
            TxHash txHash = TxHash.of(receipt.txHash().value());
            Receipt polled = client.receipt(txHash).waitFor(12, Duration.ofSeconds(1));
            System.out.println("Polled receipt status: " + polled.status());

        } catch (HttpException | RpcException | TimeoutException e) {
            System.err.println("SDK error: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Fatal error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
```

---

## Scenario 7: Contract Deployment

Deploy a WASM smart contract to the Dilithia chain. Reads the WASM binary, hashes the
bytecode, builds and signs a canonical deploy payload, assembles the full `DeployPayload`,
sends the deploy request, and waits for confirmation.

```java
package com.example;

import org.dilithia.sdk.*;
import org.dilithia.sdk.model.*;
import org.dilithia.sdk.crypto.NativeCryptoBridge;

import java.time.Duration;

public class ContractDeployer {
    static final String RPC_URL = "https://rpc.dilithia.network/rpc";
    static final String CONTRACT_NAME = "my_contract";
    static final String WASM_PATH = "./my_contract.wasm";
    static final String CHAIN_ID = "dilithia-mainnet";

    public static void main(String[] args) {
        try {
            // 1. Initialize client with builder and crypto adapter
            var client = Dilithia.client(RPC_URL).timeout(Duration.ofSeconds(30)).build();
            var crypto = new NativeCryptoBridge();

            // 2. Recover wallet from mnemonic
            var mnemonic = System.getenv("DEPLOYER_MNEMONIC");
            if (mnemonic == null || mnemonic.isBlank()) {
                throw new IllegalStateException("Set DEPLOYER_MNEMONIC env var");
            }
            var account = crypto.recoverHdWallet(mnemonic);
            System.out.println("Deployer address: " + Address.of(account.address()));

            // 3. Read the WASM file as hex
            String bytecodeHex = Dilithia.readWasmFileHex(WASM_PATH);
            System.out.println("Bytecode size: " + (bytecodeHex.length() / 2) + " bytes");

            // 4. Get the current nonce — returns Nonce with .nextNonce()
            Nonce nonceResult = client.nonce(Address.of(account.address())).get();
            System.out.println("Current nonce: " + nonceResult.nextNonce());

            // 5. Hash the bytecode hex for the canonical payload
            String bytecodeHash = crypto.hashHex(bytecodeHex);
            System.out.println("Bytecode hash: " + bytecodeHash);

            // 6. Build signer
            DilithiaSigner signer = payload -> new SignedPayload(
                    "mldsa65",
                    PublicKey.of(account.publicKey()),
                    crypto.signMessage(account.secretKey(), payload).signature());

            // 7. Build the deploy payload
            var deployPayload = new DeployPayload(
                    CONTRACT_NAME,
                    bytecodeHex,
                    Address.of(account.address()).value(),
                    "mldsa65",
                    account.publicKey(),
                    null, // signature applied by signer
                    nonceResult.nextNonce(),
                    CHAIN_ID,
                    1
            );

            // 8. Deploy — returns Receipt
            Receipt receipt = client.deploy(deployPayload).send(signer);
            System.out.printf("Contract deployed at block %d, status: %s, tx: %s%n",
                    receipt.blockHeight(), receipt.status(), receipt.txHash());

            // 9. Verify with explicit receipt lookup using TxHash typed identifier
            Receipt verified = client.receipt(TxHash.of(receipt.txHash().value()))
                    .waitFor(30, Duration.ofSeconds(3));
            System.out.println("Verified deployment status: " + verified.status());

            // 10. Shielded deposit example (bonus)
            // Receipt shielded = client.shielded()
            //         .deposit(commitment, TokenAmount.dili("100.5"), proof)
            //         .send(signer);

        } catch (HttpException e) {
            System.err.printf("HTTP error %d: %s%n", e.statusCode(), e.getMessage());
            System.exit(1);
        } catch (RpcException e) {
            System.err.printf("RPC error %d: %s%n", e.code(), e.getMessage());
            System.exit(1);
        } catch (TimeoutException e) {
            System.err.println("Timeout: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Deploy error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
```
