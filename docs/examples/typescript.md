# TypeScript Examples

Complete, runnable TypeScript examples for common Dilithia SDK tasks.

> **Async vs Sync adapters:** The SDK provides two crypto adapters:
>
> - **`loadNativeCryptoAdapter()`** (async) — returns a `DilithiaCryptoAdapter` where every method returns a `Promise`. Best for server applications and anywhere you are already using async/await.
> - **`loadSyncNativeCryptoAdapter()`** (sync) — returns a `SyncDilithiaCryptoAdapter` where every method returns its value directly. Best for CLI tools, scripts, and synchronous code paths. Note that HTTP/network calls (e.g. `client.getBalance()`) still require `await`; only the local crypto operations become synchronous.
>
> Most examples below use the async adapter. Scenarios 1 and 4 include an additional sync variant to demonstrate the pattern.

## Prerequisites

```bash
npm install @dilithia/sdk-node @dilithia/sdk-native
```

---

## 1. Balance Monitor Bot

Recover a wallet from a saved mnemonic, check its token balance, and automatically send tokens to a destination address when the balance exceeds a threshold. Useful for automated treasury sweeps or payment bots.

```typescript
import {
  DilithiaClient,
  loadNativeCryptoAdapter,
  type DilithiaCryptoAdapter,
  type DilithiaAccount,
  type Balance,
  type SimulationResult,
  type SubmitResult,
  type Receipt,
  DilithiaError,
} from "@dilithia/sdk-node";

const RPC_URL = "https://rpc.dilithia.network/rpc";
const TOKEN_CONTRACT = "dil1_token_main";
const DESTINATION = "dil1_recipient_address";
const THRESHOLD = 500_000;
const SEND_AMOUNT = 100_000;

function signerFor(crypto: DilithiaCryptoAdapter, account: DilithiaAccount) {
  return {
    async signCanonicalPayload(payloadJson: string) {
      const sig = await crypto.signMessage(account.secretKey, payloadJson);
      return { algorithm: sig.algorithm, signature: sig.signature };
    },
  };
}

async function main() {
  // 1. Initialize client and crypto adapter
  const client = new DilithiaClient({ rpcUrl: RPC_URL, timeoutMs: 15_000 });
  const crypto = await loadNativeCryptoAdapter();
  if (!crypto) throw new DilithiaError("Native crypto adapter unavailable");

  // 2. Recover wallet from saved mnemonic
  const mnemonic = process.env.BOT_MNEMONIC;
  if (!mnemonic) throw new DilithiaError("Set BOT_MNEMONIC env var");
  const account = await crypto.recoverHdWallet(mnemonic);
  console.log(`Bot address: ${account.address}`);

  // 3. Check current balance — returns typed Balance with .balance as TokenAmount
  const balanceResult: Balance = await client.getBalance(account.address);
  const balance = balanceResult.balance.toRaw();
  console.log(`Current balance: ${balanceResult.balance.formatted()}`);

  if (balance < THRESHOLD) {
    console.log(`Balance ${balance} below threshold ${THRESHOLD}. Nothing to do.`);
    return;
  }

  // 4. Build a contract call to transfer tokens
  const call = client.buildContractCall(TOKEN_CONTRACT, "transfer", {
    to: DESTINATION,
    amount: SEND_AMOUNT,
  });

  // 5. Simulate first to verify it would succeed — returns typed SimulationResult
  const simResult: SimulationResult = await client.simulate(call);
  console.log("Simulation result:", simResult);

  // 6. Sign and submit the call — returns a SubmitResult with .txHash
  const submitted: SubmitResult = await client.sendSignedCall(call, signerFor(crypto, account));
  console.log(`Transaction submitted: ${submitted.txHash}`);

  // 7. Poll for receipt — returns typed Receipt
  const receipt: Receipt = await client.waitForReceipt(submitted.txHash, 20, 2_000);
  console.log(`Confirmed in block ${receipt.blockHeight}, status: ${receipt.status}`);
  console.log(`Gas used: ${receipt.gasUsed}, fee paid: ${receipt.feePaid}`);
}

main().catch((err) => {
  if (err instanceof DilithiaError) {
    console.error("Bot error:", err.message);
  } else {
    console.error("Unexpected error:", err);
  }
  process.exit(1);
});
```

### Sync version

For scripts and CLI tools where async is unnecessary:

