# C#/.NET Examples

Complete, self-contained C# programs demonstrating the Dilithia SDK. Each scenario is a standalone console application with top-level statements or a `Main` entry point. All examples target .NET 8.0 and use the native crypto bridge via P/Invoke.

---

## Prerequisites

### NuGet package

```xml
<PackageReference Include="Dilithia.Sdk" Version="0.3.0" />
```

### Native library

Set the `DILITHIUM_NATIVE_CORE_LIB` environment variable to the path of the compiled
Dilithia native core shared library before running any example:

```bash
export DILITHIUM_NATIVE_CORE_LIB=/usr/local/lib/libdilithia_core.so
```

The `NativeCryptoBridge` constructor reads this variable and loads the library via P/Invoke.
If the variable is missing or blank, it throws `InvalidOperationException`.

---

## Scenario 1: Balance Monitor Bot

A bot that recovers its wallet from a mnemonic, checks its balance, and sends tokens
to a destination address when the balance exceeds a threshold. Covers: client setup,
wallet recovery, balance query, contract call construction, signing, submission, and
receipt polling.

```csharp
using Dilithia.Sdk;
using Dilithia.Sdk.Models;
using Dilithia.Sdk.Crypto;
using Dilithia.Sdk.Exceptions;

const string RpcUrl = "https://rpc.dilithia.network/rpc";
const string TokenContract = "dil1_token_main";
const string Destination = "dil1_recipient_address";
const long Threshold = 500_000;
const long SendAmount = 100_000;

try
{
    // 1. Initialize client with builder and crypto adapter
    using var client = DilithiaClient.Create(RpcUrl)
        .WithTimeout(TimeSpan.FromSeconds(15))
        .Build();
    var crypto = new NativeCryptoBridge();

    // 2. Recover wallet from saved mnemonic
    var mnemonic = Environment.GetEnvironmentVariable("BOT_MNEMONIC")
        ?? throw new InvalidOperationException("Set BOT_MNEMONIC env var");
    if (string.IsNullOrWhiteSpace(mnemonic))
        throw new InvalidOperationException("Set BOT_MNEMONIC env var");

    var account = crypto.RecoverHdWallet(mnemonic);
    Console.WriteLine($"Bot address: {account.Address}");

    // 3. Check current balance — returns Balance with typed fields
    var balance = await client.GetBalanceAsync(Address.Of(account.Address));
    Console.WriteLine($"Current balance: {balance.Value} (raw: {balance.RawValue})");

    if (balance.Value.LessThan(TokenAmount.Dili(Threshold.ToString())))
    {
        Console.WriteLine($"Balance below threshold {Threshold}. Nothing to do.");
        return;
    }

    // 4. Build a contract call to transfer tokens, sign, and submit
    DilithiaSigner signer = payload => new SignedPayload(
        "mldsa65",
        PublicKey.Of(account.PublicKey),
        crypto.SignMessage(account.SecretKey, payload).Signature);

    // 5. Send the contract call — returns Receipt directly
    var receipt = await client.Contract(TokenContract)
        .Call("transfer", new Dictionary<string, object>
        {
            ["to"] = Destination,
            ["amount"] = SendAmount
        })
        .SendAsync(signer);

    Console.WriteLine($"Confirmed at block {receipt.BlockHeight}, status: {receipt.Status}, tx: {receipt.TxHash}");
}
catch (HttpException e)
{
    Console.Error.WriteLine($"HTTP error {e.StatusCode}: {e.Message}");
    Environment.Exit(1);
}
catch (RpcException e)
{
    Console.Error.WriteLine($"RPC error {e.Code}: {e.Message}");
    Environment.Exit(1);
}
catch (DilithiaTimeoutException e)
{
    Console.Error.WriteLine($"Timeout: {e.Message}");
    Environment.Exit(1);
}
catch (Exception e)
{
    Console.Error.WriteLine($"Fatal error: {e.Message}");
    Console.Error.WriteLine(e.StackTrace);
    Environment.Exit(1);
}
```

---

## Scenario 2: Multi-Account Treasury Manager

A service that manages multiple HD wallet accounts derived from a single mnemonic.
It derives accounts 0 through 4, checks each balance, and consolidates all funds
into account 0. Covers: HD derivation loop, multiple balance queries, and batch
transaction construction.

