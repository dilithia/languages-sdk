using System.Text.Json;
using System.Web;
using Dilithia.Sdk.Exceptions;
using Dilithia.Sdk.Internal;
using Dilithia.Sdk.Models;

namespace Dilithia.Sdk;

/// <summary>
/// Primary client for interacting with a Dilithia node.
/// Provides typed async methods for balance queries, nonce lookups, transaction
/// submission, contract calls, name-service resolution, deploy/upgrade,
/// and shielded-pool operations.
/// </summary>
public sealed class DilithiaClient : IDisposable
{
    /// <summary>Current SDK version.</summary>
    public const string SdkVersion = "0.5.0";

    /// <summary>The JSON-RPC endpoint URL.</summary>
    public string RpcUrl { get; }

    /// <summary>The chain base URL for REST endpoints.</summary>
    public string BaseUrl { get; }

    private readonly HttpClient _httpClient;
    private readonly bool _ownsHttpClient;
    private readonly JsonRpcClient _rpc;

    // ── Construction ─────────────────────────────────────────────────────

    /// <summary>Builder entry point.</summary>
    public static DilithiaClientBuilder Create(string rpcUrl) => new(rpcUrl);

    /// <summary>
    /// Create a new Dilithia client. Prefer <see cref="Create"/> for the fluent builder API.
    /// </summary>
    internal DilithiaClient(
        string rpcUrl,
        HttpClient? httpClient = null,
        TimeSpan? timeout = null,
        string? chainBaseUrl = null,
        string? jwt = null,
        Dictionary<string, string>? headers = null)
    {
        RpcUrl = rpcUrl.TrimEnd('/');
        BaseUrl = (chainBaseUrl ?? RpcUrl.Replace("/rpc", "")).TrimEnd('/');

        var effectiveTimeout = timeout ?? TimeSpan.FromSeconds(10);

        if (httpClient is not null)
        {
            _httpClient = httpClient;
            _ownsHttpClient = false;
        }
        else
        {
            _httpClient = new HttpClient();
            _ownsHttpClient = true;
        }

        // Apply default headers
        _httpClient.DefaultRequestHeaders.TryAddWithoutValidation("Accept", "application/json");
        if (jwt is not null)
            _httpClient.DefaultRequestHeaders.TryAddWithoutValidation("Authorization", $"Bearer {jwt}");
        if (headers is not null)
        {
            foreach (var (key, value) in headers)
                _httpClient.DefaultRequestHeaders.TryAddWithoutValidation(key, value);
        }

        _rpc = new JsonRpcClient(_httpClient, RpcUrl, effectiveTimeout);
    }

    // ── Typed query methods ──────────────────────────────────────────────

    /// <summary>Fetch the token balance for an address.</summary>
    public async Task<Balance> GetBalanceAsync(Address address, CancellationToken ct = default)
    {
        var raw = await _rpc.GetJsonAsync($"{BaseUrl}/balance/{Uri.EscapeDataString(address.Value)}", ct).ConfigureAwait(false);
        var rawBalance = raw.TryGetProperty("balance", out var b) ? b.ToString() : "0";
        var addr = raw.TryGetProperty("address", out var a) ? Address.Of(a.GetString() ?? address.Value) : address;
        var amount = TokenAmount.FromRaw(long.TryParse(rawBalance, out var rv) ? rv : 0);
        return new Balance(addr, amount, rawBalance);
    }

    /// <summary>Fetch the next nonce for an address.</summary>
    public async Task<Nonce> GetNonceAsync(Address address, CancellationToken ct = default)
    {
        var raw = await _rpc.GetJsonAsync($"{BaseUrl}/nonce/{Uri.EscapeDataString(address.Value)}", ct).ConfigureAwait(false);
        var addr = raw.TryGetProperty("address", out var a) ? Address.Of(a.GetString() ?? address.Value) : address;
        var nonce = raw.TryGetProperty("nonce", out var n) ? n.GetInt64()
                  : raw.TryGetProperty("next_nonce", out var nn) ? nn.GetInt64()
                  : raw.TryGetProperty("nextNonce", out var nn2) ? nn2.GetInt64() : 0;
        return new Nonce(addr, nonce);
    }

