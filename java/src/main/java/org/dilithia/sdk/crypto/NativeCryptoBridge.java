package org.dilithia.sdk.crypto;

import com.sun.jna.Library;
import com.sun.jna.Native;
import java.util.LinkedHashMap;
import java.util.Map;
import org.dilithia.sdk.DilithiaAccount;
import org.dilithia.sdk.DilithiaCryptoAdapter;
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