```typescript
import {
  DilithiaClient,
  loadSyncNativeCryptoAdapter,
  type SyncDilithiaCryptoAdapter,
  type DilithiaAccount,
  type Balance,
  type SimulationResult,
  type SubmitResult,
  type Receipt,
  DilithiaError,
} from "@dilithia/sdk-node";

const RPC_URL = "https://rpc.dilithia.network/rpc";
const TOKEN_CONTRACT = "dil1_token_main";
const DESTINATION = "dil1_recipient_address";
const THRESHOLD = 500_000;
const SEND_AMOUNT = 100_000;

function signerFor(crypto: SyncDilithiaCryptoAdapter, account: DilithiaAccount) {
  return {
    // Network calls still need async, but signing itself is synchronous
    signCanonicalPayload(payloadJson: string) {
      const sig = crypto.signMessage(account.secretKey, payloadJson);
      return { algorithm: sig.algorithm, signature: sig.signature };
    },
  };
}

async function main() {
  const client = new DilithiaClient({ rpcUrl: RPC_URL, timeoutMs: 15_000 });

  // Crypto adapter is loaded synchronously — no await needed
  const crypto = loadSyncNativeCryptoAdapter();
  if (!crypto) throw new DilithiaError("Native crypto adapter unavailable");

  // Crypto operations return values directly — no await
  const mnemonic = process.env.BOT_MNEMONIC;
  if (!mnemonic) throw new DilithiaError("Set BOT_MNEMONIC env var");
  const account = crypto.recoverHdWallet(mnemonic);
  console.log(`Bot address: ${account.address}`);

  // Network calls still require await — returns typed Balance
  const balanceResult: Balance = await client.getBalance(account.address);
  const balance = balanceResult.balance.toRaw();
  console.log(`Current balance: ${balanceResult.balance.formatted()}`);

  if (balance < THRESHOLD) {
    console.log(`Balance ${balance} below threshold ${THRESHOLD}. Nothing to do.`);
    return;
  }

  const call = client.buildContractCall(TOKEN_CONTRACT, "transfer", {
    to: DESTINATION,
    amount: SEND_AMOUNT,
  });

  const simResult: SimulationResult = await client.simulate(call);
  console.log("Simulation result:", simResult);

  // Returns a typed SubmitResult with .txHash
  const submitted: SubmitResult = await client.sendSignedCall(call, signerFor(crypto, account));
  console.log(`Transaction submitted: ${submitted.txHash}`);

  const receipt: Receipt = await client.waitForReceipt(submitted.txHash, 20, 2_000);
  console.log(`Confirmed in block ${receipt.blockHeight}, status: ${receipt.status}`);
}

main().catch((err) => {
  if (err instanceof DilithiaError) {
    console.error("Bot error:", err.message);
  } else {
    console.error("Unexpected error:", err);
  }
  process.exit(1);
});
```

---

## 2. Multi-Account Treasury

Derive multiple HD wallet accounts from a single mnemonic, check each account's balance, and consolidate all funds into the primary account. Useful for services that manage many sub-accounts under one seed.

```typescript
import {
  DilithiaClient,
  loadNativeCryptoAdapter,
  type DilithiaCryptoAdapter,
  type DilithiaAccount,
  type Balance,
  type SubmitResult,
  type Receipt,
  DilithiaError,
  RpcError,
} from "@dilithia/sdk-node";

const RPC_URL = "https://rpc.dilithia.network/rpc";
const TOKEN_CONTRACT = "dil1_token_main";
const NUM_ACCOUNTS = 5;

function signerFor(crypto: DilithiaCryptoAdapter, account: DilithiaAccount) {
  return {
    async signCanonicalPayload(payloadJson: string) {
      const sig = await crypto.signMessage(account.secretKey, payloadJson);
      return { algorithm: sig.algorithm, signature: sig.signature };
    },
  };
}

async function main() {
  const client = new DilithiaClient({ rpcUrl: RPC_URL });
  const crypto = await loadNativeCryptoAdapter();
  if (!crypto) throw new DilithiaError("Native crypto adapter unavailable");

  const mnemonic = process.env.TREASURY_MNEMONIC;
  if (!mnemonic) throw new DilithiaError("Set TREASURY_MNEMONIC env var");

  // 1. Derive all accounts from the same mnemonic
  const accounts: DilithiaAccount[] = [];
  for (let i = 0; i < NUM_ACCOUNTS; i++) {
    const acct = await crypto.recoverHdWalletAccount(mnemonic, i);
    accounts.push(acct);
  }
  const treasuryAddress = accounts[0].address;
  console.log(`Treasury address (account 0): ${treasuryAddress}`);

  // 2. Check balances for every account — getBalance returns typed Balance
  const balances: number[] = [];
  for (const acct of accounts) {
    const result: Balance = await client.getBalance(acct.address);
    const bal = result.balance.toRaw();
    balances.push(bal);
    console.log(`  Account ${acct.accountIndex}: ${acct.address} -> ${result.balance.formatted()}`);
  }

  // 3. Consolidate: transfer from accounts 1-4 to account 0
  for (let i = 1; i < NUM_ACCOUNTS; i++) {
    if (balances[i] <= 0) {
      console.log(`  Account ${i}: zero balance, skipping.`);
      continue;
    }

    const call = client.buildContractCall(TOKEN_CONTRACT, "transfer", {
      to: treasuryAddress,
      amount: balances[i],
    });

    try {
      console.log(`  Consolidating ${balances[i]} from account ${i}...`);
      const submitted: SubmitResult = await client.sendSignedCall(call, signerFor(crypto, accounts[i]));
      const receipt: Receipt = await client.waitForReceipt(submitted.txHash);
      console.log(`  Done. Block: ${receipt.blockHeight}, status: ${receipt.status}`);
    } catch (err) {
      if (err instanceof RpcError) {
        console.error(`  RPC error consolidating account ${i}: ${err.message}`);
      } else {
        console.error(`  Failed to consolidate account ${i}:`, err);
      }
    }
  }

  // 4. Final balance check on treasury
  const finalBalance: Balance = await client.getBalance(treasuryAddress);
  console.log(`\nTreasury final balance: ${finalBalance.balance.formatted()}`);
}

main().catch(console.error);
```

