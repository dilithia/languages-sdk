using Xunit;
using Dilithia.Sdk.Models;

namespace Dilithia.Sdk.Tests;

public class ModelTests
{
    // ── Address ──────────────────────────────────────────────────────────

    [Fact]
    public void Address_Of_and_ToString()
    {
        var addr = Address.Of("dili1abc");
        Assert.Equal("dili1abc", addr.Value);
        Assert.Equal("dili1abc", addr.ToString());
    }

    [Fact]
    public void Address_equality()
    {
        Assert.Equal(Address.Of("dili1x"), Address.Of("dili1x"));
        Assert.NotEqual(Address.Of("dili1x"), Address.Of("dili1y"));
    }

    [Fact]
    public void Address_implicit_string_conversion()
    {
        Address addr = Address.Of("dili1test");
        string s = addr;
        Assert.Equal("dili1test", s);
    }

    // ── TxHash ──────────────────────────────────────────────────────────

    [Fact]
    public void TxHash_Of_and_ToString()
    {
        var h = TxHash.Of("0xabc123");
        Assert.Equal("0xabc123", h.Value);
        Assert.Equal("0xabc123", h.ToString());
    }

    [Fact]
    public void TxHash_equality()
    {
        Assert.Equal(TxHash.Of("0x1"), TxHash.Of("0x1"));
        Assert.NotEqual(TxHash.Of("0x1"), TxHash.Of("0x2"));
    }

    // ── TokenAmount ─────────────────────────────────────────────────────

    [Fact]
    public void TokenAmount_Dili_from_string()
    {
        var t = TokenAmount.Dili("42.5");
        Assert.Equal(42.5m, t.Value);
        Assert.Equal(18, t.Decimals);
    }

    [Fact]
    public void TokenAmount_Dili_from_long()
    {
        var t = TokenAmount.Dili(100);
        Assert.Equal(100m, t.Value);
    }

    [Fact]
    public void TokenAmount_FromRaw_and_ToRaw_round_trip()
    {
        var t = TokenAmount.FromRaw(1_000_000, 6);
        Assert.Equal(1m, t.Value);
        Assert.Equal(1_000_000, t.ToRaw());
    }

    [Fact]
    public void TokenAmount_Formatted()
    {
        var t = TokenAmount.Dili("42.5");
        Assert.Equal("42.5", t.Formatted());
    }

    // ── Balance ─────────────────────────────────────────────────────────

    [Fact]
    public void Balance_record()
    {
        var b = new Balance(Address.Of("dili1x"), TokenAmount.Dili(10), "10000000000000000000");
        Assert.Equal("dili1x", b.Address.Value);
        Assert.Equal(10m, b.Value.Value);
    }

    // ── Nonce ───────────────────────────────────────────────────────────

    [Fact]
    public void Nonce_record()
    {
        var n = new Nonce(Address.Of("dili1x"), 42);
        Assert.Equal(42, n.NextNonce);
    }

    // ── Receipt ─────────────────────────────────────────────────────────

    [Fact]
    public void Receipt_record()
    {
        var r = new Receipt(TxHash.Of("0xabc"), 100, "confirmed", GasUsed: 5000, FeePaid: 100);
        Assert.Equal("confirmed", r.Status);
        Assert.Equal(5000, r.GasUsed);
        Assert.Null(r.Error);
    }

    // ── NetworkInfo ─────────────────────────────────────────────────────

    [Fact]
    public void NetworkInfo_record()
    {
        var info = new NetworkInfo("dilithia-testnet-1", 12345, 100);
        Assert.Equal("dilithia-testnet-1", info.ChainId);
        Assert.Equal(12345, info.BlockHeight);
    }

    // ── GasEstimate ─────────────────────────────────────────────────────

    [Fact]
    public void GasEstimate_record()
    {
        var g = new GasEstimate(1_000_000, 100, 100_000_000);
        Assert.Equal(1_000_000, g.GasLimit);
    }

    // ── SubmitResult ────────────────────────────────────────────────────

    [Fact]
    public void SubmitResult_record()
    {
        var r = new SubmitResult(true, TxHash.Of("0xhash"));
        Assert.True(r.Accepted);
        Assert.Equal("0xhash", r.TxHash.Value);
    }

    // ── NameRecord ──────────────────────────────────────────────────────

    [Fact]
    public void NameRecord_record()
    {
        var n = new NameRecord("alice.dili", Address.Of("dili1alice"));
        Assert.Equal("alice.dili", n.Name);
    }

    // ── DeployPayload ───────────────────────────────────────────────────

    [Fact]
    public void DeployPayload_record()
    {
        var p = new DeployPayload("myContract", "0xbytecode", "dili1me", "dilithium", "0xpk", "0xsig", 1, "chain-1");
        Assert.Equal("myContract", p.Name);
        Assert.Null(p.Version);

        var p2 = p with { Version = 2 };
        Assert.Equal(2, p2.Version);
    }

    // ── ContractAbi ─────────────────────────────────────────────────────

    [Fact]
    public void ContractAbi_record()
    {
        var abi = new ContractAbi("token", new List<AbiMethod>
        {
            new("transfer", true, true),
            new("balance_of", false, true),
        });
        Assert.Equal(2, abi.Methods.Count);
        Assert.True(abi.Methods[0].Mutates);
        Assert.False(abi.Methods[1].Mutates);
    }
}
