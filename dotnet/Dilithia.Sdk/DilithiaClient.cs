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
    public const string SdkVersion = "0.3.0";

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
