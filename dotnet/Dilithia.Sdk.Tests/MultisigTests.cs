using System.Net;
using Xunit;
using Dilithia.Sdk.Models;

namespace Dilithia.Sdk.Tests;

public class MultisigTests
{
    private static DilithiaClient BuildClient(Func<HttpRequestMessage, HttpResponseMessage> handler)
    {
        var mockHandler = new MockHttpMessageHandler(handler);
        return DilithiaClient.Create("http://localhost:9070/rpc")
            .WithHttpClient(new HttpClient(mockHandler))
            .Build();
    }

    // ── Mutations (all go through /call) ────────────────────────────────

    [Fact]
    public async Task CreateMultisigAsync_sends_call()
    {
        using var client = BuildClient(req =>
        {
            Assert.Contains("/call", req.RequestUri!.ToString());
            var body = req.Content!.ReadAsStringAsync().Result;
            Assert.Contains("\"contract\":\"multisig\"", body);
            Assert.Contains("\"method\":\"create\"", body);
            Assert.Contains("\"wallet_id\":\"wallet1\"", body);
            Assert.Contains("\"threshold\":2", body);
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"accepted":true,"tx_hash":"0xtest"}""")
            };
        });

        var result = await client.CreateMultisigAsync("wallet1", new List<string> { "dili1a", "dili1b", "dili1c" }, 2);
        Assert.True(result.Accepted);
        Assert.Equal("0xtest", result.TxHash.Value);
    }

    [Fact]
    public async Task ProposeTxAsync_sends_call()
    {
        using var client = BuildClient(req =>
        {
            var body = req.Content!.ReadAsStringAsync().Result;
            Assert.Contains("\"method\":\"propose_tx\"", body);
            Assert.Contains("\"wallet_id\":\"wallet1\"", body);
            Assert.Contains("\"contract\":\"token\"", body);
            Assert.Contains("\"method\":\"transfer\"", body);
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"accepted":true,"tx_hash":"0xtest"}""")
            };
        });

        var result = await client.ProposeTxAsync("wallet1", "token", "transfer", new { to = "dili1bob", amount = 100 });
        Assert.True(result.Accepted);
        Assert.Equal("0xtest", result.TxHash.Value);
    }

    [Fact]
    public async Task ApproveMultisigTxAsync_sends_call()
    {
        using var client = BuildClient(req =>
        {
            var body = req.Content!.ReadAsStringAsync().Result;
            Assert.Contains("\"method\":\"approve\"", body);
            Assert.Contains("\"wallet_id\":\"wallet1\"", body);
            Assert.Contains("\"tx_id\":\"tx1\"", body);
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"accepted":true,"tx_hash":"0xtest"}""")
            };
        });

        var result = await client.ApproveMultisigTxAsync("wallet1", "tx1");
        Assert.True(result.Accepted);
        Assert.Equal("0xtest", result.TxHash.Value);
    }

    [Fact]
    public async Task ExecuteMultisigTxAsync_sends_call()
    {
        using var client = BuildClient(req =>
        {
            var body = req.Content!.ReadAsStringAsync().Result;
            Assert.Contains("\"method\":\"execute\"", body);
            Assert.Contains("\"wallet_id\":\"wallet1\"", body);
            Assert.Contains("\"tx_id\":\"tx1\"", body);
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"accepted":true,"tx_hash":"0xtest"}""")
            };
        });

        var result = await client.ExecuteMultisigTxAsync("wallet1", "tx1");
        Assert.True(result.Accepted);
        Assert.Equal("0xtest", result.TxHash.Value);
    }

    [Fact]
    public async Task RevokeMultisigApprovalAsync_sends_call()
    {
        using var client = BuildClient(req =>
        {
            var body = req.Content!.ReadAsStringAsync().Result;
            Assert.Contains("\"method\":\"revoke\"", body);
            Assert.Contains("\"wallet_id\":\"wallet1\"", body);
            Assert.Contains("\"tx_id\":\"tx1\"", body);
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"accepted":true,"tx_hash":"0xtest"}""")
            };
        });

        var result = await client.RevokeMultisigApprovalAsync("wallet1", "tx1");
        Assert.True(result.Accepted);
        Assert.Equal("0xtest", result.TxHash.Value);
    }

    [Fact]
    public async Task AddMultisigSignerAsync_sends_call()
    {
        using var client = BuildClient(req =>
        {
            var body = req.Content!.ReadAsStringAsync().Result;
            Assert.Contains("\"method\":\"add_signer\"", body);
            Assert.Contains("\"wallet_id\":\"wallet1\"", body);
            Assert.Contains("\"signer\":\"dili1new\"", body);
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"accepted":true,"tx_hash":"0xtest"}""")
            };
        });

        var result = await client.AddMultisigSignerAsync("wallet1", "dili1new");
        Assert.True(result.Accepted);
        Assert.Equal("0xtest", result.TxHash.Value);
    }

    [Fact]
    public async Task RemoveMultisigSignerAsync_sends_call()
    {
        using var client = BuildClient(req =>
        {
            var body = req.Content!.ReadAsStringAsync().Result;
            Assert.Contains("\"method\":\"remove_signer\"", body);
            Assert.Contains("\"wallet_id\":\"wallet1\"", body);
            Assert.Contains("\"signer\":\"dili1old\"", body);
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"accepted":true,"tx_hash":"0xtest"}""")
            };
        });

        var result = await client.RemoveMultisigSignerAsync("wallet1", "dili1old");
        Assert.True(result.Accepted);
        Assert.Equal("0xtest", result.TxHash.Value);
    }

    // ── Queries ─────────────────────────────────────────────────────────

    [Fact]
    public async Task GetMultisigWalletAsync_parses_wallet()
    {
        using var client = BuildClient(req =>
        {
            Assert.Contains("/query", req.RequestUri!.ToString());
            Assert.Contains("contract=multisig", req.RequestUri.ToString());
            Assert.Contains("method=wallet", req.RequestUri.ToString());
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"value":{"wallet_id":"wallet1","signers":["dili1a","dili1b","dili1c"],"threshold":2}}""")
            };
        });

        var wallet = await client.GetMultisigWalletAsync("wallet1");
        Assert.Equal("wallet1", wallet.WalletId);
        Assert.Equal(3, wallet.Signers.Count);
        Assert.Contains("dili1a", wallet.Signers);
        Assert.Contains("dili1b", wallet.Signers);
        Assert.Contains("dili1c", wallet.Signers);
        Assert.Equal(2, wallet.Threshold);
    }

    [Fact]
    public async Task GetMultisigTxAsync_parses_tx()
    {
        using var client = BuildClient(req =>
        {
            Assert.Contains("method=pending_tx", req.RequestUri!.ToString());
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"value":{"tx_id":"tx1","contract":"token","method":"transfer","approvals":["dili1a","dili1b"]}}""")
            };
        });

        var tx = await client.GetMultisigTxAsync("wallet1", "tx1");
        Assert.Equal("tx1", tx.TxId);
        Assert.Equal("token", tx.Contract);
        Assert.Equal("transfer", tx.Method);
        Assert.Equal(2, tx.Approvals.Count);
        Assert.Contains("dili1a", tx.Approvals);
        Assert.Contains("dili1b", tx.Approvals);
    }

    [Fact]
    public async Task ListMultisigPendingTxsAsync_parses_list()
    {
        using var client = BuildClient(req =>
        {
            Assert.Contains("method=pending_txs", req.RequestUri!.ToString());
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"value":[{"tx_id":"tx1","contract":"token","method":"transfer","approvals":["dili1a"]},{"tx_id":"tx2","contract":"staking","method":"delegate","approvals":[]}]}""")
            };
        });

        var txs = await client.ListMultisigPendingTxsAsync("wallet1");
        Assert.Equal(2, txs.Count);
        Assert.Equal("tx1", txs[0].TxId);
        Assert.Equal("token", txs[0].Contract);
        Assert.Single(txs[0].Approvals);
        Assert.Equal("tx2", txs[1].TxId);
        Assert.Equal("staking", txs[1].Contract);
        Assert.Empty(txs[1].Approvals);
    }

    [Fact]
    public async Task ListMultisigPendingTxsAsync_returns_empty_on_non_array()
    {
        using var client = BuildClient(req =>
        {
            return new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""{"value":null}""")
            };
        });

        var txs = await client.ListMultisigPendingTxsAsync("wallet1");
        Assert.Empty(txs);
    }
}
