package org.dilithia.sdk.crypto;

import com.sun.jna.Library;
import com.sun.jna.Native;
import java.util.LinkedHashMap;
import java.util.Map;
import org.dilithia.sdk.DilithiaAccount;
import org.dilithia.sdk.DilithiaCryptoAdapter;
import org.dilithia.sdk.DilithiaKeypair;
import org.dilithia.sdk.DilithiaSignature;

public final class NativeCryptoBridge implements DilithiaCryptoAdapter {
    private interface NativeCore extends Library {
        String dilithia_generate_mnemonic();
        String dilithia_create_wallet_file(String password);
        String dilithia_validate_mnemonic(String mnemonic);
        String dilithia_create_hd_wallet_file_from_mnemonic(String mnemonic, String password);
        String dilithia_create_hd_wallet_account_from_mnemonic(String mnemonic, String password, int accountIndex);
        String dilithia_recover_hd_account(String mnemonic, int accountIndex);
        String dilithia_recover_wallet_file(String walletFileJson, String mnemonic, String password);
        String dilithia_address_from_public_key(String publicKeyHex);
        String dilithia_sign_message(String secretKeyHex, String message);
        String dilithia_verify_message(String publicKeyHex, String message, String signatureHex);
        String dilithia_validate_address(String addr);
        String dilithia_address_from_pk_checksummed(String publicKeyHex);
        String dilithia_address_with_checksum(String rawAddr);
        String dilithia_validate_pk(String publicKeyHex);
        String dilithia_validate_sk(String secretKeyHex);
        String dilithia_validate_sig(String signatureHex);
        String dilithia_keygen_mldsa65();
        String dilithia_keygen_mldsa65_from_seed(String seedHex);
        String dilithia_seed_from_mnemonic(String mnemonic);
        String dilithia_derive_child_seed(String parentSeedHex, int index);
        String dilithia_constant_time_eq(String aHex, String bHex);
        String dilithia_hash_hex(String dataHex);
        String dilithia_set_hash_alg(String alg);
        String dilithia_current_hash_alg();
        String dilithia_hash_len_hex();
    }

    private final NativeCore nativeCore;

    public NativeCryptoBridge() {
        String path = System.getenv("DILITHIUM_NATIVE_CORE_LIB");
        if (path == null || path.isBlank()) {
            throw new IllegalStateException("DILITHIUM_NATIVE_CORE_LIB is not configured");
        }
        this.nativeCore = Native.load(path, NativeCore.class);
    }

    @Override
    public String generateMnemonic() {
        return (String) envelopeValue(nativeCore.dilithia_generate_mnemonic());
    }

    @Override
    public void validateMnemonic(String mnemonic) {
        envelopeValue(nativeCore.dilithia_validate_mnemonic(mnemonic));
    }

    @Override
    public DilithiaAccount recoverHdWallet(String mnemonic) {
        return recoverHdWalletAccount(mnemonic, 0);
    }

    @Override
    public DilithiaAccount recoverHdWalletAccount(String mnemonic, int accountIndex) {
        Map<String, Object> value = envelopeMap(nativeCore.dilithia_recover_hd_account(mnemonic, accountIndex));
        return new DilithiaAccount(
                (String) value.get("address"),
                (String) value.get("public_key"),
                (String) value.get("secret_key"),
                ((Number) value.get("account_index")).intValue(),
                null
        );
    }

    @Override
    public DilithiaAccount createHdWalletFileFromMnemonic(String mnemonic, String password) {
        return accountFromMap(envelopeMap(nativeCore.dilithia_create_hd_wallet_file_from_mnemonic(mnemonic, password)));
    }

    @Override
    public DilithiaAccount createHdWalletAccountFromMnemonic(String mnemonic, String password, int accountIndex) {
        return accountFromMap(
                envelopeMap(nativeCore.dilithia_create_hd_wallet_account_from_mnemonic(mnemonic, password, accountIndex))
        );
    }

    @Override
    public DilithiaAccount recoverWalletFile(Map<String, Object> walletFile, String mnemonic, String password) {
        return accountFromMap(
                envelopeMap(nativeCore.dilithia_recover_wallet_file(JsonMaps.stringify(walletFile), mnemonic, password))
        );
    }

    @Override
    public String addressFromPublicKey(String publicKeyHex) {
        Map<String, Object> value = envelopeMap(nativeCore.dilithia_address_from_public_key(publicKeyHex));
        return (String) value.get("address");
    }