    /// <summary>Fetch a transaction receipt by hash.</summary>
    public async Task<Receipt> GetReceiptAsync(TxHash txHash, CancellationToken ct = default)
    {
        var raw = await _rpc.GetJsonAsync($"{BaseUrl}/receipt/{Uri.EscapeDataString(txHash.Value)}", ct).ConfigureAwait(false);
        return ParseReceipt(raw, txHash);
    }

    /// <summary>Poll for a transaction receipt until it becomes available.</summary>
    public async Task<Receipt> WaitForReceiptAsync(TxHash txHash, int maxAttempts = 30, TimeSpan? delay = null, CancellationToken ct = default)
    {
        var delayTime = delay ?? TimeSpan.FromSeconds(1);
        for (int i = 0; i < maxAttempts; i++)
        {
            try
            {
                return await GetReceiptAsync(txHash, ct).ConfigureAwait(false);
            }
            catch (HttpException ex) when (ex.StatusCode == 404)
            {
                // Not available yet
            }
            await Task.Delay(delayTime, ct).ConfigureAwait(false);
        }
        throw new DilithiaTimeoutException($"WaitForReceipt({txHash}) after {maxAttempts} attempts");
    }

    /// <summary>Fetch network information (chain ID, block height, base fee).</summary>
    public async Task<NetworkInfo> GetNetworkInfoAsync(CancellationToken ct = default)
    {
        var raw = await _rpc.CallAsync("qsc_networkInfo", new { }, ct).ConfigureAwait(false);
        var chainId = raw.TryGetProperty("chain_id", out var c) ? c.GetString() ?? ""
                    : raw.TryGetProperty("chainId", out var c2) ? c2.GetString() ?? "" : "";
        var height = GetLongProp(raw, "block_height", "blockHeight");
        var baseFee = GetLongProp(raw, "base_fee", "baseFee");
        return new NetworkInfo(chainId, height, baseFee);
    }

    /// <summary>Fetch the current gas estimate from the node.</summary>
    public async Task<GasEstimate> GetGasEstimateAsync(CancellationToken ct = default)
    {
        var raw = await _rpc.CallAsync("qsc_gasEstimate", new { }, ct).ConfigureAwait(false);
        return new GasEstimate(
            GetLongProp(raw, "gas_limit", "gasLimit"),
            GetLongProp(raw, "base_fee", "baseFee"),
            GetLongProp(raw, "estimated_cost", "estimatedCost")
        );
    }

    /// <summary>Execute a read-only contract query.</summary>
    public async Task<QueryResult> QueryContractAsync(string contract, string method, object? args = null, CancellationToken ct = default)
    {
        var argsJson = args is not null ? JsonSerializer.Serialize(args) : "{}";
        var url = $"{BaseUrl}/query?contract={Uri.EscapeDataString(contract)}&method={Uri.EscapeDataString(method)}&args={Uri.EscapeDataString(argsJson)}";
        var raw = await _rpc.GetJsonAsync(url, ct).ConfigureAwait(false);
        var value = raw.TryGetProperty("value", out var v) ? (JsonElement?)v.Clone() : (JsonElement?)raw.Clone();
        return new QueryResult(value);
    }

    /// <summary>Submit a pre-built call envelope to the node.</summary>
    public async Task<SubmitResult> SendCallAsync(object call, CancellationToken ct = default)
    {
        var raw = await _rpc.PostJsonAsync($"{BaseUrl}/call", call, ct).ConfigureAwait(false);
        return ParseSubmitResult(raw);
    }

