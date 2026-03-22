namespace Dilithia.Sdk.Models;

/// <summary>
/// A multisig wallet stored on-chain.
/// </summary>
/// <param name="WalletId">The unique wallet identifier.</param>
/// <param name="Signers">The list of authorised signer addresses.</param>
/// <param name="Threshold">The number of approvals required to execute a transaction.</param>
public record MultisigWallet(string WalletId, List<string> Signers, int Threshold);
