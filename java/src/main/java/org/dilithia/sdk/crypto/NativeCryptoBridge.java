package org.dilithia.sdk.crypto;

import com.sun.jna.Library;
import com.sun.jna.Native;
import org.dilithia.sdk.exception.CryptoException;
import org.dilithia.sdk.exception.ValidationException;
import org.dilithia.sdk.internal.Json;
import org.dilithia.sdk.model.Address;
import org.dilithia.sdk.model.PublicKey;
import org.dilithia.sdk.model.SecretKey;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JNA-based bridge to the Dilithia native crypto library.
 *
 * <p>Requires the {@code DILITHIUM_NATIVE_CORE_LIB} environment variable
 * to point to the shared library path.</p>
 */
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

    public NativeCryptoBridge() throws CryptoException {
        String path = System.getenv("DILITHIUM_NATIVE_CORE_LIB");
        if (path == null || path.isBlank()) {
            throw new CryptoException("DILITHIUM_NATIVE_CORE_LIB is not configured");
        }
        try {
            this.nativeCore = Native.load(path, NativeCore.class);
        } catch (Exception e) {
            throw new CryptoException("Failed to load native crypto library: " + e.getMessage(), e);
        }
    }

    @Override
    public String generateMnemonic() throws CryptoException {
        return (String) envelopeValue(nativeCore.dilithia_generate_mnemonic());
    }

    @Override
    public void validateMnemonic(String mnemonic) throws ValidationException {
        try {
            envelopeValue(nativeCore.dilithia_validate_mnemonic(mnemonic));
        } catch (CryptoException e) {
            throw new ValidationException("Invalid mnemonic: " + e.getMessage(), e);
        }
    }

    @Override
    public DilithiaAccount recoverHdWallet(String mnemonic) throws CryptoException {
        return recoverHdWalletAccount(mnemonic, 0);
    }

    @Override
    public DilithiaAccount recoverHdWalletAccount(String mnemonic, int accountIndex) throws CryptoException {
        Map<String, Object> value = envelopeMap(nativeCore.dilithia_recover_hd_account(mnemonic, accountIndex));
        return new DilithiaAccount(
                Address.of((String) value.get("address")),
                PublicKey.of((String) value.get("public_key")),
                SecretKey.of((String) value.get("secret_key")),
                ((Number) value.get("account_index")).intValue(),
                null
        );
    }

    @Override
    public DilithiaAccount createHdWalletFileFromMnemonic(String mnemonic, String password) throws CryptoException {
        return accountFromMap(envelopeMap(nativeCore.dilithia_create_hd_wallet_file_from_mnemonic(mnemonic, password)));
    }

    @Override
    public DilithiaAccount createHdWalletAccountFromMnemonic(String mnemonic, String password, int accountIndex) throws CryptoException {
        return accountFromMap(
                envelopeMap(nativeCore.dilithia_create_hd_wallet_account_from_mnemonic(mnemonic, password, accountIndex))
        );
    }

    @Override
    public DilithiaAccount recoverWalletFile(Map<String, Object> walletFile, String mnemonic, String password) throws CryptoException {
        try {
            String walletJson = Json.serialize(walletFile);
            return accountFromMap(
                    envelopeMap(nativeCore.dilithia_recover_wallet_file(walletJson, mnemonic, password))
            );
        } catch (CryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new CryptoException("Failed to serialize wallet file: " + e.getMessage(), e);
        }
    }

    @Override
    public Address addressFromPublicKey(PublicKey publicKey) throws CryptoException {
        Map<String, Object> value = envelopeMap(nativeCore.dilithia_address_from_public_key(publicKey.hex()));
        return Address.of((String) value.get("address"));
    }

    @Override
    public DilithiaSignature signMessage(SecretKey secretKey, String message) throws CryptoException {
        Map<String, Object> value = envelopeMap(nativeCore.dilithia_sign_message(secretKey.hex(), message));
        return new DilithiaSignature((String) value.get("algorithm"), (String) value.get("signature"));
    }

    @Override
    public boolean verifyMessage(PublicKey publicKey, String message, String signatureHex) throws CryptoException {
        Map<String, Object> value = envelopeMap(nativeCore.dilithia_verify_message(publicKey.hex(), message, signatureHex));
        return Boolean.TRUE.equals(value.get("ok"));
    }

    @Override
    public Address validateAddress(String addr) throws ValidationException {
        try {
            Map<String, Object> value = envelopeMap(nativeCore.dilithia_validate_address(addr));
            return Address.of((String) value.get("address"));
        } catch (CryptoException e) {
            throw new ValidationException("Invalid address: " + e.getMessage(), e);
        }
    }

    @Override
    public Address addressFromPkChecksummed(PublicKey publicKey) throws CryptoException {
        Map<String, Object> value = envelopeMap(nativeCore.dilithia_address_from_pk_checksummed(publicKey.hex()));
        return Address.of((String) value.get("address"));
    }

    @Override
    public Address addressWithChecksum(String rawAddr) throws CryptoException {
        Map<String, Object> value = envelopeMap(nativeCore.dilithia_address_with_checksum(rawAddr));
        return Address.of((String) value.get("address"));
    }

    @Override
    public void validatePublicKey(PublicKey publicKey) throws ValidationException {
        try {
            envelopeValue(nativeCore.dilithia_validate_pk(publicKey.hex()));
        } catch (CryptoException e) {
            throw new ValidationException("Invalid public key: " + e.getMessage(), e);
        }
    }

    @Override
    public void validateSecretKey(SecretKey secretKey) throws ValidationException {
        try {
            envelopeValue(nativeCore.dilithia_validate_sk(secretKey.hex()));
        } catch (CryptoException e) {
            throw new ValidationException("Invalid secret key: " + e.getMessage(), e);
        }
    }

    @Override
    public void validateSignature(String signatureHex) throws ValidationException {
        try {
            envelopeValue(nativeCore.dilithia_validate_sig(signatureHex));
        } catch (CryptoException e) {
            throw new ValidationException("Invalid signature: " + e.getMessage(), e);
        }
    }

    @Override
    public DilithiaKeypair keygen() throws CryptoException {
        Map<String, Object> value = envelopeMap(nativeCore.dilithia_keygen_mldsa65());
        return new DilithiaKeypair(
                SecretKey.of((String) value.get("secret_key")),
                PublicKey.of((String) value.get("public_key")),
                Address.of((String) value.get("address"))
        );
    }

    @Override
    public DilithiaKeypair keygenFromSeed(String seedHex) throws CryptoException {
        Map<String, Object> value = envelopeMap(nativeCore.dilithia_keygen_mldsa65_from_seed(seedHex));
        return new DilithiaKeypair(
                SecretKey.of((String) value.get("secret_key")),
                PublicKey.of((String) value.get("public_key")),
                Address.of((String) value.get("address"))
        );
    }

    @Override
    public String seedFromMnemonic(String mnemonic) throws CryptoException {
        return (String) envelopeValue(nativeCore.dilithia_seed_from_mnemonic(mnemonic));
    }

    @Override
    public String deriveChildSeed(String parentSeedHex, int index) throws CryptoException {
        return (String) envelopeValue(nativeCore.dilithia_derive_child_seed(parentSeedHex, index));
    }

    @Override
    public boolean constantTimeEq(String aHex, String bHex) throws CryptoException {
        return Boolean.TRUE.equals(envelopeValue(nativeCore.dilithia_constant_time_eq(aHex, bHex)));
    }

    @Override
    public String hashHex(String dataHex) throws CryptoException {
        return (String) envelopeValue(nativeCore.dilithia_hash_hex(dataHex));
    }

    @Override
    public void setHashAlg(String alg) throws CryptoException {
        envelopeValue(nativeCore.dilithia_set_hash_alg(alg));
    }

    @Override
    public String currentHashAlg() throws CryptoException {
        return (String) envelopeValue(nativeCore.dilithia_current_hash_alg());
    }

    @Override
    public int hashLenHex() throws CryptoException {
        return ((Number) envelopeValue(nativeCore.dilithia_hash_len_hex())).intValue();
    }

    private static Object envelopeValue(String payload) throws CryptoException {
        try {
            Map<String, Object> envelope = Json.deserializeMap(payload);
            if (!Boolean.TRUE.equals(envelope.get("ok"))) {
                throw new CryptoException(String.valueOf(envelope.get("error")));
            }
            return envelope.get("value");
        } catch (CryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new CryptoException("Failed to parse native response: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> envelopeMap(String payload) throws CryptoException {
        Object value = envelopeValue(payload);
        if (!(value instanceof Map<?, ?> valueMap)) {
            throw new CryptoException("native-core response is not a map");
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        valueMap.forEach((key, entryValue) -> normalized.put(String.valueOf(key), entryValue));
        return normalized;
    }

    @SuppressWarnings("unchecked")
    private static DilithiaAccount accountFromMap(Map<String, Object> value) {
        return new DilithiaAccount(
                Address.of((String) value.get("address")),
                PublicKey.of((String) value.get("public_key")),
                SecretKey.of((String) value.get("secret_key")),
                ((Number) value.get("account_index")).intValue(),
                (Map<String, Object>) value.get("wallet_file")
        );
    }
}
