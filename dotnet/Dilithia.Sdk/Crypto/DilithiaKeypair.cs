namespace Dilithia.Sdk.Crypto;

/// <summary>
/// A public/secret key pair with the derived address.
/// </summary>
public record DilithiaKeypair(string SecretKey, string PublicKey, string Address);