---

## 3. Signature Verification Service

Validate an incoming signed message by checking the public key format, signature format, address derivation, and cryptographic signature. Useful for authentication endpoints or verifying off-chain attestations.

```typescript
import {
  loadNativeCryptoAdapter,
  type DilithiaCryptoAdapter,
  DilithiaError,
} from "@dilithia/sdk-node";

interface VerifyRequest {
  publicKey: string;   // hex-encoded ML-DSA public key
  address: string;     // claimed Dilithia address
  message: string;     // the original plaintext message
  signature: string;   // hex-encoded signature
}

interface VerifyResult {
  valid: boolean;
  error?: string;
}

async function verifySignedMessage(
  crypto: DilithiaCryptoAdapter,
  req: VerifyRequest,
): Promise<VerifyResult> {
  // 1. Validate the public key format
  try {
    await crypto.validatePublicKey(req.publicKey);
  } catch {
    return { valid: false, error: "Invalid public key format" };
  }

  // 2. Validate the signature format
  try {
    await crypto.validateSignature(req.signature);
  } catch {
    return { valid: false, error: "Invalid signature format" };
  }

  // 3. Validate the claimed address format
  try {
    await crypto.validateAddress(req.address);
  } catch {
    return { valid: false, error: "Invalid address format" };
  }

  // 4. Verify that the public key maps to the claimed address
  const derivedAddress = await crypto.addressFromPublicKey(req.publicKey);
  if (derivedAddress !== req.address) {
    return { valid: false, error: "Address does not match public key" };
  }

  // 5. Verify the cryptographic signature
  const isValid = await crypto.verifyMessage(
    req.publicKey,
    req.message,
    req.signature,
  );
  if (!isValid) {
    return { valid: false, error: "Signature verification failed" };
  }

  return { valid: true };
}

async function main() {
  const crypto = await loadNativeCryptoAdapter();
  if (!crypto) throw new DilithiaError("Native crypto adapter unavailable");

  const result = await verifySignedMessage(crypto, {
    publicKey: "abcd1234...",
    address: "dil1_abc123",
    message: "Login nonce: 98765",
    signature: "deadbeef...",
  });

  if (result.valid) {
    console.log("Signature is valid. User authenticated.");
  } else {
    console.error("Verification failed:", result.error);
  }
}

main().catch(console.error);
```

---

## 4. Wallet Backup and Recovery

Create a new HD wallet with an encrypted wallet file, save it to disk, and recover it later using the mnemonic and password. Covers the full wallet lifecycle from creation through serialization to recovery.

