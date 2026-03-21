package org.dilithia.sdk.exception;

/**
 * Thrown when an HTTP request receives a non-2xx status code.
 *
 * <p>Provides access to the raw {@link #statusCode()} and response
 * {@link #body()} for diagnostics.</p>
 */
public class HttpException extends DilithiaException {

    private final int statusCode;
    private final String body;

    /**
     * Constructs a new HTTP exception.
     *
     * @param statusCode the HTTP status code
     * @param body       the response body, may be empty
     */
    public HttpException(int statusCode, String body) {
        super("HTTP " + statusCode + ": " + body);
        this.statusCode = statusCode;
        this.body = body;
    }

    /**
     * Returns the HTTP status code.
     *
     * @return the status code
     */
    public int statusCode() {
        return statusCode;
    }

    /**
     * Returns the response body.
     *
     * @return the body text
     */
    public String body() {
        return body;
    }
}