```csharp
using Dilithia.Sdk;
using Dilithia.Sdk.Models;
using Dilithia.Sdk.Crypto;
using Dilithia.Sdk.Exceptions;

const string RpcUrl = "https://rpc.dilithia.network/rpc";
const string TokenContract = "dil1_token_main";
const int NumAccounts = 5;

try
{
    // Initialize client with builder
    using var client = DilithiaClient.Create(RpcUrl)
        .WithTimeout(TimeSpan.FromSeconds(15))
        .Build();
    var crypto = new NativeCryptoBridge();

    var mnemonic = Environment.GetEnvironmentVariable("TREASURY_MNEMONIC")
        ?? throw new InvalidOperationException("Set TREASURY_MNEMONIC env var");
    if (string.IsNullOrWhiteSpace(mnemonic))
        throw new InvalidOperationException("Set TREASURY_MNEMONIC env var");

    // 1. Derive all accounts
    var accounts = new List<DilithiaAccount>();
    for (var i = 0; i < NumAccounts; i++)
    {
        accounts.Add(crypto.RecoverHdWalletAccount(mnemonic, i));
    }
    var treasuryAccount = accounts[0];
    Console.WriteLine($"Treasury address (account 0): {treasuryAccount.Address}");

    // 2. Check balances — GetBalanceAsync returns Balance with typed fields
    var balances = new List<Balance>();
    for (var i = 0; i < NumAccounts; i++)
    {
        var bal = await client.GetBalanceAsync(Address.Of(accounts[i].Address));
        balances.Add(bal);
        Console.WriteLine($"  Account {i}: {bal.Address} -> {bal.Value}");
    }

    // 3. Consolidate from accounts 1-4 to account 0
    for (var i = 1; i < NumAccounts; i++)
    {
        if (balances[i].Value.IsZero())
        {
            Console.WriteLine($"  Account {i}: zero balance, skipping.");
            continue;
        }

        var accountIdx = i;
        DilithiaSigner signer = payload => new SignedPayload(
            "mldsa65",
            PublicKey.Of(accounts[accountIdx].PublicKey),
            crypto.SignMessage(accounts[accountIdx].SecretKey, payload).Signature);

        Console.WriteLine($"  Consolidating {balances[i].Value} from account {i}...");

        // Contract().Call().SendAsync() returns Receipt
        var receipt = await client.Contract(TokenContract)
            .Call("transfer", new Dictionary<string, object>
            {
                ["to"] = treasuryAccount.Address,
                ["amount"] = balances[i].RawValue
            })
            .SendAsync(signer);

        Console.WriteLine($"  Done. Block {receipt.BlockHeight}, status: {receipt.Status}");
    }

    // 4. Final balance check
    var finalBalance = await client.GetBalanceAsync(Address.Of(treasuryAccount.Address));
    Console.WriteLine($"\nTreasury final balance: {finalBalance.Value}");
}
catch (DilithiaException e)
{
    Console.Error.WriteLine($"SDK error: {e.Message}");
    Environment.Exit(1);
}
catch (Exception e)
{
    Console.Error.WriteLine($"Fatal error: {e.Message}");
    Console.Error.WriteLine(e.StackTrace);
    Environment.Exit(1);
}
```

---

## Scenario 3: Signature Verification Service

An API endpoint that receives a signed message and verifies the signature against the
claimed public key and address. Covers: address validation, public key validation,
signature verification, and structured error handling.

```csharp
using Dilithia.Sdk;
using Dilithia.Sdk.Models;
using Dilithia.Sdk.Crypto;

var crypto = new NativeCryptoBridge();

try
{
    // Generate a keypair and sign a message to test verification
    var keypair = crypto.Keygen();
    var message = "Login nonce: 98765";
    var sig = crypto.SignMessage(keypair.SecretKey, message);
    var address = crypto.AddressFromPublicKey(keypair.PublicKey);

    Console.WriteLine("Testing with generated keypair:");
    Console.WriteLine($"  Address:    {Address.Of(address)}");
    Console.WriteLine($"  Public key: {keypair.PublicKey[..32]}...");

    // Verify with correct data
    var goodResult = Verify(crypto, new VerifyRequest(
        keypair.PublicKey, address, message, sig.Signature));
    Console.WriteLine($"Valid signature result: {goodResult.Valid}");

    // Verify with wrong message
    var badResult = Verify(crypto, new VerifyRequest(
        keypair.PublicKey, address, "tampered message", sig.Signature));
    Console.WriteLine($"Tampered message result: {badResult.Valid}"
        + (badResult.Error is not null ? $" ({badResult.Error})" : ""));
}
catch (Exception e)
{
    Console.Error.WriteLine($"Fatal error: {e.Message}");
    Console.Error.WriteLine(e.StackTrace);
    Environment.Exit(1);
}

static VerifyResult Verify(NativeCryptoBridge crypto, VerifyRequest req)
{
    // 1. Validate the public key format
    try
    {
        crypto.ValidatePublicKey(req.PublicKey);
    }
    catch (Exception e)
    {
        return new VerifyResult(false, $"Invalid public key: {e.Message}");
    }

    // 2. Validate the signature format
    try
    {
        crypto.ValidateSignature(req.Signature);
    }
    catch (Exception e)
    {
        return new VerifyResult(false, $"Invalid signature: {e.Message}");
    }

    // 3. Validate the claimed address format using Address.Of
    try
    {
        Address.Of(req.Address);
        crypto.ValidateAddress(req.Address);
    }
    catch (Exception e)
    {
        return new VerifyResult(false, $"Invalid address: {e.Message}");
    }

    // 4. Verify the public key maps to the claimed address
    try
    {
        var derived = crypto.AddressFromPublicKey(req.PublicKey);
        if (Address.Of(derived) != Address.Of(req.Address))
        {
            return new VerifyResult(false, "Address does not match public key");
        }
    }
    catch (Exception e)
    {
        return new VerifyResult(false, $"Address derivation failed: {e.Message}");
    }

    // 5. Verify the cryptographic signature
    try
    {
        var valid = crypto.VerifyMessage(req.PublicKey, req.Message, req.Signature);
        if (!valid)
        {
            return new VerifyResult(false, "Signature verification failed");
        }
    }
    catch (Exception e)
    {
        return new VerifyResult(false, $"Verification error: {e.Message}");
    }

    return new VerifyResult(true, null);
}

record VerifyRequest(string PublicKey, string Address, string Message, string Signature);
record VerifyResult(bool Valid, string? Error);
```

---

## Scenario 4: Wallet Backup and Recovery

Create a new wallet, save the encrypted wallet file to disk, then recover it later
from the saved file. Covers the full wallet lifecycle: generate mnemonic, create
encrypted wallet file, serialize, write to disk, read from disk, deserialize, and
recover.