```typescript
import { writeFileSync, readFileSync, existsSync } from "node:fs";
import {
  loadNativeCryptoAdapter,
  type WalletFile,
  DilithiaError,
} from "@dilithia/sdk-node";

const WALLET_PATH = "./my-wallet.json";
const PASSWORD = "my-secure-passphrase";

async function main() {
  const crypto = await loadNativeCryptoAdapter();
  if (!crypto) throw new DilithiaError("Native crypto adapter unavailable");

  if (!existsSync(WALLET_PATH)) {
    // ---- CREATE NEW WALLET ----
    console.log("No wallet found. Creating a new one...");

    // 1. Generate a fresh mnemonic
    const mnemonic = await crypto.generateMnemonic();
    console.log("SAVE THIS MNEMONIC SECURELY:");
    console.log(mnemonic);
    console.log();

    // 2. Create an encrypted wallet file from the mnemonic
    const account = await crypto.createHdWalletFileFromMnemonic(mnemonic, PASSWORD);
    console.log(`Address: ${account.address}`);
    console.log(`Public key: ${account.publicKey}`);

    // 3. Serialize the wallet file to JSON and save to disk
    if (!account.walletFile) throw new DilithiaError("Wallet file not generated");
    const walletJson = JSON.stringify(account.walletFile, null, 2);
    writeFileSync(WALLET_PATH, walletJson, "utf-8");
    console.log(`Wallet saved to ${WALLET_PATH}`);
  } else {
    // ---- RECOVER EXISTING WALLET ----
    console.log("Wallet file found. Recovering...");

    // 4. Read the wallet file from disk
    const walletJson = readFileSync(WALLET_PATH, "utf-8");
    const walletFile: WalletFile = JSON.parse(walletJson);

    // 5. Recover using mnemonic + password
    const mnemonic = process.env.WALLET_MNEMONIC;
    if (!mnemonic) throw new DilithiaError("Set WALLET_MNEMONIC env var to recover");

    const account = await crypto.recoverWalletFile(walletFile, mnemonic, PASSWORD);
    console.log(`Recovered address: ${account.address}`);
    console.log(`Public key: ${account.publicKey}`);
    console.log("Wallet recovered successfully. Ready to sign transactions.");
  }
}

main().catch(console.error);
```

### Sync version

For scripts and CLI tools where async is unnecessary:

```typescript
import { writeFileSync, readFileSync, existsSync } from "node:fs";
import {
  loadSyncNativeCryptoAdapter,
  type WalletFile,
  DilithiaError,
} from "@dilithia/sdk-node";

const WALLET_PATH = "./my-wallet.json";
const PASSWORD = "my-secure-passphrase";

function main() {
  // Crypto adapter is loaded synchronously — no await, no async function needed
  const crypto = loadSyncNativeCryptoAdapter();
  if (!crypto) throw new DilithiaError("Native crypto adapter unavailable");

  if (!existsSync(WALLET_PATH)) {
    // ---- CREATE NEW WALLET ----
    console.log("No wallet found. Creating a new one...");

    // All crypto calls return values directly — no await
    const mnemonic = crypto.generateMnemonic();
    console.log("SAVE THIS MNEMONIC SECURELY:");
    console.log(mnemonic);
    console.log();

    const account = crypto.createHdWalletFileFromMnemonic(mnemonic, PASSWORD);
    console.log(`Address: ${account.address}`);
    console.log(`Public key: ${account.publicKey}`);

    if (!account.walletFile) throw new DilithiaError("Wallet file not generated");
    const walletJson = JSON.stringify(account.walletFile, null, 2);
    writeFileSync(WALLET_PATH, walletJson, "utf-8");
    console.log(`Wallet saved to ${WALLET_PATH}`);
  } else {
    // ---- RECOVER EXISTING WALLET ----
    console.log("Wallet file found. Recovering...");

    const walletJson = readFileSync(WALLET_PATH, "utf-8");
    const walletFile: WalletFile = JSON.parse(walletJson);

    const mnemonic = process.env.WALLET_MNEMONIC;
    if (!mnemonic) throw new DilithiaError("Set WALLET_MNEMONIC env var to recover");

    const account = crypto.recoverWalletFile(walletFile, mnemonic, PASSWORD);
    console.log(`Recovered address: ${account.address}`);
    console.log(`Public key: ${account.publicKey}`);
    console.log("Wallet recovered successfully. Ready to sign transactions.");
  }
}

main();
```

---

## 5. Gas-Sponsored Transaction

Submit a transaction where a sponsor contract pays the gas fee instead of the sender. Ideal for onboarding new users who have no tokens yet, or for dApps that want to subsidize user interactions.