    /// <summary>Build and submit a contract call in one step.</summary>
    public async Task<SubmitResult> CallContractAsync(string contract, string method, object? args = null, string? paymaster = null, CancellationToken ct = default)
    {
        var call = BuildContractCall(contract, method, args, paymaster);
        return await SendCallAsync(call, ct).ConfigureAwait(false);
    }

    /// <summary>Deploy a contract.</summary>
    public async Task<SubmitResult> DeployContractAsync(DeployPayload payload, CancellationToken ct = default)
    {
        var body = BuildDeployBody(payload);
        var raw = await _rpc.PostJsonAsync($"{BaseUrl}/deploy", body, ct).ConfigureAwait(false);
        return ParseSubmitResult(raw);
    }

    /// <summary>Upgrade a contract.</summary>
    public async Task<SubmitResult> UpgradeContractAsync(DeployPayload payload, CancellationToken ct = default)
    {
        var body = BuildDeployBody(payload);
        var raw = await _rpc.PostJsonAsync($"{BaseUrl}/upgrade", body, ct).ConfigureAwait(false);
        return ParseSubmitResult(raw);
    }

    // ── Name service ─────────────────────────────────────────────────────

    /// <summary>Resolve a name to an address via the name service.</summary>
    public async Task<NameRecord> ResolveNameAsync(string name, CancellationToken ct = default)
    {
        var raw = await _rpc.GetJsonAsync($"{BaseUrl}/names/resolve/{Uri.EscapeDataString(name)}", ct).ConfigureAwait(false);
        var n = raw.TryGetProperty("name", out var np) ? np.GetString() ?? name : name;
        var a = raw.TryGetProperty("address", out var ap) ? Address.Of(ap.GetString() ?? "") : Address.Of("");
        return new NameRecord(n, a);
    }

    /// <summary>Reverse-resolve an address to a name.</summary>
    public async Task<NameRecord> ReverseResolveAsync(Address address, CancellationToken ct = default)
    {
        var raw = await _rpc.GetJsonAsync($"{BaseUrl}/names/reverse/{Uri.EscapeDataString(address.Value)}", ct).ConfigureAwait(false);
        var n = raw.TryGetProperty("name", out var np) ? np.GetString() ?? "" : "";
        var a = raw.TryGetProperty("address", out var ap) ? Address.Of(ap.GetString() ?? address.Value) : address;
        return new NameRecord(n, a);
    }

    // ── Name service mutations ───────────────────────────────────────────

    /// <summary>Register a new name in the name service.</summary>
    public Task<SubmitResult> RegisterNameAsync(string name, CancellationToken ct = default) =>
        CallContractAsync("name_service", "register", new { name }, ct: ct);

    /// <summary>Renew an existing name registration.</summary>
    public Task<SubmitResult> RenewNameAsync(string name, CancellationToken ct = default) =>
        CallContractAsync("name_service", "renew", new { name }, ct: ct);

    /// <summary>Transfer ownership of a name to a new address.</summary>
    public Task<SubmitResult> TransferNameAsync(string name, string newOwner, CancellationToken ct = default) =>
        CallContractAsync("name_service", "transfer", new { name, new_owner = newOwner }, ct: ct);

    /// <summary>Set the resolution target for a name.</summary>
    public Task<SubmitResult> SetNameTargetAsync(string name, string target, CancellationToken ct = default) =>
        CallContractAsync("name_service", "set_target", new { name, target }, ct: ct);

    /// <summary>Set a key-value record on a name.</summary>
    public Task<SubmitResult> SetNameRecordAsync(string name, string key, string value, CancellationToken ct = default) =>
        CallContractAsync("name_service", "set_record", new { name, key, value }, ct: ct);

    /// <summary>Release a name, removing the registration.</summary>
    public Task<SubmitResult> ReleaseNameAsync(string name, CancellationToken ct = default) =>
        CallContractAsync("name_service", "release", new { name }, ct: ct);

    // ── Name service queries ─────────────────────────────────────────────

