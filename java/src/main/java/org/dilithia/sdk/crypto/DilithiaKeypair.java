package org.dilithia.sdk.crypto;

import org.dilithia.sdk.model.Address;
import org.dilithia.sdk.model.PublicKey;
import org.dilithia.sdk.model.SecretKey;

/**
 * Represents a Dilithia ML-DSA-65 keypair.
 *
 * @param secretKey the secret key
 * @param publicKey the public key
 * @param address   the derived account address
 */
public record DilithiaKeypair(SecretKey secretKey, PublicKey publicKey, Address address) {}
