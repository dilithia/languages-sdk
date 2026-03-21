package org.dilithia.sdk;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Fluent builder for contract call payloads.
 *
 * <p>Construct via {@link DilithiaClient#contract(String)} (future)
 * or directly for connectors.</p>
 */
public final class ContractCallBuilder {

    private final DilithiaClient client;
    private final String contract;
    private final String method;
    private final Map<String, Object> args;
    private String paymaster;

    /**
     * Creates a new contract call builder.
     *
     * @param client   the SDK client
     * @param contract the contract address
     * @param method   the method name
     * @param args     the method arguments
     */
    public ContractCallBuilder(DilithiaClient client, String contract, String method, Map<String, Object> args) {
        this.client = client;
        this.contract = contract;
        this.method = method;
        this.args = args == null ? Map.of() : new LinkedHashMap<>(args);
    }

    /**
     * Attaches a paymaster to this call for gas sponsorship.
     *
     * @param paymaster the paymaster address
     * @return this builder
     */
    public ContractCallBuilder withPaymaster(String paymaster) {
        this.paymaster = paymaster;
        return this;
    }

    /**
     * Returns the contract address.
     *
     * @return the contract address
     */
    public String contract() {
        return contract;
    }

    /**
     * Returns the method name.
     *
     * @return the method name
     */
    public String method() {
        return method;
    }

    /**
     * Returns the method arguments.
     *
     * @return unmodifiable view of the arguments
     */
    public Map<String, Object> args() {
        return Map.copyOf(args);
    }

    /**
     * Returns the paymaster address, or null if none set.
     *
     * @return the paymaster address
     */
    public String paymaster() {
        return paymaster;
    }

    /**
     * Builds the canonical call payload map.
     *
     * @return the call payload with alphabetically sorted keys
     */
    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new TreeMap<>();
        payload.put("contract", contract);
        payload.put("method", method);
        payload.put("args", new TreeMap<>(args));
        if (paymaster != null && !paymaster.isBlank()) {
            payload.put("paymaster", paymaster);
        }
        return payload;
    }
}