```csharp
using System.Text.Json;
using Dilithia.Sdk;
using Dilithia.Sdk.Models;
using Dilithia.Sdk.Crypto;

const string WalletPath = "./my-wallet.json";
const string Password = "my-secure-passphrase";

var jsonOptions = new JsonSerializerOptions { WriteIndented = true };

try
{
    var crypto = new NativeCryptoBridge();

    if (!File.Exists(WalletPath))
    {
        // ---- CREATE NEW WALLET ----
        Console.WriteLine("No wallet found. Creating a new one...");

        // 1. Generate a fresh mnemonic
        var mnemonic = crypto.GenerateMnemonic();
        Console.WriteLine("SAVE THIS MNEMONIC SECURELY:");
        Console.WriteLine(mnemonic);
        Console.WriteLine();

        // 2. Validate the generated mnemonic
        crypto.ValidateMnemonic(mnemonic);
        Console.WriteLine("Mnemonic validated successfully.");

        // 3. Create an encrypted wallet file
        var account = crypto.CreateHdWalletFileFromMnemonic(mnemonic, Password);
        Console.WriteLine($"Address:    {Address.Of(account.Address)}");
        Console.WriteLine($"Public key: {account.PublicKey}");

        // 4. Save to disk
        var walletJson = JsonSerializer.Serialize(account.WalletFile, jsonOptions);
        await File.WriteAllTextAsync(WalletPath, walletJson);
        Console.WriteLine($"Wallet saved to {WalletPath}");

        // 5. Verify a round-trip sign/verify
        var sig = crypto.SignMessage(account.SecretKey, "hello from new wallet");
        var ok = crypto.VerifyMessage(account.PublicKey, "hello from new wallet", sig.Signature);
        Console.WriteLine($"Sign/verify round-trip: {(ok ? "PASS" : "FAIL")}");
    }
    else
    {
        // ---- RECOVER EXISTING WALLET ----
        Console.WriteLine("Wallet file found. Recovering...");

        // 6. Read and deserialize
        var savedJson = await File.ReadAllTextAsync(WalletPath);
        var savedWallet = JsonSerializer.Deserialize<Dictionary<string, object>>(savedJson)
            ?? throw new InvalidOperationException("Failed to deserialize wallet file");

        // 7. Recover using mnemonic + password
        var mnemonic = Environment.GetEnvironmentVariable("WALLET_MNEMONIC")
            ?? throw new InvalidOperationException("Set WALLET_MNEMONIC env var to recover");
        if (string.IsNullOrWhiteSpace(mnemonic))
            throw new InvalidOperationException("Set WALLET_MNEMONIC env var to recover");

        var account = crypto.RecoverWalletFile(savedWallet, mnemonic, Password);
        Console.WriteLine($"Recovered address:    {Address.Of(account.Address)}");
        Console.WriteLine($"Recovered public key: {account.PublicKey}");
        Console.WriteLine("Wallet recovered successfully. Ready to sign transactions.");
    }
}
catch (Exception e)
{
    Console.Error.WriteLine($"Fatal error: {e.Message}");
    Console.Error.WriteLine(e.StackTrace);
    Environment.Exit(1);
}
```

---

## Scenario 5: Gas-Sponsored Meta-Transaction

Submit a transaction where the gas fee is paid by a sponsor contract instead of the
sender. Useful for onboarding new users who have no tokens to pay for gas. Covers:
client setup, building a paymaster-attached call, signing with the native adapter,
and submission.

```csharp
using Dilithia.Sdk;
using Dilithia.Sdk.Models;
using Dilithia.Sdk.Crypto;
using Dilithia.Sdk.Exceptions;

const string RpcUrl = "https://rpc.dilithia.network/rpc";
const string SponsorContract = "dil1_gas_sponsor_v1";
const string Paymaster = "dil1_paymaster_addr";
const string TargetContract = "dil1_nft_mint";

try
{
    // 1. Initialize client with builder and crypto adapter
    using var client = DilithiaClient.Create(RpcUrl)
        .WithTimeout(TimeSpan.FromSeconds(15))
        .Build();
    var crypto = new NativeCryptoBridge();

    // 2. Recover the user's wallet
    var mnemonic = Environment.GetEnvironmentVariable("USER_MNEMONIC")
        ?? throw new InvalidOperationException("Set USER_MNEMONIC env var");
    if (string.IsNullOrWhiteSpace(mnemonic))
        throw new InvalidOperationException("Set USER_MNEMONIC env var");

    var account = crypto.RecoverHdWallet(mnemonic);
    Console.WriteLine($"User address: {Address.Of(account.Address)}");

    // 3. Check if the sponsor accepts this call — query returns QueryResult
    var acceptResult = await client.Contract(SponsorContract)
        .QueryAsync("accept", new Dictionary<string, object>
        {
            ["user"] = account.Address,
            ["contract"] = TargetContract,
            ["method"] = "mint"
        });
    Console.WriteLine($"Sponsor accepts: {acceptResult}");

    // 4. Check remaining quota — query returns QueryResult
    var quotaResult = await client.Contract(SponsorContract)
        .QueryAsync("remaining_quota", new Dictionary<string, object>
        {
            ["user"] = account.Address
        });
    Console.WriteLine($"Remaining quota: {quotaResult}");

    // 5. Get gas estimate — returns GasEstimate
    var gasEstimate = await client.Network().GetGasEstimateAsync();
    Console.WriteLine($"Current gas estimate: {gasEstimate}");

    // 6. Build signer
    DilithiaSigner signer = payload => new SignedPayload(
        "mldsa65",
        PublicKey.Of(account.PublicKey),
        crypto.SignMessage(account.SecretKey, payload).Signature);

    // 7. Send the sponsored call — returns Receipt
    var receipt = await client.Contract(TargetContract)
        .Call("mint", new Dictionary<string, object>
        {
            ["token_id"] = "nft_001",
            ["metadata"] = "ipfs://QmSomeHash"
        })
        .SendAsync(signer);

    Console.WriteLine($"Sponsored tx confirmed at block {receipt.BlockHeight}, status: {receipt.Status}");
}
catch (HttpException e)
{
    Console.Error.WriteLine($"HTTP error {e.StatusCode}: {e.Message}");
    Environment.Exit(1);
}
catch (RpcException e)
{
    Console.Error.WriteLine($"RPC error {e.Code}: {e.Message}");
    Environment.Exit(1);
}
catch (DilithiaTimeoutException e)
{
    Console.Error.WriteLine($"Timeout: {e.Message}");
    Environment.Exit(1);
}
catch (Exception e)
{
    Console.Error.WriteLine($"Fatal error: {e.Message}");
    Console.Error.WriteLine(e.StackTrace);
    Environment.Exit(1);
}
```