    @Override
    public DilithiaSignature signMessage(String secretKeyHex, String message) {
        Map<String, Object> value = envelopeMap(nativeCore.dilithia_sign_message(secretKeyHex, message));
        return new DilithiaSignature((String) value.get("algorithm"), (String) value.get("signature"));
    }

    @Override
    public boolean verifyMessage(String publicKeyHex, String message, String signatureHex) {
        Map<String, Object> value = envelopeMap(nativeCore.dilithia_verify_message(publicKeyHex, message, signatureHex));
        return Boolean.TRUE.equals(value.get("ok"));
    }

    @Override
    public String validateAddress(String addr) {
        Map<String, Object> value = envelopeMap(nativeCore.dilithia_validate_address(addr));
        return (String) value.get("address");
    }

    @Override
    public String addressFromPkChecksummed(String publicKeyHex) {
        Map<String, Object> value = envelopeMap(nativeCore.dilithia_address_from_pk_checksummed(publicKeyHex));
        return (String) value.get("address");
    }

    @Override
    public String addressWithChecksum(String rawAddr) {
        Map<String, Object> value = envelopeMap(nativeCore.dilithia_address_with_checksum(rawAddr));
        return (String) value.get("address");
    }

    @Override
    public void validatePublicKey(String publicKeyHex) {
        envelopeValue(nativeCore.dilithia_validate_pk(publicKeyHex));
    }

    @Override
    public void validateSecretKey(String secretKeyHex) {
        envelopeValue(nativeCore.dilithia_validate_sk(secretKeyHex));
    }

    @Override
    public void validateSignature(String signatureHex) {
        envelopeValue(nativeCore.dilithia_validate_sig(signatureHex));
    }

    @Override
    public DilithiaKeypair keygen() {
        Map<String, Object> value = envelopeMap(nativeCore.dilithia_keygen_mldsa65());
        return new DilithiaKeypair(
                (String) value.get("secret_key"),
                (String) value.get("public_key"),
                (String) value.get("address")
        );
    }

    @Override
    public DilithiaKeypair keygenFromSeed(String seedHex) {
        Map<String, Object> value = envelopeMap(nativeCore.dilithia_keygen_mldsa65_from_seed(seedHex));
        return new DilithiaKeypair(
                (String) value.get("secret_key"),
                (String) value.get("public_key"),
                (String) value.get("address")
        );
    }

    @Override
    public String seedFromMnemonic(String mnemonic) {
        return (String) envelopeValue(nativeCore.dilithia_seed_from_mnemonic(mnemonic));
    }

    @Override
    public String deriveChildSeed(String parentSeedHex, int index) {
        return (String) envelopeValue(nativeCore.dilithia_derive_child_seed(parentSeedHex, index));
    }

    @Override
    public boolean constantTimeEq(String aHex, String bHex) {
        return Boolean.TRUE.equals(envelopeValue(nativeCore.dilithia_constant_time_eq(aHex, bHex)));
    }

    @Override
    public String hashHex(String dataHex) {
        return (String) envelopeValue(nativeCore.dilithia_hash_hex(dataHex));
    }

    @Override
    public void setHashAlg(String alg) {
        envelopeValue(nativeCore.dilithia_set_hash_alg(alg));
    }

    @Override
    public String currentHashAlg() {
        return (String) envelopeValue(nativeCore.dilithia_current_hash_alg());
    }

    @Override
    public int hashLenHex() {
        return ((Number) envelopeValue(nativeCore.dilithia_hash_len_hex())).intValue();
    }

    private static Object envelopeValue(String payload) {
        Map<String, Object> envelope = JsonMaps.parse(payload);
        if (!Boolean.TRUE.equals(envelope.get("ok"))) {
            throw new IllegalStateException(String.valueOf(envelope.get("error")));
        }
        return envelope.get("value");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> envelopeMap(String payload) {
        Object value = envelopeValue(payload);
        if (!(value instanceof Map<?, ?> valueMap)) {
            throw new IllegalStateException("native-core response is not a map");
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        valueMap.forEach((key, entryValue) -> normalized.put(String.valueOf(key), entryValue));
        return normalized;
    }

    @SuppressWarnings("unchecked")
    private static DilithiaAccount accountFromMap(Map<String, Object> value) {
        return new DilithiaAccount(
                (String) value.get("address"),
                (String) value.get("public_key"),
                (String) value.get("secret_key"),
                ((Number) value.get("account_index")).intValue(),
                (Map<String, Object>) value.get("wallet_file")
        );
    }
}
