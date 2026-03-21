using System.Runtime.InteropServices;
using Dilithia.Sdk.Exceptions;
using Dilithia.Sdk.Models;

namespace Dilithia.Sdk.Crypto;

/// <summary>
/// P/Invoke bridge to the <c>dilithia_native_core</c> shared library.
/// This is a reference implementation — callers should prefer the
/// <see cref="IDilithiaCryptoAdapter"/> interface for testability.
/// </summary>
public sealed class NativeCryptoBridge : IDilithiaCryptoAdapter
{
    // ── Native imports ──────────────────────────────────────────────────

    [DllImport("dilithia_native_core", CallingConvention = CallingConvention.Cdecl)]
    private static extern IntPtr dilithia_generate_mnemonic();

    [DllImport("dilithia_native_core", CallingConvention = CallingConvention.Cdecl)]
    private static extern int dilithia_validate_mnemonic(string mnemonic);

    [DllImport("dilithia_native_core", CallingConvention = CallingConvention.Cdecl)]
    private static extern IntPtr dilithia_recover_hd_wallet(string mnemonic);

    [DllImport("dilithia_native_core", CallingConvention = CallingConvention.Cdecl)]
    private static extern IntPtr dilithia_recover_hd_wallet_account(string mnemonic, int accountIndex);

    [DllImport("dilithia_native_core", CallingConvention = CallingConvention.Cdecl)]
    private static extern IntPtr dilithia_sign_message(string secretKeyHex, string message);

    [DllImport("dilithia_native_core", CallingConvention = CallingConvention.Cdecl)]
    private static extern int dilithia_verify_message(string publicKeyHex, string message, string signatureHex);

    [DllImport("dilithia_native_core", CallingConvention = CallingConvention.Cdecl)]
    private static extern IntPtr dilithia_address_from_public_key(string publicKeyHex);

    [DllImport("dilithia_native_core", CallingConvention = CallingConvention.Cdecl)]
    private static extern IntPtr dilithia_validate_address(string addr);

    [DllImport("dilithia_native_core", CallingConvention = CallingConvention.Cdecl)]
    private static extern IntPtr dilithia_keygen();

    [DllImport("dilithia_native_core", CallingConvention = CallingConvention.Cdecl)]
    private static extern IntPtr dilithia_keygen_from_seed(string seedHex);

    [DllImport("dilithia_native_core", CallingConvention = CallingConvention.Cdecl)]
    private static extern IntPtr dilithia_seed_from_mnemonic(string mnemonic);

    [DllImport("dilithia_native_core", CallingConvention = CallingConvention.Cdecl)]
    private static extern IntPtr dilithia_derive_child_seed(string parentSeedHex, int index);

    [DllImport("dilithia_native_core", CallingConvention = CallingConvention.Cdecl)]
    private static extern int dilithia_constant_time_eq(string aHex, string bHex);

    [DllImport("dilithia_native_core", CallingConvention = CallingConvention.Cdecl)]
    private static extern IntPtr dilithia_hash_hex(string dataHex);

    [DllImport("dilithia_native_core", CallingConvention = CallingConvention.Cdecl)]
    private static extern void dilithia_set_hash_alg(string alg);

    [DllImport("dilithia_native_core", CallingConvention = CallingConvention.Cdecl)]
    private static extern IntPtr dilithia_current_hash_alg();

    [DllImport("dilithia_native_core", CallingConvention = CallingConvention.Cdecl)]
    private static extern int dilithia_hash_len_hex();

    [DllImport("dilithia_native_core", CallingConvention = CallingConvention.Cdecl)]
    private static extern void dilithia_string_free(IntPtr ptr);

    // ── Helpers ─────────────────────────────────────────────────────────

    private static string ConsumeNativeString(IntPtr ptr)
    {
        if (ptr == IntPtr.Zero)
            throw new DilithiaException("Native call returned null");
        try
        {
            return Marshal.PtrToStringUTF8(ptr) ?? throw new DilithiaException("Native call returned null string");
        }
        finally
        {
            dilithia_string_free(ptr);
        }
    }

    // ── IDilithiaCryptoAdapter ──────────────────────────────────────────

    /// <inheritdoc />
    public string GenerateMnemonic() => ConsumeNativeString(dilithia_generate_mnemonic());

    /// <inheritdoc />
    public void ValidateMnemonic(string mnemonic)
    {
        if (dilithia_validate_mnemonic(mnemonic) != 0)
            throw new DilithiaException("Invalid mnemonic");
    }

    /// <inheritdoc />
    public DilithiaAccount RecoverHdWallet(string mnemonic)
    {
        var json = ConsumeNativeString(dilithia_recover_hd_wallet(mnemonic));
        return ParseAccount(json);
    }

    /// <inheritdoc />
    public DilithiaAccount RecoverHdWalletAccount(string mnemonic, int accountIndex)
    {
        var json = ConsumeNativeString(dilithia_recover_hd_wallet_account(mnemonic, accountIndex));
        return ParseAccount(json);
    }

    /// <inheritdoc />
    public DilithiaAccount CreateHdWalletFileFromMnemonic(string mnemonic, string password) =>
        throw new NotSupportedException("CreateHdWalletFileFromMnemonic requires wallet-file FFI (not yet bound)");

