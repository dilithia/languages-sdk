"""Typed dataclass models returned by the Dilithia SDK client.

Every model uses ``frozen=True`` and ``slots=True`` for immutability and
memory efficiency.  Token amounts use :class:`~decimal.Decimal` to avoid
floating-point rounding issues.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from decimal import Decimal
from typing import Any

from .exceptions import ValidationError


# ---------------------------------------------------------------------------
# Primitives
# ---------------------------------------------------------------------------


@dataclass(frozen=True, slots=True)
class Address:
    """A Dilithia on-chain address."""

    value: str

    @staticmethod
    def of(value: str) -> Address:
        """Create an :class:`Address`, validating that it is non-empty."""
        if not value:
            raise ValidationError("Address must not be empty")
        return Address(value=value)

    def __str__(self) -> str:  # noqa: D105
        return self.value


@dataclass(frozen=True, slots=True)
class TxHash:
    """An immutable transaction hash."""

    value: str

    def __str__(self) -> str:  # noqa: D105
        return self.value


# ---------------------------------------------------------------------------
# Token amounts
# ---------------------------------------------------------------------------


@dataclass(frozen=True, slots=True)
class TokenAmount:
    """A precise token amount backed by :class:`~decimal.Decimal`.

    Parameters
    ----------
    value:
        The human-readable amount (e.g. ``Decimal("1.5")`` for 1.5 DILI).
    decimals:
        The number of decimal places used by the token (default 18).
    """

    value: Decimal
    decimals: int = 18

    @staticmethod
    def dili(value: str | int) -> TokenAmount:
        """Create a :class:`TokenAmount` denominated in DILI (18 decimals)."""
        return TokenAmount(value=Decimal(str(value)), decimals=18)

    @staticmethod
    def from_raw(raw: int, decimals: int = 18) -> TokenAmount:
        """Create a :class:`TokenAmount` from the smallest on-chain unit."""
        return TokenAmount(
            value=Decimal(raw) / Decimal(10**decimals),
            decimals=decimals,
        )

    def to_raw(self) -> int:
        """Convert back to the smallest on-chain unit (integer)."""
        return int(self.value * Decimal(10**self.decimals))

    def formatted(self) -> str:
        """Return a human-readable string like ``"1.500000000000000000"``."""
        fmt = f"{{:.{self.decimals}f}}"
        return fmt.format(self.value)


# ---------------------------------------------------------------------------
# Response models
# ---------------------------------------------------------------------------


@dataclass(frozen=True, slots=True)
class Balance:
    """Balance for a single address."""

    address: Address
    balance: TokenAmount


@dataclass(frozen=True, slots=True)
class Nonce:
    """Next expected nonce for an address."""

    address: Address
    next_nonce: int


@dataclass(frozen=True, slots=True)
class Receipt:
    """Transaction receipt returned after inclusion."""

    tx_hash: TxHash
    block_height: int
    status: str
    result: Any = None
    error: str | None = None
    gas_used: int = 0
    fee_paid: int = 0


@dataclass(frozen=True, slots=True)
class NetworkInfo:
    """High-level information about the network."""

    chain_id: str
    block_height: int
    base_fee: int


@dataclass(frozen=True, slots=True)
class GasEstimate:
    """Gas estimation data returned by the node."""

    gas_limit: int
    base_fee: int
    estimated_cost: int


@dataclass(frozen=True, slots=True)
class NameRecord:
    """A name-service record linking a human-readable name to an address."""

    name: str
    address: Address


@dataclass(frozen=True, slots=True)
class QueryResult:
    """The result of a read-only contract query."""

    value: Any
