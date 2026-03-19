package org.dilithia.sdk;

public record DeployPayload(
        String name,
        String bytecode,
        String from,
        String alg,
        String pk,
        String sig,
        long nonce,
        String chainId,
        int version
) {}
