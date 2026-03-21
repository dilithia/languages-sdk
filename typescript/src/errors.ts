/**
 * Base error class for all Dilithia SDK errors.
 *
 * Every SDK-specific error extends this class, making it easy to catch
 * all SDK errors with a single `catch (e) { if (e instanceof DilithiaError) ... }`.
 */
export class DilithiaError extends Error {
  constructor(message: string, options?: ErrorOptions) {
    super(message, options);
    this.name = "DilithiaError";
  }
}

/**
 * Thrown when the JSON-RPC endpoint returns an application-level error
 * (i.e. the HTTP response was 200, but the `"error"` field is present).
 */
export class RpcError extends DilithiaError {
  /** The JSON-RPC error code. */
  readonly code: number;

  constructor(code: number, message: string) {
    super(message);
    this.name = "RpcError";
    this.code = code;
  }
}

/**
 * Thrown when the server responds with a non-2xx HTTP status code.
 */
export class HttpError extends DilithiaError {
  /** The HTTP status code. */
  readonly statusCode: number;

  /** The raw response body. */
  readonly body: string;

  constructor(statusCode: number, body: string) {
    super(`HTTP ${statusCode}`);
    this.name = "HttpError";
    this.statusCode = statusCode;
    this.body = body;
  }
}

/**
 * Thrown when a request exceeds the configured timeout.
 */
export class TimeoutError extends DilithiaError {
  /** The timeout that was exceeded, in milliseconds. */
  readonly timeoutMs: number;

  constructor(timeoutMs: number) {
    super(`Request timed out after ${timeoutMs}ms`);
    this.name = "TimeoutError";
    this.timeoutMs = timeoutMs;
  }
}
