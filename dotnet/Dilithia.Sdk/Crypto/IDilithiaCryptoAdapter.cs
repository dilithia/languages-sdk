using Dilithia.Sdk.Models;

namespace Dilithia.Sdk.Crypto;

/// <summary>
/// Interface for post-quantum cryptographic operations backed by the native core library.
/// All methods may throw <see cref="Exceptions.DilithiaException"/> on invalid input.
/// </summary>
public interface IDilithiaCryptoAdapter
{
    /// <summary>Generate a new BIP-39 mnemonic phrase.</summary>
    string GenerateMnemonic();

    /// <summary>Validate a BIP-39 mnemonic phrase. Throws on invalid input.</summary>
    void ValidateMnemonic(string mnemonic);

    /// <summary>Recover the default HD wallet account (index 0) from a mnemonic.</summary>
    DilithiaAccount RecoverHdWallet(string mnemonic);

    /// <summary>Recover a specific HD wallet account by index.</summary>
    DilithiaAccount RecoverHdWalletAccount(string mnemonic, int accountIndex);

    /// <summary>Create an encrypted wallet file from a mnemonic and password.</summary>
    DilithiaAccount CreateHdWalletFileFromMnemonic(string mnemonic, string password);

    /// <summary>Create an encrypted wallet file for a specific account index.</summary>
    DilithiaAccount CreateHdWalletAccountFromMnemonic(string mnemonic, string password, int accountIndex);

    /// <summary>Recover an account from an encrypted wallet file.</summary>
    DilithiaAccount RecoverWalletFile(Dictionary<string, object> walletFile, string mnemonic, string password);

    /// <summary>Derive an address from a public key (hex).</summary>
    Address AddressFromPublicKey(string publicKeyHex);

    /// <summary>Validate an address string. Returns the normalized address or throws.</summary>
    Address ValidateAddress(string addr);

    /// <summary>Derive a checksummed address from a public key (hex).</summary>
    Address AddressFromPkChecksummed(string publicKeyHex);

    /// <summary>Add a checksum to a raw address.</summary>
    Address AddressWithChecksum(string rawAddr);

    /// <summary>Validate a public key (hex). Throws on invalid input.</summary>
    void ValidatePublicKey(string publicKeyHex);

    /// <summary>Validate a secret key (hex). Throws on invalid input.</summary>
    void ValidateSecretKey(string secretKeyHex);

    /// <summary>Validate a signature (hex). Throws on invalid input.</summary>
    void ValidateSignature(string signatureHex);

    /// <summary>Sign a message with a secret key.</summary>
    DilithiaSignature SignMessage(string secretKeyHex, string message);

    /// <summary>Verify a message signature.</summary>
    bool VerifyMessage(string publicKeyHex, string message, string signatureHex);

    /// <summary>Generate a new key pair.</summary>
    DilithiaKeypair Keygen();

    /// <summary>Generate a key pair from a seed (hex).</summary>
    DilithiaKeypair KeygenFromSeed(string seedHex);

    /// <summary>Derive a seed from a mnemonic phrase.</summary>
    string SeedFromMnemonic(string mnemonic);

    /// <summary>Derive a child seed from a parent seed at a given index.</summary>
    string DeriveChildSeed(string parentSeedHex, int index);

    /// <summary>Constant-time comparison of two hex strings.</summary>
    bool ConstantTimeEq(string aHex, string bHex);

    /// <summary>Hash data (hex) using the current hash algorithm.</summary>
    string HashHex(string dataHex);

    /// <summary>Set the active hash algorithm (e.g. "sha3-256", "blake3").</summary>
    void SetHashAlg(string alg);

    /// <summary>Get the currently active hash algorithm name.</summary>
    string CurrentHashAlg();

    /// <summary>Get the output length (in hex chars) of the current hash algorithm.</summary>
    int HashLenHex();
}