    /// <summary>Check whether a name is available for registration.</summary>
    public async Task<bool> IsNameAvailableAsync(string name, CancellationToken ct = default)
    {
        var result = await QueryContractAsync("name_service", "is_available", new { name }, ct).ConfigureAwait(false);
        if (result.Value is JsonElement el && el.ValueKind == JsonValueKind.True)
            return true;
        return false;
    }

    /// <summary>Look up a name record from the name service.</summary>
    public async Task<NameRecord> LookupNameAsync(string name, CancellationToken ct = default)
    {
        var result = await QueryContractAsync("name_service", "lookup", new { name }, ct).ConfigureAwait(false);
        var raw = result.Value ?? default;
        var n = raw.TryGetProperty("name", out var np) ? np.GetString() ?? name : name;
        var a = raw.TryGetProperty("address", out var ap) ? Address.Of(ap.GetString() ?? "") : Address.Of("");
        return new NameRecord(n, a);
    }

    /// <summary>Get all key-value records associated with a name.</summary>
    public async Task<Dictionary<string, string>> GetNameRecordsAsync(string name, CancellationToken ct = default)
    {
        var result = await QueryContractAsync("name_service", "get_records", new { name }, ct).ConfigureAwait(false);
        var records = new Dictionary<string, string>();
        if (result.Value is JsonElement el && el.ValueKind == JsonValueKind.Object)
        {
            foreach (var prop in el.EnumerateObject())
                records[prop.Name] = prop.Value.GetString() ?? "";
        }
        return records;
    }

    /// <summary>Get all names owned by an address.</summary>
    public async Task<List<NameRecord>> GetNamesByOwnerAsync(string address, CancellationToken ct = default)
    {
        var result = await QueryContractAsync("name_service", "get_by_owner", new { address }, ct).ConfigureAwait(false);
        var list = new List<NameRecord>();
        if (result.Value is JsonElement el && el.ValueKind == JsonValueKind.Array)
        {
            foreach (var item in el.EnumerateArray())
            {
                var n = item.TryGetProperty("name", out var np) ? np.GetString() ?? "" : "";
                var a = item.TryGetProperty("address", out var ap) ? Address.Of(ap.GetString() ?? "") : Address.Of("");
                list.Add(new NameRecord(n, a));
            }
        }
        return list;
    }

    /// <summary>Get the registration cost for a name.</summary>
    public async Task<RegistrationCost> GetRegistrationCostAsync(string name, CancellationToken ct = default)
    {
        var result = await QueryContractAsync("name_service", "registration_cost", new { name }, ct).ConfigureAwait(false);
        var raw = result.Value ?? default;
        var cost = GetLongProp(raw, "cost", "cost");
        var duration = GetLongProp(raw, "duration", "duration");
        return new RegistrationCost(name, cost, duration);
    }

    // ── Credentials ─────────────────────────────────────────────────────

    /// <summary>Register a new credential schema.</summary>
    public Task<SubmitResult> RegisterSchemaAsync(string name, List<string> fields, CancellationToken ct = default) =>
        CallContractAsync("credential", "register_schema", new { name, fields }, ct: ct);

    /// <summary>Issue a credential to a holder.</summary>
    public Task<SubmitResult> IssueCredentialAsync(string schemaHash, string holder, Dictionary<string, string> claims, CancellationToken ct = default) =>
        CallContractAsync("credential", "issue", new { schema_hash = schemaHash, holder, claims }, ct: ct);

    /// <summary>Revoke a previously issued credential.</summary>
    public Task<SubmitResult> RevokeCredentialAsync(string commitment, CancellationToken ct = default) =>
        CallContractAsync("credential", "revoke", new { commitment = commitment }, ct: ct);

