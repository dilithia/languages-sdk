namespace Dilithia.Sdk.Crypto;

/// <summary>
/// Represents a recovered Dilithia HD wallet account.
/// </summary>
public record DilithiaAccount(
    string Address,
    string PublicKey,
    string SecretKey,
    int AccountIndex,
    Dictionary<string, object>? WalletFile = null
);
