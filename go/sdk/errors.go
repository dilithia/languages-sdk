package sdk

import "fmt"

// DilithiaError is the base error type for SDK operations. It wraps an
// underlying cause so callers can use errors.Is and errors.As to inspect
// the chain.
type DilithiaError struct {
	// Message describes what went wrong at the SDK level.
	Message string
	// Cause is the underlying error, if any.
	Cause error
}

// Error implements the error interface.
func (e *DilithiaError) Error() string {
	if e.Cause != nil {
		return fmt.Sprintf("%s: %v", e.Message, e.Cause)
	}
	return e.Message
}

// Unwrap returns the underlying cause so errors.Is/errors.As can traverse
// the chain.
func (e *DilithiaError) Unwrap() error { return e.Cause }

// RpcError is returned when the JSON-RPC endpoint returns an error object.
type RpcError struct {
	// Code is the JSON-RPC error code.
	Code int
	// RpcMessage is the human-readable error message from the node.
	RpcMessage string
}

// Error implements the error interface.
func (e *RpcError) Error() string {
	return fmt.Sprintf("rpc error %d: %s", e.Code, e.RpcMessage)
}

// HttpError is returned when the HTTP response has a non-2xx status code.
type HttpError struct {
	// StatusCode is the HTTP status code.
	StatusCode int
	// Body is the response body, truncated for display.
	Body string
}

// Error implements the error interface.
func (e *HttpError) Error() string {
	if e.Body != "" {
		return fmt.Sprintf("HTTP %d: %s", e.StatusCode, e.Body)
	}
	return fmt.Sprintf("HTTP %d", e.StatusCode)
}

// TimeoutError is returned when an operation exceeds its deadline.
type TimeoutError struct {
	// Operation describes the operation that timed out.
	Operation string
}

// Error implements the error interface.
func (e *TimeoutError) Error() string {
	return fmt.Sprintf("timeout: %s", e.Operation)
}