    /// <summary>Verify a credential on-chain.</summary>
    public async Task<bool> VerifyCredentialAsync(string commitment, CancellationToken ct = default)
    {
        var result = await QueryContractAsync("credential", "verify", new { commitment = commitment }, ct).ConfigureAwait(false);
        if (result.Value is JsonElement el && el.ValueKind == JsonValueKind.True)
            return true;
        return false;
    }

    /// <summary>Get a credential by its identifier.</summary>
    public async Task<Credential> GetCredentialAsync(string commitment, CancellationToken ct = default)
    {
        var result = await QueryContractAsync("credential", "get_credential", new { commitment = commitment }, ct).ConfigureAwait(false);
        var raw = result.Value ?? default;
        var id = raw.TryGetProperty("id", out var idp) ? idp.GetString() ?? commitment : commitment;
        var schemaHash = raw.TryGetProperty("schema_hash", out var sp) ? sp.GetString() ?? ""
                     : raw.TryGetProperty("schemaHash", out var sp2) ? sp2.GetString() ?? "" : "";
        var issuer = raw.TryGetProperty("issuer", out var ip) ? Address.Of(ip.GetString() ?? "") : Address.Of("");
        var holder = raw.TryGetProperty("holder", out var hp) ? Address.Of(hp.GetString() ?? "") : Address.Of("");
        var claims = new Dictionary<string, string>();
        if (raw.TryGetProperty("claims", out var cp) && cp.ValueKind == JsonValueKind.Object)
        {
            foreach (var prop in cp.EnumerateObject())
                claims[prop.Name] = prop.Value.GetString() ?? "";
        }
        var revoked = raw.TryGetProperty("revoked", out var rp) && rp.ValueKind == JsonValueKind.True;
        return new Credential(id, schemaHash, issuer, holder, claims, revoked);
    }

    /// <summary>Get a credential schema by its identifier.</summary>
    public async Task<CredentialSchema> GetSchemaAsync(string schemaHash, CancellationToken ct = default)
    {
        var result = await QueryContractAsync("credential", "get_schema", new { schema_hash = schemaHash }, ct).ConfigureAwait(false);
        var raw = result.Value ?? default;
        var id = raw.TryGetProperty("id", out var idp) ? idp.GetString() ?? schemaHash : schemaHash;
        var name = raw.TryGetProperty("name", out var np) ? np.GetString() ?? "" : "";
        var issuer = raw.TryGetProperty("issuer", out var ip) ? Address.Of(ip.GetString() ?? "") : Address.Of("");
        var fields = new List<string>();
        if (raw.TryGetProperty("fields", out var fp) && fp.ValueKind == JsonValueKind.Array)
        {
            foreach (var item in fp.EnumerateArray())
                fields.Add(item.GetString() ?? "");
        }
        return new CredentialSchema(id, name, issuer, fields);
    }

    /// <summary>List all credentials held by an address.</summary>
    public async Task<List<Credential>> ListCredentialsByHolderAsync(string holder, CancellationToken ct = default)
    {
        var result = await QueryContractAsync("credential", "list_by_holder", new { holder }, ct).ConfigureAwait(false);
        return ParseCredentialList(result, holder);
    }

    /// <summary>List all credentials issued by an address.</summary>
    public async Task<List<Credential>> ListCredentialsByIssuerAsync(string issuer, CancellationToken ct = default)
    {
        var result = await QueryContractAsync("credential", "list_by_issuer", new { issuer }, ct).ConfigureAwait(false);
        return ParseCredentialList(result, issuer);
    }

    // ── Multisig ──────────────────────────────────────────────────────────

    /// <summary>Create a new multisig wallet.</summary>
    public Task<SubmitResult> CreateMultisigAsync(string walletId, List<string> signers, int threshold, CancellationToken ct = default) =>
        CallContractAsync("multisig", "create", new { wallet_id = walletId, signers, threshold }, ct: ct);

    /// <summary>Propose a transaction on a multisig wallet.</summary>
    public Task<SubmitResult> ProposeTxAsync(string walletId, string contract, string method, object args, CancellationToken ct = default) =>
        CallContractAsync("multisig", "propose_tx", new { wallet_id = walletId, contract, method, args }, ct: ct);