```typescript
import {
  DilithiaClient,
  DilithiaGasSponsorConnector,
  loadNativeCryptoAdapter,
  type DilithiaCryptoAdapter,
  type DilithiaAccount,
  type SubmitResult,
  type Receipt,
  DilithiaError,
  RpcError,
} from "@dilithia/sdk-node";

const RPC_URL = "https://rpc.dilithia.network/rpc";
const SPONSOR_CONTRACT = "dil1_gas_sponsor_v1";
const PAYMASTER_ADDRESS = "dil1_paymaster_addr";
const TARGET_CONTRACT = "dil1_nft_mint";

function signerFor(crypto: DilithiaCryptoAdapter, account: DilithiaAccount) {
  return {
    async signCanonicalPayload(payloadJson: string) {
      const sig = await crypto.signMessage(account.secretKey, payloadJson);
      return { algorithm: sig.algorithm, signature: sig.signature };
    },
  };
}

async function main() {
  const client = new DilithiaClient({ rpcUrl: RPC_URL });
  const crypto = await loadNativeCryptoAdapter();
  if (!crypto) throw new DilithiaError("Crypto adapter unavailable");

  // 1. Recover the user's wallet (new user with zero balance)
  const mnemonic = process.env.USER_MNEMONIC;
  if (!mnemonic) throw new DilithiaError("Set USER_MNEMONIC env var");
  const account = await crypto.recoverHdWallet(mnemonic);
  console.log(`User address: ${account.address}`);

  // 2. Set up the gas sponsor connector
  const sponsor = new DilithiaGasSponsorConnector({
    client,
    sponsorContract: SPONSOR_CONTRACT,
    paymaster: PAYMASTER_ADDRESS,
  });

  // 3. Check if the sponsor will accept this call
  const acceptQuery = sponsor.buildAcceptQuery(
    account.address, TARGET_CONTRACT, "mint",
  );
  const acceptResult = await client.queryContract(
    SPONSOR_CONTRACT, "accept", acceptQuery.args,
  );
  console.log("Sponsor accepts:", acceptResult);

  // 4. Check remaining gas quota for this user
  const quotaQuery = sponsor.buildRemainingQuotaQuery(account.address);
  const quotaResult = await client.queryContract(
    SPONSOR_CONTRACT, "remaining_quota", quotaQuery.args,
  );
  console.log("Remaining quota:", quotaResult);

  // 5. Build the actual contract call
  const call = client.buildContractCall(TARGET_CONTRACT, "mint", {
    token_id: "nft_001",
    metadata: "ipfs://QmSomeHash",
  });

  // 6. Simulate the sponsored call
  const simResult = await sponsor.simulateSponsoredCall(call);
  console.log("Simulation:", simResult);

  // 7. Sign and submit the sponsored call (paymaster applied automatically)
  const submitted: SubmitResult = await sponsor.sendSponsoredCall(
    call, signerFor(crypto, account),
  );
  console.log(`Sponsored tx submitted: ${submitted.txHash}`);

  // 8. Wait for confirmation — returns typed Receipt
  const receipt: Receipt = await client.waitForReceipt(submitted.txHash);
  console.log(`Confirmed in block ${receipt.blockHeight}, status: ${receipt.status}`);
}

main().catch((err) => {
  if (err instanceof RpcError) {
    console.error("RPC error:", err.message);
  } else if (err instanceof DilithiaError) {
    console.error("SDK error:", err.message);
  } else {
    console.error("Unexpected error:", err);
  }
});
```

---

## 6. Cross-Chain Message

Send a message to another Dilithia chain via the messaging connector. Useful for bridging tokens, triggering remote contract actions, or synchronizing state across chains.