---

## Scenario 6: Cross-Chain Message Sender

Send a message to another Dilithia chain via the messaging contract. Useful for
bridging data or triggering actions on a remote chain. Covers: client setup,
building outbound messages, signing, submission, and receipt polling.

```csharp
using Dilithia.Sdk;
using Dilithia.Sdk.Models;
using Dilithia.Sdk.Crypto;
using Dilithia.Sdk.Exceptions;

const string RpcUrl = "https://rpc.dilithia.network/rpc";
const string MessagingContract = "dil1_bridge_v1";
const string Paymaster = "dil1_bridge_paymaster";
const string DestChain = "dilithia-testnet-2";

try
{
    // 1. Initialize client with builder
    using var client = DilithiaClient.Create(RpcUrl)
        .WithTimeout(TimeSpan.FromSeconds(15))
        .Build();
    var crypto = new NativeCryptoBridge();

    // 2. Recover sender wallet
    var mnemonic = Environment.GetEnvironmentVariable("SENDER_MNEMONIC")
        ?? throw new InvalidOperationException("Set SENDER_MNEMONIC env var");
    if (string.IsNullOrWhiteSpace(mnemonic))
        throw new InvalidOperationException("Set SENDER_MNEMONIC env var");

    var account = crypto.RecoverHdWallet(mnemonic);
    Console.WriteLine($"Sender address: {Address.Of(account.Address)}");

    // 3. Resolve a name — returns NameRecord
    var resolved = await client.Names().ResolveAsync("alice.dili");
    Console.WriteLine($"Resolved alice.dili -> {resolved}");

    // 4. Build the cross-chain message payload
    var messagePayload = new Dictionary<string, object>
    {
        ["action"] = "lock_tokens",
        ["sender"] = account.Address,
        ["amount"] = TokenAmount.Dili("50000"),
        ["recipient"] = Address.Of("dil1_remote_recipient").Value
    };

    // 5. Build signer
    DilithiaSigner signer = payload => new SignedPayload(
        "mldsa65",
        PublicKey.Of(account.PublicKey),
        crypto.SignMessage(account.SecretKey, payload).Signature);

    // 6. Send the cross-chain message call — returns Receipt
    var sendArgs = new Dictionary<string, object>
    {
        ["dest_chain"] = DestChain,
        ["payload"] = messagePayload
    };

    var receipt = await client.Contract(MessagingContract)
        .Call("send_message", sendArgs)
        .SendAsync(signer);

    Console.WriteLine($"Message tx confirmed at block {receipt.BlockHeight}, status: {receipt.Status}, tx: {receipt.TxHash}");

    // 7. Optionally wait for the receipt with explicit polling
    var txHash = TxHash.Of(receipt.TxHash.Value);
    var polled = await client.GetReceiptAsync(txHash, maxRetries: 12, delay: TimeSpan.FromSeconds(1));
    Console.WriteLine($"Polled receipt status: {polled.Status}");
}
catch (DilithiaException e)
{
    Console.Error.WriteLine($"SDK error: {e.Message}");
    Environment.Exit(1);
}
catch (Exception e)
{
    Console.Error.WriteLine($"Fatal error: {e.Message}");
    Console.Error.WriteLine(e.StackTrace);
    Environment.Exit(1);
}
```

---

## Scenario 7: Contract Deployment

Deploy a WASM smart contract to the Dilithia chain. Reads the WASM binary, validates
the bytecode, builds and signs a canonical deploy payload, assembles the full `DeployPayload`,
sends the deploy request, and waits for confirmation.