    /// <summary>Approve a pending multisig transaction.</summary>
    public Task<SubmitResult> ApproveMultisigTxAsync(string walletId, string txId, CancellationToken ct = default) =>
        CallContractAsync("multisig", "approve", new { wallet_id = walletId, tx_id = txId }, ct: ct);

    /// <summary>Execute a multisig transaction that has reached threshold.</summary>
    public Task<SubmitResult> ExecuteMultisigTxAsync(string walletId, string txId, CancellationToken ct = default) =>
        CallContractAsync("multisig", "execute", new { wallet_id = walletId, tx_id = txId }, ct: ct);

    /// <summary>Revoke a previously given approval on a multisig transaction.</summary>
    public Task<SubmitResult> RevokeMultisigApprovalAsync(string walletId, string txId, CancellationToken ct = default) =>
        CallContractAsync("multisig", "revoke", new { wallet_id = walletId, tx_id = txId }, ct: ct);

    /// <summary>Add a signer to a multisig wallet.</summary>
    public Task<SubmitResult> AddMultisigSignerAsync(string walletId, string signer, CancellationToken ct = default) =>
        CallContractAsync("multisig", "add_signer", new { wallet_id = walletId, signer }, ct: ct);

    /// <summary>Remove a signer from a multisig wallet.</summary>
    public Task<SubmitResult> RemoveMultisigSignerAsync(string walletId, string signer, CancellationToken ct = default) =>
        CallContractAsync("multisig", "remove_signer", new { wallet_id = walletId, signer }, ct: ct);

    /// <summary>Fetch multisig wallet details.</summary>
    public async Task<MultisigWallet> GetMultisigWalletAsync(string walletId, CancellationToken ct = default)
    {
        var result = await QueryContractAsync("multisig", "wallet", new { wallet_id = walletId }, ct).ConfigureAwait(false);
        var raw = result.Value ?? default;
        var wId = raw.TryGetProperty("wallet_id", out var wp) ? wp.GetString() ?? walletId : walletId;
        var signers = new List<string>();
        if (raw.TryGetProperty("signers", out var sp) && sp.ValueKind == JsonValueKind.Array)
        {
            foreach (var item in sp.EnumerateArray())
                signers.Add(item.GetString() ?? "");
        }
        var threshold = raw.TryGetProperty("threshold", out var tp) && tp.TryGetInt32(out var tv) ? tv : 0;
        return new MultisigWallet(wId, signers, threshold);
    }

    /// <summary>Fetch a single pending multisig transaction.</summary>
    public async Task<MultisigTx> GetMultisigTxAsync(string walletId, string txId, CancellationToken ct = default)
    {
        var result = await QueryContractAsync("multisig", "pending_tx", new { wallet_id = walletId, tx_id = txId }, ct).ConfigureAwait(false);
        var raw = result.Value ?? default;
        var tId = raw.TryGetProperty("tx_id", out var tip) ? tip.GetString() ?? txId : txId;
        var contract = raw.TryGetProperty("contract", out var cp) ? cp.GetString() ?? "" : "";
        var method = raw.TryGetProperty("method", out var mp) ? mp.GetString() ?? "" : "";
        var approvals = new List<string>();
        if (raw.TryGetProperty("approvals", out var ap) && ap.ValueKind == JsonValueKind.Array)
        {
            foreach (var item in ap.EnumerateArray())
                approvals.Add(item.GetString() ?? "");
        }
        return new MultisigTx(tId, contract, method, approvals);
    }

