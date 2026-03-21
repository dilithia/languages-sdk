using System.Net;
using Xunit;
using Dilithia.Sdk.Exceptions;
using Dilithia.Sdk.Models;

namespace Dilithia.Sdk.Tests;

/// <summary>
/// A mock <see cref="HttpMessageHandler"/> that delegates to a user-supplied function.
/// </summary>
internal sealed class MockHttpMessageHandler : HttpMessageHandler
{
    private readonly Func<HttpRequestMessage, HttpResponseMessage> _handler;

    public MockHttpMessageHandler(Func<HttpRequestMessage, HttpResponseMessage> handler)
    {
        _handler = handler;
    }

    protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken) =>
        Task.FromResult(_handler(request));
}

public class ClientTests
{
    private static DilithiaClient BuildClient(Func<HttpRequestMessage, HttpResponseMessage> handler)
    {
        var mockHandler = new MockHttpMessageHandler(handler);
        return DilithiaClient.Create("http://localhost:9070/rpc")
            .WithHttpClient(new HttpClient(mockHandler))
            .Build();
    }

    // ── URL construction ────────────────────────────────────────────────

    [Fact]
    public void Create_strips_trailing_slash()
    {
        using var client = DilithiaClient.Create("http://localhost:9070/rpc/")
            .WithHttpClient(new HttpClient(new MockHttpMessageHandler(_ => new HttpResponseMessage())))
            .Build();
        Assert.Equal("http://localhost:9070/rpc", client.RpcUrl);
    }

    [Fact]
    public void BaseUrl_derived_from_rpcUrl()
    {
        using var client = DilithiaClient.Create("http://localhost:9070/rpc")
            .WithHttpClient(new HttpClient(new MockHttpMessageHandler(_ => new HttpResponseMessage())))
            .Build();
        Assert.Equal("http://localhost:9070", client.BaseUrl);
    }

    [Fact]
    public void BaseUrl_can_be_overridden()
    {
        using var client = DilithiaClient.Create("http://localhost:9070/rpc")
            .WithChainBaseUrl("http://custom:8080")
            .WithHttpClient(new HttpClient(new MockHttpMessageHandler(_ => new HttpResponseMessage())))
            .Build();
        Assert.Equal("http://custom:8080", client.BaseUrl);
    }

    // ── Builder pattern ─────────────────────────────────────────────────

    [Fact]
    public void Builder_fluent_chain()
    {
        using var client = DilithiaClient.Create("http://localhost:9070/rpc")
            .WithTimeout(TimeSpan.FromSeconds(30))
            .WithJwt("my-token")
            .WithHeader("X-Custom", "value")
            .WithHttpClient(new HttpClient(new MockHttpMessageHandler(_ => new HttpResponseMessage())))
            .Build();

        Assert.NotNull(client);
        Assert.Equal("http://localhost:9070/rpc", client.RpcUrl);
    }

    // ── GetBalance ──────────────────────────────────────────────────────

