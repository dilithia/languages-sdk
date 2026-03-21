package org.dilithia.sdk.exception;

/**
 * Thrown when a JSON-RPC call returns an error object.
 *
 * <p>Carries the numeric error {@link #code()} and descriptive
 * {@link #rpcMessage()} from the JSON-RPC 2.0 error response.</p>
 */
public class RpcException extends DilithiaException {

    private final int code;
    private final String rpcMessage;

    /**
     * Constructs a new RPC exception.
     *
     * @param code       the JSON-RPC error code
     * @param rpcMessage the error message returned by the RPC server
     */
    public RpcException(int code, String rpcMessage) {
        super("JSON-RPC error " + code + ": " + rpcMessage);
        this.code = code;
        this.rpcMessage = rpcMessage;
    }

    /**
     * Returns the JSON-RPC error code.
     *
     * @return the numeric error code
     */
    public int code() {
        return code;
    }

    /**
     * Returns the error message from the RPC server.
     *
     * @return the RPC error message
     */
    public String rpcMessage() {
        return rpcMessage;
    }
}
