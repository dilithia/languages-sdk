package org.dilithia.sdk;

/**
 * Static entry point for the Dilithia SDK.
 *
 * <p>Use {@link #client(String)} to begin building a {@link DilithiaClient}:</p>
 * <pre>{@code
 * var client = Dilithia.client("http://localhost:8000/rpc")
 *     .timeout(Duration.ofSeconds(15))
 *     .jwt("token")
 *     .build();
 * }</pre>
 */
public final class Dilithia {

    /**
     * Creates a new client builder targeting the given RPC URL.
     *
     * @param rpcUrl the base JSON-RPC endpoint URL (e.g. {@code "http://localhost:8000/rpc"})
     * @return a new builder instance
     */
    public static DilithiaClientBuilder client(String rpcUrl) {
        return new DilithiaClientBuilder(rpcUrl);
    }

    private Dilithia() {
        // static facade — not instantiable
    }
}