```typescript
import {
  DilithiaClient,
  DilithiaMessagingConnector,
  loadNativeCryptoAdapter,
  type DilithiaCryptoAdapter,
  type DilithiaAccount,
  type SubmitResult,
  type Receipt,
  DilithiaError,
  HttpError,
} from "@dilithia/sdk-node";

const RPC_URL = "https://rpc.dilithia.network/rpc";
const MESSAGING_CONTRACT = "dil1_bridge_v1";
const PAYMASTER = "dil1_bridge_paymaster";
const DEST_CHAIN = "dilithia-testnet-2";

function signerFor(crypto: DilithiaCryptoAdapter, account: DilithiaAccount) {
  return {
    async signCanonicalPayload(payloadJson: string) {
      const sig = await crypto.signMessage(account.secretKey, payloadJson);
      return { algorithm: sig.algorithm, signature: sig.signature };
    },
  };
}

async function main() {
  const client = new DilithiaClient({ rpcUrl: RPC_URL });
  const crypto = await loadNativeCryptoAdapter();
  if (!crypto) throw new DilithiaError("Crypto adapter unavailable");

  const mnemonic = process.env.BRIDGE_MNEMONIC;
  if (!mnemonic) throw new DilithiaError("Set BRIDGE_MNEMONIC env var");
  const account = await crypto.recoverHdWallet(mnemonic);
  console.log(`Sender address: ${account.address}`);

  // 1. Set up the messaging connector
  const messaging = new DilithiaMessagingConnector({
    client,
    messagingContract: MESSAGING_CONTRACT,
    paymaster: PAYMASTER,
  });

  // 2. Check current outbox state
  const outbox = await messaging.queryOutbox();
  console.log("Current outbox:", outbox);

  // 3. Build the cross-chain message payload
  const payload = {
    action: "lock_tokens",
    sender: account.address,
    amount: 50_000,
    recipient: "dil1_remote_recipient",
    timestamp: Date.now(),
  };

  // 4. Preview the message call structure
  const messageCall = messaging.buildSendMessageCall(DEST_CHAIN, payload);
  console.log("Message call:", messageCall);

  // 5. Simulate the message send
  const simResult = await client.simulate(messageCall);
  console.log("Simulation:", simResult);

  // 6. Sign and send the message — returns typed SubmitResult
  const submitted: SubmitResult = await messaging.sendMessage(
    DEST_CHAIN, payload, signerFor(crypto, account),
  );
  console.log(`Message tx submitted: ${submitted.txHash}`);

  // 7. Wait for confirmation — returns typed Receipt
  const receipt: Receipt = await client.waitForReceipt(submitted.txHash);
  console.log(`Message confirmed in block ${receipt.blockHeight}, status: ${receipt.status}`);

  // 8. Verify message appears in outbox
  const updatedOutbox = await messaging.queryOutbox();
  console.log("Updated outbox:", updatedOutbox);
}

main().catch((err) => {
  if (err instanceof HttpError) {
    console.error("HTTP error:", err.message);
  } else if (err instanceof DilithiaError) {
    console.error("SDK error:", err.message);
  } else {
    console.error("Unexpected error:", err);
  }
});
```

---

## 7. Contract Deployment

Deploy a WASM smart contract to the Dilithia chain. This is the most involved workflow: read the WASM binary, hash the bytecode, build and sign a canonical deploy payload, assemble the full `DeployPayload`, send the deploy request, and wait for confirmation.

```typescript
import {
  DilithiaClient,
  loadNativeCryptoAdapter,
  readWasmFileHex,
  type DeployPayload,
  type Nonce,
  type SubmitResult,
  type Receipt,
  DilithiaError,
  RpcError,
} from "@dilithia/sdk-node";

const RPC_URL = "https://rpc.dilithia.network/rpc";
const CONTRACT_NAME = "my_contract";
const WASM_PATH = "./my_contract.wasm";
const CHAIN_ID = "dilithia-mainnet";

async function main() {
  // 1. Initialize client and crypto adapter
  const client = new DilithiaClient({ rpcUrl: RPC_URL, timeoutMs: 30_000 });
  const crypto = await loadNativeCryptoAdapter();
  if (!crypto) throw new DilithiaError("Native crypto adapter unavailable");

  // 2. Recover wallet from mnemonic
  const mnemonic = process.env.DEPLOYER_MNEMONIC;
  if (!mnemonic) throw new DilithiaError("Set DEPLOYER_MNEMONIC env var");
  const account = await crypto.recoverHdWallet(mnemonic);
  console.log(`Deployer address: ${account.address}`);

  // 3. Read the WASM file as hex
  const bytecodeHex = readWasmFileHex(WASM_PATH);
  console.log(`Bytecode size: ${bytecodeHex.length / 2} bytes`);

  // 4. Get the current nonce from the node — returns typed Nonce with .nextNonce
  const nonceResult: Nonce = await client.getNonce(account.address);
  const nonce = nonceResult.nextNonce;
  console.log(`Current nonce: ${nonce}`);

  // 5. Hash the bytecode hex for the canonical payload
  const bytecodeHash = await crypto.hashHex(bytecodeHex);
  console.log(`Bytecode hash: ${bytecodeHash}`);

  // 6. Build the canonical deploy payload (keys sorted for deterministic signing)
  const canonical = client.buildDeployCanonicalPayload(
    account.address,
    CONTRACT_NAME,
    bytecodeHash,
    nonce,
    CHAIN_ID,
  );
  console.log("Canonical payload:", canonical);

  // 7. Sign the canonical payload
  const canonicalJson = JSON.stringify(canonical);
  const sig = await crypto.signMessage(account.secretKey, canonicalJson);
  console.log(`Signed with algorithm: ${sig.algorithm}`);

  // 8. Assemble the full DeployPayload
  const deployPayload: DeployPayload = {
    name: CONTRACT_NAME,
    bytecode: bytecodeHex,
    from: account.address,
    alg: sig.algorithm,
    pk: account.publicKey,
    sig: sig.signature,
    nonce,
    chainId: CHAIN_ID,
    version: 1,
  };

  // 9. Deploy the contract — returns typed SubmitResult
  const submitted: SubmitResult = await client.deployContract(deployPayload);
  console.log(`Deploy tx submitted: ${submitted.txHash}`);

  // 10. Wait for the receipt — returns typed Receipt
  const receipt: Receipt = await client.waitForReceipt(submitted.txHash, 30, 3_000);
  console.log(`Contract deployed in block ${receipt.blockHeight}, status: ${receipt.status}`);
  console.log(`Gas used: ${receipt.gasUsed}, fee paid: ${receipt.feePaid}`);
}

main().catch((err) => {
  if (err instanceof RpcError) {
    console.error("RPC error:", err.message);
  } else if (err instanceof DilithiaError) {
    console.error("Deploy error:", err.message);
  } else {
    console.error("Unexpected error:", err);
  }
  process.exit(1);
});
```

