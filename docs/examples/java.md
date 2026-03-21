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

---

## Scenario 8: Shielded Pool Deposit & Withdraw

Deposit tokens into the shielded pool using a Poseidon commitment, then withdraw them
to a recipient address using a nullifier and zero-knowledge proof. Covers: ZK commitment
computation, shielded deposit, commitment root lookup, nullifier derivation, proof
generation, shielded withdrawal, and receipt polling.

```java
package com.example;

import org.dilithia.sdk.*;
import org.dilithia.sdk.model.*;
import org.dilithia.sdk.crypto.NativeCryptoBridge;
import org.dilithia.sdk.zk.NativeZkBridge;

import java.time.Duration;

public class ShieldedPoolFlow {
    static final String RPC_URL = "https://rpc.dilithia.network/rpc";
    static final String RECIPIENT = "dil1_withdraw_recipient";
    static final long DEPOSIT_VALUE = 250_000;

    public static void main(String[] args) {
        try {
            // 1. Initialize client, crypto, and ZK bridges
            var client = Dilithia.client(RPC_URL).timeout(Duration.ofSeconds(15)).build();
            var crypto = new NativeCryptoBridge();
            var zk = new NativeZkBridge();

            // 2. Generate random secret and nonce for the commitment
            var secretHex = crypto.hashHex("user_secret_entropy_1234");
            var nonceHex = crypto.hashHex("user_nonce_entropy_5678");
            System.out.println("Secret: " + secretHex.substring(0, 16) + "...");
            System.out.println("Nonce:  " + nonceHex.substring(0, 16) + "...");

            // 3. Compute the Poseidon commitment
            Commitment commitment = zk.computeCommitment(DEPOSIT_VALUE, secretHex, nonceHex);
            System.out.println("Commitment hash: " + commitment.hash());
            System.out.println("Commitment value: " + commitment.value());

            // 4. Generate a preimage proof for the deposit
            StarkProof depositProof = zk.generatePreimageProof(
                    new long[]{DEPOSIT_VALUE, Long.parseLong(secretHex.substring(0, 15), 16)});
            System.out.println("Deposit proof generated, vk: " + depositProof.vk().substring(0, 32) + "...");

            // 5. Submit the shielded deposit
            Receipt depositReceipt = client.shielded()
                    .deposit(commitment.hash(), DEPOSIT_VALUE, depositProof.proof());
            System.out.printf("Deposit confirmed at block %d, status: %s, tx: %s%n",
                    depositReceipt.blockHeight(), depositReceipt.status(),
                    depositReceipt.txHash());

            // 6. Poll to confirm the deposit receipt
            Receipt polledDeposit = client.receipt(depositReceipt.txHash())
                    .waitFor(20, Duration.ofSeconds(2));
            System.out.println("Deposit receipt status: " + polledDeposit.status());

            // 7. Query the current commitment root
            String commitmentRoot = client.shielded().commitmentRoot();
            System.out.println("Commitment root: " + commitmentRoot);

            // 8. Compute the nullifier for withdrawal
            Nullifier nullifier = zk.computeNullifier(secretHex, nonceHex);
            System.out.println("Nullifier hash: " + nullifier.hash());

            // 9. Verify the nullifier has not been spent
            boolean spent = client.shielded().isNullifierSpent(nullifier.hash());
            if (spent) {
                System.err.println("Nullifier already spent. Aborting withdrawal.");
                System.exit(1);
            }
            System.out.println("Nullifier is unspent. Proceeding with withdrawal.");

            // 10. Generate a preimage proof for the withdrawal
            StarkProof withdrawProof = zk.generatePreimageProof(
                    new long[]{DEPOSIT_VALUE, Long.parseLong(nonceHex.substring(0, 15), 16)});
            System.out.println("Withdraw proof generated.");

            // 11. Submit the shielded withdrawal
            Receipt withdrawReceipt = client.shielded()
                    .withdraw(nullifier.hash(), DEPOSIT_VALUE, RECIPIENT,
                            withdrawProof.proof(), commitmentRoot);
            System.out.printf("Withdrawal confirmed at block %d, status: %s, tx: %s%n",
                    withdrawReceipt.blockHeight(), withdrawReceipt.status(),
                    withdrawReceipt.txHash());

            // 12. Final confirmation via receipt polling
            Receipt polledWithdraw = client.receipt(withdrawReceipt.txHash())
                    .waitFor(20, Duration.ofSeconds(2));
            System.out.println("Withdrawal receipt status: " + polledWithdraw.status());

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
            System.err.println("Shielded pool error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
```

---

## Scenario 9: ZK Proof Generation & Verification

