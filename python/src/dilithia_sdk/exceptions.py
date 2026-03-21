"""Exception hierarchy for the Dilithia SDK.

All SDK-specific exceptions inherit from :class:`DilithiaError` so callers
can catch a single base type when they do not need fine-grained handling.
"""

from __future__ import annotations


class DilithiaError(Exception):
    """Base exception for all Dilithia SDK errors."""


class RpcError(DilithiaError):
    """The JSON-RPC endpoint returned an application-level error."""

    def __init__(self, code: int, message: str) -> None:
        self.code = code
        self.message = message
        super().__init__(f"RPC error {code}: {message}")


class HttpError(DilithiaError):
    """The HTTP request completed but the status code indicated failure."""

    def __init__(self, status_code: int, body: str) -> None:
        self.status_code = status_code
        self.body = body
        super().__init__(f"HTTP {status_code}: {body[:200]}")


class TimeoutError(DilithiaError):
    """The request did not complete within the configured timeout."""


class CryptoError(DilithiaError):
    """An error occurred in the cryptographic subsystem."""


class ValidationError(DilithiaError):
    """A caller-supplied value failed validation."""