```csharp
using Dilithia.Sdk;
using Dilithia.Sdk.Models;
using Dilithia.Sdk.Crypto;
using Dilithia.Sdk.Validation;
using Dilithia.Sdk.Exceptions;

const string RpcUrl = "https://rpc.dilithia.network/rpc";
const string ContractName = "my_contract";
const string WasmPath = "./my_contract.wasm";
const string ChainId = "dilithia-mainnet";

try
{
    // 1. Initialize client with builder and crypto adapter
    using var client = DilithiaClient.Create(RpcUrl)
        .WithTimeout(TimeSpan.FromSeconds(30))
        .Build();
    var crypto = new NativeCryptoBridge();

    // 2. Recover wallet from mnemonic
    var mnemonic = Environment.GetEnvironmentVariable("DEPLOYER_MNEMONIC")
        ?? throw new InvalidOperationException("Set DEPLOYER_MNEMONIC env var");
    if (string.IsNullOrWhiteSpace(mnemonic))
        throw new InvalidOperationException("Set DEPLOYER_MNEMONIC env var");

    var account = crypto.RecoverHdWallet(mnemonic);
    Console.WriteLine($"Deployer address: {Address.Of(account.Address)}");

    // 3. Read the WASM file as hex
    var bytecodeHex = DilithiaClient.ReadWasmFileHex(WasmPath);
    Console.WriteLine($"Bytecode size: {bytecodeHex.Length / 2} bytes");

    // 4. Validate the bytecode before deploying
    BytecodeValidator.Validate(bytecodeHex);
    var estimatedGas = BytecodeValidator.EstimateDeployGas(bytecodeHex);
    Console.WriteLine($"Estimated deploy gas: {estimatedGas}");

    // 5. Get the current nonce — returns Nonce with .NextNonce
    var nonceResult = await client.GetNonceAsync(Address.Of(account.Address));
    Console.WriteLine($"Current nonce: {nonceResult.NextNonce}");

    // 6. Hash the bytecode hex for the canonical payload
    var bytecodeHash = crypto.HashHex(bytecodeHex);
    Console.WriteLine($"Bytecode hash: {bytecodeHash}");

    // 7. Build signer
    DilithiaSigner signer = payload => new SignedPayload(
        "mldsa65",
        PublicKey.Of(account.PublicKey),
        crypto.SignMessage(account.SecretKey, payload).Signature);

    // 8. Build the canonical deploy payload and full deploy payload
    var canonicalPayload = DilithiaClient.BuildDeployCanonicalPayload(
        ContractName, bytecodeHash, Address.Of(account.Address).Value,
        nonceResult.NextNonce, ChainId, 1);

    var deployPayload = new DeployPayload(
        ContractName,
        bytecodeHex,
        Address.Of(account.Address).Value,
        "mldsa65",
        account.PublicKey,
        Signature: null, // applied by signer
        nonceResult.NextNonce,
        ChainId,
        1);

    // 9. Deploy — returns Receipt
    var receipt = await client.DeployContractAsync(deployPayload, signer);
    Console.WriteLine($"Contract deployed at block {receipt.BlockHeight}, status: {receipt.Status}, tx: {receipt.TxHash}");

    // 10. Verify with explicit receipt lookup using TxHash typed identifier
    var verified = await client.GetReceiptAsync(
        TxHash.Of(receipt.TxHash.Value), maxRetries: 30, delay: TimeSpan.FromSeconds(3));
    Console.WriteLine($"Verified deployment status: {verified.Status}");

    // 11. Shielded deposit example (bonus)
    // var commitment = "0xabc123...";
    // var proof = zkAdapter.GeneratePreimageProof(commitment);
    // var shielded = await client.ShieldedDepositAsync(
    //     commitment, TokenAmount.Dili("100.5"), proof, signer);
}
catch (HttpException e)
{
    Console.Error.WriteLine($"HTTP error {e.StatusCode}: {e.Message}");
    Environment.Exit(1);
}
catch (RpcException e)
{
    Console.Error.WriteLine($"RPC error {e.Code}: {e.Message}");
    Environment.Exit(1);
}
catch (DilithiaTimeoutException e)
{
    Console.Error.WriteLine($"Timeout: {e.Message}");
    Environment.Exit(1);
}
catch (Exception e)
{
    Console.Error.WriteLine($"Deploy error: {e.Message}");
    Console.Error.WriteLine(e.StackTrace);
    Environment.Exit(1);
}
```

---

## Scenario 8: Shielded Pool Deposit & Withdraw

Deposit tokens into the shielded pool and later withdraw them to a recipient address.
The deposit creates a commitment from a secret and nonce, then submits it with a
ZK preimage proof. The withdrawal computes a nullifier, verifies it has not been spent,
fetches the current commitment root, and submits the withdrawal proof. Covers: ZK
commitment and nullifier computation, shielded deposit and withdraw RPCs, commitment
root queries, nullifier spend checks, and receipt polling.

```csharp
using Dilithia.Sdk;
using Dilithia.Sdk.Models;
using Dilithia.Sdk.Crypto;
using Dilithia.Sdk.Zk;
using Dilithia.Sdk.Exceptions;

const string RpcUrl = "https://rpc.dilithia.network/rpc";
const string Recipient = "dil1_withdraw_recipient";
const long DepositValue = 250_000;
const long WithdrawAmount = 100_000;

try
{
    // 1. Initialize client, crypto bridge, and ZK adapter
    using var client = DilithiaClient.Create(RpcUrl)
        .WithTimeout(TimeSpan.FromSeconds(15))
        .Build();
    var crypto = new NativeCryptoBridge();
    IDilithiaZkAdapter zk = new NativeZkBridge(); // P/Invoke

    // 2. Generate secret and nonce for the commitment
    var secretHex = crypto.HashHex("user-secret-entropy-seed");
    var nonceHex = crypto.HashHex("user-nonce-entropy-seed");
    Console.WriteLine($"Secret: {secretHex[..16]}...");
    Console.WriteLine($"Nonce:  {nonceHex[..16]}...");

    // ---- DEPOSIT ----

    // 3. Compute the commitment hash from value, secret, and nonce
    var commitment = zk.ComputeCommitment(DepositValue, secretHex, nonceHex);
    Console.WriteLine($"Commitment hash: {commitment.Hash}");

    // 4. Generate a preimage proof for the deposit
    var depositProof = zk.GeneratePreimageProof(new long[] { DepositValue, commitment.Hash.Length });
    Console.WriteLine($"Deposit proof generated, inputs: {depositProof.Inputs.Length}");

    // 5. Submit the shielded deposit — returns tx hash
    var depositTxHash = await client.ShieldedDepositAsync(
        commitment.Hash, DepositValue, depositProof.Proof);
    Console.WriteLine($"Deposit tx submitted: {depositTxHash}");

    // 6. Wait for the deposit receipt
    var depositReceipt = await client.WaitForReceiptAsync(depositTxHash, maxAttempts: 20, delay: TimeSpan.FromSeconds(2));
    Console.WriteLine($"Deposit confirmed at block {depositReceipt.BlockHeight}, status: {depositReceipt.Status}");

    // ---- WITHDRAW ----

    // 7. Compute the nullifier from the same secret and nonce
    var nullifier = zk.ComputeNullifier(secretHex, nonceHex);
    Console.WriteLine($"Nullifier hash: {nullifier.Hash}");

    // 8. Check that the nullifier has not already been spent
    var alreadySpent = await client.IsNullifierSpentAsync(nullifier.Hash);
    if (alreadySpent)
    {
        Console.Error.WriteLine("Nullifier already spent. Cannot withdraw.");
        Environment.Exit(1);
    }
    Console.WriteLine("Nullifier not yet spent. Proceeding with withdrawal.");

    // 9. Fetch the current commitment root
    var commitmentRoot = await client.GetCommitmentRootAsync();
    Console.WriteLine($"Current commitment root: {commitmentRoot}");

    // 10. Generate a range proof to show the withdrawal amount is within bounds
    var withdrawProof = zk.GenerateRangeProof(WithdrawAmount, 0, DepositValue);
    Console.WriteLine($"Withdraw proof generated, inputs: {withdrawProof.Inputs.Length}");

    // 11. Submit the shielded withdrawal
    var withdrawTxHash = await client.ShieldedWithdrawAsync(
        nullifier.Hash, WithdrawAmount, Recipient, withdrawProof.Proof, commitmentRoot);
    Console.WriteLine($"Withdraw tx submitted: {withdrawTxHash}");

    // 12. Wait for the withdrawal receipt
    var withdrawReceipt = await client.WaitForReceiptAsync(withdrawTxHash, maxAttempts: 20, delay: TimeSpan.FromSeconds(2));
    Console.WriteLine($"Withdraw confirmed at block {withdrawReceipt.BlockHeight}, status: {withdrawReceipt.Status}");

    // 13. Verify the nullifier is now marked as spent
    var spentAfter = await client.IsNullifierSpentAsync(nullifier.Hash);
    Console.WriteLine($"Nullifier spent after withdrawal: {spentAfter}");
}
catch (HttpException e)
{
    Console.Error.WriteLine($"HTTP error {e.StatusCode}: {e.Message}");
    Environment.Exit(1);
}
catch (RpcException e)
{
    Console.Error.WriteLine($"RPC error {e.Code}: {e.Message}");
    Environment.Exit(1);
}
catch (DilithiaTimeoutException e)
{
    Console.Error.WriteLine($"Timeout: {e.Message}");
    Environment.Exit(1);
}
catch (Exception e)
{
    Console.Error.WriteLine($"Fatal error: {e.Message}");
    Console.Error.WriteLine(e.StackTrace);
    Environment.Exit(1);
}
```