    /// <summary>List all pending transactions for a multisig wallet.</summary>
    public async Task<List<MultisigTx>> ListMultisigPendingTxsAsync(string walletId, CancellationToken ct = default)
    {
        var result = await QueryContractAsync("multisig", "pending_txs", new { wallet_id = walletId }, ct).ConfigureAwait(false);
        var list = new List<MultisigTx>();
        if (result.Value is JsonElement el && el.ValueKind == JsonValueKind.Array)
        {
            foreach (var item in el.EnumerateArray())
            {
                var tId = item.TryGetProperty("tx_id", out var tip) ? tip.GetString() ?? "" : "";
                var contract = item.TryGetProperty("contract", out var cp) ? cp.GetString() ?? "" : "";
                var method = item.TryGetProperty("method", out var mp) ? mp.GetString() ?? "" : "";
                var approvals = new List<string>();
                if (item.TryGetProperty("approvals", out var ap) && ap.ValueKind == JsonValueKind.Array)
                {
                    foreach (var a in ap.EnumerateArray())
                        approvals.Add(a.GetString() ?? "");
                }
                list.Add(new MultisigTx(tId, contract, method, approvals));
            }
        }
        return list;
    }

    // ── Shielded pool ────────────────────────────────────────────────────

    /// <summary>Deposit into the shielded pool.</summary>
    public Task<SubmitResult> ShieldedDepositAsync(string commitment, long value, string proofHex, CancellationToken ct = default) =>
        CallContractAsync("shielded", "deposit", new { commitment, value, proof = proofHex }, ct: ct);

    /// <summary>Withdraw from the shielded pool.</summary>
    public Task<SubmitResult> ShieldedWithdrawAsync(string nullifier, long amount, string recipient, string proofHex, string commitmentRoot, CancellationToken ct = default) =>
        CallContractAsync("shielded", "withdraw", new { nullifier, amount, recipient, proof = proofHex, commitment_root = commitmentRoot }, ct: ct);

    /// <summary>Query the current commitment Merkle root from the shielded pool.</summary>
    public async Task<string> GetCommitmentRootAsync(CancellationToken ct = default)
    {
        var result = await QueryContractAsync("shielded", "commitment_root", ct: ct).ConfigureAwait(false);
        return result.Value?.ToString() ?? "";
    }

    /// <summary>Check whether a nullifier has already been spent.</summary>
    public async Task<bool> IsNullifierSpentAsync(string nullifier, CancellationToken ct = default)
    {
        var result = await QueryContractAsync("shielded", "is_nullifier_spent", new { nullifier }, ct).ConfigureAwait(false);
        if (result.Value is JsonElement el && el.ValueKind == JsonValueKind.True)
            return true;
        return false;
    }

    // ── Static helpers ───────────────────────────────────────────────────

    /// <summary>Build a contract call envelope.</summary>
    public static Dictionary<string, object> BuildContractCall(string contract, string method, object? args = null, string? paymaster = null)
    {
        var call = new Dictionary<string, object>
        {
            ["contract"] = contract,
            ["method"] = method,
            ["args"] = args ?? new { }
        };
        if (paymaster is not null)
            call["paymaster"] = paymaster;
        return call;
    }

    /// <summary>Build the canonical payload for a deploy or upgrade request (keys sorted for deterministic signing).</summary>
    public static Dictionary<string, object> BuildDeployCanonicalPayload(string from, string name, string bytecodeHash, long nonce, string chainId)
    {
        // Sorted alphabetically by key for deterministic signing
        return new Dictionary<string, object>(StringComparer.Ordinal)
        {
            ["bytecode_hash"] = bytecodeHash,
            ["chain_id"] = chainId,
            ["from"] = from,
            ["name"] = name,
            ["nonce"] = nonce,
        };
    }

    /// <summary>Read a WASM file from disk and return its contents as a hex string.</summary>
    public static string ReadWasmFileHex(string path) =>
        Convert.ToHexString(File.ReadAllBytes(path)).ToLowerInvariant();

    // ── IDisposable ──────────────────────────────────────────────────────

    /// <inheritdoc />
    public void Dispose()
    {
        if (_ownsHttpClient)
            _httpClient.Dispose();
    }

