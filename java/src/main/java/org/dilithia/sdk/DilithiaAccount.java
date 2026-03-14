package org.dilithia.sdk;

import java.util.Map;

public record DilithiaAccount(
        String address,
        String publicKey,
        String secretKey,
        int accountIndex,
        Map<String, Object> walletFile
) {}
