package org.dilithia.sdk.model;

/**
 * Represents a generic JSON-RPC response.
 *
 * @param id     the request ID
 * @param result the result object (may be any type)
 */
public record RpcResponse(int id, Object result) {}
