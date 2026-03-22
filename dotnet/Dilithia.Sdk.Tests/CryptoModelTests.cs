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

    // ── 0.5.0 ZK result records ─────────────────────────────────────────

    [Fact]
    public void PredicateProofResult_record()
    {
        var p = new PredicateProofResult("0xproof", "0xcommit", 18, 200, 1);
        Assert.Equal("0xproof", p.Proof);
        Assert.Equal("0xcommit", p.Commitment);
        Assert.Equal(18, p.Min);
        Assert.Equal(200, p.Max);
        Assert.Equal(1, p.DomainTag);
    }

    [Fact]
    public void PredicateProofResult_equality()
    {
        var a = new PredicateProofResult("p", "c", 18, 200, 1);
        var b = new PredicateProofResult("p", "c", 18, 200, 1);
        Assert.Equal(a, b);
        Assert.Equal(a.GetHashCode(), b.GetHashCode());
    }

    [Fact]
    public void TransferProofResult_record()
    {
        var t = new TransferProofResult("0xproof", 1000, 500, 800, 700);
        Assert.Equal("0xproof", t.Proof);
        Assert.Equal(1000, t.SenderPre);
        Assert.Equal(500, t.ReceiverPre);
        Assert.Equal(800, t.SenderPost);
        Assert.Equal(700, t.ReceiverPost);
    }

    [Fact]
    public void TransferProofResult_equality()
    {
        var a = new TransferProofResult("p", 1000, 500, 800, 700);
        var b = new TransferProofResult("p", 1000, 500, 800, 700);
        Assert.Equal(a, b);
        Assert.Equal(a.GetHashCode(), b.GetHashCode());
    }

    [Fact]
    public void MerkleProofResult_record()
    {
        var m = new MerkleProofResult("0xproof", "0xleaf", "0xroot", 4);
        Assert.Equal("0xproof", m.Proof);
        Assert.Equal("0xleaf", m.LeafHash);
        Assert.Equal("0xroot", m.Root);
        Assert.Equal(4, m.Depth);
    }

    [Fact]
    public void MerkleProofResult_equality()
    {
        var a = new MerkleProofResult("p", "leaf", "root", 3);
        var b = new MerkleProofResult("p", "leaf", "root", 3);
        Assert.Equal(a, b);
        Assert.Equal(a.GetHashCode(), b.GetHashCode());
    }

    [Fact]
    public void PredicateProofResult_with_expression()
    {
        var original = new PredicateProofResult("p", "c", 18, 200, 1);
        var modified = original with { Min = 21 };
        Assert.Equal(21, modified.Min);
        Assert.Equal("p", modified.Proof);
    }

    [Fact]
    public void TransferProofResult_with_expression()
    {
        var original = new TransferProofResult("p", 1000, 500, 800, 700);
        var modified = original with { SenderPost = 750 };
        Assert.Equal(750, modified.SenderPost);
    }

    [Fact]
    public void MerkleProofResult_with_expression()
    {
        var original = new MerkleProofResult("p", "leaf", "root", 3);
        var modified = original with { Depth = 5 };
        Assert.Equal(5, modified.Depth);
    }
}
