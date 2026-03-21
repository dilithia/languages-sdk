using Xunit;
using Dilithia.Sdk.Crypto;
using Dilithia.Sdk.Zk;

namespace Dilithia.Sdk.Tests;

public class CryptoModelTests
{
    [Fact]
    public void DilithiaAccount_record()
    {
        var acct = new DilithiaAccount("dili1addr", "0xpk", "0xsk", 0);
        Assert.Equal("dili1addr", acct.Address);
        Assert.Equal("0xpk", acct.PublicKey);
        Assert.Equal("0xsk", acct.SecretKey);
        Assert.Equal(0, acct.AccountIndex);
        Assert.Null(acct.WalletFile);
    }

    [Fact]
    public void DilithiaAccount_with_wallet_file()
    {
        var wallet = new Dictionary<string, object> { ["version"] = 1 };
        var acct = new DilithiaAccount("dili1addr", "0xpk", "0xsk", 0, wallet);
        Assert.NotNull(acct.WalletFile);
        Assert.Equal(1, acct.WalletFile!["version"]);
    }

    [Fact]
    public void DilithiaSignature_record()
    {
        var sig = new DilithiaSignature("dilithium", "0xsig123");
        Assert.Equal("dilithium", sig.Algorithm);
        Assert.Equal("0xsig123", sig.Signature);
    }

    [Fact]
    public void DilithiaKeypair_record()
    {
        var kp = new DilithiaKeypair("0xsk", "0xpk", "dili1addr");
        Assert.Equal("0xsk", kp.SecretKey);
        Assert.Equal("0xpk", kp.PublicKey);
        Assert.Equal("dili1addr", kp.Address);
    }

    // ── ZK model records ──────────────────────────────────────────────

    [Fact]
    public void Commitment_record()
    {
        var c = new Commitment("0xhash", 1000, "0xsecret", "0xnonce");
        Assert.Equal("0xhash", c.Hash);
        Assert.Equal(1000, c.Value);
        Assert.Equal("0xsecret", c.Secret);
        Assert.Equal("0xnonce", c.Nonce);
    }

    [Fact]
    public void Nullifier_record()
    {
        var n = new Nullifier("0xnullhash");
        Assert.Equal("0xnullhash", n.Hash);
    }

    [Fact]
    public void StarkProof_record()
    {
        var p = new StarkProof("0xproof", "0xvk", "0xinputs");
        Assert.Equal("0xproof", p.Proof);
        Assert.Equal("0xvk", p.Vk);
        Assert.Equal("0xinputs", p.Inputs);
    }
}