Generate and verify zero-knowledge proofs using the native ZK bridge. Demonstrates
Poseidon hashing, preimage proof generation and verification, range proof generation
and verification, and commitment/nullifier computation. Covers: all standalone ZK
operations without network interaction.

```java
package com.example;

import org.dilithia.sdk.*;
import org.dilithia.sdk.model.*;
import org.dilithia.sdk.crypto.NativeCryptoBridge;
import org.dilithia.sdk.zk.NativeZkBridge;

public class ZkProofDemo {

    public static void main(String[] args) {
        try {
            var crypto = new NativeCryptoBridge();
            var zk = new NativeZkBridge();

            // ---- POSEIDON HASHING ----

            // 1. Hash a set of field elements with Poseidon
            String hash1 = zk.poseidonHash(new long[]{42, 100, 7});
            System.out.println("Poseidon hash [42, 100, 7]: " + hash1);

            String hash2 = zk.poseidonHash(new long[]{42, 100, 8});
            System.out.println("Poseidon hash [42, 100, 8]: " + hash2);
            System.out.println("Hashes differ: " + !hash1.equals(hash2));

            // ---- COMMITMENT & NULLIFIER ----

            // 2. Compute a commitment from value, secret, and nonce
            var secretHex = crypto.hashHex("my_secret_data");
            var nonceHex = crypto.hashHex("my_nonce_data");
            long value = 500_000;

            Commitment commitment = zk.computeCommitment(value, secretHex, nonceHex);
            System.out.println("\nCommitment hash:   " + commitment.hash());
            System.out.println("Commitment value:  " + commitment.value());
            System.out.println("Commitment secret: " + commitment.secret().substring(0, 16) + "...");
            System.out.println("Commitment nonce:  " + commitment.nonce().substring(0, 16) + "...");

            // 3. Compute the nullifier from the same secret and nonce
            Nullifier nullifier = zk.computeNullifier(secretHex, nonceHex);
            System.out.println("Nullifier hash:    " + nullifier.hash());

            // 4. Verify determinism — same inputs produce same outputs
            Commitment commitment2 = zk.computeCommitment(value, secretHex, nonceHex);
            Nullifier nullifier2 = zk.computeNullifier(secretHex, nonceHex);
            System.out.println("\nCommitment deterministic: " + commitment.hash().equals(commitment2.hash()));
            System.out.println("Nullifier deterministic:  " + nullifier.hash().equals(nullifier2.hash()));

            // ---- PREIMAGE PROOF ----

            // 5. Generate a preimage proof
            long[] preimageInputs = new long[]{value, 42, 7};
            StarkProof preimageProof = zk.generatePreimageProof(preimageInputs);
            System.out.println("\nPreimage proof size: " + preimageProof.proof().length() + " chars");
            System.out.println("Preimage vk:        " + preimageProof.vk().substring(0, 32) + "...");
            System.out.println("Preimage inputs:    " + preimageProof.inputs().length + " elements");

            // 6. Verify the preimage proof — should pass
            boolean preimageValid = zk.verifyPreimageProof(
                    preimageProof.proof(), preimageProof.vk(), preimageProof.inputs());
            System.out.println("Preimage proof valid: " + preimageValid);

            // 7. Verify with tampered inputs — should fail
            long[] tamperedInputs = new long[]{value + 1, 42, 7};
            boolean tamperedValid = zk.verifyPreimageProof(
                    preimageProof.proof(), preimageProof.vk(), tamperedInputs);
            System.out.println("Tampered proof valid: " + tamperedValid);

            // ---- RANGE PROOF ----

            // 8. Generate a range proof — prove value is within [min, max]
            long rangeValue = 750;
            long rangeMin = 0;
            long rangeMax = 1_000_000;
            StarkProof rangeProof = zk.generateRangeProof(rangeValue, rangeMin, rangeMax);
            System.out.println("\nRange proof size: " + rangeProof.proof().length() + " chars");
            System.out.println("Range vk:        " + rangeProof.vk().substring(0, 32) + "...");

            // 9. Verify the range proof — should pass
            boolean rangeValid = zk.verifyRangeProof(
                    rangeProof.proof(), rangeProof.vk(), rangeProof.inputs());
            System.out.println("Range proof valid (750 in [0, 1000000]): " + rangeValid);

            // 10. Generate a range proof for a boundary value
            StarkProof boundaryProof = zk.generateRangeProof(rangeMax, rangeMin, rangeMax);
            boolean boundaryValid = zk.verifyRangeProof(
                    boundaryProof.proof(), boundaryProof.vk(), boundaryProof.inputs());
            System.out.println("Boundary proof valid (1000000 in [0, 1000000]): " + boundaryValid);

            System.out.println("\nAll ZK operations completed successfully.");

        } catch (Exception e) {
            System.err.println("ZK proof error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
```