---

## Scenario 9: ZK Proof Generation & Verification

Generate and verify zero-knowledge proofs using the native ZK bridge. Demonstrates
Poseidon hashing, preimage proof generation and verification, and range proof
generation and verification. Covers: PoseidonHash, GeneratePreimageProof,
VerifyPreimageProof, GenerateRangeProof, VerifyRangeProof, and structured error
handling for proof failures.

```csharp
using Dilithia.Sdk;
using Dilithia.Sdk.Models;
using Dilithia.Sdk.Crypto;
using Dilithia.Sdk.Zk;
using Dilithia.Sdk.Exceptions;

try
{
    var crypto = new NativeCryptoBridge();
    IDilithiaZkAdapter zk = new NativeZkBridge(); // P/Invoke

    // ---- POSEIDON HASHING ----

    // 1. Hash a set of values using the Poseidon hash function
    var values = new long[] { 42, 100, 999 };
    var poseidonHash = zk.PoseidonHash(values);
    Console.WriteLine($"Poseidon hash of [{string.Join(", ", values)}]: {poseidonHash}");

    // 2. Hash a different set to show determinism
    var sameHash = zk.PoseidonHash(new long[] { 42, 100, 999 });
    Console.WriteLine($"Same inputs produce same hash: {poseidonHash == sameHash}");

    var differentHash = zk.PoseidonHash(new long[] { 42, 100, 1000 });
    Console.WriteLine($"Different inputs produce different hash: {poseidonHash != differentHash}");

    // ---- PREIMAGE PROOF ----

    // 3. Generate a preimage proof — proves knowledge of inputs that hash to a value
    var preimageInputs = new long[] { 42, 100, 999 };
    var preimageProof = zk.GeneratePreimageProof(preimageInputs);
    Console.WriteLine($"\nPreimage proof generated:");
    Console.WriteLine($"  Proof length:  {preimageProof.Proof.Length} chars");
    Console.WriteLine($"  VK length:     {preimageProof.Vk.Length} chars");
    Console.WriteLine($"  Input count:   {preimageProof.Inputs.Length}");

    // 4. Verify the preimage proof with correct inputs — should succeed
    var preimageValid = zk.VerifyPreimageProof(
        preimageProof.Proof, preimageProof.Vk, preimageProof.Inputs);
    Console.WriteLine($"  Verification (correct inputs): {(preimageValid ? "PASS" : "FAIL")}");

    // 5. Verify with tampered inputs — should fail
    var tamperedInputs = new long[] { 42, 100, 1000 };
    var preimageInvalid = zk.VerifyPreimageProof(
        preimageProof.Proof, preimageProof.Vk, tamperedInputs);
    Console.WriteLine($"  Verification (tampered inputs): {(preimageInvalid ? "PASS" : "FAIL")}");

    // ---- RANGE PROOF ----

    // 6. Generate a range proof — proves a value lies within [min, max]
    long secretValue = 500;
    long rangeMin = 0;
    long rangeMax = 1000;
    var rangeProof = zk.GenerateRangeProof(secretValue, rangeMin, rangeMax);
    Console.WriteLine($"\nRange proof generated (value={secretValue}, range=[{rangeMin}, {rangeMax}]):");
    Console.WriteLine($"  Proof length:  {rangeProof.Proof.Length} chars");
    Console.WriteLine($"  VK length:     {rangeProof.Vk.Length} chars");
    Console.WriteLine($"  Input count:   {rangeProof.Inputs.Length}");

    // 7. Verify the range proof with correct inputs — should succeed
    var rangeValid = zk.VerifyRangeProof(
        rangeProof.Proof, rangeProof.Vk, rangeProof.Inputs);
    Console.WriteLine($"  Verification (correct): {(rangeValid ? "PASS" : "FAIL")}");

    // 8. Verify with tampered inputs — should fail
    var tamperedRangeInputs = new long[] { 1500, rangeMin, rangeMax };
    var rangeInvalid = zk.VerifyRangeProof(
        rangeProof.Proof, rangeProof.Vk, tamperedRangeInputs);
    Console.WriteLine($"  Verification (tampered): {(rangeInvalid ? "PASS" : "FAIL")}");

    // ---- COMMITMENT & NULLIFIER ROUND-TRIP ----

    // 9. Compute a commitment and its corresponding nullifier
    var secretHex = crypto.HashHex("my-secret-data");
    var nonceHex = crypto.HashHex("my-unique-nonce");

    var commitment = zk.ComputeCommitment(1000, secretHex, nonceHex);
    var nullifier = zk.ComputeNullifier(secretHex, nonceHex);

    Console.WriteLine($"\nCommitment/Nullifier round-trip:");
    Console.WriteLine($"  Commitment hash: {commitment.Hash}");
    Console.WriteLine($"  Commitment value: {commitment.Value}");
    Console.WriteLine($"  Nullifier hash:  {nullifier.Hash}");

    // 10. Verify determinism — same inputs always produce same outputs
    var commitment2 = zk.ComputeCommitment(1000, secretHex, nonceHex);
    var nullifier2 = zk.ComputeNullifier(secretHex, nonceHex);
    Console.WriteLine($"  Commitment deterministic: {commitment.Hash == commitment2.Hash}");
    Console.WriteLine($"  Nullifier deterministic:  {nullifier.Hash == nullifier2.Hash}");

    Console.WriteLine("\nAll ZK proof tests completed.");
}
catch (DilithiaException e)
{
    Console.Error.WriteLine($"SDK error: {e.Message}");
    Environment.Exit(1);
}
catch (Exception e)
{
    Console.Error.WriteLine($"Fatal error: {e.Message}");
    Console.Error.WriteLine(e.StackTrace);
    Environment.Exit(1);
}
```

