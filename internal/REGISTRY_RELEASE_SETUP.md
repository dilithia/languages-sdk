# Registry Release Setup

This document describes the GitHub Actions release prerequisites for each
language in `languages-sdk`.

## npm

Used by:

- TypeScript SDK
- TypeScript native crypto bridge

Publishing mode:

- Trusted Publishing via GitHub Actions OIDC

Expected target names after the Dilithia rename:

- `@dilithia/sdk-node`
- `@dilithia/sdk-native`

Workflow requirements:

- `permissions.id-token = write`
- `permissions.contents = read`

Notes:

- Do not store a long-lived `NPM_TOKEN` if Trusted Publishing is enabled.
- Configure the GitHub repository/workflow as a trusted publisher in npm.

## PyPI

Used by:

- Python SDK
- Python native crypto bridge

Publishing mode:

- Trusted Publishing via GitHub Actions OIDC

Expected target names after the Dilithia rename:

- `dilithia-sdk`
- `dilithia-sdk-native`

Workflow requirements:

- `permissions.id-token = write`
- `permissions.contents = read`

Notes:

- No `PYPI_TOKEN` secret needed.
- Configure each package as a trusted publisher at pypi.org/manage/account/publishing/.

## crates.io

Used by:

- Rust SDK

Required GitHub secret:

- `CARGO_REGISTRY_TOKEN`

Expected target name after the Dilithia rename:

- `dilithia-sdk-rust`

Note:

- crates.io names are reserved by the first publish.
- Publish a minimal honest release first if you want to reserve the final crate.

## Maven Central

Used by:

- Java SDK

Required GitHub secrets:

- `MAVEN_CENTRAL_USERNAME`
- `MAVEN_CENTRAL_PASSWORD`

Chosen namespace:

- `org.dilithia`

Expected coordinates:

- `org.dilithia:dilithia-sdk-java`

Notes:

- Maven Central is the only target here that does not work like npm/PyPI.
- You must first have Central credentials for the chosen namespace.
- The current workflow assumes Central credentials are already configured.

## Go

Used by:

- Go SDK

No separate registry credentials are needed.

Publication model:

- GitHub repository path
- semantic version tags
- GitHub releases

Target module path after the Dilithia rename:

- `github.com/dilithia/languages-sdk/go`

Go consumers then resolve through:

- `go get`
- `proxy.golang.org`
- `pkg.go.dev`

## Release Trigger

Current workflows publish or package on:

- GitHub Release `published`

That means the intended sequence is:

1. merge to the default branch
2. create a version tag or GitHub release
3. let the workflow publish or package the corresponding artifact