    // ── Private helpers ──────────────────────────────────────────────────

    private static SubmitResult ParseSubmitResult(JsonElement raw)
    {
        var accepted = raw.TryGetProperty("accepted", out var a) && a.GetBoolean();
        var hash = raw.TryGetProperty("tx_hash", out var h) ? h.GetString() ?? ""
                 : raw.TryGetProperty("txHash", out var h2) ? h2.GetString() ?? "" : "";
        return new SubmitResult(accepted, TxHash.Of(hash));
    }

    private static Receipt ParseReceipt(JsonElement raw, TxHash fallbackHash)
    {
        var hash = raw.TryGetProperty("tx_hash", out var h) ? TxHash.Of(h.GetString() ?? fallbackHash.Value)
                 : raw.TryGetProperty("txHash", out var h2) ? TxHash.Of(h2.GetString() ?? fallbackHash.Value)
                 : fallbackHash;
        var height = GetLongProp(raw, "block_height", "blockHeight");
        var status = raw.TryGetProperty("status", out var s) ? s.GetString() ?? "unknown" : "unknown";
        var result = raw.TryGetProperty("result", out var r) ? (JsonElement?)r.Clone() : null;
        var error = raw.TryGetProperty("error", out var e) && e.ValueKind != JsonValueKind.Null ? e.GetString() : null;
        var gasUsed = GetLongProp(raw, "gas_used", "gasUsed");
        var feePaid = GetLongProp(raw, "fee_paid", "feePaid");
        return new Receipt(hash, height, status, result, error, gasUsed, feePaid);
    }

    private static long GetLongProp(JsonElement el, string snakeCase, string camelCase)
    {
        if (el.TryGetProperty(snakeCase, out var v) && v.TryGetInt64(out var val)) return val;
        if (el.TryGetProperty(camelCase, out var v2) && v2.TryGetInt64(out var val2)) return val2;
        return 0;
    }

    private static List<Credential> ParseCredentialList(QueryResult result, string fallbackAddress)
    {
        var list = new List<Credential>();
        if (result.Value is JsonElement el && el.ValueKind == JsonValueKind.Array)
        {
            foreach (var item in el.EnumerateArray())
            {
                var id = item.TryGetProperty("id", out var idp) ? idp.GetString() ?? "" : "";
                var schemaHash = item.TryGetProperty("schema_hash", out var sp) ? sp.GetString() ?? ""
                             : item.TryGetProperty("schemaHash", out var sp2) ? sp2.GetString() ?? "" : "";
                var issuer = item.TryGetProperty("issuer", out var ip) ? Address.Of(ip.GetString() ?? "") : Address.Of("");
                var holder = item.TryGetProperty("holder", out var hp) ? Address.Of(hp.GetString() ?? "") : Address.Of("");
                var claims = new Dictionary<string, string>();
                if (item.TryGetProperty("claims", out var cp) && cp.ValueKind == JsonValueKind.Object)
                {
                    foreach (var prop in cp.EnumerateObject())
                        claims[prop.Name] = prop.Value.GetString() ?? "";
                }
                var revoked = item.TryGetProperty("revoked", out var rp) && rp.ValueKind == JsonValueKind.True;
                list.Add(new Credential(id, schemaHash, issuer, holder, claims, revoked));
            }
        }
        return list;
    }

    private static Dictionary<string, object?> BuildDeployBody(DeployPayload p)
    {
        var body = new Dictionary<string, object?>
        {
            ["name"] = p.Name,
            ["bytecode"] = p.Bytecode,
            ["from"] = p.From,
            ["alg"] = p.Alg,
            ["pk"] = p.Pk,
            ["sig"] = p.Sig,
            ["nonce"] = p.Nonce,
            ["chain_id"] = p.ChainId,
        };
        if (p.Version is not null)
            body["version"] = p.Version;
        return body;
    }
}