---

## 8. Shielded Pool Deposit & Withdraw

Deposit tokens into the shielded pool for privacy and later withdraw them to a recipient address. This privacy-focused workflow covers ZK adapter loading, commitment computation, shielded deposit, nullifier computation, commitment root query, shielded withdraw, and nullifier spent check.

```typescript
import {
  DilithiaClient,
  loadNativeCryptoAdapter,
  loadNativeZkAdapter,
  type DilithiaCryptoAdapter,
  type DilithiaZkAdapter,
  type DilithiaAccount,
  type SubmitResult,
  type Receipt,
  type Address,
  type TxHash,
  type TokenAmount,
  DilithiaError,
  RpcError,
} from "@dilithia/sdk-node";

const RPC_URL = "https://rpc.dilithia.network/rpc";
const DEPOSIT_AMOUNT = 10_000;
const WITHDRAW_AMOUNT = 10_000;
const RECIPIENT: Address = "dil1_recipient_address";

function signerFor(crypto: DilithiaCryptoAdapter, account: DilithiaAccount) {
  return {
    async signCanonicalPayload(payloadJson: string) {
      const sig = await crypto.signMessage(account.secretKey, payloadJson);
      return { algorithm: sig.algorithm, signature: sig.signature };
    },
  };
}

async function main() {
  // 1. Initialize client and load native crypto + ZK adapters
  const client = new DilithiaClient({ rpcUrl: RPC_URL, timeoutMs: 30_000 });
  const crypto = await loadNativeCryptoAdapter();
  if (!crypto) throw new DilithiaError("Native crypto adapter unavailable");
  const zkAdapter = await loadNativeZkAdapter();
  if (!zkAdapter) throw new DilithiaError("Native ZK adapter unavailable");

  // 2. Recover wallet from mnemonic
  const mnemonic = process.env.SHIELDED_MNEMONIC;
  if (!mnemonic) throw new DilithiaError("Set SHIELDED_MNEMONIC env var");
  const account = await crypto.recoverHdWallet(mnemonic);
  console.log(`Wallet address: ${account.address}`);

  // 3. Generate random secret and nonce as hex strings
  const secretHex = await crypto.hashHex("secret_seed");
  const nonceHex = await crypto.hashHex("nonce_seed");
  console.log(`Secret: ${secretHex}`);
  console.log(`Nonce: ${nonceHex}`);

  // 4. Compute commitment using Poseidon hash
  const commitment = await zkAdapter.computeCommitment(DEPOSIT_AMOUNT, secretHex, nonceHex);
  console.log(`Commitment hash: ${commitment.hash}`);

  // 5. Generate a preimage proof for the deposit
  const depositProof = await zkAdapter.generatePreimageProof([DEPOSIT_AMOUNT]);
  console.log("Preimage proof generated for deposit");

  // 6. Deposit into shielded pool — returns typed SubmitResult
  const depositResult: SubmitResult = await client.shieldedDeposit(
    commitment.hash,
    DEPOSIT_AMOUNT,
    depositProof.proof,
  );
  console.log(`Deposit tx submitted: ${depositResult.txHash}`);

  // 7. Wait for receipt — returns typed Receipt
  const depositReceipt: Receipt = await client.waitForReceipt(depositResult.txHash, 20, 2_000);
  console.log(`Deposit confirmed in block ${depositReceipt.blockHeight}, status: ${depositReceipt.status}`);

  // 8. Compute nullifier from secret and nonce
  const nullifier = await zkAdapter.computeNullifier(secretHex, nonceHex);
  console.log(`Nullifier hash: ${nullifier.hash}`);

  // 9. Get current commitment root from the chain
  const commitmentRoot = await client.getCommitmentRoot();
  console.log(`Commitment root: ${commitmentRoot}`);

  // 10. Generate range proof for withdraw
  const withdrawProof = await zkAdapter.generateRangeProof(WITHDRAW_AMOUNT, 0, 1_000_000);
  console.log("Range proof generated for withdraw");

  // 11. Withdraw from shielded pool — returns typed SubmitResult
  const withdrawResult: SubmitResult = await client.shieldedWithdraw(
    nullifier.hash,
    WITHDRAW_AMOUNT,
    RECIPIENT,
    withdrawProof.proof,
    commitmentRoot,
  );
  console.log(`Withdraw tx submitted: ${withdrawResult.txHash}`);

  // 12. Wait for withdraw receipt
  const withdrawReceipt: Receipt = await client.waitForReceipt(withdrawResult.txHash, 20, 2_000);
  console.log(`Withdraw confirmed in block ${withdrawReceipt.blockHeight}, status: ${withdrawReceipt.status}`);

  // 13. Check that the nullifier has been spent
  const isSpent = await client.isNullifierSpent(nullifier.hash);
  console.log(`Nullifier spent: ${isSpent}`);
}

main().catch((err) => {
  if (err instanceof RpcError) {
    console.error("RPC error:", err.message);
  } else if (err instanceof DilithiaError) {
    console.error("Shielded pool error:", err.message);
  } else {
    console.error("Unexpected error:", err);
  }
  process.exit(1);
});
```