    /// <inheritdoc />
    public DilithiaAccount CreateHdWalletAccountFromMnemonic(string mnemonic, string password, int accountIndex) =>
        throw new NotSupportedException("CreateHdWalletAccountFromMnemonic requires wallet-file FFI (not yet bound)");

    /// <inheritdoc />
    public DilithiaAccount RecoverWalletFile(Dictionary<string, object> walletFile, string mnemonic, string password) =>
        throw new NotSupportedException("RecoverWalletFile requires wallet-file FFI (not yet bound)");

    /// <inheritdoc />
    public Address AddressFromPublicKey(string publicKeyHex) =>
        Address.Of(ConsumeNativeString(dilithia_address_from_public_key(publicKeyHex)));

    /// <inheritdoc />
    public Address ValidateAddress(string addr) =>
        Address.Of(ConsumeNativeString(dilithia_validate_address(addr)));

    /// <inheritdoc />
    public Address AddressFromPkChecksummed(string publicKeyHex) =>
        AddressFromPublicKey(publicKeyHex); // Delegates to same native call

    /// <inheritdoc />
    public Address AddressWithChecksum(string rawAddr) =>
        ValidateAddress(rawAddr); // Returns checksummed version

    /// <inheritdoc />
    public void ValidatePublicKey(string publicKeyHex)
    {
        // Attempt to derive address; throws on invalid key
        AddressFromPublicKey(publicKeyHex);
    }

    /// <inheritdoc />
    public void ValidateSecretKey(string secretKeyHex)
    {
        // Sign a dummy message; throws on invalid key
        SignMessage(secretKeyHex, "validate");
    }

    /// <inheritdoc />
    public void ValidateSignature(string signatureHex)
    {
        if (string.IsNullOrEmpty(signatureHex))
            throw new DilithiaException("Invalid signature: empty");
    }

    /// <inheritdoc />
    public DilithiaSignature SignMessage(string secretKeyHex, string message)
    {
        var json = ConsumeNativeString(dilithia_sign_message(secretKeyHex, message));
        var doc = System.Text.Json.JsonDocument.Parse(json);
        var root = doc.RootElement;
        return new DilithiaSignature(
            root.GetProperty("algorithm").GetString() ?? "dilithium",
            root.GetProperty("signature").GetString() ?? ""
        );
    }

    /// <inheritdoc />
    public bool VerifyMessage(string publicKeyHex, string message, string signatureHex) =>
        dilithia_verify_message(publicKeyHex, message, signatureHex) != 0;

    /// <inheritdoc />
    public DilithiaKeypair Keygen()
    {
        var json = ConsumeNativeString(dilithia_keygen());
        return ParseKeypair(json);
    }

    /// <inheritdoc />
    public DilithiaKeypair KeygenFromSeed(string seedHex)
    {
        var json = ConsumeNativeString(dilithia_keygen_from_seed(seedHex));
        return ParseKeypair(json);
    }

    /// <inheritdoc />
    public string SeedFromMnemonic(string mnemonic) =>
        ConsumeNativeString(dilithia_seed_from_mnemonic(mnemonic));

    /// <inheritdoc />
    public string DeriveChildSeed(string parentSeedHex, int index) =>
        ConsumeNativeString(dilithia_derive_child_seed(parentSeedHex, index));

    /// <inheritdoc />
    public bool ConstantTimeEq(string aHex, string bHex) =>
        dilithia_constant_time_eq(aHex, bHex) != 0;

    /// <inheritdoc />
    public string HashHex(string dataHex) =>
        ConsumeNativeString(dilithia_hash_hex(dataHex));

    /// <inheritdoc />
    public void SetHashAlg(string alg) =>
        dilithia_set_hash_alg(alg);

    /// <inheritdoc />
    public string CurrentHashAlg() =>
        ConsumeNativeString(dilithia_current_hash_alg());

    /// <inheritdoc />
    public int HashLenHex() =>
        dilithia_hash_len_hex();

    // ── Private parse helpers ───────────────────────────────────────────

    private static DilithiaAccount ParseAccount(string json)
    {
        var doc = System.Text.Json.JsonDocument.Parse(json);
        var r = doc.RootElement;
        return new DilithiaAccount(
            Address: r.GetProperty("address").GetString() ?? "",
            PublicKey: r.GetProperty("public_key").GetString() ?? r.GetProperty("publicKey").GetString() ?? "",
            SecretKey: r.GetProperty("secret_key").GetString() ?? r.GetProperty("secretKey").GetString() ?? "",
            AccountIndex: r.TryGetProperty("account_index", out var idx) ? idx.GetInt32()
                        : r.TryGetProperty("accountIndex", out var idx2) ? idx2.GetInt32() : 0
        );
    }

    private static DilithiaKeypair ParseKeypair(string json)
    {
        var doc = System.Text.Json.JsonDocument.Parse(json);
        var r = doc.RootElement;
        return new DilithiaKeypair(
            SecretKey: r.GetProperty("secret_key").GetString() ?? r.GetProperty("secretKey").GetString() ?? "",
            PublicKey: r.GetProperty("public_key").GetString() ?? r.GetProperty("publicKey").GetString() ?? "",
            Address: r.GetProperty("address").GetString() ?? ""
        );
    }
}