---

## Scenario 10: Name Service & Identity Profile

A utility that registers a `.dili` name, configures profile records, resolves names, and demonstrates the full name service lifecycle. Covers: `getRegistrationCost`, `isNameAvailable`, `registerName`, `setNameTarget`, `setNameRecord`, `getNameRecords`, `lookupName`, `resolveName`, `reverseResolveName`, `getNamesByOwner`, `renewName`, `transferName`, `releaseName`.

```csharp
using Dilithia.Sdk;
using Dilithia.Sdk.Models;
using Dilithia.Sdk.Crypto;
using Dilithia.Sdk.Exceptions;

const string RpcUrl = "https://rpc.dilithia.network/rpc";
const string Name = "alice";
const string TransferTo = "dil1_bob_address";

try
{
    // 1. Initialize client and crypto adapter
    using var client = DilithiaClient.Create(RpcUrl)
        .WithTimeout(TimeSpan.FromSeconds(15))
        .Build();
    var crypto = new NativeCryptoBridge();

    // 2. Recover wallet from mnemonic
    var mnemonic = Environment.GetEnvironmentVariable("WALLET_MNEMONIC")
        ?? throw new DilithiaException("Set WALLET_MNEMONIC env var");
    var account = crypto.RecoverHdWallet(mnemonic);
    Console.WriteLine($"Address: {account.Address}");

    // 3. Query registration cost for the name
    var cost = await client.GetRegistrationCostAsync(Name);
    Console.WriteLine($"Registration cost for \"{Name}\": {cost.Formatted()}");

    // 4. Check if name is available
    var available = await client.IsNameAvailableAsync(Name);
    Console.WriteLine($"Name \"{Name}\" available: {available}");
    if (!available)
        throw new DilithiaException($"Name \"{Name}\" is already taken");

    // 5. Register name
    var regHash = await client.RegisterNameAsync(Name, account.Address, account.SecretKey);
    var regReceipt = await client.WaitForReceiptAsync(regHash, retries: 20, delayMs: 2000);
    Console.WriteLine($"Name registered in block {regReceipt.BlockHeight}");

    // 6. Set target address
    var targetHash = await client.SetNameTargetAsync(Name, account.Address, account.SecretKey);
    await client.WaitForReceiptAsync(targetHash, retries: 20, delayMs: 2000);
    Console.WriteLine($"Target address set to {account.Address}");

    // 7. Set profile records
    var records = new Dictionary<string, string>
    {
        ["display_name"] = "Alice",
        ["avatar"] = "https://example.com/alice.png",
        ["bio"] = "Builder on Dilithia",
        ["email"] = "alice@example.com",
        ["website"] = "https://alice.dev",
    };
    foreach (var (key, value) in records)
    {
        var hash = await client.SetNameRecordAsync(Name, key, value, account.SecretKey);
        await client.WaitForReceiptAsync(hash, retries: 20, delayMs: 2000);
        Console.WriteLine($"  Set record \"{key}\" = \"{value}\"");
    }

    // 8. Get all records
    var allRecords = await client.GetNameRecordsAsync(Name);
    Console.WriteLine($"All records: {allRecords}");

    // 9. Resolve name to address
    var resolved = await client.ResolveNameAsync(Name);
    Console.WriteLine($"ResolveName(\"{Name}\") -> {resolved}");

    // 10. Reverse resolve address to name
    var reverseName = await client.ReverseResolveNameAsync(account.Address);
    Console.WriteLine($"ReverseResolveName(\"{account.Address}\") -> {reverseName}");

    // 11. List all names by owner
    var owned = await client.GetNamesByOwnerAsync(account.Address);
    Console.WriteLine($"Names owned by {account.Address}: [{string.Join(", ", owned)}]");

    // 12. Renew name
    var renewHash = await client.RenewNameAsync(Name, account.SecretKey);
    var renewReceipt = await client.WaitForReceiptAsync(renewHash, retries: 20, delayMs: 2000);
    Console.WriteLine($"Name renewed in block {renewReceipt.BlockHeight}");

    // 13. Transfer name to another address
    var transferHash = await client.TransferNameAsync(Name, TransferTo, account.SecretKey);
    var transferReceipt = await client.WaitForReceiptAsync(transferHash, retries: 20, delayMs: 2000);
    Console.WriteLine($"Name transferred to {TransferTo} in block {transferReceipt.BlockHeight}");
}
catch (DilithiaException e)
{
    Console.Error.WriteLine($"Name service error: {e.Message}");
    Environment.Exit(1);
}
catch (Exception e)
{
    Console.Error.WriteLine($"Fatal error: {e.Message}");
    Console.Error.WriteLine(e.StackTrace);
    Environment.Exit(1);
}
```

