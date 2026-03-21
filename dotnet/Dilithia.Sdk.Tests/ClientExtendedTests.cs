using System.Net;
using System.Text.Json;
using Xunit;
using Dilithia.Sdk.Exceptions;
using Dilithia.Sdk.Models;

namespace Dilithia.Sdk.Tests;

public class ClientExtendedTests
{
    private static DilithiaClient BuildClient(Func<HttpRequestMessage, HttpResponseMessage> handler)
    {
        var mockHandler = new MockHttpMessageHandler(handler);
        return DilithiaClient.Create("http://localhost:9070/rpc")
            .WithHttpClient(new HttpClient(mockHandler))
            .Build();
    }

    // ── QueryContract ─────────────────────────────────────────────────

    [Fact]
    public async Task QueryContractAsync_with_args()
    {
        using var client = BuildClient(req =>
        {
            Assert.Contains("/query?", req.RequestUri!.ToString());
            Assert.Contains("contract=token", req.RequestUri.ToString());
            Assert.Contains("method=balance_of", req.RequestUri.ToString());
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"value":42}""")
            };
        });

        var result = await client.QueryContractAsync("token", "balance_of", new { address = "dili1x" });
        Assert.NotNull(result.Value);
    }

    [Fact]
    public async Task QueryContractAsync_no_args()
    {
        using var client = BuildClient(req =>
        {
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"value":"hello"}""")
            };
        });

        var result = await client.QueryContractAsync("config", "get_version");
        Assert.NotNull(result.Value);
    }

    // ── DeployContract ────────────────────────────────────────────────

    [Fact]
    public async Task DeployContractAsync_sends_payload()
    {
        using var client = BuildClient(req =>
        {
            Assert.Contains("/deploy", req.RequestUri!.ToString());
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"accepted":true,"tx_hash":"0xdeploy1"}""")
            };
        });

        var payload = new DeployPayload("myContract", "0x00", "dili1me", "dilithium", "0xpk", "0xsig", 1, "chain-1");
        var result = await client.DeployContractAsync(payload);
        Assert.True(result.Accepted);
        Assert.Equal("0xdeploy1", result.TxHash.Value);
    }

    [Fact]
    public async Task DeployContractAsync_with_version()
    {
        using var client = BuildClient(req =>
        {
            var body = req.Content!.ReadAsStringAsync().Result;
            Assert.Contains("\"version\"", body);
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"accepted":true,"tx_hash":"0xupg"}""")
            };
        });

        var payload = new DeployPayload("myContract", "0x00", "dili1me", "dilithium", "0xpk", "0xsig", 2, "chain-1", Version: 2);
        var result = await client.DeployContractAsync(payload);
        Assert.True(result.Accepted);
    }

    // ── UpgradeContract ───────────────────────────────────────────────

    [Fact]
    public async Task UpgradeContractAsync_sends_to_upgrade_endpoint()
    {
        using var client = BuildClient(req =>
        {
            Assert.Contains("/upgrade", req.RequestUri!.ToString());
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"accepted":true,"tx_hash":"0xupg2"}""")
            };
        });

        var payload = new DeployPayload("myContract", "0x00", "dili1me", "dilithium", "0xpk", "0xsig", 2, "chain-1", Version: 2);
        var result = await client.UpgradeContractAsync(payload);
        Assert.True(result.Accepted);
        Assert.Equal("0xupg2", result.TxHash.Value);
    }

    // ── ReverseResolve ────────────────────────────────────────────────

    [Fact]
    public async Task ReverseResolveAsync_parses_response()
    {
        using var client = BuildClient(req =>
        {
            Assert.Contains("/names/reverse/dili1alice", req.RequestUri!.ToString());
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"name":"alice.dili","address":"dili1alice"}""")
            };
        });

        var record = await client.ReverseResolveAsync(Address.Of("dili1alice"));
        Assert.Equal("alice.dili", record.Name);
        Assert.Equal("dili1alice", record.Address.Value);
    }

    // ── Shielded pool operations ──────────────────────────────────────

    [Fact]
    public async Task ShieldedDepositAsync_calls_shielded_contract()
    {
        using var client = BuildClient(req =>
        {
            Assert.Contains("/call", req.RequestUri!.ToString());
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"accepted":true,"tx_hash":"0xshield1"}""")
            };
        });

        var result = await client.ShieldedDepositAsync("0xcommitment", 1000, "0xproof");
        Assert.True(result.Accepted);
        Assert.Equal("0xshield1", result.TxHash.Value);
    }

    [Fact]
    public async Task ShieldedWithdrawAsync_calls_shielded_contract()
    {
        using var client = BuildClient(req =>
        {
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"accepted":true,"tx_hash":"0xwithdraw1"}""")
            };
        });

        var result = await client.ShieldedWithdrawAsync("0xnullifier", 500, "dili1bob", "0xproof", "0xroot");
        Assert.True(result.Accepted);
    }

    [Fact]
    public async Task GetCommitmentRootAsync_returns_root()
    {
        using var client = BuildClient(req =>
        {
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"value":"0xmerkleroot"}""")
            };
        });

        var root = await client.GetCommitmentRootAsync();
        Assert.Contains("merkleroot", root);
    }

    [Fact]
    public async Task IsNullifierSpentAsync_returns_true()
    {
        using var client = BuildClient(req =>
        {
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"value":true}""")
            };
        });

        var spent = await client.IsNullifierSpentAsync("0xnull");
        Assert.True(spent);
    }

    [Fact]
    public async Task IsNullifierSpentAsync_returns_false()
    {
        using var client = BuildClient(req =>
        {
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"value":false}""")
            };
        });

        var spent = await client.IsNullifierSpentAsync("0xnull");
        Assert.False(spent);
    }

    // ── WaitForReceipt ────────────────────────────────────────────────

    [Fact]
    public async Task WaitForReceiptAsync_retries_on_404_then_succeeds()
    {
        int callCount = 0;
        using var client = BuildClient(req =>
        {
            callCount++;
            if (callCount < 3)
            {
                return new HttpResponseMessage(HttpStatusCode.NotFound)
                {
                    Content = new StringContent("not found")
                };
            }
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"tx_hash":"0xwait","block_height":50,"status":"confirmed","gas_used":1000,"fee_paid":50}""")
            };
        });

        var receipt = await client.WaitForReceiptAsync(TxHash.Of("0xwait"), maxAttempts: 5, delay: TimeSpan.FromMilliseconds(10));
        Assert.Equal("0xwait", receipt.TxHash.Value);
        Assert.Equal(50, receipt.BlockHeight);
    }

    [Fact]
    public async Task WaitForReceiptAsync_throws_timeout_after_max_attempts()
    {
        using var client = BuildClient(req =>
        {
            return new HttpResponseMessage(HttpStatusCode.NotFound)
            {
                Content = new StringContent("not found")
            };
        });

        await Assert.ThrowsAsync<DilithiaTimeoutException>(
            () => client.WaitForReceiptAsync(TxHash.Of("0xnever"), maxAttempts: 2, delay: TimeSpan.FromMilliseconds(10))
        );
    }

    // ── Receipt with result and error fields ──────────────────────────

    [Fact]
    public async Task GetReceiptAsync_with_result_field()
    {
        using var client = BuildClient(req =>
        {
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"tx_hash":"0xr","block_height":10,"status":"confirmed","result":{"data":"hello"},"gas_used":100,"fee_paid":10}""")
            };
        });

        var receipt = await client.GetReceiptAsync(TxHash.Of("0xr"));
        Assert.NotNull(receipt.Result);
        Assert.Null(receipt.Error);
    }

    [Fact]
    public async Task GetReceiptAsync_with_error_field()
    {
        using var client = BuildClient(req =>
        {
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"tx_hash":"0xe","block_height":10,"status":"failed","error":"out of gas","gas_used":100,"fee_paid":10}""")
            };
        });

        var receipt = await client.GetReceiptAsync(TxHash.Of("0xe"));
        Assert.Equal("out of gas", receipt.Error);
        Assert.Equal("failed", receipt.Status);
    }

    // ── Nonce parsing variants ────────────────────────────────────────

    [Fact]
    public async Task GetNonceAsync_parses_next_nonce_field()
    {
        using var client = BuildClient(req =>
        {
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"address":"dili1x","next_nonce":7}""")
            };
        });

        var nonce = await client.GetNonceAsync(Address.Of("dili1x"));
        Assert.Equal(7, nonce.NextNonce);
    }

    [Fact]
    public async Task GetNonceAsync_parses_camelCase_field()
    {
        using var client = BuildClient(req =>
        {
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"address":"dili1x","nextNonce":9}""")
            };
        });

        var nonce = await client.GetNonceAsync(Address.Of("dili1x"));
        Assert.Equal(9, nonce.NextNonce);
    }

    // ── NetworkInfo camelCase ─────────────────────────────────────────

    [Fact]
    public async Task GetNetworkInfoAsync_parses_camelCase()
    {
        using var client = BuildClient(req =>
        {
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"jsonrpc":"2.0","id":1,"result":{"chainId":"test-2","blockHeight":200,"baseFee":50}}""")
            };
        });

        var info = await client.GetNetworkInfoAsync();
        Assert.Equal("test-2", info.ChainId);
        Assert.Equal(200, info.BlockHeight);
        Assert.Equal(50, info.BaseFee);
    }

    // ── GasEstimate camelCase ─────────────────────────────────────────

    [Fact]
    public async Task GetGasEstimateAsync_parses_camelCase()
    {
        using var client = BuildClient(req =>
        {
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"jsonrpc":"2.0","id":1,"result":{"gasLimit":500000,"baseFee":200,"estimatedCost":50000000}}""")
            };
        });

        var estimate = await client.GetGasEstimateAsync();
        Assert.Equal(500_000, estimate.GasLimit);
        Assert.Equal(200, estimate.BaseFee);
    }

    // ── SubmitResult camelCase tx_hash ─────────────────────────────────

    [Fact]
    public async Task SendCallAsync_parses_camelCase_txHash()
    {
        using var client = BuildClient(req =>
        {
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"accepted":true,"txHash":"0xcamel"}""")
            };
        });

        var result = await client.SendCallAsync(new { test = true });
        Assert.Equal("0xcamel", result.TxHash.Value);
    }

    // ── Receipt camelCase fields ──────────────────────────────────────

    [Fact]
    public async Task GetReceiptAsync_parses_camelCase()
    {
        using var client = BuildClient(req =>
        {
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"txHash":"0xcr","blockHeight":77,"status":"ok","gasUsed":300,"feePaid":15}""")
            };
        });

        var receipt = await client.GetReceiptAsync(TxHash.Of("0xcr"));
        Assert.Equal("0xcr", receipt.TxHash.Value);
        Assert.Equal(77, receipt.BlockHeight);
        Assert.Equal(300, receipt.GasUsed);
        Assert.Equal(15, receipt.FeePaid);
    }

    // ── Static helpers ────────────────────────────────────────────────

    [Fact]
    public void ReadWasmFileHex_reads_file()
    {
        var tmpPath = Path.GetTempFileName();
        try
        {
            File.WriteAllBytes(tmpPath, [0x00, 0x61, 0x73, 0x6d, 0x01, 0x00, 0x00, 0x00]);
            var hex = DilithiaClient.ReadWasmFileHex(tmpPath);
            Assert.Equal("0061736d01000000", hex);
        }
        finally
        {
            File.Delete(tmpPath);
        }
    }

    [Fact]
    public void SdkVersion_is_set()
    {
        Assert.Equal("0.3.0", DilithiaClient.SdkVersion);
    }

    // ── Builder null guard ────────────────────────────────────────────

    [Fact]
    public void Builder_null_rpcUrl_throws()
    {
        Assert.Throws<ArgumentNullException>(() => new DilithiaClientBuilder(null!));
    }

    // ── HttpException with empty body ─────────────────────────────────

    [Fact]
    public void HttpException_empty_body()
    {
        var ex = new HttpException(404, "");
        Assert.Equal(404, ex.StatusCode);
        Assert.Equal("", ex.Body);
        Assert.Equal("HTTP 404", ex.Message);
    }

    // ── DilithiaException with inner exception ────────────────────────

    [Fact]
    public void DilithiaException_with_inner()
    {
        var inner = new InvalidOperationException("inner");
        var ex = new DilithiaException("outer", inner);
        Assert.Equal("outer", ex.Message);
        Assert.Same(inner, ex.InnerException);
    }

    // ── DilithiaTimeoutException properties ───────────────────────────

    [Fact]
    public void DilithiaTimeoutException_properties()
    {
        var ex = new DilithiaTimeoutException("GET /test");
        Assert.Equal("GET /test", ex.Operation);
        Assert.Contains("Timeout", ex.Message);
    }

    // ── RpcException properties ───────────────────────────────────────

    [Fact]
    public void RpcException_properties()
    {
        var ex = new RpcException(-32600, "Invalid request");
        Assert.Equal(-32600, ex.Code);
        Assert.Equal("Invalid request", ex.RpcMessage);
        Assert.Contains("-32600", ex.Message);
    }

    // ── Client without JWT or custom headers ──────────────────────────

    [Fact]
    public void Client_created_without_optional_config()
    {
        using var client = DilithiaClient.Create("http://localhost:9070/rpc")
            .WithHttpClient(new HttpClient(new MockHttpMessageHandler(_ => new HttpResponseMessage())))
            .Build();
        Assert.Equal("http://localhost:9070", client.BaseUrl);
    }

    // ── Client owns HttpClient when none provided ─────────────────────

    [Fact]
    public void Client_disposes_owned_http_client()
    {
        // Client created without WithHttpClient should create and own its own HttpClient
        var client = DilithiaClient.Create("http://localhost:9070/rpc").Build();
        // Should not throw
        client.Dispose();
    }

    // ── Timeout on GET ────────────────────────────────────────────────

    [Fact]
    public async Task GetBalanceAsync_timeout_throws_DilithiaTimeoutException()
    {
        var slowHandler = new MockHttpMessageHandler(req =>
        {
            Thread.Sleep(5000); // Simulate slow response
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("{}")
            };
        });

        using var client = DilithiaClient.Create("http://localhost:9070/rpc")
            .WithTimeout(TimeSpan.FromMilliseconds(50))
            .WithHttpClient(new HttpClient(slowHandler))
            .Build();

        await Assert.ThrowsAsync<DilithiaTimeoutException>(
            () => client.GetBalanceAsync(Address.Of("dili1x"))
        );
    }

    // ── Timeout on RPC call ───────────────────────────────────────────

    [Fact]
    public async Task GetNetworkInfoAsync_timeout_throws_DilithiaTimeoutException()
    {
        var slowHandler = new MockHttpMessageHandler(req =>
        {
            Thread.Sleep(5000);
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("{}")
            };
        });

        using var client = DilithiaClient.Create("http://localhost:9070/rpc")
            .WithTimeout(TimeSpan.FromMilliseconds(50))
            .WithHttpClient(new HttpClient(slowHandler))
            .Build();

        await Assert.ThrowsAsync<DilithiaTimeoutException>(
            () => client.GetNetworkInfoAsync()
        );
    }

    // ── Timeout on POST ───────────────────────────────────────────────

    [Fact]
    public async Task SendCallAsync_timeout_throws_DilithiaTimeoutException()
    {
        var slowHandler = new MockHttpMessageHandler(req =>
        {
            Thread.Sleep(5000);
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("{}")
            };
        });

        using var client = DilithiaClient.Create("http://localhost:9070/rpc")
            .WithTimeout(TimeSpan.FromMilliseconds(50))
            .WithHttpClient(new HttpClient(slowHandler))
            .Build();

        await Assert.ThrowsAsync<DilithiaTimeoutException>(
            () => client.SendCallAsync(new { test = true })
        );
    }

    // ── HTTP error on POST ────────────────────────────────────────────

    [Fact]
    public async Task SendCallAsync_http_error()
    {
        using var client = BuildClient(req =>
        {
            return new HttpResponseMessage(HttpStatusCode.BadRequest)
            {
                Content = new StringContent("bad request")
            };
        });

        await Assert.ThrowsAsync<HttpException>(
            () => client.SendCallAsync(new { test = true })
        );
    }

    // ── HTTP error on RPC call ────────────────────────────────────────

    [Fact]
    public async Task GetNetworkInfoAsync_http_error()
    {
        using var client = BuildClient(req =>
        {
            return new HttpResponseMessage(HttpStatusCode.ServiceUnavailable)
            {
                Content = new StringContent("service unavailable")
            };
        });

        await Assert.ThrowsAsync<HttpException>(
            () => client.GetNetworkInfoAsync()
        );
    }
}