---

## 9. ZK Proof Generation & Verification

A standalone utility that demonstrates all ZK proof operations without interacting with the chain. Covers Poseidon hashing, commitment and nullifier computation, preimage proof generation and verification, and range proof generation and verification.

```typescript
import {
  loadNativeCryptoAdapter,
  loadNativeZkAdapter,
  type DilithiaZkAdapter,
  DilithiaError,
} from "@dilithia/sdk-node";

async function main() {
  // 1. Load ZK adapter
  const crypto = await loadNativeCryptoAdapter();
  if (!crypto) throw new DilithiaError("Native crypto adapter unavailable");
  const zkAdapter = await loadNativeZkAdapter();
  if (!zkAdapter) throw new DilithiaError("Native ZK adapter unavailable");

  // 2. Compute Poseidon hash of multiple values
  const poseidonResult = await zkAdapter.poseidonHash([42, 100, 7]);
  console.log(`Poseidon hash of [42, 100, 7]: ${poseidonResult}`);

  // 3. Compute a commitment from value, secret, and nonce
  const secretHex = await crypto.hashHex("secret_seed");
  const nonceHex = await crypto.hashHex("nonce_seed");
  const commitment = await zkAdapter.computeCommitment(1000, secretHex, nonceHex);
  console.log(`Commitment hash: ${commitment.hash}`);

  // 4. Compute a nullifier from secret and nonce
  const nullifier = await zkAdapter.computeNullifier(secretHex, nonceHex);
  console.log(`Nullifier hash: ${nullifier.hash}`);

  // 5. Generate preimage proof
  const preimageProof = await zkAdapter.generatePreimageProof([42, 100]);
  console.log("Preimage proof generated");
  console.log(`  Proof length: ${preimageProof.proof.length}`);

  // 6. Verify preimage proof
  const preimageValid = await zkAdapter.verifyPreimageProof(
    preimageProof.proof,
    preimageProof.vk,
    preimageProof.inputs,
  );
  console.log(`Preimage proof valid: ${preimageValid}`);

  // 7. Generate range proof (value 500 is within [0, 1000])
  const rangeProof = await zkAdapter.generateRangeProof(500, 0, 1000);
  console.log("Range proof generated");
  console.log(`  Proof length: ${rangeProof.proof.length}`);

  // 8. Verify range proof
  const rangeValid = await zkAdapter.verifyRangeProof(
    rangeProof.proof,
    rangeProof.vk,
    rangeProof.inputs,
  );
  console.log(`Range proof valid: ${rangeValid}`);

  // 9. Print summary
  console.log("\n--- Summary ---");
  console.log(`Poseidon hash:       ${poseidonResult}`);
  console.log(`Commitment hash:     ${commitment.hash}`);
  console.log(`Nullifier hash:      ${nullifier.hash}`);
  console.log(`Preimage proof valid: ${preimageValid}`);
  console.log(`Range proof valid:    ${rangeValid}`);
}

main().catch((err) => {
  if (err instanceof DilithiaError) {
    console.error("ZK error:", err.message);
  } else {
    console.error("Unexpected error:", err);
  }
  process.exit(1);
});
```
