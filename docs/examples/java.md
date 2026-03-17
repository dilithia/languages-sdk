# Java Examples

Complete, self-contained Java programs demonstrating the Dilithia SDK. Each scenario is a standalone class with a `public static void main` entry point. All examples target Java 17+ and use the native crypto bridge via JNA.

---

## Prerequisites

### Maven dependency

```xml
<dependency>
  <groupId>org.dilithia</groupId>
  <artifactId>dilithia-sdk-java</artifactId>
  <version>0.2.0</version>
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

import org.dilithia.sdk.DilithiaClient;
import org.dilithia.sdk.DilithiaCryptoAdapter;
import org.dilithia.sdk.DilithiaSigner;
import org.dilithia.sdk.crypto.NativeCryptoBridge;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

public class BalanceMonitorBot {
    static final String RPC_URL = "https://rpc.dilithia.network/rpc";
    static final String TOKEN_CONTRACT = "dil1_token_main";
    static final String DESTINATION = "dil1_recipient_address";
    static final long THRESHOLD = 500_000;
    static final long SEND_AMOUNT = 100_000;

    public static void main(String[] args) {
        try {
            var mapper = new ObjectMapper();
            var http = HttpClient.newHttpClient();

            // 1. Initialize client and crypto adapter
            var client = new DilithiaClient(RPC_URL, 15_000);
            DilithiaCryptoAdapter crypto = new NativeCryptoBridge();

            // 2. Recover wallet from saved mnemonic
            var mnemonic = System.getenv("BOT_MNEMONIC");
            if (mnemonic == null || mnemonic.isBlank()) {
                throw new IllegalStateException("Set BOT_MNEMONIC env var");
            }
            var account = crypto.recoverHdWallet(mnemonic);
            System.out.println("Bot address: " + account.address());

            // 3. Check current balance
            var balanceReq = HttpRequest.newBuilder(URI.create(client.balancePath(account.address())))
                    .header("accept", "application/json").GET().build();
            var balanceBody = http.send(balanceReq, HttpResponse.BodyHandlers.ofString()).body();
            @SuppressWarnings("unchecked")
            var balanceMap = mapper.readValue(balanceBody, Map.class);
            long balance = ((Number) balanceMap.getOrDefault("balance", 0)).longValue();
            System.out.println("Current balance: " + balance);

            if (balance < THRESHOLD) {
                System.out.printf("Balance %d below threshold %d. Nothing to do.%n", balance, THRESHOLD);
                return;
            }

            // 4. Build a contract call to transfer tokens
            var call = client.buildContractCall(TOKEN_CONTRACT, "transfer",
                    Map.of("to", DESTINATION, "amount", SEND_AMOUNT), null);

            // 5. Sign and submit
            DilithiaSigner signer = (payload) -> {
                var sig = crypto.signMessage(account.secretKey(), mapper.writeValueAsString(payload));
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("algorithm", sig.algorithm());
                result.put("signature", sig.signature());
                return result;
            };
            var signedBody = client.sendSignedCallBody(call, signer);
            var callJson = mapper.writeValueAsString(signedBody);

            var submitReq = HttpRequest.newBuilder(URI.create(RPC_URL + "/call"))
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(callJson)).build();
            var submitResp = http.send(submitReq, HttpResponse.BodyHandlers.ofString()).body();
            @SuppressWarnings("unchecked")
            var submitted = mapper.readValue(submitResp, Map.class);
            var txHash = (String) submitted.get("tx_hash");
            System.out.println("Transaction submitted: " + txHash);

            // 6. Poll for receipt
            for (int attempt = 0; attempt < 20; attempt++) {
                try {
                    var receiptReq = HttpRequest.newBuilder(URI.create(client.receiptPath(txHash)))
                            .header("accept", "application/json").GET().build();
                    var receiptResp = http.send(receiptReq, HttpResponse.BodyHandlers.ofString());
                    if (receiptResp.statusCode() == 200) {
                        System.out.println("Transaction confirmed: " + receiptResp.body());
                        return;
                    }
                } catch (Exception ignored) {
                    // Receipt not ready yet
                }
                Thread.sleep(2_000);
            }
            System.err.println("Receipt not available after polling.");

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

import org.dilithia.sdk.DilithiaAccount;
import org.dilithia.sdk.DilithiaClient;
import org.dilithia.sdk.DilithiaCryptoAdapter;
import org.dilithia.sdk.DilithiaSigner;
import org.dilithia.sdk.crypto.NativeCryptoBridge;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

public class TreasuryManager {
    static final String RPC_URL = "https://rpc.dilithia.network/rpc";
    static final String TOKEN_CONTRACT = "dil1_token_main";
    static final int NUM_ACCOUNTS = 5;

    public static void main(String[] args) {
        try {
            var mapper = new ObjectMapper();
            var http = HttpClient.newHttpClient();
            var client = new DilithiaClient(RPC_URL);
            DilithiaCryptoAdapter crypto = new NativeCryptoBridge();

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

            // 2. Check balances
            var balances = new ArrayList<Long>();
            for (int i = 0; i < NUM_ACCOUNTS; i++) {
                var req = HttpRequest.newBuilder(URI.create(client.balancePath(accounts.get(i).address())))
                        .header("accept", "application/json").GET().build();
                var body = http.send(req, HttpResponse.BodyHandlers.ofString()).body();
                @SuppressWarnings("unchecked")
                var result = mapper.readValue(body, Map.class);
                long bal = ((Number) result.getOrDefault("balance", 0)).longValue();
                balances.add(bal);
                System.out.printf("  Account %d: %s -> %d%n", i, accounts.get(i).address(), bal);
            }

            // 3. Consolidate from accounts 1-4 to account 0
            for (int i = 1; i < NUM_ACCOUNTS; i++) {
                if (balances.get(i) <= 0) {
                    System.out.printf("  Account %d: zero balance, skipping.%n", i);
                    continue;
                }

                var call = client.buildContractCall(
                        TOKEN_CONTRACT, "transfer",
                        Map.of("to", treasuryAccount.address(), "amount", balances.get(i)),
                        null);

                // Build a signer for the source account
                final int accountIdx = i;
                DilithiaSigner signer = (payload) -> {
                    var sig = crypto.signMessage(
                            accounts.get(accountIdx).secretKey(),
                            mapper.writeValueAsString(payload));
                    Map<String, Object> sigMap = new LinkedHashMap<>();
                    sigMap.put("algorithm", sig.algorithm());
                    sigMap.put("signature", sig.signature());
                    return sigMap;
                };
                var signedBody = client.sendSignedCallBody(call, signer);
                var callJson = mapper.writeValueAsString(signedBody);

                System.out.printf("  Consolidating %d from account %d...%n", balances.get(i), i);
                var submitReq = HttpRequest.newBuilder(URI.create(RPC_URL + "/call"))
                        .header("content-type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(callJson)).build();
                var resp = http.send(submitReq, HttpResponse.BodyHandlers.ofString());
                System.out.println("  Submitted: " +
                        resp.body().substring(0, Math.min(80, resp.body().length())));
            }

            // 4. Final balance check
            var finalReq = HttpRequest.newBuilder(URI.create(client.balancePath(treasuryAccount.address())))
                    .header("accept", "application/json").GET().build();
            var finalBody = http.send(finalReq, HttpResponse.BodyHandlers.ofString()).body();
            System.out.println("\nTreasury final balance: " + finalBody);

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

import org.dilithia.sdk.DilithiaCryptoAdapter;
import org.dilithia.sdk.crypto.NativeCryptoBridge;

public class SignatureVerifier {

    record VerifyRequest(String publicKey, String address, String message, String signature) {}
    record VerifyResult(boolean valid, String error) {}

    static VerifyResult verify(DilithiaCryptoAdapter crypto, VerifyRequest req) {
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

        // 3. Validate the claimed address format
        try {
            crypto.validateAddress(req.address());
        } catch (Exception e) {
            return new VerifyResult(false, "Invalid address: " + e.getMessage());
        }

        // 4. Verify the public key maps to the claimed address
        try {
            var derived = crypto.addressFromPublicKey(req.publicKey());
            if (!derived.equals(req.address())) {
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
            DilithiaCryptoAdapter crypto = new NativeCryptoBridge();

            // Generate a keypair and sign a message to test verification
            var keypair = crypto.keygen();
            var message = "Login nonce: 98765";
            var sig = crypto.signMessage(keypair.secretKey(), message);
            var address = crypto.addressFromPublicKey(keypair.publicKey());

            System.out.println("Testing with generated keypair:");
            System.out.println("  Address:   " + address);
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

import org.dilithia.sdk.DilithiaCryptoAdapter;
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
            DilithiaCryptoAdapter crypto = new NativeCryptoBridge();

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
                System.out.println("Address:    " + account.address());
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
                System.out.println("Recovered address:    " + account.address());
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

import org.dilithia.sdk.DilithiaClient;
import org.dilithia.sdk.DilithiaCryptoAdapter;
import org.dilithia.sdk.DilithiaSigner;
import org.dilithia.sdk.crypto.NativeCryptoBridge;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

public class GasSponsoredMint {
    static final String RPC_URL = "https://rpc.dilithia.network/rpc";
    static final String SPONSOR_CONTRACT = "dil1_gas_sponsor_v1";
    static final String PAYMASTER = "dil1_paymaster_addr";
    static final String TARGET_CONTRACT = "dil1_nft_mint";

    public static void main(String[] args) {
        try {
            var mapper = new ObjectMapper();
            var http = HttpClient.newHttpClient();
            var client = new DilithiaClient(RPC_URL);
            DilithiaCryptoAdapter crypto = new NativeCryptoBridge();

            // 1. Recover the user's wallet
            var mnemonic = System.getenv("USER_MNEMONIC");
            if (mnemonic == null || mnemonic.isBlank()) {
                throw new IllegalStateException("Set USER_MNEMONIC env var");
            }
            var account = crypto.recoverHdWallet(mnemonic);
            System.out.println("User address: " + account.address());

            // 2. Check if the sponsor accepts this call by querying the contract
            var acceptArgsJson = mapper.writeValueAsString(
                    Map.of("user", account.address(), "contract", TARGET_CONTRACT, "method", "mint"));
            var acceptUrl = client.queryContractPath(SPONSOR_CONTRACT, "accept", acceptArgsJson);
            var acceptReq = HttpRequest.newBuilder(URI.create(acceptUrl))
                    .header("accept", "application/json").GET().build();
            var acceptResp = http.send(acceptReq, HttpResponse.BodyHandlers.ofString()).body();
            System.out.println("Sponsor accepts: " + acceptResp);

            // 3. Check remaining quota
            var quotaArgsJson = mapper.writeValueAsString(Map.of("user", account.address()));
            var quotaUrl = client.queryContractPath(SPONSOR_CONTRACT, "remaining_quota", quotaArgsJson);
            var quotaReq = HttpRequest.newBuilder(URI.create(quotaUrl))
                    .header("accept", "application/json").GET().build();
            var quotaResp = http.send(quotaReq, HttpResponse.BodyHandlers.ofString()).body();
            System.out.println("Remaining quota: " + quotaResp);

            // 4. Build the contract call with paymaster
            var call = client.buildContractCall(TARGET_CONTRACT, "mint",
                    Map.of("token_id", "nft_001", "metadata", "ipfs://QmSomeHash"),
                    PAYMASTER);

            // 5. Sign and submit
            DilithiaSigner signer = (payload) -> {
                var sig = crypto.signMessage(account.secretKey(), mapper.writeValueAsString(payload));
                Map<String, Object> sigMap = new LinkedHashMap<>();
                sigMap.put("algorithm", sig.algorithm());
                sigMap.put("signature", sig.signature());
                return sigMap;
            };
            var signedBody = client.sendSignedCallBody(call, signer);
            var body = mapper.writeValueAsString(signedBody);

            var submitReq = HttpRequest.newBuilder(URI.create(RPC_URL + "/call"))
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            var resp = http.send(submitReq, HttpResponse.BodyHandlers.ofString());
            System.out.println("Sponsored tx submitted: " + resp.body());

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

import org.dilithia.sdk.DilithiaClient;
import org.dilithia.sdk.DilithiaCryptoAdapter;
import org.dilithia.sdk.DilithiaSigner;
import org.dilithia.sdk.crypto.NativeCryptoBridge;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

public class CrossChainSender {
    static final String RPC_URL = "https://rpc.dilithia.network/rpc";
    static final String MESSAGING_CONTRACT = "dil1_bridge_v1";
    static final String PAYMASTER = "dil1_bridge_paymaster";
    static final String DEST_CHAIN = "dilithia-testnet-2";

    public static void main(String[] args) {
        try {
            var mapper = new ObjectMapper();
            var http = HttpClient.newHttpClient();
            var client = new DilithiaClient(RPC_URL);
            DilithiaCryptoAdapter crypto = new NativeCryptoBridge();

            // 1. Recover sender wallet
            var mnemonic = System.getenv("SENDER_MNEMONIC");
            if (mnemonic == null || mnemonic.isBlank()) {
                throw new IllegalStateException("Set SENDER_MNEMONIC env var");
            }
            var account = crypto.recoverHdWallet(mnemonic);
            System.out.println("Sender address: " + account.address());

            // 2. Build the cross-chain message payload
            Map<String, Object> messagePayload = new LinkedHashMap<>();
            messagePayload.put("action", "lock_tokens");
            messagePayload.put("sender", account.address());
            messagePayload.put("amount", 50_000);
            messagePayload.put("recipient", "dil1_remote_recipient");

            // 3. Build the send_message contract call with paymaster
            Map<String, Object> sendArgs = new LinkedHashMap<>();
            sendArgs.put("dest_chain", DEST_CHAIN);
            sendArgs.put("payload", messagePayload);
            var messageCall = client.buildContractCall(
                    MESSAGING_CONTRACT, "send_message", sendArgs, PAYMASTER);
            System.out.println("Message call built: " + messageCall);

            // 4. Sign and submit
            DilithiaSigner signer = (canonicalPayload) -> {
                var sig = crypto.signMessage(
                        account.secretKey(), mapper.writeValueAsString(canonicalPayload));
                Map<String, Object> sigMap = new LinkedHashMap<>();
                sigMap.put("algorithm", sig.algorithm());
                sigMap.put("signature", sig.signature());
                return sigMap;
            };
            var signedBody = client.sendSignedCallBody(messageCall, signer);
            var body = mapper.writeValueAsString(signedBody);

            var submitReq = HttpRequest.newBuilder(URI.create(RPC_URL + "/call"))
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            var resp = http.send(submitReq, HttpResponse.BodyHandlers.ofString());
            System.out.println("Message tx submitted: " + resp.body());

            // 5. Poll for receipt
            @SuppressWarnings("unchecked")
            var submitted = mapper.readValue(resp.body(), Map.class);
            var txHash = (String) submitted.get("tx_hash");

            for (int attempt = 0; attempt < 12; attempt++) {
                try {
                    var receiptReq = HttpRequest.newBuilder(URI.create(client.receiptPath(txHash)))
                            .header("accept", "application/json").GET().build();
                    var receiptResp = http.send(receiptReq, HttpResponse.BodyHandlers.ofString());
                    if (receiptResp.statusCode() == 200) {
                        System.out.println("Message confirmed: " + receiptResp.body());
                        return;
                    }
                } catch (Exception ignored) {
                    // Receipt not ready yet
                }
                Thread.sleep(1_000);
            }
            System.err.println("Receipt not available after polling.");

        } catch (Exception e) {
            System.err.println("Fatal error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
```
