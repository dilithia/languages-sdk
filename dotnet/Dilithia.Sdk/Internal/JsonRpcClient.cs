using System.Net.Http.Json;
using System.Text.Json;
using Dilithia.Sdk.Exceptions;

namespace Dilithia.Sdk.Internal;

/// <summary>
/// Low-level JSON-RPC 2.0 helper over <see cref="HttpClient"/>.
/// </summary>
internal sealed class JsonRpcClient
{
    private readonly HttpClient _http;
    private readonly string _rpcUrl;
    private readonly TimeSpan _timeout;
    private int _nextId;

    internal JsonRpcClient(HttpClient http, string rpcUrl, TimeSpan timeout)
    {
        _http = http;
        _rpcUrl = rpcUrl;
        _timeout = timeout;
    }

    /// <summary>
    /// Send a JSON-RPC request and return the <c>result</c> field.
    /// </summary>
    internal async Task<JsonElement> CallAsync(string method, object? @params = null, CancellationToken ct = default)
    {
        var id = Interlocked.Increment(ref _nextId);
        var envelope = new
        {
            jsonrpc = "2.0",
            id,
            method,
            @params = @params ?? new { }
        };

        using var cts = CancellationTokenSource.CreateLinkedTokenSource(ct);
        cts.CancelAfter(_timeout);

        HttpResponseMessage response;
        try
        {
            response = await _http.PostAsJsonAsync(_rpcUrl, envelope, cts.Token).ConfigureAwait(false);
        }
        catch (OperationCanceledException) when (!ct.IsCancellationRequested)
        {
            throw new DilithiaTimeoutException($"JSON-RPC call '{method}' exceeded {_timeout.TotalMilliseconds}ms");
        }

        var body = await response.Content.ReadAsStringAsync(cts.Token).ConfigureAwait(false);

        if (!response.IsSuccessStatusCode)
            throw new HttpException((int)response.StatusCode, body);

        using var doc = JsonDocument.Parse(body);
        var root = doc.RootElement;

        if (root.TryGetProperty("error", out var err) && err.ValueKind == JsonValueKind.Object)
        {
            var code = err.TryGetProperty("code", out var c) ? c.GetInt32() : -1;
            var msg = err.TryGetProperty("message", out var m) ? m.GetString() ?? "Unknown RPC error" : "Unknown RPC error";
            throw new RpcException(code, msg);
        }

        if (root.TryGetProperty("result", out var result))
            return result.Clone();

        return root.Clone();
    }

    /// <summary>
    /// Send a GET request and return parsed JSON.
    /// </summary>
    internal async Task<JsonElement> GetJsonAsync(string url, CancellationToken ct = default)
    {
        using var cts = CancellationTokenSource.CreateLinkedTokenSource(ct);
        cts.CancelAfter(_timeout);

        HttpResponseMessage response;
        try
        {
            response = await _http.GetAsync(url, cts.Token).ConfigureAwait(false);
        }
        catch (OperationCanceledException) when (!ct.IsCancellationRequested)
        {
            throw new DilithiaTimeoutException($"GET {url} exceeded {_timeout.TotalMilliseconds}ms");
        }

        var body = await response.Content.ReadAsStringAsync(cts.Token).ConfigureAwait(false);

        if (!response.IsSuccessStatusCode)
            throw new HttpException((int)response.StatusCode, body);

        using var doc = JsonDocument.Parse(body);
        return doc.RootElement.Clone();
    }

    /// <summary>
    /// Send a POST request with JSON body and return parsed JSON.
    /// </summary>
    internal async Task<JsonElement> PostJsonAsync(string url, object body, CancellationToken ct = default)
    {
        using var cts = CancellationTokenSource.CreateLinkedTokenSource(ct);
        cts.CancelAfter(_timeout);

        HttpResponseMessage response;
        try
        {
            response = await _http.PostAsJsonAsync(url, body, cts.Token).ConfigureAwait(false);
        }
        catch (OperationCanceledException) when (!ct.IsCancellationRequested)
        {
            throw new DilithiaTimeoutException($"POST {url} exceeded {_timeout.TotalMilliseconds}ms");
        }

        var responseBody = await response.Content.ReadAsStringAsync(cts.Token).ConfigureAwait(false);

        if (!response.IsSuccessStatusCode)
            throw new HttpException((int)response.StatusCode, responseBody);

        using var doc = JsonDocument.Parse(responseBody);
        return doc.RootElement.Clone();
    }
}
