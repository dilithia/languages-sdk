"""Credential and identity primitives for the Dilithia credential contract."""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

CREDENTIAL_CONTRACT = "credential"


@dataclass(frozen=True)
class SchemaAttribute:
    """A single attribute definition within a credential schema."""

    name: str
    type: str


@dataclass(frozen=True)
class CredentialSchema:
    """A credential schema registered on-chain."""

    name: str
    version: str
    attributes: list[SchemaAttribute]


@dataclass(frozen=True)
class Credential:
    """An issued credential stored on-chain."""

    commitment: str
    issuer: str
    holder: str
    schema_hash: str
    status: str
    revoked: bool


@dataclass(frozen=True)
class VerificationResult:
    """Result of a credential verification call."""

    valid: bool
    commitment: str
    reason: str | None = None


@dataclass(frozen=True)
class Predicate:
    """A zero-knowledge predicate used during selective disclosure verification."""

    type: str  # gt, eq, range, membership
    attribute: str
    threshold: int | None = None
    value: Any = None
    min: int | None = None
    max: int | None = None
    set: list[Any] = field(default_factory=list)


class CredentialClient:
    """High-level client for the credential contract. Wraps a DilithiaClient."""

    def __init__(self, client) -> None:
        self._client = client

    async def register_schema(
        self, name: str, version: str, attributes: list[dict]
    ) -> dict:
        """Register a new credential schema on-chain."""
        return await self._client.call_contract(
            CREDENTIAL_CONTRACT,
            "register_schema",
            {"name": name, "version": version, "attributes": attributes},
        )

    async def issue(
        self,
        holder: str,
        schema_hash: str,
        commitment: str,
        attributes: dict | None = None,
    ) -> dict:
        """Issue a credential to *holder* under the given schema."""
        args: dict[str, Any] = {
            "holder": holder,
            "schema_hash": schema_hash,
            "commitment": commitment,
        }
        if attributes:
            args["attributes"] = attributes
        return await self._client.call_contract(
            CREDENTIAL_CONTRACT, "issue", args
        )

    async def verify(
        self,
        commitment: str,
        schema_hash: str,
        proof: str,
        revealed_attributes: dict,
        predicates: list[dict] | None = None,
    ) -> VerificationResult:
        """Verify a credential proof, optionally with predicate checks."""
        result = await self._client.call_contract(
            CREDENTIAL_CONTRACT,
            "verify",
            {
                "commitment": commitment,
                "schema_hash": schema_hash,
                "proof": proof,
                "revealed_attributes": revealed_attributes,
                "predicates": predicates or [],
            },
        )
        return VerificationResult(
            valid=result.get("valid", False),
            commitment=commitment,
            reason=result.get("reason"),
        )

    async def revoke(self, commitment: str) -> dict:
        """Revoke a previously issued credential."""
        return await self._client.call_contract(
            CREDENTIAL_CONTRACT, "revoke", {"commitment": commitment}
        )

    async def get_credential(self, commitment: str) -> Credential | None:
        """Fetch a single credential by its commitment hash."""
        result = await self._client.query_contract(
            CREDENTIAL_CONTRACT, "get_credential", {"commitment": commitment}
        )
        val = result.value if hasattr(result, "value") else result
        cred = val.get("credential")
        if not cred:
            return None
        revoked = val.get("revoked", False)
        return Credential(
            commitment=commitment,
            issuer=cred["issuer"],
            holder=cred["holder"],
            schema_hash=cred["schema_hash"],
            status=cred.get("status", "active"),
            revoked=revoked,
        )

    async def get_schema(self, schema_hash: str) -> CredentialSchema | None:
        """Fetch a credential schema by its hash."""
        result = await self._client.query_contract(
            CREDENTIAL_CONTRACT,
            "get_schema",
            {"schema_hash": schema_hash},
        )
        val = result.value if hasattr(result, "value") else result
        schema = val.get("schema")
        if not schema:
            return None
        attrs = [
            SchemaAttribute(a["name"], a["type"])
            for a in schema.get("attributes", [])
        ]
        return CredentialSchema(
            name=schema["name"], version=schema["version"], attributes=attrs
        )

    async def list_by_holder(self, holder: str) -> list[dict]:
        """List all credentials held by *holder*."""
        result = await self._client.query_contract(
            CREDENTIAL_CONTRACT, "list_by_holder", {"holder": holder}
        )
        val = result.value if hasattr(result, "value") else result
        return val.get("credentials", [])

    async def list_by_issuer(self, issuer: str) -> list[dict]:
        """List all credentials issued by *issuer*."""
        result = await self._client.query_contract(
            CREDENTIAL_CONTRACT, "list_by_issuer", {"issuer": issuer}
        )
        val = result.value if hasattr(result, "value") else result
        return val.get("credentials", [])
