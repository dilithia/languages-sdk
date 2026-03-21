package org.dilithia.sdk.crypto;

import org.dilithia.sdk.model.Address;
import org.dilithia.sdk.model.PublicKey;
import org.dilithia.sdk.model.SecretKey;

import java.util.Map;

/**
 * Represents a Dilithia HD wallet account.
 *
 * @param address      the account address
 * @param publicKey    the public key
 * @param secretKey    the secret key
 * @param accountIndex the HD derivation index
 * @param walletFile   the optional wallet file map
 */
public record DilithiaAccount(
        Address address,
        PublicKey publicKey,
        SecretKey secretKey,
        int accountIndex,
        Map<String, Object> walletFile
) {}