    [Fact]
    public async Task GetBalanceAsync_parses_response()
    {
        using var client = BuildClient(req =>
        {
            Assert.Contains("/balance/dili1test", req.RequestUri!.ToString());
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"address":"dili1test","balance":"1000000"}""")
            };
        });

        var balance = await client.GetBalanceAsync(Address.Of("dili1test"));
        Assert.Equal("dili1test", balance.Address.Value);
        Assert.Equal("1000000", balance.RawValue);
    }

    // ── GetNonce ────────────────────────────────────────────────────────

    [Fact]
    public async Task GetNonceAsync_parses_response()
    {
        using var client = BuildClient(req =>
        {
            Assert.Contains("/nonce/dili1test", req.RequestUri!.ToString());
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"address":"dili1test","nonce":42}""")
            };
        });

        var nonce = await client.GetNonceAsync(Address.Of("dili1test"));
        Assert.Equal(42, nonce.NextNonce);
    }

    // ── GetReceipt ──────────────────────────────────────────────────────

    [Fact]
    public async Task GetReceiptAsync_parses_response()
    {
        using var client = BuildClient(req =>
        {
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"tx_hash":"0xabc","block_height":100,"status":"confirmed","gas_used":5000,"fee_paid":100}""")
            };
        });

        var receipt = await client.GetReceiptAsync(TxHash.Of("0xabc"));
        Assert.Equal("0xabc", receipt.TxHash.Value);
        Assert.Equal(100, receipt.BlockHeight);
        Assert.Equal("confirmed", receipt.Status);
        Assert.Equal(5000, receipt.GasUsed);
    }

    // ── GetNetworkInfo ──────────────────────────────────────────────────

    [Fact]
    public async Task GetNetworkInfoAsync_parses_rpc_response()
    {
        using var client = BuildClient(req =>
        {
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"jsonrpc":"2.0","id":1,"result":{"chain_id":"testnet-1","block_height":500,"base_fee":100}}""")
            };
        });

        var info = await client.GetNetworkInfoAsync();
        Assert.Equal("testnet-1", info.ChainId);
        Assert.Equal(500, info.BlockHeight);
        Assert.Equal(100, info.BaseFee);
    }

    // ── GetGasEstimate ──────────────────────────────────────────────────

    [Fact]
    public async Task GetGasEstimateAsync_parses_rpc_response()
    {
        using var client = BuildClient(req =>
        {
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"jsonrpc":"2.0","id":1,"result":{"gas_limit":1000000,"base_fee":100,"estimated_cost":100000000}}""")
            };
        });

        var estimate = await client.GetGasEstimateAsync();
        Assert.Equal(1_000_000, estimate.GasLimit);
        Assert.Equal(100, estimate.BaseFee);
        Assert.Equal(100_000_000, estimate.EstimatedCost);
    }

    // ── SendCall ────────────────────────────────────────────────────────

    [Fact]
    public async Task SendCallAsync_parses_submit_result()
    {
        using var client = BuildClient(req =>
        {
            Assert.Contains("/call", req.RequestUri!.ToString());
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"accepted":true,"tx_hash":"0xhash123"}""")
            };
        });

        var result = await client.SendCallAsync(new { contract = "token", method = "transfer" });
        Assert.True(result.Accepted);
        Assert.Equal("0xhash123", result.TxHash.Value);
    }

    // ── CallContract ────────────────────────────────────────────────────

    [Fact]
    public async Task CallContractAsync_builds_and_submits()
    {
        using var client = BuildClient(req =>
        {
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"accepted":true,"tx_hash":"0xcall"}""")
            };
        });

        var result = await client.CallContractAsync("token", "transfer", new { to = "dili1bob", amount = 100 });
        Assert.True(result.Accepted);
    }

    // ── ResolveName ─────────────────────────────────────────────────────

    [Fact]
    public async Task ResolveNameAsync_parses_response()
    {
        using var client = BuildClient(req =>
        {
            Assert.Contains("/names/resolve/alice.dili", req.RequestUri!.ToString());
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"name":"alice.dili","address":"dili1alice"}""")
            };
        });

        var record = await client.ResolveNameAsync("alice.dili");
        Assert.Equal("alice.dili", record.Name);
        Assert.Equal("dili1alice", record.Address.Value);
    }

    // ── HTTP error handling ─────────────────────────────────────────────

    [Fact]
    public async Task HttpException_on_non_2xx()
    {
        using var client = BuildClient(req =>
        {
            return new HttpResponseMessage(HttpStatusCode.InternalServerError)
            {
                Content = new StringContent("internal server error")
            };
        });

        var ex = await Assert.ThrowsAsync<HttpException>(() => client.GetBalanceAsync(Address.Of("dili1x")));
        Assert.Equal(500, ex.StatusCode);
        Assert.Contains("internal server error", ex.Body);
    }

    // ── RPC error handling ──────────────────────────────────────────────

    [Fact]
    public async Task RpcException_on_rpc_error()
    {
        using var client = BuildClient(req =>
        {
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"jsonrpc":"2.0","id":1,"error":{"code":-32601,"message":"Method not found"}}""")
            };
        });

        var ex = await Assert.ThrowsAsync<RpcException>(() => client.GetNetworkInfoAsync());
        Assert.Equal(-32601, ex.Code);
        Assert.Equal("Method not found", ex.RpcMessage);
    }

    // ── Static helpers ──────────────────────────────────────────────────

    [Fact]
    public void BuildContractCall_basic()
    {
        var call = DilithiaClient.BuildContractCall("token", "transfer", new { to = "bob", amount = 100 });
        Assert.Equal("token", call["contract"]);
        Assert.Equal("transfer", call["method"]);
        Assert.NotNull(call["args"]);
        Assert.False(call.ContainsKey("paymaster"));
    }

    [Fact]
    public void BuildContractCall_with_paymaster()
    {
        var call = DilithiaClient.BuildContractCall("token", "transfer", paymaster: "dili1sponsor");
        Assert.Equal("dili1sponsor", call["paymaster"]);
    }

    [Fact]
    public void BuildDeployCanonicalPayload_sorted_keys()
    {
        var payload = DilithiaClient.BuildDeployCanonicalPayload("dili1me", "myContract", "0xhash", 1, "chain-1");
        var keys = payload.Keys.ToList();
        // Must be sorted alphabetically for deterministic signing
        Assert.Equal("bytecode_hash", keys[0]);
        Assert.Equal("chain_id", keys[1]);
        Assert.Equal("from", keys[2]);
        Assert.Equal("name", keys[3]);
        Assert.Equal("nonce", keys[4]);
    }

    // ── Dispose ─────────────────────────────────────────────────────────

    [Fact]
    public void Dispose_does_not_throw()
    {
        var client = DilithiaClient.Create("http://localhost/rpc")
            .WithHttpClient(new HttpClient(new MockHttpMessageHandler(_ => new HttpResponseMessage())))
            .Build();
        client.Dispose();
    }

    // ── Exception hierarchy ─────────────────────────────────────────────

    [Fact]
    public void Exception_hierarchy()
    {
        Assert.IsAssignableFrom<DilithiaException>(new RpcException(-1, "err"));
        Assert.IsAssignableFrom<DilithiaException>(new HttpException(500, "body"));
        Assert.IsAssignableFrom<DilithiaException>(new DilithiaTimeoutException("op"));
        Assert.IsAssignableFrom<Exception>(new DilithiaException("msg"));
    }
}
