namespace Dilithia.Sdk.Crypto;

/// <summary>
/// The result of signing a message.
/// </summary>
public record DilithiaSignature(string Algorithm, string Signature);