---

## Scenario 11: Credential Issuance & Verification

An issuer creates a KYC credential schema, issues a credential to a holder, and a verifier checks it with selective disclosure. Covers: `registerSchema`, `issueCredential`, `getSchema`, `getCredential`, `listCredentialsByHolder`, `listCredentialsByIssuer`, `verifyCredential`, `revokeCredential`.

```csharp
using Dilithia.Sdk;
using Dilithia.Sdk.Models;
using Dilithia.Sdk.Crypto;
using Dilithia.Sdk.Exceptions;

const string RpcUrl = "https://rpc.dilithia.network/rpc";
const string HolderAddress = "dil1_holder_address";

try
{
    // 1. Initialize client and crypto adapter
    using var client = DilithiaClient.Create(RpcUrl)
        .WithTimeout(TimeSpan.FromSeconds(15))
        .Build();
    var crypto = new NativeCryptoBridge();

    // 2. Recover issuer wallet
    var mnemonic = Environment.GetEnvironmentVariable("ISSUER_MNEMONIC")
        ?? throw new DilithiaException("Set ISSUER_MNEMONIC env var");
    var issuer = crypto.RecoverHdWallet(mnemonic);
    Console.WriteLine($"Issuer address: {issuer.Address}");

    // 3. Register a KYC schema
    var schema = new SchemaDefinition("KYC_Basic_v1", new[]
    {
        new SchemaAttribute("full_name", "string"),
        new SchemaAttribute("country", "string"),
        new SchemaAttribute("age", "u64"),
        new SchemaAttribute("verified", "bool"),
    });
    var schemaHash = await client.RegisterSchemaAsync(schema, issuer.SecretKey);
    var schemaReceipt = await client.WaitForReceiptAsync(schemaHash, retries: 20, delayMs: 2000);
    var registeredHash = schemaReceipt.Logs[0]["schema_hash"].ToString()!;
    Console.WriteLine($"Schema registered: {registeredHash}");

    // 4. Issue credential to holder with commitment hash
    var commitmentInput = $"{HolderAddress}:KYC_Basic_v1:{DateTimeOffset.UtcNow.ToUnixTimeSeconds()}";
    var commitmentHash = crypto.HashHex(commitmentInput);

    var issueHash = await client.IssueCredentialAsync(new CredentialParams
    {
        SchemaHash = registeredHash,
        Holder = HolderAddress,
        CommitmentHash = commitmentHash,
        Attributes = new Dictionary<string, object>
        {
            ["full_name"] = "Alice Smith",
            ["country"] = "CH",
            ["age"] = 30,
            ["verified"] = true,
        },
    }, issuer.SecretKey);
    var issueReceipt = await client.WaitForReceiptAsync(issueHash, retries: 20, delayMs: 2000);
    Console.WriteLine($"Credential issued in block {issueReceipt.BlockHeight}");

    // 5. Get schema by hash
    var fetchedSchema = await client.GetSchemaAsync(registeredHash);
    Console.WriteLine($"Schema: {fetchedSchema}");

    // 6. Get credential by commitment
    var credential = await client.GetCredentialAsync(commitmentHash);
    Console.WriteLine($"Credential: {credential}");

    // 7. List credentials by holder
    var holderCreds = await client.ListCredentialsByHolderAsync(HolderAddress);
    Console.WriteLine($"Holder has {holderCreds.Count} credential(s)");

    // 8. List credentials by issuer
    var issuerCreds = await client.ListCredentialsByIssuerAsync(issuer.Address);
    Console.WriteLine($"Issuer has {issuerCreds.Count} credential(s)");

    // 9. Verify selective disclosure — prove age > 18 without revealing exact age
    var proof = crypto.GenerateSelectiveDisclosureProof(
        commitmentHash,
        new[] { "age" },
        new Dictionary<string, object>
        {
            ["age"] = new Dictionary<string, object> { ["operator"] = "gt", ["threshold"] = 18 },
        });
    var verified = await client.VerifyCredentialAsync(commitmentHash, proof);
    Console.WriteLine($"Selective disclosure (age > 18) verified: {verified}");

    // 10. Revoke the credential
    var revokeHash = await client.RevokeCredentialAsync(commitmentHash, issuer.SecretKey);
    var revokeReceipt = await client.WaitForReceiptAsync(revokeHash, retries: 20, delayMs: 2000);
    Console.WriteLine($"Credential revoked in block {revokeReceipt.BlockHeight}");

    // 11. Verify revocation by fetching credential again
    var revokedCred = await client.GetCredentialAsync(commitmentHash);
    Console.WriteLine($"Credential status after revocation: {revokedCred.Status}");
}
catch (DilithiaException e)
{
    Console.Error.WriteLine($"Credential error: {e.Message}");
    Environment.Exit(1);
}
catch (Exception e)
{
    Console.Error.WriteLine($"Fatal error: {e.Message}");
    Console.Error.WriteLine(e.StackTrace);
    Environment.Exit(1);
}
```
